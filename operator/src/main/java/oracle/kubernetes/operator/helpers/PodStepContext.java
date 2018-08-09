// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.google.common.base.Strings.isNullOrEmpty;

import io.kubernetes.client.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1Handler;
import io.kubernetes.client.models.V1Lifecycle;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1SecretVolumeSource;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;

@SuppressWarnings("deprecation")
public abstract class PodStepContext {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String SECRETS_VOLUME = "weblogic-credentials-volume";
  private static final String SCRIPTS_VOLUME = "weblogic-domain-cm-volume";
  private static final String STORAGE_VOLUME = "weblogic-domain-storage-volume";
  private static final String SECRETS_MOUNT_PATH = "/weblogic-operator/secrets";
  private static final String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";
  private static final String STORAGE_MOUNT_PATH = "/shared";
  private static final int FAILURE_THRESHOLD = 1;

  @SuppressWarnings("OctalInteger")
  private static final int ALL_READ_AND_EXECUTE = 0555;

  private static final String STOP_SERVER = "/weblogic-operator/scripts/stopServer.sh";
  private static final String START_SERVER = "/weblogic-operator/scripts/startServer.sh";
  private static final String READINESS_PROBE = "/weblogic-operator/scripts/readinessProbe.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private final DomainPresenceInfo info;
  private final Step conflictStep;
  private V1Pod podModel;
  private Map<String, String> substitutionVariables = new HashMap<>();

  PodStepContext(Step conflictStep, Packet packet) {
    this.conflictStep = conflictStep;
    info = packet.getSPI(DomainPresenceInfo.class);
  }

  void init() {
    createSubstitutionMap();
    podModel = createPodModel();
  }

  private void createSubstitutionMap() {
    substitutionVariables.put("DOMAIN_NAME", getDomainName());
    substitutionVariables.put("DOMAIN_HOME", getDomainHome());
    substitutionVariables.put("SERVER_NAME", getServerName());
    substitutionVariables.put("ADMIN_NAME", getAsName());
    substitutionVariables.put("ADMIN_PORT", getAsPort().toString());
  }

  private V1Pod getPodModel() {
    return podModel;
  }

  // ------------------------ data methods ----------------------------

  String getNamespace() {
    return info.getNamespace();
  }

  String getDomainUID() {
    return info.getDomain().getSpec().getDomainUID();
  }

  String getDomainName() {
    return info.getDomain().getSpec().getDomainName();
  }

  String getPodName() {
    return LegalNames.toPodName(getDomainUID(), getServerName());
  }

  String getAsName() {
    return info.getDomain().getSpec().getAsName();
  }

  Integer getAsPort() {
    return info.getDomain().getSpec().getAsPort();
  }

  abstract Integer getPort();

  abstract String getServerName();

  private String getAdminSecretName() {
    return info.getDomain().getSpec().getAdminSecret().getName();
  }

  private List<V1PersistentVolumeClaim> getClaims() {
    return info.getClaims().getItems();
  }

  private String getClaimName() {
    return getClaims().iterator().next().getMetadata().getName();
  }

  ServerKubernetesObjects getSko() {
    return ServerKubernetesObjectsManager.getOrCreate(info, getServerName());
  }

  List<ServerStartup> getServerStartups() {
    List<ServerStartup> startups = info.getDomain().getSpec().getServerStartup();
    return startups != null ? startups : Collections.emptyList();
  }

  // ----------------------- step methods ------------------------------

  // Prevent the watcher from recreating pod with old spec
  private void clearRecord() {
    setRecordedPod(null);
  }

  private void setRecordedPod(V1Pod pod) {
    getSko().getPod().set(pod);
  }

  /**
   * Reads the specified pod and decides whether it must be created or replaced.
   *
   * @param next the next step to perform after the pod verification is complete.
   * @return a step to be scheduled.
   */
  Step verifyPod(Step next) {
    return new CallBuilder().readPodAsync(getPodName(), getNamespace(), readResponse(next));
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
    return new CallBuilder().createPodAsync(getNamespace(), getPodModel(), createResponse(next));
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
    return new CallBuilder().createPodAsync(getNamespace(), getPodModel(), replaceResponse(next));
  }

  private void logPodCreated() {
    LOGGER.info(getPodCreatedMessageKey(), getDomainUID(), getServerName());
  }

  private void logPodExists() {
    LOGGER.fine(getPodExistsMessageKey(), getDomainUID(), getServerName());
  }

  private void logPodReplaced() {
    LOGGER.info(getPodReplacedMessageKey(), getDomainUID(), getServerName());
  }

  abstract String getPodCreatedMessageKey();

  abstract String getPodExistsMessageKey();

  abstract String getPodReplacedMessageKey();

  AtomicBoolean getExplicitRestartAdmin() {
    return info.getExplicitRestartAdmin();
  }

  abstract void updateRestartForNewPod();

  abstract void updateRestartForReplacedPod();

  Set<String> getExplicitRestartClusters() {
    return info.getExplicitRestartClusters();
  }

  protected abstract boolean isExplicitRestartThisServer();

  Step createCyclePodStep(Step next) {
    return new CyclePodStep(next);
  }

  private class CyclePodStep extends Step {

    CyclePodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      clearRecord();
      return doNext(deletePod(getNext()), packet);
    }
  }

  private boolean canUseCurrentPod(V1Pod currentPod) {
    return !isExplicitRestartThisServer() && isCurrentPodValid(getPodModel(), currentPod);
  }

  // We want to detect changes that would require replacing an existing Pod
  // however, we've also found that Pod.equals(Pod) isn't right because k8s
  // returns fields, such as nodeName, even when export=true is specified.
  // Therefore, we'll just compare specific fields
  private static boolean isCurrentPodValid(V1Pod build, V1Pod current) {

    if (!VersionHelper.matchesResourceVersion(current.getMetadata(), VersionConstants.DOMAIN_V1)) {
      return false;
    }

    List<V1Container> buildContainers = build.getSpec().getContainers();
    List<V1Container> currentContainers = current.getSpec().getContainers();

    if (buildContainers != null) {
      if (currentContainers == null) {
        return false;
      }

      for (V1Container bc : buildContainers) {
        V1Container fcc = getContainerWithName(currentContainers, bc.getName());
        if (fcc == null) {
          return false;
        }
        if (!fcc.getImage().equals(bc.getImage())
            || !fcc.getImagePullPolicy().equals(bc.getImagePullPolicy())) {
          return false;
        }
        if (areUnequal(fcc.getPorts(), bc.getPorts())) {
          return false;
        }
        if (areUnequal(fcc.getEnv(), bc.getEnv())) {
          return false;
        }
        if (areUnequal(fcc.getEnvFrom(), bc.getEnvFrom())) {
          return false;
        }
      }
    }

    return true;
  }

  private static V1Container getContainerWithName(List<V1Container> containers, String name) {
    for (V1Container cc : containers) {
      if (cc.getName().equals(name)) {
        return cc;
      }
    }
    return null;
  }

  private static <T> boolean areUnequal(List<T> a, List<T> b) {
    if (a == b) {
      return false;
    } else if (a == null || b == null) {
      return true;
    }
    if (a.size() != b.size()) {
      return true;
    }

    List<T> bprime = new ArrayList<>(b);
    for (T at : a) {
      if (!bprime.remove(at)) {
        return true;
      }
    }
    return false;
  }

  Set<String> getExplicitRestartServers() {
    return info.getExplicitRestartServers();
  }

  private ResponseStep<V1Pod> readResponse(Step next) {
    return new ReadResponseStep(next);
  }

  private class ReadResponseStep extends DefaultResponseStep<V1Pod> {

    ReadResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
      V1Pod currentPod = callResponse.getResult();
      if (currentPod == null) {
        updateRestartForNewPod();
        return doNext(createNewPod(getNext()), packet);
      } else if (canUseCurrentPod(currentPod)) {
        logPodExists();
        setRecordedPod(currentPod);
        return doNext(packet);
      } else {
        return doNext(replaceCurrentPod(getNext()), packet);
      }
    }
  }

  private ResponseStep<V1Pod> createResponse(Step next) {
    return new CreateResponseStep(next);
  }

  private class CreateResponseStep extends ResponseStep<V1Pod> {
    CreateResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
      return super.onFailure(conflictStep, packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
      logPodCreated();
      if (callResponse.getResult() != null) {
        setRecordedPod(callResponse.getResult());
      }
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
      return super.onFailure(conflictStep, packet, callResponses);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Status> callResponses) {
      updateRestartForReplacedPod();
      return doNext(replacePod(getNext()), packet);
    }
  }

  private ResponseStep<V1Pod> replaceResponse(Step next) {
    return new ReplacePodResponseStep(next);
  }

  private class ReplacePodResponseStep extends ResponseStep<V1Pod> {
    private final Step next;

    ReplacePodResponseStep(Step next) {
      super(next);
      this.next = next;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
      return super.onFailure(conflictStep, packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {

      V1Pod newPod = callResponse.getResult();
      logPodReplaced();
      if (newPod != null) {
        setRecordedPod(newPod);
      }

      PodAwaiterStepFactory pw = PodHelper.getPodAwaiterStepFactory(packet);
      return doNext(pw.waitForReady(newPod, next), packet);
    }
  }

  // ---------------------- model methods ------------------------------

  private V1Pod createPodModel() {
    return new V1Pod().metadata(createMetadata()).spec(createSpec(TuningParameters.getInstance()));
  }

  protected V1ObjectMeta createMetadata() {
    V1ObjectMeta metadata =
        new V1ObjectMeta()
            .name(getPodName())
            .namespace(getNamespace())
            .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
            .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUID())
            .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, getDomainName())
            .putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName())
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    AnnotationHelper.annotateForPrometheus(metadata, getPort());
    return metadata;
  }

  protected V1PodSpec createSpec(TuningParameters tuningParameters) {
    V1PodSpec podSpec =
        new V1PodSpec()
            .addContainersItem(createContainer(tuningParameters))
            .addVolumesItem(
                new V1Volume()
                    .name(SECRETS_VOLUME)
                    .secret(new V1SecretVolumeSource().secretName(getAdminSecretName())))
            .addVolumesItem(
                new V1Volume()
                    .name(SCRIPTS_VOLUME)
                    .configMap(
                        new V1ConfigMapVolumeSource()
                            .name(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
                            .defaultMode(ALL_READ_AND_EXECUTE)));

    if (!getClaims().isEmpty()) {
      podSpec.addVolumesItem(
          new V1Volume()
              .name(STORAGE_VOLUME)
              .persistentVolumeClaim(
                  new V1PersistentVolumeClaimVolumeSource().claimName(getClaimName())));
    }
    return podSpec;
  }

  private V1Container createContainer(TuningParameters tuningParameters) {
    return new V1Container()
        .name(KubernetesConstants.CONTAINER_NAME)
        .image(getImageName())
        .imagePullPolicy(getImagePullPolicy())
        .command(getContainerCommand())
        .env(getEnvironmentVariables(tuningParameters))
        .addPortsItem(new V1ContainerPort().containerPort(getPort()).protocol("TCP"))
        .lifecycle(createLifecycle())
        .addVolumeMountsItem(volumeMount(STORAGE_VOLUME, STORAGE_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SECRETS_VOLUME, SECRETS_MOUNT_PATH))
        .addVolumeMountsItem(readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH))
        .readinessProbe(createReadinessProbe(tuningParameters.getPodTuning()))
        .livenessProbe(createLivenessProbe(tuningParameters.getPodTuning()));
  }

  private String getImageName() {
    String imageName = info.getDomain().getSpec().getImage();
    return isNullOrEmpty(imageName) ? KubernetesConstants.DEFAULT_IMAGE : imageName;
  }

  String getImagePullPolicy() {
    String imagePullPolicy = info.getDomain().getSpec().getImagePullPolicy();
    return isNullOrEmpty(imagePullPolicy) ? getInferredImagePullPolicy() : imagePullPolicy;
  }

  private boolean useLatestImage() {
    return getImageName().endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX);
  }

  private String getInferredImagePullPolicy() {
    return useLatestImage()
        ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY
        : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  protected List<String> getContainerCommand() {
    return Arrays.asList(START_SERVER, getDomainUID(), getServerName(), getDomainName());
  }

  abstract List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters);

  void overrideContainerWeblogicEnvVars(List<V1EnvVar> vars) {
    // Override the domain name, domain directory, admin server name and admin server port.
    addEnvVar(vars, "DOMAIN_NAME", getDomainName());
    addEnvVar(vars, "DOMAIN_HOME", getDomainHome());
    addEnvVar(vars, "ADMIN_NAME", getAsName());
    addEnvVar(vars, "ADMIN_PORT", getAsPort().toString());
    addEnvVar(vars, "SERVER_NAME", getServerName());
    hideAdminUserCredentials(vars);
  }

  private String getDomainHome() {
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

  private V1Lifecycle createLifecycle() {
    return new V1Lifecycle()
        .preStop(handler(STOP_SERVER, getDomainUID(), getServerName(), getDomainName()));
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
        .exec(execAction(READINESS_PROBE, getDomainName(), getServerName()));
    return readinessProbe;
  }

  private V1Probe createLivenessProbe(TuningParameters.PodTuning tuning) {
    return new V1Probe()
        .initialDelaySeconds(tuning.livenessProbeInitialDelaySeconds)
        .timeoutSeconds(tuning.livenessProbeTimeoutSeconds)
        .periodSeconds(tuning.livenessProbePeriodSeconds)
        .failureThreshold(FAILURE_THRESHOLD)
        .exec(execAction(LIVENESS_PROBE, getDomainName(), getServerName()));
  }
}
