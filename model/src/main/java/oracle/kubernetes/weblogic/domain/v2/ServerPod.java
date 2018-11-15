// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static java.util.Collections.emptyList;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.custom.Quantity;
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
   * <<<<<<< HEAD Defines the key-value pairs for the pod to fit on a node, the node must have each
   * of the indicated key-value pairs as labels
   *
   * @since 2.0
   */
  @SerializedName("nodeSelector")
  @Expose
  @Description("Mapping of key-value pairs for the pod to fit on a node")
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
   * ======= >>>>>>> 2eee6231654d610cd04c6dec4de65c51d358d62a The additional volumes.
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

  boolean hasV2Fields() {
    return !this.env.isEmpty()
        || !this.nodeSelectorMap.isEmpty()
        || !this.resourceRequirements.getRequests().isEmpty()
        || !this.resourceRequirements.getLimits().isEmpty()
        || !this.nodeSelectorMap.isEmpty()
        || !hasAnySecurityContextConfigured()
        || !hasAnyPodSecurityContextConfigured();
  }

  private boolean hasAnySecurityContextConfigured() {
    return podSecurityContext.isRunAsNonRoot() == null
        || podSecurityContext.getFsGroup() == null
        || podSecurityContext.getRunAsGroup() == null
        || podSecurityContext.getRunAsUser() == null
        || podSecurityContext.getSeLinuxOptions() == null
        || podSecurityContext.getSupplementalGroups() == null
        || podSecurityContext.getSysctls() == null;
  }

  private boolean hasAnyPodSecurityContextConfigured() {
    return containerSecurityContext.isAllowPrivilegeEscalation() == null
        || containerSecurityContext.isPrivileged() == null
        || containerSecurityContext.isReadOnlyRootFilesystem() == null
        || containerSecurityContext.isRunAsNonRoot() == null
        || containerSecurityContext.getCapabilities() == null
        || containerSecurityContext.getRunAsGroup() == null
        || containerSecurityContext.getRunAsUser() == null
        || containerSecurityContext.getSeLinuxOptions() == null;
  }

  void fillInFrom(ServerPod serverPod1) {
    for (V1EnvVar var : serverPod1.getV1EnvVars()) addIfMissing(var);
    livenessProbeTuning.copyValues(serverPod1.livenessProbeTuning);
    readinessProbeTuning.copyValues(serverPod1.readinessProbeTuning);
    for (V1Volume var : serverPod1.getAdditionalVolumes()) addIfMissing(var);
    for (V1VolumeMount var : serverPod1.getAdditionalVolumeMounts()) addIfMissing(var);
    serverPod1.nodeSelectorMap.forEach(nodeSelectorMap::putIfAbsent);
    copyValues(resourceRequirements, serverPod1.resourceRequirements);
    copyValues(podSecurityContext, serverPod1.podSecurityContext);
    copyValues(containerSecurityContext, serverPod1.containerSecurityContext);
  }

  private void copyValues(
      V1PodSecurityContext toPodSecurityContext, V1PodSecurityContext fromPodSecurityContext) {
    if (toPodSecurityContext.isRunAsNonRoot() == null)
      toPodSecurityContext.runAsNonRoot(fromPodSecurityContext.isRunAsNonRoot());
    if (toPodSecurityContext.getFsGroup() == null)
      toPodSecurityContext.fsGroup(fromPodSecurityContext.getFsGroup());
    if (toPodSecurityContext.getRunAsGroup() == null)
      toPodSecurityContext.runAsGroup(fromPodSecurityContext.getRunAsGroup());
    if (toPodSecurityContext.getRunAsUser() == null)
      toPodSecurityContext.runAsUser(fromPodSecurityContext.getRunAsUser());
    if (toPodSecurityContext.getSeLinuxOptions() == null)
      toPodSecurityContext.seLinuxOptions(fromPodSecurityContext.getSeLinuxOptions());
    if (toPodSecurityContext.getSupplementalGroups() == null)
      toPodSecurityContext.supplementalGroups(fromPodSecurityContext.getSupplementalGroups());
    if (toPodSecurityContext.getSysctls() == null)
      toPodSecurityContext.sysctls(fromPodSecurityContext.getSysctls());
  }

  private void copyValues(
      V1SecurityContext toContainerSecurityContext,
      V1SecurityContext fromContainerSecurityContext) {
    if (toContainerSecurityContext.isAllowPrivilegeEscalation() == null)
      toContainerSecurityContext.allowPrivilegeEscalation(
          fromContainerSecurityContext.isAllowPrivilegeEscalation());
    if (toContainerSecurityContext.isPrivileged() == null)
      toContainerSecurityContext.privileged(fromContainerSecurityContext.isPrivileged());
    if (toContainerSecurityContext.isReadOnlyRootFilesystem() == null)
      toContainerSecurityContext.readOnlyRootFilesystem(
          fromContainerSecurityContext.isReadOnlyRootFilesystem());
    if (toContainerSecurityContext.isRunAsNonRoot() == null)
      toContainerSecurityContext.runAsNonRoot(fromContainerSecurityContext.isRunAsNonRoot());
    if (toContainerSecurityContext.getCapabilities() == null)
      toContainerSecurityContext.capabilities(fromContainerSecurityContext.getCapabilities());
    if (toContainerSecurityContext.getRunAsGroup() == null)
      toContainerSecurityContext.runAsGroup(fromContainerSecurityContext.getRunAsGroup());
    if (toContainerSecurityContext.getRunAsUser() == null)
      toContainerSecurityContext.runAsUser(fromContainerSecurityContext.getRunAsUser());
    if (toContainerSecurityContext.getSeLinuxOptions() == null)
      toContainerSecurityContext.seLinuxOptions(fromContainerSecurityContext.getSeLinuxOptions());
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

  private static void copyValues(
      V1ResourceRequirements toResourceRequirements,
      V1ResourceRequirements fromResourceRequirements) {
    fromResourceRequirements
        .getRequests()
        .forEach(toResourceRequirements.getRequests()::putIfAbsent);
    fromResourceRequirements.getLimits().forEach(toResourceRequirements.getLimits()::putIfAbsent);
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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("env", env)
        .append("livenessProbe", livenessProbeTuning)
        .append("readinessProbe", readinessProbeTuning)
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
        .append(nodeSelectorMap)
        .append(resourceRequirements)
        .append(podSecurityContext)
        .append(containerSecurityContext)
        .toHashCode();
  }
}
