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

/** NonClusteredServer describes the desired state of a non-clustered server. */
public class NonClusteredServer extends Server {

  /**
   * Whether this non-clustered server should be started. Legal values are ALWAYS and NEVER.
   *
   * <p>Defaults to ALWAYS.
   */
  @SerializedName("nonClusteredServerStartPolicy")
  @Expose
  private String nonClusteredServerStartPolicy;

  /**
   * Whether this non-clustered server should be started. Legal values are ALWAYS and NEVER.
   *
   * <p>Defaults to ALWAYS.
   *
   * @return non-clustered server start policy
   */
  public String getNonClusteredServerStartPolicy() {
    return nonClusteredServerStartPolicy;
  }

  /**
   * Whether this non-clustered server should be started. Legal values are ALWAYS and NEVER.
   *
   * <p>Defaults to ALWAYS.
   *
   * @param nonClusteredServerStartPolicy non-clustered server start policy
   */
  public void setNonClusteredServerStartPolicy(String nonClusteredServerStartPolicy) {
    this.nonClusteredServerStartPolicy = nonClusteredServerStartPolicy;
  }

  /**
   * Whether this non-clustered server should be started. Legal values are ALWAYS and NEVER.
   *
   * <p>Defaults to ALWAYS.
   *
   * @param nonClusteredServerStartPolicy non-clustered server start policy
   * @return this
   */
  public NonClusteredServer withNonClusteredServerStartPolicy(
      String nonClusteredServerStartPolicy) {
    this.nonClusteredServerStartPolicy = nonClusteredServerStartPolicy;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withStartedServerState(String startedServerState) {
    super.withStartedServerState(startedServerState);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withRestartedLabel(String restartedLabel) {
    super.withRestartedLabel(restartedLabel);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withNodePort(Integer nodePort) {
    super.withNodePort(nodePort);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withEnv(List<V1EnvVar> env) {
    super.withEnv(env);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withImage(String image) {
    super.withImage(image);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withImagePullPolicy(String imagePullPolicy) {
    super.withImagePullPolicy(imagePullPolicy);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    super.withImagePullSecrets(imagePullSecrets);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withShutdownPolicy(String shutdownPolicy) {
    super.withShutdownPolicy(shutdownPolicy);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withGracefulShutdownTimeout(Integer gracefulShutdownTimeout) {
    super.withGracefulShutdownTimeout(gracefulShutdownTimeout);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withGracefulShutdownIgnoreSessions(
      Boolean gracefulShutdownIgnoreSessions) {
    super.withGracefulShutdownIgnoreSessions(gracefulShutdownIgnoreSessions);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServer withGracefulShutdownWaitForSessions(
      Boolean gracefulShutdownWaitForSessions) {
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
    if ((other instanceof NonClusteredServer) == false) {
      return false;
    }
    NonClusteredServer rhs = ((NonClusteredServer) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(other))
        .append(nonClusteredServerStartPolicy, rhs.nonClusteredServerStartPolicy)
        .isEquals();
  }
}
