// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobCondition;
import io.kubernetes.client.models.V1JobStatus;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** Watches for Jobs to become Ready or leave Ready state. */
public class JobWatcher extends Watcher<V1Job> implements WatchListener<V1Job> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String ns;

  // Map of Pod name to Complete
  private final ConcurrentMap<String, Complete> completeCallbackRegistrations =
      new ConcurrentHashMap<>();

  /**
   * Factory for JobWatcher.
   *
   * @param factory thread factory
   * @param ns Namespace
   * @param initialResourceVersion Initial resource version or empty string
   * @param tuning Tuning parameters for the watch, for example watch lifetime
   * @param isStopping Stop signal
   * @return Job watcher for the namespace
   */
  public static JobWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      AtomicBoolean isStopping) {
    JobWatcher watcher = new JobWatcher(ns, initialResourceVersion, tuning, isStopping);
    watcher.start(factory);
    return watcher;
  }

  private JobWatcher(
      String ns, String initialResourceVersion, WatchTuning tuning, AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping);
    setListener(this);
    this.ns = ns;
  }

  @Override
  public WatchI<V1Job> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createJobWatch(ns);
  }

  public void receivedResponse(Watch.Response<V1Job> item) {
    LOGGER.entering();

    LOGGER.fine("JobWatcher.receivedResponse response item: " + item);
    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        V1Job job = item.object;
        Boolean isComplete = isComplete(job);
        Boolean isFailed = isFailed(job);
        String jobName = job.getMetadata().getName();
        if (isComplete || isFailed) {
          Complete complete = completeCallbackRegistrations.get(jobName);
          if (complete != null) {
            complete.isComplete(job);
          }
        }
        break;
      case "DELETED":
      case "ERROR":
      default:
    }

    LOGGER.exiting();
  }

  public static boolean isComplete(V1Job job) {
    V1JobStatus status = job.getStatus();
    LOGGER.info(MessageKeys.JOB_IS_COMPLETE, job.getMetadata().getName(), status);
    if (status != null) {
      List<V1JobCondition> conds = status.getConditions();
      if (conds != null) {
        for (V1JobCondition cond : conds) {
          if ("Complete".equals(cond.getType())) {
            if ("True".equals(cond.getStatus())) { // TODO: Verify V1JobStatus.succeeded count?
              // Job is complete!
              LOGGER.info(MessageKeys.JOB_IS_COMPLETE, job.getMetadata().getName());
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public static boolean isFailed(V1Job job) {
    V1JobStatus status = job.getStatus();
    if (status != null) {
      if (status.getFailed() != null && status.getFailed() > 0) {
        LOGGER.severe(MessageKeys.JOB_IS_FAILED, job.getMetadata().getName());
        return true;
      }
    }
    return false;
  }

  /**
   * Waits until the Job is Ready.
   *
   * @param job Job to watch
   * @param next Next processing step once Job is ready
   * @return Asynchronous step
   */
  public Step waitForReady(V1Job job, Step next) {
    return new WaitForJobReadyStep(job, next);
  }

  private class WaitForJobReadyStep extends Step {
    private final V1Job job;

    private WaitForJobReadyStep(V1Job job, Step next) {
      super(next);
      this.job = job;
    }

    boolean shouldProcessJob(V1Job job) {
      return (this.job.getMetadata().getCreationTimestamp().getMillis()
          == job.getMetadata().getCreationTimestamp().getMillis());
    }

    @Override
    public NextAction apply(Packet packet) {
      if (isComplete(job)) {
        return doNext(packet);
      }

      V1ObjectMeta metadata = job.getMetadata();

      LOGGER.info(MessageKeys.WAITING_FOR_JOB_READY, metadata.getName());

      AtomicBoolean didResume = new AtomicBoolean(false);
      return doSuspend(
          (fiber) -> {
            Complete complete =
                (V1Job job) -> {
                  if (!shouldProcessJob(job)) {
                    return;
                  }
                  completeCallbackRegistrations.remove(job.getMetadata().getName());
                  if (didResume.compareAndSet(false, true)) {
                    LOGGER.fine("Job status: " + job.getStatus());
                    packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB, job);
                    fiber.resume(packet);
                  }
                };
            completeCallbackRegistrations.put(metadata.getName(), complete);

            // Timing window -- job may have come ready before registration for callback
            CallBuilderFactory factory =
                ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
            fiber
                .createChildFiber()
                .start(
                    factory
                        .create()
                        .readJobAsync(
                            metadata.getName(),
                            metadata.getNamespace(),
                            new ResponseStep<V1Job>(null) {
                              @Override
                              public NextAction onFailure(
                                  Packet packet,
                                  ApiException e,
                                  int statusCode,
                                  Map<String, List<String>> responseHeaders) {
                                return super.onFailure(packet, e, statusCode, responseHeaders);
                              }

                              @Override
                              public NextAction onSuccess(
                                  Packet packet,
                                  V1Job result,
                                  int statusCode,
                                  Map<String, List<String>> responseHeaders) {
                                if (result != null && isComplete(result) /*isReady(result)*/) {
                                  if (didResume.compareAndSet(false, true)) {
                                    completeCallbackRegistrations.remove(
                                        metadata.getName(), complete);
                                    fiber.resume(packet);
                                  }
                                }
                                return doNext(packet);
                              }
                            }),
                    packet.clone(),
                    null);
          });
    }
  }

  @FunctionalInterface
  private interface Complete {
    void isComplete(V1Job job);
  }
}
