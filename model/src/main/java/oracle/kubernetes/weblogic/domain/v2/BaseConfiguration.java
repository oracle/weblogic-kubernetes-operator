// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static java.util.Collections.emptyList;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Probe;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.Valid;
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
   * Defines the settings for the liveness probe. Any that are not specified will default to the
   * runtime liveness probe tuning settings.
   *
   * @since 2.0
   */
  @SerializedName("livenessProbe")
  @Expose
  @Description("Settings for the liveness probe associated with a server")
  private V1Probe livenessProbe = new V1Probe();

  /**
   * Defines the settings for the readiness probe. Any that are not specified will default to the
   * runtime readiness probe tuning settings.
   *
   * @since 2.0
   */
  @SerializedName("readinessProbe")
  @Expose
  @Description("Settings for the readiness probe associated with a server")
  private V1Probe readinessProbe = new V1Probe();

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(BaseConfiguration other) {
    if (other == null) return;

    if (serverStartState == null) serverStartState = other.getServerStartState();
    if (overrideStartPolicyFrom(other)) serverStartPolicy = other.getServerStartPolicy();

    for (V1EnvVar var : getV1EnvVars(other)) addIfMissing(var);
    copyValues(livenessProbe, other.livenessProbe);
    copyValues(readinessProbe, other.readinessProbe);
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

  private List<V1EnvVar> getV1EnvVars(BaseConfiguration configuration) {
    return Optional.ofNullable(configuration.getEnv()).orElse(emptyList());
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

  private void copyValues(V1Probe toProbe, V1Probe fromProbe) {
    if (toProbe.getInitialDelaySeconds() == null)
      toProbe.setInitialDelaySeconds(fromProbe.getInitialDelaySeconds());
    if (toProbe.getTimeoutSeconds() == null)
      toProbe.setTimeoutSeconds(fromProbe.getTimeoutSeconds());
    if (toProbe.getPeriodSeconds() == null) toProbe.setPeriodSeconds(fromProbe.getPeriodSeconds());
  }

  /**
   * Returns true if any version 2 configuration fields are specified.
   *
   * @return whether there is version 2 configuration field in this instance
   */
  protected boolean hasV2Fields() {
    return serverStartState != null || serverStartPolicy != null || !env.isEmpty();
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
    return env;
  }

  public void setEnv(@Nullable List<V1EnvVar> env) {
    this.env = env;
  }

  void addEnvironmentVariable(String name, String value) {
    addEnvVar(new V1EnvVar().name(name).value(value));
  }

  private void addEnvVar(V1EnvVar var) {
    if (env == null) setEnv(new ArrayList<>());
    env.add(var);
  }

  void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  protected String getServerStartPolicy() {
    return serverStartPolicy;
  }

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    livenessProbe.initialDelaySeconds(initialDelay).timeoutSeconds(timeout).periodSeconds(period);
  }

  V1Probe getLivenessProbe() {
    return livenessProbe;
  }

  void setReadinessProbe(Integer initialDelay, Integer timeout, Integer period) {
    readinessProbe.initialDelaySeconds(initialDelay).timeoutSeconds(timeout).periodSeconds(period);
  }

  V1Probe getReadinessProbe() {
    return readinessProbe;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverStartState", serverStartState)
        .append("serverStartPolicy", serverStartPolicy)
        .append("livenessProbe.initialDelaySeconds", livenessProbe.getInitialDelaySeconds())
        .append("livenessProbe.timeoutSeconds", livenessProbe.getTimeoutSeconds())
        .append("livenessProbe.periodSeconds", livenessProbe.getPeriodSeconds())
        .append("readinessProbeProbe.initialDelaySeconds", readinessProbe.getInitialDelaySeconds())
        .append("readinessProbe.timeoutSeconds", readinessProbe.getTimeoutSeconds())
        .append("readinessProbe.periodSeconds", readinessProbe.getPeriodSeconds())
        .append("env", env)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    BaseConfiguration that = (BaseConfiguration) o;

    return new EqualsBuilder()
        .append(env, that.env)
        .append(serverStartState, that.serverStartState)
        .append(serverStartPolicy, that.serverStartPolicy)
        .append(livenessProbe, that.livenessProbe)
        .append(readinessProbe, that.readinessProbe)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(env)
        .append(serverStartState)
        .append(serverStartPolicy)
        .append(livenessProbe)
        .append(readinessProbe)
        .toHashCode();
  }
}
