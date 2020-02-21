// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;

public class ManagedServersUpStep extends Step {
  static final String SERVERS_UP_MSG =
      "Running servers for domain with UID: {0}, running list: {1}";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static NextStepFactory NEXT_STEP_FACTORY =
      (info, config, servers, next) ->
          scaleDownIfNecessary(info, config, servers, new ClusterServicesStep(next));

  public ManagedServersUpStep(Step next) {
    super(next);
  }

  public static Collection<String> getRunningServers(DomainPresenceInfo info) {
    return info.getServerPods().map(PodHelper::getPodServerName).collect(Collectors.toList());
  }

  private static Step scaleDownIfNecessary(
      DomainPresenceInfo info,
      WlsDomainConfig domainTopology,
      Collection<String> servers,
      Step next) {

    List<Step> steps = new ArrayList<>(Collections.singletonList(next));

    List<String> serversToIgnore = new ArrayList<>(servers);
    if (info.getDomain().isShuttingDown()) {
      insert(steps, createAvailableHookStep());
    } else {
      serversToIgnore.add(domainTopology.getAdminServerName());
    }

    List<String> serversToStop = getServersToStop(info, serversToIgnore);

    if (!serversToStop.isEmpty()) {
      insert(steps, new ServerDownIteratorStep(serversToStop, null));
    }

    return Step.chain(steps.toArray(new Step[0]));
  }

  private static List<String> getServersToStop(
      DomainPresenceInfo info, List<String> serversToIgnore) {
    return info.getServerNames().stream()
        .filter(n -> !serversToIgnore.contains(n))
        .collect(Collectors.toList());
  }

  private static Step createAvailableHookStep() {
    return DomainStatusUpdater.createAvailableStep(
        DomainStatusUpdater.ALL_STOPPED_AVAILABLE_REASON, null);
  }

  private static void insert(List<Step> steps, Step step) {
    steps.add(0, step);
  }

  @Override
  public NextAction apply(Packet packet) {
    LOGGER.entering();
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    WlsDomainConfig config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);

    ServersUpStepFactory factory = new ServersUpStepFactory(config, info.getDomain());

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine(SERVERS_UP_MSG, factory.domain.getDomainUid(), getRunningServers(info));
    }

    Set<String> clusteredServers = new HashSet<>();

    for (WlsClusterConfig clusterConfig : config.getClusterConfigs().values()) {
      factory.logIfReplicasExceedsClusterServersMax(clusterConfig);
      for (WlsServerConfig serverConfig : clusterConfig.getServerConfigs()) {
        factory.addServerIfNeeded(serverConfig, clusterConfig);
        clusteredServers.add(serverConfig.getName());
      }
    }

    for (WlsServerConfig serverConfig : config.getServerConfigs().values()) {
      if (!clusteredServers.contains(serverConfig.getName())) {
        factory.addServerIfNeeded(serverConfig, null);
      }
    }

    info.setServerStartupInfo(factory.getStartupInfos());
    LOGGER.exiting();

    return doNext(
        NEXT_STEP_FACTORY.createServerStep(
            info, config, factory.servers, factory.createNextStep(getNext())),
        packet);
  }

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createServerStep(
        DomainPresenceInfo info, WlsDomainConfig config, Collection<String> servers, Step next);
  }

  class ServersUpStepFactory {
    final WlsDomainConfig domainTopology;
    final Domain domain;
    Collection<ServerStartupInfo> startupInfos;
    final Collection<String> servers = new ArrayList<>();
    final Map<String, Integer> replicas = new HashMap<>();

    ServersUpStepFactory(WlsDomainConfig domainTopology, Domain domain) {
      this.domainTopology = domainTopology;
      this.domain = domain;
    }

    /**
     * Checks whether we should pre-create server service for the given server.
     *
     * @param server ServerSpec for the managed server
     * @return True if we should pre-create server service for the given managed server, false
     *         otherwise.
     */
    boolean shouldPrecreateServerService(ServerSpec server) {
      if (server.isPrecreateServerService()) {
        // skip pre-create if admin server and managed server are both shutting down
        return ! (domain.getAdminServerSpec().isShuttingDown() && server.isShuttingDown());
      }
      return false;
    }

    void addServerIfNeeded(@Nonnull WlsServerConfig serverConfig, WlsClusterConfig clusterConfig) {
      String serverName = serverConfig.getName();
      if (servers.contains(serverName) || serverName.equals(domainTopology.getAdminServerName())) {
        return;
      }

      String clusterName = clusterConfig == null ? null : clusterConfig.getClusterName();
      ServerSpec server = domain.getServer(serverName, clusterName);

      if (server.shouldStart(getReplicaCount(clusterName))) {
        servers.add(serverName);
        addStartupInfo(new ServerStartupInfo(serverConfig, clusterName, server));
        addToCluster(clusterName);
      } else if (shouldPrecreateServerService(server)) {
        servers.add(serverName);
        addStartupInfo(new ServerStartupInfo(serverConfig, clusterName, server, true));
      }
    }

    boolean exceedsMaxConfiguredClusterSize(WlsClusterConfig clusterConfig) {
      if (clusterConfig != null) {
        String clusterName = clusterConfig.getClusterName();
        int configMaxClusterSize = clusterConfig.getMaxDynamicClusterSize();
        return clusterConfig.hasDynamicServers()
            && clusterConfig.getServerConfigs().size() == configMaxClusterSize
            && domain.getReplicaCount(clusterName) > configMaxClusterSize;
      }
      return false;
    }

    private Step createNextStep(Step next) {
      if (servers.isEmpty()) {
        return next;
      } else {
        return new ManagedServerUpIteratorStep(getStartupInfos(), next);
      }
    }

    Collection<ServerStartupInfo> getStartupInfos() {
      return startupInfos;
    }

    private void addStartupInfo(ServerStartupInfo startupInfo) {
      if (startupInfos == null) {
        startupInfos = new ArrayList<>();
      }
      startupInfos.add(startupInfo);
    }

    private void addToCluster(String clusterName) {
      if (clusterName != null) {
        replicas.put(clusterName, 1 + getReplicaCount(clusterName));
      }
    }

    private Integer getReplicaCount(String clusterName) {
      return Optional.ofNullable(replicas.get(clusterName)).orElse(0);
    }

    private void logIfReplicasExceedsClusterServersMax(WlsClusterConfig clusterConfig) {
      if (exceedsMaxConfiguredClusterSize(clusterConfig)) {
        String clusterName = clusterConfig.getClusterName();
        LOGGER.warning(
            MessageKeys.REPLICAS_EXCEEDS_TOTAL_CLUSTER_SERVER_COUNT,
            domain.getReplicaCount(clusterName),
            clusterConfig.getMaxDynamicClusterSize(),
            clusterName);
      }
    }
  }
}
