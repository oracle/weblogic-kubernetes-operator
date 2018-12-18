// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.forDomainUid;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.*;
import java.io.File;
import java.util.*;
import oracle.kubernetes.operator.*;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;

@SuppressWarnings("deprecation")
public abstract class PodStepContext implements StepContextConstants {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String STOP_SERVER = "/weblogic-operator/scripts/stopServer.sh";
  private static final String START_SERVER = "/weblogic-operator/scripts/startServer.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private static final String READINESS_PATH = "/weblogic";

  private final DomainPresenceInfo info;
  private final WlsDomainConfig domainTopology;
  private final Step conflictStep;
  private V1Pod podModel;
  private Map<String, String> substitutionVariables = new HashMap<>();

  PodStepContext(Step conflictStep, Packet packet) {
    this.conflictStep = conflictStep;
    info = packet.getSPI(DomainPresenceInfo.class);
    domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
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

  private String getEffectiveLogHome() {
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

  abstract Integer getPort();

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
    return isCurrentPodValid(getPodModel(), currentPod);
  }

  // We want to detect changes that would require replacing an existing Pod
  // however, we've also found that Pod.equals(Pod) isn't right because k8s
  // returns fields, such as nodeName, even when export=true is specified.
  // Therefore, we'll just compare specific fields
  private static boolean isCurrentPodValid(V1Pod build, V1Pod current) {
    List<String> ignoring = getVolumesToIgnore(current);
    if (!VersionHelper.matchesResourceVersion(
        current.getMetadata(), VersionConstants.DEFAULT_DOMAIN_VERSION)) {
      return false;
    }

    if (!isRestartVersionValid(build, current)) {
      return false;
    }

    if (areUnequal(
        volumesWithout(current.getSpec().getVolumes(), ignoring), build.getSpec().getVolumes()))
      return false;

    if (areUnequal(current.getSpec().getImagePullSecrets(), build.getSpec().getImagePullSecrets()))
      return false;

    if (areUnequal(getCustomerLabels(current), getCustomerLabels(build))) return false;

    if (areUnequal(current.getMetadata().getAnnotations(), build.getMetadata().getAnnotations()))
      return false;

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

        if (areUnequal(mountsWithout(fcc.getVolumeMounts(), ignoring), bc.getVolumeMounts()))
          return false;

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

  private static List<V1Volume> volumesWithout(
      List<V1Volume> volumeMounts, List<String> volumesToIgnore) {
    List<V1Volume> result = new ArrayList<>(volumeMounts);
    for (Iterator<V1Volume> each = result.iterator(); each.hasNext(); )
      if (volumesToIgnore.contains(each.next().getName())) each.remove();

    return result;
  }

  private static List<V1VolumeMount> mountsWithout(
      List<V1VolumeMount> volumeMounts, List<String> volumesToIgnore) {
    List<V1VolumeMount> result = new ArrayList<>(volumeMounts);
    for (Iterator<V1VolumeMount> each = result.iterator(); each.hasNext(); )
      if (volumesToIgnore.contains(each.next().getName())) each.remove();

    return result;
  }

  private static List<String> getVolumesToIgnore(V1Pod current) {
    List<String> k8sVolumeNames = new ArrayList<>();
    for (V1Container container : getContainers(current))
      for (V1VolumeMount mount : getVolumeMounts(container))
        if (PodDefaults.K8S_SERVICE_ACCOUNT_MOUNT_PATH.equals(mount.getMountPath()))
          k8sVolumeNames.add(mount.getName());

    return k8sVolumeNames;
  }

  private static List<V1Container> getContainers(V1Pod current) {
    return Optional.ofNullable(current.getSpec().getContainers()).orElse(Collections.emptyList());
  }

  private static List<V1VolumeMount> getVolumeMounts(V1Container container) {
    return Optional.ofNullable(container.getVolumeMounts()).orElse(Collections.emptyList());
  }

  private static Map<String, String> getCustomerLabels(V1Pod pod) {
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, String> entry : pod.getMetadata().getLabels().entrySet())
      if (!isOperatorLabel(entry)) result.put(entry.getKey(), entry.getValue());
    return result;
  }

  private static boolean isOperatorLabel(Map.Entry<String, String> label) {
    return label.getKey().startsWith("weblogic.");
  }

  private static boolean isRestartVersionValid(V1Pod build, V1Pod current) {
    V1ObjectMeta m1 = build.getMetadata();
    V1ObjectMeta m2 = current.getMetadata();
    return isLabelSame(m1, m2, LabelConstants.DOMAINRESTARTVERSION_LABEL)
        && isLabelSame(m1, m2, LabelConstants.CLUSTERRESTARTVERSION_LABEL)
        && isLabelSame(m1, m2, LabelConstants.SERVERRESTARTVERSION_LABEL);
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

  private static <T> boolean areUnequal(List<T> a, List<T> b) {
    if (a == b) {
      return false;
    }

    if (a == null) a = Collections.emptyList();
    if (b == null) b = Collections.emptyList();

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

  private static <K, V> boolean areUnequal(Map<K, V> a, Map<K, V> b) {
    return !emptyIfNull(a).equals(emptyIfNull(b));
  }

  private static <K, V> Map<K, V> emptyIfNull(Map<K, V> map) {
    return map != null ? map : Collections.emptyMap();
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
      } else if (canUseCurrentPod(currentPod)) {
        logPodExists();
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
        .putLabelsItem(
            LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
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
    AnnotationHelper.annotateForPrometheus(metadata, getPort());
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
            .addPortsItem(new V1ContainerPort().containerPort(getPort()).protocol("TCP"))
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

  abstract List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters);

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
    hideAdminUserCredentials(vars);
  }

  private String getDomainHome() {
    return getDomain().getDomainHome();
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
        .httpGet(httpGetAction(READINESS_PATH, getPort()));
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
