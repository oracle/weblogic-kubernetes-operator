// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.forDomainUidSelector;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;

import io.kubernetes.client.custom.IntOrString;
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
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonPatchBuilder;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
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
import org.apache.commons.lang3.builder.EqualsBuilder;

@SuppressWarnings("deprecation")
public abstract class PodStepContext extends StepContextBase {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String STOP_SERVER = "/weblogic-operator/scripts/stopServer.sh";
  private static final String START_SERVER = "/weblogic-operator/scripts/startServer.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private static final String READINESS_PATH = "/weblogic/ready";

  private final DomainPresenceInfo info;
  private final WlsDomainConfig domainTopology;
  final WlsServerConfig scan;
  private final Step conflictStep;
  private V1Pod podModel;

  PodStepContext(Step conflictStep, Packet packet) {
    this.conflictStep = conflictStep;
    info = packet.getSPI(DomainPresenceInfo.class);
    domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    scan = (WlsServerConfig) packet.get(ProcessingConstants.SERVER_SCAN);
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

  V1Pod getPodModel() {
    return podModel;
  }

  abstract ServerSpec getServerSpec();

  private Step getConflictStep() {
    return new ConflictStep();
  }

  abstract Map<String, String> getPodLabels();

  abstract Map<String, String> getPodAnnotations();

  private class ConflictStep extends Step {

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
    return domainTopology.getName();
  }

  private String getDomainResourceName() {
    return info.getDomain().getMetadata().getName();
  }

  String getPodName() {
    return LegalNames.toPodName(getDomainUID(), getServerName());
  }

  String getAsName() {
    return domainTopology.getAdminServerName();
  }

  Integer getAsPort() {
    return domainTopology.getServerConfig(domainTopology.getAdminServerName()).getListenPort();
  }

  private String getLogHome() {
    return getDomain().getLogHome();
  }

  private String getEffectiveLogHome() {
    if (!getDomain().getLogHomeEnabled()) return null;
    String logHome = getLogHome();
    if (logHome == null || "".equals(logHome.trim())) {
      // logHome not specified, use default value
      return DEFAULT_LOG_HOME + File.separator + getDomainUID();
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
                  .name(LegalNames.toDNS1123LegalName(nap.getName()))
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

  ServerKubernetesObjects getSko() {
    return info.getServers().computeIfAbsent(getServerName(), k -> new ServerKubernetesObjects());
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
    LOGGER.info(getPodCreatedMessageKey(), getDomainUID(), getServerName());
  }

  private void logPodExists() {
    LOGGER.fine(getPodExistsMessageKey(), getDomainUID(), getServerName());
  }

  private void logPodPatched() {
    LOGGER.info(getPodPatchedMessageKey(), getDomainUID(), getServerName());
  }

  private void logPodReplaced() {
    LOGGER.info(getPodReplacedMessageKey(), getDomainUID(), getServerName());
  }

  abstract String getPodCreatedMessageKey();

  abstract String getPodExistsMessageKey();

  abstract String getPodPatchedMessageKey();

  abstract String getPodReplacedMessageKey();

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

  private class VerifyPodStep extends Step {

    VerifyPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      V1Pod currentPod = getSko().getPod().get();
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

  private ResponseStep<V1Pod> createResponse(Step next) {
    return new CreateResponseStep(next);
  }

  private class CreateResponseStep extends ResponseStep<V1Pod> {
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
      return super.onFailure(getConflictStep(), packet, callResponses);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Status> callResponses) {
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
      return super.onFailure(getConflictStep(), packet, callResponse);
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

  private ResponseStep<V1Pod> patchResponse(Step next) {
    return new PatchPodResponseStep(next);
  }

  private class PatchPodResponseStep extends ResponseStep<V1Pod> {
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

      PodAwaiterStepFactory pw = PodHelper.getPodAwaiterStepFactory(packet);
      return doNext(pw.waitForReady(newPod, next), packet);
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
              .withLabelSelectors(forDomainUidSelector(domainUID))
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

  private V1Pod createPodModel() {
    return withNonHashedElements(AnnotationHelper.withSha256Hash(createPodRecipe()));
  }

  // Adds labels and annotations to a pod, skipping any whose names begin with "weblogic."
  V1Pod withNonHashedElements(V1Pod pod) {
    V1ObjectMeta metadata = pod.getMetadata();
    getPodLabels().entrySet().stream()
        .filter(PodStepContext::isCustomerItem)
        .forEach(e -> metadata.putLabelsItem(e.getKey(), e.getValue()));
    getPodAnnotations().entrySet().stream()
        .filter(PodStepContext::isCustomerItem)
        .forEach(e -> metadata.putAnnotationsItem(e.getKey(), e.getValue()));
    return pod;
  }

  private static boolean isCustomerItem(Map.Entry<String, String> entry) {
    return !entry.getKey().startsWith("weblogic.");
  }

  // Creates a pod model containing elements which are not patchable.
  private V1Pod createPodRecipe() {
    return new V1Pod().metadata(createMetadata()).spec(createSpec(TuningParameters.getInstance()));
  }

  protected V1ObjectMeta createMetadata() {
    V1ObjectMeta metadata = new V1ObjectMeta().name(getPodName()).namespace(getNamespace());
    metadata
        .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION)
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUID())
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
    V1PodSpec podSpec =
        new V1PodSpec()
            .containers(getServerSpec().getContainers())
            .addContainersItem(
                createContainer(tuningParameters)
                    .resources(getServerSpec().getResources())
                    .securityContext(getServerSpec().getContainerSecurityContext()))
            .nodeSelector(getServerSpec().getNodeSelectors())
            .securityContext(getServerSpec().getPodSecurityContext())
            .initContainers(getServerSpec().getInitContainers());

    podSpec.setImagePullSecrets(getServerSpec().getImagePullSecrets());

    for (V1Volume additionalVolume : getVolumes(getDomainUID())) {
      podSpec.addVolumesItem(additionalVolume);
    }

    return podSpec;
  }

  private List<V1Volume> getVolumes(String domainUID) {
    List<V1Volume> volumes = PodDefaults.getStandardVolumes(domainUID);
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

    if (!mockWLS()) {
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
    List<V1VolumeMount> mounts = PodDefaults.getStandardVolumeMounts(getDomainUID());
    mounts.addAll(getServerSpec().getAdditionalVolumeMounts());
    return mounts;
  }

  void overrideContainerWeblogicEnvVars(List<V1EnvVar> vars) {
    // Override the domain name, domain directory, admin server name and admin server port.
    addEnvVar(vars, "DOMAIN_NAME", getDomainName());
    addEnvVar(vars, "DOMAIN_HOME", getDomainHome());
    addEnvVar(vars, "ADMIN_NAME", getAsName());
    addEnvVar(vars, "ADMIN_PORT", getAsPort().toString());
    addEnvVar(vars, "SERVER_NAME", getServerName());
    addEnvVar(vars, "DOMAIN_UID", getDomainUID());
    addEnvVar(vars, "NODEMGR_HOME", NODEMGR_HOME);
    addEnvVar(vars, "LOG_HOME", getEffectiveLogHome());
    addEnvVar(vars, "SERVER_OUT_IN_POD_LOG", getIncludeServerOutInPodLog());
    addEnvVar(
        vars, "SERVICE_NAME", LegalNames.toServerServiceName(getDomainUID(), getServerName()));
    addEnvVar(vars, "AS_SERVICE_NAME", LegalNames.toServerServiceName(getDomainUID(), getAsName()));
    if (mockWLS()) {
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
        .failureThreshold(FAILURE_THRESHOLD)
        .httpGet(httpGetAction(READINESS_PATH, getDefaultPort()));
    return readinessProbe;
  }

  @SuppressWarnings("SameParameterValue")
  private V1HTTPGetAction httpGetAction(String path, int port) {
    V1HTTPGetAction getAction = new V1HTTPGetAction();
    getAction.path(path).port(new IntOrString(port));
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

  private boolean mockWLS() {
    return Boolean.getBoolean("mockWLS");
  }
}
