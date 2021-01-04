// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "ServerPod describes the configuration for a Kubernetes pod for a server.")
public class ServerPod {

  @ApiModelProperty("A list of environment variables to add to a server.")
  private List<V1EnvVar> env = new ArrayList<>();

  @ApiModelProperty(
      "The labels to be attached to generated resources. The label names must "
          + "not start with 'weblogic.'.")
  private Map<String, String> labels = new HashMap<>();

  @ApiModelProperty("The annotations to be attached to generated resources.")
  private Map<String, String> annotations = new HashMap<>();

  @ApiModelProperty("Settings for the liveness probe associated with a server.")
  private ProbeTuning livenessProbe;

  @ApiModelProperty("Settings for the readiness probe associated with a server.")
  private ProbeTuning readinessProbe;

  @ApiModelProperty(
      "Selector which must match a node's labels for the pod to be scheduled on that node.")
  private Map<String, String> nodeSelector = new HashMap<>();

  @ApiModelProperty("If specified, the pod's scheduling constraints")
  private V1Affinity affinity;

  @ApiModelProperty(
      "If specified, indicates the pod's priority. \"system-node-critical\" and \"system-cluster-critical\" "
          + "are two special keywords which indicate the highest priorities with the former being the highest "
          + "priority. Any other name must be defined by creating a PriorityClass object with that name. If not "
          + "specified, the pod priority will be default or zero if there is no default.")
  private String priorityClassName;

  @ApiModelProperty(
      "If specified, all readiness gates will be evaluated for pod readiness. A pod is ready when all its "
          + "containers are ready AND all conditions specified in the readiness gates have status equal to \"True\" "
          + "More info: https://github.com/kubernetes/community/blob/master/keps/sig-network/0007-pod-ready%2B%2B.md")
  private List<V1PodReadinessGate> readinessGates = new ArrayList<>();

  @ApiModelProperty(
      "Restart policy for all containers within the pod. One of Always, OnFailure, Never. Default to Always. "
          + "More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy")
  private String restartPolicy;

  @ApiModelProperty(
      "RuntimeClassName refers to a RuntimeClass object in the node.k8s.io group, which should be used to run "
          + "this pod.  If no RuntimeClass resource matches the named class, the pod will not be run. If unset or "
          + "empty, the \"legacy\" RuntimeClass will be used, which is an implicit class with an empty definition that "
          + "uses the default runtime handler. More "
          + "info: https://github.com/kubernetes/community/blob/master/keps/sig-node/0014-runtime-class.md This is an "
          + "alpha feature and may change in the future.")
  private String runtimeClassName;

  @ApiModelProperty(
      "NodeName is a request to schedule this pod onto a specific node. If it is non-empty, the scheduler "
          + "simply schedules this pod onto that node, assuming that it fits resource requirements.")
  private String nodeName;

  @ApiModelProperty(
      "If specified, the pod will be dispatched by specified scheduler. If not specified, the pod will be "
          + "dispatched by default scheduler.")
  private String schedulerName;

  @ApiModelProperty("If specified, the pod's tolerations.")
  private List<V1Toleration> tolerations = new ArrayList<>();

  @ApiModelProperty(
      "Name of the ServiceAccount to be used to run this pod. If it is not set, default "
          + "ServiceAccount will be used. The ServiceAccount has to exist at the time the pod is created.")
  private String serviceAccountName;

  @ApiModelProperty("Memory and CPU minimum requirements and limits for the server.")
  private V1ResourceRequirements resources;

  @ApiModelProperty("Pod-level security attributes.")
  private V1PodSecurityContext podSecurityContext;

  @ApiModelProperty("Initialization containers to be included in the server pod.")
  private List<V1Container> initContainers = new ArrayList<>();

  @ApiModelProperty("Additional containers to be included in the server pod.")
  private List<V1Container> containers = new ArrayList<>();

  @ApiModelProperty("Configures how the operator should shutdown the server instance.")
  private Shutdown shutdown;

  @ApiModelProperty(
      "Container-level security attributes. Will override any matching pod-level attributes.")
  private V1SecurityContext containerSecurityContext;

  @ApiModelProperty("Additional volumes to be created in the server pod.")
  private List<V1Volume> volumes = new ArrayList<>();

  @ApiModelProperty("Additional volume mounts for the server pod.")
  private List<V1VolumeMount> volumeMounts = new ArrayList<>();

  public ServerPod env(List<V1EnvVar> env) {
    this.env = env;
    return this;
  }

  public List<V1EnvVar> env() {
    return env;
  }

  /**
   * Adds env item.
   * @param envItem Environment item
   * @return this
   */
  public ServerPod addEnvItem(V1EnvVar envItem) {
    if (env == null) {
      env = new ArrayList<>();
    }
    env.add(envItem);
    return this;
  }

  public List<V1EnvVar> getEnv() {
    return env;
  }

  public void setEnv(List<V1EnvVar> env) {
    this.env = env;
  }

  public ServerPod labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Map<String, String> labels() {
    return labels;
  }

  /**
   * Puts labels item.
   * @param key Label name
   * @param labelsItem Label value
   * @return this
   */
  public ServerPod putLabelsItem(String key, String labelsItem) {
    if (labels == null) {
      labels = new HashMap<>();
    }
    labels.put(key, labelsItem);
    return this;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public ServerPod annotations(Map<String, String> annotations) {
    this.annotations = annotations;
    return this;
  }

  public Map<String, String> annotations() {
    return annotations;
  }

  /**
   * Puts annotations item.
   * @param key Annotation name
   * @param annotationsItem Annotation value
   * @return this
   */
  public ServerPod putAnnotationsItem(String key, String annotationsItem) {
    if (annotations == null) {
      annotations = new HashMap<>();
    }
    annotations.put(key, annotationsItem);
    return this;
  }

  public Map<String, String> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(Map<String, String> annotations) {
    this.annotations = annotations;
  }

  public ServerPod livenessProve(ProbeTuning livenessProbe) {
    this.livenessProbe = livenessProbe;
    return this;
  }

  public ProbeTuning livenessProbe() {
    return livenessProbe;
  }

  public ProbeTuning getLivenessProbe() {
    return livenessProbe;
  }

  public void setLivenessProbe(ProbeTuning livenessProbe) {
    this.livenessProbe = livenessProbe;
  }

  public ServerPod readinessProbe(ProbeTuning readinessProbe) {
    this.readinessProbe = readinessProbe;
    return this;
  }

  public ProbeTuning readinessProbe() {
    return readinessProbe;
  }

  public ProbeTuning getReadinessProbe() {
    return readinessProbe;
  }

  public void setReadinessProbe(ProbeTuning readinessProbe) {
    this.readinessProbe = readinessProbe;
  }

  public ServerPod nodeSelector(Map<String, String> nodeSelector) {
    this.nodeSelector = nodeSelector;
    return this;
  }

  public Map<String, String> nodeSelector() {
    return nodeSelector;
  }

  /**
   * Puts node selector item.
   * @param key Node selector name
   * @param nodeSelectorItem Node selector value
   * @return this
   */
  public ServerPod putNodeSelectorItem(String key, String nodeSelectorItem) {
    if (nodeSelector == null) {
      nodeSelector = new HashMap<>();
    }
    nodeSelector.put(key, nodeSelectorItem);
    return this;
  }

  public Map<String, String> getNodeSelector() {
    return nodeSelector;
  }

  public void setNodeSelector(Map<String, String> nodeSelector) {
    this.nodeSelector = nodeSelector;
  }

  public ServerPod affinity(V1Affinity affinity) {
    this.affinity = affinity;
    return this;
  }

  public V1Affinity affinity() {
    return affinity;
  }

  public V1Affinity getAffinity() {
    return affinity;
  }

  public void setAffinity(V1Affinity affinity) {
    this.affinity = affinity;
  }

  public ServerPod priorityClassName(String priorityClassName) {
    this.priorityClassName = priorityClassName;
    return this;
  }

  public String priorityClassName() {
    return priorityClassName;
  }

  public String getPriorityClassName() {
    return priorityClassName;
  }

  public void setPriorityClassName(String priorityClassName) {
    this.priorityClassName = priorityClassName;
  }

  public ServerPod readinessGates(List<V1PodReadinessGate> readinessGates) {
    this.readinessGates = readinessGates;
    return this;
  }

  public List<V1PodReadinessGate> readinessGates() {
    return readinessGates;
  }

  /**
   * Adds readiness gate item.
   * @param readinessGateItem Readiness gate
   * @return this
   */
  public ServerPod addReadinessGatesItem(V1PodReadinessGate readinessGateItem) {
    if (readinessGates == null) {
      readinessGates = new ArrayList<>();
    }
    readinessGates.add(readinessGateItem);
    return this;
  }

  public List<V1PodReadinessGate> getReadinessGates() {
    return readinessGates;
  }

  public void setReadinessGates(List<V1PodReadinessGate> readinessGates) {
    this.readinessGates = readinessGates;
  }

  public ServerPod restartPolicy(String restartPolicy) {
    this.restartPolicy = restartPolicy;
    return this;
  }

  public String restartPolicy() {
    return restartPolicy;
  }

  public String getRestartPolicy() {
    return restartPolicy;
  }

  public void setRestartPolicy(String restartPolicy) {
    this.restartPolicy = restartPolicy;
  }

  public ServerPod runtimeClassName(String runtimeClassName) {
    this.runtimeClassName = runtimeClassName;
    return this;
  }

  public String runtimeClassName() {
    return runtimeClassName;
  }

  public String getRuntimeClassName() {
    return runtimeClassName;
  }

  public void setRuntimeClassName(String runtimeClassName) {
    this.runtimeClassName = runtimeClassName;
  }

  public ServerPod nodeName(String nodeName) {
    this.nodeName = nodeName;
    return this;
  }

  public String nodeName() {
    return nodeName;
  }

  public String getNodeName() {
    return nodeName;
  }

  public void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  public ServerPod schedulerName(String schedulerName) {
    this.schedulerName = schedulerName;
    return this;
  }

  public String schedulerName() {
    return schedulerName;
  }

  public String getSchedulerName() {
    return schedulerName;
  }

  public void setSchedulerName(String schedulerName) {
    this.schedulerName = schedulerName;
  }

  public ServerPod tolerations(List<V1Toleration> tolerations) {
    this.tolerations = tolerations;
    return this;
  }

  public List<V1Toleration> tolerations() {
    return tolerations;
  }

  /**
   * Adds tolerations item.
   * @param tolerationsItem Toleration
   * @return this
   */
  public ServerPod addTolerationsItem(V1Toleration tolerationsItem) {
    if (tolerations == null) {
      tolerations = new ArrayList<>();
    }
    tolerations.add(tolerationsItem);
    return this;
  }

  public List<V1Toleration> getTolerations() {
    return tolerations;
  }

  public void setTolerations(List<V1Toleration> tolerations) {
    this.tolerations = tolerations;
  }

  public ServerPod serviceAccountName(String serviceAccountName) {
    this.serviceAccountName = serviceAccountName;
    return this;
  }

  public String serviceAccountName() {
    return serviceAccountName;
  }

  public String getServiceAccountName() {
    return serviceAccountName;
  }

  public void setServiceAccountName(String serviceAccountName) {
    this.serviceAccountName = serviceAccountName;
  }

  public ServerPod resources(V1ResourceRequirements resources) {
    this.resources = resources;
    return this;
  }

  public V1ResourceRequirements resources() {
    return resources;
  }

  public V1ResourceRequirements getResources() {
    return resources;
  }

  public void setResources(V1ResourceRequirements resources) {
    this.resources = resources;
  }

  public ServerPod podSecurityContext(V1PodSecurityContext podSecurityContext) {
    this.podSecurityContext = podSecurityContext;
    return this;
  }

  public V1PodSecurityContext podSecurityContext() {
    return podSecurityContext;
  }

  public V1PodSecurityContext getPodSecurityContext() {
    return podSecurityContext;
  }

  public void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    this.podSecurityContext = podSecurityContext;
  }

  public ServerPod initContainers(List<V1Container> initContainers) {
    this.initContainers = initContainers;
    return this;
  }

  public List<V1Container> initContainers() {
    return initContainers;
  }

  /**
   * Adds init containers item.
   * @param initContainersItem Init container
   * @return this
   */
  public ServerPod addInitContainersItem(V1Container initContainersItem) {
    if (initContainers == null) {
      initContainers = new ArrayList<>();
    }
    initContainers.add(initContainersItem);
    return this;
  }

  public List<V1Container> getInitContainers() {
    return initContainers;
  }

  public void setInitContainers(List<V1Container> initContainers) {
    this.initContainers = initContainers;
  }

  public ServerPod containers(List<V1Container> containers) {
    this.containers = containers;
    return this;
  }

  public List<V1Container> containers() {
    return containers;
  }

  /**
   * Adds containers item.
   * @param containersItem Container
   * @return this
   */
  public ServerPod addContainersItem(V1Container containersItem) {
    if (containers == null) {
      containers = new ArrayList<>();
    }
    containers.add(containersItem);
    return this;
  }

  public List<V1Container> getContainers() {
    return containers;
  }

  public void setContainers(List<V1Container> containers) {
    this.containers = containers;
  }

  public ServerPod shutdown(Shutdown shutdown) {
    this.shutdown = shutdown;
    return this;
  }

  public Shutdown shutdown() {
    return shutdown;
  }

  public Shutdown getShutdown() {
    return shutdown;
  }

  public void setShutdown(Shutdown shutdown) {
    this.shutdown = shutdown;
  }

  public ServerPod containerSecurityContext(V1SecurityContext containerSecurityContext) {
    this.containerSecurityContext = containerSecurityContext;
    return this;
  }

  public V1SecurityContext containerSecurityContext() {
    return containerSecurityContext;
  }

  public V1SecurityContext getContainerSecurityContext() {
    return containerSecurityContext;
  }

  public void setContainerSecurityContext(V1SecurityContext containerSecurityContext) {
    this.containerSecurityContext = containerSecurityContext;
  }

  public ServerPod volumes(List<V1Volume> volumes) {
    this.volumes = volumes;
    return this;
  }

  public List<V1Volume> volumes() {
    return volumes;
  }

  /**
   * Adds volumes item.
   * @param volumesItem Volume
   * @return this
   */
  public ServerPod addVolumesItem(V1Volume volumesItem) {
    if (volumes == null) {
      volumes = new ArrayList<>();
    }
    volumes.add(volumesItem);
    return this;
  }

  public List<V1Volume> getVolumes() {
    return volumes;
  }

  public void setVolumes(List<V1Volume> volumes) {
    this.volumes = volumes;
  }

  public ServerPod volumeMounts(List<V1VolumeMount> volumeMounts) {
    this.volumeMounts = volumeMounts;
    return this;
  }

  public List<V1VolumeMount> volumeMounts() {
    return volumeMounts;
  }

  /**
   * Adds volume mounts item.
   * @param volumeMountsItem Volume mount
   * @return this
   */
  public ServerPod addVolumeMountsItem(V1VolumeMount volumeMountsItem) {
    if (volumeMounts == null) {
      volumeMounts = new ArrayList<>();
    }
    volumeMounts.add(volumeMountsItem);
    return this;
  }

  public List<V1VolumeMount> getVolumeMounts() {
    return volumeMounts;
  }

  public void setVolumeMounts(List<V1VolumeMount> volumeMounts) {
    this.volumeMounts = volumeMounts;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("env", env)
        .append("labels", labels)
        .append("annotations", annotations)
        .append("livenessProbe", livenessProbe)
        .append("readinessProbe", readinessProbe)
        .append("additionalVolumes", volumes)
        .append("additionalVolumeMounts", volumeMounts)
        .append("nodeSelector", nodeSelector)
        .append("resourceRequirements", resources)
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
        .append("serviceAccountName", serviceAccountName)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ServerPod rhs = (ServerPod) other;
    return new EqualsBuilder()
        .append(env, rhs.env)
        .append(labels, rhs.labels)
        .append(annotations, rhs.annotations)
        .append(livenessProbe, rhs.livenessProbe)
        .append(readinessProbe, rhs.readinessProbe)
        .append(nodeSelector, rhs.nodeSelector)
        .append(affinity, rhs.affinity)
        .append(priorityClassName, rhs.priorityClassName)
        .append(readinessGates, rhs.readinessGates)
        .append(restartPolicy, rhs.restartPolicy)
        .append(runtimeClassName, rhs.runtimeClassName)
        .append(nodeName, rhs.nodeName)
        .append(schedulerName, rhs.schedulerName)
        .append(tolerations, rhs.tolerations)
        .append(serviceAccountName, rhs.serviceAccountName)
        .append(resources, rhs.resources)
        .append(podSecurityContext, rhs.podSecurityContext)
        .append(initContainers, rhs.initContainers)
        .append(containers, rhs.containers)
        .append(shutdown, rhs.shutdown)
        .append(containerSecurityContext, rhs.containerSecurityContext)
        .append(volumes, rhs.volumes)
        .append(volumeMounts, rhs.volumeMounts)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(env)
        .append(labels)
        .append(annotations)
        .append(livenessProbe)
        .append(readinessProbe)
        .append(nodeSelector)
        .append(affinity)
        .append(priorityClassName)
        .append(readinessGates)
        .append(restartPolicy)
        .append(runtimeClassName)
        .append(nodeName)
        .append(schedulerName)
        .append(tolerations)
        .append(serviceAccountName)
        .append(resources)
        .append(podSecurityContext)
        .append(initContainers)
        .append(containers)
        .append(shutdown)
        .append(containerSecurityContext)
        .append(volumes)
        .append(volumeMounts)
        .toHashCode();
  }
}
