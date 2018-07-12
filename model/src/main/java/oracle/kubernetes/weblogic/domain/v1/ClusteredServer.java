// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ClusteredServer describes the desired state of a clustered server. */
public class ClusteredServer extends Server {

  /**
   * Whether this clustered server should be started. Legal values are ALWAYS, IF_NEEDED and NEVER.
   *
   * <p>Defaults to IF_NEEDED.
   */
  @SerializedName("clusteredServerStartPolicy")
  @Expose
  private String clusteredServerStartPolicy;

  /**
   * Whether this clustered server should be started. Legal values are ALWAYS, IF_NEEDED and NEVER.
   *
   * <p>Defaults to IF_NEEDED.
   *
   * @return clustered server start policy
   */
  public String getClusteredServerStartPolicy() {
    return clusteredServerStartPolicy;
  }

  /**
   * Whether this clustered server should be started. Legal values are ALWAYS, IF_NEEDED and NEVER.
   *
   * <p>Defaults to IF_NEEDED.
   *
   * @param clusteredServerStartPolicy clustered server start policy
   */
  public void setClusteredServerStartPolicy(String clusteredServerStartPolicy) {
    this.clusteredServerStartPolicy = clusteredServerStartPolicy;
  }

  /**
   * Whether this clustered server should be started. Legal values are ALWAYS, IF_NEEDED and NEVER.
   *
   * <p>Defaults to IF_NEEDED.
   *
   * @param clusteredServerStartPolicy clustered server start policy
   * @return this
   */
  public ClusteredServer withClusteredServerStartPolicy(String clusteredServerStartPolicy) {
    this.clusteredServerStartPolicy = clusteredServerStartPolicy;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withStartedServerState(String startedServerState) {
    super.withStartedServerState(startedServerState);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withRestartedLabel(String restartedLabel) {
    super.withRestartedLabel(restartedLabel);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withNodePort(Integer nodePort) {
    super.withNodePort(nodePort);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withEnv(List<V1EnvVar> env) {
    super.withEnv(env);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withImage(String image) {
    super.withImage(image);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withImagePullPolicy(String imagePullPolicy) {
    super.withImagePullPolicy(imagePullPolicy);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    super.withImagePullSecrets(imagePullSecrets);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withShutdownPolicy(String shutdownPolicy) {
    super.withShutdownPolicy(shutdownPolicy);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withGracefulShutdownTimeout(Integer gracefulShutdownTimeout) {
    super.withGracefulShutdownTimeout(gracefulShutdownTimeout);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withGracefulShutdownIgnoreSessions(
      Boolean gracefulShutdownIgnoreSessions) {
    super.withGracefulShutdownIgnoreSessions(gracefulShutdownIgnoreSessions);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServer withGracefulShutdownWaitForSessions(
      Boolean gracefulShutdownWaitForSessions) {
    super.withGracefulShutdownWaitForSessions(gracefulShutdownWaitForSessions);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("clusteredServerStartPolicy", clusteredServerStartPolicy)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .appendSuper(super.hashCode())
        .append(clusteredServerStartPolicy)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof ClusteredServer) == false) {
      return false;
    }
    ClusteredServer rhs = ((ClusteredServer) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(other))
        .append(clusteredServerStartPolicy, rhs.clusteredServerStartPolicy)
        .isEquals();
  }
}
