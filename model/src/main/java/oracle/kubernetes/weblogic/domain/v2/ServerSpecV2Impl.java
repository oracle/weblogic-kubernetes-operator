// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_IF_NEEDED;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_NEVER;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** The effective configuration for a server configured by the version 2 domain model. */
public abstract class ServerSpecV2Impl extends ServerSpec {
  private final Server server;
  private Integer clusterLimit;

  /**
   * Constructs an object to return the effective configuration
   *
   * @param server the server whose configuration is to be returned
   * @param clusterLimit the number of servers desired for the cluster, or null if not a clustered
   *     server
   * @param configurations the additional configurations to search for values if the server lacks
   *     them
   */
  ServerSpecV2Impl(
      DomainSpec spec, Server server, Integer clusterLimit, BaseConfiguration... configurations) {
    super(spec);
    this.server = getBaseConfiguration(server);
    this.clusterLimit = clusterLimit;
    for (BaseConfiguration configuration : configurations) this.server.fillInFrom(configuration);
  }

  private Server getBaseConfiguration(Server server) {
    return server != null ? server.getConfiguration() : new Server();
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
    return withStateAdjustments(server.getEnv());
  }

  @Override
  public List<V1Volume> getAdditionalVolumes() {
    return server.getAdditionalVolumes();
  }

  @Override
  public List<V1VolumeMount> getAdditionalVolumeMounts() {
    return server.getAdditionalVolumeMounts();
  }

  @Override
  public String getDesiredState() {
    return Optional.ofNullable(getConfiguredDesiredState()).orElse("RUNNING");
  }

  private String getConfiguredDesiredState() {
    return server.getServerStartState();
  }

  @Override
  public Integer getNodePort() {
    return server.getNodePort();
  }

  @Override
  public boolean shouldStart(int currentReplicas) {
    switch (getEffectiveServerStartPolicy()) {
      case START_NEVER:
        return false;
      case START_ALWAYS:
        return true;
      case START_IF_NEEDED:
        return clusterLimit == null || currentReplicas < clusterLimit;
      default:
        return clusterLimit == null;
    }
  }

  boolean isStartAdminServerOnly() {
    return domainSpec.isStartAdminServerOnly();
  }

  private String getEffectiveServerStartPolicy() {
    return Optional.ofNullable(server.getServerStartPolicy()).orElse("undefined");
  }

  @Nonnull
  @Override
  public ProbeTuning getLivenessProbe() {
    return server.getLivenessProbe();
  }

  @Nonnull
  @Override
  public ProbeTuning getReadinessProbe() {
    return server.getReadinessProbe();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("server", server)
        .append("clusterLimit", clusterLimit)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    if (!(o instanceof ServerSpecV2Impl)) return false;

    ServerSpecV2Impl that = (ServerSpecV2Impl) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(server, that.server)
        .append(clusterLimit, that.clusterLimit)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(server)
        .append(clusterLimit)
        .toHashCode();
  }
}
