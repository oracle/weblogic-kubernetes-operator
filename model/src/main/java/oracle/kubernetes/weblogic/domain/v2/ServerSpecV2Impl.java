// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_IF_NEEDED;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_NEVER;

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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/** The effective configuration for a server configured by the version 2 domain model. */
public abstract class ServerSpecV2Impl extends ServerSpec {
  private final Server server;
  private Integer clusterLimit;
  private RestartVersion effectiveRestartVersion = new RestartVersion();

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
    for (BaseConfiguration configuration : configurations) {
      this.server.fillInFrom(configuration);
      addEfectiveRestartVersion(configuration);
    }
    addEfectiveRestartVersion(server);
  }

  private void addEfectiveRestartVersion(BaseConfiguration configuration) {
    if (configuration == null) return;
    configuration.addEffectiveRestartVersion(effectiveRestartVersion);
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
  public Map<String, String> getPodLabels() {
    Map<String, String> serverPodLabels = server.getPodLabels();
    serverPodLabels.put(
        ConfigurationConstants.LABEL_RESTART_VERSION, effectiveRestartVersion.toString());
    return serverPodLabels;
  }

  @Override
  public Map<String, String> getPodAnnotations() {
    return server.getPodAnnotations();
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
  public String getRestartVersion() {
    return effectiveRestartVersion.toString();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("server", server)
        .append("clusterLimit", clusterLimit)
        .append("effectiveRestartVersion", effectiveRestartVersion.toString())
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
        .append(effectiveRestartVersion, that.effectiveRestartVersion)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(server)
        .append(clusterLimit)
        .append(effectiveRestartVersion)
        .toHashCode();
  }

  public class RestartVersion {
    private Integer domainRestartVersion = 0;
    private Integer clusterRestartVersion = 0;
    private Integer serverRestartVersion = 0;

    public RestartVersion() {
      super();
    }

    public Integer getDomainRestartVersion() {
      return domainRestartVersion;
    }

    public RestartVersion addDomainRestartVersion(Integer domainRestartVersion) {
      this.domainRestartVersion = domainRestartVersion != null ? domainRestartVersion : 0;
      return this;
    }

    public Integer getClusterRestartVersion() {
      return clusterRestartVersion;
    }

    public RestartVersion addClusterRestartVersion(Integer clusterRestartVersion) {
      this.clusterRestartVersion = clusterRestartVersion != null ? clusterRestartVersion : 0;
      return this;
    }

    public Integer getServerRestartVersion() {
      return serverRestartVersion;
    }

    public RestartVersion addServerRestartVersion(Integer serverRestartVersion) {
      this.serverRestartVersion = serverRestartVersion != null ? serverRestartVersion : 0;
      return this;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this, ToStringStyle.SIMPLE_STYLE)
          .append("domainRestartVersion", domainRestartVersion)
          .append("clusterRestartVersion", clusterRestartVersion)
          .append("serverRestartVersion", serverRestartVersion)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37)
          .append(domainRestartVersion)
          .append(clusterRestartVersion)
          .append(serverRestartVersion)
          .toHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;

      if (o == null || getClass() != o.getClass()) return false;

      if (!(o instanceof RestartVersion)) return false;

      RestartVersion that = (RestartVersion) o;

      return new EqualsBuilder()
          .append(domainRestartVersion, that.domainRestartVersion)
          .append(clusterRestartVersion, that.clusterRestartVersion)
          .append(serverRestartVersion, that.serverRestartVersion)
          .isEquals();
    }
  }
}
