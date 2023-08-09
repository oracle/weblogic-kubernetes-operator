// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.IntrospectionStatus;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.WatchDomainIntrospectorJobReadyStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.Server;

import static java.time.temporal.ChronoUnit.SECONDS;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INTROSPECTION_INCOMPLETE;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTOR_JOB_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTOR_JOB_FAILED_DETAIL;
import static oracle.kubernetes.operator.DomainSourceType.FROM_MODEL;
import static oracle.kubernetes.operator.DomainStatusUpdater.createIntrospectionFailureSteps;
import static oracle.kubernetes.operator.DomainStatusUpdater.createRemoveSelectedFailuresStep;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_DOMAIN_SPEC_GENERATION;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTION_COMPLETE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED;
import static oracle.kubernetes.operator.ProcessingConstants.INTROSPECTOR_JOB_FAILURE_THROWABLE;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_FLUENTD_CONTAINER_TERMINATED;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER;
import static oracle.kubernetes.operator.helpers.ConfigMapHelper.readExistingIntrospectorConfigMap;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;

public class JobHelper {

  private static final int JOB_DELETE_TIMEOUT_SECONDS = 1;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  public static final String INTROSPECTOR_LOG_PREFIX = "Introspector Job Log: ";
  private static final String EOL_PATTERN = "\\r?\\n";

  private JobHelper() {
  }

  //----------- for unit testing only ---------

  // Creates the job spec from the specified packet
  static V1JobSpec createJobSpec(Packet packet) {
    return new IntrospectorJobStepContext(packet).createJobSpec();
  }

  static Step deleteDomainIntrospectorJobStep(Step next) {
    return UnitTestAdaptor.create(IntrospectorJobStepContext::deleteIntrospectorJob, next);
  }

  public static Step readDomainIntrospectorPodLog(Step next) {
    return UnitTestAdaptor.create(IntrospectorJobStepContext::readNamedPodLog, next);
  }

  public static Step readIntrospectorResults(Step next) {
    return UnitTestAdaptor.create(IntrospectorJobStepContext::readIntrospectorResults, next);
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

    // If Domain level Server Start Policy = Always, IfNeeded or AdminOnly we most likely will start a server pod.
    // NOTE: that will not be the case if every cluster and server is marked as Never.
    private boolean domainShouldStart() {
      return shouldStart(getDomainSpec().getServerStartPolicy());
    }

    // Returns true if any cluster is configured to start.
    private boolean willStartACluster() {
      return getClusters().stream().map(ClusterResource::getSpec).anyMatch(this::shouldStart);
    }

    // Returns true if any server is configured to start.
    private boolean willStartAServer() {
      return getDomainSpec().getManagedServers().stream().map(Server::getServerStartPolicy).anyMatch(this::shouldStart);
    }

    // Returns true if the specified cluster is configured to start.
    private boolean shouldStart(ClusterSpec clusterSpec) {
      return (shouldStart(clusterSpec.getServerStartPolicy()))
              && info.getReplicaCount(clusterSpec.getClusterName()) > 0;
    }

    // Returns true if the specified server start policy will allow starting a server.
    private boolean shouldStart(ServerStartPolicy serverStartPolicy) {
      return !ServerStartPolicy.NEVER.equals(serverStartPolicy);
    }

    @Nonnull
    private DomainSpec getDomainSpec() {
      return getDomain().getSpec();
    }

    private DomainResource getDomain() {
      return info.getDomain();
    }

    private List<ClusterResource> getClusters() {
      return info.getReferencedClusters();
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
   */
  public static Step createIntrospectionStartStep() {
    return new IntrospectionStartStep();
  }

  private static class IntrospectionStartStep extends Step {

    IntrospectionStartStep() {
      super();
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
            DomainValidationSteps.createValidateDomainTopologySteps(next));
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

        if (isKnownFailedJob(job) || JobWatcher.isJobTimedOut(job) || isInProgressJobOutdated(job)) {
          return doNext(cleanUpAndReintrospect(getNext()), packet);
        } else if (job != null) {
          return doNext(processExistingIntrospectorJob(getNext()), packet).withDebugComment(job, this::jobDescription);
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

      private boolean isInProgressJobOutdated(V1Job job) {
        return Optional.ofNullable(job)
            .map(j -> hasNotCompleted(j) && (hasAnyImageChanged(j) || hasIntrospectVersionChanged(j)))
            .orElse(false);
      }

      private boolean hasNotCompleted(V1Job job) {
        return job != null && !JobWatcher.isComplete(job);
      }

      private boolean hasAnyImageChanged(V1Job job) {
        return hasImageChanged(job) || hasAuxiliaryImageChanged(job);
      }

      private boolean hasImageChanged(@Nonnull V1Job job) {
        return !Objects.equals(getImageFromJob(job), getJobModelPodSpecImage());
      }

      private boolean hasAuxiliaryImageChanged(@Nonnull V1Job job) {
        return ! getSortedJobModelPodSpecAuxiliaryImages().equals(getSortedAuxiliaryImagesFromJob(job));
      }

      private boolean hasIntrospectVersionChanged(@Nonnull V1Job job) {
        return !Objects.equals(getIntrospectVersionLabelFromJob(job),
            getIntrospectVersionLabelFromJob(getJobModel()));
      }

      String getImageFromJob(V1Job job) {
        return getPodSpecFromJob(job).map(this::getImageFromPodSpec).orElse(null);
      }

      List<String> getSortedAuxiliaryImagesFromJob(V1Job job) {
        return getAuxiliaryImagesFromJob(job).sorted().collect(Collectors.toList());
      }

      Stream<String> getAuxiliaryImagesFromJob(V1Job job) {
        return getPodSpecFromJob(job).map(this::getAuxiliaryImagesFromPodSpec).orElse(Stream.empty());
      }

      Optional<V1PodSpec> getPodSpecFromJob(V1Job job) {
        return Optional.ofNullable(job)
            .map(V1Job::getSpec)
            .map(V1JobSpec::getTemplate)
            .map(V1PodTemplateSpec::getSpec);
      }

      @Nullable
      String getImageFromPodSpec(@Nonnull V1PodSpec pod) {
        return getContainer(pod)
            .map(V1Container::getImage)
            .orElse(null);
      }

      Stream<String> getAuxiliaryImagesFromPodSpec(@Nonnull V1PodSpec pod) {
        return getAuxiliaryContainers(pod)
            .map(V1Container::getImage);
      }

      @Nullable
      String getJobModelPodSpecImage() {
        return Optional.ofNullable(getJobModelPodSpec()).map(this::getImageFromPodSpec).orElse(null);
      }

      List<String> getSortedJobModelPodSpecAuxiliaryImages() {
        return getJobModelPodSpecAuxiliaryImages().sorted().collect(Collectors.toList());
      }

      Stream<String> getJobModelPodSpecAuxiliaryImages() {
        return Optional.ofNullable(getJobModelPodSpec())
            .map(this::getAuxiliaryImagesFromPodSpec)
            .orElse(Stream.empty());
      }

      @Nullable
      String getIntrospectVersionLabelFromJob(V1Job job) {
        return Optional.ofNullable(job)
            .map(V1Job::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .map(m -> m.get(INTROSPECTION_STATE_LABEL))
            .orElse(null);
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
        } else if (isIntrospectionRequested(packet)) {
          sb.append("introspection was requested");
        } else if (isIntrospectVersionChanged(packet)) {
          sb.append("introspection version was changed");
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
              .map(DomainResource::getMetadata)
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
        return getDomain().getDomainHomeSourceType() == FROM_MODEL;
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
        return Optional.ofNullable(getDomain()).map(DomainResource::getSpec).map(DomainSpec::getIntrospectVersion)
                .orElse("");
      }
    }

    private Step cleanUpAndReintrospect(Step next) {
      return Step.chain(deleteIntrospectorJob(), createIntrospectionSteps(next));
    }

    private Step createIntrospectionSteps(Step next) {
      return Step.chain(
              readExistingIntrospectorConfigMap(),
              createNewJob(),
              processExistingIntrospectorJob(next));
    }

    // Returns a chain of steps which read the job pod and decide how to handle it.
    private Step processExistingIntrospectorJob(Step next) {
      return Step.chain(waitForIntrospectionToComplete(), readIntrospectorResults(), next);
    }

    private Step waitForIntrospectionToComplete() {
      return new WatchDomainIntrospectorJobReadyStep();
    }

    private Step readIntrospectorResults() {
      return new ReadDomainIntrospectorPodStep();
    }

    private Step readNamedPodLog() {
      return new ReadPodLogStep();
    }

    private class ReadPodLogStep extends Step {

      @Override
      public NextAction apply(Packet packet) {
        String containerName;
        V1Pod jobPod = (V1Pod) packet.get(ProcessingConstants.JOB_POD);
        V1ContainerStatus status = getJobPodContainerStatus(jobPod);
        if (status != null && Boolean.TRUE == status.getStarted() && Boolean.TRUE == status.getReady()) {
          containerName = getContainerName();
        } else {
          containerName = getInitContainerName(jobPod);
        }

        String jobPodName = JobHelper.getName(jobPod);

        return doNext(readDomainIntrospectorPodLog(jobPodName, containerName, getNext()), packet);
      }

      private V1ContainerStatus getJobPodContainerStatus(V1Pod jobPod) {
        return Optional.ofNullable(getContainerStatuses(jobPod))
            .map(cs -> cs.stream().findFirst().orElse(null)).orElse(null);
      }

      private List<V1ContainerStatus> getContainerStatuses(V1Pod jobPod) {
        return Optional.ofNullable(jobPod.getStatus()).map(s -> s.getContainerStatuses()).orElse(null);
      }

      private String getContainerName() {
        return getDomain().getDomainUid() + "-introspector";
      }

      private String getInitContainerName(V1Pod jobPod) {
        return Optional.ofNullable(getInitContainerStatuses(jobPod))
            .map(is -> is.stream().filter(cs -> hasError(cs)).findFirst().map(c  -> c.getName())
                .orElse(getContainerName()))
            .orElse(getContainerName());
      }

      private boolean hasError(V1ContainerStatus cs) {
        return Optional.ofNullable(cs.getState()).map(s -> s.getTerminated())
            .map(t -> t.getReason()).map(r -> r.equals("Error")).orElse(false);
      }

      private List<V1ContainerStatus> getInitContainerStatuses(V1Pod jobPod) {
        return Optional.ofNullable(jobPod.getStatus()).map(s -> s.getInitContainerStatuses()).orElse(null);
      }

      private Step readDomainIntrospectorPodLog(String jobPodName, String containerName, Step next) {
        return new CallBuilder()
                .withContainerName(containerName)
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

    private static class ReadPodLogResponseStep extends ResponseStep<String> {
      public static final String INTROSPECTION_FAILED = "INTROSPECTION_FAILED";
      private StringBuilder logMessage = new StringBuilder();
      private final List<String> severeStatuses = new ArrayList<>();

      ReadPodLogResponseStep(Step nextStep) {
        super(nextStep);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<String> callResponse) {
        Optional.ofNullable(callResponse.getResult()).ifPresent(result -> processIntrospectionResult(packet, result));

        addFluentdContainerLogAsSevereStatus(packet);

        final V1Job domainIntrospectorJob = packet.getValue(DOMAIN_INTROSPECTOR_JOB);
        if (severeStatuses.isEmpty()) {
          if (!isDomainIntrospectionComplete(callResponse)) {
            LOGGER.severe(DOMAIN_INTROSPECTION_INCOMPLETE, callResponse.getResult());
            severeStatuses.add(LOGGER.formatMessage(DOMAIN_INTROSPECTION_INCOMPLETE, callResponse.getResult()));
            return handleFailure(packet, domainIntrospectorJob);
          }
          return doNext(createRemoveSelectedFailuresStep(getNext(), INTROSPECTION), packet);
        } else {
          return handleFailure(packet, domainIntrospectorJob);
        }
      }

      @Nonnull
      private Boolean isDomainIntrospectionComplete(CallResponse<String> callResponse) {
        return Optional.ofNullable(callResponse).map(CallResponse::getResult)
            .map(r -> r.contains(DOMAIN_INTROSPECTION_COMPLETE)).orElse(false);
      }

      // Note: fluentd container log can be huge, may not be a good idea to read the container log.
      //  Just set a flag and let the user know they can check the container log to determine unlikely
      //  starting error, most likely a very bad formatted configuration.
      private void addFluentdContainerLogAsSevereStatus(Packet packet) {
        Optional.ofNullable(packet.<String>getValue(JOB_POD_FLUENTD_CONTAINER_TERMINATED))
            .ifPresent(severeStatuses::add);
      }

      private void processIntrospectionResult(Packet packet, String result) {
        LOGGER.fine("+++++ ReadDomainIntrospectorPodLogResponseStep: \n" + result);
        convertJobLogsToOperatorLogs(result);
        packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT, result);
        MakeRightDomainOperation.recordInspection(packet);
      }

      private NextAction handleFailure(Packet packet, V1Job domainIntrospectorJob) {
        Optional.ofNullable(domainIntrospectorJob).ifPresent(job -> logIntrospectorFailure(packet, job));

        return doNext(Step.chain(
            createIntrospectionFailureSteps(onSeparateLines(severeStatuses), domainIntrospectorJob),
            getNextStep(packet, domainIntrospectorJob), null), packet);
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
        final int retryInterval = TuningParameters.getInstance().getDomainPresenceRecheckIntervalSeconds();
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

      private String onSeparateLines(List<String> lines) {
        return String.join(System.lineSeparator(), lines);
      }
    }

    // A step which records the name of the introspector pod in the packet at JOB_POD_NAME.
    private class ReadDomainIntrospectorPodStep extends Step {

      @Override
      public NextAction apply(Packet packet) {
        Throwable t = (Throwable) packet.remove(INTROSPECTOR_JOB_FAILURE_THROWABLE);
        if (t != null) {
          return doTerminate(t, packet);
        }
        return doNext(listPodsInNamespace(getNamespace(), getNext()), packet);
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

      private void addContainerTerminatedMarkerToPacket(V1Pod jobPod, String jobName, Packet packet) {
        final FluentdContainer fluentdContainer = new FluentdContainer(jobPod);
        if (fluentdContainer.isTerminated()) {
          fluentdContainer.logAndAddToPacket(packet);
        } else if (new JobPodContainer(jobPod, jobName).isTerminatedWithoutError()) {
          packet.put(JOB_POD_INTROSPECT_CONTAINER_TERMINATED, JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER);
        }
      }

      private class FluentdContainer {
        private final V1Pod jobPod;
        private final V1ContainerStatus matchingStatus;

        FluentdContainer(V1Pod jobPod) {
          this.matchingStatus = Optional.ofNullable(jobPod)
              .map(V1Pod::getStatus)
              .map(V1PodStatus::getContainerStatuses).orElseGet(Collections::emptyList).stream()
              .filter(this::isTerminatedFluentdContainerStatus)
              .findFirst()
              .orElse(null);
          this.jobPod = jobPod;
        }

        private boolean isTerminatedFluentdContainerStatus(V1ContainerStatus containerStatus) {
          return FLUENTD_CONTAINER_NAME.equals(containerStatus.getName())
              && containerStatus.getState() != null
              && containerStatus.getState().getTerminated() != null;
        }

        boolean isTerminated() {
          return matchingStatus != null;
        }

        private void logAndAddToPacket(Packet packet) {
          LOGGER.severe(INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED, getParameters());
          packet.put(JOB_POD_FLUENTD_CONTAINER_TERMINATED,
              LOGGER.formatMessage(INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED, getParameters()));
        }

        private Object[] getParameters() {
          return new Object[] {
              jobPod.getMetadata().getName(),
              jobPod.getMetadata().getNamespace(),
              matchingStatus.getState().getTerminated().getExitCode(),
              matchingStatus.getState().getTerminated().getReason(),
              matchingStatus.getState().getTerminated().getMessage()
          };
        }
      }

      class JobPodContainer {
        private final V1Pod jobPod;
        private final String jobName;

        JobPodContainer(V1Pod jobPod, String jobName) {
          this.jobPod = jobPod;
          this.jobName = jobName;
        }

        boolean isTerminatedWithoutError() {
          return getMatchingStatus() != null;
        }

        private V1ContainerStatus getMatchingStatus() {
          return Optional.ofNullable(jobPod)
              .map(V1Pod::getStatus)
              .map(V1PodStatus::getContainerStatuses).orElseGet(Collections::emptyList).stream()
              .filter(this::isTerminatedJobContainerStatus)
              .findFirst()
              .orElse(null);
        }

        private boolean isTerminatedJobContainerStatus(V1ContainerStatus status) {
          return jobName.equals(status.getName()) && getTerminatedExitCode(status) == 0;
        }

        @Nonnull
        private Integer getTerminatedExitCode(V1ContainerStatus status) {
          return Optional.ofNullable(status.getState())
              .map(V1ContainerState::getTerminated)
              .map(V1ContainerStateTerminated::getExitCode)
              .orElse(-1);
        }
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
        } else if (hasImagePullError(jobPod) || initContainersHaveImagePullError(jobPod)) {
          return doNext(cleanUpAndReintrospect(getNext()), packet);
        } else if (isJobPodTimedOut(jobPod)) {
          // process job pod timed out same way as job timed out, which is to
          // terminate current fiber
          return onFailureNoRetry(packet, callResponse);
        } else {
          addContainerTerminatedMarkerToPacket(jobPod, getJobName(), packet);
          recordJobPod(packet, jobPod);
          return doNext(processIntrospectorPodLog(getNext()), packet);
        }
      }

      @Override
      protected Throwable createTerminationException(Packet packet,
          CallResponse<V1PodList> callResponse) {
        return new JobWatcher.DeadlineExceededException((V1Job) packet.get(DOMAIN_INTROSPECTOR_JOB));
      }

      private boolean isJobPodTimedOut(V1Pod jobPod) {
        return "DeadlineExceeded".equals(getJobPodStatusReason(jobPod));
      }

      private String getJobPodStatusReason(V1Pod jobPod) {
        return Optional.ofNullable(jobPod.getStatus()).map(V1PodStatus::getReason).orElse(null);
      }

      private Step createIntrospectorConfigMap() {
        return ConfigMapHelper.createIntrospectorConfigMapStep(null);
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

      private boolean hasImagePullError(V1Pod pod) {
        return Optional.ofNullable(getJobPodContainerWaitingReason(pod))
              .map(IntrospectionStatus::isImagePullError)
              .orElse(false);
      }

      private String getJobPodContainerWaitingReason(V1Pod pod) {
        return Optional.ofNullable(pod).map(V1Pod::getStatus)
              .map(V1PodStatus::getContainerStatuses).map(statuses -> statuses.get(0))
              .map(V1ContainerStatus::getState).map(V1ContainerState::getWaiting)
              .map(V1ContainerStateWaiting::getReason).orElse(null);
      }

      private boolean initContainersHaveImagePullError(V1Pod pod) {
        return Optional.ofNullable(getInitContainerStatuses(pod))
                .map(statuses -> statuses.stream()
                        .map(V1ContainerStatus::getState).filter(Objects::nonNull)
                        .map(V1ContainerState::getWaiting).filter(Objects::nonNull)
                        .map(V1ContainerStateWaiting::getReason)
                        .anyMatch(IntrospectionStatus::isImagePullError))
                .orElse(false);

      }

      private List<V1ContainerStatus> getInitContainerStatuses(V1Pod pod) {
        return Optional.ofNullable(pod.getStatus()).map(V1PodStatus::getInitContainerStatuses).orElse(null);
      }

      private void recordJobPod(Packet packet, V1Pod jobPod) {
        packet.put(ProcessingConstants.JOB_POD, jobPod);
      }
    }
  }

  private static void logIntrospectorFailure(Packet packet, V1Job domainIntrospectorJob) {
    Boolean logged = (Boolean) packet.get(ProcessingConstants.INTROSPECTOR_JOB_FAILURE_LOGGED);
    V1Pod jobPod = (V1Pod) packet.get(ProcessingConstants.JOB_POD);
    if (logged == null || !logged) {
      packet.put(ProcessingConstants.INTROSPECTOR_JOB_FAILURE_LOGGED, Boolean.TRUE);
      LOGGER.info(INTROSPECTOR_JOB_FAILED,
          Objects.requireNonNull(domainIntrospectorJob.getMetadata()).getName(),
          domainIntrospectorJob.getMetadata().getNamespace(),
          domainIntrospectorJob.getStatus(),
          getName(jobPod));
      LOGGER.fine(INTROSPECTOR_JOB_FAILED_DETAIL,
          domainIntrospectorJob.getMetadata().getNamespace(),
          domainIntrospectorJob.getMetadata().getName(),
          domainIntrospectorJob.toString());
    }
  }

  private static String getName(V1Pod pod) {
    return Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
  }

  static void logJobDeleted(String domainUid, String namespace, String jobName, Packet packet) {
    V1Job domainIntrospectorJob =
            (V1Job) packet.remove(DOMAIN_INTROSPECTOR_JOB);

    packet.remove(ProcessingConstants.INTROSPECTOR_JOB_FAILURE_LOGGED);
    if (domainIntrospectorJob != null
        && hasStatusAndCondition(domainIntrospectorJob) && !JobWatcher.isComplete(domainIntrospectorJob)) {
      logIntrospectorFailure(packet, domainIntrospectorJob);
    }
    packet.remove(ProcessingConstants.JOB_POD);

    LOGGER.fine(getJobDeletedMessageKey(), domainUid, namespace, jobName);
  }

  private static boolean hasStatusAndCondition(V1Job job) {
    return job.getStatus() != null && job.getStatus().getConditions() != null;
  }

  static String getJobDeletedMessageKey() {
    return MessageKeys.JOB_DELETED;
  }

}
