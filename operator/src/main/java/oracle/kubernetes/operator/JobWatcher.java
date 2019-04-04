// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobCondition;
import io.kubernetes.client.models.V1JobStatus;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

/** Watches for Jobs to become Ready or leave Ready state. */
public class JobWatcher extends Watcher<V1Job> implements WatchListener<V1Job> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final Map<String, JobWatcher> JOB_WATCHERS = new HashMap<>();
  private static JobWatcherFactory factory;

  private final String namespace;

  // Map of Pod name to Complete
  private final ConcurrentMap<String, Complete> completeCallbackRegistrations =
      new ConcurrentHashMap<>();

  /**
   * Returns a cached JobWatcher, if present; otherwise, creates a new one.
   *
   * @param domain the domain for which the job watcher is to be returned
   * @return a cached jobwatcher.
   */
  public static @Nonnull JobWatcher getOrCreateFor(Domain domain) {
    return JOB_WATCHERS.computeIfAbsent(getNamespace(domain), n -> factory.createFor(domain));
  }

  private static String getNamespace(Domain domain) {
    return domain.getMetadata().getNamespace();
  }

  /**
   * Creates a new JobWatcher and caches it by namespace.
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
      String namespace,
      String initialResourceVersion,
      WatchTuning tuning,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping);
    setListener(this);
    this.namespace = namespace;
  }

  static void defineFactory(
      ThreadFactory threadFactory,
      WatchTuning tuning,
      Function<String, AtomicBoolean> isNamespaceStopping) {
    factory = new JobWatcherFactory(threadFactory, tuning, isNamespaceStopping);
  }

  @Override
  public WatchI<V1Job> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createJobWatch(namespace);
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
            fiber
                .createChildFiber()
                .start(
                    new CallBuilder()
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

  static class JobWatcherFactory {
    private ThreadFactory threadFactory;
    private WatchTuning watchTuning;

    private Function<String, AtomicBoolean> isNamespaceStopping;

    JobWatcherFactory(
        ThreadFactory threadFactory,
        WatchTuning watchTuning,
        Function<String, AtomicBoolean> isNamespaceStopping) {
      this.threadFactory = threadFactory;
      this.watchTuning = watchTuning;
      this.isNamespaceStopping = isNamespaceStopping;
    }

    JobWatcher createFor(Domain domain) {
      String namespace = getNamespace(domain);
      return create(
          threadFactory,
          namespace,
          domain.getMetadata().getResourceVersion(),
          watchTuning,
          isNamespaceStopping.apply(namespace));
    }
  }

  @FunctionalInterface
  private interface Complete {
    void isComplete(V1Job job);
  }
}
