// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonPatchBuilder;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.V1Affinity;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1HTTPGetAction;
import io.kubernetes.client.models.V1Handler;
import io.kubernetes.client.models.V1Lifecycle;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodReadinessGate;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Toleration;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import oracle.kubernetes.weblogic.domain.model.Shutdown;
import org.apache.commons.lang3.builder.EqualsBuilder;

import static oracle.kubernetes.operator.KubernetesConstants.GRACEFUL_SHUTDOWNTYPE;
import static oracle.kubernetes.operator.LabelConstants.forDomainUidSelector;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;

@SuppressWarnings("deprecation")
public abstract class PodStepContext extends StepContextBase {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String STOP_SERVER = "/weblogic-operator/scripts/stopServer.sh";
  private static final String START_SERVER = "/weblogic-operator/scripts/startServer.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private static final String READINESS_PATH = "/weblogic/ready";
  final WlsServerConfig scan;
  private final DomainPresenceInfo info;
  private final WlsDomainConfig domainTopology;
  private final Step conflictStep;
  private V1Pod podModel;

  PodStepContext(Step conflictStep, Packet packet) {
    this.conflictStep = conflictStep;
    info = packet.getSpi(DomainPresenceInfo.class);
    domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    scan = (WlsServerConfig) packet.get(ProcessingConstants.SERVER_SCAN);
  }

  private static boolean isCustomerItem(Map.Entry<String, String> entry) {
    return !entry.getKey().startsWith("weblogic.");
  }

  void init() {
    podModel = createPodModel();
  }

  V1Pod getPodModel() {
    return podModel;
  }

  abstract ServerSpec getServerSpec();

  private Step getConflictStep() {
    return new ConflictStep();
  }

  abstract Map<String, String> getPodLabels();

  abstract Map<String, String> getPodAnnotations();

  String getNamespace() {
    return info.getNamespace();
  }

  // ------------------------ data methods ----------------------------

  String getDomainUid() {
    return getDomain().getDomainUid();
  }

  Domain getDomain() {
    return info.getDomain();
  }

  String getDomainName() {
    return domainTopology.getName();
  }

  private String getDomainResourceName() {
    return info.getDomain().getMetadata().getName();
  }

  String getPodName() {
    return LegalNames.toPodName(getDomainUid(), getServerName());
  }

  String getAsName() {
    return domainTopology.getAdminServerName();
  }

  Integer getAsPort() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .getLocalAdminProtocolChannelPort();
  }

  boolean isLocalAdminProtocolChannelSecure() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .isLocalAdminProtocolChannelSecure();
  }

  Integer getLocalAdminProtocolChannelPort() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .getLocalAdminProtocolChannelPort();
  }

  private String getLogHome() {
    return getDomain().getLogHome();
  }

  private String getEffectiveLogHome() {
    if (!getDomain().getLogHomeEnabled()) return null;
    String logHome = getLogHome();
    if (logHome == null || "".equals(logHome.trim())) {
      // logHome not specified, use default value
      return DEFAULT_LOG_HOME + File.separator + getDomainUid();
    }
    return logHome;
  }

  private String getIncludeServerOutInPodLog() {
    return Boolean.toString(getDomain().isIncludeServerOutInPodLog());
  }

  private List<V1ContainerPort> getContainerPorts() {
    if (scan != null) {
      List<V1ContainerPort> ports = new ArrayList<>();
      if (scan.getNetworkAccessPoints() != null) {
        for (NetworkAccessPoint nap : scan.getNetworkAccessPoints()) {
          V1ContainerPort port =
              new V1ContainerPort()
                  .name(LegalNames.toDns1123LegalName(nap.getName()))
                  .containerPort(nap.getListenPort())
                  .protocol("TCP");
          ports.add(port);
        }
      }
      if (scan.getListenPort() != null) {
        ports.add(
            new V1ContainerPort()
                .name("default")
                .containerPort(scan.getListenPort())
                .protocol("TCP"));
      }
      if (scan.getSslListenPort() != null) {
        ports.add(
            new V1ContainerPort()
                .name("default-secure")
                .containerPort(scan.getSslListenPort())
                .protocol("TCP"));
      }
      if (scan.getAdminPort() != null) {
        ports.add(
            new V1ContainerPort()
                .name("default-admin")
                .containerPort(scan.getAdminPort())
                .protocol("TCP"));
      }
      return ports;
    }
    return null;
  }

  abstract Integer getDefaultPort();

  abstract String getServerName();

  String getClusterName() {
    return null;
  }

  // Prevent the watcher from recreating pod with old spec
  private void markBeingDeleted() {
    info.setServerPodBeingDeleted(getServerName(), Boolean.TRUE);
  }

  // ----------------------- step methods ------------------------------

  private void clearBeingDeleted() {
    info.setServerPodBeingDeleted(getServerName(), Boolean.FALSE);
  }

  private void setRecordedPod(V1Pod pod) {
    info.setServerPod(getServerName(), pod);
  }

  /**
   * Reads the specified pod and decides whether it must be created or replaced.
   *
   * @param next the next step to perform after the pod verification is complete.
   * @return a step to be scheduled.
   */
  Step verifyPod(Step next) {
    return new VerifyPodStep(next);
  }

  /**
   * Deletes the specified pod.
   *
   * @param next the next step to perform after the pod deletion is complete.
   * @return a step to be scheduled.
   */
  private Step deletePod(Step next) {
    return new CallBuilder()
        .deletePodAsync(getPodName(), getNamespace(), new V1DeleteOptions(), deleteResponse(next));
  }

  /**
   * Creates the specified new pod and performs any additional needed processing.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  abstract Step createNewPod(Step next);

  /**
   * Creates the specified new pod and records it.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  Step createPod(Step next) {
    clearBeingDeleted();
    return createPodAsync(createResponse(next));
  }

  private Step createPodAsync(ResponseStep<V1Pod> response) {
    return new CallBuilder().createPodAsync(getNamespace(), getPodModel(), response);
  }

  /**
   * Creates the specified replacement pod and performs any additional needed processing.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  abstract Step replaceCurrentPod(Step next);

  /**
   * Creates the specified replacement pod and records it.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  private Step replacePod(Step next) {
    return createPodAsync(replaceResponse(next));
  }

  private Step patchCurrentPod(V1Pod currentPod, Step next) {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();

    KubernetesUtils.addPatches(
        patchBuilder, "/metadata/labels/", currentPod.getMetadata().getLabels(), getPodLabels());
    KubernetesUtils.addPatches(
        patchBuilder,
        "/metadata/annotations/",
        currentPod.getMetadata().getAnnotations(),
        getPodAnnotations());

    return new CallBuilder()
        .patchPodAsync(getPodName(), getNamespace(), patchBuilder.build(), patchResponse(next));
  }

  private void logPodCreated() {
    LOGGER.info(getPodCreatedMessageKey(), getDomainUid(), getServerName());
  }

  private void logPodExists() {
    LOGGER.fine(getPodExistsMessageKey(), getDomainUid(), getServerName());
  }

  private void logPodPatched() {
    LOGGER.info(getPodPatchedMessageKey(), getDomainUid(), getServerName());
  }

  private void logPodReplaced() {
    LOGGER.info(getPodReplacedMessageKey(), getDomainUid(), getServerName());
  }

  abstract String getPodCreatedMessageKey();

  abstract String getPodExistsMessageKey();

  abstract String getPodPatchedMessageKey();

  abstract String getPodReplacedMessageKey();

  Step createCyclePodStep(Step next) {
    return new CyclePodStep(next);
  }

  private boolean mustPatchPod(V1Pod currentPod) {
    return KubernetesUtils.isMissingValues(currentPod.getMetadata().getLabels(), getPodLabels())
        || KubernetesUtils.isMissingValues(
            currentPod.getMetadata().getAnnotations(), getPodAnnotations());
  }

  private boolean canUseCurrentPod(V1Pod currentPod) {
    boolean useCurrent =
        AnnotationHelper.getHash(getPodModel()).equals(AnnotationHelper.getHash(currentPod));
    if (!useCurrent && AnnotationHelper.getDebugString(currentPod) != null)
      LOGGER.info(
          MessageKeys.POD_DUMP,
          AnnotationHelper.getDebugString(currentPod),
          AnnotationHelper.getDebugString(getPodModel()));

    return useCurrent;
  }

  private String getReasonToRecycle(V1Pod currentPod) {
    PodCompatibility compatibility = new PodCompatibility(getPodModel(), currentPod);
    return compatibility.getIncompatibility();
  }

  private ResponseStep<V1Pod> createResponse(Step next) {
    return new CreateResponseStep(next);
  }

  private ResponseStep<V1Status> deleteResponse(Step next) {
    return new DeleteResponseStep(next);
  }

  private ResponseStep<V1Pod> replaceResponse(Step next) {
    return new ReplacePodResponseStep(next);
  }

  private ResponseStep<V1Pod> patchResponse(Step next) {
    return new PatchPodResponseStep(next);
  }

  Step verifyPersistentVolume(Step next) {
    return new VerifyPersistentVolumeStep(next);
  }

  V1Pod createPodModel() {
    return withNonHashedElements(AnnotationHelper.withSha256Hash(createPodRecipe()));
  }

  protected Optional<V1Container> getContainer(V1Pod v1Pod) {
    return v1Pod.getSpec().getContainers().stream().filter(this::isK8sContainer).findFirst();
  }

  protected boolean isK8sContainer(V1Container c) {
    return KubernetesConstants.CONTAINER_NAME.equals(c.getName());
  }

  V1Pod withNonHashedElements(V1Pod pod) {
    V1ObjectMeta metadata = pod.getMetadata();
    // Adds labels and annotations to a pod, skipping any whose names begin with "weblogic."
    getPodLabels().entrySet().stream()
        .filter(PodStepContext::isCustomerItem)
        .forEach(e -> metadata.putLabelsItem(e.getKey(), e.getValue()));
    getPodAnnotations().entrySet().stream()
        .filter(PodStepContext::isCustomerItem)
        .forEach(e -> metadata.putAnnotationsItem(e.getKey(), e.getValue()));

    updateForStartupMode(pod);
    updateForShutdown(pod);
    updateForDeepSubstitution(pod);

    return pod;
  }

  final void updateForDeepSubstitution(V1Pod pod) {
    getContainer(pod)
        .ifPresent(
            c -> {
              doDeepSubstitution(deepSubVars(c.getEnv()), pod);
            });
  }

  final Map<String, String> deepSubVars(List<V1EnvVar> envVars) {
    Map<String, String> vars = varsToSubVariables(envVars);
    String clusterName = getClusterName();
    if (clusterName != null) {
      vars.put("CLUSTER_NAME", clusterName);
    }
    return vars;
  }

  final void updateForStartupMode(V1Pod pod) {
    ServerSpec serverSpec = getServerSpec();
    if (serverSpec != null) {
      String desiredState = serverSpec.getDesiredState();
      if (!WebLogicConstants.RUNNING_STATE.equals(desiredState)) {
        getContainer(pod)
            .ifPresent(
                c -> {
                  List<V1EnvVar> env = c.getEnv();
                  addDefaultEnvVarIfMissing(env, "STARTUP_MODE", desiredState);
                });
      }
    }
  }

  /**
   * Inserts into the pod the environment variables and other configuration related to shutdown
   * behavior.
   *
   * @param pod The pod
   */
  final void updateForShutdown(V1Pod pod) {
    String shutdownType;
    Long timeout;
    boolean ignoreSessions;

    ServerSpec serverSpec = getServerSpec();
    if (serverSpec != null) {
      Shutdown shutdown = serverSpec.getShutdown();
      shutdownType = shutdown.getShutdownType();
      timeout = shutdown.getTimeoutSeconds();
      ignoreSessions = shutdown.getIgnoreSessions();
    } else {
      shutdownType = GRACEFUL_SHUTDOWNTYPE;
      timeout = Shutdown.DEFAULT_TIMEOUT;
      ignoreSessions = Shutdown.DEFAULT_IGNORESESSIONS;
    }

    getContainer(pod)
        .ifPresent(
            c -> {
              List<V1EnvVar> env = c.getEnv();
              if (scan != null) {
                Integer localAdminPort = scan.getLocalAdminProtocolChannelPort();
                addOrReplaceEnvVar(env, "LOCAL_ADMIN_PORT", String.valueOf(localAdminPort));
                addOrReplaceEnvVar(
                    env,
                    "LOCAL_ADMIN_PROTOCOL",
                    localAdminPort.equals(scan.getListenPort()) ? "t3" : "t3s");
              }
              addDefaultEnvVarIfMissing(env, "SHUTDOWN_TYPE", shutdownType);
              addDefaultEnvVarIfMissing(env, "SHUTDOWN_TIMEOUT", String.valueOf(timeout));
              addDefaultEnvVarIfMissing(
                  env, "SHUTDOWN_IGNORE_SESSIONS", String.valueOf(ignoreSessions));
            });

    pod.getSpec().terminationGracePeriodSeconds(timeout + PodHelper.DEFAULT_ADDITIONAL_DELETE_TIME);
  }

  // Creates a pod model containing elements which are not patchable.
  private V1Pod createPodRecipe() {
    return new V1Pod().metadata(createMetadata()).spec(createSpec(TuningParameters.getInstance()));
  }

  protected V1ObjectMeta createMetadata() {
    V1ObjectMeta metadata = new V1ObjectMeta().name(getPodName()).namespace(getNamespace());
    metadata
        .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION)
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
        .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, getDomainName())
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName())
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
        .putLabelsItem(
            LabelConstants.DOMAINRESTARTVERSION_LABEL, getServerSpec().getDomainRestartVersion())
        .putLabelsItem(
            LabelConstants.CLUSTERRESTARTVERSION_LABEL, getServerSpec().getClusterRestartVersion())
        .putLabelsItem(
            LabelConstants.SERVERRESTARTVERSION_LABEL, getServerSpec().getServerRestartVersion());

    // Add prometheus annotations. This will overwrite any custom annotations with same name.
    AnnotationHelper.annotateForPrometheus(metadata, getDefaultPort());
    return metadata;
  }

  protected V1PodSpec createSpec(TuningParameters tuningParameters) {
    V1Container container =
        createContainer(tuningParameters)
            .resources(getServerSpec().getResources())
            .securityContext(getServerSpec().getContainerSecurityContext());
    V1PodSpec podSpec =
        new V1PodSpec()
            .containers(getServerSpec().getContainers())
            .addContainersItem(container)
            .affinity(getAffinity())
            .nodeSelector(getServerSpec().getNodeSelectors())
            .nodeName(getServerSpec().getNodeName())
            .schedulerName(getServerSpec().getSchedulerName())
            .priorityClassName(getServerSpec().getPriorityClassName())
            .runtimeClassName(getServerSpec().getRuntimeClassName())
            .tolerations(getTolerations())
            .readinessGates(getReadinessGates())
            .restartPolicy(getServerSpec().getRestartPolicy())
            .securityContext(getServerSpec().getPodSecurityContext())
            .initContainers(getServerSpec().getInitContainers());

    podSpec.setImagePullSecrets(getServerSpec().getImagePullSecrets());

    for (V1Volume additionalVolume : getVolumes(getDomainUid())) {
      podSpec.addVolumesItem(additionalVolume);
    }

    return podSpec;
  }

  // ---------------------- model methods ------------------------------

  private V1Affinity getAffinity() {
    return getServerSpec().getAffinity();
  }

  private List<V1Toleration> getTolerations() {
    List<V1Toleration> tolerations = getServerSpec().getTolerations();
    return tolerations.isEmpty() ? null : tolerations;
  }

  private List<V1PodReadinessGate> getReadinessGates() {
    List<V1PodReadinessGate> readinessGates = getServerSpec().getReadinessGates();
    return readinessGates.isEmpty() ? null : readinessGates;
  }

  private List<V1Volume> getVolumes(String domainUid) {
    List<V1Volume> volumes = PodDefaults.getStandardVolumes(domainUid);
    volumes.addAll(getServerSpec().getAdditionalVolumes());
    return volumes;
  }

  private V1Container createContainer(TuningParameters tuningParameters) {
    V1Container v1Container =
        new V1Container()
            .name(KubernetesConstants.CONTAINER_NAME)
            .image(getImageName())
            .imagePullPolicy(getImagePullPolicy())
            .command(getContainerCommand())
            .env(getEnvironmentVariables(tuningParameters))
            .ports(getContainerPorts())
            .lifecycle(createLifecycle())
            .livenessProbe(createLivenessProbe(tuningParameters.getPodTuning()));

    if (!mockWls()) {
      v1Container.readinessProbe(createReadinessProbe(tuningParameters.getPodTuning()));
    }

    for (V1VolumeMount additionalVolumeMount : getVolumeMounts()) {
      v1Container.addVolumeMountsItem(additionalVolumeMount);
    }

    return v1Container;
  }

  private String getImageName() {
    return getServerSpec().getImage();
  }

  String getImagePullPolicy() {
    return getServerSpec().getImagePullPolicy();
  }

  protected List<String> getContainerCommand() {
    return Collections.singletonList(START_SERVER);
  }

  private List<V1VolumeMount> getVolumeMounts() {
    List<V1VolumeMount> mounts = PodDefaults.getStandardVolumeMounts(getDomainUid());
    mounts.addAll(getServerSpec().getAdditionalVolumeMounts());
    return mounts;
  }

  void overrideContainerWeblogicEnvVars(List<V1EnvVar> vars) {
    // Override the domain name, domain directory, admin server name and admin server port.
    addEnvVar(vars, "DOMAIN_NAME", getDomainName());
    addEnvVar(vars, "DOMAIN_HOME", getDomainHome());
    addEnvVar(vars, "ADMIN_NAME", getAsName());
    addEnvVar(vars, "ADMIN_PORT", getAsPort().toString());
    if (isLocalAdminProtocolChannelSecure()) {
      addEnvVar(vars, "ADMIN_PORT_SECURE", "true");
    }
    addEnvVar(vars, "SERVER_NAME", getServerName());
    addEnvVar(vars, "DOMAIN_UID", getDomainUid());
    addEnvVar(vars, "NODEMGR_HOME", NODEMGR_HOME);
    addEnvVar(vars, "LOG_HOME", getEffectiveLogHome());
    addEnvVar(vars, "SERVER_OUT_IN_POD_LOG", getIncludeServerOutInPodLog());
    addEnvVar(
        vars, "SERVICE_NAME", LegalNames.toServerServiceName(getDomainUid(), getServerName()));
    addEnvVar(vars, "AS_SERVICE_NAME", LegalNames.toServerServiceName(getDomainUid(), getAsName()));
    if (mockWls()) {
      addEnvVar(vars, "MOCK_WLS", "true");
    }
  }

  private String getDomainHome() {
    return getDomain().getDomainHome();
  }

  private V1Lifecycle createLifecycle() {
    return new V1Lifecycle().preStop(handler(STOP_SERVER));
  }

  private V1Handler handler(String... commandItems) {
    return new V1Handler().exec(execAction(commandItems));
  }

  private V1ExecAction execAction(String... commandItems) {
    return new V1ExecAction().command(Arrays.asList(commandItems));
  }

  private V1Probe createReadinessProbe(TuningParameters.PodTuning tuning) {
    V1Probe readinessProbe = new V1Probe();
    readinessProbe
        .initialDelaySeconds(getReadinessProbeInitialDelaySeconds(tuning))
        .timeoutSeconds(getReadinessProbeTimeoutSeconds(tuning))
        .periodSeconds(getReadinessProbePeriodSeconds(tuning))
        .failureThreshold(FAILURE_THRESHOLD);
    try {
      boolean istioEnabled = getDomain().istioEnabled();
      if (istioEnabled) {
        int istioReadinessPort = getDomain().getIstioReadinessPort();
        readinessProbe =
            readinessProbe.httpGet(httpGetAction(READINESS_PATH, istioReadinessPort, false));
      } else {
        readinessProbe =
            readinessProbe.httpGet(
                httpGetAction(
                    READINESS_PATH,
                    getLocalAdminProtocolChannelPort(),
                    isLocalAdminProtocolChannelSecure()));
      }
    } catch (Exception e) {
      // do nothing
    }
    return readinessProbe;
  }

  @SuppressWarnings("SameParameterValue")
  private V1HTTPGetAction httpGetAction(String path, int port, boolean useHttps) {
    V1HTTPGetAction getAction = new V1HTTPGetAction();
    getAction.path(path).port(new IntOrString(port));
    if (useHttps) {
      getAction.scheme("HTTPS");
    }
    return getAction;
  }

  private int getReadinessProbePeriodSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getPeriodSeconds())
        .orElse(tuning.readinessProbePeriodSeconds);
  }

  private int getReadinessProbeTimeoutSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getTimeoutSeconds())
        .orElse(tuning.readinessProbeTimeoutSeconds);
  }

  private int getReadinessProbeInitialDelaySeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getInitialDelaySeconds())
        .orElse(tuning.readinessProbeInitialDelaySeconds);
  }

  private V1Probe createLivenessProbe(TuningParameters.PodTuning tuning) {
    return new V1Probe()
        .initialDelaySeconds(getLivenessProbeInitialDelaySeconds(tuning))
        .timeoutSeconds(getLivenessProbeTimeoutSeconds(tuning))
        .periodSeconds(getLivenessProbePeriodSeconds(tuning))
        .failureThreshold(FAILURE_THRESHOLD)
        .exec(execAction(LIVENESS_PROBE));
  }

  private int getLivenessProbeInitialDelaySeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getInitialDelaySeconds())
        .orElse(tuning.livenessProbeInitialDelaySeconds);
  }

  private int getLivenessProbeTimeoutSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getTimeoutSeconds())
        .orElse(tuning.livenessProbeTimeoutSeconds);
  }

  private int getLivenessProbePeriodSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getPeriodSeconds())
        .orElse(tuning.livenessProbePeriodSeconds);
  }

  private boolean mockWls() {
    return Boolean.getBoolean("mockWLS");
  }

  private abstract class BaseStep extends Step {
    BaseStep() {
      this(null);
    }

    BaseStep(Step next) {
      super(next);
    }

    protected String getDetail() {
      return getServerName();
    }
  }

  private class ConflictStep extends BaseStep {

    @Override
    public NextAction apply(Packet packet) {
      return doNext(getConflictStep(), packet);
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (!(other instanceof ConflictStep)) {
        return false;
      }
      ConflictStep rhs = ((ConflictStep) other);
      return new EqualsBuilder().append(conflictStep, rhs.getConflictStep()).isEquals();
    }

    private Step getConflictStep() {
      return conflictStep;
    }
  }

  private class CyclePodStep extends BaseStep {

    CyclePodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      markBeingDeleted();
      return doNext(deletePod(getNext()), packet);
    }
  }

  private class VerifyPodStep extends BaseStep {

    VerifyPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      V1Pod currentPod = info.getServerPod(getServerName());
      if (currentPod == null) {
        return doNext(createNewPod(getNext()), packet);
      } else if (!canUseCurrentPod(currentPod)) {
        LOGGER.info(
            MessageKeys.CYCLING_POD,
            currentPod.getMetadata().getName(),
            getReasonToRecycle(currentPod));
        return doNext(replaceCurrentPod(getNext()), packet);
      } else if (mustPatchPod(currentPod)) {
        return doNext(patchCurrentPod(currentPod, getNext()), packet);
      } else {
        logPodExists();
        return doNext(packet);
      }
    }
  }

  private abstract class BaseResponseStep extends ResponseStep<V1Pod> {
    BaseResponseStep(Step next) {
      super(next);
    }

    protected String getDetail() {
      return getServerName();
    }
  }

  private class CreateResponseStep extends BaseResponseStep {
    CreateResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
      return super.onFailure(getConflictStep(), packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
      logPodCreated();
      if (callResponse.getResult() != null) {
        info.updateLastKnownServerStatus(getServerName(), WebLogicConstants.STARTING_STATE);
        setRecordedPod(callResponse.getResult());
      }
      return doNext(packet);
    }
  }

  private class DeleteResponseStep extends ResponseStep<V1Status> {
    DeleteResponseStep(Step next) {
      super(next);
    }

    protected String getDetail() {
      return getServerName();
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Status> callResponses) {
      if (callResponses.getStatusCode() == CallBuilder.NOT_FOUND) {
        return onSuccess(packet, callResponses);
      }
      return super.onFailure(getConflictStep(), packet, callResponses);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Status> callResponses) {
      return doNext(replacePod(getNext()), packet);
    }
  }

  private class ReplacePodResponseStep extends BaseResponseStep {

    ReplacePodResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
      return super.onFailure(getConflictStep(), packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {

      V1Pod newPod = callResponse.getResult();
      logPodReplaced();
      if (newPod != null) {
        setRecordedPod(newPod);
      }

      PodAwaiterStepFactory pw = packet.getSpi(PodAwaiterStepFactory.class);
      return doNext(pw.waitForReady(newPod, getNext()), packet);
    }
  }

  private class PatchPodResponseStep extends BaseResponseStep {
    private final Step next;

    PatchPodResponseStep(Step next) {
      super(next);
      this.next = next;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
      return super.onFailure(getConflictStep(), packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {

      V1Pod newPod = callResponse.getResult();
      logPodPatched();
      if (newPod != null) {
        setRecordedPod(newPod);
      }

      return doNext(next, packet);
    }
  }

  private class VerifyPersistentVolumeStep extends Step {

    VerifyPersistentVolumeStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      String domainUid = getDomainUid();
      Step list =
          new CallBuilder()
              .withLabelSelectors(forDomainUidSelector(domainUid))
              .listPersistentVolumeAsync(
                  new DefaultResponseStep<V1PersistentVolumeList>(getNext()) {
                    @Override
                    public NextAction onSuccess(
                        Packet packet,
                        V1PersistentVolumeList result,
                        int statusCode,
                        Map<String, List<String>> responseHeaders) {
                      if (result != null) {
                        for (V1PersistentVolume pv : result.getItems()) {
                          List<String> accessModes = pv.getSpec().getAccessModes();
                          boolean foundAccessMode = false;
                          for (String accessMode : accessModes) {
                            if (accessMode.equals(READ_WRITE_MANY_ACCESS)) {
                              foundAccessMode = true;
                              break;
                            }
                          }

                          // Persistent volume does not have ReadWriteMany access mode,
                          if (!foundAccessMode) {
                            LOGGER.warning(
                                MessageKeys.PV_ACCESS_MODE_FAILED,
                                pv.getMetadata().getName(),
                                getDomainResourceName(),
                                domainUid,
                                READ_WRITE_MANY_ACCESS);
                          }
                        }
                      } else {
                        LOGGER.warning(
                            MessageKeys.PV_NOT_FOUND_FOR_DOMAIN_UID,
                            getDomainResourceName(),
                            domainUid);
                      }
                      return doNext(packet);
                    }
                  });

      return doNext(list, packet);
    }
  }
}
