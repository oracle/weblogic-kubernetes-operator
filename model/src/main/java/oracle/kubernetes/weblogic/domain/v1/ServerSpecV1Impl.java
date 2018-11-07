// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.StartupControlConstants.ALL_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.SPECIFIED_STARTUPCONTROL;

import io.kubernetes.client.models.V1EnvVar;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** The effective configuration for a server configured by the version 1 domain model. */
public class ServerSpecV1Impl extends ServerSpec {
  private final String clusterName;

  @SuppressWarnings("deprecation")
  private ServerStartup serverStartup;

  @SuppressWarnings("deprecation")
  private ClusterStartup clusterStartup;

  @SuppressWarnings("deprecation")
  ServerSpecV1Impl(
      DomainSpec domainSpec,
      String clusterName,
      ServerStartup serverStartup,
      ClusterStartup clusterStartup) {
    super(domainSpec);
    this.clusterName = clusterName;
    this.serverStartup = serverStartup;
    this.clusterStartup = clusterStartup;
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
    List<V1EnvVar> vars =
        serverStartup != null
            ? serverStartup.getEnv()
            : clusterStartup != null ? clusterStartup.getEnv() : Collections.emptyList();
    return withStateAdjustments(vars);
  }

  @Override
  public String getDesiredState() {
    return Optional.ofNullable(getConfiguredDesiredState()).orElse("RUNNING");
  }

  @Override
  public Integer getNodePort() {
    return serverStartup == null ? null : serverStartup.getNodePort();
  }

  private String getConfiguredDesiredState() {
    if (serverStartup != null) return serverStartup.getDesiredState();
    return clusterStartup == null ? null : clusterStartup.getDesiredState();
  }

  @Override
  public boolean shouldStart(int currentReplicas) {
    switch (domainSpec.getEffectiveStartupControl()) {
      case ALL_STARTUPCONTROL:
        return true;
      case AUTO_STARTUPCONTROL:
        if (clusterName != null) return currentReplicas < getReplicaCount();
      case SPECIFIED_STARTUPCONTROL:
        return isSpecified() && currentReplicas < getReplicaCount();
      default:
        return false;
    }
  }

  private int getReplicaCount() {
    return domainSpec.getReplicaCount(clusterStartup);
  }

  private boolean isSpecified() {
    return serverStartup != null || clusterStartup != null;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("clusterName", clusterName)
        .append("serverStartup", serverStartup)
        .append("clusterStartup", clusterStartup)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .appendSuper(super.hashCode())
        .append(clusterName)
        .append(serverStartup)
        .append(clusterStartup)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof ServerSpecV1Impl) == false) {
      return false;
    }
    ServerSpecV1Impl rhs = ((ServerSpecV1Impl) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(rhs))
        .append(clusterName, rhs.clusterName)
        .append(serverStartup, rhs.serverStartup)
        .append(clusterStartup, rhs.clusterStartup)
        .isEquals();
  }
}
