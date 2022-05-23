// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.WatchDomainIntrospectorJobReadyStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;

import static java.time.temporal.ChronoUnit.SECONDS;
import static oracle.kubernetes.operator.DomainSourceType.FromModel;
import static oracle.kubernetes.operator.DomainStatusUpdater.INSPECTING_DOMAIN_PROGRESS_REASON;
import static oracle.kubernetes.operator.DomainStatusUpdater.createProgressingStartedEventStep;
import static oracle.kubernetes.operator.DomainStatusUpdater.recordLastIntrospectJobProcessedUid;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_DOMAIN_SPEC_GENERATION;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_FLUENTD_CONTAINER_TERMINATED;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONTAINER_NAME;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED_DETAIL;

public class JobHelper {

  static final String START_TIME = "WlsRetriever-startTime";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  static final String INTROSPECTOR_LOG_PREFIX = "Introspector Job Log: ";
  private static final String EOL_PATTERN = "\\r?\\n";

  private JobHelper() {
  }

  static String createJobName(String domainUid) {
    return LegalNames.toJobIntrospectorName(domainUid);
  }

  /**
   * Factory for {@link Step} that creates WebLogic domain introspector job.
   * Uses the following packet values:
   *  ProcessingConstants.DOMAIN_TOPOLOGY - the domain topology
   *  ProcessingConstants.DOMAIN_RESTART_VERSION - the restart version from the domain
   *  ProcessingConstants.DOMAIN_INPUTS_HASH
   *  ProcessingConstants.DOMAIN_INTROSPECT_VERSION - the introspect version from the old domain spec
   *
   * @param next Next processing step
   * @return Step for creating job
   */
  public static Step createDomainIntrospectorJobStep(Step next) {

    return new DomainIntrospectorJobStep(next);
  }

  private static boolean runIntrospector(Packet packet, DomainPresenceInfo info) {
    WlsDomainConfig topology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    LOGGER.fine("runIntrospector topology: " + topology);
    LOGGER.fine("runningServersCount: " + runningServersCount(info));
    LOGGER.fine("creatingServers: " + creatingServers(info));
    LOGGER.fine("isModelInImageUpdate: " + isModelInImageUpdate(packet, info));
    return topology == null
          || isBringingUpNewDomain(packet, info)
          || checkIfIntrospectionRequestedAndReset(packet)
          || isModelInImageUpdate(packet, info)
          || isIntrospectVersionChanged(packet, info);
  }

  private static boolean isBringingUpNewDomain(Packet packet, DomainPresenceInfo info) {
    return runningServersCount(info) == 0 && creatingServers(info) && isGenerationChanged(packet, info);
  }

  private static boolean checkIfIntrospectionRequestedAndReset(Packet packet) {
    return packet.remove(DOMAIN_INTROSPECT_REQUESTED) != null;
  }

  private static boolean isIntrospectVersionChanged(Packet packet, DomainPresenceInfo info) {
    return Optional.ofNullable(packet.get(INTROSPECTION_STATE_LABEL))
            .map(introspectVersionLabel -> !introspectVersionLabel.equals(getIntrospectVersion(info))).orElse(false);
  }

  private static boolean isGenerationChanged(Packet packet, DomainPresenceInfo info) {
    return Optional.ofNullable(packet.get(INTROSPECTION_DOMAIN_SPEC_GENERATION))
            .map(gen -> !gen.equals(getGeneration(info))).orElse(true);
  }

  private static String getIntrospectVersion(DomainPresenceInfo info) {
    return Optional.ofNullable(info.getDomain()).map(Domain::getSpec).map(DomainSpec::getIntrospectVersion)
            .orElse("");
  }

  private static String getGeneration(DomainPresenceInfo info) {
    return Optional.ofNullable(info.getDomain()).map(Domain::getMetadata).map(m -> m.getGeneration().toString())
            .orElse("");
  }

  private static boolean isModelInImageUpdate(Packet packet, DomainPresenceInfo info) {
    return isModelInImage(info) && !getCurrentImageSpecHash(info).equals(getIntrospectionImageSpecHash(packet));
  }

  private static boolean isModelInImage(DomainPresenceInfo info) {
    return info.getDomain().getDomainHomeSourceType() == FromModel;
  }

  private static String getCurrentImageSpecHash(DomainPresenceInfo info) {
    return String.valueOf(ConfigMapHelper.getModelInImageSpecHash(info.getDomain().getSpec().getImage()));
  }

  private static String getIntrospectionImageSpecHash(Packet packet) {
    return (String) packet.get(IntrospectorConfigMapConstants.DOMAIN_INPUTS_HASH);
  }

  private static int runningServersCount(DomainPresenceInfo info) {
    return ManagedServersUpStep.getRunningServers(info).size();
  }

  /**
   * TODO: Enhance determination of when we believe we're creating WLS managed server pods.
   *
   * @param info the domain presence info
   * @return True, if creating servers
   */
  static boolean creatingServers(DomainPresenceInfo info) {
    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();
    List<Cluster> clusters = spec.getClusters();
    List<ManagedServer> servers = spec.getManagedServers();

    // Are we starting a cluster?
    // NOTE: clusterServerStartPolicy == null indicates default policy
    for (Cluster cluster : clusters) {
      int replicaCount = dom.getReplicaCount(cluster.getClusterName());
      String clusterServerStartPolicy = cluster.getServerStartPolicy();
      LOGGER.fine(
            "Start Policy: "
                  + clusterServerStartPolicy
                  + ", replicaCount: "
                  + replicaCount
                  + " for cluster: "
                  + cluster);
      if ((clusterServerStartPolicy == null
            || !clusterServerStartPolicy.equals(ConfigurationConstants.START_NEVER))
            && replicaCount > 0) {
        return true;
      }
    }

    // If Domain level Server Start Policy = ALWAYS, IF_NEEDED or ADMIN_ONLY then we most likely
    // will start a server pod
    // NOTE: domainServerStartPolicy == null indicates default policy
    String domainServerStartPolicy = dom.getSpec().getServerStartPolicy();
    if (domainServerStartPolicy == null
          || !domainServerStartPolicy.equals(ConfigurationConstants.START_NEVER)) {
      return true;
    }

    // Are we starting any explicitly specified individual server?
    // NOTE: serverStartPolicy == null indicates default policy
    for (ManagedServer server : servers) {
      String serverStartPolicy = server.getServerStartPolicy();
      if (serverStartPolicy == null
            || !serverStartPolicy.equals(ConfigurationConstants.START_NEVER)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Factory for {@link Step} that replaces or creates WebLogic domain introspector job.
   *
   * @param next Next processing step
   * @return Step for replacing or creating the domain introsepctor jod
   */
  public static Step replaceOrCreateDomainIntrospectorJobStep(Step next) {
    return new ReplaceOrCreateIntrospectorJobStep(next);
  }

  private static Step createWatchDomainIntrospectorJobReadyStep(Step next) {
    return new WatchDomainIntrospectorJobReadyStep(next);
  }

  /**
   * Factory for {@link Step} that reads WebLogic domain introspector job results from pod's log.
   *
   * @param next Next processing step
   * @return Step for reading WebLogic domain introspector pod log
   */
  private static Step readDomainIntrospectorPodLogStep(Step next) {
    return createWatchDomainIntrospectorJobReadyStep(
          readDomainIntrospectorPodStep(readDomainIntrospectorPodLog(next)));
  }

  /**
   * Factory for {@link Step} that reads WebLogic domain introspector pod.
   *
   * @param next Next processing step
   * @return Step for reading WebLogic domain introspector pod
   */
  private static Step readDomainIntrospectorPodStep(Step next) {
    return new ReadDomainIntrospectorPodStep(next);
  }

  static class DomainIntrospectorJobStepContext extends JobStepContext {

    // domainTopology is null if this is 1st time we're running job for this domain
    private final WlsDomainConfig domainTopology;

    DomainIntrospectorJobStepContext(Packet packet) {
      super(packet);
      this.domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
      init();
    }

    /**
     * Creates the specified new pod and performs any additional needed processing.
     *
     * @param next the next step to perform after the pod creation is complete.
     * @return a step to be scheduled.
     */
    @Override
    Step createNewJob(Step next) {
      return createJob(next);
    }

    @Override
    String getJobCreatedMessageKey() {
      return MessageKeys.JOB_CREATED;
    }

    @Override
    String getJobName() {
      return LegalNames.toJobIntrospectorName(getDomainUid());
    }

    @Override
    protected List<V1Volume> getAdditionalVolumes() {
      List<V1Volume> volumes = getDomain().getSpec().getAdditionalVolumes();
      getServerSpec().getAdditionalVolumes().stream().forEach(volume -> addVolumeIfMissing(volume, volumes));
      return volumes;
    }

    private void addVolumeIfMissing(V1Volume volume, List<V1Volume> volumes) {
      if (!volumes.contains(volume) && volume.getName().startsWith(COMPATIBILITY_MODE)) {
        volumes.add(volume);
      }
    }

    @Override
    protected List<V1VolumeMount> getAdditionalVolumeMounts() {
      List<V1VolumeMount> volumeMounts = getDomain().getSpec().getAdditionalVolumeMounts();
      getServerSpec().getAdditionalVolumeMounts().stream()
              .forEach(mount -> addVolumeMountIfMissing(mount, volumeMounts));
      return volumeMounts;
    }

    private void addVolumeMountIfMissing(V1VolumeMount mount, List<V1VolumeMount> volumeMounts) {
      if (!volumeMounts.contains(mount) && mount.getName().startsWith(COMPATIBILITY_MODE)) {
        volumeMounts.add(mount);
      }
    }

  }

  static class DomainIntrospectorJobStep extends Step {

    DomainIntrospectorJobStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      if (runIntrospector(packet, info)) {
        JobStepContext context = new DomainIntrospectorJobStepContext(packet);

        packet.putIfAbsent(START_TIME, OffsetDateTime.now());

        return doNext(
            Step.chain(
                DomainValidationSteps.createAdditionalDomainValidationSteps(
                    Objects.requireNonNull(context.getJobModel().getSpec()).getTemplate().getSpec()),
                createProgressingStartedEventStep(info, INSPECTING_DOMAIN_PROGRESS_REASON, true, null),
                context.createNewJob(null),
                readDomainIntrospectorPodLogStep(null),
                deleteDomainIntrospectorJobStep(null),
                ConfigMapHelper.createIntrospectorConfigMapStep(getNext())),
              packet);
      }

      return doNext(packet);
    }
  }

  private static class ReplaceOrCreateIntrospectorJobStep extends Step {

    static final int JOB_DELETE_TIMEOUT_SECONDS = 1;

    ReplaceOrCreateIntrospectorJobStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(replaceOrCreateJob(packet, getNext()), packet);
    }

    private Step replaceOrCreateJob(Packet packet, Step next) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return new CallBuilder().readJobAsync(JobHelper.createJobName(info.getDomain().getDomainUid()),
              info.getNamespace(), info.getDomain().getDomainUid(),
              new ReplaceOrCreateStep(next));
    }
  }

  static class ReplaceOrCreateStep extends DefaultResponseStep {

    ReplaceOrCreateStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse callResponse) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      V1Job job = (V1Job) callResponse.getResult();
      if ((job != null) && (packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB) == null)) {
        packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB, job);
      }
      return doNext(getIntrospectorPodStatus(info.getDomainUid(), info.getNamespace(), getNext()), packet);
    }

    private Step getIntrospectorPodStatus(String domainUid, String namespace, Step next) {
      return new CallBuilder()
              .withLabelSelectors(LabelConstants.JOBNAME_LABEL)
              .listPodAsync(namespace, new IntrospectorPodStatusStep(domainUid, next));
    }

    private static boolean isImagePullError(String jobPodContainerWaitingReason) {
      return Optional.ofNullable(jobPodContainerWaitingReason)
              .map(s -> s.contains("ErrImagePull") || s.contains("ImagePullBackOff"))
              .orElse(false);
    }

    private List<V1ContainerStatus> getInitContainerStatuses(V1Pod pod) {
      return Optional.ofNullable(pod.getStatus()).map(V1PodStatus::getInitContainerStatuses).orElse(null);
    }

    private static boolean isJobTimedout(DomainPresenceInfo info) {
      return Objects.equals(getReason(info), "DeadlineExceeded") || getMessage(info).contains("DeadlineExceeded");
    }

    private static boolean isJobNewOrNotProcesssed(V1Job job, String lastJobProcessedUid) {
      return (lastJobProcessedUid == null) || (!lastJobProcessedUid.equals(job.getMetadata().getUid()));
    }

    private static String getLastIntrospectJobProcessedId(DomainPresenceInfo info) {
      return Optional.of(info)
              .map(DomainPresenceInfo::getDomain)
              .map(Domain::getStatus)
              .map(DomainStatus::getLastIntrospectJobProcessedUid)
              .orElse(null);
    }

    private static String getReason(DomainPresenceInfo info) {
      return Optional.of(info)
              .map(DomainPresenceInfo::getDomain)
              .map(Domain::getStatus)
              .map(DomainStatus::getReason)
              .orElse(null);
    }

    private static String getMessage(DomainPresenceInfo info) {
      return Optional.of(info)
              .map(DomainPresenceInfo::getDomain)
              .map(Domain::getStatus)
              .map(DomainStatus::getMessage)
              .orElse("");
    }

    private class IntrospectorPodStatusStep extends ResponseStep<V1PodList> {
      private final String domainUid;

      IntrospectorPodStatusStep(String domainUid, Step next) {
        super(next);
        this.domainUid = domainUid;
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1PodList> callResponse) {
        return super.onFailure(packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1PodList> callResponse) {
        List<Step> nextSteps = new ArrayList<>();
        Optional.ofNullable(callResponse.getResult())
                .map(V1PodList::getItems)
                .orElseGet(Collections::emptyList)
                .forEach(pod -> recordJobPodNameAndStatus(packet, pod));

        V1Job job = (V1Job) packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);
        OffsetDateTime startTime = createNextSteps(nextSteps, packet, job, getNext());
        packet.putIfAbsent(START_TIME, startTime);
        return doContinueListOrNext(callResponse, packet, nextSteps.get(0));
      }

      private void recordJobPodNameAndStatus(Packet packet, V1Pod pod) {
        String name = Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
        if (name.startsWith(JobHelper.createJobName(domainUid))) {
          packet.put(ProcessingConstants.JOB_POD_NAME, name);

          packet.put(ProcessingConstants.JOB_POD_CONTAINER_WAITING_REASON, Optional.ofNullable(pod.getStatus())
                  .map(V1PodStatus::getContainerStatuses).map(statuses -> statuses.get(0))
                  .map(V1ContainerStatus::getState).map(V1ContainerState::getWaiting)
                  .map(V1ContainerStateWaiting::getReason).orElse(null));
          packet.put(ProcessingConstants.JOB_POD_INIT_CONTAINER_WAITING_REASON, getInitContainerWaitingMessages(pod));
        }
      }

      private Boolean getInitContainerWaitingMessages(V1Pod pod) {
        return Optional.ofNullable(getInitContainerStatuses(pod)).orElseGet(Collections::emptyList).stream()
                .anyMatch(status -> isImagePullError(getWaitingReason(status)));
      }

      private String getWaitingReason(V1ContainerStatus status) {
        return Optional.ofNullable(status)
                .map(V1ContainerStatus::getState)
                .map(V1ContainerState::getWaiting)
                .map(V1ContainerStateWaiting::getReason)
                .orElse(null);
      }
    }

    static OffsetDateTime createNextSteps(List<Step> nextSteps, Packet packet, V1Job job, Step next) {
      OffsetDateTime jobStartTime;
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      String namespace = info.getNamespace();
      String jobPodContainerWaitingReason = packet.getValue(ProcessingConstants.JOB_POD_CONTAINER_WAITING_REASON);
      Boolean jobInitContainerImagePullError = getjobInitContainerImagePullError(packet);

      if (job != null) {
        jobStartTime = Optional.ofNullable(job.getMetadata())
                .map(V1ObjectMeta::getCreationTimestamp).orElse(OffsetDateTime.now());
        String lastIntrospectJobProcessedId = getLastIntrospectJobProcessedId(info);

        if (isJobTimedout(info) || (isImagePullError(jobPodContainerWaitingReason)) || jobInitContainerImagePullError) {
          jobStartTime = OffsetDateTime.now();
          packet.put(DOMAIN_INTROSPECT_REQUESTED, ReadDomainIntrospectorPodLogResponseStep.INTROSPECTION_FAILED);
          nextSteps.add(Step.chain(deleteDomainIntrospectorJobStep(null),
                  createDomainIntrospectorJobStep(next)));
        } else if (isJobNewOrNotProcesssed(job, lastIntrospectJobProcessedId)) {
          nextSteps.add(Step.chain(readDomainIntrospectorPodLogStep(null),
                  deleteDomainIntrospectorJobStep(null),
                  ConfigMapHelper.createIntrospectorConfigMapStep(next)));
        } else {
          nextSteps.add(Step.chain(createWatchDomainIntrospectorJobReadyStep(null),
                  deleteDomainIntrospectorJobStep(null),
                  new DomainProcessorImpl.IntrospectionRequestStep(info),
                  createDomainIntrospectorJobStep(next)));
        }
      } else {
        jobStartTime = OffsetDateTime.now();
        nextSteps.add(Step.chain(
                ConfigMapHelper.readExistingIntrospectorConfigMap(namespace, info.getDomainUid()),
                createDomainIntrospectorJobStep(next)));
      }
      return jobStartTime;
    }

    private static Boolean getjobInitContainerImagePullError(Packet packet) {
      return Optional.ofNullable(packet.<Boolean>getValue(ProcessingConstants.JOB_POD_INIT_CONTAINER_WAITING_REASON))
              .orElse(Boolean.FALSE);
    }
  }

  static ReadDomainIntrospectorPodLogStep readDomainIntrospectorPodLog(Step next) {
    return new ReadDomainIntrospectorPodLogStep(next);
  }

  private static class ReadDomainIntrospectorPodLogStep extends Step {

    ReadDomainIntrospectorPodLogStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      String namespace = info.getNamespace();

      String jobPodName = (String) packet.get(ProcessingConstants.JOB_POD_NAME);

      return doNext(readDomainIntrospectorPodLog(jobPodName, namespace, info.getDomainUid(), getNext()), packet);
    }

    private Step readDomainIntrospectorPodLog(String jobPodName, String namespace, String domainUid, Step next) {
      return new CallBuilder()
              .withContainerName(domainUid + "-introspector")
              .readPodLogAsync(
                      jobPodName, namespace, domainUid, new ReadDomainIntrospectorPodLogResponseStep(next));
    }

  }

  private static class ReadDomainIntrospectorPodLogResponseStep extends ResponseStep<String> {
    public static final String INTROSPECTION_FAILED = "INTROSPECTION_FAILED";
    private StringBuilder logMessage = new StringBuilder();
    private final List<String> severeStatuses = new ArrayList<>();

    ReadDomainIntrospectorPodLogResponseStep(Step nextStep) {
      super(nextStep);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<String> callResponse) {
      String result = callResponse.getResult();
      LOGGER.fine("+++++ ReadDomainIntrospectorPodLogResponseStep: \n" + result);

      if (result != null) {
        convertJobLogsToOperatorLogs(result);
        if (!severeStatuses.isEmpty()) {
          updateStatus(packet.getSpi(DomainPresenceInfo.class));
        }
        packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT, result);
        MakeRightDomainOperation.recordInspection(packet);
      }

      V1Job domainIntrospectorJob =
              (V1Job) packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);
      boolean jobPodContainerTerminated = JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER
          .equals(packet.get(JOB_POD_INTROSPECT_CONTAINER_TERMINATED));

      if (isNotComplete(domainIntrospectorJob) && !jobPodContainerTerminated) {
        List<String> jobConditionsReason = new ArrayList<>();
        if (domainIntrospectorJob != null) {
          logIntrospectorFailure(packet, domainIntrospectorJob);
          V1JobStatus status = domainIntrospectorJob.getStatus();
          if (status != null && status.getConditions() != null) {
            for (V1JobCondition cond : status.getConditions()) {
              jobConditionsReason.add(cond.getReason());
            }
          }
        }
        if (jobConditionsReason.size() == 0) {
          jobConditionsReason.add(DomainStatusUpdater.ERR_INTROSPECTOR);
        }
        //Introspector job is incomplete, update domain status and terminate processing
        Step nextStep = null;
        int retryIntervalSeconds = TuningParameters.getInstance().getMainTuning().domainPresenceRecheckIntervalSeconds;

        if (OffsetDateTime.now().isAfter(
                getJobCreationTime(domainIntrospectorJob).plus(retryIntervalSeconds, SECONDS))) {
          //Introspector job is incomplete and current time is greater than the lazy deletion time for the job,
          //update the domain status and execute the next step
          packet.put(DOMAIN_INTROSPECT_REQUESTED, INTROSPECTION_FAILED);
          nextStep = getNext();
        }

        nextStep = Step.chain(recordLastIntrospectJobProcessedUid(
                getLastIntrospectJobProcessedId(domainIntrospectorJob)), nextStep);

        if (packet.get(JOB_POD_FLUENTD_CONTAINER_TERMINATED) != null) {
          severeStatuses.add(packet.get(JOB_POD_FLUENTD_CONTAINER_TERMINATED).toString());
        }

        if (!severeStatuses.isEmpty()) {
          nextStep = Step.chain(DomainStatusUpdater.createFailureCountStep(), nextStep);
        }

        return doNext(
                DomainStatusUpdater.createFailureRelatedSteps(
                        onSeparateLines(jobConditionsReason),
                        onSeparateLines(severeStatuses),
                        nextStep),
                packet);
      }

      // Note: fluentd container log can be huge, may not be a good idea to read the container log.
      //  Just set a flag and let the user know they can check the container log to determine unlikely
      //  starting error, most likely a very bad formatted configuration.

      if (packet.get(JOB_POD_FLUENTD_CONTAINER_TERMINATED) != null) {
        severeStatuses.add(packet.get(JOB_POD_FLUENTD_CONTAINER_TERMINATED).toString());
        Step nextStep = null;
        nextStep = Step.chain(DomainStatusUpdater.createFailureCountStep(), nextStep);
        List<String> jobConditionsReason = new ArrayList<>();
        return doNext(
            DomainStatusUpdater.createFailureRelatedSteps(
                onSeparateLines(jobConditionsReason),
                onSeparateLines(severeStatuses),
                nextStep),
            packet);
      }

      Step nextSteps = Step.chain(recordLastIntrospectJobProcessedUid(
              getLastIntrospectJobProcessedId(domainIntrospectorJob)), getNext());
      return doNext(nextSteps, packet);

    }

    private String getLastIntrospectJobProcessedId(V1Job domainIntrospectorJob) {
      return Optional.ofNullable(domainIntrospectorJob).map(V1Job::getMetadata)
              .map(V1ObjectMeta::getUid).orElse(null);
    }

    private OffsetDateTime getJobCreationTime(V1Job domainIntrospectorJob) {
      return Optional.ofNullable(domainIntrospectorJob.getMetadata())
              .map(V1ObjectMeta::getCreationTimestamp).orElse(OffsetDateTime.now());
    }

    private boolean isNotComplete(V1Job domainIntrospectorJob) {
      return !JobWatcher.isComplete(domainIntrospectorJob);
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

    private void updateStatus(DomainPresenceInfo domainPresenceInfo) {
      DomainStatusPatch.updateSynchronously(
            domainPresenceInfo.getDomain(), DomainStatusUpdater.ERR_INTROSPECTOR, onSeparateLines(severeStatuses));
    }

    private String onSeparateLines(List<String> lines) {
      return String.join(System.lineSeparator(), lines);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<String> callResponse) {
      if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
        return updateDomainStatus(packet, callResponse);
      } else {
        return super.onFailure(packet, callResponse);
      }
    }

    private NextAction updateDomainStatus(Packet packet, CallResponse<String> callResponse) {
      return doNext(
            Step.chain(
                  DomainStatusUpdater.createFailureCountStep(),
                  DomainStatusUpdater.createFailureRelatedSteps(callResponse, null)),
            packet);
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

  static class DeleteDomainIntrospectorJobStep extends Step {

    DeleteDomainIntrospectorJobStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      String jobName = JobHelper.createJobName(info.getDomainUid());
      logJobDeleted(info.getDomainUid(), info.getNamespace(), jobName, packet);
      return doNext(new CallBuilder().withTimeoutSeconds(ReplaceOrCreateIntrospectorJobStep.JOB_DELETE_TIMEOUT_SECONDS)
              .deleteJobAsync(
                      jobName,
                      info.getNamespace(),
                      info.getDomainUid(),
                      new V1DeleteOptions().propagationPolicy("Foreground"),
                      new DefaultResponseStep<>(getNext())), packet);
    }
  }

  public static Step deleteDomainIntrospectorJobStep(Step next) {
    return new DeleteDomainIntrospectorJobStep(next);
  }

  static void logJobDeleted(String domainUid, String namespace, String jobName, Packet packet) {
    V1Job domainIntrospectorJob =
            (V1Job) packet.remove(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);

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

  private static class ReadDomainIntrospectorPodStep extends Step {

    ReadDomainIntrospectorPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      String domainUid = info.getDomain().getDomainUid();
      String namespace = info.getNamespace();
      Integer currentIntrospectFailureRetryCount = Optional.of(info)
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getStatus)
          .map(DomainStatus::getIntrospectJobFailureCount)
          .orElse(0);

      if (currentIntrospectFailureRetryCount > 0) {
        LOGGER.info(MessageKeys.INTROSPECT_JOB_FAILED, info.getDomain().getDomainUid(),
            TuningParameters.getInstance().getMainTuning().domainPresenceRecheckIntervalSeconds,
            currentIntrospectFailureRetryCount);
      }

      return doNext(readDomainIntrospectorPod(domainUid, namespace, getNext()), packet);
    }

    private Step readDomainIntrospectorPod(String domainUid, String namespace, Step next) {
      return new CallBuilder()
            .withLabelSelectors(LabelConstants.JOBNAME_LABEL)
            .listPodAsync(namespace, new PodListStep(domainUid, next));
    }
  }

  private static class PodListStep extends ResponseStep<V1PodList> {
    private final String domainUid;

    PodListStep(String domainUid, Step next) {
      super(next);
      this.domainUid = domainUid;
    }

    private void addContainerTerminatedMarkerToPacket(V1Pod jobPod, String jobName, Packet packet) {

      Optional<V1ContainerStatus> containerStatus = Optional.ofNullable(jobPod)
          .map(V1Pod::getStatus)
          .map(V1PodStatus::getContainerStatuses)
          .orElseGet(Collections::emptyList)
          .stream().filter(v -> v.getState().getTerminated() != null)
          .filter(c -> FLUENTD_CONTAINER_NAME.equals(c.getName()))
          .findFirst();

      if (!containerStatus.isEmpty()) {
        LOGGER.severe(INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED, jobPod.getMetadata().getName(),
            jobPod.getMetadata().getNamespace(),
            containerStatus.get().getState().getTerminated().getExitCode(),
            containerStatus.get().getState().getTerminated().getReason(),
            containerStatus.get().getState().getTerminated().getMessage());

        packet.put(JOB_POD_FLUENTD_CONTAINER_TERMINATED,
            LOGGER.formatMessage(INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED, jobPod.getMetadata().getName(),
                jobPod.getMetadata().getNamespace(),
                containerStatus.get().getState().getTerminated().getExitCode(),
                containerStatus.get().getState().getTerminated().getReason(),
                containerStatus.get().getState().getTerminated().getMessage()));

        return;

      }

      containerStatus = Optional.ofNullable(jobPod)
          .map(V1Pod::getStatus)
          .map(V1PodStatus::getContainerStatuses)
          .orElseGet(Collections::emptyList)
          .stream().filter(v -> v.getState().getTerminated() != null)
          .filter(c -> jobName.equals(c.getName()))
          .filter(c -> c.getState().getTerminated().getExitCode() == 0)
          .findFirst();

      if (!containerStatus.isEmpty()) {
        packet.put(JOB_POD_INTROSPECT_CONTAINER_TERMINATED, JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER);
      }

    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1PodList> callResponse) {
      return super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1PodList> callResponse) {

      final V1Pod jobPod
          = Optional.ofNullable(callResponse.getResult())
          .map(V1PodList::getItems)
          .orElseGet(Collections::emptyList)
          .stream()
          .filter(p -> p.getMetadata().getName().startsWith(getJobName()))
          .findFirst()
          .orElse(null);

      if (jobPod != null) {
        addContainerTerminatedMarkerToPacket(jobPod, getJobName(), packet);
        recordJobPodName(packet, getName(jobPod));
      }
      return doContinueListOrNext(callResponse, packet);
    }

    private String getJobName() {
      return domainUid + "-introspector";
    }

    private String getName(V1Pod pod) {
      return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
    }

    private void recordJobPodName(Packet packet, String podName) {
      packet.put(ProcessingConstants.JOB_POD_NAME, podName);
    }

  }
}
