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

  V1Probe getReadinessProbe() {
    return this.readinessProbe;
  }

  void setReadinessProbe(Integer initialDelay, Integer timeout, Integer period) {
    this.readinessProbe
        .initialDelaySeconds(initialDelay)
        .timeoutSeconds(timeout)
        .periodSeconds(period);
  }

  boolean hasV2Fields() {
    return !this.env.isEmpty();
  }

  void fillInFrom(ServerPod serverPod1) {
    for (V1EnvVar var : serverPod1.getV1EnvVars()) addIfMissing(var);
    copyValues(livenessProbe, serverPod1.livenessProbe);
    copyValues(readinessProbe, serverPod1.readinessProbe);
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

  private static void copyValues(V1Probe toProbe, V1Probe fromProbe) {
    if (toProbe.getInitialDelaySeconds() == null)
      toProbe.setInitialDelaySeconds(fromProbe.getInitialDelaySeconds());
    if (toProbe.getTimeoutSeconds() == null)
      toProbe.setTimeoutSeconds(fromProbe.getTimeoutSeconds());
    if (toProbe.getPeriodSeconds() == null) toProbe.setPeriodSeconds(fromProbe.getPeriodSeconds());
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

  V1Probe getLivenessProbe() {
    return this.livenessProbe;
  }

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    this.livenessProbe
        .initialDelaySeconds(initialDelay)
        .timeoutSeconds(timeout)
        .periodSeconds(period);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("env", env)
        .append("livenessProbe.initialDelaySeconds", livenessProbe.getInitialDelaySeconds())
        .append("livenessProbe.timeoutSeconds", livenessProbe.getTimeoutSeconds())
        .append("livenessProbe.periodSeconds", livenessProbe.getPeriodSeconds())
        .append("readinessProbeProbe.initialDelaySeconds", readinessProbe.getInitialDelaySeconds())
        .append("readinessProbe.timeoutSeconds", readinessProbe.getTimeoutSeconds())
        .append("readinessProbe.periodSeconds", readinessProbe.getPeriodSeconds())
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ServerPod that = (ServerPod) o;

    return new EqualsBuilder()
        .append(env, that.env)
        .append(livenessProbe, that.livenessProbe)
        .append(readinessProbe, that.readinessProbe)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(env)
        .append(livenessProbe)
        .append(readinessProbe)
        .toHashCode();
  }
}
