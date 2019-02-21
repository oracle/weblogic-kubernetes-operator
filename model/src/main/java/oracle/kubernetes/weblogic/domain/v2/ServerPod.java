// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static java.util.Collections.emptyList;

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
import java.util.Comparator;
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

@Description("ServerPod describes the configuration for a Kubernetes pod for a server.")
class ServerPod extends KubernetesResource {

  /**
   * Environment variables to pass while starting a server.
   *
   * @since 2.0
   */
  @Valid
  @Description("A list of environment variables to add to a server")
  private List<V1EnvVar> env = new ArrayList<>();

  /**
   * Defines the settings for the liveness probe. Any that are not specified will default to the
   * runtime liveness probe tuning settings.
   *
   * @since 2.0
   */
  @Description("Settings for the liveness probe associated with a server.")
  private ProbeTuning livenessProbe = new ProbeTuning();

  /**
   * Defines the settings for the readiness probe. Any that are not specified will default to the
   * runtime readiness probe tuning settings.
   *
   * @since 2.0
   */
  @Description("Settings for the readiness probe associated with a server.")
  private ProbeTuning readinessProbe = new ProbeTuning();

  /**
   * Defines the key-value pairs for the pod to fit on a node, the node must have each of the
   * indicated key-value pairs as labels.
   *
   * @since 2.0
   */
  @Description(
      "Selector which must match a node's labels for the pod to be scheduled on that node.")
  private Map<String, String> nodeSelector = new HashMap<>();

  /**
   * Defines the requirements and limits for the pod server.
   *
   * @since 2.0
   */
  @Description("Memory and cpu minimum requirements and limits for the server.")
  private V1ResourceRequirements resources =
      new V1ResourceRequirements().limits(new HashMap<>()).requests(new HashMap<>());

  /**
   * PodSecurityContext holds pod-level security attributes and common container settings. Some
   * fields are also present in container.securityContext. Field values of container.securityContext
   * take precedence over field values of PodSecurityContext.
   *
   * @since 2.0
   */
  @Description("Pod-level security attributes.")
  private V1PodSecurityContext podSecurityContext = new V1PodSecurityContext();

  /**
   * SecurityContext holds security configuration that will be applied to a container. Some fields
   * are present in both SecurityContext and PodSecurityContext. When both are set, the values in
   * SecurityContext take precedence
   *
   * @since 2.0
   */
  @Description(
      "Container-level security attributes. Will override any matching pod-level attributes.")
  private V1SecurityContext containerSecurityContext = new V1SecurityContext();

  /**
   * The additional volumes.
   *
   * @since 2.0
   */
  @Description("Additional volumes to be created in the server pod.")
  private List<V1Volume> volumes = new ArrayList<>();

  /**
   * The additional volume mounts.
   *
   * @since 2.0
   */
  @Description("Additional volume mounts for the server pod.")
  private List<V1VolumeMount> volumeMounts = new ArrayList<>();

  ProbeTuning getReadinessProbeTuning() {
    return this.readinessProbe;
  }

  void setReadinessProbeTuning(Integer initialDelay, Integer timeout, Integer period) {
    this.readinessProbe
        .initialDelaySeconds(initialDelay)
        .timeoutSeconds(timeout)
        .periodSeconds(period);
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

  void fillInFrom(ServerPod serverPod1) {
    for (V1EnvVar var : serverPod1.getV1EnvVars()) {
      addIfMissing(var);
    }
    livenessProbe.copyValues(serverPod1.livenessProbe);
    readinessProbe.copyValues(serverPod1.readinessProbe);
    for (V1Volume var : serverPod1.getAdditionalVolumes()) {
      addIfMissing(var);
    }
    for (V1VolumeMount var : serverPod1.getAdditionalVolumeMounts()) {
      addIfMissing(var);
    }
    fillInFrom((KubernetesResource) serverPod1);
    serverPod1.nodeSelector.forEach(nodeSelector::putIfAbsent);
    copyValues(resources, serverPod1.resources);
    copyValues(podSecurityContext, serverPod1.podSecurityContext);
    copyValues(containerSecurityContext, serverPod1.containerSecurityContext);
  }

  @SuppressWarnings("Duplicates")
  private void copyValues(V1PodSecurityContext to, V1PodSecurityContext from) {
    if (to.isRunAsNonRoot() == null) {
      to.runAsNonRoot(from.isRunAsNonRoot());
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
    if (to.isAllowPrivilegeEscalation() == null) {
      to.allowPrivilegeEscalation(from.isAllowPrivilegeEscalation());
    }
    if (to.isPrivileged() == null) {
      to.privileged(from.isPrivileged());
    }
    if (to.isReadOnlyRootFilesystem() == null) {
      to.readOnlyRootFilesystem(from.isReadOnlyRootFilesystem());
    }
    if (to.isRunAsNonRoot() == null) {
      to.runAsNonRoot(from.isRunAsNonRoot());
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

  private static void copyValues(V1ResourceRequirements to, V1ResourceRequirements from) {
    from.getRequests().forEach(to.getRequests()::putIfAbsent);
    from.getLimits().forEach(to.getLimits()::putIfAbsent);
  }

  private void copyValues(V1Capabilities to, V1Capabilities from) {
    if (from.getAdd() != null) {
      List<String> allAddCapabilities = new ArrayList<>();
      if (to.getAdd() != null) {
        allAddCapabilities =
            Stream.concat(to.getAdd().stream(), from.getAdd().stream())
                .distinct()
                .collect(Collectors.toList());
      }
      to.setAdd(allAddCapabilities);
    }

    if (from.getDrop() != null) {
      List<String> allDropCapabilities = new ArrayList<>();
      if (to.getDrop() != null) {
        allDropCapabilities =
            Stream.concat(to.getDrop().stream(), from.getDrop().stream())
                .distinct()
                .collect(Collectors.toList());
      }
      to.setDrop(allDropCapabilities);
    }
  }

  private void addIfMissing(V1Volume var) {
    if (!hasVolumeName(var.getName())) {
      addAdditionalVolume(var);
    }
  }

  private void addIfMissing(V1EnvVar var) {
    if (!hasEnvVar(var.getName())) {
      addEnvVar(var);
    }
  }

  private void addIfMissing(V1VolumeMount var) {
    if (!hasVolumeMountName(var.getName())) {
      addAdditionalVolumeMount(var);
    }
  }

  private List<V1EnvVar> getV1EnvVars() {
    return Optional.ofNullable(getEnv()).orElse(emptyList());
  }

  private boolean hasEnvVar(String name) {
    if (env == null) {
      return false;
    }
    for (V1EnvVar var : env) {
      if (var.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasVolumeName(String name) {
    for (V1Volume var : volumes) {
      if (var.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasVolumeMountName(String name) {
    for (V1VolumeMount var : volumeMounts) {
      if (var.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  List<V1EnvVar> getEnv() {
    return this.env;
  }

  void addEnvVar(V1EnvVar var) {
    if (this.env == null) {
      setEnv(new ArrayList<>());
    }
    this.env.add(var);
  }

  void setEnv(@Nullable List<V1EnvVar> env) {
    this.env = env;
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

  private void addAdditionalVolume(V1Volume var) {
    volumes.add(var);
  }

  void addAdditionalVolumeMount(String name, String path) {
    addAdditionalVolumeMount(new V1VolumeMount().name(name).mountPath(path));
  }

  private void addAdditionalVolumeMount(V1VolumeMount var) {
    volumeMounts.add(var);
  }

  List<V1Volume> getAdditionalVolumes() {
    return volumes;
  }

  List<V1VolumeMount> getAdditionalVolumeMounts() {
    return volumeMounts;
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
        .append("resourceRequirements", resources)
        .append("podSecurityContext", podSecurityContext)
        .append("containerSecurityContext", containerSecurityContext)
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
            Domain.sortOrNull(env, ENV_VAR_COMPARATOR),
            Domain.sortOrNull(that.env, ENV_VAR_COMPARATOR))
        .append(livenessProbe, that.livenessProbe)
        .append(readinessProbe, that.readinessProbe)
        .append(
            Domain.sortOrNull(volumes, VOLUME_COMPARATOR),
            Domain.sortOrNull(that.volumes, VOLUME_COMPARATOR))
        .append(
            Domain.sortOrNull(volumeMounts, VOLUME_MOUNT_COMPARATOR),
            Domain.sortOrNull(that.volumeMounts, VOLUME_MOUNT_COMPARATOR))
        .append(nodeSelector, that.nodeSelector)
        .append(resources, that.resources)
        .append(podSecurityContext, that.podSecurityContext)
        .append(containerSecurityContext, that.containerSecurityContext)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(Domain.sortOrNull(env, ENV_VAR_COMPARATOR))
        .append(livenessProbe)
        .append(readinessProbe)
        .append(Domain.sortOrNull(volumes, VOLUME_COMPARATOR))
        .append(Domain.sortOrNull(volumeMounts, VOLUME_MOUNT_COMPARATOR))
        .append(nodeSelector)
        .append(resources)
        .append(podSecurityContext)
        .append(containerSecurityContext)
        .toHashCode();
  }

  private static final Comparator<V1EnvVar> ENV_VAR_COMPARATOR =
      (a, b) -> {
        return a.getName().compareTo(b.getName());
      };

  private static final Comparator<V1Volume> VOLUME_COMPARATOR =
      (a, b) -> {
        return a.getName().compareTo(b.getName());
      };

  private static final Comparator<V1VolumeMount> VOLUME_MOUNT_COMPARATOR =
      (a, b) -> {
        return a.getName().compareTo(b.getName());
      };
}
