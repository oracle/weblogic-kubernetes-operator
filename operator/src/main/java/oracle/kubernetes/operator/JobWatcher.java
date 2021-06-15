// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;

/** Watches for Jobs to become Ready or leave Ready state. */
public class JobWatcher extends Watcher<V1Job> implements WatchListener<V1Job>, JobAwaiterStepFactory {
  static final WatchListener<V1Job> NULL_LISTENER = r -> {};

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String namespace;

  // Map of Job name to Runnable
  private final Map<String,Consumer<V1Job>> completeCallbackRegistrations = new ConcurrentHashMap<>();

  private JobWatcher(
      String namespace,
      String initialResourceVersion,
      WatchTuning tuning,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping);
    setListener(this);
    this.namespace = namespace;
  }

  private void addOnModifiedCallback(String jobName, Consumer<V1Job> callback) {
    completeCallbackRegistrations.put(jobName, callback);
  }

  private void dispatchCallback(String jobName, V1Job job) {
    Optional.ofNullable(completeCallbackRegistrations.get(jobName)).ifPresent(callback -> callback.accept(job));
  }

  private void removeOnModifiedCallback(String jobName, Consumer<V1Job> callback) {
    completeCallbackRegistrations.remove(jobName, callback);
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  @Override
  public String getDomainUid(Watch.Response<V1Job> item) {
    return KubernetesUtils.getDomainUidLabel(
        Optional.ofNullable(item.object).map(V1Job::getMetadata).orElse(null));
  }

  /**
   * Creates a new JobWatcher.
   *
   * @param factory thread factory
   * @param ns Namespace
   * @param initialResourceVersion Initial resource version or empty string
   * @param tuning Tuning parameters for the watch, for example watch lifetime
   * @param listener a null listener to keep the same signature as other watcher create methods
   * @param isStopping Stop signal
   * @return Job watcher for the namespace
   */
  public static JobWatcher create(
        ThreadFactory factory,
        String ns,
        String initialResourceVersion,
        WatchTuning tuning,
        @SuppressWarnings("unused") WatchListener<V1Job> listener,
        AtomicBoolean isStopping) {
    JobWatcher watcher = new JobWatcher(ns, initialResourceVersion, tuning, isStopping);
    watcher.start(factory);
    return watcher;
  }

  /**
   * Test if job is complete.
   * @param job job
   * @return true, if complete
   */
  public static boolean isComplete(V1Job job) {
    if (job == null) {
      return false;
    }

    V1JobStatus status = job.getStatus();
    LOGGER.fine(
        "JobWatcher.isComplete status of job " + Objects.requireNonNull(job.getMetadata()).getName() + ": " + status);
    if (status != null) {
      List<V1JobCondition> conds = status.getConditions();
      if (conds != null) {
        for (V1JobCondition cond : conds) {
          if ("Complete".equals(cond.getType())) {
            if ("True".equals(cond.getStatus())) { // TODO: Verify V1JobStatus.succeeded count?
              // Job is complete!
              LOGGER.info(MessageKeys.JOB_IS_COMPLETE, job.getMetadata().getName(), status);
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  static boolean isFailed(V1Job job) {
    if (job == null) {
      return false;
    }

    if (isStatusFailed(job) || isConditionFailed(job)) {
      LOGGER.severe(MessageKeys.JOB_IS_FAILED, job.getMetadata().getName());
      return true;
    }
    return false;
  }

  private static boolean isStatusFailed(V1Job job) {
    return Optional.ofNullable(job.getStatus()).map(V1JobStatus::getFailed).map(failed -> (failed > 0)).orElse(false);
  }

  private static boolean isConditionFailed(V1Job job) {
    return getJobConditions(job).stream().anyMatch(JobWatcher::isJobConditionFailed);
  }

  private static List<V1JobCondition> getJobConditions(@Nonnull V1Job job) {
    return Optional.ofNullable(job.getStatus()).map(V1JobStatus::getConditions).orElse(Collections.emptyList());
  }

  private static boolean isJobConditionFailed(V1JobCondition jobCondition) {
    return getType(jobCondition).equals("Failed") && getStatus(jobCondition).equals("True");
  }

  private static String getType(V1JobCondition jobCondition) {
    return Optional.ofNullable(jobCondition).map(V1JobCondition::getType).orElse("");
  }

  private static String getStatus(V1JobCondition jobCondition) {
    return Optional.ofNullable(jobCondition).map(V1JobCondition::getStatus).orElse("");
  }


  static String getFailedReason(V1Job job) {
    V1JobStatus status = job.getStatus();
    if (status != null && status.getConditions() != null) {
      for (V1JobCondition cond : status.getConditions()) {
        if ("Failed".equals(cond.getType()) && "True".equals(cond.getStatus())) {
          return cond.getReason();
        }
      }
    }
    return null;
  }

  @Override
  public Watchable<V1Job> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createJobWatch(namespace);
  }

  /**
   * receive response.
   * @param item item
   */
  public void receivedResponse(Watch.Response<V1Job> item) {
    LOGGER.entering();

    LOGGER.fine("JobWatcher.receivedResponse response item: " + item);
    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        dispatchCallback(getJobName(item), item.object);
        break;
      case "DELETED":
      case "ERROR":
      default:
    }

    LOGGER.exiting();
  }

  private String getJobName(Watch.Response<V1Job> item) {
    return item.object.getMetadata().getName();
  }

  /**
   * Waits until the Job is Ready.
   *
   * @param job Job to watch
   * @param next Next processing step once Job is ready
   * @return Asynchronous step
   */
  @Override
  public Step waitForReady(V1Job job, Step next) {
    return new WaitForJobReadyStep(job, next);
  }

  private class WaitForJobReadyStep extends WaitForReadyStep<V1Job> {
    private final OffsetDateTime jobCreationTime;

    private WaitForJobReadyStep(V1Job job, Step next) {
      super(job, next);
      jobCreationTime = getCreationTime(job);
      V1ObjectMeta metadata = job.getMetadata();
      LOGGER.info(MessageKeys.JOB_CREATION_TIMESTAMP_MESSAGE, metadata.getName(),
          metadata.getCreationTimestamp());
    }

    // A job is considered ready once it has either successfully completed, or been marked as failed.
    @Override
    boolean isReady(V1Job job) {
      return isComplete(job) || isFailed(job);
    }

    @Override
    boolean onReadNotFoundForCachedResource(V1Job cachedJob, boolean isNotFoundOnRead) {
      return false;
    }

    // Ignore modified callbacks from different jobs (identified by having different creation times) or those
    // where the job is not yet ready.
    @Override
    boolean shouldProcessCallback(V1Job job) {
      return hasExpectedCreationTime(job) && isReady(job);
    }

    private boolean hasExpectedCreationTime(V1Job job) {
      return getCreationTime(job).equals(jobCreationTime);
    }

    private OffsetDateTime getCreationTime(V1Job job) {
      return job.getMetadata().getCreationTimestamp();
    }

    @Override
    V1ObjectMeta getMetadata(V1Job job) {
      return job.getMetadata();
    }

    @Override
    void addCallback(String name, Consumer<V1Job> callback) {
      addOnModifiedCallback(name, callback);
    }

    @Override
    void removeCallback(String name, Consumer<V1Job> callback) {
      removeOnModifiedCallback(name, callback);
    }

    @Override
    Step createReadAsyncStep(String name, String namespace, String domainUid, ResponseStep<V1Job> responseStep) {
      return new CallBuilder().readJobAsync(name, namespace, domainUid, responseStep);
    }

    // When we detect a job as ready, we add it to the packet for downstream processing.
    @Override
    void updatePacket(Packet packet, V1Job job) {
      packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB, job);
    }

    // Do not proceed to next step such as ReadDomainIntrospectorPodLog if job
    // failed due to DeadlineExceeded, as the pod container would likely not
    // be available for reading
    @Override
    boolean shouldTerminateFiber(V1Job job) {
      return isFailed(job) && ("DeadlineExceeded".equals(getFailedReason(job)));
    }

    // create an exception to terminate the fiber
    @Override
    Throwable createTerminationException(V1Job job) {
      return new DeadlineExceededException(job);
    }

    @Override
    void logWaiting(String name) {
      LOGGER.fine(MessageKeys.WAITING_FOR_JOB_READY, name);
    }

    @Override
    protected DefaultResponseStep<V1Job> resumeIfReady(Callback callback) {
      return new DefaultResponseStep<>(null) {
        @Override
        public NextAction onSuccess(Packet packet, CallResponse<V1Job> callResponse) {
          if (isReady(callResponse.getResult()) || callback.didResumeFiber()) {
            callback.proceedFromWait(callResponse.getResult());
            return doNext(packet);
          }
          return doDelay(createReadAndIfReadyCheckStep(callback), packet,
                  getWatchBackstopRecheckDelaySeconds(), TimeUnit.SECONDS);
        }
      };
    }
  }

  static class DeadlineExceededException extends Exception {
    final V1Job job;

    DeadlineExceededException(V1Job job) {
      super();
      this.job = job;
    }

    public String toString() {
      return LOGGER.formatMessage(
          MessageKeys.JOB_DEADLINE_EXCEEDED_MESSAGE,
          job.getMetadata().getName(),
          job.getSpec().getActiveDeadlineSeconds(),
          getJobStartedSeconds(),
          DomainPresence.getDomainPresenceFailureRetryMaxCount());
    }

    private long getJobStartedSeconds() {
      if (job.getStatus() != null && job.getStatus().getStartTime() != null) {
        return ChronoUnit.SECONDS.between(job.getStatus().getStartTime(), SystemClock.now());
      }
      return -1;
    }
  }
}