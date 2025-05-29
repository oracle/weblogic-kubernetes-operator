// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.io.Serial;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.IntrospectionJobHolder;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.utils.SystemClock;

/** Watches for Jobs to become Ready or leave Ready state. */
public class JobWatcher extends Watcher<V1Job> implements WatchListener<V1Job> {
  static final WatchListener<V1Job> NULL_LISTENER = r -> {};

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String namespace;

  // Map of Job name to Runnable
  private final Map<String,Consumer<V1Job>> completeCallbackRegistrations = new ConcurrentHashMap<>();

  private JobWatcher(
      CoreDelegate delegate,
      String namespace,
      String initialResourceVersion,
      WatchTuning tuning,
      AtomicBoolean isStopping) {
    super(delegate, initialResourceVersion, tuning, isStopping);
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
   * @param delegate Delegate
   * @param factory thread factory
   * @param ns Namespace
   * @param initialResourceVersion Initial resource version or empty string
   * @param tuning Tuning parameters for the watch, for example watch lifetime
   * @param listener a null listener to keep the same signature as other watcher create methods
   * @param isStopping Stop signal
   * @return Job watcher for the namespace
   */
  public static JobWatcher create(
        CoreDelegate delegate,
        ThreadFactory factory,
        String ns,
        String initialResourceVersion,
        WatchTuning tuning,
        @SuppressWarnings("unused") WatchListener<V1Job> listener,
        AtomicBoolean isStopping) {
    JobWatcher watcher = new JobWatcher(delegate, ns, initialResourceVersion, tuning, isStopping);
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
          if ("Complete".equals(cond.getType()) && "True".equals(cond.getStatus())) {
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

  private static List<V1JobCondition> getJobConditions(@Nonnull V1Job job) {
    return Optional.ofNullable(job.getStatus()).map(V1JobStatus::getConditions).orElse(Collections.emptyList());
  }

  private static boolean isJobConditionFailed(V1JobCondition jobCondition) {
    return "Failed".equals(getType(jobCondition)) && getStatus(jobCondition).equals("True");
  }

  private static String getType(V1JobCondition jobCondition) {
    return Optional.ofNullable(jobCondition).map(V1JobCondition::getType).orElse(null);
  }

  private static String getStatus(V1JobCondition jobCondition) {
    return Optional.ofNullable(jobCondition).map(V1JobCondition::getStatus).orElse("");
  }

  /**
   * Get the reason for job failure.
   * @param job job
   * @return Job failure reason.
   */
  public static String getFailedReason(V1Job job) {
    V1JobStatus status = job.getStatus();
    if (status != null && status.getConditions() != null) {
      for (V1JobCondition cond : status.getConditions()) {
        if (("FailureTarget".equals(cond.getType()) || "Failed".equals(cond.getType()))
              && "True".equals(cond.getStatus())) {
          return cond.getReason();
        }
      }
    }
    return null;
  }

  @Override
  public Watchable<V1Job> initiateWatch(ListOptions options) throws ApiException {
    return delegate.getJobBuilder().watch(namespace,
            options.labelSelector(LabelConstants.DOMAINUID_LABEL + "," + LabelConstants.CREATEDBYOPERATOR_LABEL));
  }

  /**
   * receive response.
   * @param item item
   */
  public void receivedResponse(Watch.Response<V1Job> item) {
    LOGGER.entering();

    LOGGER.fine("JobWatcher.receivedResponse response item: " + item);
    switch (item.type) {
      case "ADDED", "MODIFIED":
        dispatchCallback(getJobName(item), item.object);
        break;
      case "DELETED", "ERROR":
      default:
    }

    LOGGER.exiting();
  }

  private String getJobName(Watch.Response<V1Job> item) {
    return item.object.getMetadata().getName();
  }

  public static class DeadlineExceededException extends Exception implements IntrospectionJobHolder {
    @Serial
    private static final long serialVersionUID  = 1L;

    final transient V1Job job;

    public DeadlineExceededException(V1Job job) {
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
          Optional.ofNullable(job).map(V1Job::getMetadata).map(V1ObjectMeta::getName).orElse(""),
          Optional.ofNullable(job).map(V1Job::getSpec)
                  .map(V1JobSpec::getActiveDeadlineSeconds).map(Object::toString).orElse(""),
          getJobStartedSeconds());
    }

    private long getJobStartedSeconds() {
      if (job.getStatus() != null && job.getStatus().getStartTime() != null) {
        return ChronoUnit.SECONDS.between(job.getStatus().getStartTime(), SystemClock.now());
      }
      return -1;
    }
  }
}
