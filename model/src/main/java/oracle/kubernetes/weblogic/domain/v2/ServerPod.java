// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static java.util.Collections.emptyList;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1Capabilities;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1HostPathVolumeSource;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1SecurityContext;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.validation.Valid;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

class ServerPod {

  /**
   * Environment variables to pass while starting a server.
   *
   * @since 2.0
   */
  @SerializedName("env")
  @Expose
  @Valid
  @Description("A list of environment variables to add to a server")
  private List<V1EnvVar> env = new ArrayList<>();

  /**
   * Defines the settings for the liveness probe. Any that are not specified will default to the
   * runtime liveness probe tuning settings.
   *
   * @since 2.0
   */
  @SerializedName("livenessProbe")
  @Expose
  @Description("Settings for the liveness probe associated with a server")
  private ProbeTuning livenessProbeTuning = new ProbeTuning();

  /**
   * Defines the settings for the readiness probe. Any that are not specified will default to the
   * runtime readiness probe tuning settings.
   *
   * @since 2.0
   */
  @SerializedName("readinessProbe")
  @Expose
  @Description("Settings for the readiness probe associated with a server")
  private ProbeTuning readinessProbeTuning = new ProbeTuning();

  /**
   * Defines the key-value pairs for the pod to fit on a node, the node must have each of the
   * indicated key-value pairs as labels
   *
   * @since 2.0
   */
  @SerializedName("nodeSelector")
  @Expose
  @Description(
      "Selector which must match a node's labels for the pod to be scheduled on that node.")
  private Map<String, String> nodeSelectorMap = new HashMap<String, String>(1);

  /**
   * Defines the requirements and limits for the pod server
   *
   * @since 2.0
   */
  @SerializedName("resources")
  @Expose
  @Description("Memory and cpu minimum requirements and limits for the server")
  private V1ResourceRequirements resourceRequirements =
      new V1ResourceRequirements()
          .limits(new HashMap<String, Quantity>())
          .requests(new HashMap<String, Quantity>());

  /**
   * PodSecurityContext holds pod-level security attributes and common container settings. Some
   * fields are also present in container.securityContext. Field values of container.securityContext
   * take precedence over field values of PodSecurityContext.
   *
   * @since 2.0
   */
  @SerializedName("podSecurityContext")
  @Expose
  @Description("")
  private V1PodSecurityContext podSecurityContext = new V1PodSecurityContext();

  /**
   * SecurityContext holds security configuration that will be applied to a container. Some fields
   * are present in both SecurityContext and PodSecurityContext. When both are set, the values in
   * SecurityContext take precedence
   *
   * @since 2.0
   */
  @SerializedName("containerSecurityContext")
  @Expose
  @Description("SecurityContext holds security configuration that will be applied to a container.")
  private V1SecurityContext containerSecurityContext = new V1SecurityContext();

  /**
   * The additional volumes.
   *
   * @since 2.0
   */
  @SerializedName("volumes")
  @Expose
  @Description("Additional volumes")
  private List<V1Volume> additionalVolumes = new ArrayList<>();

  /**
   * The additional volume mounts.
   *
   * @since 2.0
   */
  @SerializedName("volumeMounts")
  @Expose
  @Description("Additional volume mounts")
  private List<V1VolumeMount> additionalVolumeMounts = new ArrayList<>();

  /**
   * The labels to be attached to pods.
   *
   * @since 2.0
   */
  @SerializedName("podLabels")
  @Expose
  @Description("Labels applied to pods")
  private Map<String, String> podLabels = new HashMap<String, String>();

  /**
   * The annotations to be attached to pods.
   *
   * @since 2.0
   */
  @SerializedName("podAnnotations")
  @Expose
  @Description("Annotations applied to pods")
  private Map<String, String> podAnnotations = new HashMap<String, String>();

  ProbeTuning getReadinessProbeTuning() {
    return this.readinessProbeTuning;
  }

  void setReadinessProbeTuning(Integer initialDelay, Integer timeout, Integer period) {
    this.readinessProbeTuning
        .initialDelaySeconds(initialDelay)
        .timeoutSeconds(timeout)
        .periodSeconds(period);
  }

  ProbeTuning getLivenessProbeTuning() {
    return this.livenessProbeTuning;
  }

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    this.livenessProbeTuning
        .initialDelaySeconds(initialDelay)
        .timeoutSeconds(timeout)
        .periodSeconds(period);
  }

  void fillInFrom(ServerPod serverPod1) {
    for (V1EnvVar var : serverPod1.getV1EnvVars()) addIfMissing(var);
    livenessProbeTuning.copyValues(serverPod1.livenessProbeTuning);
    readinessProbeTuning.copyValues(serverPod1.readinessProbeTuning);
    for (V1Volume var : serverPod1.getAdditionalVolumes()) addIfMissing(var);
    for (V1VolumeMount var : serverPod1.getAdditionalVolumeMounts()) addIfMissing(var);
    serverPod1.getPodLabels().forEach((k, v) -> addPodLabelIfMissing(k, v));
    serverPod1.getPodAnnotations().forEach((k, v) -> addPodAnnotationIfMissing(k, v));
    serverPod1.nodeSelectorMap.forEach(nodeSelectorMap::putIfAbsent);
    copyValues(resourceRequirements, serverPod1.resourceRequirements);
    copyValues(podSecurityContext, serverPod1.podSecurityContext);
    copyValues(containerSecurityContext, serverPod1.containerSecurityContext);
  }

  private void copyValues(V1PodSecurityContext to, V1PodSecurityContext from) {
    if (to.isRunAsNonRoot() == null) to.runAsNonRoot(from.isRunAsNonRoot());
    if (to.getFsGroup() == null) to.fsGroup(from.getFsGroup());
    if (to.getRunAsGroup() == null) to.runAsGroup(from.getRunAsGroup());
    if (to.getRunAsUser() == null) to.runAsUser(from.getRunAsUser());
    if (to.getSeLinuxOptions() == null) to.seLinuxOptions(from.getSeLinuxOptions());
    if (to.getSupplementalGroups() == null) to.supplementalGroups(from.getSupplementalGroups());
    if (to.getSysctls() == null) to.sysctls(from.getSysctls());
  }

  private void copyValues(V1SecurityContext to, V1SecurityContext from) {
    if (to.isAllowPrivilegeEscalation() == null)
      to.allowPrivilegeEscalation(from.isAllowPrivilegeEscalation());
    if (to.isPrivileged() == null) to.privileged(from.isPrivileged());
    if (to.isReadOnlyRootFilesystem() == null)
      to.readOnlyRootFilesystem(from.isReadOnlyRootFilesystem());
    if (to.isRunAsNonRoot() == null) to.runAsNonRoot(from.isRunAsNonRoot());
    if (to.getCapabilities() == null) {
      to.setCapabilities(from.getCapabilities());
    } else {
      copyValues(to.getCapabilities(), from.getCapabilities());
    }
    if (to.getRunAsGroup() == null) to.runAsGroup(from.getRunAsGroup());
    if (to.getRunAsUser() == null) to.runAsUser(from.getRunAsUser());
    if (to.getSeLinuxOptions() == null) to.seLinuxOptions(from.getSeLinuxOptions());
  }

  private void copyValues(V1Capabilities to, V1Capabilities from) {
    if (from.getAdd() != null) {
      List<String> allAddCapabilities = new ArrayList<String>();
      if (to.getAdd() != null) {
        allAddCapabilities =
            Stream.concat(to.getAdd().stream(), from.getAdd().stream())
                .distinct()
                .collect(Collectors.toList());
      }
      to.setAdd(allAddCapabilities);
    }

    if (from.getDrop() != null) {
      List<String> allDropCapabilities = new ArrayList<String>();
      if (to.getDrop() != null) {
        allDropCapabilities =
            Stream.concat(to.getDrop().stream(), from.getDrop().stream())
                .distinct()
                .collect(Collectors.toList());
      }
      to.setDrop(allDropCapabilities);
    }
  }

  private List<V1EnvVar> getV1EnvVars() {
    return Optional.ofNullable(getEnv()).orElse(emptyList());
  }

  private void addIfMissing(V1EnvVar var) {
    if (!hasEnvVar(var.getName())) addEnvVar(var);
  }

  private boolean hasEnvVar(String name) {
    if (env == null) return false;
    for (V1EnvVar var : env) {
      if (var.getName().equals(name)) return true;
    }
    return false;
  }

  private static void copyValues(V1ResourceRequirements to, V1ResourceRequirements from) {
    from.getRequests().forEach(to.getRequests()::putIfAbsent);
    from.getLimits().forEach(to.getLimits()::putIfAbsent);
  }

  private boolean hasVolumeName(String name) {
    for (V1Volume var : additionalVolumes) {
      if (var.getName().equals(name)) return true;
    }
    return false;
  }

  private void addIfMissing(V1Volume var) {
    if (!hasVolumeName(var.getName())) addAdditionalVolume(var);
  }

  private boolean hasVolumeMountName(String name) {
    for (V1VolumeMount var : additionalVolumeMounts) {
      if (var.getName().equals(name)) return true;
    }
    return false;
  }

  private void addIfMissing(V1VolumeMount var) {
    if (!hasVolumeMountName(var.getName())) addAdditionalVolumeMount(var);
  }

  private void addPodLabelIfMissing(String name, String value) {
    if (!podLabels.containsKey(name)) podLabels.put(name, value);
  }

  private void addPodAnnotationIfMissing(String name, String value) {
    if (!podAnnotations.containsKey(name)) podAnnotations.put(name, value);
  }

  List<V1EnvVar> getEnv() {
    return this.env;
  }

  void addEnvVar(V1EnvVar var) {
    if (this.env == null) setEnv(new ArrayList<>());
    this.env.add(var);
  }

  void setEnv(@Nullable List<V1EnvVar> env) {
    this.env = env;
  }

  Map<String, String> getNodeSelector() {
    return nodeSelectorMap;
  }

  void addNodeSelector(String labelKey, String labelValue) {
    this.nodeSelectorMap.put(labelKey, labelValue);
  }

  void setNodeSelector(@Nullable Map<String, String> nodeSelectors) {
    this.nodeSelectorMap = nodeSelectors;
  }

  public V1ResourceRequirements getResourceRequirements() {
    return resourceRequirements;
  }

  public void setResourceRequirements(V1ResourceRequirements resourceRequirements) {
    this.resourceRequirements = resourceRequirements;
  }

  public void addRequestRequirement(String resource, String quantity) {
    resourceRequirements.putRequestsItem(resource, Quantity.fromString(quantity));
  }

  public void addLimitRequirement(String resource, String quantity) {
    resourceRequirements.putLimitsItem(resource, Quantity.fromString(quantity));
  }

  public V1PodSecurityContext getPodSecurityContext() {
    return podSecurityContext;
  }

  public void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    this.podSecurityContext = podSecurityContext;
  }

  public V1SecurityContext getContainerSecurityContext() {
    return containerSecurityContext;
  }

  public void setContainerSecurityContext(V1SecurityContext containerSecurityContext) {
    this.containerSecurityContext = containerSecurityContext;
  }

  void addAdditionalVolume(String name, String path) {
    addAdditionalVolume(
        new V1Volume().name(name).hostPath(new V1HostPathVolumeSource().path(path)));
  }

  private void addAdditionalVolume(V1Volume var) {
    additionalVolumes.add(var);
  }

  void addAdditionalVolumeMount(String name, String path) {
    addAdditionalVolumeMount(new V1VolumeMount().name(name).mountPath(path));
  }

  private void addAdditionalVolumeMount(V1VolumeMount var) {
    additionalVolumeMounts.add(var);
  }

  public List<V1Volume> getAdditionalVolumes() {
    return additionalVolumes;
  }

  public List<V1VolumeMount> getAdditionalVolumeMounts() {
    return additionalVolumeMounts;
  }

  void addPodLabel(String name, String value) {
    podLabels.put(name, value);
  }

  void addPodAnnotations(String name, String value) {
    podAnnotations.put(name, value);
  }

  public Map<String, String> getPodLabels() {
    return podLabels;
  }

  public Map<String, String> getPodAnnotations() {
    return podAnnotations;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("env", env)
        .append("livenessProbe", livenessProbeTuning)
        .append("readinessProbe", readinessProbeTuning)
        .append("additionalVolumes", additionalVolumes)
        .append("additionalVolumeMounts", additionalVolumeMounts)
        .append("podLabels", podLabels)
        .append("podAnnotations", podAnnotations)
        .append("nodeSelector", nodeSelectorMap)
        .append("resourceRequirements", resourceRequirements)
        .append("podSecurityContext", podSecurityContext)
        .append("containerSecurityContext", containerSecurityContext)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ServerPod that = (ServerPod) o;

    return new EqualsBuilder()
        .append(env, that.env)
        .append(livenessProbeTuning, that.livenessProbeTuning)
        .append(readinessProbeTuning, that.readinessProbeTuning)
        .append(additionalVolumes, that.additionalVolumes)
        .append(additionalVolumeMounts, that.additionalVolumeMounts)
        .append(podLabels, that.podLabels)
        .append(podAnnotations, that.podAnnotations)
        .append(nodeSelectorMap, that.nodeSelectorMap)
        .append(resourceRequirements, that.resourceRequirements)
        .append(podSecurityContext, that.podSecurityContext)
        .append(containerSecurityContext, that.containerSecurityContext)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(env)
        .append(livenessProbeTuning)
        .append(readinessProbeTuning)
        .append(additionalVolumes)
        .append(additionalVolumeMounts)
        .append(podLabels)
        .append(podAnnotations)
        .append(nodeSelectorMap)
        .append(resourceRequirements)
        .append(podSecurityContext)
        .append(containerSecurityContext)
        .toHashCode();
  }
}
