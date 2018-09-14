// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import io.kubernetes.client.models.V1LocalObjectReference;
import javax.annotation.Nonnull;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.ConfigurationNotSupportedException;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

public class DomainV2Configurator implements DomainConfigurator {
  private Domain domain;

  @Override
  public DomainConfigurator createFor(Domain domain) {
    return new DomainV2Configurator(domain);
  }

  public DomainV2Configurator(Domain domain) {
    this.domain = domain;
  }

  @Override
  public void defineAdminServer(String adminServerName) {}

  @Override
  public void defineAdminServer(String adminServerName, int port) {}

  @Override
  public void setDefaultReplicas(int replicas) {}

  @Override
  public void setDefaultImage(String image) {
    getDomainSpec().setImage(image);
  }

  @Override
  public void setDefaultImagePullPolicy(String imagepullpolicy) {
    getDomainSpec().setImagePullPolicy(imagepullpolicy);
  }

  @Override
  public void setDefaultImagePullSecret(V1LocalObjectReference secretReference) {
    getDomainSpec().setImagePullSecret(secretReference);
  }

  @Override
  public DomainConfigurator setStartupControl(String startupControl) {
    throw new ConfigurationNotSupportedException("domain", "startupControl");
  }

  @Override
  public DomainConfigurator withEnvironmentVariable(String name, String value) {
    ((BaseConfiguration) getDomainSpec()).addEnvironmentVariable(name, value);
    return this;
  }

  @Override
  public ServerConfigurator configureAdminServer() {
    return new AdminServerConfiguratorImpl(getOrCreateAdminServer());
  }

  class AdminServerConfiguratorImpl extends ServerConfiguratorImpl {
    AdminServerConfiguratorImpl(AdminServer adminServer) {
      super(adminServer);
    }
  }

  private AdminServer getOrCreateAdminServer() {
    AdminServer adminServer = getDomainSpec().getAdminServer();
    if (adminServer != null) return adminServer;

    return createAdminServer();
  }

  private DomainSpec getDomainSpec() {
    return domain.getSpec();
  }

  private AdminServer createAdminServer() {
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
    public ServerConfigurator withImage(String imageName) {
      server.setImage(imageName);
      return this;
    }

    @Override
    public ServerConfigurator withImagePullPolicy(String policy) {
      server.setImagePullPolicy(policy);
      return this;
    }

    @Override
    public ServerConfigurator withImagePullSecret(String secretName) {
      server.setImagePullSecret(new V1LocalObjectReference().name(secretName));
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
    public ClusterConfigurator withImage(String imageName) {
      cluster.setImage(imageName);
      return this;
    }

    @Override
    public ClusterConfigurator withImagePullPolicy(String policy) {
      cluster.setImagePullPolicy(policy);
      return this;
    }

    @Override
    public ClusterConfigurator withImagePullSecret(String secretName) {
      cluster.setImagePullSecret(new V1LocalObjectReference().name(secretName));
      return this;
    }

    @Override
    public ClusterConfigurator withServerStartState(String state) {
      return withDesiredState(state);
    }
  }
}
