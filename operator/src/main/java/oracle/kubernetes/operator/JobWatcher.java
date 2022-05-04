// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;

import static oracle.kubernetes.operator.KubernetesConstants.BACKOFFLIMIT_EXCEEDED_REASON;
import static oracle.kubernetes.operator.KubernetesConstants.DEADLINE_EXCEEDED_REASON;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER;

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

  private void dispatchCallback(String jobName, V1Job job) {
    Optional.ofNullable(completeCallbackRegistrations.get(jobName)).ifPresent(callback -> callback.accept(job));
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
          if (V1JobCondition.TypeEnum.COMPLETE.equals(cond.getType()) && "True".equals(cond.getStatus())) {
            // Job is complete!
            LOGGER.info(MessageKeys.JOB_IS_COMPLETE, job.getMetadata().getName(), status);
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Returns true if the specified job has a failed status or condition.
   * @param job job to be tested
   */
  public static boolean isFailed(V1Job job) {
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

  private static List<V1JobCondition> getJobConditions(V1Job job) {
    return Optional.ofNullable(job).map(V1Job::getStatus).map(V1JobStatus::getConditions)
        .orElse(Collections.emptyList());
  }

  private static boolean isJobConditionFailed(V1JobCondition jobCondition) {
    return V1JobCondition.TypeEnum.FAILED.equals(getType(jobCondition)) && getStatus(jobCondition).equals("True");
  }

  private static V1JobCondition.TypeEnum getType(V1JobCondition jobCondition) {
    return Optional.ofNullable(jobCondition).map(V1JobCondition::getType).orElse(null);
  }

  private static String getStatus(V1JobCondition jobCondition) {
    return Optional.ofNullable(jobCondition).map(V1JobCondition::getStatus).orElse("");
  }

  private static boolean isJobConditionFailedBackoffLimitExceeded(V1JobCondition jobCondition) {
    return isJobConditionFailed(jobCondition) && BACKOFFLIMIT_EXCEEDED_REASON.equals(jobCondition.getReason());
  }

  /**
   * Get the reason for job failure.
   * @param job job
   * @return Job failure reason.
   */
  public static String getFailedReason(V1Job job) {
    return getJobConditions(job)
        .stream()
        .filter(JobWatcher::isJobConditionFailed)
        .findAny()
        .map(V1JobCondition::getReason)
        .orElse(null);
  }

  static Optional<OffsetDateTime> getTimeOfBackoffLimitExceededCondition(V1Job job) {
    return getJobConditions(job)
        .stream()
        .filter(JobWatcher::isJobConditionFailedBackoffLimitExceeded)
        .findFirst()
        .map(V1JobCondition::getLastTransitionTime);
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

    private void addOnModifiedCallback(String jobName, Consumer<V1Job> callback) {
      completeCallbackRegistrations.put(jobName, callback);
    }

    @Override
    void addCallback(String name, Consumer<V1Job> callback) {
      addOnModifiedCallback(name, callback);
    }

    private void removeOnModifiedCallback(String jobName, Consumer<V1Job> callback) {
      completeCallbackRegistrations.remove(jobName, callback);
    }

    @Override
    void removeCallback(String name, Consumer<V1Job> callback) {
      removeOnModifiedCallback(name, callback);
    }

    @Override
    Step createReadAsyncStep(String name, String namespace, String domainUid, ResponseStep<V1Job> responseStep) {
      return checkPodContainerInNamespace(namespace, name,
              new CallBuilder().readJobAsync(name, namespace, domainUid, responseStep));
    }

    private Step checkPodContainerInNamespace(String namespace, String jobName, Step next) {
      return new CallBuilder()
              .withLabelSelectors(LabelConstants.JOBNAME_LABEL)
              .listPodAsync(namespace, new PodContainerCheckResponseStep(jobName, next));
    }

    private class PodContainerCheckResponseStep extends ResponseStep<V1PodList> {

      private String jobName;

      PodContainerCheckResponseStep(String jobName, Step next) {
        super(next);
        this.jobName = jobName;
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1PodList> callResponse) {
        final V1Pod jobPod
                = Optional.ofNullable(callResponse.getResult())
                .map(V1PodList::getItems)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(this::isJobPod)
                .findFirst()
                .orElse(null);

        if (jobPod == null) {
          packet.remove(JOB_POD_INTROSPECT_CONTAINER_TERMINATED);
          return doContinueListOrNext(callResponse, packet, getNext());
        } else {
          addContainerTerminatedMarkerToPacket(jobPod, jobName, packet);
        }

        return doNext(getNext(), packet);
      }

      private void addContainerTerminatedMarkerToPacket(V1Pod jobPod, String jobName, Packet packet) {
        // if the job container (<domin uid>-introspecto) exited, then the check for pod container finished is done

        Optional<V1ContainerStatus> containerStatus = Optional.ofNullable(jobPod)
                .map(V1Pod::getStatus)
                .map(V1PodStatus::getContainerStatuses)
                .orElseGet(Collections::emptyList)
                .stream().filter(v -> v.getState().getTerminated() != null)
                .filter(c -> jobName.equals(c.getName()))
                .findFirst();

        if (!containerStatus.isEmpty()) {
          packet.put(JOB_POD_INTROSPECT_CONTAINER_TERMINATED, JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER);
        }

      }

      private String getName(V1Pod pod) {
        return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
      }

      private boolean isJobPod(V1Pod pod) {
        return getName(pod).startsWith(jobName);
      }

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
      return isJobTimedOut(job);
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

          // The introspect container has exited, setting this so that the job will be considered finished
          // in the WaitDomainIntrospectorJobReadyStep and proceed reading the job pod log and process the result.

          if (isReady(callResponse.getResult()) || callback.didResumeFiber()
                || JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER
                  .equals(packet.get(JOB_POD_INTROSPECT_CONTAINER_TERMINATED))) {
            callback.proceedFromWait(callResponse.getResult());
            return doNext(packet);
          }
          return doDelay(createReadAndIfReadyCheckStep(callback), packet,
                  getWatchBackstopRecheckDelaySeconds(), TimeUnit.SECONDS);
        }
      };
    }
  }

  public static boolean isJobTimedOut(V1Job job) {
    return isFailed(job) && isDeadlineExceeded(job);
  }

  static boolean isDeadlineExceeded(V1Job job) {
    return DEADLINE_EXCEEDED_REASON.equals(getFailedReason(job))
        || isBackoffLimitExceededActiveDeadline(job);
  }

  static boolean isBackoffLimitExceededActiveDeadline(V1Job job) {
    return getTimeOfBackoffLimitExceededCondition(job)
        .map(conditionTime -> getJobRuntime(conditionTime, job))
        .map(jobRuntime -> isLongerThanActiveDeadline(jobRuntime, job))
        .orElse(false);
  }

  static Long getJobRuntime(OffsetDateTime conditionTime, V1Job job) {
    return getJobStatusStartTime(job)
        .map(startTime -> ChronoUnit.SECONDS.between(startTime, conditionTime))
        .orElse(null);
  }

  static Optional<OffsetDateTime> getJobStatusStartTime(V1Job job) {
    return Optional.ofNullable(job)
        .map(V1Job::getStatus)
        .map(V1JobStatus::getStartTime);
  }

  static boolean isLongerThanActiveDeadline(Long jobActiveTime, V1Job job) {
    return getActiveDeadlineSeconds(job).map(activeDeadline -> jobActiveTime >= activeDeadline).orElse(false);
  }

  static Optional<Long> getActiveDeadlineSeconds(V1Job job) {
    return Optional.ofNullable(job)
        .map(V1Job::getSpec)
        .map(V1JobSpec::getActiveDeadlineSeconds);
  }

  static class DeadlineExceededException extends Exception implements IntrospectionJobHolder {
    final V1Job job;

    DeadlineExceededException(V1Job job) {
      super();
      this.job = job;
    }

    @Override
    public V1Job getIntrospectionJob() {
      return job;
    }

    @Override
    public String toString() {
      return LOGGER.formatMessage(
          MessageKeys.JOB_DEADLINE_EXCEEDED_MESSAGE,
          job.getMetadata().getName(),
          job.getSpec().getActiveDeadlineSeconds(),
          getJobStartedSeconds(),
          DomainPresence.getFailureRetryMaxCount());
    }

    private long getJobStartedSeconds() {
      return getJobStatusStartTime(job)
          .map(startTime -> ChronoUnit.SECONDS.between(startTime, SystemClock.now()))
          .orElse(-1L);
    }
  }
}