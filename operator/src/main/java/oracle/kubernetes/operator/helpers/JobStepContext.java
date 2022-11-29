// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.common.utils.CommonUtils;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;

import static oracle.kubernetes.common.CommonConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.common.CommonConstants.SCRIPTS_MOUNTS_PATH;
import static oracle.kubernetes.common.CommonConstants.SCRIPTS_VOLUME;
import static oracle.kubernetes.common.utils.CommonUtils.MAX_ALLOWED_VOLUME_NAME_LENGTH;
import static oracle.kubernetes.common.utils.CommonUtils.VOLUME_NAME_SUFFIX;
import static oracle.kubernetes.operator.DomainStatusUpdater.createKubernetesFailureSteps;
import static oracle.kubernetes.operator.helpers.AffinityHelper.getDefaultAntiAffinity;
import static oracle.kubernetes.utils.OperatorUtils.emptyToNull;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_USE_ONLINE_UPDATE;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_ACTIVATE_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_CONNECT_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_DEPLOY_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_REDEPLOY_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_SET_SERVERGROUPS_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_START_APPLICATION_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_STOP_APPLICATION_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_UNDEPLOY_TIMEOUT;

public class JobStepContext extends BasePodStepContext {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH =
        "/weblogic-operator/scripts/introspectDomain.sh";
  private static final String CONFIGMAP_TYPE = "cm";
  private static final String SECRET_TYPE = "st";
  // domainTopology is null if this is 1st time we're running job for this domain
  private final WlsDomainConfig domainTopology;
  private static CommonUtils.CheckedFunction<String, String> getMD5Hash = CommonUtils::getMD5Hash;
  private V1Job jobModel;
  private Step conflictStep;

  JobStepContext(Packet packet) {
    super(packet.getSpi(DomainPresenceInfo.class));
    domainTopology = packet.getValue(ProcessingConstants.DOMAIN_TOPOLOGY);
    init();
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

  WlsDomainConfig getDomainTopology() {
    return domainTopology;
  }

  @Nullable
  V1PodSpec getJobModelPodSpec() {
    return Optional.ofNullable(getJobModel())
          .map(V1Job::getSpec)
          .map(V1JobSpec::getTemplate)
          .map(V1PodTemplateSpec::getSpec)
          .orElse(null);
  }

  String getNamespace() {
    return info.getNamespace();
  }

  String getDomainUid() {
    return getDomain().getDomainUid();
  }

  DomainResource getDomain() {
    return info.getDomain();
  }

  EffectiveServerSpec getServerSpec() {
    return getDomain().getAdminServerSpec();
  }

  String getJobName() {
    return createJobName(getDomainUid());
  }

  @Nonnull
  static String createJobName(String domainUid) {
    return LegalNames.toJobIntrospectorName(domainUid);
  }

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

  List<V1Volume> getAdditionalVolumes() {
    List<V1Volume> volumes = getDomain().getSpec().getAdditionalVolumes();
    getServerSpec().getAdditionalVolumes().forEach(volume -> addVolumeIfMissing(volume, volumes));
    return volumes;
  }

  private void addVolumeIfMissing(V1Volume volume, List<V1Volume> volumes) {
    if (!volumes.contains(volume) && volume.getName().startsWith(COMPATIBILITY_MODE)) {
      volumes.add(volume);
    }
  }

  List<V1VolumeMount> getAdditionalVolumeMounts() {
    List<V1VolumeMount> volumeMounts = getDomain().getSpec().getAdditionalVolumeMounts();
    getServerSpec().getAdditionalVolumeMounts().forEach(mount -> addVolumeMountIfMissing(mount, volumeMounts));
    return volumeMounts;
  }

  private void addVolumeMountIfMissing(V1VolumeMount mount, List<V1VolumeMount> volumeMounts) {
    if (!volumeMounts.contains(mount) && mount.getName().startsWith(COMPATIBILITY_MODE)) {
      volumeMounts.add(mount);
    }
  }

  private List<V1Container> getAdditionalInitContainers() {
    return getServerSpec().getInitContainers();
  }

  /**
   * Creates the specified new pod and performs any additional needed processing.
   *
   * @return a step to be scheduled.
   */
  Step createNewJob() {
    return createJob();
  }

  /**
   * Creates the specified new job.
   *
   * @return a step to be scheduled.
   */
  Step createJob() {
    conflictStep = new CallBuilder().createJobAsync(getNamespace(), getDomainUid(), getJobModel(), newCreateResponse());
    return conflictStep;
  }

  String getJobCreatedMessageKey() {
    return MessageKeys.JOB_CREATED;
  }

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

  ModelInImageDomainType getWdtDomainType() {
    return getDomain().getWdtDomainType();
  }

  DomainSourceType getDomainHomeSourceType() {
    return getDomain().getDomainHomeSourceType();
  }

  boolean isUseOnlineUpdate() {
    return getDomain().isUseOnlineUpdate();
  }

  public boolean isAdminChannelPortForwardingEnabled(DomainSpec domainSpec) {
    return DomainResource.isAdminChannelPortForwardingEnabled(domainSpec);
  }

  String getEffectiveLogHome() {
    return getDomain().getEffectiveLogHome();
  }

  LogHomeLayoutType getLogHomeLayout() {
    return getDomain().getLogHomeLayout();
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

  // ---------------------- model methods ------------------------------

  private String getWdtConfigMap() {
    return emptyToNull(getDomain().getWdtConfigMap());
  }

  private ResponseStep<V1Job> newCreateResponse() {
    return new CreateResponseStep(null);
  }

  private V1Job createJobModel() {
    return new V1Job()
          .metadata(createMetadata())
          .spec(createJobSpec());
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

  private long getActiveDeadlineSeconds() {
    return getIntrospectorJobActiveDeadlineSeconds()
          + (TuningParameters.getInstance().getActiveDeadlineIncrementSeconds() * getNumDeadlineIncreases());
  }

  private long getIntrospectorJobActiveDeadlineSeconds() {
    return Optional.ofNullable(getDomain().getIntrospectorJobActiveDeadlineSeconds())
        .orElse(TuningParameters.getInstance().getActiveJobInitialDeadlineSeconds());
  }

  @Nonnull
  private Long getNumDeadlineIncreases() {
    return Math.min(TuningParameters.getInstance().getActiveDeadlineMaxNumIncrements(), info.getNumDeadlineIncreases());
  }

  V1JobSpec createJobSpec() {
    LOGGER.fine(
          "Creating job "
                + getJobName()
                + " with activeDeadlineSeconds = "
                + getActiveDeadlineSeconds());

    return new V1JobSpec()
          .backoffLimit(0)
          .activeDeadlineSeconds(getActiveDeadlineSeconds())
          .template(createPodTemplateSpec());
  }

  private V1PodTemplateSpec createPodTemplateSpec() {
    V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec()
          .metadata(createPodTemplateMetadata())
          .spec(createPodSpec());
    addInitContainers(podTemplateSpec.getSpec());
    Optional.ofNullable(getAuxiliaryImages())
            .ifPresent(p -> podTemplateSpec.getSpec().addVolumesItem(createEmptyDirVolume()));

    return updateForDeepSubstitution(podTemplateSpec.getSpec(), podTemplateSpec);
  }

  private List<AuxiliaryImage> getAuxiliaryImages() {
    return getDomain().getAuxiliaryImages();
  }

  private V1ObjectMeta createPodTemplateMetadata() {
    V1ObjectMeta metadata = new V1ObjectMeta()
          .name(getJobName())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
          .putLabelsItem(LabelConstants.JOBNAME_LABEL, createJobName(getDomainUid()));
    // always set it to false
    metadata.putAnnotationsItem("sidecar.istio.io/inject", "false");
    return metadata;
  }

  protected void addInitContainers(V1PodSpec podSpec) {
    List<V1Container> initContainers = new ArrayList<>();
    Optional.ofNullable(getAuxiliaryImages()).ifPresent(auxImages -> addInitContainers(initContainers, auxImages));
    initContainers.addAll(getAdditionalInitContainers().stream()
            .filter(container -> container.getName().startsWith(COMPATIBILITY_MODE))
            .map(c -> c.env(createEnv(c)).resources(createResources()))
            .collect(Collectors.toList()));
    podSpec.initContainers(initContainers);
  }

  private void addInitContainers(List<V1Container> initContainers, List<AuxiliaryImage> auxiliaryImages) {
    IntStream.range(0, auxiliaryImages.size()).forEach(idx ->
            initContainers.add(createInitContainerForAuxiliaryImage(auxiliaryImages.get(idx), idx)));
  }

  @Override
  protected V1PodSpec createPodSpec() {
    V1PodSpec podSpec = super.createPodSpec()
            .activeDeadlineSeconds(getActiveDeadlineSeconds())
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

    if (getDefaultAntiAffinity().equals(podSpec.getAffinity())) {
      podSpec.affinity(null);
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
    return getDomainHomeSourceType() == DomainSourceType.FROM_MODEL;
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

  @Override
  protected V1Container createPrimaryContainer() {
    V1Container container = super.createPrimaryContainer()
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

    Optional.ofNullable(getAuxiliaryImages()).ifPresent(auxiliaryImages -> addVolumeMountIfMissing(container));

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
    try {
      return getLegalVolumeName(resourceName, type);
    } catch (Exception ex) {
      LOGGER.severe(MessageKeys.EXCEPTION, ex);
      return resourceName;
    }
  }

  private String getLegalVolumeName(String volumeName, String type) throws NoSuchAlgorithmException {
    return volumeName.length() > (MAX_ALLOWED_VOLUME_NAME_LENGTH - VOLUME_NAME_SUFFIX.length())
        ? getShortName(volumeName, type)
        : volumeName + VOLUME_NAME_SUFFIX;
  }

  private String getShortName(String resourceName, String type) throws NoSuchAlgorithmException {
    String volumeSuffix = VOLUME_NAME_SUFFIX + "-" + type + "-"
        + Optional.ofNullable(getMD5Hash.apply(resourceName)).orElse("");
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
            .map(DomainResource::getFluentdSpecification)
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

  private String getAsName() {
    return domainTopology.getAdminServerName();
  }

  private Integer getAsPort() {
    return domainTopology
        .getServerConfig(getAsName())
        .getLocalAdminProtocolChannelPort();
  }

  private boolean isLocalAdminProtocolChannelSecure() {
    return domainTopology
        .getServerConfig(getAsName())
        .isLocalAdminProtocolChannelSecure();
  }

  private String getAsServiceName() {
    return LegalNames.toServerServiceName(getDomainUid(), getAsName());
  }

  private String getIntrospectVersionLabel() {
    return Optional.ofNullable(getDomain().getIntrospectVersion()).orElse(null);
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
    if (getLogHomeLayout() == LogHomeLayoutType.FLAT) {
      addEnvVar(vars, ServerEnvVars.LOG_HOME_LAYOUT, getLogHomeLayout().toString());
    }
    addEnvVar(vars, ServerEnvVars.SERVER_OUT_IN_POD_LOG, getIncludeServerOutInPodLog());
    addEnvVar(vars, ServerEnvVars.ACCESS_LOG_IN_LOG_HOME, getHttpAccessLogInLogHome());
    addEnvVar(vars, IntrospectorJobEnvVars.NAMESPACE, getNamespace());
    addEnvVar(vars, IntrospectorJobEnvVars.INTROSPECT_HOME, getIntrospectHome());
    addEnvVar(vars, IntrospectorJobEnvVars.CREDENTIALS_SECRET_NAME, getWebLogicCredentialsSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.OPSS_KEY_SECRET_NAME, getOpssWalletPasswordSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.OPSS_WALLETFILE_SECRET_NAME, getOpssWalletFileSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.RUNTIME_ENCRYPTION_SECRET_NAME, getRuntimeEncryptionSecretName());
    addEnvVar(vars, IntrospectorJobEnvVars.WDT_DOMAIN_TYPE, getWdtDomainType().toString());
    addEnvVar(vars, IntrospectorJobEnvVars.DOMAIN_SOURCE_TYPE, getDomainHomeSourceType().toString());
    addEnvVar(vars, IntrospectorJobEnvVars.ADMIN_CHANNEL_PORT_FORWARDING_ENABLED,
            Boolean.toString(isAdminChannelPortForwardingEnabled(getDomain().getSpec())));
    Optional.ofNullable(getKubernetesPlatform())
            .ifPresent(v -> addEnvVar(vars, ServerEnvVars.KUBERNETES_PLATFORM, v));

    if (isUseOnlineUpdate()) {
      addOnlineUpdateEnvVars(vars);
    }

    String dataHome = getDataHome();
    if (dataHome != null && !dataHome.isEmpty()) {
      addEnvVar(vars, ServerEnvVars.DATA_HOME, dataHome);
    }

    // Populate env var list used by the MII introspector job's 'short circuit' MD5
    // check. To prevent a false trip of the circuit breaker, the list must be the
    // same regardless of whether domainTopology == null.
    StringBuilder sb = new StringBuilder(vars.size() * 32);
    for (V1EnvVar envVar : vars) {
      sb.append(envVar.getName()).append(',');
    }
    sb.deleteCharAt(sb.length() - 1);
    addEnvVar(vars, "OPERATOR_ENVVAR_NAMES", sb.toString());

    if (domainTopology != null) {
      addEnvVarsForExistingTopology(vars);
    }

    String modelHome = getModelHome();
    if (modelHome != null && !modelHome.isEmpty()) {
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_MODEL_HOME, modelHome);
    }

    String wdtInstallHome = getWdtInstallHome();
    if (wdtInstallHome != null && !wdtInstallHome.isEmpty()) {
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_INSTALL_HOME, wdtInstallHome);
    }

    Optional.ofNullable(getAuxiliaryImages()).ifPresent(ais -> addAuxImagePathEnv(ais, vars));
    return vars;
  }

  private void addAuxImagePathEnv(List<AuxiliaryImage> auxiliaryImages, List<V1EnvVar> vars) {
    if (!auxiliaryImages.isEmpty()) {
      addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_MOUNT_PATH, getDomain().getAuxiliaryImageVolumeMountPath());
    }
  }

  private void addEnvVarsForExistingTopology(List<V1EnvVar> vars) {
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

  private void addOnlineUpdateEnvVars(List<V1EnvVar> vars) {
    addEnvVar(vars, MII_USE_ONLINE_UPDATE, "true");
    addEnvVar(vars, MII_WDT_ACTIVATE_TIMEOUT, getDomain().getWDTActivateTimeoutMillis().toString());
    addEnvVar(vars, MII_WDT_CONNECT_TIMEOUT, getDomain().getWDTConnectTimeoutMillis().toString());
    addEnvVar(vars, MII_WDT_DEPLOY_TIMEOUT, getDomain().getWDTDeployTimeoutMillis().toString());
    addEnvVar(vars, MII_WDT_REDEPLOY_TIMEOUT, getDomain().getWDTReDeployTimeoutMillis().toString());
    addEnvVar(vars, MII_WDT_UNDEPLOY_TIMEOUT, getDomain().getWDTUnDeployTimeoutMillis().toString());
    addEnvVar(vars, MII_WDT_START_APPLICATION_TIMEOUT, getDomain().getWDTStartApplicationTimeoutMillis().toString());
    addEnvVar(vars, MII_WDT_STOP_APPLICATION_TIMEOUT, getDomain().getWDTStopApplicationTimeoutMillis().toString());
    addEnvVar(vars, MII_WDT_SET_SERVERGROUPS_TIMEOUT, getDomain().getWDTSetServerGroupsTimeoutMillis().toString());
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
        return onFailure(conflictStep, packet, callResponse);
      }
    }

    private NextAction updateDomainStatus(Packet packet, CallResponse<V1Job> callResponse) {
      return doNext(createKubernetesFailureSteps(callResponse), packet);
    }

    private void logJobCreated() {
      LOGGER.info(getJobCreatedMessageKey(), getJobName());
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

  private V1ConfigMapVolumeSource getWdtConfigMapVolumeSource(String name) {
    return new V1ConfigMapVolumeSource().name(name).defaultMode(ALL_READ_AND_EXECUTE);
  }

}
