// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** NonClusteredServerConfig describes the desired state of a non-clustered server. */
public class NonClusteredServerConfig extends ServerConfig {

  public static final String NON_CLUSTERED_SERVER_START_POLICY_ALWAYS = SERVER_START_POLICY_ALWAYS;
  public static final String NON_CLUSTERED_SERVER_START_POLICY_NEVER = SERVER_START_POLICY_NEVER;

  private String nonClusteredServerStartPolicy;

  /**
   * Whether this non-clustered server should be started.
   *
   * @return non-clustered server start policy
   */
  public String getNonClusteredServerStartPolicy() {
    return nonClusteredServerStartPolicy;
  }

  /**
   * Whether this non-clustered server should be started.
   *
   * @param nonClusteredServerStartPolicy non-clustered server start policy
   */
  public void setNonClusteredServerStartPolicy(String nonClusteredServerStartPolicy) {
    this.nonClusteredServerStartPolicy = nonClusteredServerStartPolicy;
  }

  /**
   * Whether this non-clustered server should be started.
   *
   * @param nonClusteredServerStartPolicy non-clustered server start policy
   * @return this
   */
  public NonClusteredServerConfig withNonClusteredServerStartPolicy(
      String nonClusteredServerStartPolicy) {
    this.nonClusteredServerStartPolicy = nonClusteredServerStartPolicy;
    return this;
  }

  @Override
  public NonClusteredServerConfig withServerName(String serverName) {
    super.withServerName(serverName);
    return this;
  }

  @Override
  public NonClusteredServerConfig withStartedServerState(String startedServerState) {
    super.withStartedServerState(startedServerState);
    return this;
  }

  @Override
  public NonClusteredServerConfig withRestartedLabel(String restartedLabel) {
    super.withRestartedLabel(restartedLabel);
    return this;
  }

  @Override
  public NonClusteredServerConfig withNodePort(int nodePort) {
    super.withNodePort(nodePort);
    return this;
  }

  @Override
  public NonClusteredServerConfig withEnv(List<V1EnvVar> env) {
    super.withEnv(env);
    return this;
  }

  @Override
  public NonClusteredServerConfig withImage(String image) {
    super.withImage(image);
    return this;
  }

  @Override
  public NonClusteredServerConfig withImagePullPolicy(String imagePullPolicy) {
    super.withImagePullPolicy(imagePullPolicy);
    return this;
  }

  @Override
  public NonClusteredServerConfig withImagePullSecrets(
      List<V1LocalObjectReference> imagePullSecrets) {
    super.withImagePullSecrets(imagePullSecrets);
    return this;
  }

  @Override
  public NonClusteredServerConfig withShutdownPolicy(String shutdownPolicy) {
    super.withShutdownPolicy(shutdownPolicy);
    return this;
  }

  @Override
  public NonClusteredServerConfig withGracefulShutdownTimeout(int gracefulShutdownTimeout) {
    super.withGracefulShutdownTimeout(gracefulShutdownTimeout);
    return this;
  }

  @Override
  public NonClusteredServerConfig withGracefulShutdownIgnoreSessions(
      boolean gracefulShutdownIgnoreSessions) {
    super.withGracefulShutdownIgnoreSessions(gracefulShutdownIgnoreSessions);
    return this;
  }

  @Override
  public NonClusteredServerConfig withGracefulShutdownWaitForSessions(
      boolean gracefulShutdownWaitForSessions) {
    super.withGracefulShutdownWaitForSessions(gracefulShutdownWaitForSessions);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("nonClusteredServerStartPolicy", nonClusteredServerStartPolicy)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .appendSuper(super.hashCode())
        .append(nonClusteredServerStartPolicy)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof NonClusteredServerConfig) == false) {
      return false;
    }
    NonClusteredServerConfig rhs = ((NonClusteredServerConfig) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(other))
        .append(nonClusteredServerStartPolicy, rhs.nonClusteredServerStartPolicy)
        .isEquals();
  }
}
