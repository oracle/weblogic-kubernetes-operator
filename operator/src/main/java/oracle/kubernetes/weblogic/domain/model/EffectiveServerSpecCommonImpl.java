// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.processing.EffectiveServerSpecBase;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;

/** The effective configuration for a server configured by the version 2 domain model. */
public abstract class EffectiveServerSpecCommonImpl extends EffectiveServerSpecBase {
  private final Server server;
  private final ClusterSpec clusterSpec;
  private final Integer clusterLimit;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param server the server whose configuration is to be returned
   * @param clusterSpec the cluster to which the server belongs
   * @param clusterLimit the number of servers desired for the cluster, or null if not a clustered
   *     server
   */
  EffectiveServerSpecCommonImpl(DomainSpec spec, Server server, ClusterSpec clusterSpec, Integer clusterLimit) {
    super(spec);
    this.server = getBaseConfiguration(server);
    this.clusterLimit = clusterLimit;
    this.server.fillInFrom(clusterSpec);
    this.server.fillInFrom(spec);
    this.clusterSpec = clusterSpec;
  }

  private Server getBaseConfiguration(Server server) {
    return server != null ? server.getConfiguration() : new Server();
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
    return server.getEnv();
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

  public Boolean isPrecreateServerService() {
    return server.isPrecreateServerService();
  }

  @Override
  @Nonnull
  public Map<String, String> getServiceLabels() {
    return server.getServiceLabels();
  }

  @Override
  @Nonnull
  public Map<String, String> getServiceAnnotations() {
    return server.getServiceAnnotations();
  }

  @Override
  @Nonnull
  public List<V1Container> getContainers() {
    return server.getContainers();
  }

  @Override
  @Nonnull
  public List<V1Container> getInitContainers() {
    return server.getInitContainers();
  }

  @Override
  public String getStateGoal() {
    return getEffectiveServerStartPolicy().equals(ServerStartPolicy.NEVER) ? SHUTDOWN_STATE : RUNNING_STATE;
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

  @Override
  public boolean isShuttingDown() {
    return !shouldStart(0);
  }

  boolean isStartAdminServerOnly() {
    return domainSpec.isStartAdminServerOnly();
  }

  private ServerStartPolicy getEffectiveServerStartPolicy() {
    return Optional.ofNullable(server.getServerStartPolicy())
        .orElse(ServerStartPolicy.getDefaultPolicy());
  }

  public boolean alwaysStart() {
    return ServerStartPolicy.ALWAYS.equals(getEffectiveServerStartPolicy());
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
  public Shutdown getShutdown() {
    return server.getShutdown();
  }

  @Nonnull
  @Override
  public Map<String, String> getNodeSelectors() {
    return server.getNodeSelector();
  }

  @Override
  public V1Affinity getAffinity() {
    return server.getAffinity();
  }

  @Override
  public String getPriorityClassName() {
    return server.getPriorityClassName();
  }

  @Override
  public List<V1PodReadinessGate> getReadinessGates() {
    return server.getReadinessGates();
  }

  @Override
  public String getRestartPolicy() {
    return server.getRestartPolicy();
  }

  @Override
  public String getRuntimeClassName() {
    return server.getRuntimeClassName();
  }

  @Override
  public String getNodeName() {
    return server.getNodeName();
  }

  @Override
  public String getServiceAccountName() {
    return server.getServiceAccountName();
  }

  @Override
  public String getSchedulerName() {
    return server.getSchedulerName();
  }

  @Override
  public List<V1Toleration> getTolerations() {
    return server.getTolerations();
  }

  @Override
  public List<V1HostAlias> getHostAliases() {
    return server.getHostAliases();
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
    return clusterSpec != null ? clusterSpec.getRestartVersion() : null;
  }

  @Override
  public String getServerRestartVersion() {
    return server.getRestartVersion();
  }

  @Override
  public Long getMaximumReadyWaitTimeSeconds() {
    return server.getMaximumReadyWaitTimeSeconds();
  }

  @Override
  public Long getMaximumPendingWaitTimeSeconds() {
    return server.getMaximumPendingWaitTimeSeconds();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("server", server)
        .append("clusterLimit", clusterLimit)
        .append("cluster", clusterSpec)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof EffectiveServerSpecCommonImpl)) {
      return false;
    }

    EffectiveServerSpecCommonImpl that = (EffectiveServerSpecCommonImpl) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(server, that.server)
        .append(clusterLimit, that.clusterLimit)
        .append(clusterSpec, that.clusterSpec)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(server)
        .append(clusterLimit)
        .append(clusterSpec)
        .toHashCode();
  }
}
