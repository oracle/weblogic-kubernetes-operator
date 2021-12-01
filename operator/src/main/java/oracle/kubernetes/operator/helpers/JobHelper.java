// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.WatchDomainIntrospectorJobReadyStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.Server;

import static java.time.temporal.ChronoUnit.SECONDS;
import static oracle.kubernetes.operator.DomainFailureReason.Introspection;
import static oracle.kubernetes.operator.DomainSourceType.FromModel;
import static oracle.kubernetes.operator.DomainStatusUpdater.createFailureRelatedSteps;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_DOMAIN_SPEC_GENERATION;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED;
import static oracle.kubernetes.operator.helpers.ConfigMapHelper.readExistingIntrospectorConfigMap;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED_DETAIL;

public class JobHelper {

  private static final int JOB_DELETE_TIMEOUT_SECONDS = 1;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  static final String INTROSPECTOR_LOG_PREFIX = "Introspector Job Log: ";
  private static final String EOL_PATTERN = "\\r?\\n";

  private JobHelper() {
  }

  //----------- for unit testing only ---------

  // Creates the job spec from the specified packet
  static V1JobSpec createJobSpec(Packet packet) {
    return new IntrospectorJobStepContext(packet).createJobSpec(TuningParameters.getInstance());
  }

  static Step deleteDomainIntrospectorJobStep(Step next) {
    return UnitTestAdaptor.create(IntrospectorJobStepContext::deleteIntrospectorJob, next);
  }

  static Step readDomainIntrospectorPodLog(Step next) {
    return UnitTestAdaptor.create(IntrospectorJobStepContext::readNamedPodLog, next);
  }

  static class UnitTestAdaptor extends Step {
    private final Function<IntrospectorJobStepContext,Step> functionConstructor;

    private static Step create(Function<IntrospectorJobStepContext, Step> functionConstructor, Step next) {
      return Step.chain(new UnitTestAdaptor(functionConstructor), next);
    }

    private UnitTestAdaptor(Function<IntrospectorJobStepContext, Step> functionConstructor) {
      this.functionConstructor = functionConstructor;
    }

    @Override
    public NextAction apply(Packet packet) {
      IntrospectorJobStepContext context = new IntrospectorJobStepContext(packet);
      return doNext(Step.chain(functionConstructor.apply(context), getNext()), packet);
    }
  }

  //----------------

  /**
   * Returns true if we will be starting a managed server for this domain.
   *
   * @param info the domain presence info
   */
  static boolean creatingServers(DomainPresenceInfo info) {
    return new StartupComputation(info).isCreatingAServer();
  }

  private static class StartupComputation {
    private final DomainPresenceInfo info;

    private StartupComputation(DomainPresenceInfo info) {
      this.info = info;
    }

    private boolean isCreatingAServer() {
      return domainShouldStart() || willStartACluster() || willStartAServer();
    }

    // If Domain level Server Start Policy = ALWAYS, IF_NEEDED or ADMIN_ONLY we most likely will start a server pod.
    // NOTE: that will not be the case if every cluster and server is marked as NEVER.
    private boolean domainShouldStart() {
      return shouldStart(getDomainSpec().getServerStartPolicy());
    }

    // Returns true if any cluster is configured to start.
    private boolean willStartACluster() {
      return getDomainSpec().getClusters().stream().anyMatch(this::shouldStart);
    }

    // Returns true if any server is configured to start.
    private boolean willStartAServer() {
      return getDomainSpec().getManagedServers().stream().map(Server::getServerStartPolicy).anyMatch(this::shouldStart);
    }

    // Returns true if the specified cluster is configured to start.
    private boolean shouldStart(Cluster cluster) {
      return (shouldStart(cluster.getServerStartPolicy())) && getDomain().getReplicaCount(cluster.getClusterName()) > 0;
    }

    // Returns true if the specified server start policy will allow starting a server.
    private boolean shouldStart(String serverStartPolicy) {
      return !ConfigurationConstants.START_NEVER.equals(serverStartPolicy);
    }

    @Nonnull
    private DomainSpec getDomainSpec() {
      return getDomain().getSpec();
    }

    private Domain getDomain() {
      return info.getDomain();
    }
  }

  /**
   * Returns the first step in the introspection process.
   *
   * Uses the following packet values:
   *  ProcessingConstants.DOMAIN_TOPOLOGY - the domain topology
   *  ProcessingConstants.DOMAIN_RESTART_VERSION - the restart version from the domain
   *  ProcessingConstants.DOMAIN_INPUTS_HASH
   *  ProcessingConstants.DOMAIN_INTROSPECT_VERSION - the introspect version from the old domain spec
   *
   * @param next Next processing step
   */
  public static Step createIntrospectionStartStep(Step next) {
    return new IntrospectionStartStep(next);
  }

  private static class IntrospectionStartStep extends Step {

    IntrospectionStartStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(new IntrospectorJobStepContext(packet).createStartSteps(getNext()), packet);
    }

  }

  private static class IntrospectorJobStepContext extends JobStepContext {

    IntrospectorJobStepContext(Packet packet) {
      super(packet);
    }

    private Step createStartSteps(Step next) {
      return Step.chain(
            DomainValidationSteps.createAdditionalDomainValidationSteps(getJobModelPodSpec()),
            verifyIntrospectorJob(),
            next);
    }

    private Step verifyIntrospectorJob() {
      return new CallBuilder().readJobAsync(getJobName(), getNamespace(), getDomainUid(), createReadJobResponse());
    }

    @Nonnull
    private VerifyIntrospectorJobResponseStep<V1Job> createReadJobResponse() {
      return new VerifyIntrospectorJobResponseStep<>();
    }

    private class VerifyIntrospectorJobResponseStep<T> extends DefaultResponseStep<T> {

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<T> callResponse) {
        V1Job job = (V1Job) callResponse.getResult();
        if ((job != null) && (packet.get(DOMAIN_INTROSPECTOR_JOB) == null)) {
          packet.put(DOMAIN_INTROSPECTOR_JOB, job);
        }

        if (isKnownFailedJob(job) || JobWatcher.isJobTimedOut(job)) {
          return doNext(cleanUpAndReintrospect(getNext()), packet);
        } else if (job != null) {
          return doNext(processIntrospectionResults(getNext()), packet).withDebugComment(job, this::jobDescription);
        } else if (isIntrospectionNeeded(packet)) {
          return doNext(createIntrospectionSteps(getNext()), packet).withDebugComment(packet, this::introspectReason);
        } else {
          return doNext(packet).withDebugComment(packet, this::introspectionNotNeededReason);
        }
      }

      @Nonnull
      private String jobDescription(@Nonnull V1Job job) {
        return "found introspection job " + job.getMetadata().getName()
                         + ", started at " + job.getMetadata().getCreationTimestamp();
      }

      private boolean isKnownFailedJob(V1Job job) {
        return getUid(job).equals(getLastFailedUid());
      }

      @Nonnull
      private String getUid(V1Job job) {
        return Optional.ofNullable(job).map(V1Job::getMetadata).map(V1ObjectMeta::getUid).orElse("");
      }

      @Nullable
      private String getLastFailedUid() {
        return getDomain().getOrCreateStatus().getFailedIntrospectionUid();
      }

      private boolean isIntrospectionNeeded(Packet packet) {
        return getDomainTopology() == null
              || isBringingUpNewDomain(packet)
              || isIntrospectionRequested(packet)
              || isModelInImageUpdate(packet)
              || isIntrospectVersionChanged(packet);
      }

      private String introspectReason(Packet packet) {
        StringBuilder sb = new StringBuilder("introspection needed because ");
        if (getDomainTopology() == null) {
          sb.append("domain topology is null");
        } else if (isBringingUpNewDomain(packet)) {
          sb.append("bringing up new domain");
        } else {
          sb.append("something else");
        }
        return sb.toString();
      }

      private String introspectionNotNeededReason(Packet packet) {
        StringBuilder sb = new StringBuilder("introspection not needed because ");
        if (getNumRunningServers() != 0) {
          sb.append("have running servers: ").append(String.join(", ", getRunningServerNames()));
        } else if (!creatingServers(info)) {
          sb.append("should not be creating servers");
        } else {
          sb.append("domain generation = ").append(getDomainGeneration())
                .append(" and packet last generation is ").append(packet.get(INTROSPECTION_DOMAIN_SPEC_GENERATION));
        }
        return sb.toString();
      }

      @Nonnull
      private Collection<String> getRunningServerNames() {
        return Optional.ofNullable(info).map(DomainPresenceInfo::getServerNames).orElse(Collections.emptyList());
      }

      private boolean isBringingUpNewDomain(Packet packet) {
        return getNumRunningServers() == 0 && creatingServers(info) && isDomainGenerationChanged(packet);
      }

      private int getNumRunningServers() {
        return info.getServerNames().size();
      }

      private boolean isDomainGenerationChanged(Packet packet) {
        return Optional.ofNullable(packet.get(INTROSPECTION_DOMAIN_SPEC_GENERATION))
                .map(gen -> !gen.equals(getDomainGeneration())).orElse(true);
      }

      private String getDomainGeneration() {
        return Optional.ofNullable(getDomain())
              .map(Domain::getMetadata)
              .map(V1ObjectMeta::getGeneration)
              .map(Object::toString)
              .orElse("");
      }

      // Returns true if an introspection was requested. Clears the flag in any case.
      private boolean isIntrospectionRequested(Packet packet) {
        return packet.remove(DOMAIN_INTROSPECT_REQUESTED) != null;
      }

      private boolean isModelInImageUpdate(Packet packet) {
        return isModelInImage() && !getCurrentImageSpecHash().equals(getIntrospectionImageSpecHash(packet));
      }

      private boolean isModelInImage() {
        return getDomain().getDomainHomeSourceType() == FromModel;
      }

      private String getCurrentImageSpecHash() {
        return String.valueOf(ConfigMapHelper.getModelInImageSpecHash(getDomain().getSpec().getImage()));
      }

      private String getIntrospectionImageSpecHash(Packet packet) {
        return (String) packet.get(IntrospectorConfigMapConstants.DOMAIN_INPUTS_HASH);
      }

      private boolean isIntrospectVersionChanged(Packet packet) {
        return Optional.ofNullable(packet.get(INTROSPECTION_STATE_LABEL))
                .map(introspectVersionLabel -> !introspectVersionLabel.equals(getIntrospectVersion())).orElse(false);
      }

      private String getIntrospectVersion() {
        return Optional.ofNullable(getDomain()).map(Domain::getSpec).map(DomainSpec::getIntrospectVersion)
                .orElse("");
      }
    }

    private Step cleanUpAndReintrospect(Step next) {
      return Step.chain(deleteIntrospectorJob(), createIntrospectionSteps(next));
    }

    private Step createIntrospectionSteps(Step next) {
      return Step.chain(
              readExistingIntrospectorConfigMap(getNamespace(), getDomainUid()),
              startNewIntrospection(next));
    }

    @Nonnull
    private Step startNewIntrospection(Step next) {
      return Step.chain(createNewJob(), processIntrospectionResults(next));
    }

    // Returns a chain of steps which read the job pod and decide how to handle it.
    private Step processIntrospectionResults(Step next) {
      return Step.chain(waitForIntrospectionToComplete(), verifyJobPod(), next);
    }

    private Step waitForIntrospectionToComplete() {
      return new WatchDomainIntrospectorJobReadyStep();
    }

    private Step verifyJobPod() {
      return new ReadDomainIntrospectorPodStep();
    }

    private Step readNamedPodLog() {
      return new ReadPodLogStep();
    }

    private class ReadPodLogStep extends Step {

      @Override
      public NextAction apply(Packet packet) {
        String jobPodName = (String) packet.get(ProcessingConstants.JOB_POD_NAME);

        return doNext(readDomainIntrospectorPodLog(jobPodName, getNext()), packet);
      }

      private Step readDomainIntrospectorPodLog(String jobPodName, Step next) {
        return new CallBuilder()
                .readPodLogAsync(
                        jobPodName, getNamespace(), getDomainUid(), new ReadPodLogResponseStep(next));
      }
    }

    private Step deleteIntrospectorJob() {
      return new DeleteDomainIntrospectorJobStep();
    }

    class DeleteDomainIntrospectorJobStep extends Step {

      @Override
      public NextAction apply(Packet packet) {
        logJobDeleted(getDomainUid(), getNamespace(), getJobName(), packet);
        return doNext(new CallBuilder().withTimeoutSeconds(JOB_DELETE_TIMEOUT_SECONDS)
                .deleteJobAsync(
                      getJobName(),
                        getNamespace(),
                        getDomainUid(),
                        new V1DeleteOptions().propagationPolicy("Foreground"),
                        new DefaultResponseStep<>(getNext())), packet);
      }
    }

    private Step createIntrospectorConfigMap() {
      return ConfigMapHelper.createIntrospectorConfigMapStep(null);
    }

    private class ReadPodLogResponseStep extends ResponseStep<String> {
      public static final String INTROSPECTION_FAILED = "INTROSPECTION_FAILED";
      private StringBuilder logMessage = new StringBuilder();
      private final List<String> severeStatuses = new ArrayList<>();

      ReadPodLogResponseStep(Step nextStep) {
        super(nextStep);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<String> callResponse) {
        Optional.ofNullable(callResponse.getResult()).ifPresent(result -> processIntrospectionResult(packet, result));

        final V1Job domainIntrospectorJob = packet.getValue(DOMAIN_INTROSPECTOR_JOB);
        if (JobWatcher.isComplete(domainIntrospectorJob)) {
          return doNext(packet);
        } else {
          return handleFailure(packet, domainIntrospectorJob);
        }
      }

      private void processIntrospectionResult(Packet packet, String result) {
        LOGGER.fine("+++++ ReadDomainIntrospectorPodLogResponseStep: \n" + result);
        convertJobLogsToOperatorLogs(result);
        if (!severeStatuses.isEmpty()) {
          updateStatusSynchronously();
        }
        packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT, result);
        MakeRightDomainOperation.recordInspection(packet);
      }

      private NextAction handleFailure(Packet packet, V1Job domainIntrospectorJob) {
        Step nextSteps = null;
        Optional.ofNullable(domainIntrospectorJob).ifPresent(job -> logIntrospectorFailure(packet, job));

        if (!severeStatuses.isEmpty()) {
          nextSteps = Step.chain(
                  createIntrospectionFailureRelatedSteps(domainIntrospectorJob),
                  getNextStep(packet, domainIntrospectorJob), nextSteps);
        } else {
          nextSteps = Step.chain(
                  createFailureRelatedSteps(Introspection, onSeparateLines(severeStatuses)),
                  getNextStep(packet, domainIntrospectorJob), nextSteps);
        }

        return doNext(nextSteps, packet);
      }

      private Step createIntrospectionFailureRelatedSteps(V1Job domainIntrospectorJob) {
        return DomainStatusUpdater.createIntrospectionFailureRelatedSteps(
                Introspection, onSeparateLines(severeStatuses), domainIntrospectorJob);
      }

      @Nullable
      private Step getNextStep(Packet packet, V1Job domainIntrospectorJob) {
        if (isRecheckIntervalExceeded(domainIntrospectorJob)) {
          packet.put(DOMAIN_INTROSPECT_REQUESTED, INTROSPECTION_FAILED);
          return getNext();
        } else {
          return null;
        }
      }

      // Returns true if the job is left over from an earlier make-right, and we may now delete it.
      private boolean isRecheckIntervalExceeded(V1Job domainIntrospectorJob) {
        final int retryInterval = TuningParameters.getInstance().getMainTuning().domainPresenceRecheckIntervalSeconds;
        return SystemClock.now().isAfter(getJobCreationTime(domainIntrospectorJob).plus(retryInterval, SECONDS));
      }


      private OffsetDateTime getJobCreationTime(V1Job domainIntrospectorJob) {
        return Optional.ofNullable(domainIntrospectorJob)
              .map(V1Job::getMetadata)
              .map(V1ObjectMeta::getCreationTimestamp)
              .orElse(OffsetDateTime.now());
      }

      // Parse log messages out of a Job Log
      //  - assumes each job log message starts with '@['
      //  - assumes any lines that don't start with '@[' are part
      //    of the previous log message
      //  - ignores all lines in the log up to the first line that starts with '@['
      private void convertJobLogsToOperatorLogs(String jobLogs) {
        for (String line : jobLogs.split(EOL_PATTERN)) {
          if (line.startsWith("@[")) {
            logToOperator();
            logMessage = new StringBuilder(INTROSPECTOR_LOG_PREFIX).append(line.trim());
          } else if (logMessage.length() > 0) {
            logMessage.append(System.lineSeparator()).append(line.trim());
          }
        }
        logToOperator();
      }

      private void logToOperator() {
        if (logMessage.length() == 0) {
          return;
        }

        String logMsg = logMessage.toString();
        switch (getLogLevel(logMsg)) {
          case "SEVERE":
            addSevereStatus(logMsg); // fall through
          case "ERROR":
            LOGGER.severe(logMsg);
            break;
          case "WARNING":
            LOGGER.warning(logMsg);
            break;
          case "INFO":
            LOGGER.info(logMsg);
            break;
          case "FINER":
            LOGGER.finer(logMsg);
            break;
          case "FINEST":
            LOGGER.finest(logMsg);
            break;
          case "FINE":
          default:
            LOGGER.fine(logMsg);
            break;
        }
      }

      private void addSevereStatus(String logMsg) {
        int index = logMsg.toUpperCase().lastIndexOf("[SEVERE]") + "[SEVERE]".length();
        severeStatuses.add(logMsg.substring(index).trim());
      }

      private String getLogLevel(String logMsg) {
        String regExp = ".*\\[(SEVERE|ERROR|WARNING|INFO|FINE|FINER|FINEST)].*";
        return getFirstLine(logMsg).toUpperCase().replaceAll(regExp, "$1");
      }

      private String getFirstLine(String logMsg) {
        return logMsg.split(EOL_PATTERN)[0];
      }

      private void updateStatusSynchronously() {
        DomainStatusPatch.updateSynchronously(getDomain(), Introspection, onSeparateLines(severeStatuses));
      }

      private String onSeparateLines(List<String> lines) {
        return String.join(System.lineSeparator(), lines);
      }
    }

    // A step which records the name of the introspector pod in the packet at JOB_POD_NAME.
    private class ReadDomainIntrospectorPodStep extends Step {

      @Override
      public NextAction apply(Packet packet) {
        if (getCurrentIntrospectFailureRetryCount() > 0) {
          reportIntrospectJobFailure();
        }

        return doNext(listPodsInNamespace(getNamespace(), getNext()), packet);
      }

      @Nonnull
      private Integer getCurrentIntrospectFailureRetryCount() {
        return Optional.of(getDomain())
            .map(Domain::getStatus)
            .map(DomainStatus::getIntrospectJobFailureCount)
            .orElse(0);
      }

      private void reportIntrospectJobFailure() {
        LOGGER.info(MessageKeys.INTROSPECT_JOB_FAILED,
            getDomainUid(),
            TuningParameters.getInstance().getMainTuning().domainPresenceRecheckIntervalSeconds,
            getCurrentIntrospectFailureRetryCount());
      }

      private Step listPodsInNamespace(String namespace, Step next) {
        return new CallBuilder()
              .withLabelSelectors(LabelConstants.JOBNAME_LABEL)
              .listPodAsync(namespace, new PodListResponseStep(next));
      }
    }

    private class PodListResponseStep extends ResponseStep<V1PodList> {

      PodListResponseStep(Step next) {
        super(next);
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
          return doContinueListOrNext(callResponse, packet, processIntrospectorPodLog(getNext()));
        } else if (hasImagePullFailure(jobPod) || isJobPodTimedOut(jobPod)) {
          return doNext(cleanUpAndReintrospect(getNext()), packet);
        } else {
          recordJobPodName(packet, getName(jobPod));
          return doNext(processIntrospectorPodLog(getNext()), packet);
        }
      }

      private boolean isJobPodTimedOut(V1Pod jobPod) {
        return "DeadlineExceeded".equals(getJobPodStatusReason(jobPod));
      }

      private String getJobPodStatusReason(V1Pod jobPod) {
        return Optional.ofNullable(jobPod.getStatus()).map(V1PodStatus::getReason).orElse(null);
      }

      // Returns a chain of steps which read the pod log and create a config map.
      private Step processIntrospectorPodLog(Step next) {
        return Step.chain(readNamedPodLog(), deleteIntrospectorJob(), createIntrospectorConfigMap(), next);
      }

      private String getName(V1Pod pod) {
        return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
      }

      private boolean isJobPod(V1Pod pod) {
        return getName(pod).startsWith(getJobName());
      }

      private boolean hasImagePullFailure(V1Pod pod) {
        return Optional.ofNullable(getJobPodContainerWaitingReason(pod))
              .map(s -> s.contains("ErrImagePull") || s.contains("ImagePullBackOff"))
              .orElse(false);
      }

      private String getJobPodContainerWaitingReason(V1Pod pod) {
        return Optional.ofNullable(pod).map(V1Pod::getStatus)
              .map(V1PodStatus::getContainerStatuses).map(statuses -> statuses.get(0))
              .map(V1ContainerStatus::getState).map(V1ContainerState::getWaiting)
              .map(V1ContainerStateWaiting::getReason).orElse(null);
      }

      private void recordJobPodName(Packet packet, String podName) {
        packet.put(ProcessingConstants.JOB_POD_NAME, podName);
      }
    }
  }

  private static void logIntrospectorFailure(Packet packet, V1Job domainIntrospectorJob) {
    Boolean logged = (Boolean) packet.get(ProcessingConstants.INTROSPECTOR_JOB_FAILURE_LOGGED);
    String jobPodName = (String) packet.get(ProcessingConstants.JOB_POD_NAME);
    if (logged == null || !logged) {
      packet.put(ProcessingConstants.INTROSPECTOR_JOB_FAILURE_LOGGED, Boolean.TRUE);
      LOGGER.info(INTROSPECTOR_JOB_FAILED,
          Objects.requireNonNull(domainIntrospectorJob.getMetadata()).getName(),
          domainIntrospectorJob.getMetadata().getNamespace(),
          domainIntrospectorJob.getStatus(),
          jobPodName);
      LOGGER.fine(INTROSPECTOR_JOB_FAILED_DETAIL,
          domainIntrospectorJob.getMetadata().getNamespace(),
          domainIntrospectorJob.getMetadata().getName(),
          domainIntrospectorJob.toString());
    }
  }

  static void logJobDeleted(String domainUid, String namespace, String jobName, Packet packet) {
    V1Job domainIntrospectorJob =
            (V1Job) packet.remove(DOMAIN_INTROSPECTOR_JOB);

    packet.remove(ProcessingConstants.INTROSPECTOR_JOB_FAILURE_LOGGED);
    if (domainIntrospectorJob != null
            && !JobWatcher.isComplete(domainIntrospectorJob)) {
      logIntrospectorFailure(packet, domainIntrospectorJob);
    }
    packet.remove(ProcessingConstants.JOB_POD_NAME);

    LOGGER.fine(getJobDeletedMessageKey(), domainUid, namespace, jobName);
  }

  static String getJobDeletedMessageKey() {
    return MessageKeys.JOB_DELETED;
  }

}
