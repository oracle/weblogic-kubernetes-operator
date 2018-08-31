// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nonnull;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;

/** An implementation of the domain configuration for the version 1 domain. */
public class DomainV1Configurator implements DomainConfigurator {
  private Domain domain;

  /**
   * Constructs a version 1 domain configurator
   *
   * @param domain the domain to be configured
   */
  public DomainV1Configurator(Domain domain) {
    this.domain = domain;
  }

  @Override
  public void defineAdminServer(String adminServerName) {
    domain.getSpec().setAsName(adminServerName);
  }

  @Override
  public void defineAdminServer(String adminServerName, int port) {
    domain.getSpec().withAsName(adminServerName).setAsPort(port);
  }

  @Override
  public void setDefaultReplicas(int replicas) {
    domain.getSpec().setReplicas(replicas);
  }

  @Override
  public DomainConfigurator setStartupControl(String startupControl) {
    domain.getSpec().setStartupControl(startupControl);
    return this;
  }

  @Override
  public ServerConfigurator configureServer(@Nonnull String serverName) {
    return new ServerStartupConfigurator(getOrCreateServerStartup(serverName));
  }

  @SuppressWarnings("deprecation")
  private ServerStartup getOrCreateServerStartup(@Nonnull String serverName) {
    for (ServerStartup startup :
        Optional.ofNullable(domain.getSpec().getServerStartup()).orElse(Collections.emptyList())) {
      if (serverName.equals(startup.getServerName())) return startup;
    }

    ServerStartup serverStartup = new ServerStartup().withServerName(serverName);
    domain.getSpec().addServerStartupItem(serverStartup);
    return serverStartup;
  }

  @Override
  public ClusterConfigurator configureCluster(@Nonnull String clusterName) {
    return new ClusterStartupConfigurator(getOrCreateClusterStartup(clusterName));
  }

  @SuppressWarnings("deprecation")
  private ClusterStartup getOrCreateClusterStartup(String clusterName) {
    for (ClusterStartup startup :
        Optional.ofNullable(domain.getSpec().getClusterStartup()).orElse(Collections.emptyList())) {
      if (clusterName.equals(startup.getClusterName())) return startup;
    }

    ClusterStartup startup = new ClusterStartup().withClusterName(clusterName);
    domain.getSpec().addClusterStartupItem(startup);
    return startup;
  }

  class ServerStartupConfigurator implements ServerConfigurator {
    private ServerStartup serverStartup;

    ServerStartupConfigurator(ServerStartup serverStartup) {
      this.serverStartup = serverStartup;
    }

    @Override
    public ServerConfigurator withNodePort(int nodePort) {
      serverStartup.setNodePort(nodePort);
      return this;
    }

    @Override
    public ServerConfigurator withDesiredState(String desiredState) {
      serverStartup.setDesiredState(desiredState);
      return this;
    }

    @Override
    public ServerConfigurator withEnvironmentVariable(String name, String value) {
      serverStartup.withEnvironmentVariable(name, value);
      return this;
    }
  }

  class ClusterStartupConfigurator implements ClusterConfigurator {
    private ClusterStartup clusterStartup;

    ClusterStartupConfigurator(ClusterStartup clusterStartup) {
      this.clusterStartup = clusterStartup;
    }

    @Override
    public ClusterConfigurator withReplicas(int replicas) {
      clusterStartup.setReplicas(replicas);
      return this;
    }

    @Override
    public ClusterConfigurator withEnvironmentVariable(String name, String value) {
      clusterStartup.withEnvironmentVariable(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withDesiredState(String state) {
      clusterStartup.setDesiredState(state);
      return this;
    }
  }
}
