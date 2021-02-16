// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Strings;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
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
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;

public abstract class JobStepContext extends BasePodStepContext {
  static final long DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS = 60L;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH =
        "/weblogic-operator/scripts/introspectDomain.sh";
  private V1Job jobModel;

  JobStepContext(Packet packet) {
    super(packet.getSpi(DomainPresenceInfo.class));
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

  protected V1Job getJobModel() {
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

  protected String getDataHome() {
    String dataHome = getDomain().getDataHome();
    return dataHome != null && !dataHome.isEmpty() ? dataHome + File.separator + getDomainUid() : null;
  }

  protected String getModelHome() {
    return getDomain().getModelHome();
  }

  protected String getWdtDomainType() {
    return getDomain().getWdtDomainType();
  }

  protected DomainSourceType getDomainHomeSourceType() {
    return getDomain().getDomainHomeSourceType();
  }

  public boolean isUseOnlineUpdate() {
    return getDomain().isUseOnlineUpdate();
  }

  public boolean isIstioEnabled() {
    return getDomain().isIstioEnabled();
  }

  public int getIstioReadinessPort() {
    return getDomain().getIstioReadinessPort();
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
    return Strings.emptyToNull(getDomain().getConfigOverrides());
  }

  private long getIntrospectorJobActiveDeadlineSeconds(TuningParameters.PodTuning podTuning) {
    return Optional.ofNullable(getDomain().getIntrospectorJobActiveDeadlineSeconds())
        .orElse(podTuning.introspectorJobActiveDeadlineSeconds);
  }

  // ---------------------- model methods ------------------------------

  String getWdtConfigMap() {
    return Strings.emptyToNull(getDomain().getWdtConfigMap());
  }

  private ResponseStep<V1Job> createResponse(Step next) {
    return new CreateResponseStep(next);
  }

  private V1Job createJobModel() {
    return new V1Job()
          .metadata(createMetadata())
          .spec(createJobSpec(TuningParameters.getInstance()));
  }

  V1ObjectMeta createMetadata() {
    return updateForOwnerReference(
        new V1ObjectMeta()
          .name(getJobName())
          .namespace(getNamespace())
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"));
  }

  private long getActiveDeadlineSeconds(TuningParameters.PodTuning podTuning) {
    return getIntrospectorJobActiveDeadlineSeconds(podTuning)
          + (DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS * info.getRetryCount());
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
                .name(secretName + "-volume")
                .secret(getOverrideSecretVolumeSource(secretName)));
  }

  private void addConfigOverrideVolume(V1PodSpec podSpec, String configOverrides) {
    podSpec.addVolumesItem(
          new V1Volume()
                .name(configOverrides + "-volume")
                .configMap(getOverridesVolumeSource(configOverrides)));
  }

  private boolean isSourceWdt() {
    return getDomainHomeSourceType() == DomainSourceType.FromModel;
  }

  private void addWdtConfigMapVolume(V1PodSpec podSpec, String configMapName) {
    podSpec.addVolumesItem(
        new V1Volume()
            .name(configMapName + "-volume")
            .configMap(getWdtConfigMapVolumeSource(configMapName)));
  }

  private void addWdtSecretVolume(V1PodSpec podSpec) {
    podSpec.addVolumesItem(
        new V1Volume()
            .name(RUNTIME_ENCRYPTION_SECRET_VOLUME)
            .secret(getRuntimeEncryptionSecretVolume()));
  }

  protected V1Container createContainer(TuningParameters tuningParameters) {
    V1Container container = super.createContainer(tuningParameters)
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
            readOnlyVolumeMount(getConfigOverrides() + "-volume", OVERRIDES_CM_MOUNT_PATH));
    }

    List<String> configOverrideSecrets = getConfigOverrideSecrets();
    for (String secretName : configOverrideSecrets) {
      container.addVolumeMountsItem(
            readOnlyVolumeMount(
                  secretName + "-volume", OVERRIDE_SECRETS_MOUNT_PATH + '/' + secretName));
    }

    if (isSourceWdt()) {
      if (getWdtConfigMap() != null) {
        container.addVolumeMountsItem(
            readOnlyVolumeMount(getWdtConfigMap() + "-volume", WDTCONFIGMAP_MOUNT_PATH));
      }
      container.addVolumeMountsItem(
          readOnlyVolumeMount(RUNTIME_ENCRYPTION_SECRET_VOLUME,
              RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH));

    }

    return container;
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
    return new ArrayList<>();
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

  protected V1ConfigMapVolumeSource getIntrospectMD5VolumeSource() {
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
        return super.onFailure(packet, callResponse);
      }
    }

    private NextAction updateDomainStatus(Packet packet, CallResponse<V1Job> callResponse) {
      return doNext(DomainStatusUpdater.createFailureRelatedSteps(callResponse, null), packet);
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

  protected V1ConfigMapVolumeSource getWdtConfigMapVolumeSource(String name) {
    return new V1ConfigMapVolumeSource().name(name).defaultMode(ALL_READ_AND_EXECUTE);
  }

}
