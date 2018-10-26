// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.VersionConstants.DOMAIN_V2;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_NEVER;

import javax.annotation.Nonnull;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.ConfigurationNotSupportedException;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.ExportedNetworkAccessPoint;

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
  public void withDefaultReplicaCount(int replicas) {
    throw new ConfigurationNotSupportedException("domain", "defaultReplicas");
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
  public DomainConfigurator withStartupControl(String startupControl) {
    throw new ConfigurationNotSupportedException("domain", "startupControl");
  }

  @Override
  public DomainConfigurator withEnvironmentVariable(String name, String value) {
    ((BaseConfiguration) getDomainSpec()).addEnvironmentVariable(name, value);
    return this;
  }

  class AdminServerConfiguratorImpl extends ServerConfiguratorImpl
      implements AdminServerConfigurator {
    AdminServerConfiguratorImpl(AdminServer adminServer) {
      super(adminServer);
    }

    @Override
    public AdminServerConfigurator withPort(int port) {
      getDomainSpec().setAsPort(port);
      return this;
    }

    @Override
    public AdminServerConfigurator withExportedNetworkAccessPoints(String... names) {
      for (String name : names) {
        getDomainSpec().addExportedNetworkAccessPoint(name);
      }
      return this;
    }

    @Override
    public ExportedNetworkAccessPoint configureExportedNetworkAccessPoint(String channelName) {
      return getDomainSpec().addExportedNetworkAccessPoint(channelName);
    }
  }

  private AdminServer getOrCreateAdminServer(String adminServerName) {
    AdminServer adminServer = getDomainSpec().getAdminServer();
    if (adminServer != null) return adminServer;

    return createAdminServer(adminServerName);
  }

  private AdminServer createAdminServer(String adminServerName) {
    getDomainSpec().setAsName(adminServerName);
    AdminServer adminServer = new AdminServer();
    getDomainSpec().setAdminServer(adminServer);
    return adminServer;
  }

  @Override
  public ServerConfigurator configureServer(@Nonnull String serverName) {
    return new ServerConfiguratorImpl(getOrCreateManagedServer(serverName));
  }

  private Server getOrCreateManagedServer(@Nonnull String serverName) {
    for (ManagedServer server : getDomainSpec().getManagedServers()) {
      if (serverName.equals(server.getServerName())) return server;
    }

    return createManagedServer(serverName);
  }

  private Server createManagedServer(String serverName) {
    ManagedServer server = new ManagedServer().withServerName(serverName);
    getDomainSpec().getManagedServers().add(server);
    return server;
  }

  class ServerConfiguratorImpl implements ServerConfigurator {
    private Server server;

    ServerConfiguratorImpl(Server server) {
      this.server = server;
    }

    @Override
    public ServerConfigurator withNodePort(int nodePort) {
      server.setNodePort(nodePort);
      return this;
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
  }

  @Override
  public ClusterConfigurator configureCluster(@Nonnull String clusterName) {
    return new ClusterConfiguratorImpl(getOrCreateCluster(clusterName));
  }

  private Cluster getOrCreateCluster(@Nonnull String clusterName) {
    for (Cluster cluster : getDomainSpec().getClusters()) {
      if (clusterName.equals(cluster.getClusterName())) return cluster;
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
    configureAdminServer("").withServerStartPolicy(shuttingDown ? START_NEVER : START_ALWAYS);
  }

  @Override
  public boolean useDomainV1() {
    return false;
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
    public ClusterConfigurator withServerStartupPolicy(String policy) {
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
  }
}
