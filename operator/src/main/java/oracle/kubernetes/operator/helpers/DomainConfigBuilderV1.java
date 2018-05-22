// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.StartupControlConstants.*;
import static oracle.kubernetes.operator.helpers.ClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.NonClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.ServerConfig.*;

import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;

/**
 * This helper class uses the domain spec that the customer configured to calculate the effective
 * configuration for the servers and clusters in the domain for a domain-v1 DomainSpec.
 */
public class DomainConfigBuilderV1 extends DomainConfigBuilder {

  private DomainSpec domainSpec;

  /**
   * Construct a DomainConfigBuilderV1 instance.
   *
   * @param domainSpec the domain spec
   */
  public DomainConfigBuilderV1(DomainSpec domainSpec) {
    this.domainSpec = domainSpec;
    LOGGER.entering(domainSpec);
    LOGGER.exiting();
  }

  /** {@inheritDoc} */
  @Override
  public void updateDomainSpec(ClusterConfig clusterConfig) {
    LOGGER.entering(clusterConfig);
    ClusterStartup clusterStartup = getClusterStartup(clusterConfig.getClusterName());
    if (clusterStartup != null && clusterStartup.getReplicas() != null) {
      clusterStartup.setReplicas(clusterConfig.getReplicas());
    } else {
      domainSpec.setReplicas(clusterConfig.getReplicas());
    }
    LOGGER.finer("Updated domainSpec: " + domainSpec);
    LOGGER.exiting();
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServerConfig getEffectiveNonClusteredServerConfig(String serverName) {
    LOGGER.entering(serverName);
    NonClusteredServerConfig result = new NonClusteredServerConfig().withServerName(serverName);
    ServerStartup serverStartup = getServerStartup(serverName);
    initServerConfigFromDefaults(result);
    initServerConfigFromDomainSpec(result);
    initServerConfigFromServerStartup(result, serverStartup);
    result.setNonClusteredServerStartPolicy(
        getNonClusteredServerStartPolicy(
            domainSpec.getStartupControl(),
            isAdminServer(serverName, domainSpec.getAsName()),
            serverStartup != null));
    LOGGER.exiting(result);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServerConfig getEffectiveClusteredServerConfig(
      String clusterName, String serverName) {
    LOGGER.entering(clusterName, serverName);
    ClusteredServerConfig result =
        new ClusteredServerConfig().withClusterName(clusterName).withServerName(serverName);
    ClusterStartup clusterStartup = getClusterStartup(clusterName);
    ServerStartup serverStartup = getServerStartup(serverName);
    initServerConfigFromDefaults(result);
    initServerConfigFromDomainSpec(result);
    initClusteredServerConfigFromClusterStartup(result, clusterStartup);
    initServerConfigFromServerStartup(result, serverStartup);
    result.setClusteredServerStartPolicy(
        getClusteredServerStartPolicy(
            domainSpec.getStartupControl(),
            isAdminServer(serverName, domainSpec.getAsName()),
            clusterStartup != null,
            serverStartup != null));
    LOGGER.exiting(result);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public ClusterConfig getEffectiveClusterConfig(String clusterName) {
    LOGGER.entering(clusterName);
    ClusterConfig result = new ClusterConfig().withClusterName(clusterName);
    ClusterStartup clusterStartup = getClusterStartup(clusterName);
    initClusterConfigFromDefaults(result);
    initClusterConfigFromDomainSpec(result);
    initClusterConfigFromClusterStartup(result, clusterStartup);
    result.withMinReplicas(result.getReplicas()).withMaxReplicas(result.getReplicas());
    LOGGER.exiting(result);
    return result;
  }

  protected void initServerConfigFromServerStartup(
      ServerConfig serverConfig, ServerStartup serverStartup) {
    if (serverStartup != null) {
      Integer nodePort = serverStartup.getNodePort();
      if (nodePort != null) {
        serverConfig.withNodePort(nodePort);
      }
      String desiredState = serverStartup.getDesiredState();
      if (desiredState != null) {
        serverConfig.withStartedServerState(desiredState);
      }
      serverConfig.withEnv(serverStartup.getEnv());
    }
  }

  protected void initClusteredServerConfigFromClusterStartup(
      ClusteredServerConfig serverConfig, ClusterStartup clusterStartup) {
    if (clusterStartup != null) {
      String desiredState = clusterStartup.getDesiredState();
      if (desiredState != null) {
        serverConfig.withStartedServerState(desiredState);
      }
      serverConfig.withEnv(clusterStartup.getEnv());
    }
  }

  protected void initServerConfigFromDomainSpec(ServerConfig serverConfig) {
    String image = domainSpec.getImage();
    if (image != null) {
      serverConfig.withImage(image);
    }
    String imagePullPolicy = domainSpec.getImagePullPolicy();
    if (imagePullPolicy != null) {
      serverConfig.withImagePullPolicy(imagePullPolicy);
    }
    if (serverConfig.getImagePullPolicy() == null) {
      serverConfig.withImagePullPolicy(getDefaultImagePullPolicy(serverConfig.getImage()));
    }
  }

  protected void initServerConfigFromDefaults(ServerConfig serverConfig) {
    serverConfig
        .withNodePort(DEFAULT_NODE_PORT)
        .withStartedServerState(DEFAULT_STARTED_SERVER_STATE)
        .withEnv(new ArrayList<V1EnvVar>())
        .withImage(DEFAULT_IMAGE)
        .withShutdownPolicy(DEFAULT_SHUTDOWN_POLICY)
        .withGracefulShutdownTimeout(DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT)
        .withGracefulShutdownIgnoreSessions(DEFAULT_GRACEFUL_SHUTDOWN_IGNORE_SESSIONS)
        .withGracefulShutdownWaitForSessions(DEFAULT_GRACEFUL_SHUTDOWN_WAIT_FOR_SESSIONS);
  }

  protected void initClusterConfigFromClusterStartup(
      ClusterConfig clusterConfig, ClusterStartup clusterStartup) {
    if (clusterStartup != null) {
      Integer replicas = clusterStartup.getReplicas();
      if (replicas != null) {
        clusterConfig.withReplicas(replicas);
      }
    }
  }

  protected void initClusterConfigFromDomainSpec(ClusterConfig clusterConfig) {
    Integer replicas = domainSpec.getReplicas();
    if (replicas != null) {
      clusterConfig.withReplicas(replicas);
    }
  }

  protected void initClusterConfigFromDefaults(ClusterConfig clusterConfig) {
    clusterConfig.withReplicas(DEFAULT_REPLICAS);
  }

  protected String getNonClusteredServerStartPolicy(
      String startupControl, boolean isAdminServer, boolean haveServerStartup) {
    if (NONE_STARTUPCONTROL.equals(startupControl)) {
      return NON_CLUSTERED_SERVER_START_POLICY_NEVER;
    }
    if (ALL_STARTUPCONTROL.equals(startupControl)) {
      return CLUSTERED_SERVER_START_POLICY_ALWAYS;
    }
    if (ADMIN_STARTUPCONTROL.equals(startupControl)) {
      if (isAdminServer) {
        return NON_CLUSTERED_SERVER_START_POLICY_ALWAYS;
      } else {
        return NON_CLUSTERED_SERVER_START_POLICY_NEVER;
      }
    }
    if (SPECIFIED_STARTUPCONTROL.equals(startupControl)
        || AUTO_STARTUPCONTROL.equals(startupControl)) {
      if (isAdminServer || haveServerStartup) {
        return NON_CLUSTERED_SERVER_START_POLICY_ALWAYS;
      } else {
        return NON_CLUSTERED_SERVER_START_POLICY_NEVER;
      }
    }
    throw new AssertionError("Illegal startupControl: '" + startupControl + "'");
  }

  protected String getClusteredServerStartPolicy(
      String startupControl,
      boolean isAdminServer,
      boolean haveClusterStartup,
      boolean haveServerStartup) {
    if (NONE_STARTUPCONTROL.equals(startupControl)) {
      return CLUSTERED_SERVER_START_POLICY_NEVER;
    }
    if (ALL_STARTUPCONTROL.equals(startupControl)) {
      return CLUSTERED_SERVER_START_POLICY_ALWAYS;
    }
    if (ADMIN_STARTUPCONTROL.equals(startupControl)) {
      if (isAdminServer) {
        return CLUSTERED_SERVER_START_POLICY_ALWAYS;
      } else {
        return CLUSTERED_SERVER_START_POLICY_NEVER;
      }
    }
    if (SPECIFIED_STARTUPCONTROL.equals(startupControl)) {
      if (isAdminServer || haveServerStartup) {
        return CLUSTERED_SERVER_START_POLICY_ALWAYS;
      } else if (haveClusterStartup) {
        return CLUSTERED_SERVER_START_POLICY_IF_NEEDED;
      } else {
        return CLUSTERED_SERVER_START_POLICY_NEVER;
      }
    }
    if (AUTO_STARTUPCONTROL.equals(startupControl)) {
      if (isAdminServer || haveServerStartup) {
        return CLUSTERED_SERVER_START_POLICY_ALWAYS;
      } else {
        return CLUSTERED_SERVER_START_POLICY_IF_NEEDED;
      }
    }
    throw new AssertionError("Illegal startupControl: '" + startupControl + "'");
  }

  protected boolean isAdminServer(String serverName, String adminServerName) {
    if (serverName != null) {
      return serverName.equals(adminServerName);
    }
    return false;
  }

  protected ClusterStartup getClusterStartup(String clusterName) {
    List<ClusterStartup> clusterStartups = domainSpec.getClusterStartup();
    if (clusterName != null && clusterStartups != null) {
      for (ClusterStartup clusterStartup : clusterStartups) {
        if (clusterName.equals(clusterStartup.getClusterName())) {
          return clusterStartup;
        }
      }
    }
    return null;
  }

  protected ServerStartup getServerStartup(String serverName) {
    List<ServerStartup> serverStartups = domainSpec.getServerStartup();
    if (serverName != null && serverStartups != null) {
      for (ServerStartup serverStartup : serverStartups) {
        if (serverName.equals(serverStartup.getServerName())) {
          return serverStartup;
        }
      }
    }
    return null;
  }
}
