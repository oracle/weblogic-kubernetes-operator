// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.common.AuxiliaryImageConstants;
import oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.common.utils.CommonUtils;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.processing.EffectiveIntrospectorJobPodSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.CreateIfNotExists;
import oracle.kubernetes.weblogic.domain.model.DeploymentImage;
import oracle.kubernetes.weblogic.domain.model.DomainCreationImage;
import oracle.kubernetes.weblogic.domain.model.DomainOnPV;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.InitializeDomainOnPV;
import oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_TARGET_PATH;
import static oracle.kubernetes.common.CommonConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.common.CommonConstants.SCRIPTS_MOUNTS_PATH;
import static oracle.kubernetes.common.CommonConstants.SCRIPTS_VOLUME;
import static oracle.kubernetes.common.CommonConstants.WLS_SHARED;
import static oracle.kubernetes.common.utils.CommonUtils.MAX_ALLOWED_VOLUME_NAME_LENGTH;
import static oracle.kubernetes.common.utils.CommonUtils.VOLUME_NAME_SUFFIX;
import static oracle.kubernetes.operator.DomainStatusUpdater.createKubernetesFailureSteps;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.NUM_CONFIG_MAPS;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.helpers.AffinityHelper.getDefaultAntiAffinity;
import static oracle.kubernetes.utils.OperatorUtils.emptyToNull;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME;
import static oracle.kubernetes.weblogic.domain.model.DomainCreationImage.DOMAIN_CREATION_IMAGE_MOUNT_PATH;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_RUNNING_SERVERS_STATES;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_USE_ONLINE_UPDATE;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_ACTIVATE_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_CONNECT_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_DEPLOY_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_REDEPLOY_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_SET_SERVERGROUPS_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_START_APPLICATION_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_STOP_APPLICATION_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_UNDEPLOY_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.DOMAIN_HOME;

public class JobStepContext extends BasePodStepContext {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH =
        "/weblogic-operator/scripts/introspectDomain.sh";
  private static final String CONFIGMAP_TYPE = "cm";
  private static final String SECRET_TYPE = "st";
  // domainTopology is null if this is 1st time we're running job for this domain
  private final WlsDomainConfig domainTopology;
  private static final CommonUtils.CheckedFunction<String, String> getMD5Hash = CommonUtils::getMD5Hash;
  private V1Job jobModel;
  private Step conflict;
  private Packet packet;

  JobStepContext(Packet packet) {
    super((DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO));
    this.packet = packet;
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

  protected V1ResourceRequirements getResources() {
    return Optional.ofNullable(getDomain().getIntrospectorSpec()).map(EffectiveIntrospectorJobPodSpec::getResources)
        .orElse(getAdminServerResources());
  }

  private V1ResourceRequirements getAdminServerResources() {
    return Optional.ofNullable(getDomain().getAdminServerSpec()).map(EffectiveServerSpec::getResources).orElse(null);
  }

  protected List<V1EnvFromSource> getEnvFrom() {
    return Optional.ofNullable(getDomain().getIntrospectorSpec()).map(EffectiveIntrospectorJobPodSpec::getEnvFrom)
        .orElse(getAdminServerEnvFrom());
  }

  private List<V1EnvFromSource> getAdminServerEnvFrom() {
    return Optional.ofNullable(getDomain().getAdminServerSpec()).map(EffectiveServerSpec::getEnvFrom).orElse(null);
  }

  protected List<V1EnvVar> getServerPodEnvironmentVariables() {
    List<V1EnvVar> envVars = getIntrospectorEnvVariables();
    getAdminServerEnvVariables().forEach(adminEnvVar -> addIfMissing(envVars, adminEnvVar.getName(),
        adminEnvVar.getValue(), adminEnvVar.getValueFrom()));
    return envVars;
  }

  private List<V1EnvVar> getIntrospectorEnvVariables() {
    return Optional.ofNullable(getDomain().getIntrospectorSpec())
        .map(EffectiveIntrospectorJobPodSpec::getEnv).orElse(new ArrayList<>());
  }

  private List<V1Container> getIntrospectorInitContainers() {
    return Optional.ofNullable(getDomain().getIntrospectorSpec())
            .map(EffectiveIntrospectorJobPodSpec::getInitContainers).orElse(new ArrayList<>());
  }

  private List<V1EnvVar> getAdminServerEnvVariables() {
    return Optional.ofNullable(getDomain().getAdminServerSpec()).map(EffectiveServerSpec::getEnvironmentVariables)
        .orElse(new ArrayList<>());
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
    if (!volumes.contains(volume) && isAllowedInIntrospector(volume.getName())) {
      volumes.add(volume);
    }
  }

  private boolean isAllowedInIntrospector(String name) {
    return name.startsWith(COMPATIBILITY_MODE) || name.startsWith(WLS_SHARED);
  }

  List<V1VolumeMount> getAdditionalVolumeMounts() {
    List<V1VolumeMount> volumeMounts = getDomain().getSpec().getAdditionalVolumeMounts();
    getServerSpec().getAdditionalVolumeMounts().forEach(mount -> addVolumeMountIfMissing(mount, volumeMounts));
    return volumeMounts;
  }

  private void addVolumeMountIfMissing(V1VolumeMount mount, List<V1VolumeMount> volumeMounts) {
    if (!volumeMounts.contains(mount) && isAllowedInIntrospector(mount.getName())) {
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
    conflict = RequestBuilder.JOB.create(getJobModel(), newCreateResponse());
    return conflict;
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

  String getWdtDomainTypeValue() {
    if (getInitializeDomainOnPV().isEmpty()) {
      return getDomain().getWdtDomainType().toString();
    } else {
      return getInitializeDomainOnPV().map(InitializeDomainOnPV::getDomain).map(DomainOnPV::getDomainType)
          .orElse(null);
    }
  }

  DomainSourceType getDomainHomeSourceType() {
    return getDomain().getDomainHomeSourceType();
  }

  boolean isInitializeDomainOnPV() {
    return getDomain().isInitializeDomainOnPV();
  }

  boolean isUseOnlineUpdate() {
    return getDomain().isUseOnlineUpdate();
  }

  String getDomainCreationConfigMap() {
    return getDomain().getDomainCreationConfigMap();
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

  private ResponseStep<V1Job> newCreateResponse() {
    return new CreateResponseStep(null);
  }

  private String getWdtConfigMap() {
    return emptyToNull(getDomain().getWdtConfigMap());
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
        .orElse(getDefaultIntrospectorJobInitialDeadlineSeconds());
  }

  private long getDefaultIntrospectorJobInitialDeadlineSeconds() {
    return TuningParameters.getInstance().getActiveJobInitialDeadlineSeconds(isInitializeDomainOnPV(),
        getDomain().getInitializeDomainOnPVDomainType());
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
    if (auxiliaryOrDomainCreationImagesConfigured()) {
      podTemplateSpec.getSpec().addVolumesItem(createEmptyDirVolume());
    }

    return updateForDeepSubstitution(podTemplateSpec.getSpec(), podTemplateSpec);
  }

  private boolean auxiliaryOrDomainCreationImagesConfigured() {
    return getAuxiliaryImages() != null || getDomainCreationImages() != null;
  }

  private List<AuxiliaryImage> getAuxiliaryImages() {
    return getDomain().getAuxiliaryImages();
  }

  private List<DomainCreationImage> getDomainCreationImages() {
    return getDomain().getDomainCreationImages();
  }

  private V1ObjectMeta createPodTemplateMetadata() {
    return new V1ObjectMeta()
          .name(getJobName())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
          .putLabelsItem(LabelConstants.JOBNAME_LABEL, createJobName(getDomainUid()));
  }

  protected void addInitContainers(V1PodSpec podSpec) {
    List<V1Container> initContainers = new ArrayList<>();
    getInitializeDomainOnPV().ifPresent(initializeDomainOnPV -> addInitDomainOnPVInitContainer(initContainers));
    Optional.ofNullable(getAuxiliaryImages()).ifPresent(auxImages -> addInitContainers(initContainers, auxImages));
    Optional.ofNullable(getDomainCreationImages()).ifPresent(dcrImages -> addInitContainers(initContainers, dcrImages));
    initContainers.addAll(getAdditionalInitContainers().stream()
            .filter(container -> isAllowedInIntrospector(container.getName()))
            .map(c -> c.env(createEnv(c)).envFrom(c.getEnvFrom()).resources(createResources()))
            .toList());
    podSpec.initContainers(initContainers);
  }

  private void addInitContainers(List<V1Container> initContainers, List<? extends DeploymentImage> auxiliaryImages) {
    IntStream.range(0, auxiliaryImages.size()).forEach(idx ->
        initContainers.add(createInitContainerForAuxiliaryImage(auxiliaryImages.get(idx), idx,
                isInitializeDomainOnPV())));
    List<V1Container> introspectorInitContainers = getIntrospectorInitContainers();
    for (V1Container initContainer : introspectorInitContainers) {
      initContainer.securityContext(getInitContainerSecurityContext());
      initContainers.add(initContainer);
    }
  }

  private Optional<InitializeDomainOnPV> getInitializeDomainOnPV() {
    return Optional.of(getDomain().getSpec())
        .map(DomainSpec::getConfiguration)
        .map(Configuration::getInitializeDomainOnPV);
  }

  private void addInitDomainOnPVInitContainer(List<V1Container> initContainers) {
    initContainers.add(new V1Container()
        .name(INIT_DOMAIN_ON_PV_CONTAINER)
        .image(getDomain().getSpec().getImage())
        .volumeMounts(getDomain().getAdminServerSpec().getAdditionalVolumeMounts())
        .addVolumeMountsItem(new V1VolumeMount().name(SCRIPTS_VOLUME).mountPath(SCRIPTS_MOUNTS_PATH))
        .addVolumeMountsItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
            .mountPath(AUXILIARY_IMAGE_TARGET_PATH))
        .env(getDomain().getAdminServerSpec().getEnvironmentVariables())
        .addEnvItem(new V1EnvVar().name(DOMAIN_HOME).value(getDomainHome()))
        .addEnvItem(new V1EnvVar().name(ServerEnvVars.LOG_HOME).value(getEffectiveLogHome()))
        .addEnvItem(new V1EnvVar().name(ServerEnvVars.DOMAIN_HOME_ON_PV_DEFAULT_UGID)
                .value(getDomainHomeOnPVHomeOwnership()))
        .addEnvItem(new V1EnvVar().name(AuxiliaryImageEnvVars.AUXILIARY_IMAGE_TARGET_PATH)
            .value(AuxiliaryImageConstants.AUXILIARY_IMAGE_TARGET_PATH))
        .securityContext(getInitContainerSecurityContext())
        .command(List.of(INIT_DOMAIN_ON_PV_SCRIPT))
    );
  }

  @Override
  V1SecurityContext getInitContainerSecurityContext() {
    if (isInitDomainOnPVRunAsRoot()) {
      return new V1SecurityContext().runAsGroup(0L).runAsUser(0L);
    }
    if (getServerSpec().getContainerSecurityContext() != null) {
      return getServerSpec().getContainerSecurityContext();
    }
    if (getPodSecurityContext().equals(PodSecurityHelper.getDefaultPodSecurityContext())) {
      return PodSecurityHelper.getDefaultContainerSecurityContext();
    }
    return creatSecurityContextFromPodSecurityContext(getPodSecurityContext());
  }

  @NotNull
  private Boolean isInitDomainOnPVRunAsRoot() {
    return Optional.ofNullable(getDomain().getInitializeDomainOnPV())
        .map(InitializeDomainOnPV::getRunDomainInitContainerAsRoot).orElse(false);
  }

  private V1SecurityContext creatSecurityContextFromPodSecurityContext(
      V1PodSecurityContext podSecurityContext) {
    return new V1SecurityContext()
        .runAsUser(podSecurityContext.getRunAsUser())
        .runAsGroup(podSecurityContext.getRunAsGroup())
        .runAsNonRoot(podSecurityContext.getRunAsNonRoot())
        .seccompProfile(podSecurityContext.getSeccompProfile())
        .seLinuxOptions(podSecurityContext.getSeLinuxOptions())
        .windowsOptions(podSecurityContext.getWindowsOptions());
  }

  private String getDomainHomeOnPVHomeOwnership() {
    long uid = Optional.ofNullable(getDomain().getAdminServerSpec())
                    .map(EffectiveServerSpec::getPodSecurityContext)
                            .map(V1PodSecurityContext::getRunAsUser)
                                    .orElse(-1L);
    long gid = Optional.ofNullable(getDomain().getAdminServerSpec())
            .map(EffectiveServerSpec::getPodSecurityContext)
            .map(V1PodSecurityContext::getRunAsGroup)
            .orElse(-1L);

    if ("OpenShift".equals(getKubernetesPlatform())) {
      uid = (uid == -1L) ? 1000L : uid;
      gid = (gid == -1L) ? 0L : gid;
    } else {
      uid = (uid == -1L) ? 1000L : uid;
      gid = (gid == -1L) ? 1000L : gid;
    }

    return uid + ":" + gid;
  }

  private Integer getSpecifiedNumConfigMaps() {
    return Optional.ofNullable(packet.<String>getValue(NUM_CONFIG_MAPS)).map(this::parseOrNull).orElse(null);
  }

  private Integer parseOrNull(String count) {
    try {
      return Integer.parseInt(count);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  protected V1PodSpec createPodSpec() {

    Integer numOfConfigMaps = getSpecifiedNumConfigMaps();

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
                    .configMap(getIntrospectMD5VolumeSource(0)));

    if (numOfConfigMaps != null && numOfConfigMaps > 1) {
      for (int i = 1; i < numOfConfigMaps; i++) {
        podSpec.addVolumesItem(
                new V1Volume()
                        .name("mii" + IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX + '-' + i)
                        .configMap(getIntrospectMD5VolumeSource(i)));
      }
    }

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

    for (V1Volume additionalVolume : info.getDomain().getAdminServerSpec().getAdditionalVolumes()) {
      if (podSpec.getVolumes() != null) {
        List<V1Volume> volumes = podSpec.getVolumes();
        if (!volumes.contains(additionalVolume) && !additionalVolume.getName().startsWith(COMPATIBILITY_MODE)) {
          volumes.add(additionalVolume);
        }
      }
    }

    getConfigOverrideSecrets().forEach(secretName -> addConfigOverrideSecretVolume(podSpec, secretName));
    Optional.ofNullable(getConfigOverrides()).ifPresent(overrides -> addConfigOverrideVolume(podSpec, overrides));

    if (isDomainSourceFromModel()) {
      Optional.ofNullable(getWdtConfigMap()).ifPresent(mapName -> addWdtConfigMapVolume(podSpec, mapName));
      addWdtSecretVolume(podSpec);
    }

    if (isInitializeDomainOnPV()) {
      Optional.ofNullable(getDomainCreationConfigMap()).ifPresent(mapName -> addWdtConfigMapVolume(podSpec, mapName));
    }

    if (getDefaultAntiAffinity().equals(podSpec.getAffinity())) {
      podSpec.affinity(null);
    }

    if (isInitializeDomainOnPV()) {
      V1PodSecurityContext podSecurityContext = getPodSecurityContext();
      if (Boolean.TRUE.equals(getDomain().getInitializeDomainOnPV().getSetDefaultSecurityContextFsGroup())) {
        if (podSecurityContext.getFsGroup() == null && podSecurityContext.getRunAsGroup() != null) {
          podSpec.securityContext(podSecurityContext.fsGroup(podSecurityContext.getRunAsGroup()));
        } else if (podSecurityContext.getFsGroup() == null) {
          Optional.ofNullable(TuningParameters.getInstance()).ifPresent(instance -> {
            if (!"OpenShift".equalsIgnoreCase(instance.getKubernetesPlatform()) && !isInitDomainOnPVRunAsRoot()) {
              podSpec.securityContext(podSecurityContext.fsGroup(0L));
            }
          });
        }
        if (podSpec.getSecurityContext().getFsGroupChangePolicy() == null) {
          Optional.ofNullable(TuningParameters.getInstance()).ifPresent(instance -> {
            if (!"OpenShift".equalsIgnoreCase(instance.getKubernetesPlatform()) && !isInitDomainOnPVRunAsRoot()) {
              podSpec.getSecurityContext().fsGroupChangePolicy("OnRootMismatch");
            }
          });
        }
      }
    }
    return podSpec;
  }

  @Override
  V1PodSecurityContext getPodSecurityContext() {
    return Optional.ofNullable(getDomain().getIntrospectorSpec())
        .map(EffectiveIntrospectorJobPodSpec::getPodSecurityContext)
        .orElse(getDomain().getAdminServerSpec().getPodSecurityContext());
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

  private boolean isDomainSourceFromModel() {
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
    Integer numOfConfigMaps = getSpecifiedNumConfigMaps();
    V1Container container = super.createPrimaryContainer()
        .addVolumeMountsItem(readOnlyVolumeMount(SECRETS_VOLUME, SECRETS_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH))
        .addVolumeMountsItem(
          volumeMount(
              "mii" + IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX,
              "/weblogic-operator/introspectormii")
              .readOnly(false));

    if (numOfConfigMaps != null && numOfConfigMaps > 1) {
      for (int i = 1; i < numOfConfigMaps; i++) {
        container.addVolumeMountsItem(
                volumeMount(
                  "mii" + IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX + '-' + i,
                    "/weblogic-operator/introspectormii" + '-' + i)
                                .readOnly(false));
      }
    }

    if (getOpssWalletPasswordSecretVolume() != null) {
      container.addVolumeMountsItem(readOnlyVolumeMount(OPSS_KEYPASSPHRASE_VOLUME, OPSS_KEY_MOUNT_PATH));
    }
    if (getOpssWalletFileSecretVolume() != null) {
      container.addVolumeMountsItem(readOnlyVolumeMount(OPSS_WALLETFILE_VOLUME, OPSS_WALLETFILE_MOUNT_PATH));
    }
    
    for (V1VolumeMount additionalVolumeMount : getAdditionalVolumeMounts()) {
      container.addVolumeMountsItem(additionalVolumeMount);
    }

    if (getConfigOverrides() != null && !getConfigOverrides().isEmpty()) {
      container.addVolumeMountsItem(
            readOnlyVolumeMount(getVolumeName(getConfigOverrides(), CONFIGMAP_TYPE), OVERRIDES_CM_MOUNT_PATH));
    }

    Optional.ofNullable(getAuxiliaryImages()).ifPresent(ai -> addVolumeMountIfMissing(container));
    Optional.ofNullable(getDomainCreationImages()).ifPresent(dci -> addVolumeMountIfMissing(container,
        DOMAIN_CREATION_IMAGE_MOUNT_PATH));

    List<String> configOverrideSecrets = getConfigOverrideSecrets();
    for (String secretName : configOverrideSecrets) {
      container.addVolumeMountsItem(
            readOnlyVolumeMount(
                  getVolumeName(secretName, SECRET_TYPE), OVERRIDE_SECRETS_MOUNT_PATH + '/' + secretName));
    }

    if (isDomainSourceFromModel()) {
      if (getWdtConfigMap() != null) {
        container.addVolumeMountsItem(
            readOnlyVolumeMount(getVolumeName(getWdtConfigMap(), CONFIGMAP_TYPE), WDTCONFIGMAP_MOUNT_PATH));
      }
      container.addVolumeMountsItem(
          readOnlyVolumeMount(RUNTIME_ENCRYPTION_SECRET_VOLUME,
              RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH));
    }

    if (isInitializeDomainOnPV() && getDomainCreationConfigMap() != null) {
      container.addVolumeMountsItem(
          readOnlyVolumeMount(getVolumeName(getDomainCreationConfigMap(), CONFIGMAP_TYPE), WDTCONFIGMAP_MOUNT_PATH));
    }

    return container;
  }

  private String getVolumeName(String resourceName, String type) {
    try {
      return CommonUtils.toDns1123LegalName(getLegalVolumeName(resourceName, type));
    } catch (Exception ex) {
      LOGGER.severe(MessageKeys.EXCEPTION, ex);
      return CommonUtils.toDns1123LegalName(resourceName);
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

    Optional.ofNullable(getDomain().getFluentbitSpecification())
            .ifPresent(fluentbit -> {
              if (Boolean.TRUE.equals(fluentbit.getWatchIntrospectorLogs())) {
                FluentbitHelper.addFluentbitContainer(fluentbit,
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
            .configMap(new V1ConfigMapVolumeSource()
                .name(getDomainUid() + FLUENTD_CONFIGMAP_NAME_SUFFIX)
                .defaultMode(420))));
    return volumes;
  }

  protected List<V1Volume> getFluentbitVolumes() {
    List<V1Volume> volumes = new ArrayList<>();
    Optional.ofNullable(getDomain())
            .map(DomainResource::getFluentbitSpecification)
            .ifPresent(c -> volumes.add(new V1Volume().name(FLUENTBIT_CONFIGMAP_VOLUME)
                    .configMap(new V1ConfigMapVolumeSource()
                            .name(getDomainUid() + FLUENTBIT_CONFIGMAP_NAME_SUFFIX)
                            .defaultMode(420))));
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

  private V1ConfigMapVolumeSource getIntrospectMD5VolumeSource(int index) {
    String suffix = "";
    if (index > 0) {
      suffix = "-" + index;
    }
    V1ConfigMapVolumeSource result =
        new V1ConfigMapVolumeSource()
            .name(getDomainUid() + IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX + suffix)
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
    return getDomain().getIntrospectVersion();
  }

  @Override
  List<V1EnvVar> getConfiguredEnvVars() {
    // Pod for introspector job would use same environment variables as for admin server
    List<V1EnvVar> vars =
          PodHelper.createCopy(getServerPodEnvironmentVariables());

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
    addEnvVar(vars, IntrospectorJobEnvVars.WDT_DOMAIN_TYPE, getWdtDomainTypeValue());
    addEnvVar(vars, IntrospectorJobEnvVars.DOMAIN_SOURCE_TYPE, getDomainHomeSourceType().toString());
    addEnvVar(vars, IntrospectorJobEnvVars.ADMIN_CHANNEL_PORT_FORWARDING_ENABLED,
            Boolean.toString(isAdminChannelPortForwardingEnabled(getDomain().getSpec())));
    Optional.ofNullable(getKubernetesPlatform())
            .ifPresent(v -> addEnvVar(vars, ServerEnvVars.KUBERNETES_PLATFORM, v));
    Optional.ofNullable(getDomain().isReplaceVariablesInJavaOptions())
        .ifPresent(v -> addEnvVar(vars, "REPLACE_VARIABLES_IN_JAVA_OPTIONS", Boolean.toString(v)));
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

    getInitializeDomainOnPV().ifPresent(initializeDomainOnPV ->
        addEnvVar(vars, IntrospectorJobEnvVars.INIT_DOMAIN_ON_PV, getInitializeDomainOnPV()
            .map(InitializeDomainOnPV::getDomain).map(DomainOnPV::getCreateIfNotExists)
            .map(CreateIfNotExists::toString).orElse(null)));

    Optional.ofNullable(getAuxiliaryImages()).ifPresent(ais -> addAuxImagePathEnv(ais, vars));
    Optional.ofNullable(getDomainCreationImages()).ifPresent(dcrImages -> addAuxImagePathEnv(dcrImages, vars,
        DOMAIN_CREATION_IMAGE_MOUNT_PATH));

    if (isDomainSourceFromModel()) {
      StringBuilder runningServer = new StringBuilder();
      Optional.ofNullable(this.info.getDomain())
              .map(DomainResource::getStatus)
              .map(DomainStatus::getServers)
              .ifPresent(servers -> servers.forEach(item -> addServerStateIfNotShutDown(runningServer, item)));

      if (!runningServer.isEmpty()) {
        addEnvVar(vars, MII_RUNNING_SERVERS_STATES, runningServer.toString());
      }
    }

    return vars;
  }

  private void addServerStateIfNotShutDown(StringBuilder runningServer, ServerStatus item) {
    if (!item.getState().equals(SHUTDOWN_STATE)) {
      runningServer.append(item.getServerName()).append("=").append(item.getState()).append(" ");
    }
  }

  private void addAuxImagePathEnv(List<? extends DeploymentImage> auxiliaryImages, List<V1EnvVar> vars) {
    addAuxImagePathEnv(auxiliaryImages, vars, getDomain().getAuxiliaryImageVolumeMountPath());
  }

  private void addAuxImagePathEnv(List<? extends DeploymentImage> auxiliaryImages, List<V1EnvVar> vars,
                                  String mountPath) {
    if (!auxiliaryImages.isEmpty()) {
      addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_MOUNT_PATH, mountPath);
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
    public Result onFailure(Packet packet, KubernetesApiResponse<V1Job> callResponse) {
      if (isUnrecoverable(callResponse)) {
        return updateDomainStatus(packet, callResponse);
      } else {
        return onFailure(conflict, packet, callResponse);
      }
    }

    private Result updateDomainStatus(Packet packet, KubernetesApiResponse<V1Job> callResponse) {
      return doNext(createKubernetesFailureSteps(callResponse, createFailureMessage(callResponse)), packet);
    }

    private void logJobCreated() {
      LOGGER.info(getJobCreatedMessageKey(), getJobName());
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<V1Job> callResponse) {
      logJobCreated();
      V1Job job = callResponse.getObject();
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
