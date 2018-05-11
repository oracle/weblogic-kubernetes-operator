// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.StartupControlConstants;
import oracle.kubernetes.operator.WebLogicConstants;
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
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;

public class ManagedServersUpStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ManagedServersUpStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    LOGGER.entering();
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();

    if (LOGGER.isFineEnabled()) {
      Collection<String> runningList = new ArrayList<>();
      for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
        ServerKubernetesObjects sko = entry.getValue();
        if (sko != null && sko.getPod() != null) {
          runningList.add(entry.getKey());
        }
      }
      LOGGER.fine(
          "Running servers for domain with UID: "
              + spec.getDomainUID()
              + ", running list: "
              + runningList);
    }

    String sc = spec.getStartupControl();
    if (sc == null) {
      sc = StartupControlConstants.AUTO_STARTUPCONTROL;
    } else {
      sc = sc.toUpperCase();
    }

    WlsDomainConfig scan = info.getScan();
    Collection<ServerStartupInfo> ssic = new ArrayList<ServerStartupInfo>();

    String asName = spec.getAsName();

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
    Collection<String> servers = new ArrayList<String>();
    switch (sc) {
      case StartupControlConstants.ALL_STARTUPCONTROL:
        startAll = true;
      case StartupControlConstants.AUTO_STARTUPCONTROL:
      case StartupControlConstants.SPECIFIED_STARTUPCONTROL:
        Collection<String> clusters = new ArrayList<String>();

        // start specified servers with their custom options
        List<ServerStartup> ssl = spec.getServerStartup();
        if (ssl != null) {
          for (ServerStartup ss : ssl) {
            String serverName = ss.getServerName();
            WlsServerConfig wlsServerConfig = scan.getServerConfig(serverName);
            if (!serverName.equals(asName)
                && wlsServerConfig != null
                && !servers.contains(serverName)) {
              // start server
              servers.add(serverName);
              // find cluster if this server is part of one
              WlsClusterConfig cc = null;
              find:
              for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
                for (WlsServerConfig clusterMemberServerConfig :
                    wlsClusterConfig.getServerConfigs()) {
                  if (serverName.equals(clusterMemberServerConfig.getName())) {
                    cc = wlsClusterConfig;
                    break find;
                  }
                }
              }
              List<V1EnvVar> env = ss.getEnv();
              if (WebLogicConstants.ADMIN_STATE.equals(ss.getDesiredState())) {
                env = startInAdminMode(env);
              }
              ssic.add(new ServerStartupInfo(wlsServerConfig, cc, env, ss));
            }
          }
        }
        List<ClusterStartup> lcs = spec.getClusterStartup();
        if (lcs != null) {
          cluster:
          for (ClusterStartup cs : lcs) {
            String clusterName = cs.getClusterName();
            clusters.add(clusterName);
            int startedCount = 0;
            // find cluster
            WlsClusterConfig wlsClusterConfig = scan.getClusterConfig(clusterName);
            if (wlsClusterConfig != null) {
              for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
                // done with the current cluster
                if (startedCount >= cs.getReplicas() && !startAll) continue cluster;

                String serverName = wlsServerConfig.getName();
                if (!serverName.equals(asName) && !servers.contains(serverName)) {
                  List<V1EnvVar> env = cs.getEnv();
                  ServerStartup ssi = null;
                  ssl = spec.getServerStartup();
                  if (ssl != null) {
                    for (ServerStartup ss : ssl) {
                      String s = ss.getServerName();
                      if (serverName.equals(s)) {
                        env = ss.getEnv();
                        ssi = ss;
                        break;
                      }
                    }
                  }
                  // start server
                  servers.add(serverName);
                  if (WebLogicConstants.ADMIN_STATE.equals(cs.getDesiredState())) {
                    env = startInAdminMode(env);
                  }
                  ssic.add(new ServerStartupInfo(wlsServerConfig, wlsClusterConfig, env, ssi));
                  startedCount++;
                }
              }
            }
          }
        }
        if (startAll) {
          // Look for any other servers
          for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
            for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
              String serverName = wlsServerConfig.getListenAddress();
              // do not start admin server
              if (!serverName.equals(asName) && !servers.contains(serverName)) {
                // start server
                servers.add(serverName);
                ssic.add(new ServerStartupInfo(wlsServerConfig, wlsClusterConfig, null, null));
              }
            }
          }
          for (Map.Entry<String, WlsServerConfig> wlsServerConfig :
              scan.getServerConfigs().entrySet()) {
            String serverName = wlsServerConfig.getKey();
            // do not start admin server
            if (!serverName.equals(asName) && !servers.contains(serverName)) {
              // start server
              servers.add(serverName);
              ssic.add(new ServerStartupInfo(wlsServerConfig.getValue(), null, null, null));
            }
          }
        } else if (StartupControlConstants.AUTO_STARTUPCONTROL.equals(sc)) {
          for (Map.Entry<String, WlsClusterConfig> wlsClusterConfig :
              scan.getClusterConfigs().entrySet()) {
            if (!clusters.contains(wlsClusterConfig.getKey())) {
              int startedCount = 0;
              WlsClusterConfig config = wlsClusterConfig.getValue();
              for (WlsServerConfig wlsServerConfig : config.getServerConfigs()) {
                if (startedCount >= spec.getReplicas()) break;
                String serverName = wlsServerConfig.getName();
                if (!serverName.equals(asName) && !servers.contains(serverName)) {
                  // start server
                  servers.add(serverName);
                  ssic.add(new ServerStartupInfo(wlsServerConfig, config, null, null));
                  startedCount++;
                }
              }
            }
          }
        }

        info.setServerStartupInfo(ssic);
        LOGGER.exiting();
        return doNext(
            scaleDownIfNecessary(
                info,
                servers,
                new ClusterServicesStep(info, new ManagedServerUpIteratorStep(ssic, next))),
            packet);
      case StartupControlConstants.ADMIN_STARTUPCONTROL:
      case StartupControlConstants.NONE_STARTUPCONTROL:
      default:
        info.setServerStartupInfo(null);
        LOGGER.exiting();
        return doNext(
            scaleDownIfNecessary(info, servers, new ClusterServicesStep(info, next)), packet);
    }
  }

  private static List<V1EnvVar> startInAdminMode(List<V1EnvVar> env) {
    if (env == null) {
      env = new ArrayList<>();
    }

    // look for JAVA_OPTIONS
    V1EnvVar jo = null;
    for (V1EnvVar e : env) {
      if ("JAVA_OPTIONS".equals(e.getName())) {
        jo = e;
        if (jo.getValueFrom() != null) {
          throw new IllegalStateException();
        }
        break;
      }
    }
    if (jo == null) {
      jo = new V1EnvVar();
      jo.setName("JAVA_OPTIONS");
      env.add(jo);
    }

    // create or update value
    String startInAdmin = "-Dweblogic.management.startupMode=ADMIN";
    String value = jo.getValue();
    value = (value != null) ? (startInAdmin + " " + value) : startInAdmin;
    jo.setValue(value);

    return env;
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
