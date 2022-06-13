// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.ServerStartState;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;

/** The effective configuration for a server configured by the version 2 domain model. */
public abstract class ServerSpecCommonImpl extends ServerSpecBase {
  private final Server server;
  private final Cluster cluster;
  private final Integer clusterLimit;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param server the server whose configuration is to be returned
   * @param cluster the cluster to which the server belongs
   * @param clusterLimit the number of servers desired for the cluster, or null if not a clustered
   *     server
   */
  ServerSpecCommonImpl(DomainSpec spec, Server server, Cluster cluster, Integer clusterLimit) {
    super(spec);
    this.server = getBaseConfiguration(server);
    this.clusterLimit = clusterLimit;
    this.server.fillInFrom(cluster);
    this.server.fillInFrom(spec);
    this.cluster = cluster;

    if (isClusteredServerWithtoutAffinityPolicy(cluster)) {
      addDefaultAntiAffinityPolicy();
    }
  }

  private boolean isClusteredServerWithtoutAffinityPolicy(Cluster cluster) {
    return cluster != null && cluster.getAffinity() == null;
  }

  void addDefaultAntiAffinityPolicy() {
    List<V1WeightedPodAffinityTerm> existingAffinityTerms =
        Optional.ofNullable(this.server.getAffinity())
        .map(a -> a.getPodAntiAffinity())
        .map(aa -> aa.getPreferredDuringSchedulingIgnoredDuringExecution()).orElse(new ArrayList<>());
    List<V1WeightedPodAffinityTerm> newAffinityTerms = new ArrayList<>();
    if (existingAffinityTerms.isEmpty()) {
      addDefaultAntiAffinityPolicy(newAffinityTerms);
    } else if (!existingAffinityTerms.contains(getPodAntiAffinityTerm())) {
      newAffinityTerms.addAll(existingAffinityTerms);
      addDefaultAntiAffinityPolicy(newAffinityTerms);
    }
  }

  private void addDefaultAntiAffinityPolicy(List<V1WeightedPodAffinityTerm> newAffinityTerms) {
    newAffinityTerms.add(getPodAntiAffinityTerm());
    this.server.setAffinity(Optional.ofNullable(this.server.getAffinity()).orElse(new V1Affinity())
        .podAntiAffinity(new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(newAffinityTerms)));
  }

  private V1WeightedPodAffinityTerm getPodAntiAffinityTerm() {
    return new V1WeightedPodAffinityTerm()
        .weight(100)
        .podAffinityTerm(
            new V1PodAffinityTerm()
                .labelSelector(
                    new V1LabelSelector()
                        .addMatchExpressionsItem(
                            new V1LabelSelectorRequirement().key(CLUSTERNAME_LABEL).operator("In")
                                .addValuesItem("$(CLUSTER_NAME)")
                        )
                )
                .topologyKey("kubernetes.io/hostname")
        );
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
  public String getDesiredState() {
    return getEffectiveServerStartPolicy().equals(ServerStartPolicy.NEVER) ? SHUTDOWN_STATE
            : Optional.ofNullable(getConfiguredDesiredState()).orElse(ServerStartState.RUNNING).toString();
  }

  private ServerStartState getConfiguredDesiredState() {
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
  public V1PodSpec.RestartPolicyEnum getRestartPolicy() {
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
    return cluster != null ? cluster.getRestartVersion() : null;
  }

  @Override
  public String getServerRestartVersion() {
    return server.getRestartVersion();
  }

  @Override
  public long getMaximumReadyWaitTimeSeconds() {
    return server.getMaximumReadyWaitTimeSeconds();
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

    if (!(o instanceof ServerSpecCommonImpl)) {
      return false;
    }

    ServerSpecCommonImpl that = (ServerSpecCommonImpl) o;

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
