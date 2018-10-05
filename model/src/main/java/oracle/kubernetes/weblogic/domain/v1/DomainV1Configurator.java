// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import io.kubernetes.client.models.V1LocalObjectReference;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nonnull;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.ConfigurationNotSupportedException;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;

/** An implementation of the domain configuration for the version 1 domain. */
public class DomainV1Configurator extends DomainConfigurator {

  @Override
  public DomainConfigurator createFor(Domain domain) {
    return new DomainV1Configurator(domain);
  }

  /**
   * Constructs a version 1 domain configurator
   *
   * @param domain the domain to be configured
   */
  public DomainV1Configurator(Domain domain) {
    super(domain);
  }

  @Override
  public void defineAdminServer(String adminServerName) {
    getDomainSpec().setAsName(adminServerName);
  }

  @Override
  public void defineAdminServer(String adminServerName, int port) {
    getDomainSpec().withAsName(adminServerName).setAsPort(port);
  }

  @Override
  public void withDefaultReplicaCount(int replicas) {
    getDomainSpec().setReplicas(replicas);
  }

  @Override
  public void withDefaultImage(String image) {
    getDomainSpec().setImage(image);
  }

  @Override
  public void withDefaultImagePullPolicy(String imagepullpolicy) {
    getDomainSpec().setImagePullPolicy(imagepullpolicy);
  }

  @Override
  public void withDefaultImagePullSecret(V1LocalObjectReference secretReference) {
    getDomainSpec().setImagePullSecret(secretReference);
  }

  @Override
  public DomainConfigurator withPredefinedClaim(String claimName) {
    getDomainSpec().setStorage(DomainStorage.createPredefinedClaim(claimName));
    return this;
  }

  @Override
  public DomainConfigurator withHostPathStorage(String path) {
    getDomainSpec().setStorage(DomainStorage.createHostPathStorage(path));
    return this;
  }

  @Override
  public DomainConfigurator withStorageSize(String size) {
    getDomainSpec().getStorage().setStorageSize(size);
    return this;
  }

  @Override
  public void withDefaultReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period) {
    throw new ConfigurationNotSupportedException("domain", "readinessProbe");
  }

  @Override
  public void withDefaultLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period) {
    throw new ConfigurationNotSupportedException("domain", "livenessProbe");
  }

  @Override
  public DomainConfigurator setStartupControl(String startupControl) {
    getDomainSpec().setStartupControl(startupControl);
    return this;
  }

  @Override
  public DomainConfigurator withEnvironmentVariable(String name, String value) {
    throw new ConfigurationNotSupportedException("domain", "env");
  }

  @Override
  public ServerConfigurator configureAdminServer() {
    return configureServer(getAsName());
  }

  @Override
  public ServerConfigurator configureServer(@Nonnull String serverName) {
    return new ServerStartupConfigurator(getOrCreateServerStartup(serverName));
  }

  @SuppressWarnings("deprecation")
  private ServerStartup getOrCreateServerStartup(@Nonnull String serverName) {
    for (ServerStartup startup :
        Optional.ofNullable(getDomainSpec().getServerStartup()).orElse(Collections.emptyList())) {
      if (serverName.equals(startup.getServerName())) return startup;
    }

    ServerStartup serverStartup = new ServerStartup().withServerName(serverName);
    getDomainSpec().addServerStartupItem(serverStartup);
    return serverStartup;
  }

  @Override
  public ClusterConfigurator configureCluster(@Nonnull String clusterName) {
    return new ClusterStartupConfigurator(getOrCreateClusterStartup(clusterName));
  }

  @SuppressWarnings("deprecation")
  private ClusterStartup getOrCreateClusterStartup(String clusterName) {
    for (ClusterStartup startup :
        Optional.ofNullable(getDomainSpec().getClusterStartup()).orElse(Collections.emptyList())) {
      if (clusterName.equals(startup.getClusterName())) return startup;
    }

    ClusterStartup startup = new ClusterStartup().withClusterName(clusterName);
    getDomainSpec().addClusterStartupItem(startup);
    return startup;
  }

  @SuppressWarnings("deprecation")
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

    @Override
    public ServerConfigurator withImage(String imageName) {
      throw new ConfigurationNotSupportedException("server", "image");
    }

    @Override
    public ServerConfigurator withImagePullPolicy(String policy) {
      throw new ConfigurationNotSupportedException("server", "imagePullPolicy");
    }

    @Override
    public ServerConfigurator withImagePullSecret(String secretName) {
      throw new ConfigurationNotSupportedException("server", "imagePullSecret");
    }

    @Override
    public ServerConfigurator withServerStartState(String state) {
      return withDesiredState(state);
    }

    @Override
    public ServerConfigurator withServerStartPolicy(String startNever) {
      throw new ConfigurationNotSupportedException("server", "serverStartPolicy");
    }

    @Override
    public ServerConfigurator withLivenessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      throw new ConfigurationNotSupportedException("server", "livenessProbe");
    }

    @Override
    public ServerConfigurator withReadinessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      throw new ConfigurationNotSupportedException("server", "readinessProbe");
    }
  }

  @SuppressWarnings("deprecation")
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
    public ClusterConfigurator withImage(String imageName) {
      throw new ConfigurationNotSupportedException("cluster", "image");
    }

    @Override
    public ClusterConfigurator withImagePullPolicy(String policy) {
      throw new ConfigurationNotSupportedException("cluster", "imagePullPolicy");
    }

    @Override
    public ClusterConfigurator withImagePullSecret(String secretName) {
      throw new ConfigurationNotSupportedException("cluster", "imagePullSecret");
    }

    @Override
    public ClusterConfigurator withServerStartState(String state) {
      return withDesiredState(state);
    }

    @Override
    public ClusterConfigurator withReadinessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      throw new ConfigurationNotSupportedException("cluster", "readinessProbe");
    }

    @Override
    public ClusterConfigurator withLivenessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      throw new ConfigurationNotSupportedException("cluster", "livenessProbe");
    }

    @Override
    public ClusterConfigurator withDesiredState(String state) {
      clusterStartup.setDesiredState(state);
      return this;
    }
  }
}
