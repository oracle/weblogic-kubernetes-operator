// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.utils.ChecksumUtils;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImageEnvVars;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars;
import oracle.kubernetes.weblogic.domain.model.Istio;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;

import static oracle.kubernetes.operator.ProcessingConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.utils.OperatorUtils.emptyToNull;

public abstract class JobStepContext extends BasePodStepContext {
  static final long DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS = 60L;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH =
        "/weblogic-operator/scripts/introspectDomain.sh";
  private static final int MAX_ALLOWED_VOLUME_NAME_LENGTH = 63;
  private static final String VOLUME_NAME_SUFFIX = "-volume";
  private static final String CONFIGMAP_TYPE = "cm";
  private static final String SECRET_TYPE = "st";
  private final WlsDomainConfig domainTopology;
  private V1Job jobModel;

  JobStepContext(Packet packet) {
    super(packet.getSpi(DomainPresenceInfo.class));
    domainTopology = packet.getValue(ProcessingConstants.DOMAIN_TOPOLOGY);
  }

  Step getConflictStep(Step next) {
    return new ConflictStep(next);
  }

  private static V1VolumeMount readOnlyVolumeMount(String volumeName, String mountPath) {
    return volumeMount(volumeName, mountPath).readOnly(true);
  }

  private static V1VolumeMount volumeMount(String volumeName, String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }

  void init() {
    jobModel = createJobModel();
  }

  // ------------------------ data methods ----------------------------

  V1Job getJobModel() {
    return jobModel;
  }

  String getNamespace() {
    return info.getNamespace();
  }

  String getDomainUid() {
    return getDomain().getDomainUid();
  }

  Domain getDomain() {
    return info.getDomain();
  }

  ServerSpec getServerSpec() {
    return getDomain().getAdminServerSpec();
  }

  abstract String getJobName();

  @Override
  protected String getMainContainerName() {
    return getJobName();
  }

  @Override
  protected Map<String, String> augmentSubVars(Map<String, String> vars) {
    // For other introspector job pod content, we use the values that would apply administration server; however,
    // since we won't know the name of the administation server from the domain configuration until introspection
    // has run, we will use the hardcoded value "introspector" as the server name.
    vars.put("SERVER_NAME", "introspector");
    return vars;
  }

  String getWebLogicCredentialsSecretName() {
    return getDomain().getWebLogicCredentialsSecretName();
  }

  String getOpssWalletPasswordSecretName() {
    return getDomain().getOpssWalletPasswordSecret();
  }

  String getOpssWalletFileSecretName() {
    return getDomain().getOpssWalletFileSecret();
  }

  String getRuntimeEncryptionSecretName() {
    return getDomain().getRuntimeEncryptionSecret();
  }


  // ----------------------- step methods ------------------------------

  abstract List<V1Volume> getAdditionalVolumes();

  abstract List<V1VolumeMount> getAdditionalVolumeMounts();

  /**
   * Creates the specified new pod and performs any additional needed processing.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  abstract Step createNewJob(Step next);

  /**
   * Creates the specified new job.
   *
   * @param next the next step to perform after the job creation is complete.
   * @return a step to be scheduled.
   */
  Step createJob(Step next) {
    return new CallBuilder().createJobAsync(getNamespace(), getDomainUid(), getJobModel(), createResponse(next));
  }

  private void logJobCreated() {
    LOGGER.info(getJobCreatedMessageKey(), getJobName());
  }

  abstract String getJobCreatedMessageKey();

  String getNodeManagerHome() {
    return NODEMGR_HOME;
  }

  String getDataHome() {
    String dataHome = getDomain().getDataHome();
    return dataHome != null && !dataHome.isEmpty() ? dataHome + File.separator + getDomainUid() : null;
  }

  String getModelHome() {
    return getDomain().getModelHome();
  }

  String getWdtInstallHome() {
    return getDomain().getWdtInstallHome();
  }

  List<AuxiliaryImage> getAuxiliaryImages() {
    return getServerSpec().getAuxiliaryImages();
  }

  String getWdtDomainType() {
    return getDomain().getWdtDomainType();
  }

  DomainSourceType getDomainHomeSourceType() {
    return getDomain().getDomainHomeSourceType();
  }

  boolean isUseOnlineUpdate() {
    return getDomain().isUseOnlineUpdate();
  }

  public boolean isIstioEnabled() {
    return getDomain().isIstioEnabled();
  }

  public boolean isAdminChannelPortForwardingEnabled(DomainSpec domainSpec) {
    return getDomain().isAdminChannelPortForwardingEnabled(domainSpec);
  }

  int getIstioReadinessPort() {
    return getDomain().getIstioReadinessPort();
  }

  int getIstioReplicationPort() {
    return getDomain().getIstioReplicationPort();
  }

  boolean isLocalhostBindingsEnabled() {
    return getDomain().isLocalhostBindingsEnabled();
  }

  String getEffectiveLogHome() {
    return getDomain().getEffectiveLogHome();
  }

  String getIncludeServerOutInPodLog() {
    return Boolean.toString(getDomain().isIncludeServerOutInPodLog());
  }

  String getHttpAccessLogInLogHome() {
    return Boolean.toString(getDomain().isHttpAccessLogInLogHome());
  }

  String getIntrospectHome() {
    return getDomainHome();
  }

  private List<String> getConfigOverrideSecrets() {
    return getDomain().getConfigOverrideSecrets();
  }

  private String getConfigOverrides() {
    return emptyToNull(getDomain().getConfigOverrides());
  }

  @Override
  List<V1EnvVar> getConfiguredEnvVars() {
    // Pod for introspector job would use same environment variables as for admin server
    List<V1EnvVar> vars =
        PodHelper.createCopy(getDomain().getAdminServerSpec().getEnvironmentVariables());

    addEnvVar(vars, ServerEnvVars.DOMAIN_UID, getDomainUid());
    addEnvVar(vars, ServerEnvVars.DOMAIN_HOME, getDomainHome());
    addEnvVar(vars, ServerEnvVars.NODEMGR_HOME, getNodeManagerHome());
    addEnvVar(vars, ServerEnvVars.LOG_HOME, getEffectiveLogHome());
    addEnvVar(vars, ServerEnvVars.SERVER_OUT_IN_POD_LOG, getIncludeServerOutInPodLog());
    addEnvVar(vars, ServerEnvVars.ACCESS_LOG_IN_LOG_HOME, getHttpAccessLogInLogHome());
    addEnvVar(vars, IntrospectorJobEnvVars.NAMESPACE, getNamespace());
    addEnvVar(vars, IntrospectorJobEnvVars.INTROSPECT_HOME, getIntrospectHome());
    addEnvVar(vars, IntrospectorJobEnvVars.CREDENTIALS_SECRET_NAME, getWebLogicCredentialsSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.OPSS_KEY_SECRET_NAME, getOpssWalletPasswordSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.OPSS_WALLETFILE_SECRET_NAME, getOpssWalletFileSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.RUNTIME_ENCRYPTION_SECRET_NAME, getRuntimeEncryptionSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.WDT_DOMAIN_TYPE, getWdtDomainType());
    addEnvVar(vars, IntrospectorJobEnvVars.DOMAIN_SOURCE_TYPE, getDomainHomeSourceType().toString());
    addEnvVar(vars, IntrospectorJobEnvVars.ISTIO_ENABLED, Boolean.toString(isIstioEnabled()));
    addEnvVar(vars, IntrospectorJobEnvVars.ADMIN_CHANNEL_PORT_FORWARDING_ENABLED,
        Boolean.toString(isAdminChannelPortForwardingEnabled(getDomain().getSpec())));
    Optional.ofNullable(getKubernetesPlatform())
        .ifPresent(v -> addEnvVar(vars, ServerEnvVars.KUBERNETES_PLATFORM, v));

    addEnvVar(vars, IntrospectorJobEnvVars.ISTIO_READINESS_PORT, Integer.toString(getIstioReadinessPort()));
    addEnvVar(vars, IntrospectorJobEnvVars.ISTIO_POD_NAMESPACE, getNamespace());
    if (isIstioEnabled()) {
      // Only add the following Istio configuration environment variables when explicitly configured
      // otherwise the introspection job will needlessly run, after operator upgrade, based on generated
      // hash code of the set of environment variables.
      if (!isLocalhostBindingsEnabled()) {
        addEnvVar(vars, IntrospectorJobEnvVars.ISTIO_USE_LOCALHOST_BINDINGS, "false");
      }

      if (getIstioReplicationPort() != Istio.DEFAULT_REPLICATION_PORT) {
        addEnvVar(vars, IntrospectorJobEnvVars.ISTIO_REPLICATION_PORT, Integer.toString(getIstioReplicationPort()));
      }
    }
    if (isUseOnlineUpdate()) {
      addEnvVar(vars, IntrospectorJobEnvVars.MII_USE_ONLINE_UPDATE, "true");
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_ACTIVATE_TIMEOUT,
          Long.toString(getDomain().getWDTActivateTimeoutMillis()));
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_CONNECT_TIMEOUT,
          Long.toString(getDomain().getWDTConnectTimeoutMillis()));
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_DEPLOY_TIMEOUT,
          Long.toString(getDomain().getWDTDeployTimeoutMillis()));
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_REDEPLOY_TIMEOUT,
          Long.toString(getDomain().getWDTReDeployTimeoutMillis()));
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_UNDEPLOY_TIMEOUT,
          Long.toString(getDomain().getWDTUnDeployTimeoutMillis()));
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_START_APPLICATION_TIMEOUT,
          Long.toString(getDomain().getWDTStartApplicationTimeoutMillis()));
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_STOP_APPLICAITON_TIMEOUT,
          Long.toString(getDomain().getWDTStopApplicationTimeoutMillis()));
      addEnvVar(vars, IntrospectorJobEnvVars.MII_WDT_SET_SERVERGROUPS_TIMEOUT,
          Long.toString(getDomain().getWDTSetServerGroupsTimeoutMillis()));
    }

    String dataHome = getDataHome();
    if (dataHome != null && !dataHome.isEmpty()) {
      addEnvVar(vars, ServerEnvVars.DATA_HOME, dataHome);
    }

    // Populate env var list used by the MII introspector job's 'short circuit' MD5
    // check. To prevent a false trip of the circuit breaker, the list must be the
    // same regardless of whether domainTopology == null.
    StringBuilder sb = new StringBuilder(vars.size() * 32);
    for (V1EnvVar var : vars) {
      sb.append(var.getName()).append(',');
    }
    sb.deleteCharAt(sb.length() - 1);
    addEnvVar(vars, "OPERATOR_ENVVAR_NAMES", sb.toString());

    if (domainTopology != null) {
      // The domainTopology != null when the job is rerun for the same domain. In which
      // case we should now know how to contact the admin server, the admin server may
      // already be running, and the job may want to contact the admin server.

      addEnvVar(vars, "ADMIN_NAME", getAsName());
      addEnvVar(vars, "ADMIN_PORT", getAsPort().toString());
      if (isLocalAdminProtocolChannelSecure()) {
        addEnvVar(vars, "ADMIN_PORT_SECURE", "true");
      }
      addEnvVar(vars, "AS_SERVICE_NAME", getAsServiceName());
    }

    String modelHome = getModelHome();
    if (modelHome != null && !modelHome.isEmpty()) {
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_MODEL_HOME, modelHome);
    }

    String wdtInstallHome = getWdtInstallHome();
    if (wdtInstallHome != null && !wdtInstallHome.isEmpty()) {
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_INSTALL_HOME, wdtInstallHome);
    }

    Optional.ofNullable(getAuxiliaryImagePaths(getServerSpec().getAuxiliaryImages(),
        getDomain().getAuxiliaryImageVolumes()))
        .ifPresent(c -> addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATHS, c));
    return vars;
  }

  private boolean isLocalAdminProtocolChannelSecure() {
    return domainTopology
        .getServerConfig(getAsName())
        .isLocalAdminProtocolChannelSecure();
  }

  private String getAsServiceName() {
    return LegalNames.toServerServiceName(getDomainUid(), getAsName());
  }

  private Integer getAsPort() {
    return domainTopology
        .getServerConfig(getAsName())
        .getLocalAdminProtocolChannelPort();
  }

  private String getAsName() {
    return domainTopology.getAdminServerName();
  }

  private String getIntrospectVersionLabel() {
    return Optional.ofNullable(getDomain().getIntrospectVersion()).orElse(null);
  }

  private long getIntrospectorJobActiveDeadlineSeconds(TuningParameters.PodTuning podTuning) {
    return Optional.ofNullable(getDomain().getIntrospectorJobActiveDeadlineSeconds())
        .orElse(podTuning.introspectorJobActiveDeadlineSeconds);
  }

  // ---------------------- model methods ------------------------------

  private String getWdtConfigMap() {
    return emptyToNull(getDomain().getWdtConfigMap());
  }

  private ResponseStep<V1Job> createResponse(Step next) {
    return new CreateResponseStep(next);
  }

  private V1Job createJobModel() {
    return new V1Job()
          .metadata(createMetadata())
          .spec(createJobSpec(TuningParameters.getInstance()));
  }

  private V1ObjectMeta createMetadata() {
    return updateForOwnerReference(
        new V1ObjectMeta()
          .name(getJobName())
          .namespace(getNamespace())
          .putLabelsItem(LabelConstants.INTROSPECTION_STATE_LABEL, getIntrospectVersionLabel())
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"));
  }

  private long getActiveDeadlineSeconds(TuningParameters.PodTuning podTuning) {
    return getIntrospectorJobActiveDeadlineSeconds(podTuning)
          + (DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS * getIntrospectJobFailureCount());
  }

  private Integer getIntrospectJobFailureCount() {
    return Optional.ofNullable(info.getDomain().getStatus())
            .map(s -> s.getIntrospectJobFailureCount()).orElse(0);
  }

  V1JobSpec createJobSpec(TuningParameters tuningParameters) {
    LOGGER.fine(
          "Creating job "
                + getJobName()
                + " with activeDeadlineSeconds = "
                + getActiveDeadlineSeconds(tuningParameters.getPodTuning()));

    return new V1JobSpec()
          .backoffLimit(0)
          .activeDeadlineSeconds(getActiveDeadlineSeconds(tuningParameters.getPodTuning()))
          .template(createPodTemplateSpec(tuningParameters));
  }

  private V1PodTemplateSpec createPodTemplateSpec(TuningParameters tuningParameters) {
    V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec()
          .metadata(createPodTemplateMetadata())
          .spec(createPodSpec(tuningParameters));
    addInitContainers(podTemplateSpec.getSpec(), tuningParameters);
    addEmptyDirVolume(podTemplateSpec.getSpec(), info.getDomain().getAuxiliaryImageVolumes());

    return updateForDeepSubstitution(podTemplateSpec.getSpec(), podTemplateSpec);
  }

  private V1ObjectMeta createPodTemplateMetadata() {
    V1ObjectMeta metadata = new V1ObjectMeta()
          .name(getJobName())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
          .putLabelsItem(
                LabelConstants.JOBNAME_LABEL, LegalNames.toJobIntrospectorName(getDomainUid()));
    if (isIstioEnabled()) {
      metadata.putAnnotationsItem("sidecar.istio.io/inject", "false");
    }
    return metadata;
  }

  private V1PodSpec addInitContainers(V1PodSpec podSpec, TuningParameters tuningParameters) {
    List<V1Container> initContainers = new ArrayList<>();
    Optional.ofNullable(getAuxiliaryImages()).ifPresent(auxImages -> addInitContainers(initContainers, auxImages));
    initContainers.addAll(getAdditionalInitContainers().stream()
            .filter(container -> container.getName().startsWith(COMPATIBILITY_MODE))
            .map(c -> c.env(createEnv(c, tuningParameters)).resources(createResources()))
            .collect(Collectors.toList()));
    podSpec.initContainers(initContainers);
    return podSpec;
  }

  private void addInitContainers(List<V1Container> initContainers, List<AuxiliaryImage> auxiliaryImages) {
    IntStream.range(0, auxiliaryImages.size()).forEach(idx ->
            initContainers.add(createInitContainerForAuxiliaryImage(auxiliaryImages.get(idx), idx)));
  }

  protected List<V1EnvVar> createEnv(V1Container c, TuningParameters tuningParameters) {
    List<V1EnvVar> initContainerEnvVars = new ArrayList<>();
    Optional.ofNullable(c.getEnv()).ifPresent(initContainerEnvVars::addAll);
    if (!c.getName().startsWith(COMPATIBILITY_MODE)) {
      getEnvironmentVariables(tuningParameters).stream()
              .forEach(var -> addIfMissing(initContainerEnvVars, var.getName(), var.getValue(), var.getValueFrom()));
    }
    return initContainerEnvVars;
  }

  private List<V1Container> getAdditionalInitContainers() {
    return getServerSpec().getInitContainers();
  }

  protected V1PodSpec createPodSpec(TuningParameters tuningParameters) {
    V1PodSpec podSpec = super.createPodSpec(tuningParameters)
            .activeDeadlineSeconds(getActiveDeadlineSeconds(tuningParameters.getPodTuning()))
            .restartPolicy("Never")
            .serviceAccountName(info.getDomain().getSpec().getServiceAccountName())
            .addVolumesItem(new V1Volume().name(SECRETS_VOLUME).secret(getSecretsVolume()))
            .addVolumesItem(
                new V1Volume().name(SCRIPTS_VOLUME).configMap(getConfigMapVolumeSource()))
            .addVolumesItem(
                new V1Volume()
                    .name("mii" + IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX)
                    .configMap(getIntrospectMD5VolumeSource()));
    if (getOpssWalletPasswordSecretVolume() != null) {
      podSpec.addVolumesItem(new V1Volume().name(OPSS_KEYPASSPHRASE_VOLUME).secret(
          getOpssWalletPasswordSecretVolume()));
    }
    if (getOpssWalletFileSecretName() != null) {
      podSpec.addVolumesItem(new V1Volume().name(OPSS_WALLETFILE_VOLUME).secret(
              getOpssWalletFileSecretVolume()));
    }

    podSpec.setImagePullSecrets(info.getDomain().getSpec().getImagePullSecrets());

    for (V1Volume additionalVolume : getAdditionalVolumes()) {
      podSpec.addVolumesItem(additionalVolume);
    }

    getConfigOverrideSecrets().forEach(secretName -> addConfigOverrideSecretVolume(podSpec, secretName));
    Optional.ofNullable(getConfigOverrides()).ifPresent(overrides -> addConfigOverrideVolume(podSpec, overrides));

    if (isSourceWdt()) {
      Optional.ofNullable(getWdtConfigMap()).ifPresent(mapName -> addWdtConfigMapVolume(podSpec, mapName));
      addWdtSecretVolume(podSpec);
    }
    return podSpec;
  }

  private void addConfigOverrideSecretVolume(V1PodSpec podSpec, String secretName) {
    podSpec.addVolumesItem(
          new V1Volume()
                .name(getVolumeName(secretName, SECRET_TYPE))
                .secret(getOverrideSecretVolumeSource(secretName)));
  }

  private void addConfigOverrideVolume(V1PodSpec podSpec, String configOverrides) {
    podSpec.addVolumesItem(
          new V1Volume()
                .name(getVolumeName(configOverrides, CONFIGMAP_TYPE))
                .configMap(getOverridesVolumeSource(configOverrides)));
  }

  private boolean isSourceWdt() {
    return getDomainHomeSourceType() == DomainSourceType.FromModel;
  }

  private void addWdtConfigMapVolume(V1PodSpec podSpec, String configMapName) {
    podSpec.addVolumesItem(
        new V1Volume()
            .name(getVolumeName(configMapName, CONFIGMAP_TYPE))
            .configMap(getWdtConfigMapVolumeSource(configMapName)));
  }

  private void addWdtSecretVolume(V1PodSpec podSpec) {
    podSpec.addVolumesItem(
        new V1Volume()
            .name(RUNTIME_ENCRYPTION_SECRET_VOLUME)
            .secret(getRuntimeEncryptionSecretVolume()));
  }

  protected V1Container createPrimaryContainer(TuningParameters tuningParameters) {
    V1Container container = super.createPrimaryContainer(tuningParameters)
        .addVolumeMountsItem(readOnlyVolumeMount(SECRETS_VOLUME, SECRETS_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH))
        .addVolumeMountsItem(
          volumeMount(
              "mii" + IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX,
              "/weblogic-operator/introspectormii")
              .readOnly(false));

    if (getOpssWalletPasswordSecretVolume() != null) {
      container.addVolumeMountsItem(readOnlyVolumeMount(OPSS_KEYPASSPHRASE_VOLUME, OPSS_KEY_MOUNT_PATH));
    }
    if (getOpssWalletFileSecretVolume() != null) {
      container.addVolumeMountsItem(readOnlyVolumeMount(OPSS_WALLETFILE_VOLUME, OPSS_WALLETFILE_MOUNT_PATH));
    }
    
    for (V1VolumeMount additionalVolumeMount : getAdditionalVolumeMounts()) {
      container.addVolumeMountsItem(additionalVolumeMount);
    }

    if (getConfigOverrides() != null && getConfigOverrides().length() > 0) {
      container.addVolumeMountsItem(
            readOnlyVolumeMount(getVolumeName(getConfigOverrides(), CONFIGMAP_TYPE), OVERRIDES_CM_MOUNT_PATH));
    }

    Optional.ofNullable(getServerSpec().getAuxiliaryImages()).ifPresent(auxiliaryImages ->
            auxiliaryImages.forEach(cm -> addVolumeMount(container, cm)));

    List<String> configOverrideSecrets = getConfigOverrideSecrets();
    for (String secretName : configOverrideSecrets) {
      container.addVolumeMountsItem(
            readOnlyVolumeMount(
                  getVolumeName(secretName, SECRET_TYPE), OVERRIDE_SECRETS_MOUNT_PATH + '/' + secretName));
    }

    if (isSourceWdt()) {
      if (getWdtConfigMap() != null) {
        container.addVolumeMountsItem(
            readOnlyVolumeMount(getVolumeName(getWdtConfigMap(), CONFIGMAP_TYPE), WDTCONFIGMAP_MOUNT_PATH));
      }
      container.addVolumeMountsItem(
          readOnlyVolumeMount(RUNTIME_ENCRYPTION_SECRET_VOLUME,
              RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH));

    }

    return container;
  }

  private String getVolumeName(String resourceName, String type) {
    return getName(resourceName, type);
  }

  private String getName(String resourceName, String type) {
    return resourceName.length() > (MAX_ALLOWED_VOLUME_NAME_LENGTH - VOLUME_NAME_SUFFIX.length())
            ? getShortName(resourceName, type)
            : resourceName + VOLUME_NAME_SUFFIX;
  }

  private String getShortName(String resourceName, String type) {
    String volumeSuffix = VOLUME_NAME_SUFFIX + "-" + type + "-"
            + Optional.ofNullable(ChecksumUtils.getMD5Hash(resourceName)).orElse("");
    return resourceName.substring(0, MAX_ALLOWED_VOLUME_NAME_LENGTH - volumeSuffix.length()) + volumeSuffix;
  }

  protected String getContainerName() {
    return getJobName();
  }

  protected List<String> getContainerCommand() {
    return Collections.singletonList(WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH);
  }

  protected List<V1Container> getContainers() {
    // Returning an empty array since introspector pod does not start with any additional containers
    // configured in the ServerPod configuration
    List<V1Container> containers = new ArrayList<>();

    Optional.ofNullable(getDomain().getFluentdSpecification())
        .ifPresent(fluentd -> {
          if (Boolean.TRUE.equals(fluentd.getWatchIntrospectorLogs())) {
            FluentdHelper.addFluentdContainer(fluentd,
                    containers, getDomain(), true);
          }
        });

    return containers;

  }

  protected List<V1Volume> getFluentdVolumes() {
    List<V1Volume> volumes = new ArrayList<>();
    Optional.ofNullable(getDomain())
            .map(Domain::getFluentdSpecification)
            .ifPresent(c -> volumes.add(new V1Volume().name(FLUENTD_CONFIGMAP_VOLUME)
                    .configMap(new V1ConfigMapVolumeSource().name(FLUENTD_CONFIGMAP_NAME).defaultMode(420))));
    return volumes;
  }

  protected String getDomainHome() {
    return getDomain().getDomainHome();
  }

  private V1SecretVolumeSource getSecretsVolume() {
    return new V1SecretVolumeSource()
          .secretName(getWebLogicCredentialsSecretName())
          .defaultMode(420);
  }

  private V1SecretVolumeSource getRuntimeEncryptionSecretVolume() {
    V1SecretVolumeSource result = new V1SecretVolumeSource()
          .secretName(getRuntimeEncryptionSecretName())
          .defaultMode(420);
    result.setOptional(true);
    return result;
  }

  private V1SecretVolumeSource getOpssWalletPasswordSecretVolume() {
    if (getOpssWalletPasswordSecretName() != null) {
      V1SecretVolumeSource result =  new V1SecretVolumeSource()
          .secretName(getOpssWalletPasswordSecretName())
          .defaultMode(420);
      result.setOptional(true);
      return result;
    }
    return null;
  }

  private V1SecretVolumeSource getOpssWalletFileSecretVolume() {
    if (getOpssWalletFileSecretName() != null) {
      V1SecretVolumeSource result =  new V1SecretVolumeSource()
              .secretName(getOpssWalletFileSecretName())
              .defaultMode(420);
      result.setOptional(true);
      return result;
    }
    return null;
  }

  private V1ConfigMapVolumeSource getConfigMapVolumeSource() {
    return new V1ConfigMapVolumeSource()
          .name(KubernetesConstants.SCRIPT_CONFIG_MAP_NAME)
          .defaultMode(ALL_READ_AND_EXECUTE);
  }

  private V1ConfigMapVolumeSource getIntrospectMD5VolumeSource() {
    V1ConfigMapVolumeSource result =
        new V1ConfigMapVolumeSource()
            .name(getDomainUid() + IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX)
            .defaultMode(ALL_READ_AND_EXECUTE);
    result.setOptional(true);
    return result;
  }

  private V1SecretVolumeSource getOverrideSecretVolumeSource(String name) {
    return new V1SecretVolumeSource().secretName(name).defaultMode(420);
  }

  private V1ConfigMapVolumeSource getOverridesVolumeSource(String name) {
    return new V1ConfigMapVolumeSource().name(name).defaultMode(ALL_READ_AND_EXECUTE);
  }

  private class CreateResponseStep extends ResponseStep<V1Job> {
    CreateResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Job> callResponse) {
      if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
        return updateDomainStatus(packet, callResponse);
      } else {
        return super.onFailure(getConflictStep(getNext()), packet, callResponse);
      }
    }

    private NextAction updateDomainStatus(Packet packet, CallResponse<V1Job> callResponse) {
      return doNext(
            Step.chain(
                  DomainStatusUpdater.createFailureCountStep(),
                  DomainStatusUpdater.createFailureRelatedSteps(callResponse, null)),
            packet);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Job> callResponse) {
      logJobCreated();
      V1Job job = callResponse.getResult();
      if (job != null) {
        packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB, job);
      }
      return doNext(packet);
    }
  }

  class ConflictStep extends Step {
    ConflictStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(
              new CallBuilder().readJobAsync(getJobName(), info.getNamespace(), getDomainUid(),
                      new ReadResponseStep(getNext())), packet);
    }
  }

  private class ReadResponseStep extends DefaultResponseStep<V1Job> {
    ReadResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Job> callResponse) {
      return callResponse.getStatusCode() == KubernetesConstants.HTTP_NOT_FOUND
              ? doNext(createNewJob(getNext()), packet)
              : onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Job> callResponse) {
      return doNext(getConflictStep(getNext()), packet);
    }
  }




  private V1ConfigMapVolumeSource getWdtConfigMapVolumeSource(String name) {
    return new V1ConfigMapVolumeSource().name(name).defaultMode(ALL_READ_AND_EXECUTE);
  }

}
