// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerShutdownInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
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
import oracle.kubernetes.utils.OperatorUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;

import static java.util.Comparator.comparing;
import static oracle.kubernetes.operator.DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON;
import static oracle.kubernetes.operator.DomainStatusUpdater.createProgressingStartedEventStep;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;

public class ManagedServersUpStep extends Step {
  static final String SERVERS_UP_MSG =
      "Running servers for domain with UID: {0}, running list: {1}";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static NextStepFactory NEXT_STEP_FACTORY =
      (info, config, factory, next) ->
          scaleDownIfNecessary(info, config, factory, new ClusterServicesStep(next));

  public ManagedServersUpStep(Step next) {
    super(next);
  }

  public static Collection<String> getRunningServers(DomainPresenceInfo info) {
    return info.getServerPods().map(PodHelper::getPodServerName).collect(Collectors.toList());
  }

  private static Step scaleDownIfNecessary(
      DomainPresenceInfo info,
      WlsDomainConfig domainTopology,
      ServersUpStepFactory factory,
      Step next) {

    List<Step> steps = new ArrayList<>(Collections.singletonList(next));

    if (info.getDomain().isShuttingDown()) {
      insert(steps, createAvailableHookStep());
      factory.shutdownInfos.add(new ServerShutdownInfo(domainTopology.getAdminServerName(), null));
    }

    List<ServerShutdownInfo> serversToStop = getServersToStop(info, factory.shutdownInfos);

    if (!serversToStop.isEmpty()) {
      insert(steps,
              Step.chain(createProgressingStartedEventStep(info, MANAGED_SERVERS_STARTING_PROGRESS_REASON, true,
                      null), new ServerDownIteratorStep(factory.shutdownInfos, null)));
    }

    return Step.chain(steps.toArray(new Step[0]));
  }

  private static List<ServerShutdownInfo> getServersToStop(
          DomainPresenceInfo info, List<ServerShutdownInfo> shutdownInfos) {
    return shutdownInfos.stream()
            .filter(ssi -> isNotAlreadyStoppedOrServiceOnly(info, ssi)).collect(Collectors.toList());
  }

  private static boolean isNotAlreadyStoppedOrServiceOnly(DomainPresenceInfo info, ServerShutdownInfo ssi) {
    return (info.getServerPod(ssi.getServerName()) != null
            && !info.isServerPodBeingDeleted(ssi.getServerName()))
            || (ssi.isServiceOnly() && info.getServerService(ssi.getServerName()) == null);
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
    boolean isExplicitRecheck = MakeRightDomainOperation.isExplicitRecheck(packet);
    WlsDomainConfig config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);

    ServersUpStepFactory factory = new ServersUpStepFactory(config,
        info.getDomain(), info, isExplicitRecheck);

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine(SERVERS_UP_MSG, factory.domain.getDomainUid(), getRunningServers(info));
    }

    Optional.ofNullable(config).ifPresent(wlsDomainConfig -> addServersToFactory(factory, wlsDomainConfig));

    info.setServerStartupInfo(factory.getStartupInfos());
    info.setServerShutdownInfo(factory.getShutdownInfos());
    LOGGER.exiting();

    return doNext(
        NEXT_STEP_FACTORY.createServerStep(
            info, config, factory, factory.createNextStep(getNext())),
        packet);
  }

  private void addServersToFactory(@Nonnull ServersUpStepFactory factory, @Nonnull WlsDomainConfig wlsDomainConfig) {
    Set<String> clusteredServers = new HashSet<>();

    List<ServerConfig> pendingServers = new ArrayList<>();
    wlsDomainConfig.getClusterConfigs().values()
        .forEach(wlsClusterConfig -> addClusteredServersToFactory(
            factory, clusteredServers, wlsClusterConfig, pendingServers));

    wlsDomainConfig.getServerConfigs().values().stream()
        .filter(wlsServerConfig -> !clusteredServers.contains(wlsServerConfig.getName()))
        .forEach(wlsServerConfig -> factory.addServerIfAlways(wlsServerConfig, null, pendingServers));

    for (ServerConfig serverConfig : pendingServers) {
      factory.addServerIfNeeded(serverConfig.wlsServerConfig, serverConfig.wlsClusterConfig);
    }
  }

  private void addClusteredServersToFactory(
      @Nonnull ServersUpStepFactory factory, Set<String> clusteredServers,
      @Nonnull WlsClusterConfig wlsClusterConfig, List<ServerConfig> pendingServers) {
    factory.logIfInvalidReplicaCount(wlsClusterConfig);
    // We depend on 'getServerConfigs()' returning an ascending 'numero-lexi'
    // sorted list so that a cluster's "lowest named" servers have precedence
    // when the  cluster's replica  count is lower than  the WL cluster size.
    wlsClusterConfig.getServerConfigs()
        .forEach(wlsServerConfig -> {
          factory.addServerIfAlways(wlsServerConfig, wlsClusterConfig, pendingServers);
          clusteredServers.add(wlsServerConfig.getName());
        });
  }

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createServerStep(
        DomainPresenceInfo info, WlsDomainConfig config, ServersUpStepFactory factory, Step next);
  }

  static class ServersUpStepFactory {
    final WlsDomainConfig domainTopology;
    final Domain domain;
    final DomainPresenceInfo info;
    final boolean skipEventCreation;
    List<ServerStartupInfo> startupInfos;
    List<ServerShutdownInfo> shutdownInfos = new ArrayList<>();
    final Collection<String> servers = new ArrayList<>();
    final Collection<String> preCreateServers = new ArrayList<>();
    final Map<String, Integer> replicas = new HashMap<>();
    private Step eventStep;

    ServersUpStepFactory(WlsDomainConfig domainTopology, Domain domain,
        DomainPresenceInfo info, boolean skipEventCreation) {
      this.domainTopology = domainTopology;
      this.domain = domain;
      this.info = info;
      this.skipEventCreation = skipEventCreation;
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

    private void addServerIfNeeded(@Nonnull WlsServerConfig serverConfig, WlsClusterConfig clusterConfig) {
      String serverName = serverConfig.getName();
      if (adminServerOrDone(serverName)) {
        return;
      }

      String clusterName = getClusterName(clusterConfig);
      ServerSpec server = domain.getServer(serverName, clusterName);

      if (server.shouldStart(getReplicaCount(clusterName))) {
        addServerToStart(serverConfig, clusterName, server);
      } else if (shouldPrecreateServerService(server)) {
        preCreateServers.add(serverName);
        addShutdownInfo(new ServerShutdownInfo(serverConfig, clusterName, server, true));
      } else {
        addShutdownInfo(new ServerShutdownInfo(serverConfig, clusterName, server, false));
      }
    }

    private void addServerToStart(@Nonnull WlsServerConfig serverConfig, String clusterName, ServerSpec server) {
      servers.add(serverConfig.getName());
      if (shouldPrecreateServerService(server)) {
        preCreateServers.add(serverConfig.getName());
      }
      addStartupInfo(new ServerStartupInfo(serverConfig, clusterName, server));
      addToCluster(clusterName);
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
      Step nextStep = (servers.isEmpty()) ? next : new ManagedServerUpIteratorStep(getStartupInfos(), next);
      return Optional.ofNullable(eventStep).map(s -> Step.chain(s, nextStep)).orElse(nextStep);
    }

    Collection<ServerStartupInfo> getStartupInfos() {
      if (startupInfos != null) {
        startupInfos.sort(
            comparing((ServerStartupInfo sinfo) -> OperatorUtils.getSortingString(sinfo.getServerName())));
      }
      return startupInfos;
    }

    Collection<DomainPresenceInfo.ServerShutdownInfo> getShutdownInfos() {
      return shutdownInfos;
    }

    private void addStartupInfo(ServerStartupInfo startupInfo) {
      if (startupInfos == null) {
        startupInfos = new ArrayList<>();
      }
      startupInfos.add(startupInfo);
    }

    private void addShutdownInfo(DomainPresenceInfo.ServerShutdownInfo shutdownInfo) {
      if (shutdownInfos == null) {
        shutdownInfos = new ArrayList<>();
      }
      shutdownInfos.add(shutdownInfo);
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
        addValidationErrorEventAndWarning(MessageKeys.REPLICAS_EXCEEDS_TOTAL_CLUSTER_SERVER_COUNT,
            domain.getReplicaCount(clusterName),
            clusterConfig.getMaxDynamicClusterSize(),
            clusterName);
      }
    }

    private void logIfReplicasLessThanClusterServersMin(WlsClusterConfig clusterConfig) {
      if (lessThanMinConfiguredClusterSize(clusterConfig)) {
        String clusterName = clusterConfig.getClusterName();
        addValidationErrorEventAndWarning(MessageKeys.REPLICAS_LESS_THAN_TOTAL_CLUSTER_SERVER_COUNT,
            domain.getReplicaCount(clusterName),
            clusterConfig.getMinDynamicClusterSize(),
            clusterName);

        // Reset current replica count so we don't scale down less than minimum
        // dynamic cluster size
        domain.setReplicaCount(clusterName, clusterConfig.getMinDynamicClusterSize());
      }
    }

    private void addValidationErrorEventAndWarning(String msgId, Object... messageParams) {
      LOGGER.warning(msgId, messageParams);
      String message = LOGGER.formatMessage(msgId, messageParams);
      if (!skipEventCreation) {
        eventStep = createEventStep(new EventData(EventItem.DOMAIN_VALIDATION_ERROR, message));
      }
      info.addValidationWarning(message);
    }

    private boolean lessThanMinConfiguredClusterSize(WlsClusterConfig clusterConfig) {
      if (clusterConfig != null) {
        String clusterName = clusterConfig.getClusterName();
        if (clusterConfig.hasDynamicServers()
            && !domain.isAllowReplicasBelowMinDynClusterSize(clusterName)) {
          int configMinClusterSize = clusterConfig.getMinDynamicClusterSize();
          return domain.getReplicaCount(clusterName) < configMinClusterSize;
        }
      }
      return false;
    }

    private void logIfInvalidReplicaCount(WlsClusterConfig clusterConfig) {
      logIfReplicasExceedsClusterServersMax(clusterConfig);
      logIfReplicasLessThanClusterServersMin(clusterConfig);
    }

    private void addServerIfAlways(
        WlsServerConfig wlsServerConfig,
        WlsClusterConfig wlsClusterConfig,
        List<ServerConfig> pendingServers) {
      String serverName = wlsServerConfig.getName();
      if (adminServerOrDone(serverName)) {
        return;
      }
      String clusterName = getClusterName(wlsClusterConfig);
      ServerSpec server = domain.getServer(serverName, clusterName);
      if (server.alwaysStart()) {
        addServerToStart(wlsServerConfig, clusterName, server);
      } else {
        pendingServers.add(new ServerConfig(wlsClusterConfig, wlsServerConfig));
      }
    }

    private boolean adminServerOrDone(String serverName) {
      return servers.contains(serverName) || serverName.equals(domainTopology.getAdminServerName());
    }

    private static String getClusterName(WlsClusterConfig clusterConfig) {
      return clusterConfig == null ? null : clusterConfig.getClusterName();
    }

  }

  private static class ServerConfig {
    protected final WlsServerConfig wlsServerConfig;
    protected final WlsClusterConfig wlsClusterConfig;

    ServerConfig(WlsClusterConfig cluster, WlsServerConfig server) {
      this.wlsClusterConfig = cluster;
      this.wlsServerConfig = server;
    }
  }
}
