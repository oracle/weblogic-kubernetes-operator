// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.forDomainUid;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
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
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;
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
  protected final WlsServerConfig scan;
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

  private V1Pod getPodModel() {
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

  protected boolean isDomainHomeInImage() {
    return getDomain().isDomainHomeInImage();
  }

  private String getEffectiveLogHome() {
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

  private String getIncludeServerOutInPodLog() {
    return Boolean.toString(getDomain().isIncludeServerOutInPodLog());
  }

  protected List<V1ContainerPort> getContainerPorts() {
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
    return isCurrentPodValid(getPodModel(), currentPod);
  }

  // We want to detect changes that would require replacing an existing Pod
  // however, we've also found that Pod.equals(Pod) isn't right because k8s
  // returns fields, such as nodeName, even when export=true is specified.
  // Therefore, we'll just compare specific fields
  private static boolean isCurrentPodValid(V1Pod build, V1Pod current) {
    List<String> ignoring = getVolumesToIgnore(current);

    return isCurrentPodMetadataValid(build.getMetadata(), current.getMetadata())
        && isCurrentPodSpecValid(build.getSpec(), current.getSpec(), ignoring);
  }

  private static boolean isCurrentPodMetadataValid(V1ObjectMeta build, V1ObjectMeta current) {
    return VersionHelper.matchesResourceVersion(current, DEFAULT_DOMAIN_VERSION)
        && isRestartVersionValid(build, current);
  }

  private static boolean isCurrentPodSpecValid(
      V1PodSpec build, V1PodSpec current, List<String> ignoring) {
    return Objects.equals(current.getSecurityContext(), build.getSecurityContext())
        && KubernetesUtils.mapEquals(current.getNodeSelector(), build.getNodeSelector())
        && equalSets(volumesWithout(current.getVolumes(), ignoring), build.getVolumes())
        && equalSets(current.getImagePullSecrets(), build.getImagePullSecrets())
        && areCompatible(build.getContainers(), current.getContainers(), ignoring);
  }

  private static boolean areCompatible(
      List<V1Container> build, List<V1Container> current, List<String> ignoring) {
    if (build != null) {
      if (current == null) {
        return false;
      }

      for (V1Container bc : build) {
        V1Container fcc = getContainerWithName(current, bc.getName());
        if (fcc == null || !isCompatible(bc, fcc, ignoring)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Compares two pod spec containers for equality.
   *
   * @param build the desired container model
   * @param current the current container, obtained from Kubernetes
   * @param ignoring a list of volume names to ignore
   * @return true if the containers are considered equal
   */
  private static boolean isCompatible(
      V1Container build, V1Container current, List<String> ignoring) {
    return current.getImage().equals(build.getImage())
        && current.getImagePullPolicy().equals(build.getImagePullPolicy())
        && Objects.equals(current.getSecurityContext(), build.getSecurityContext())
        && equalSettings(current.getLivenessProbe(), build.getLivenessProbe())
        && equalSettings(current.getReadinessProbe(), build.getReadinessProbe())
        && resourcesEqual(current.getResources(), build.getResources())
        && equalSets(mountsWithout(current.getVolumeMounts(), ignoring), build.getVolumeMounts())
        && equalSets(current.getPorts(), build.getPorts())
        && equalSets(current.getEnv(), build.getEnv())
        && equalSets(current.getEnvFrom(), build.getEnvFrom());
  }

  private static boolean equalSettings(V1Probe probe1, V1Probe probe2) {
    return Objects.equals(probe1.getInitialDelaySeconds(), probe2.getInitialDelaySeconds())
        && Objects.equals(probe1.getTimeoutSeconds(), probe2.getTimeoutSeconds())
        && Objects.equals(probe1.getPeriodSeconds(), probe2.getPeriodSeconds());
  }

  private static boolean resourcesEqual(V1ResourceRequirements a, V1ResourceRequirements b) {
    return KubernetesUtils.mapEquals(getLimits(a), getLimits(b))
        && KubernetesUtils.mapEquals(getRequests(a), getRequests(b));
  }

  private static Map<String, Quantity> getLimits(V1ResourceRequirements requirements) {
    return requirements == null ? Collections.emptyMap() : requirements.getLimits();
  }

  private static Map<String, Quantity> getRequests(V1ResourceRequirements requirements) {
    return requirements == null ? Collections.emptyMap() : requirements.getRequests();
  }

  private static List<V1Volume> volumesWithout(
      List<V1Volume> volumeMounts, List<String> volumesToIgnore) {
    List<V1Volume> result = new ArrayList<>(volumeMounts);
    for (Iterator<V1Volume> each = result.iterator(); each.hasNext(); ) {
      if (volumesToIgnore.contains(each.next().getName())) {
        each.remove();
      }
    }

    return result;
  }

  private static List<V1VolumeMount> mountsWithout(
      List<V1VolumeMount> volumeMounts, List<String> volumesToIgnore) {
    List<V1VolumeMount> result = new ArrayList<>(volumeMounts);
    for (Iterator<V1VolumeMount> each = result.iterator(); each.hasNext(); ) {
      if (volumesToIgnore.contains(each.next().getName())) {
        each.remove();
      }
    }

    return result;
  }

  private static List<String> getVolumesToIgnore(V1Pod current) {
    List<String> k8sVolumeNames = new ArrayList<>();
    for (V1Container container : getContainers(current)) {
      for (V1VolumeMount mount : getVolumeMounts(container)) {
        if (PodDefaults.K8S_SERVICE_ACCOUNT_MOUNT_PATH.equals(mount.getMountPath())) {
          k8sVolumeNames.add(mount.getName());
        }
      }
    }

    return k8sVolumeNames;
  }

  private static List<V1Container> getContainers(V1Pod current) {
    return Optional.ofNullable(current.getSpec().getContainers()).orElse(Collections.emptyList());
  }

  private static List<V1VolumeMount> getVolumeMounts(V1Container container) {
    return Optional.ofNullable(container.getVolumeMounts()).orElse(Collections.emptyList());
  }

  private static boolean isRestartVersionValid(V1ObjectMeta build, V1ObjectMeta current) {
    return isLabelSame(build, current, LabelConstants.DOMAINRESTARTVERSION_LABEL)
        && isLabelSame(build, current, LabelConstants.CLUSTERRESTARTVERSION_LABEL)
        && isLabelSame(build, current, LabelConstants.SERVERRESTARTVERSION_LABEL);
  }

  private static boolean isLabelSame(V1ObjectMeta build, V1ObjectMeta current, String labelName) {
    return Objects.equals(build.getLabels().get(labelName), current.getLabels().get(labelName));
  }

  private static V1Container getContainerWithName(List<V1Container> containers, String name) {
    for (V1Container cc : containers) {
      if (cc.getName().equals(name)) {
        return cc;
      }
    }
    return null;
  }

  private static <T> boolean equalSets(List<T> first, List<T> second) {
    if (first == second) {
      return true;
    }
    return asSet(first).equals(asSet(second));
  }

  private static <T> Set<T> asSet(List<T> first) {
    return (first == null) ? Collections.emptySet() : new HashSet<>(first);
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
        LOGGER.info(MessageKeys.CYCLING_POD, currentPod, getPodModel());
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

  private V1Pod createPodModel() {
    return new V1Pod().metadata(createMetadata()).spec(createSpec(TuningParameters.getInstance()));
  }

  protected V1ObjectMeta createMetadata() {
    V1ObjectMeta metadata = new V1ObjectMeta().name(getPodName()).namespace(getNamespace());
    // Add custom labels
    getPodLabels().forEach(metadata::putLabelsItem);

    // Add internal labels. This will overwrite any custom labels that conflict with internal
    // labels.
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

    // Add custom annotations
    getPodAnnotations().forEach(metadata::putAnnotationsItem);

    // Add prometheus annotations. This will overwrite any custom annotations with same name.
    AnnotationHelper.annotateForPrometheus(metadata, getDefaultPort());
    return metadata;
  }

  protected V1PodSpec createSpec(TuningParameters tuningParameters) {
    V1PodSpec podSpec =
        new V1PodSpec()
            .addContainersItem(
                createContainer(tuningParameters)
                    .resources(getServerSpec().getResources())
                    .securityContext(getServerSpec().getContainerSecurityContext()))
            .nodeSelector(getServerSpec().getNodeSelectors())
            .securityContext(getServerSpec().getPodSecurityContext());

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
