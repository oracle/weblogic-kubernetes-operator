// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.VersionConstants.DOMAIN_V2;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_NEVER;

import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1SecurityContext;
import java.util.Arrays;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;

public class DomainCommonConfigurator extends DomainConfigurator {

  @Override
  public DomainConfigurator createFor(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }

  public DomainCommonConfigurator() {}

  DomainCommonConfigurator(@Nonnull Domain domain) {
    super(domain);
    setApiVersion(domain);
  }

  private void setApiVersion(Domain domain) {
    domain.getMetadata().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V2);
    domain.setApiVersion(
        KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION);
  }

  @Override
  public AdminServerConfigurator configureAdminServer() {
    return new AdminServerConfiguratorImpl(getOrCreateAdminServer());
  }

  @Override
  public void withDefaultReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period) {
    ((BaseConfiguration) getDomainSpec()).setReadinessProbe(initialDelay, timeout, period);
  }

  @Override
  public void withDefaultLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period) {
    ((BaseConfiguration) getDomainSpec()).setLivenessProbe(initialDelay, timeout, period);
  }

  @Override
  public DomainConfigurator withDefaultServerStartPolicy(String startPolicy) {
    ((BaseConfiguration) getDomainSpec()).setServerStartPolicy(startPolicy);
    return this;
  }

  @Override
  public DomainConfigurator withEnvironmentVariable(String name, String value) {
    ((BaseConfiguration) getDomainSpec()).addEnvironmentVariable(name, value);
    return this;
  }

  @Override
  public DomainConfigurator withAdditionalVolume(String name, String path) {
    ((BaseConfiguration) getDomainSpec()).addAdditionalVolume(name, path);
    return this;
  }

  @Override
  public DomainConfigurator withAdditionalVolumeMount(String name, String path) {
    ((BaseConfiguration) getDomainSpec()).addAdditionalVolumeMount(name, path);
    return this;
  }

  @Override
  public DomainConfigurator withInitContainer(V1Container initContainer) {
    ((BaseConfiguration) getDomainSpec()).addInitContainer(initContainer);
    return this;
  }

  @Override
  public DomainConfigurator withContainer(V1Container container) {
    ((BaseConfiguration) getDomainSpec()).addContainer(container);
    return this;
  }

  @Override
  /**
   * Sets the WebLogic configuration overrides config map name for the domain
   *
   * @param configMapName Name of the Kubernetes config map that contains the configuration
   *     overrides
   * @return this object
   */
  public DomainConfigurator withConfigOverrides(String configMapName) {
    getDomainSpec().setConfigOverrides(configMapName);
    return this;
  }

  @Override
  public DomainConfigurator withConfigOverrideSecrets(String... secretNames) {
    getDomainSpec().setConfigOverrideSecrets(Arrays.asList(secretNames));
    return this;
  }

  @Override
  public DomainConfigurator withPodLabel(String name, String value) {
    getDomainSpec().addPodLabel(name, value);
    return this;
  }

  @Override
  public DomainConfigurator withPodAnnotation(String name, String value) {
    getDomainSpec().addPodAnnotation(name, value);
    return this;
  }

  class AdminServerConfiguratorImpl extends ServerConfiguratorImpl
      implements AdminServerConfigurator {
    private AdminServer adminServer;

    AdminServerConfiguratorImpl(AdminServer adminServer) {
      super(adminServer);
      this.adminServer = adminServer;
    }

    @Override
    public AdminService configureAdminService() {
      return adminServer.getAdminService();
    }
  }

  private AdminServer getOrCreateAdminServer() {
    return getDomainSpec().getOrCreateAdminServer();
  }

  @Override
  public ServerConfigurator configureServer(@Nonnull String serverName) {
    return new ServerConfiguratorImpl(getOrCreateManagedServer(serverName));
  }

  private Server getOrCreateManagedServer(@Nonnull String serverName) {
    ManagedServer server = getDomainSpec().getManagedServer(serverName);
    if (server != null) {
      return server;
    }

    return createManagedServer(serverName);
  }

  private Server createManagedServer(String serverName) {
    ManagedServer server = new ManagedServer().withServerName(serverName);
    getDomainSpec().getManagedServers().add(server);
    return server;
  }

  @Override
  public DomainConfigurator withNodeSelector(String labelKey, String labelValue) {
    ((BaseConfiguration) getDomainSpec()).addNodeSelector(labelKey, labelValue);
    return this;
  }

  @Override
  public DomainConfigurator withRequestRequirement(String resource, String quantity) {
    ((BaseConfiguration) getDomainSpec()).addRequestRequirement(resource, quantity);
    return this;
  }

  @Override
  public DomainConfigurator withLimitRequirement(String resource, String quantity) {
    ((BaseConfiguration) getDomainSpec()).addLimitRequirement(resource, quantity);
    return this;
  }

  @Override
  public DomainConfigurator withContainerSecurityContext(
      V1SecurityContext containerSecurityContext) {
    ((BaseConfiguration) getDomainSpec()).setContainerSecurityContext(containerSecurityContext);
    return this;
  }

  @Override
  public DomainConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    ((BaseConfiguration) getDomainSpec()).setPodSecurityContext(podSecurityContext);
    return this;
  }

  @Override
  public DomainConfigurator withRestartVersion(String restartVersion) {
    ((BaseConfiguration) getDomainSpec()).setRestartVersion(restartVersion);
    return this;
  }

  class ServerConfiguratorImpl implements ServerConfigurator {
    private Server server;

    ServerConfiguratorImpl(Server server) {
      this.server = server;
    }

    @Override
    public ServerConfigurator withDesiredState(String desiredState) {
      server.setServerStartState(desiredState);
      return this;
    }

    @Override
    public ServerConfigurator withEnvironmentVariable(String name, String value) {
      server.addEnvironmentVariable(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withServerStartState(String state) {
      return withDesiredState(state);
    }

    @Override
    public ServerConfigurator withServerStartPolicy(String policy) {
      server.setServerStartPolicy(policy);
      return this;
    }

    @Override
    public ServerConfigurator withLivenessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      server.setLivenessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ServerConfigurator withReadinessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      server.setReadinessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ServerConfigurator withRequestRequirement(String resource, String quantity) {
      server.addRequestRequirement(resource, quantity);
      return this;
    }

    @Override
    public ServerConfigurator withNodeSelector(String labelKey, String labelValue) {
      server.addNodeSelector(labelKey, labelValue);
      return this;
    }

    @Override
    public ServerConfigurator withLimitRequirement(String resource, String quantity) {
      server.addLimitRequirement(resource, quantity);
      return this;
    }

    @Override
    public ServerConfigurator withContainerSecurityContext(
        V1SecurityContext containerSecurityContext) {
      server.setContainerSecurityContext(containerSecurityContext);
      return this;
    }

    @Override
    public ServerConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
      server.setPodSecurityContext(podSecurityContext);
      return this;
    }

    @Override
    public ServerConfigurator withAdditionalVolume(String name, String path) {
      server.addAdditionalVolume(name, path);
      return this;
    }

    @Override
    public ServerConfigurator withAdditionalVolumeMount(String name, String path) {
      server.addAdditionalVolumeMount(name, path);
      return this;
    }

    @Override
    public ServerConfigurator withInitContainer(V1Container initContainer) {
      server.addInitContainer(initContainer);
      return this;
    }

    @Override
    public ServerConfigurator withContainer(V1Container container) {
      server.addContainer(container);
      return this;
    }

    @Override
    public ServerConfigurator withPodLabel(String name, String value) {
      server.addPodLabel(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withPodAnnotation(String name, String value) {
      server.addPodAnnotation(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withServiceLabel(String name, String value) {
      server.addServiceLabel(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withServiceAnnotation(String name, String value) {
      server.addServiceAnnotation(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withRestartVersion(String restartVersion) {
      server.setRestartVersion(restartVersion);
      return this;
    }
  }

  @Override
  public ClusterConfigurator configureCluster(@Nonnull String clusterName) {
    return new ClusterConfiguratorImpl(getOrCreateCluster(clusterName));
  }

  private Cluster getOrCreateCluster(@Nonnull String clusterName) {
    Cluster cluster = getDomainSpec().getCluster(clusterName);
    if (cluster != null) {
      return cluster;
    }

    return createCluster(clusterName);
  }

  private Cluster createCluster(@Nonnull String clusterName) {
    Cluster cluster = new Cluster().withClusterName(clusterName);
    getDomainSpec().getClusters().add(cluster);
    return cluster;
  }

  @Override
  public void setShuttingDown(boolean shuttingDown) {
    configureAdminServer().withServerStartPolicy(shuttingDown ? START_NEVER : START_ALWAYS);
  }

  class ClusterConfiguratorImpl implements ClusterConfigurator {
    private Cluster cluster;

    ClusterConfiguratorImpl(Cluster cluster) {
      this.cluster = cluster;
    }

    @Override
    public ClusterConfigurator withReplicas(int replicas) {
      cluster.setReplicas(replicas);
      return this;
    }

    @Override
    public ClusterConfigurator withMaxUnavailable(int maxUnavailable) {
      cluster.setMaxUnavailable(maxUnavailable);
      return this;
    }

    @Override
    public ClusterConfigurator withDesiredState(String state) {
      cluster.setServerStartState(state);
      return this;
    }

    @Override
    public ClusterConfigurator withEnvironmentVariable(String name, String value) {
      cluster.addEnvironmentVariable(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServerStartState(String state) {
      return withDesiredState(state);
    }

    @Override
    public ClusterConfigurator withServerStartPolicy(String policy) {
      cluster.setServerStartPolicy(policy);
      return this;
    }

    @Override
    public ClusterConfigurator withReadinessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      cluster.setReadinessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ClusterConfigurator withLivenessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      cluster.setLivenessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ClusterConfigurator withNodeSelector(String labelKey, String labelValue) {
      cluster.addNodeSelector(labelKey, labelValue);
      return this;
    }

    @Override
    public ClusterConfigurator withRequestRequirement(String resource, String quantity) {
      cluster.addRequestRequirement(resource, quantity);
      return this;
    }

    @Override
    public ClusterConfigurator withLimitRequirement(String resource, String quantity) {
      cluster.addLimitRequirement(resource, quantity);
      return this;
    }

    @Override
    public ClusterConfigurator withContainerSecurityContext(
        V1SecurityContext containerSecurityContext) {
      cluster.setContainerSecurityContext(containerSecurityContext);
      return this;
    }

    @Override
    public ClusterConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
      cluster.setPodSecurityContext(podSecurityContext);
      return this;
    }

    @Override
    public ClusterConfigurator withAdditionalVolume(String name, String path) {
      cluster.addAdditionalVolume(name, path);
      return this;
    }

    @Override
    public ClusterConfigurator withAdditionalVolumeMount(String name, String path) {
      cluster.addAdditionalVolumeMount(name, path);
      return this;
    }

    @Override
    public ClusterConfigurator withInitContainer(V1Container initContainer) {
      cluster.addInitContainer(initContainer);
      return this;
    }

    @Override
    public ClusterConfigurator withContainer(V1Container container) {
      cluster.addContainer(container);
      return this;
    }

    @Override
    public ClusterConfigurator withPodLabel(String name, String value) {
      cluster.addPodLabel(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withPodAnnotation(String name, String value) {
      cluster.addPodAnnotation(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServiceLabel(String name, String value) {
      cluster.addClusterLabel(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServiceAnnotation(String name, String value) {
      cluster.addClusterAnnotation(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withRestartVersion(String restartVersion) {
      cluster.setRestartVersion(restartVersion);
      return this;
    }
  }
}
