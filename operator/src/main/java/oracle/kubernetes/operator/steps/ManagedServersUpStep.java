// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;

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
    Collection<ServerStartupInfo> startupInfos;
    Collection<String> servers = new ArrayList<>();
    Map<String, Integer> replicas = new HashMap<>();

    ServersUpStepFactory(Domain domain) {
      this.domain = domain;
    }

    void addServerIfNeeded(@Nonnull WlsServerConfig serverConfig, WlsClusterConfig clusterConfig) {
      String serverName = serverConfig.getName();
      if (servers.contains(serverName) || serverName.equals(domain.getAsName())) return;

      String clusterName = clusterConfig == null ? null : clusterConfig.getClusterName();
      ServerSpec server = domain.getServer(serverName, clusterName);

      if (server.shouldStart(getReplicaCount(clusterName))) {
        servers.add(serverName);
        addStartupInfo(new ServerStartupInfo(serverConfig, clusterName, server));
        addToCluster(clusterName);
      }
    }

    private Step createNextStep(Step next) {
      if (servers.isEmpty()) return next;
      else return new ManagedServerUpIteratorStep(getStartupInfos(), next);
    }

    Collection<ServerStartupInfo> getStartupInfos() {
      return startupInfos;
    }

    private void addStartupInfo(ServerStartupInfo startupInfo) {
      if (startupInfos == null) startupInfos = new ArrayList<>();
      startupInfos.add(startupInfo);
    }

    private void addToCluster(String clusterName) {
      if (clusterName != null) replicas.put(clusterName, 1 + getReplicaCount(clusterName));
    }

    private Integer getReplicaCount(String clusterName) {
      return Optional.ofNullable(replicas.get(clusterName)).orElse(0);
    }
  }

  public ManagedServersUpStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    LOGGER.entering();
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
    ServersUpStepFactory factory = new ServersUpStepFactory(info.getDomain());

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine(SERVERS_UP_MSG, factory.domain.getDomainUID(), getRunningServers(info));
    }

    updateExplicitRestart(info);

    for (WlsServerConfig serverConfig : info.getScan().getServerConfigs().values()) {
      factory.addServerIfNeeded(serverConfig, null);
    }

    for (WlsClusterConfig clusterConfig : info.getScan().getClusterConfigs().values()) {
      for (WlsServerConfig serverConfig : clusterConfig.getServerConfigs()) {
        factory.addServerIfNeeded(serverConfig, clusterConfig);
      }
    }

    info.setServerStartupInfo(factory.getStartupInfos());
    LOGGER.exiting();
    return doNext(
        NEXT_STEP_FACTORY.createServerStep(
            info, factory.servers, factory.createNextStep(getNext())),
        packet);
  }

  private void updateExplicitRestart(DomainPresenceInfo info) {
    for (String clusterName : info.getExplicitRestartClusters()) {
      WlsClusterConfig cluster = info.getScan().getClusterConfig(clusterName);
      if (cluster != null) {
        for (WlsServerConfig server : cluster.getServerConfigs()) {
          info.getExplicitRestartServers().add(server.getName());
        }
      }
    }
    info.getExplicitRestartClusters().clear();
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

    List<Step> steps = new ArrayList<>(Collections.singletonList(next));

    List<String> serversToIgnore = new ArrayList<>(servers);
    if (info.getDomain().isShuttingDown()) {
      insert(steps, createAvailableHookStep());
    } else {
      serversToIgnore.add(info.getDomain().getAsName());
    }

    Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop =
        getServersToStop(info, serversToIgnore);

    if (!serversToStop.isEmpty()) {
      insert(steps, new ServerDownIteratorStep(serversToStop, null));
    }

    return Step.chain(steps.toArray(new Step[0]));
  }

  private static Collection<Map.Entry<String, ServerKubernetesObjects>> getServersToStop(
      DomainPresenceInfo info, List<String> serversToIgnore) {
    Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop = new ArrayList<>();
    for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
      if (!serversToIgnore.contains(entry.getKey())) {
        serversToStop.add(entry);
      }
    }
    return serversToStop;
  }

  private static Step createAvailableHookStep() {
    return DomainStatusUpdater.createAvailableStep(
        DomainStatusUpdater.ALL_STOPPED_AVAILABLE_REASON, null);
  }

  private static void insert(List<Step> steps, Step step) {
    steps.add(0, step);
  }
}
