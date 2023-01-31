// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.processing.EffectiveServerPodSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.helpers.AffinityHelper.getDefaultAntiAffinity;

/**
 * Configuration values shared by multiple levels: domain, admin server, managed server, and
 * cluster.
 *
 * @since 2.0
 */
public class ServerPodConfiguration implements EffectiveServerPodSpec {

  @Description("Customization affecting the generation of Pods for WebLogic Server instances or Introspector Job Pod.")
  protected final ServerPod serverPod = new ServerPod();

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(ServerPodConfiguration other) {
    if (other == null) {
      return;
    }

    serverPod.fillInFrom(other.serverPod);
  }

  public ServerPod getServerPod() {
    return serverPod;
  }

  public ServerPodConfiguration getServerPodSpec() {
    return this;
  }

  public void setEnv(@Nullable List<V1EnvVar> env) {
    serverPod.setEnv(env);
  }

  void addEnvironmentVariable(String name, String value) {
    serverPod.addEnvVar(new V1EnvVar().name(name).value(value));
  }

  void addEnvironmentVariable(V1EnvVar envVar) {
    serverPod.addEnvVar(envVar);
  }

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    serverPod.setLivenessProbe(initialDelay, timeout, period);
  }

  public void setLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    serverPod.setLivenessProbeThresholds(successThreshold, failureThreshold);
  }

  public ProbeTuning getLivenessProbe() {
    return serverPod.getLivenessProbeTuning();
  }

  void setReadinessProbe(Integer initialDelay, Integer timeout, Integer period) {
    serverPod.setReadinessProbeTuning(initialDelay, timeout, period);
  }

  void setReadinessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    serverPod.setReadinessProbeThresholds(successThreshold, failureThreshold);
  }

  public ProbeTuning getReadinessProbe() {
    return serverPod.getReadinessProbeTuning();
  }

  public Shutdown getShutdown() {
    return serverPod.getShutdown();
  }

  void addNodeSelector(String labelKey, String labelValue) {
    serverPod.addNodeSelector(labelKey, labelValue);
  }

  Map<String, String> getNodeSelector() {
    return serverPod.getNodeSelector();
  }

  public V1Affinity getAffinity() {
    return Optional.ofNullable(serverPod.getAffinity()).orElse(getDefaultAntiAffinity());
  }

  void setAffinity(V1Affinity affinity) {
    serverPod.setAffinity(affinity);
  }

  public String getPriorityClassName() {
    return serverPod.getPriorityClassName();
  }

  void setPriorityClassName(String priorityClassName) {
    serverPod.setPriorityClassName(priorityClassName);
  }

  public List<V1PodReadinessGate> getReadinessGates() {
    return serverPod.getReadinessGates();
  }

  void addReadinessGate(V1PodReadinessGate readinessGate) {
    serverPod.addReadinessGate(readinessGate);
  }

  public String getRestartPolicy() {
    return serverPod.getRestartPolicy();
  }

  void setRestartPolicy(String restartPolicy) {
    serverPod.setRestartPolicy(restartPolicy);
  }

  public String getRuntimeClassName() {
    return serverPod.getRuntimeClassName();
  }

  void setRuntimeClassName(String runtimeClassName) {
    serverPod.setRuntimeClassName(runtimeClassName);
  }

  public String getNodeName() {
    return serverPod.getNodeName();
  }

  public String getServiceAccountName() {
    return serverPod.getServiceAccountName();
  }

  public void setNodeName(String nodeName) {
    serverPod.setNodeName(nodeName);
  }

  public String getSchedulerName() {
    return serverPod.getSchedulerName();
  }

  void setSchedulerName(String schedulerName) {
    serverPod.setSchedulerName(schedulerName);
  }

  public List<V1Toleration> getTolerations() {
    return serverPod.getTolerations();
  }

  void addToleration(V1Toleration toleration) {
    serverPod.addToleration(toleration);
  }

  public List<V1HostAlias> getHostAliases() {
    return serverPod.getHostAliases();
  }

  public void setHostAliases(List<V1HostAlias> hostAliases) {
    serverPod.setHostAliases(hostAliases);
  }

  public V1ResourceRequirements getResources() {
    return serverPod.getResourceRequirements();
  }

  void addRequestRequirement(String resource, String quantity) {
    serverPod.addRequestRequirement(resource, quantity);
  }

  void addLimitRequirement(String resource, String quantity) {
    serverPod.addLimitRequirement(resource, quantity);
  }

  public V1PodSecurityContext getPodSecurityContext() {
    return serverPod.getPodSecurityContext();
  }

  void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    serverPod.setPodSecurityContext(podSecurityContext);
  }

  public V1SecurityContext getContainerSecurityContext() {
    return serverPod.getContainerSecurityContext();
  }

  void setContainerSecurityContext(V1SecurityContext containerSecurityContext) {
    serverPod.setContainerSecurityContext(containerSecurityContext);
  }

  public List<V1Volume> getAdditionalVolumes() {
    return serverPod.getAdditionalVolumes();
  }

  void addAdditionalVolume(String name, String path) {
    serverPod.addAdditionalVolume(name, path);
  }

  void addAdditionalVolume(V1Volume volume) {
    serverPod.addAdditionalVolume(volume);
  }

  void addAdditionalPvClaimVolume(String name, String claimName) {
    serverPod.addAdditionalPvClaimVolume(name, claimName);
  }

  @Override
  public List<V1EnvVar> getEnv() {
    return serverPod.getEnv();
  }

  public List<V1VolumeMount> getAdditionalVolumeMounts() {
    return serverPod.getAdditionalVolumeMounts();
  }

  void addAdditionalVolumeMount(String name, String path) {
    serverPod.addAdditionalVolumeMount(name, path);
  }

  void addInitContainer(V1Container initContainer) {
    serverPod.addInitContainer(initContainer);
  }

  void addContainer(V1Container container) {
    serverPod.addContainer(container);
  }

  public Map<String, String> getPodLabels() {
    return serverPod.getLabels();
  }

  void addPodLabel(String name, String value) {
    serverPod.addLabel(name, value);
  }

  public Map<String, String> getPodAnnotations() {
    return serverPod.getAnnotations();
  }

  void addPodAnnotation(String name, String value) {
    serverPod.addAnnotations(name, value);
  }

  public List<V1Container> getInitContainers() {
    return serverPod.getInitContainers();
  }

  public List<V1Container> getContainers() {
    return serverPod.getContainers();
  }

  public Long getMaximumReadyWaitTimeSeconds() {
    return serverPod.getMaxReadyWaitTimeSeconds();
  }

  public Long getMaximumPendingWaitTimeSeconds() {
    return serverPod.getMaxPendingWaitTimeSeconds();
  }

  public void setMaxReadyWaitTimeSeconds(long waitTime) {
    serverPod.setMaxReadyWaitTimeSeconds(waitTime);
  }

  public void setMaxPendingWaitTimeSeconds(long waitTime) {
    serverPod.setMaxPendingWaitTimeSeconds(waitTime);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverPod", serverPod)
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

    ServerPodConfiguration that = (ServerPodConfiguration) o;

    return new EqualsBuilder()
        .append(serverPod, that.serverPod)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(serverPod)
        .toHashCode();
  }
}
