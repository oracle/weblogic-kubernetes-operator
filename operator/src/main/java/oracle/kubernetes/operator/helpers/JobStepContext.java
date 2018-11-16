package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.*;
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

public abstract class JobStepContext implements StepContextConstants {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH =
      "/weblogic-operator/scripts/introspectDomain.sh";

  private final DomainPresenceInfo info;
  private V1Job jobModel;

  JobStepContext(Packet packet) {
    info = packet.getSPI(DomainPresenceInfo.class);
  }

  void init() {
    jobModel = createJobModel();
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

  String getDomainName() {
    return getDomain().getDomainName();
  }

  private String getDomainResourceName() {
    return info.getDomain().getMetadata().getName();
  }

  abstract String getJobName();

  private String getAdminSecretName() {
    return getDomain().getAdminSecret().getName();
  }

  private List<V1PersistentVolumeClaim> getClaims() {
    return info.getClaims().getItems();
  }

  private String getClaimName() {
    return getClaims().iterator().next().getMetadata().getName();
  }

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
    String logHome = getLogHome();
    if (logHome == null || "".equals(logHome.trim())) {
      // logHome not specified, use default value
      return DEFAULT_LOG_HOME + File.separator + getDomainUID();
    }
    return logHome;
  }

  String getIncludeServerOutInPodLog() {
    return getDomain().getIncludeServerOutInPodLog();
  }

  protected String getIntrospectHome() {
    return getDomainHome();
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
            .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, getDomainName())
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    return metadata;
  }

  protected V1JobSpec createJobSpec(TuningParameters tuningParameters) {
    V1JobSpec jobSpec =
        new V1JobSpec().backoffLimit(0).template(createPodTemplateSpec(tuningParameters));

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
            .activeDeadlineSeconds(60L)
            .restartPolicy("Never")
            .addContainersItem(createContainer(tuningParameters))
            .addVolumesItem(new V1Volume().name(SECRETS_VOLUME).secret(getSecretsVolume()))
            .addVolumesItem(
                new V1Volume().name(SCRIPTS_VOLUME).configMap(getConfigMapVolumeSource()));

    podSpec.setImagePullSecrets(info.getDomain().getSpec().getImagePullSecrets());

    if (!getClaims().isEmpty()) {
      podSpec.addVolumesItem(
          new V1Volume()
              .name(STORAGE_VOLUME)
              .persistentVolumeClaim(getPersistenVolumeClaimVolumeSource(getClaimName())));
    }
    return podSpec;
  }

  private V1Container createContainer(TuningParameters tuningParameters) {
    return new V1Container()
        .name(getJobName())
        .image(getImageName())
        .imagePullPolicy(getImagePullPolicy())
        .command(getContainerCommand())
        .env(getEnvironmentVariables(tuningParameters))
        .addVolumeMountsItem(volumeMount(STORAGE_VOLUME, STORAGE_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SECRETS_VOLUME, SECRETS_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH));
  }

  String getImageName() {
    String imageName = getDomain().getSpec().getImage();
    if (imageName == null) {
      imageName = KubernetesConstants.DEFAULT_IMAGE;
    }
    return imageName;
  }

  String getImagePullPolicy() {
    return KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  protected List<String> getContainerCommand() {
    return Arrays.asList(WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH);
  }

  abstract List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters);

  protected String getDomainHome() {
    return "/shared/domains/" + getDomainUID();
  }

  static void addEnvVar(List<V1EnvVar> vars, String name, String value) {
    vars.add(new V1EnvVar().name(name).value(value));
  }

  private static V1VolumeMount readOnlyVolumeMount(String volumeName, String mountPath) {
    return volumeMount(volumeName, mountPath).readOnly(true);
  }

  private static V1VolumeMount volumeMount(String volumeName, String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }

  protected V1SecretVolumeSource getSecretsVolume() {
    return new V1SecretVolumeSource().secretName(getAdminSecretName()).defaultMode(420);
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
}
