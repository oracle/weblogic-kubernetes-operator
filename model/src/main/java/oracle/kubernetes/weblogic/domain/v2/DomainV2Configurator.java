// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.VersionConstants.DOMAIN_V2;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_NEVER;

import java.util.Arrays;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;

public class DomainV2Configurator extends DomainConfigurator {

  @Override
  public DomainConfigurator createFor(Domain domain) {
    return new DomainV2Configurator(domain);
  }

  public DomainV2Configurator() {}

  public DomainV2Configurator(@Nonnull Domain domain) {
    super(domain);
    setApiVersion(domain);
  }

  private void setApiVersion(Domain domain) {
    domain.getMetadata().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V2);
    domain.setApiVersion(KubernetesConstants.API_VERSION_ORACLE_V2);
  }

  @Override
  public AdminServerConfigurator configureAdminServer(String adminServerName) {
    return new AdminServerConfiguratorImpl(getOrCreateAdminServer(adminServerName));
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
  /**
   * Sets the WebLogic configuration overrides configmap name for the domain
   *
   * @param configMapName Name of the Kubernetes configmap that contains the config overrides
   * @return this object
   */
  public DomainConfigurator withConfigOverrides(String configMapName) {
    getDomainSpec().setConfigOverrides(configMapName);
    return this;
  }

  @Override
  /**
   * Sets the WebLogic configuration overrides secret names for the domain
   *
   * @param secretNames a list of secret names
   * @return this object
   */
  public DomainConfigurator withConfigOverrideSecrets(String... secretNames) {
    getDomainSpec().setConfigOverrideSecrets(Arrays.asList(secretNames));
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
    public AdminServerConfigurator withPort(int port) {
      getDomainSpec().setAsPort(port);
      return this;
    }

    @Override
    public AdminServerConfigurator withNodePort(int nodePort) {
      adminServer.setNodePort(nodePort);
      return this;
    }

    @Override
    public AdminServerConfigurator withExportedNetworkAccessPoints(String... names) {
      for (String name : names) {
        adminServer.addExportedNetworkAccessPoint(name);
      }
      return this;
    }

    @Override
    public ExportedNetworkAccessPoint configureExportedNetworkAccessPoint(String channelName) {
      return adminServer.addExportedNetworkAccessPoint(channelName);
    }
  }

  private AdminServer getOrCreateAdminServer(String adminServerName) {
    return getDomainSpec().getOrCreateAdminServer(adminServerName);
  }

  @Override
  public ServerConfigurator configureServer(@Nonnull String serverName) {
    return new ServerConfiguratorImpl(getOrCreateManagedServer(serverName));
  }

  private Server getOrCreateManagedServer(@Nonnull String serverName) {
    ManagedServer server = getDomainSpec().getManagedServers().get(serverName);
    if (server != null) {
      server.setServerName(serverName);
      return server;
    }

    return createManagedServer(serverName);
  }

  private Server createManagedServer(String serverName) {
    ManagedServer server = new ManagedServer().withServerName(serverName);
    getDomainSpec().getManagedServers().put(serverName, server);
    return server;
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
    public ServerConfigurator withAdditionalVolume(String name, String path) {
      server.addAdditionalVolume(name, path);
      return this;
    }

    @Override
    public ServerConfigurator withAdditionalVolumeMount(String name, String path) {
      server.addAdditionalVolumeMount(name, path);
      return this;
    }
  }

  @Override
  public ClusterConfigurator configureCluster(@Nonnull String clusterName) {
    return new ClusterConfiguratorImpl(getOrCreateCluster(clusterName));
  }

  private Cluster getOrCreateCluster(@Nonnull String clusterName) {
    Cluster cluster = getDomainSpec().getClusters().get(clusterName);
    if (cluster != null) {
      cluster.setClusterName(clusterName);
      return cluster;
    }

    return createCluster(clusterName);
  }

  private Cluster createCluster(@Nonnull String clusterName) {
    Cluster cluster = new Cluster().withClusterName(clusterName);
    getDomainSpec().getClusters().put(clusterName, cluster);
    return cluster;
  }

  @Override
  public void setShuttingDown(boolean shuttingDown) {
    configureAdminServer("").withServerStartPolicy(shuttingDown ? START_NEVER : START_ALWAYS);
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
    public ClusterConfigurator withAdditionalVolume(String name, String path) {
      cluster.addAdditionalVolume(name, path);
      return this;
    }

    @Override
    public ClusterConfigurator withAdditionalVolumeMount(String name, String path) {
      cluster.addAdditionalVolumeMount(name, path);
      return this;
    }
  }
}
