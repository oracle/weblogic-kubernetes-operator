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

/** ClusteredServerConfig describes the desired state of a clustered server. */
public class ClusteredServerConfig extends ServerConfig {

  public static final String CLUSTERED_SERVER_START_POLICY_ALWAYS = SERVER_START_POLICY_ALWAYS;
  public static final String CLUSTERED_SERVER_START_POLICY_NEVER = SERVER_START_POLICY_NEVER;
  public static final String CLUSTERED_SERVER_START_POLICY_IF_NEEDED = "IF_NEEDED";

  private String clusteredServerStartPolicy;
  private String clusterName;

  /**
   * Gets cluster's name.
   *
   * @return cluster's name
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * Sets the cluster's name.
   *
   * @param clusterName the cluster's name.
   */
  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  /**
   * Sets cluster's name.
   *
   * @param clusterName the cluster's name.
   * @return this
   */
  public ClusteredServerConfig withClusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  /**
   * Gets whether this clustered server should be started. Legal values are ALWAYS, IF_NEEDED and
   * NEVER.
   *
   * @return clustered server start policy
   */
  public String getClusteredServerStartPolicy() {
    return clusteredServerStartPolicy;
  }

  /**
   * Sets whether this clustered server should be started.
   *
   * @param clusteredServerStartPolicy clustered server start policy
   */
  public void setClusteredServerStartPolicy(String clusteredServerStartPolicy) {
    this.clusteredServerStartPolicy = clusteredServerStartPolicy;
  }

  /**
   * Sets whether this clustered server should be started.
   *
   * @param clusteredServerStartPolicy clustered server start policy
   * @return this
   */
  public ClusteredServerConfig withClusteredServerStartPolicy(String clusteredServerStartPolicy) {
    this.clusteredServerStartPolicy = clusteredServerStartPolicy;
    return this;
  }

  @Override
  public ClusteredServerConfig withServerName(String serverName) {
    super.withServerName(serverName);
    return this;
  }

  @Override
  public ClusteredServerConfig withStartedServerState(String startedServerState) {
    super.withStartedServerState(startedServerState);
    return this;
  }

  @Override
  public ClusteredServerConfig withRestartedLabel(String restartedLabel) {
    super.withRestartedLabel(restartedLabel);
    return this;
  }

  @Override
  public ClusteredServerConfig withNodePort(int nodePort) {
    super.withNodePort(nodePort);
    return this;
  }

  @Override
  public ClusteredServerConfig withEnv(List<V1EnvVar> env) {
    super.withEnv(env);
    return this;
  }

  @Override
  public ClusteredServerConfig withImage(String image) {
    super.withImage(image);
    return this;
  }

  @Override
  public ClusteredServerConfig withImagePullPolicy(String imagePullPolicy) {
    super.withImagePullPolicy(imagePullPolicy);
    return this;
  }

  @Override
  public ClusteredServerConfig withImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    super.withImagePullSecrets(imagePullSecrets);
    return this;
  }

  @Override
  public ClusteredServerConfig withShutdownPolicy(String shutdownPolicy) {
    super.withShutdownPolicy(shutdownPolicy);
    return this;
  }

  @Override
  public ClusteredServerConfig withGracefulShutdownTimeout(int gracefulShutdownTimeout) {
    super.withGracefulShutdownTimeout(gracefulShutdownTimeout);
    return this;
  }

  @Override
  public ClusteredServerConfig withGracefulShutdownIgnoreSessions(
      boolean gracefulShutdownIgnoreSessions) {
    super.withGracefulShutdownIgnoreSessions(gracefulShutdownIgnoreSessions);
    return this;
  }

  @Override
  public ClusteredServerConfig withGracefulShutdownWaitForSessions(
      boolean gracefulShutdownWaitForSessions) {
    super.withGracefulShutdownWaitForSessions(gracefulShutdownWaitForSessions);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("clusterName", clusterName)
        .append("clusteredServerStartPolicy", clusteredServerStartPolicy)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .appendSuper(super.hashCode())
        .append(clusterName)
        .append(clusteredServerStartPolicy)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof ClusteredServerConfig) == false) {
      return false;
    }
    ClusteredServerConfig rhs = ((ClusteredServerConfig) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(other))
        .append(clusterName, rhs.clusterName)
        .append(clusteredServerStartPolicy, rhs.clusteredServerStartPolicy)
        .isEquals();
  }
}
