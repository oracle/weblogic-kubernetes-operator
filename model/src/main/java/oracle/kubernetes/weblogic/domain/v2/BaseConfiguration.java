// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1SecurityContext;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.operator.ServerStartState;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Configuration values shared by multiple levels: domain, admin server, managed server, and
 * cluster.
 *
 * @since 2.0
 */
public abstract class BaseConfiguration {

  @Description("Configuration affecting server pods")
  private ServerPod serverPod = new ServerPod();

  @Description(
      "Customization affecting ClusterIP Kubernetes services for WebLogic server instances.")
  @SerializedName("serverService")
  @Expose
  private KubernetesResource serverService = new KubernetesResource();

  /** Desired startup state. Legal values are RUNNING or ADMIN. */
  @EnumClass(ServerStartState.class)
  @Description(
      "The state in which the server is to be started. Use ADMIN if server should start "
          + "in the admin state. Defaults to RUNNING.")
  private String serverStartState;

  /**
   * Tells the operator whether the customer wants to restart the server pods. The value can be any
   * String and it can be defined on domain, cluster or server to restart the different pods. After
   * the value is added, the corresponding pods will be terminated and created again. If customer
   * modifies the value again after the pods were recreated, then the pods will again be terminated
   * and recreated.
   *
   * @since 2.0
   */
  @Description(
      "If present, every time this value is updated the operator will restart"
          + " the required servers.")
  private String restartVersion;

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(BaseConfiguration other) {
    if (other == null) {
      return;
    }

    if (serverStartState == null) {
      serverStartState = other.getServerStartState();
    }
    if (overrideStartPolicyFrom(other)) {
      setServerStartPolicy(other.getServerStartPolicy());
    }

    serverPod.fillInFrom(other.serverPod);
    serverService.fillInFrom(other.serverService);
  }

  private boolean overrideStartPolicyFrom(BaseConfiguration other) {
    if (other.isStartAdminServerOnly()) {
      return false;
    }
    return getServerStartPolicy() == null || other.isStartNever();
  }

  boolean isStartAdminServerOnly() {
    return Objects.equals(getServerStartPolicy(), ConfigurationConstants.START_ADMIN_ONLY);
  }

  private boolean isStartNever() {
    return Objects.equals(getServerStartPolicy(), ConfigurationConstants.START_NEVER);
  }

  @Nullable
  String getServerStartState() {
    return serverStartState;
  }

  void setServerStartState(@Nullable String serverStartState) {
    this.serverStartState = serverStartState;
  }

  @Nullable
  public List<V1EnvVar> getEnv() {
    return serverPod.getEnv();
  }

  public void setEnv(@Nullable List<V1EnvVar> env) {
    serverPod.setEnv(env);
  }

  void addEnvironmentVariable(String name, String value) {
    serverPod.addEnvVar(new V1EnvVar().name(name).value(value));
  }

  /**
   * Tells the operator whether the customer wants the server to be running. For non-clustered
   * servers - the operator will start it if the policy isn't NEVER. For clustered servers - the
   * operator will start it if the policy is ALWAYS or the policy is IF_NEEDED and the server needs
   * to be started to get to the cluster's replica count..
   *
   * @since 2.0
   * @param serverStartPolicy start policy
   */
  public abstract void setServerStartPolicy(String serverStartPolicy);

  public abstract String getServerStartPolicy();

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    serverPod.setLivenessProbe(initialDelay, timeout, period);
  }

  ProbeTuning getLivenessProbe() {
    return serverPod.getLivenessProbeTuning();
  }

  void setReadinessProbe(Integer initialDelay, Integer timeout, Integer period) {
    serverPod.setReadinessProbeTuning(initialDelay, timeout, period);
  }

  ProbeTuning getReadinessProbe() {
    return serverPod.getReadinessProbeTuning();
  }

  void addNodeSelector(String labelKey, String labelValue) {
    serverPod.addNodeSelector(labelKey, labelValue);
  }

  Map<String, String> getNodeSelector() {
    return serverPod.getNodeSelector();
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

  V1PodSecurityContext getPodSecurityContext() {
    return serverPod.getPodSecurityContext();
  }

  V1SecurityContext getContainerSecurityContext() {
    return serverPod.getContainerSecurityContext();
  }

  void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    serverPod.setPodSecurityContext(podSecurityContext);
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

  public List<V1VolumeMount> getAdditionalVolumeMounts() {
    return serverPod.getAdditionalVolumeMounts();
  }

  void addAdditionalVolumeMount(String name, String path) {
    serverPod.addAdditionalVolumeMount(name, path);
  }

  Map<String, String> getPodLabels() {
    return serverPod.getLabels();
  }

  void addPodLabel(String name, String value) {
    serverPod.addLabel(name, value);
  }

  Map<String, String> getPodAnnotations() {
    return serverPod.getAnnotations();
  }

  void addPodAnnotation(String name, String value) {
    serverPod.addAnnotations(name, value);
  }

  public void setServerService(KubernetesResource serverService) {
    this.serverService = serverService;
  }

  public KubernetesResource getServerService() {
    return serverService;
  }

  public Map<String, String> getServiceLabels() {
    return serverService.getLabels();
  }

  void addServiceLabel(String name, String value) {
    serverService.addLabel(name, value);
  }

  public Map<String, String> getServiceAnnotations() {
    return serverService.getAnnotations();
  }

  void addServiceAnnotation(String name, String value) {
    serverService.addAnnotations(name, value);
  }

  String getRestartVersion() {
    return restartVersion;
  }

  void setRestartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverStartState", serverStartState)
        .append("serverPod", serverPod)
        .append("serverService", serverService)
        .append("restartVersion", restartVersion)
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

    BaseConfiguration that = (BaseConfiguration) o;

    return new EqualsBuilder()
        .append(serverPod, that.serverPod)
        .append(serverService, that.serverService)
        .append(serverStartState, that.serverStartState)
        .append(restartVersion, that.restartVersion)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(serverPod)
        .append(serverService)
        .append(serverStartState)
        .append(restartVersion)
        .toHashCode();
  }
}
