// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonPatchBuilder;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ExecAction;
import io.kubernetes.client.openapi.models.V1HTTPGetAction;
import io.kubernetes.client.openapi.models.V1Handler;
import io.kubernetes.client.openapi.models.V1Lifecycle;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapKeys;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import oracle.kubernetes.weblogic.domain.model.Shutdown;
import org.apache.commons.lang3.builder.EqualsBuilder;

public abstract class PodStepContext extends BasePodStepContext {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String STOP_SERVER = "/weblogic-operator/scripts/stopServer.sh";
  private static final String START_SERVER = "/weblogic-operator/scripts/startServer.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private static final String READINESS_PATH = "/weblogic/ready";

  final WlsServerConfig scan;
  private final WlsDomainConfig domainTopology;
  private final Step conflictStep;
  private V1Pod podModel;
  private final String miiModelSecretsHash;
  private final String miiDomainZipHash;
  private final String domainRestartVersion;

  PodStepContext(Step conflictStep, Packet packet) {
    super(packet.getSpi(DomainPresenceInfo.class));
    this.conflictStep = conflictStep;
    domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    miiModelSecretsHash = (String)packet.get(IntrospectorConfigMapKeys.SECRETS_MD_5);
    miiDomainZipHash = (String)packet.get(IntrospectorConfigMapKeys.DOMAINZIP_HASH);
    domainRestartVersion = (String)packet.get(IntrospectorConfigMapKeys.DOMAIN_RESTART_VERSION);
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

  private DomainSourceType getDomainHomeSourceType() {
    return getDomain().getDomainHomeSourceType();
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

  /**
   * Check if the server is listening on a secure port. NOTE: If the targetted server is a managed
   * server, this method is overridden to check if the managed server has a secure listen port rather
   * than the admin server. See PodHelper.ManagedPodStepContext
   *
   * @return true if server is listening on a secure port
   */
  boolean isLocalAdminProtocolChannelSecure() {
    return domainTopology
        .getServerConfig(getServerName())
        .isLocalAdminProtocolChannelSecure();
  }

  /**
   * Check if the admin server is listening on a secure port.
   *
   * @return true if admin server is listening on a secure port
   */
  private boolean isAdminServerProtocolChannelSecure() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .isLocalAdminProtocolChannelSecure();
  }


  Integer getLocalAdminProtocolChannelPort() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .getLocalAdminProtocolChannelPort();
  }

  private String getDataHome() {
    String dataHome = getDomain().getDataHome();
    return dataHome != null && !dataHome.isEmpty() ? dataHome + File.separator + getDomainUid() : null;
  }

  private String getEffectiveLogHome() {
    return getDomain().getEffectiveLogHome();
  }

  private String isIncludeServerOutInPodLog() {
    return Boolean.toString(getDomain().isIncludeServerOutInPodLog());
  }

  private String getRuntimeEncryptionSecret() {
    return getDomain().getRuntimeEncryptionSecret();
  }

  private List<V1ContainerPort> getContainerPorts() {
    if (scan != null) {
      List<V1ContainerPort> ports = new ArrayList<>();
      if (scan.getNetworkAccessPoints() != null) {
        for (NetworkAccessPoint nap : scan.getNetworkAccessPoints()) {
          String napName = nap.getName();
          V1ContainerPort port =
              new V1ContainerPort()
                  .name(LegalNames.toDns1123LegalName(napName))
                  .containerPort(nap.getListenPort())
                  .protocol("TCP");
          ports.add(port);
        }
      }
      // Istio type is already passed from the introspector output, no need to create it again
      if (!this.getDomain().isIstioEnabled()) {
        if (scan.getListenPort() != null) {
          String napName = "default";
          ports.add(
              new V1ContainerPort()
                  .name(napName)
                  .containerPort(scan.getListenPort())
                  .protocol("TCP"));
        }
        if (scan.getSslListenPort() != null) {
          String napName = "default-secure";
          ports.add(
              new V1ContainerPort()
                  .name(napName)
                  .containerPort(scan.getSslListenPort())
                  .protocol("TCP"));
        }
        if (scan.getAdminPort() != null) {
          String napName = "default-admin";
          ports.add(
              new V1ContainerPort()
                  .name(napName)
                  .containerPort(scan.getAdminPort())
                  .protocol("TCP"));
        }
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
          .deletePodAsync(getPodName(), getNamespace(), getDomainUid(), new V1DeleteOptions(), deleteResponse(next));
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

  /**
   * Creates a Progressing step before an action step.
   *
   * @param actionStep the step to perform after the ProgressingStep.
   * @return a step to be scheduled.
   */
  abstract Step createProgressingStep(Step actionStep);

  private Step patchCurrentPod(V1Pod currentPod, Step next) {
    return createProgressingStep(patchPod(currentPod, next));
  }

  protected Step patchPod(V1Pod currentPod, Step next) {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();

    KubernetesUtils.addPatches(
        patchBuilder, "/metadata/labels/", getLabels(currentPod), getPodLabels());
    KubernetesUtils.addPatches(
        patchBuilder, "/metadata/annotations/", getAnnotations(currentPod), getPodAnnotations());

    return new CallBuilder()
            .patchPodAsync(getPodName(), getNamespace(), getDomainUid(),
            new V1Patch(patchBuilder.build().toString()), patchResponse(next));
  }

  private Map<String, String> getLabels(V1Pod pod) {
    return Optional.ofNullable(pod.getMetadata()).map(V1ObjectMeta::getLabels).orElseGet(Collections::emptyMap);
  }

  private Map<String, String> getAnnotations(V1Pod pod) {
    return Optional.ofNullable(pod.getMetadata()).map(V1ObjectMeta::getAnnotations).orElseGet(Collections::emptyMap);
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
    return KubernetesUtils.isMissingValues(getLabels(currentPod), getPodLabels())
        || KubernetesUtils.isMissingValues(getAnnotations(currentPod), getPodAnnotations());
  }

  private boolean canUseCurrentPod(V1Pod currentPod) {
    boolean useCurrent =
        AnnotationHelper.getHash(getPodModel()).equals(AnnotationHelper.getHash(currentPod));
    if (!useCurrent && AnnotationHelper.getDebugString(currentPod).length() > 0) {
      LOGGER.fine(
          MessageKeys.POD_DUMP,
          AnnotationHelper.getDebugString(currentPod),
          AnnotationHelper.getDebugString(getPodModel()));
    }

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

  V1Pod createPodModel() {
    return withNonHashedElements(AnnotationHelper.withSha256Hash(createPodRecipe()));
  }

  @Override
  protected Map<String, String> augmentSubVars(Map<String, String> vars) {
    String clusterName = getClusterName();
    if (clusterName != null) {
      vars.put("CLUSTER_NAME", clusterName);
    }
    return vars;
  }

  V1Pod withNonHashedElements(V1Pod pod) {
    V1ObjectMeta metadata = Objects.requireNonNull(pod.getMetadata());
    // Adds labels and annotations to a pod, skipping any whose names begin with "weblogic."
    getPodLabels().entrySet().stream()
        .filter(PodStepContext::isCustomerItem)
        .forEach(e -> metadata.putLabelsItem(e.getKey(), e.getValue()));
    getPodAnnotations().entrySet().stream()
        .filter(PodStepContext::isCustomerItem)
        .forEach(e -> metadata.putAnnotationsItem(e.getKey(), e.getValue()));

    setTerminationGracePeriod(pod);
    getContainer(pod).map(V1Container::getEnv).ifPresent(this::updateEnv);

    updateForOwnerReference(metadata);
    return updateForDeepSubstitution(pod.getSpec(), pod);
  }

  private void setTerminationGracePeriod(V1Pod pod) {
    Objects.requireNonNull(pod.getSpec()).terminationGracePeriodSeconds(getTerminationGracePeriodSeconds());
  }

  private long getTerminationGracePeriodSeconds() {
    return getShutdownSpec().getTimeoutSeconds() + PodHelper.DEFAULT_ADDITIONAL_DELETE_TIME;
  }

  private void updateEnv(List<V1EnvVar> env) {
    updateEnvForShutdown(env);
    updateEnvForStartupMode(env);
    defineConfigOverride(env);
  }

  private void updateEnvForShutdown(List<V1EnvVar> env) {
    if (scan != null) {
      Integer localAdminPort = scan.getLocalAdminProtocolChannelPort();
      addOrReplaceEnvVar(env, "LOCAL_ADMIN_PORT", String.valueOf(localAdminPort));
      addOrReplaceEnvVar(env, "LOCAL_ADMIN_PROTOCOL", localAdminPort.equals(scan.getListenPort()) ? "t3" : "t3s");
    }

    Shutdown shutdown = getShutdownSpec();
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_TYPE", shutdown.getShutdownType());
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_TIMEOUT", String.valueOf(shutdown.getTimeoutSeconds()));
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_IGNORE_SESSIONS", String.valueOf(shutdown.getIgnoreSessions()));
  }

  private Shutdown getShutdownSpec() {
    return Optional.ofNullable(getServerSpec()).map(ServerSpec::getShutdown).orElse(new Shutdown());
  }

  private void updateEnvForStartupMode(List<V1EnvVar> env) {
    Optional.ofNullable(getServerSpec())
          .map(ServerSpec::getDesiredState)
          .filter(this::isNotRunning)
          .ifPresent(s -> addDefaultEnvVarIfMissing(env, "STARTUP_MODE", s));
  }

  private boolean isNotRunning(String desiredState) {
    return !WebLogicConstants.RUNNING_STATE.equals(desiredState);
  }

  private void defineConfigOverride(List<V1EnvVar> env) {
    if (distributeOverridesDynamically()) {
      addDefaultEnvVarIfMissing(env, ServerEnvVars.DYNAMIC_CONFIG_OVERRIDE, "true");
    }
  }

  // Creates a pod model containing elements which are not patchable.
  private V1Pod createPodRecipe() {
    return new V1Pod().metadata(createMetadata()).spec(createSpec(TuningParameters.getInstance()));
  }

  protected V1ObjectMeta createMetadata() {
    final V1ObjectMeta metadata = new V1ObjectMeta().name(getPodName()).namespace(getNamespace());

    LOGGER.finest("PodStepContext.createMetaData domainRestartVersion from INIT "
        + domainRestartVersion);
    LOGGER.finest("PodStepContext.createMetaData domainRestartVersion from serverspec "
        + getServerSpec().getDomainRestartVersion());
    LOGGER.finest("PodStepContext.createMetaData domainIntrospectVersion from spec "
        + getDomain().getIntrospectVersion());
    
    metadata
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

    Optional.ofNullable(miiDomainZipHash)
          .ifPresent(hash -> addHashLabel(metadata, LabelConstants.MODEL_IN_IMAGE_DOMAINZIP_HASH, hash));
    Optional.ofNullable(miiModelSecretsHash)
          .ifPresent(hash -> addHashLabel(metadata, LabelConstants.MODEL_IN_IMAGE_MODEL_SECRETS_HASH, hash));

    // Add prometheus annotations. This will overwrite any custom annotations with same name.
    AnnotationHelper.annotateForPrometheus(metadata, getDefaultPort());
    return metadata;
  }

  private void addHashLabel(V1ObjectMeta metadata, String label, String hash) {
    metadata.putLabelsItem(label, formatHashLabel(hash));
  }

  private static String formatHashLabel(String hash) {
    return String.format("md5.%s.md5", hash.replace("\n", ""));
  }


  protected V1PodSpec createSpec(TuningParameters tuningParameters) {
    V1PodSpec podSpec = createPodSpec(tuningParameters)
        .readinessGates(getReadinessGates())
        .initContainers(getServerSpec().getInitContainers());

    for (V1Volume additionalVolume : getVolumes(getDomainUid())) {
      podSpec.addVolumesItem(additionalVolume);
    }
    return podSpec;
  }

  // ---------------------- model methods ------------------------------

  private List<V1PodReadinessGate> getReadinessGates() {
    List<V1PodReadinessGate> readinessGates = getServerSpec().getReadinessGates();
    return readinessGates.isEmpty() ? null : readinessGates;
  }

  private List<V1Volume> getVolumes(String domainUid) {
    List<V1Volume> volumes = PodDefaults.getStandardVolumes(domainUid);
    volumes.addAll(getServerSpec().getAdditionalVolumes());
    if (getDomainHomeSourceType() == DomainSourceType.FromModel) {
      volumes.add(createRuntimeEncryptionSecretVolume());
    }

    return volumes;
  }

  protected V1Container createContainer(TuningParameters tuningParameters) {
    V1Container v1Container = super.createContainer(tuningParameters)
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

  private V1VolumeMount createRuntimeEncryptionSecretVolumeMount() {
    return new V1VolumeMount().name(RUNTIME_ENCRYPTION_SECRET_VOLUME)
        .mountPath(RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH).readOnly(true);
  }

  protected String getContainerName() {
    return KubernetesConstants.CONTAINER_NAME;
  }

  protected List<String> getContainerCommand() {
    return Collections.singletonList(START_SERVER);
  }

  protected List<V1Container> getContainers() {
    return getServerSpec().getContainers();
  }

  private List<V1VolumeMount> getVolumeMounts() {
    List<V1VolumeMount> mounts = PodDefaults.getStandardVolumeMounts(getDomainUid());
    mounts.addAll(getServerSpec().getAdditionalVolumeMounts());
    if (getDomainHomeSourceType() == DomainSourceType.FromModel) {
      mounts.add(createRuntimeEncryptionSecretVolumeMount());
    }
    return mounts;
  }

  private V1Volume createRuntimeEncryptionSecretVolume() {
    return new V1Volume()
        .name(RUNTIME_ENCRYPTION_SECRET_VOLUME)
        .secret(getRuntimeEncryptionSecretVolumeSource(getRuntimeEncryptionSecret()));
  }

  private V1SecretVolumeSource getRuntimeEncryptionSecretVolumeSource(String name) {
    return new V1SecretVolumeSource().secretName(name).defaultMode(420);
  }

  /**
   * Sets the environment variables used by operator/src/main/resources/scripts/startServer.sh
   * @param vars a list to which new variables are to be added
   */
  void addStartupEnvVars(List<V1EnvVar> vars) {
    addEnvVar(vars, ServerEnvVars.DOMAIN_NAME, getDomainName());
    addEnvVar(vars, ServerEnvVars.DOMAIN_HOME, getDomainHome());
    addEnvVar(vars, ServerEnvVars.ADMIN_NAME, getAsName());
    addEnvVar(vars, ServerEnvVars.ADMIN_PORT, getAsPort().toString());
    addEnvVarIfTrue(isLocalAdminProtocolChannelSecure(), vars, "ADMIN_PORT_SECURE");
    addEnvVarIfTrue(isAdminServerProtocolChannelSecure(), vars, ServerEnvVars.ADMIN_SERVER_PORT_SECURE);
    addEnvVar(vars, ServerEnvVars.SERVER_NAME, getServerName());
    addEnvVar(vars, ServerEnvVars.DOMAIN_UID, getDomainUid());
    addEnvVar(vars, ServerEnvVars.NODEMGR_HOME, NODEMGR_HOME);
    addEnvVar(vars, ServerEnvVars.LOG_HOME, getEffectiveLogHome());
    addEnvVar(vars, ServerEnvVars.SERVER_OUT_IN_POD_LOG, isIncludeServerOutInPodLog());
    addEnvVar(vars, ServerEnvVars.SERVICE_NAME, LegalNames.toServerServiceName(getDomainUid(), getServerName()));
    addEnvVar(vars, ServerEnvVars.AS_SERVICE_NAME, LegalNames.toServerServiceName(getDomainUid(), getAsName()));
    Optional.ofNullable(getDataHome()).ifPresent(v -> addEnvVar(vars, ServerEnvVars.DATA_HOME, v));
    addEnvVarIfTrue(mockWls(), vars, "MOCK_WLS");
  }

  private String getDomainHome() {
    return getDomain().getDomainHome();
  }

  private boolean distributeOverridesDynamically() {
    return getDomain().distributeOverridesDynamically();
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
      boolean istioEnabled = getDomain().isIstioEnabled();
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
            Objects.requireNonNull(currentPod.getMetadata()).getName(),
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

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
      if (UnrecoverableErrorBuilder.isAsyncCallFailure(callResponse)) {
        return updateDomainStatus(packet, callResponse);
      } else {
        return onFailure(getConflictStep(), packet, callResponse);
      }
    }

    private NextAction updateDomainStatus(Packet packet, CallResponse<V1Pod> callResponse) {
      return doNext(DomainStatusUpdater.createFailedStep(callResponse, null), packet);
    }
  }

  private class CreateResponseStep extends BaseResponseStep {

    CreateResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
      logPodCreated();
      if (callResponse.getResult() != null) {
        info.updateLastKnownServerStatus(getServerName(), WebLogicConstants.STARTING_STATE);
        setRecordedPod(callResponse.getResult());
      }

      boolean waitForPodReady =
          (boolean) Optional.ofNullable(packet.get(ProcessingConstants.WAIT_FOR_POD_READY)).orElse(false);

      if (waitForPodReady) {
        PodAwaiterStepFactory pw = packet.getSpi(PodAwaiterStepFactory.class);
        return doNext(pw.waitForReady(callResponse.getResult(), getNext()), packet);
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
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {

      V1Pod newPod = callResponse.getResult();
      logPodPatched();
      if (newPod != null) {
        setRecordedPod(newPod);
      }

      return doNext(next, packet);
    }
  }

}
