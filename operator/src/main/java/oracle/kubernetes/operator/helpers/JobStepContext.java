package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.forDomainUid;

import io.kubernetes.client.models.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;

public abstract class JobStepContext {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String SECRETS_VOLUME = "weblogic-credentials-volume";
  private static final String SCRIPTS_VOLUME = "weblogic-domain-cm-volume";
  private static final String STORAGE_VOLUME = "weblogic-domain-storage-volume";
  private static final String SECRETS_MOUNT_PATH = "/weblogic-operator/secrets";
  private static final String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";
  private static final String STORAGE_MOUNT_PATH = "/shared";
  private static final String NODEMGR_HOME = "/u01/nodemanager";
  private static final String LOG_HOME = "/shared/logs";
  private static final String INTROSPECT_HOME = "";
  private static final int FAILURE_THRESHOLD = 1;

  @SuppressWarnings("OctalInteger")
  private static final int ALL_READ_AND_EXECUTE = 0555;

  private static final String READINESS_PROBE = "/weblogic-operator/scripts/readinessProbe.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private static final String READ_WRITE_MANY_ACCESS = "ReadWriteMany";

  private final DomainPresenceInfo info;
  private V1Job jobModel;
  private Map<String, String> substitutionVariables = new HashMap<>();

  JobStepContext(Packet packet) {
    info = packet.getSPI(DomainPresenceInfo.class);
  }

  void init() {
    createSubstitutionMap();
    jobModel = createJobModel();
  }

  private void createSubstitutionMap() {
    substitutionVariables.put("DOMAIN_NAME", getDomainName());
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
   * Deletes the specified pod.
   *
   * @param next the next step to perform after the pod deletion is complete.
   * @return a step to be scheduled.
   */
  public Step deleteJob(Step next) {
    logJobDeleted();
    Step step =
        new CallBuilder()
            .deleteJobAsync(
                getJobName(),
                getNamespace(),
                new V1DeleteOptions().propagationPolicy("Foreground"),
                deleteResponse(next));
    return step;
  }

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
    logJobCreated();
    return new CallBuilder().createJobAsync(getNamespace(), getJobModel(), createResponse(next));
  }

  protected void logJobCreated() {
    LOGGER.info(getJobCreatedMessageKey(), getDomainUID());
  }

  protected void logJobDeleted() {
    LOGGER.info(getJobDeletedMessageKey(), getDomainUID());
  }

  abstract String getJobCreatedMessageKey();

  abstract String getJobDeletedMessageKey();

  private ResponseStep<V1Job> readResponse(Step next) {
    return new ReadResponseStep(next);
  }

  protected String getNodeManagerHome() {
    return NODEMGR_HOME;
  }

  protected String getLogHome() {
    return LOG_HOME;
  }

  protected String getIntrospectHome() {
    return getDomainHome();
  }

  private class ReadResponseStep extends DefaultResponseStep<V1Job> {

    ReadResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Job> callResponse) {
      if (callResponse.getStatusCode() == CallBuilder.NOT_FOUND) {
        return onSuccess(packet, callResponse);
      }
      return super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Job> callResponse) {
      V1Job currentJob = callResponse.getResult();
      return doNext(packet);
    }
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
      return doNext(packet);
    }
  }

  private ResponseStep<V1Status> deleteResponse(Step next) {
    return new DeleteResponseStep(next);
  }

  private class DeleteResponseStep extends ResponseStep<V1Status> {
    DeleteResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Status> callResponses) {
      if (callResponses.getStatusCode() == CallBuilder.NOT_FOUND) {
        return onSuccess(packet, callResponses);
      }
      return super.onFailure(packet, callResponses);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Status> callResponses) {
      return doNext(getNext(), packet);
    }
  }

  Step verifyPersistentVolume(Step next) {
    return new VerifyPersistentVolumeStep(next);
  }

  private class VerifyPersistentVolumeStep extends Step {

    VerifyPersistentVolumeStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      String domainUID = getDomainUID();
      Step list =
          new CallBuilder()
              .withLabelSelectors(forDomainUid(domainUID))
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
                                domainUID,
                                READ_WRITE_MANY_ACCESS);
                          }
                        }
                      } else {
                        LOGGER.warning(
                            MessageKeys.PV_NOT_FOUND_FOR_DOMAIN_UID,
                            getDomainResourceName(),
                            domainUID);
                      }
                      return doNext(packet);
                    }
                  });

      return doNext(list, packet);
    }
  }

  // ---------------------- model methods ------------------------------

  private V1Job createJobModel() {
    return new V1Job()
        .metadata(createMetadata())
        .spec(createJobSpec(TuningParameters.getInstance()));
  }

  protected V1ObjectMeta createMetadata() {
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
    V1PodTemplateSpec podTemplateSpec =
        new V1PodTemplateSpec().spec(createPodSpec(tuningParameters));
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

    V1LocalObjectReference imagePullSecret = info.getDomain().getSpec().getImagePullSecret();
    if (imagePullSecret != null) {
      podSpec.addImagePullSecretsItem(imagePullSecret);
    }

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
        // .addArgsItem("-c")
        // .addArgsItem("echo \"Hello " + getJobName() + "\"")
        .env(getEnvironmentVariables(tuningParameters))
        .addVolumeMountsItem(volumeMount(STORAGE_VOLUME, STORAGE_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SECRETS_VOLUME, SECRETS_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH))
        .readinessProbe(createReadinessProbe(tuningParameters.getPodTuning()))
        .livenessProbe(createLivenessProbe(tuningParameters.getPodTuning()));
  }

  String getImageName() {
    return KubernetesConstants.DEFAULT_IMAGE;
  }

  String getImagePullPolicy() {
    return KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  protected List<String> getContainerCommand() {
    return Arrays.asList("/weblogic-operator/scripts/introspectDomain.sh");
    // return Arrays.asList("/bin/bash");
  }

  abstract List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters);

  void overrideContainerWeblogicEnvVars(List<V1EnvVar> vars) {
    // Override the domain name, domain directory, admin server name and admin server port.
    addEnvVar(vars, "DOMAIN_NAME", getDomainName());
    addEnvVar(vars, "DOMAIN_HOME", getDomainHome());
    addEnvVar(vars, "NODEMGR_HOME", getNodeManagerHome());
    addEnvVar(vars, "LOG_HOME", getLogHome());
    hideAdminUserCredentials(vars);
  }

  protected String getDomainHome() {
    return "/shared/domain/" + getDomainName();
  }

  // Hide the admin account's user name and password.
  // Note: need to use null v.s. "" since if you upload a "" to kubectl then download it,
  // it comes back as a null and V1EnvVar.equals returns false even though it's supposed to
  // be the same value.
  // Regardless, the pod ends up with an empty string as the value (v.s. thinking that
  // the environment variable hasn't been set), so it honors the value (instead of using
  // the default, e.g. 'weblogic' for the user name).
  private static void hideAdminUserCredentials(List<V1EnvVar> vars) {
    addEnvVar(vars, "ADMIN_USERNAME", null);
    addEnvVar(vars, "ADMIN_PASSWORD", null);
  }

  static void addEnvVar(List<V1EnvVar> vars, String name, String value) {
    vars.add(new V1EnvVar().name(name).value(value));
  }

  void doSubstitution(List<V1EnvVar> vars) {
    for (V1EnvVar var : vars) {
      var.setValue(translate(var.getValue()));
    }
  }

  private String translate(String rawValue) {
    String result = rawValue;
    for (Map.Entry<String, String> entry : substitutionVariables.entrySet()) {
      if (result != null && entry.getValue() != null) {
        result = result.replace(String.format("$(%s)", entry.getKey()), entry.getValue());
      }
    }
    return result;
  }

  private V1Handler handler(String... commandItems) {
    return new V1Handler().exec(execAction(commandItems));
  }

  private V1ExecAction execAction(String... commandItems) {
    return new V1ExecAction().command(Arrays.asList(commandItems));
  }

  private static V1VolumeMount readOnlyVolumeMount(String volumeName, String mountPath) {
    return volumeMount(volumeName, mountPath).readOnly(true);
  }

  private static V1VolumeMount volumeMount(String volumeName, String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }

  private V1Probe createReadinessProbe(TuningParameters.PodTuning tuning) {
    V1Probe readinessProbe = new V1Probe();
    readinessProbe
        .initialDelaySeconds(tuning.readinessProbeInitialDelaySeconds)
        .timeoutSeconds(tuning.readinessProbeTimeoutSeconds)
        .periodSeconds(tuning.readinessProbePeriodSeconds)
        .failureThreshold(FAILURE_THRESHOLD)
        .exec(execAction(READINESS_PROBE));
    return readinessProbe;
  }

  private V1Probe createLivenessProbe(TuningParameters.PodTuning tuning) {
    return new V1Probe()
        .initialDelaySeconds(tuning.livenessProbeInitialDelaySeconds)
        .timeoutSeconds(tuning.livenessProbeTimeoutSeconds)
        .periodSeconds(tuning.livenessProbePeriodSeconds)
        .failureThreshold(FAILURE_THRESHOLD)
        .exec(execAction(LIVENESS_PROBE));
  }

  protected V1SecretVolumeSource getSecretsVolume() {
    return new V1SecretVolumeSource().secretName(getAdminSecretName()).defaultMode(420);
  }

  protected V1ConfigMapVolumeSource getConfigMapVolumeSource() {
    return new V1ConfigMapVolumeSource()
        .name(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .defaultMode(ALL_READ_AND_EXECUTE);
  }

  protected V1PersistentVolumeClaimVolumeSource getPersistenVolumeClaimVolumeSource(
      String claimName) {
    return new V1PersistentVolumeClaimVolumeSource().claimName(claimName);
  }
}
