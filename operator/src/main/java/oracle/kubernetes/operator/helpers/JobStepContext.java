package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1SecretVolumeSource;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;

public abstract class JobStepContext extends StepContextBase {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH =
      "/weblogic-operator/scripts/introspectDomain.sh";

  private final DomainPresenceInfo info;
  private V1Job jobModel;
  static final long DEFAULT_ACTIVE_DEADLINE_SECONDS = 120L;
  static final long DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS = 60L;

  JobStepContext(Packet packet) {
    info = packet.getSPI(DomainPresenceInfo.class);
  }

  void init() {
    jobModel = createJobModel();
    createSubstitutionMap();
  }

  private void createSubstitutionMap() {
    substitutionVariables.put("DOMAIN_HOME", getDomainHome());
  }

  private V1Job getJobModel() {
    return jobModel;
  }

  // ------------------------ data methods ----------------------------

  String getNamespace() {
    return info.getNamespace();
  }

  String getDomainUID() {
    return getDomain().getDomainUID();
  }

  Domain getDomain() {
    return info.getDomain();
  }

  abstract String getJobName();

  String getWebLogicCredentialsSecretName() {
    return getDomain().getWebLogicCredentialsSecret().getName();
  }

  abstract List<V1Volume> getAdditionalVolumes();

  abstract List<V1VolumeMount> getAdditionalVolumeMounts();

  // ----------------------- step methods ------------------------------

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
    return new CallBuilder().createJobAsync(getNamespace(), getJobModel(), createResponse(next));
  }

  protected void logJobCreated() {
    LOGGER.info(getJobCreatedMessageKey(), getJobName());
  }

  abstract String getJobCreatedMessageKey();

  protected String getNodeManagerHome() {
    return NODEMGR_HOME;
  }

  protected String getLogHome() {
    return getDomain().getLogHome();
  }

  protected boolean isDomainHomeInImage() {
    return getDomain().isDomainHomeInImage();
  }

  String getEffectiveLogHome() {
    if (!getDomain().getLogHomeEnabled()) {
      return null;
    }
    String logHome = getLogHome();
    if (logHome == null || "".equals(logHome.trim())) {
      // logHome not specified, use default value
      return DEFAULT_LOG_HOME + File.separator + getDomainUID();
    }
    return logHome;
  }

  String getIncludeServerOutInPodLog() {
    return Boolean.toString(getDomain().isIncludeServerOutInPodLog());
  }

  protected String getIntrospectHome() {
    return getDomainHome();
  }

  List<String> getConfigOverrideSecrets() {
    return getDomain().getConfigOverrideSecrets();
  }

  String getConfigOverrides() {
    return getDomain().getConfigOverrides();
  }

  private ResponseStep<V1Job> createResponse(Step next) {
    return new CreateResponseStep(next);
  }

  private class CreateResponseStep extends ResponseStep<V1Job> {
    CreateResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Job> callResponse) {
      return super.onFailure(packet, callResponse);
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

  // ---------------------- model methods ------------------------------

  private V1Job createJobModel() {
    return new V1Job()
        .metadata(createMetadata())
        .spec(createJobSpec(TuningParameters.getInstance()));
  }

  V1ObjectMeta createMetadata() {
    V1ObjectMeta metadata =
        new V1ObjectMeta()
            .name(getJobName())
            .namespace(getNamespace())
            .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
            .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUID())
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    return metadata;
  }

  private long getActiveDeadlineSeconds() {
    return DEFAULT_ACTIVE_DEADLINE_SECONDS
        + (DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS * info.getRetryCount());
  }

  protected V1JobSpec createJobSpec(TuningParameters tuningParameters) {
    LOGGER.fine(
        "Creating job "
            + getJobName()
            + " with activeDeadlineSeconds = "
            + getActiveDeadlineSeconds());
    V1JobSpec jobSpec =
        new V1JobSpec()
            .backoffLimit(0)
            .activeDeadlineSeconds(getActiveDeadlineSeconds())
            .template(createPodTemplateSpec(tuningParameters));

    return jobSpec;
  }

  private V1PodTemplateSpec createPodTemplateSpec(TuningParameters tuningParameters) {
    V1ObjectMeta metadata = new V1ObjectMeta().name(getJobName());
    V1PodTemplateSpec podTemplateSpec =
        new V1PodTemplateSpec().metadata(metadata).spec(createPodSpec(tuningParameters));
    return podTemplateSpec;
  }

  private V1PodSpec createPodSpec(TuningParameters tuningParameters) {
    V1PodSpec podSpec =
        new V1PodSpec()
            .activeDeadlineSeconds(getActiveDeadlineSeconds())
            .restartPolicy("Never")
            .addContainersItem(createContainer(tuningParameters))
            .addVolumesItem(new V1Volume().name(SECRETS_VOLUME).secret(getSecretsVolume()))
            .addVolumesItem(
                new V1Volume().name(SCRIPTS_VOLUME).configMap(getConfigMapVolumeSource()));

    podSpec.setImagePullSecrets(info.getDomain().getSpec().getImagePullSecrets());

    for (V1Volume additionalVolume : getAdditionalVolumes()) {
      podSpec.addVolumesItem(additionalVolume);
    }

    List<String> configOverrideSecrets = getConfigOverrideSecrets();
    for (String secretName : configOverrideSecrets) {
      podSpec.addVolumesItem(
          new V1Volume()
              .name(secretName + "-volume")
              .secret(getOverrideSecretVolumeSource(secretName)));
    }
    if (getConfigOverrides() != null && getConfigOverrides().length() > 0) {
      podSpec.addVolumesItem(
          new V1Volume()
              .name(getConfigOverrides() + "-volume")
              .configMap(getOverridesVolumeSource(getConfigOverrides())));
    }

    return podSpec;
  }

  private V1Container createContainer(TuningParameters tuningParameters) {
    V1Container container =
        new V1Container()
            .name(getJobName())
            .image(getImageName())
            .imagePullPolicy(getImagePullPolicy())
            .command(getContainerCommand())
            .env(getEnvironmentVariables(tuningParameters))
            .addVolumeMountsItem(readOnlyVolumeMount(SECRETS_VOLUME, SECRETS_MOUNT_PATH))
            .addVolumeMountsItem(readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH));

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
    return container;
  }

  String getImageName() {
    String imageName = getDomain().getSpec().getImage();
    if (imageName == null) {
      imageName = KubernetesConstants.DEFAULT_IMAGE;
    }
    return imageName;
  }

  String getImagePullPolicy() {
    String imagePullPolicy = getDomain().getSpec().getImagePullPolicy();
    if (imagePullPolicy == null) {
      imagePullPolicy = KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
    }
    return imagePullPolicy;
  }

  protected List<String> getContainerCommand() {
    return Arrays.asList(WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH);
  }

  protected String getDomainHome() {
    return getDomain().getDomainHome();
  }

  private static V1VolumeMount readOnlyVolumeMount(String volumeName, String mountPath) {
    return volumeMount(volumeName, mountPath).readOnly(true);
  }

  private static V1VolumeMount volumeMount(String volumeName, String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }

  protected V1SecretVolumeSource getSecretsVolume() {
    return new V1SecretVolumeSource()
        .secretName(getWebLogicCredentialsSecretName())
        .defaultMode(420);
  }

  protected V1ConfigMapVolumeSource getConfigMapVolumeSource() {
    return new V1ConfigMapVolumeSource()
        .name(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .defaultMode(ALL_READ_AND_EXECUTE);
  }

  protected V1ConfigMapVolumeSource getConfigMapVolumeSource(String name, int mode) {
    return new V1ConfigMapVolumeSource().name(name).defaultMode(mode);
  }

  protected V1PersistentVolumeClaimVolumeSource getPersistenVolumeClaimVolumeSource(
      String claimName) {
    return new V1PersistentVolumeClaimVolumeSource().claimName(claimName);
  }

  protected V1SecretVolumeSource getOverrideSecretVolumeSource(String name) {
    return new V1SecretVolumeSource().secretName(name).defaultMode(420);
  }

  protected V1ConfigMapVolumeSource getOverridesVolumeSource(String name) {
    return new V1ConfigMapVolumeSource().name(name).defaultMode(ALL_READ_AND_EXECUTE);
  }
}
