// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.StartupControlConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;

public class ManagedServersUpStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  static final String SERVERS_UP_MSG = "Running servers for domain with UID: %s, running list: %s";
  private static NextStepFactory NEXT_STEP_FACTORY =
      (info, servers, next) ->
          scaleDownIfNecessary(info, servers, new ClusterServicesStep(info, next));

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createServerStep(DomainPresenceInfo info, Collection<String> servers, Step next);
  }

  class ServersUpStepFactory {
    Domain domain;
    Collection<ServerStartupInfo> ssic = new ArrayList<>();
    Collection<String> servers = new ArrayList<>();
    Map<String, Integer> replicas = new HashMap<>();

    ServersUpStepFactory(Domain domain) {
      this.domain = domain;
    }

    private boolean needToAddServer(String serverName) {
      return !serverName.equals(domain.getAsName()) && !servers.contains(serverName);
    }

    private void addStartupInfo(WlsServerConfig server, String clusterName) {
      ServerSpec serverSpec = domain.getServer(clusterName, server.getName());
      ssic.add(
          new ServerStartupInfo(
              server,
              clusterName,
              serverSpec.getEnvironmentVariables(),
              serverSpec.getServerStartup()));
    }

    private void addToCluster(String clusterName) {
      if (clusterName != null) replicas.put(clusterName, 1 + getReplicas(clusterName));
    }

    private Integer getReplicas(String clusterName) {
      return Optional.ofNullable(replicas.get(clusterName)).orElse(0);
    }
  }

  private ServersUpStepFactory factory;

  public ManagedServersUpStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    LOGGER.entering();
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();
    factory = new ServersUpStepFactory(dom);

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine(SERVERS_UP_MSG, spec.getDomainUID(), getRunningServers(info));
    }

    String sc = dom.getStartupControl();

    WlsDomainConfig scan = info.getScan();

    for (String clusterName : info.getExplicitRestartClusters()) {
      WlsClusterConfig cluster = scan.getClusterConfig(clusterName);
      if (cluster != null) {
        for (WlsServerConfig server : cluster.getServerConfigs()) {
          info.getExplicitRestartServers().add(server.getName());
        }
      }
    }
    info.getExplicitRestartClusters().clear();

    boolean startAll = false;
    switch (sc) {
      case StartupControlConstants.ALL_STARTUPCONTROL:
        startAll = true;
      case StartupControlConstants.AUTO_STARTUPCONTROL:
      case StartupControlConstants.SPECIFIED_STARTUPCONTROL:
        Collection<String> clusters = new ArrayList<>();

        // start specified servers with their custom options
        List<ServerStartup> ssl = spec.getServerStartup();
        if (ssl != null) {
          for (ServerStartup ss : ssl) {
            WlsServerConfig wlsServerConfig = scan.getServerConfig(ss.getServerName());
            if (wlsServerConfig != null) {
              String serverName = wlsServerConfig.getName();
              WlsClusterConfig wlsClusterConfig = getWlsClusterConfig(scan, serverName);
              String clusterName =
                  wlsClusterConfig == null ? null : wlsClusterConfig.getClusterName();
              if (factory.needToAddServer(serverName)) {
                // start server
                factory.servers.add(serverName);
                factory.addStartupInfo(wlsServerConfig, clusterName);
              }
            }
          }
        }
        List<ClusterStartup> lcs = spec.getClusterStartup();
        if (lcs != null) {
          cluster:
          for (ClusterStartup cs : lcs) {
            String clusterName = cs.getClusterName();
            clusters.add(clusterName);
            // find cluster
            WlsClusterConfig wlsClusterConfig = scan.getClusterConfig(clusterName);
            if (wlsClusterConfig != null) {
              for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
                // done with the current cluster
                if (factory.getReplicas(clusterName) >= factory.domain.getReplicaLimit(clusterName)
                    && !startAll) continue cluster;

                String serverName = wlsServerConfig.getName();
                if (factory.needToAddServer(serverName)) {
                  factory.servers.add(serverName);
                  factory.addStartupInfo(wlsServerConfig, clusterName);
                  factory.addToCluster(clusterName);
                }
              }
            }
          }
        }
        if (startAll) {
          // Look for any other servers
          for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
            for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
              String serverName = wlsServerConfig.getName();
              String clusterName = wlsClusterConfig.getClusterName();
              // do not start admin server
              if (factory.needToAddServer(serverName)) {
                // start server
                factory.servers.add(serverName);
                factory.addStartupInfo(wlsServerConfig, clusterName);
              }
            }
          }
          for (WlsServerConfig wlsServerConfig : scan.getServerConfigs().values()) {
            String serverName = wlsServerConfig.getName();
            // do not start admin server
            if (factory.needToAddServer(serverName)) {
              // start server
              factory.servers.add(serverName);
              factory.addStartupInfo(wlsServerConfig, null);
            }
          }
        } else if (StartupControlConstants.AUTO_STARTUPCONTROL.equals(sc)) {
          for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
            String clusterName = wlsClusterConfig.getClusterName();
            if (!clusters.contains(clusterName)) {
              for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
                if (factory.getReplicas(clusterName) >= factory.domain.getReplicaLimit(clusterName))
                  break;
                String serverName = wlsServerConfig.getName();
                if (factory.needToAddServer(serverName)) {
                  // start server
                  factory.servers.add(serverName);
                  factory.addStartupInfo(wlsServerConfig, clusterName);
                  factory.addToCluster(clusterName);
                }
              }
            }
          }
        }

        info.setServerStartupInfo(factory.ssic);
        LOGGER.exiting();
        return doNext(
            NEXT_STEP_FACTORY.createServerStep(
                info, factory.servers, new ManagedServerUpIteratorStep(factory.ssic, getNext())),
            packet);
      case StartupControlConstants.ADMIN_STARTUPCONTROL:
      case StartupControlConstants.NONE_STARTUPCONTROL:
      default:
        info.setServerStartupInfo(null);
        LOGGER.exiting();
        return doNext(NEXT_STEP_FACTORY.createServerStep(info, factory.servers, getNext()), packet);
    }
  }

  private WlsClusterConfig getWlsClusterConfig(WlsDomainConfig scan, String serverName) {
    WlsClusterConfig cc = null;
    find:
    for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
      for (WlsServerConfig clusterMemberServerConfig : wlsClusterConfig.getServerConfigs()) {
        if (serverName.equals(clusterMemberServerConfig.getName())) {
          cc = wlsClusterConfig;
          break find;
        }
      }
    }
    return cc;
  }

  private Collection<String> getRunningServers(DomainPresenceInfo info) {
    Collection<String> runningList = new ArrayList<>();
    for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
      ServerKubernetesObjects sko = entry.getValue();
      if (sko != null && sko.getPod() != null) {
        runningList.add(entry.getKey());
      }
    }
    return runningList;
  }

  private static Step scaleDownIfNecessary(
      DomainPresenceInfo info, Collection<String> servers, Step next) {
    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();

    boolean shouldStopAdmin = false;
    String sc = spec.getStartupControl();
    if (sc != null && StartupControlConstants.NONE_STARTUPCONTROL.equals(sc.toUpperCase())) {
      shouldStopAdmin = true;
      next =
          DomainStatusUpdater.createAvailableStep(
              DomainStatusUpdater.ALL_STOPPED_AVAILABLE_REASON, next);
    }

    String adminName = spec.getAsName();
    Map<String, ServerKubernetesObjects> currentServers = info.getServers();
    Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop = new ArrayList<>();
    for (Map.Entry<String, ServerKubernetesObjects> entry : currentServers.entrySet()) {
      if ((shouldStopAdmin || !entry.getKey().equals(adminName))
          && !servers.contains(entry.getKey())) {
        serversToStop.add(entry);
      }
    }

    if (!serversToStop.isEmpty()) {
      return new ServerDownIteratorStep(serversToStop, next);
    }

    return next;
  }
}
