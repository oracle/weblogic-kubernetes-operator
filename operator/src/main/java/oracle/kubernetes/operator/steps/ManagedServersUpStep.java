// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.DomainStatusUpdater.ClearCompletedConditionSteps;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerShutdownInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.OperatorUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static java.util.Comparator.comparing;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodServerName;

public class ManagedServersUpStep extends Step {
  static final String SERVERS_UP_MSG =
      "Running servers for domain with UID: {0}, running list: {1}";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static NextStepFactory nextStepFactory =
      (info, config, factory, next) ->
          scaleDownIfNecessary(info, config, factory, new ClusterServicesStep(next));

  public ManagedServersUpStep(Step next) {
    super(next);
  }

  public static Collection<String> getRunningServers(DomainPresenceInfo info) {
    return info.getServerPods().map(PodHelper::getPodServerName).toList();
  }

  private static Step scaleDownIfNecessary(
      DomainPresenceInfo info,
      WlsDomainConfig domainTopology,
      ServersUpStepFactory factory,
      Step next) {

    List<Step> steps = new ArrayList<>(Collections.singletonList(next));

    if (info.getDomain().isShuttingDown()) {
      Optional.ofNullable(domainTopology).ifPresent(
          wlsDomainConfig ->
              factory.shutdownInfos.add(new ServerShutdownInfo(wlsDomainConfig.getAdminServerName(), null)));
    }

    List<ServerShutdownInfo> serversToStop = getServersToStop(info, factory.shutdownInfos);

    if (!serversToStop.isEmpty()) {
      insert(steps, new ServerDownIteratorStep(factory.shutdownInfos, null));
    }

    if (hasWorkToDo(steps, next)) {
      insert(steps, new ClearCompletedConditionSteps());
    }
    return Step.chain(steps.toArray(new Step[0]));
  }

  private static boolean hasWorkToDo(List<Step> steps, Step next) {
    return getNonNullStepNum(steps) > getExpectedNumber(next);
  }

  private static int getExpectedNumber(Step next) {
    return next == null ? 0 : 1;
  }

  private static boolean isNotNull(Step step) {
    return step != null;
  }

  private static int getNonNullStepNum(List<Step> steps) {
    return (int) steps.stream().filter(ManagedServersUpStep::isNotNull).count();
  }

  private static List<ServerShutdownInfo> getServersToStop(
          DomainPresenceInfo info, List<ServerShutdownInfo> shutdownInfos) {
    return shutdownInfos.stream()
            .filter(ssi -> isNotAlreadyStoppedOrServiceOnly(info, ssi)).toList();
  }

  private static boolean isNotAlreadyStoppedOrServiceOnly(DomainPresenceInfo info, ServerShutdownInfo ssi) {
    return (info.getServerPod(ssi.getServerName()) != null
            && !info.isServerPodBeingDeleted(ssi.getServerName()))
            || (ssi.isServiceOnly() && (info.getServerService(ssi.getServerName()) == null)
            || (!ssi.isServiceOnly() && (info.getServerService(ssi.getServerName()) != null)));
  }

  private static void insert(List<Step> steps, Step step) {
    steps.add(0, step);
  }

  @Override
  public NextAction apply(Packet packet) {
    LOGGER.entering();
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    WlsDomainConfig config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);

    ServersUpStepFactory factory = new ServersUpStepFactory(config, info);

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine(SERVERS_UP_MSG, factory.domain.getDomainUid(), getRunningServers(info));
    }

    Optional.ofNullable(config).ifPresent(wlsDomainConfig -> addServersToFactory(factory, wlsDomainConfig, info));

    info.setServerStartupInfo(factory.getStartupInfos());
    info.setServerShutdownInfo(factory.getShutdownInfos());

    LOGGER.exiting();

    return doNext(
        nextStepFactory.createServerStep(
            info, config, factory, factory.createNextStep(getNext())),
        packet);
  }

  private void addServersToFactory(@Nonnull ServersUpStepFactory factory, @Nonnull WlsDomainConfig wlsDomainConfig,
                                   DomainPresenceInfo info) {
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

    info.getServerPods().filter(pod -> podShouldNotBeRunning(pod, factory))
            .filter(pod -> podNotAlreadyMarkedForShutdown(pod, factory))
            .filter(pod -> !getPodServerName(pod).equals(wlsDomainConfig.getAdminServerName()))
            .forEach(pod -> shutdownServersNotPresentInDomainConfig(factory, pod));
  }

  private boolean podShouldNotBeRunning(V1Pod pod, ServersUpStepFactory factory) {
    return !factory.getServers().contains(getPodServerName(pod));
  }

  private boolean podNotAlreadyMarkedForShutdown(V1Pod pod, ServersUpStepFactory factory) {
    return factory.getShutdownInfos().stream().noneMatch(ssi -> ssi.getServerName().equals(getPodServerName(pod)));
  }

  private void shutdownServersNotPresentInDomainConfig(ServersUpStepFactory factory, V1Pod pod) {
    WlsServerConfig serverConfig = new WlsServerConfig(getPodServerName(pod), PodHelper.getPodName(pod), 0);
    factory.addShutdownInfo(new ServerShutdownInfo(serverConfig, PodHelper.getPodClusterName(pod), null, false));
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
    final DomainResource domain;
    final DomainPresenceInfo info;
    List<ServerStartupInfo> startupInfos = new ArrayList<>();
    List<ServerShutdownInfo> shutdownInfos = new ArrayList<>();
    final Collection<String> servers = new ArrayList<>();
    final Collection<String> preCreateServers = new ArrayList<>();
    final Map<String, Integer> replicas = new HashMap<>();

    ServersUpStepFactory(WlsDomainConfig domainTopology, DomainPresenceInfo info) {
      this.domainTopology = domainTopology;
      this.domain = info.getDomain();
      this.info = info;
    }

    /**
     * Checks whether we should pre-create server service for the given server.
     *
     * @param server ServerSpec for the managed server
     * @return True if we should pre-create server service for the given managed server, false
     *         otherwise.
     */
    boolean shouldPrecreateServerService(EffectiveServerSpec server) {
      if (Boolean.TRUE.equals(server.isPrecreateServerService())) {
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
      EffectiveServerSpec server = info.getServer(serverName, clusterName);

      if (server.shouldStart(getReplicaCount(clusterName))) {
        addServerToStart(serverConfig, clusterName, server);
      } else if (shouldPrecreateServerService(server)) {
        preCreateServers.add(serverName);
        addShutdownInfo(new ServerShutdownInfo(serverConfig, clusterName, server, true));
      } else {
        addShutdownInfo(new ServerShutdownInfo(serverConfig, clusterName, server, false));
      }
    }

    private void addServerToStart(@Nonnull WlsServerConfig serverConfig, String clusterName,
                                  EffectiveServerSpec server) {
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
        int configMaxClusterSize = clusterConfig.getClusterSize();
        return clusterConfig.hasDynamicServers()
            && clusterConfig.getServerConfigs().size() == configMaxClusterSize
            && info.getReplicaCount(clusterName) > configMaxClusterSize;
      }
      return false;
    }

    private Step createNextStep(Step next) {
      return  (servers.isEmpty()) ? next : new ManagedServerUpIteratorStep(getStartupInfos(), next);
    }

    Collection<ServerStartupInfo> getStartupInfos() {
      startupInfos.sort(
          comparing((ServerStartupInfo sinfo) -> OperatorUtils.getSortingString(sinfo.getServerName())));
      return startupInfos;
    }

    Collection<DomainPresenceInfo.ServerShutdownInfo> getShutdownInfos() {
      return shutdownInfos;
    }

    Collection<String> getServers() {
      return servers;
    }

    private void addStartupInfo(ServerStartupInfo startupInfo) {
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
        addReplicasTooHighValidationErrorWarning(
            info.getReplicaCount(clusterName),
            clusterConfig.getClusterSize(),
            clusterName);
      }
    }

    private void addReplicasTooHighValidationErrorWarning(Object... messageParams) {
      LOGGER.warning(MessageKeys.REPLICAS_EXCEEDS_TOTAL_CLUSTER_SERVER_COUNT, messageParams);
    }

    private void logIfInvalidReplicaCount(WlsClusterConfig clusterConfig) {
      logIfReplicasExceedsClusterServersMax(clusterConfig);
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
      EffectiveServerSpec server = info.getServer(serverName, clusterName);
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
