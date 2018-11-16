// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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

  @Description("Configuration affecting the server pod")
  private ServerPod serverPod = new ServerPod();

  /** Desired startup state. Legal values are RUNNING or ADMIN. */
  @SerializedName("serverStartState")
  @Expose
  @Description("The state in which the server is to be started")
  private String serverStartState;

  /**
   * Tells the operator whether the customer wants the server to be running. For non-clustered
   * servers - the operator will start it if the policy isn't NEVER. For clustered servers - the
   * operator will start it if the policy is ALWAYS or the policy is IF_NEEDED and the server needs
   * to be started to get to the cluster's replica count..
   *
   * @since 2.0
   */
  @SerializedName("serverStartPolicy")
  @Expose
  @Description(
      "The strategy for deciding whether to start a server. "
          + "Legal values are NEVER, ALWAYS, or IF_NEEDED.")
  private String serverStartPolicy;

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(BaseConfiguration other) {
    if (other == null) return;

    if (serverStartState == null) serverStartState = other.getServerStartState();
    if (overrideStartPolicyFrom(other)) serverStartPolicy = other.getServerStartPolicy();

    serverPod.fillInFrom(other.serverPod);
  }

  private boolean overrideStartPolicyFrom(BaseConfiguration other) {
    if (other.isStartAdminServerOnly()) return false;
    return serverStartPolicy == null || other.isStartNever();
  }

  public boolean isStartAdminServerOnly() {
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

  void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  protected String getServerStartPolicy() {
    return serverStartPolicy;
  }

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

  public V1PodSecurityContext getPodSecurityContext() {
    return serverPod.getPodSecurityContext();
  }

  public V1SecurityContext getContainerSecurityContext() {
    return serverPod.getContainerSecurityContext();
  }

  public void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    serverPod.setPodSecurityContext(podSecurityContext);
  }

  public void setContainerSecurityContext(V1SecurityContext containerSecurityContext) {
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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverStartState", serverStartState)
        .append("serverStartPolicy", serverStartPolicy)
        .append("serverPod", serverPod)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    BaseConfiguration that = (BaseConfiguration) o;

    return new EqualsBuilder()
        .append(serverPod, that.serverPod)
        .append(serverStartState, that.serverStartState)
        .append(serverStartPolicy, that.serverStartPolicy)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(serverPod)
        .append(serverStartState)
        .append(serverStartPolicy)
        .toHashCode();
  }
}
