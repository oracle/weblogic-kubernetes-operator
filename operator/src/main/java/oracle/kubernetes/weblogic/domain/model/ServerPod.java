// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Capabilities;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeBuilder;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1VolumeMountBuilder;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.ShutdownType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static java.util.Collections.emptyList;
import static oracle.kubernetes.operator.helpers.AffinityHelper.getDefaultAntiAffinity;
import static oracle.kubernetes.operator.helpers.PodHelper.createCopy;

class ServerPod extends KubernetesResource {

  private static final Comparator<V1EnvVar> ENV_VAR_COMPARATOR =
      Comparator.comparing(V1EnvVar::getName);
  private static final Comparator<V1Volume> VOLUME_COMPARATOR =
      Comparator.comparing(V1Volume::getName);
  private static final Comparator<V1VolumeMount> VOLUME_MOUNT_COMPARATOR =
      Comparator.comparing(V1VolumeMount::getName);

  /**
   * Environment variables to pass while starting a server.
   *
   * @since 2.0
   */
  @Valid
  @Description("A list of environment variables to set in the container running a WebLogic Server instance. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-resource/#jvm-memory-and-java-option-environment-variables. "
      + "See `kubectl explain pods.spec.containers.env`.")
  private List<V1EnvVar> env = new ArrayList<>();

  /**
   * Defines the settings for the liveness probe. Any that are not specified will default to the
   * runtime liveness probe tuning settings.
   *
   * @since 2.0
   */
  @Description("Settings for the liveness probe associated with a WebLogic Server instance.")
  private final ProbeTuning livenessProbe = new ProbeTuning();

  /**
   * Defines the settings for the readiness probe. Any that are not specified will default to the
   * runtime readiness probe tuning settings.
   *
   * @since 2.0
   */
  @Description("Settings for the readiness probe associated with a WebLogic Server instance.")
  private final ProbeTuning readinessProbe = new ProbeTuning();

  /**
   * Defines the key-value pairs for the pod to fit on a node, the node must have each of the
   * indicated key-value pairs as labels.
   *
   * @since 2.0
   */
  @Description(
      "Selector which must match a Node's labels for the Pod to be scheduled on that Node. "
      + "See `kubectl explain pods.spec.nodeSelector`.")
  private final Map<String, String> nodeSelector = new HashMap<>();

  @Description("The Pod's scheduling constraints."
      + " More info: https://oracle.github.io/weblogic-kubernetes-operator/faq/node-heating/. "
      + " See `kubectl explain pods.spec.affinity`.")
  private V1Affinity affinity = null;

  @Description("If specified, indicates the Pod's priority. \"system-node-critical\" and \"system-cluster-critical\" "
      + "are two special keywords which indicate the highest priorities with the former being the highest priority. "
      + "Any other name must be defined by creating a PriorityClass object with that name. If not specified, the pod "
      + "priority will be the default or zero, if there is no default. "
      + "See `kubectl explain pods.spec.priorityClassName`.")
  private String priorityClassName = null;

  @Description("If specified, all readiness gates will be evaluated for Pod readiness. A Pod is ready when all its "
      + "containers are ready AND all conditions specified in the readiness gates have a status equal to \"True\". "
      + "More info: https://github.com/kubernetes/community/blob/master/keps/sig-network/0007-pod-ready%2B%2B.md.")
  private List<V1PodReadinessGate> readinessGates = new ArrayList<>();

  @Description("Restart policy for all containers within the Pod. One of Always, OnFailure, Never. Default to Always. "
      + "More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy. "
      + "See `kubectl explain pods.spec.restartPolicy`.")
  private String restartPolicy = null;

  @Description("RuntimeClassName refers to a RuntimeClass object in the node.k8s.io group, which should be used to run "
      + "this Pod. If no RuntimeClass resource matches the named class, the Pod will not be run. If unset or empty, "
      + "the \"legacy\" RuntimeClass will be used, which is an implicit class with an empty definition that uses the "
      + "default runtime handler. More "
      + "info: https://github.com/kubernetes/community/blob/master/keps/sig-node/0014-runtime-class.md This is an "
      + "alpha feature and may change in the future. See `kubectl explain pods.spec.runtimeClassName`.")
  private String runtimeClassName = null;

  @Description("NodeName is a request to schedule this Pod onto a specific Node. If it is non-empty, the scheduler "
      + "simply schedules this pod onto that node, assuming that it fits the resource requirements. "
      + "See `kubectl explain pods.spec.nodeName`.")
  private String nodeName = null;

  @Description("If specified, the Pod will be dispatched by the specified scheduler. If not specified, the Pod will be "
      + "dispatched by the default scheduler. See `kubectl explain pods.spec.schedulerName`.")
  private String schedulerName = null;

  @Description("If specified, the Pod's tolerations. See `kubectl explain pods.spec.tolerations`.")
  private List<V1Toleration> tolerations = new ArrayList<>();

  @Description("Name of the ServiceAccount to be used to run this Pod. If it is not set, default "
      + "ServiceAccount will be used. The ServiceAccount has to exist at the time the Pod is created. "
      + "See `kubectl explain pods.spec.serviceAccountName`.")
  private String serviceAccountName = null;

  @Description("HostAliases is an optional list of hosts and IPs that will be injected into the pod's hosts file "
      + "if specified. This is only valid for non-hostNetwork pods.")
  private List<V1HostAlias> hostAliases = new ArrayList<>();

  /**
   * Defines the requirements and limits for the pod server.
   *
   * @since 2.0
   */
  @Description("Memory and CPU minimum requirements and limits for the WebLogic Server instance. "
      + "See `kubectl explain pods.spec.containers.resources`.")
  private final V1ResourceRequirements resources =
      new V1ResourceRequirements().limits(new HashMap<>()).requests(new HashMap<>());

  /**
   * PodSecurityContext holds pod-level security attributes and common container settings. Some
   * fields are also present in container.securityContext. Field values of container.securityContext
   * take precedence over field values of PodSecurityContext.
   *
   * @since 2.0
   */
  @Description("Pod-level security attributes. See `kubectl explain pods.spec.securityContext`. "
      + "Beginning with operator version 4.0.5, if no value is specified for this field, the operator will use default "
      + "content for the pod-level `securityContext`. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/security/domain-security/pod-and-container/.")
  private V1PodSecurityContext podSecurityContext = new V1PodSecurityContext();

  /**
   * InitContainers holds a list of initialization containers that should be run before starting the
   * main containers in this pod.
   *
   * @since 2.1
   */
  @Description("Initialization containers to be included in the server Pod. "
      + "See `kubectl explain pods.spec.initContainers`.")
  private List<V1Container> initContainers = new ArrayList<>();

  /**
   * The additional containers.
   *
   * @since 2.1
   */
  @Description("Additional containers to be included in the server Pod. See `kubectl explain pods.spec.containers`.")
  private List<V1Container> containers = new ArrayList<>();

  /**
   * Configures how the operator should shutdown the server instance.
   *
   * @since 2.2
   */
  @Description("Configures how the operator should shut down the server instance.")
  private final Shutdown shutdown = new Shutdown();

  /**
   * SecurityContext holds security configuration that will be applied to a container. Some fields
   * are present in both SecurityContext and PodSecurityContext. When both are set, the values in
   * SecurityContext take precedence
   *
   * @since 2.0
   */
  @Description("Container-level security attributes. Will override any matching Pod-level attributes. "
      + "See `kubectl explain pods.spec.containers.securityContext`. "
      + "Beginning with operator version 4.0.5, if no value is specified for this field, the operator will use default "
      + "content for container-level `securityContext`. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/security/domain-security/pod-and-container/.")
  private V1SecurityContext containerSecurityContext = new V1SecurityContext();

  public List<V1Volume> getVolumes() {
    return volumes;
  }

  /**
   * The additional volumes.
   *
   * @since 2.0
   */
  @Description("Additional volumes to be created in the server Pod. See `kubectl explain pods.spec.volumes`.")
  private final List<V1Volume> volumes = new ArrayList<>();

  public List<V1VolumeMount> getVolumeMounts() {
    return volumeMounts;
  }

  public void setVolumeMounts(List<V1VolumeMount> volumeMounts) {
    this.volumeMounts.addAll(volumeMounts);
  }

  public void setVolumes(List<V1Volume> volumes) {
    this.volumes.addAll(volumes);
  }

  /**
   * The additional volume mounts.
   *
   * @since 2.0
   */
  @Description("Additional volume mounts for the container running a WebLogic Server instance. "
      + "See `kubectl explain pods.spec.containers.volumeMounts`.")
  private final List<V1VolumeMount> volumeMounts = new ArrayList<>();

  /**
   * The maximum ready wait time.
   *
   * @since 4.0
   */
  @Description("The maximum time in seconds that the operator waits for a WebLogic Server pod to reach the ready "
      + "state before it considers the pod failed. Defaults to 1800 seconds.")
  private Long maxReadyWaitTimeSeconds = null;

  /**
   * The maximum pending wait time.
   *
   * @since 4.0
   */
  @Description("The maximum time in seconds that the operator waits for a WebLogic Server pod to reach the running "
      + "state before it considers the pod failed. Defaults to 5 minutes.")
  private Long maxPendingWaitTimeSeconds = null;

  private static void copyValues(V1ResourceRequirements to, V1ResourceRequirements from) {
    if (from != null) {
      if (from.getRequests() != null) {
        from.getRequests().forEach(to.getRequests()::putIfAbsent);
      }
      if (from.getLimits() != null) {
        from.getLimits().forEach(to.getLimits()::putIfAbsent);
      }
    }
  }

  @SuppressWarnings("Duplicates")
  private void copyValues(V1PodSecurityContext to, V1PodSecurityContext from) {
    if (to.getRunAsNonRoot() == null) {
      to.runAsNonRoot(from.getRunAsNonRoot());
    }
    if (to.getFsGroup() == null) {
      to.fsGroup(from.getFsGroup());
    }
    if (to.getRunAsGroup() == null) {
      to.runAsGroup(from.getRunAsGroup());
    }
    if (to.getRunAsUser() == null) {
      to.runAsUser(from.getRunAsUser());
    }
    if (to.getSeLinuxOptions() == null) {
      to.seLinuxOptions(from.getSeLinuxOptions());
    }
    if (to.getSupplementalGroups() == null) {
      to.supplementalGroups(from.getSupplementalGroups());
    }
    if (to.getSysctls() == null) {
      to.sysctls(from.getSysctls());
    }
  }

  @SuppressWarnings("Duplicates")
  private void copyValues(V1SecurityContext to, V1SecurityContext from) {
    if (to.getAllowPrivilegeEscalation() == null) {
      to.allowPrivilegeEscalation(from.getAllowPrivilegeEscalation());
    }
    if (to.getPrivileged() == null) {
      to.privileged(from.getPrivileged());
    }
    if (to.getReadOnlyRootFilesystem() == null) {
      to.readOnlyRootFilesystem(from.getReadOnlyRootFilesystem());
    }
    if (to.getRunAsNonRoot() == null) {
      to.runAsNonRoot(from.getRunAsNonRoot());
    }
    if (to.getCapabilities() == null) {
      to.setCapabilities(from.getCapabilities());
    } else {
      copyValues(to.getCapabilities(), from.getCapabilities());
    }
    if (to.getRunAsGroup() == null) {
      to.runAsGroup(from.getRunAsGroup());
    }
    if (to.getRunAsUser() == null) {
      to.runAsUser(from.getRunAsUser());
    }
    if (to.getSeLinuxOptions() == null) {
      to.seLinuxOptions(from.getSeLinuxOptions());
    }
  }

  private void copyValues(V1Capabilities to, V1Capabilities from) {
    if (from.getAdd() != null) {
      Stream<String> stream = (to.getAdd() != null)
          ? Stream.concat(to.getAdd().stream(), from.getAdd().stream()) : from.getAdd().stream();
      to.setAdd(stream.distinct().collect(Collectors.toList()));
    }

    if (from.getDrop() != null) {
      Stream<String> stream = (to.getDrop() != null)
          ? Stream.concat(to.getDrop().stream(), from.getDrop().stream()) : from.getDrop().stream();
      to.setDrop(stream.distinct().collect(Collectors.toList()));
    }
  }

  Shutdown getShutdown() {
    return this.shutdown;
  }

  void setShutdown(ShutdownType shutdownType, Long timeoutSeconds, Boolean ignoreSessions, Boolean waitForAllSessions) {
    this.shutdown
        .shutdownType(shutdownType)
        .timeoutSeconds(timeoutSeconds)
        .ignoreSessions(ignoreSessions)
        .waitForAllSessions(waitForAllSessions);
  }

  ProbeTuning getReadinessProbeTuning() {
    return this.readinessProbe;
  }

  void setReadinessProbeTuning(Integer initialDelay, Integer timeout, Integer period) {
    this.readinessProbe
        .initialDelaySeconds(initialDelay)
        .timeoutSeconds(timeout)
        .periodSeconds(period);
  }

  void setReadinessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    this.readinessProbe
        .successThreshold(successThreshold)
        .failureThreshold(failureThreshold);
  }

  ProbeTuning getLivenessProbeTuning() {
    return this.livenessProbe;
  }

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    this.livenessProbe
        .initialDelaySeconds(initialDelay)
        .timeoutSeconds(timeout)
        .periodSeconds(period);
  }

  void setLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    this.livenessProbe
        .successThreshold(successThreshold)
        .failureThreshold(failureThreshold);
  }

  public Long getMaxReadyWaitTimeSeconds() {
    return this.maxReadyWaitTimeSeconds;
  }

  public void setMaxReadyWaitTimeSeconds(@Nullable Long maxReadyWaitTimeSeconds) {
    this.maxReadyWaitTimeSeconds = maxReadyWaitTimeSeconds;
  }

  public Long getMaxPendingWaitTimeSeconds() {
    return this.maxPendingWaitTimeSeconds;
  }

  public void setMaxPendingWaitTimeSeconds(@Nullable Long maxPendingWaitTimeSeconds) {
    this.maxPendingWaitTimeSeconds = maxPendingWaitTimeSeconds;
  }

  void fillInFrom(ServerPod serverPod1) {
    for (V1EnvVar envVar : serverPod1.getV1EnvVars()) {
      addIfMissing(envVar);
    }
    livenessProbe.copyValues(serverPod1.livenessProbe);
    readinessProbe.copyValues(serverPod1.readinessProbe);
    shutdown.copyValues(serverPod1.shutdown);
    for (V1Volume volume : serverPod1.getAdditionalVolumes()) {
      addIfMissing(new V1VolumeBuilder(volume).build());
    }
    for (V1VolumeMount volumeMount : serverPod1.getAdditionalVolumeMounts()) {
      addIfMissing(new V1VolumeMountBuilder(volumeMount).build());
    }
    for (V1Container c : serverPod1.getInitContainers()) {
      addInitContainerIfMissing(createWithEnvCopy(c));
    }
    for (V1Container c : serverPod1.getContainers()) {
      addContainerIfMissing(c);
    }
    fillInFrom((KubernetesResource) serverPod1);
    serverPod1.nodeSelector.forEach(nodeSelector::putIfAbsent);
    copyValues(resources, serverPod1.resources);
    copyValues(podSecurityContext, serverPod1.podSecurityContext);
    copyValues(containerSecurityContext, serverPod1.containerSecurityContext);
    if (maxReadyWaitTimeSeconds == null) {
      maxReadyWaitTimeSeconds = serverPod1.maxReadyWaitTimeSeconds;
    }
    if (maxPendingWaitTimeSeconds == null) {
      maxPendingWaitTimeSeconds = serverPod1.maxPendingWaitTimeSeconds;
    }
    if (serverPod1.affinity != null && isNullOrDefaultAffinity()) {
      affinity = serverPod1.affinity;
    }
    if (priorityClassName == null) {
      priorityClassName = serverPod1.priorityClassName;
    }
    readinessGates.addAll(serverPod1.readinessGates);
    if (restartPolicy == null) {
      restartPolicy = serverPod1.restartPolicy;
    }
    if (runtimeClassName == null) {
      runtimeClassName = serverPod1.runtimeClassName;
    }
    if (nodeName == null) {
      nodeName = serverPod1.nodeName;
    }
    if (serviceAccountName == null) {
      serviceAccountName = serverPod1.serviceAccountName;
    }
    if (schedulerName == null) {
      schedulerName = serverPod1.schedulerName;
    }
    tolerations.addAll(serverPod1.tolerations);
    hostAliases.addAll(serverPod1.hostAliases);
  }

  private boolean isNullOrDefaultAffinity() {
    return affinity == null || affinity.equals(getDefaultAntiAffinity());
  }

  private V1Container createWithEnvCopy(V1Container c) {
    return new V1ContainerBuilder(c).withEnv(createCopy(c.getEnv())).build();
  }

  private void addIfMissing(V1Volume volume) {
    if (!hasVolumeName(volume.getName())) {
      addAdditionalVolume(volume);
    }
  }

  private void addIfMissing(V1EnvVar envVar) {
    if (!hasEnvVar(envVar.getName())) {
      addEnvVar(envVar);
    }
  }

  private void addIfMissing(V1VolumeMount volumeMount) {
    if (!hasVolumeMountName(volumeMount.getName())) {
      addAdditionalVolumeMount(volumeMount);
    }
  }

  private void addInitContainerIfMissing(V1Container c) {
    if (!hasInitContainerName(c.getName())) {
      addInitContainer(c);
    }
  }

  private void addContainerIfMissing(V1Container c) {
    if (!hasContainerName(c.getName())) {
      addContainer(c);
    }
  }

  private List<V1EnvVar> getV1EnvVars() {
    return Optional.ofNullable(getEnv()).orElse(emptyList());
  }

  private boolean hasEnvVar(String name) {
    if (env == null) {
      return false;
    }
    for (V1EnvVar envVar : env) {
      if (envVar.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasVolumeName(String name) {
    for (V1Volume volume : volumes) {
      if (volume.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasVolumeMountName(String name) {
    for (V1VolumeMount volumeMount : volumeMounts) {
      if (volumeMount.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasInitContainerName(String name) {
    for (V1Container c : initContainers) {
      if (c.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasContainerName(String name) {
    for (V1Container c : containers) {
      if (c.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  List<V1EnvVar> getEnv() {
    return this.env;
  }

  void setEnv(@Nullable List<V1EnvVar> env) {
    this.env = env;
  }

  void addEnvVar(V1EnvVar envVar) {
    if (this.env == null) {
      setEnv(new ArrayList<>());
    }
    this.env.add(envVar);
  }

  Map<String, String> getNodeSelector() {
    return nodeSelector;
  }

  void addNodeSelector(String labelKey, String labelValue) {
    this.nodeSelector.put(labelKey, labelValue);
  }

  V1ResourceRequirements getResourceRequirements() {
    return resources;
  }

  void addRequestRequirement(String resource, String quantity) {
    resources.putRequestsItem(resource, Quantity.fromString(quantity));
  }

  void addLimitRequirement(String resource, String quantity) {
    resources.putLimitsItem(resource, Quantity.fromString(quantity));
  }

  V1PodSecurityContext getPodSecurityContext() {
    return podSecurityContext;
  }

  void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    this.podSecurityContext = podSecurityContext;
  }

  List<V1Container> getInitContainers() {
    return initContainers;
  }

  void setInitContainers(List<V1Container> initContainers) {
    this.initContainers = initContainers;
  }

  void addInitContainer(V1Container initContainer) {
    initContainers.add(initContainer);
  }

  List<V1Container> getContainers() {
    return containers;
  }

  void setContainers(List<V1Container> containers) {
    this.containers = containers;
  }

  void addContainer(V1Container container) {
    containers.add(container);
  }

  V1SecurityContext getContainerSecurityContext() {
    return containerSecurityContext;
  }

  void setContainerSecurityContext(V1SecurityContext containerSecurityContext) {
    this.containerSecurityContext = containerSecurityContext;
  }

  void addAdditionalVolume(String name, String path) {
    addAdditionalVolume(
        new V1Volume().name(name).hostPath(new V1HostPathVolumeSource().path(path)));
  }

  void addAdditionalVolume(V1Volume volume) {
    volumes.add(volume);
  }

  void addAdditionalPvClaimVolume(String name, String claimName) {
    addAdditionalVolume(
        new V1Volume().name(name).persistentVolumeClaim(
            new V1PersistentVolumeClaimVolumeSource().claimName(claimName))
    );
  }

  void addAdditionalVolumeMount(String name, String path) {
    addAdditionalVolumeMount(new V1VolumeMount().name(name).mountPath(path));
  }

  private void addAdditionalVolumeMount(V1VolumeMount volumeMount) {
    volumeMounts.add(volumeMount);
  }

  List<V1Volume> getAdditionalVolumes() {
    return volumes;
  }

  List<V1VolumeMount> getAdditionalVolumeMounts() {
    return volumeMounts;
  }

  V1Affinity getAffinity() {
    return affinity;
  }

  void setAffinity(V1Affinity affinity) {
    this.affinity = affinity;
  }

  String getPriorityClassName() {
    return priorityClassName;
  }

  List<V1PodReadinessGate> getReadinessGates() {
    return readinessGates;
  }

  void setPriorityClassName(String priorityClassName) {
    this.priorityClassName = priorityClassName;
  }

  void setReadinessGates(List<V1PodReadinessGate> readinessGates) {
    this.readinessGates = readinessGates;
  }

  void addReadinessGate(V1PodReadinessGate readinessGate) {
    readinessGates.add(readinessGate);
  }

  String getRestartPolicy() {
    return restartPolicy;
  }

  void setRestartPolicy(String restartPolicy) {
    this.restartPolicy = restartPolicy;
  }

  String getRuntimeClassName() {
    return runtimeClassName;
  }

  void setRuntimeClassName(String runtimeClassName) {
    this.runtimeClassName = runtimeClassName;
  }

  String getNodeName() {
    return nodeName;
  }

  void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  String getSchedulerName() {
    return schedulerName;
  }

  void setSchedulerName(String schedulerName) {
    this.schedulerName = schedulerName;
  }

  String getServiceAccountName() {
    return serviceAccountName;
  }

  void setServiceAccountName(String serviceAccountName) {
    this.serviceAccountName = serviceAccountName;
  }

  List<V1Toleration> getTolerations() {
    return tolerations;
  }

  void setTolerations(List<V1Toleration> tolerations) {
    this.tolerations = tolerations;
  }

  void addToleration(V1Toleration toleration) {
    tolerations.add(toleration);
  }

  List<V1HostAlias> getHostAliases() {
    return hostAliases;
  }

  void setHostAliases(List<V1HostAlias> hostAliases) {
    this.hostAliases = hostAliases;
  }

  void addHostAlias(V1HostAlias hostAlias) {
    hostAliases.add(hostAlias);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("env", env)
        .append("livenessProbe", livenessProbe)
        .append("readinessProbe", readinessProbe)
        .append("additionalVolumes", volumes)
        .append("additionalVolumeMounts", volumeMounts)
        .append("nodeSelector", nodeSelector)
        .append("resources", resources)
        .append("podSecurityContext", podSecurityContext)
        .append("containerSecurityContext", containerSecurityContext)
        .append("initContainers", initContainers)
        .append("containers", containers)
        .append("shutdown", shutdown)
        .append("affinity", affinity)
        .append("priorityClassName", priorityClassName)
        .append("readinessGates", readinessGates)
        .append("restartPolicy", restartPolicy)
        .append("runtimeClassName", runtimeClassName)
        .append("nodeName", nodeName)
        .append("schedulerName", schedulerName)
        .append("tolerations", tolerations)
        .append("hostAliases", hostAliases)
        .append("serviceAccountName", serviceAccountName)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ServerPod that = (ServerPod) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(
            DomainResource.sortList(env, ENV_VAR_COMPARATOR),
            DomainResource.sortList(that.env, ENV_VAR_COMPARATOR))
        .append(livenessProbe, that.livenessProbe)
        .append(readinessProbe, that.readinessProbe)
        .append(
            DomainResource.sortList(volumes, VOLUME_COMPARATOR),
            DomainResource.sortList(that.volumes, VOLUME_COMPARATOR))
        .append(
            DomainResource.sortList(volumeMounts, VOLUME_MOUNT_COMPARATOR),
            DomainResource.sortList(that.volumeMounts, VOLUME_MOUNT_COMPARATOR))
        .append(nodeSelector, that.nodeSelector)
        .append(resources, that.resources)
        .append(podSecurityContext, that.podSecurityContext)
        .append(containerSecurityContext, that.containerSecurityContext)
        .append(initContainers, that.initContainers)
        .append(containers, that.containers)
        .append(shutdown, that.shutdown)
        .append(affinity, that.affinity)
        .append(priorityClassName, that.priorityClassName)
        .append(readinessGates, that.readinessGates)
        .append(restartPolicy, that.restartPolicy)
        .append(runtimeClassName, that.runtimeClassName)
        .append(nodeName, that.nodeName)
        .append(schedulerName, that.schedulerName)
        .append(tolerations, that.tolerations)
        .append(hostAliases, that.hostAliases)
        .append(serviceAccountName, that.serviceAccountName)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(DomainResource.sortList(env, ENV_VAR_COMPARATOR))
        .append(livenessProbe)
        .append(readinessProbe)
        .append(DomainResource.sortList(volumes, VOLUME_COMPARATOR))
        .append(DomainResource.sortList(volumeMounts, VOLUME_MOUNT_COMPARATOR))
        .append(nodeSelector)
        .append(resources)
        .append(podSecurityContext)
        .append(containerSecurityContext)
        .append(initContainers)
        .append(containers)
        .append(shutdown)
        .append(affinity)
        .append(priorityClassName)
        .append(readinessGates)
        .append(restartPolicy)
        .append(runtimeClassName)
        .append(nodeName)
        .append(schedulerName)
        .append(tolerations)
        .append(hostAliases)
        .append(serviceAccountName)
        .toHashCode();
  }
}
