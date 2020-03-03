// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

/** Watches for Jobs to become Ready or leave Ready state. */
public class JobWatcher extends Watcher<V1Job> implements WatchListener<V1Job> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final Map<String, JobWatcher> JOB_WATCHERS = new HashMap<>();
  private static JobWatcherFactory factory;

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

  /**
   * Returns a cached JobWatcher, if present; otherwise, creates a new one.
   *
   * @param domain the domain for which the job watcher is to be returned
   * @return a cached jobwatcher.
   */
  public static @Nonnull JobWatcher getOrCreateFor(Domain domain) {
    return JOB_WATCHERS.computeIfAbsent(getNamespace(domain), n -> factory.createFor(domain));
  }

  static void removeNamespace(String ns) {
    JOB_WATCHERS.remove(ns);
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

  static void defineFactory(
      ThreadFactory threadFactory,
      WatchTuning tuning,
      Function<String, AtomicBoolean> isNamespaceStopping) {
    factory = new JobWatcherFactory(threadFactory, tuning, isNamespaceStopping);
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
        "JobWatcher.isComplete status of job " + job.getMetadata().getName() + ": " + status);
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

    V1JobStatus status = job.getStatus();
    if (status != null) {
      if (status.getFailed() != null && status.getFailed() > 0) {
        LOGGER.severe(MessageKeys.JOB_IS_FAILED, job.getMetadata().getName());
        return true;
      }
    }
    return false;
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
  public WatchI<V1Job> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
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
  public Step waitForReady(V1Job job, Step next) {
    return new WaitForJobReadyStep(job, next);
  }

  static class JobWatcherFactory {
    private final ThreadFactory threadFactory;
    private final WatchTuning watchTuning;

    private final Function<String, AtomicBoolean> isNamespaceStopping;

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

  private class WaitForJobReadyStep extends WaitForReadyStep<V1Job> {
    private final long jobCreationTime;

    private WaitForJobReadyStep(V1Job job, Step next) {
      super(job, next);
      jobCreationTime = getCreationTime(job);
    }

    // A job is considered ready once it has either successfully completed, or been marked as failed.
    @Override
    boolean isReady(V1Job job) {
      return isComplete(job) || isFailed(job);
    }

    // Ignore modified callbacks from different jobs (identified by having different creation times) or those
    // where the job is not yet ready.
    @Override
    boolean shouldProcessCallback(V1Job job) {
      return hasExpectedCreationTime(job) && isReady(job);
    }

    private boolean hasExpectedCreationTime(V1Job job) {
      return getCreationTime(job) == jobCreationTime;
    }

    private long getCreationTime(V1Job job) {
      return job.getMetadata().getCreationTimestamp().getMillis();
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
    Step createReadAsyncStep(String name, String namespace, ResponseStep<V1Job> responseStep) {
      return new CallBuilder().readJobAsync(name, namespace, responseStep);
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
      return isFailed(job) && "DeadlineExceeded".equals(getFailedReason(job));
    }

    // create an exception to terminate the fiber
    @Override
    Throwable createTerminationException(V1Job job) {
      return new DeadlineExceededException(job);
    }

    @Override
    void logWaiting(String name) {
      LOGGER.info(MessageKeys.WAITING_FOR_JOB_READY, name);
    }
  }

  static class DeadlineExceededException extends Exception {
    final V1Job job;

    DeadlineExceededException(V1Job job) {
      super();
      this.job = job;
    }

    public String toString() {
      return LOGGER.getFormattedMessage(
          MessageKeys.JOB_DEADLINE_EXCEEDED_MESSAGE,
          job.getMetadata().getName(),
          job.getSpec().getActiveDeadlineSeconds(),
          getJobStartedSeconds(),
          DomainPresence.getDomainPresenceFailureRetryMaxCount());
    }

    private long getJobStartedSeconds() {
      if (job.getStatus() != null && job.getStatus().getStartTime() != null) {
        return (System.currentTimeMillis() - job.getStatus().getStartTime().getMillis()) / 1000;
      }
      return -1;
    }
  }
}
