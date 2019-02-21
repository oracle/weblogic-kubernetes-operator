// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1SecurityContext;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.ServerStartPolicy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** The effective configuration for a server configured by the version 2 domain model. */
public abstract class ServerSpecV2Impl extends ServerSpec {
  private final Server server;
  private final Cluster cluster;
  private Integer clusterLimit;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param server the server whose configuration is to be returned
   * @param cluster the cluster to which the server belongs
   * @param clusterLimit the number of servers desired for the cluster, or null if not a clustered
   *     server
   */
  ServerSpecV2Impl(DomainSpec spec, Server server, Cluster cluster, Integer clusterLimit) {
    super(spec);
    this.server = getBaseConfiguration(server);
    this.clusterLimit = clusterLimit;
    this.server.fillInFrom(cluster);
    this.server.fillInFrom(spec);
    this.cluster = cluster;
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
  @Nonnull
  public Map<String, String> getPodLabels() {
    return server.getPodLabels();
  }

  @Override
  @Nonnull
  public Map<String, String> getPodAnnotations() {
    return server.getPodAnnotations();
  }

  @Override
  public Map<String, String> getServiceLabels() {
    return server.getServiceLabels();
  }

  @Override
  public Map<String, String> getServiceAnnotations() {
    return server.getServiceAnnotations();
  }

  @Override
  public String getDesiredState() {
    return Optional.ofNullable(getConfiguredDesiredState()).orElse("RUNNING");
  }

  private String getConfiguredDesiredState() {
    return server.getServerStartState();
  }

  @Override
  public boolean shouldStart(int currentReplicas) {
    switch (getEffectiveServerStartPolicy()) {
      case ALWAYS:
        return true;
      case IF_NEEDED:
        return clusterLimit == null || currentReplicas < clusterLimit;
      default:
        return false;
    }
  }

  boolean isStartAdminServerOnly() {
    return domainSpec.isStartAdminServerOnly();
  }

  private ServerStartPolicy getEffectiveServerStartPolicy() {
    return Optional.ofNullable(server.getServerStartPolicy())
        .map(ServerStartPolicy::valueOf)
        .orElse(ServerStartPolicy.getDefaultPolicy());
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

  @Nonnull
  @Override
  public Map<String, String> getNodeSelectors() {
    return server.getNodeSelector();
  }

  @Override
  public V1ResourceRequirements getResources() {
    return server.getResources();
  }

  @Override
  public V1PodSecurityContext getPodSecurityContext() {
    return server.getPodSecurityContext();
  }

  @Override
  public V1SecurityContext getContainerSecurityContext() {
    return server.getContainerSecurityContext();
  }

  @Override
  public String getDomainRestartVersion() {
    return domainSpec.getRestartVersion();
  }

  @Override
  public String getClusterRestartVersion() {
    return cluster != null ? cluster.getRestartVersion() : null;
  }

  @Override
  public String getServerRestartVersion() {
    return server.getRestartVersion();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("server", server)
        .append("clusterLimit", clusterLimit)
        .append("cluster", cluster)
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

    if (!(o instanceof ServerSpecV2Impl)) {
      return false;
    }

    ServerSpecV2Impl that = (ServerSpecV2Impl) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(server, that.server)
        .append(clusterLimit, that.clusterLimit)
        .append(cluster, that.cluster)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(server)
        .append(clusterLimit)
        .append(cluster)
        .toHashCode();
  }
}
