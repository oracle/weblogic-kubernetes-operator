// Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;

/**
 * Updates for status of Domain. This class has two modes: 1) Watching for Pod state changes by
 * listening to events from {@link PodWatcher} and 2) Factory for {@link Step}s that the main
 * processing flow can use to explicitly set the condition to Progressing or Failed.
 */
public class DomainStatusUpdater {
  public static final String INSPECTING_DOMAIN_PROGRESS_REASON = "InspectingDomainPrescence";
  public static final String MANAGED_SERVERS_STARTING_PROGRESS_REASON = "ManagedServersStarting";
  public static final String SERVERS_READY_REASON = "ServersReady";
  public static final String ALL_STOPPED_AVAILABLE_REASON = "AllServersStopped";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String TRUE = "True";
  private static final String FALSE = "False";

  private DomainStatusUpdater() {
  }

  /**
   * Asynchronous step to set Domain status to indicate WebLogic server status.
   *
   * @param timeoutSeconds Timeout in seconds
   * @param next Next step
   * @return Step
   */
  @SuppressWarnings("SameParameterValue")
  static Step createStatusStep(int timeoutSeconds, Step next) {
    return new StatusUpdateHookStep(timeoutSeconds, next);
  }

  /**
   * Asynchronous step to set Domain condition to Progressing.
   *
   * @param reason Progressing reason
   * @param isPreserveAvailable true, if existing Available=True condition should be preserved
   * @param next Next step
   * @return Step
   */
  public static Step createProgressingStep(String reason, boolean isPreserveAvailable, Step next) {
    return new ProgressingStep(reason, isPreserveAvailable, next);
  }

  /**
   * Asynchronous step to set Domain condition end Progressing and set Available, if needed.
   *
   * @param next Next step
   * @return Step
   */
  static Step createEndProgressingStep(Step next) {
    return new EndProgressingStep(next);
  }

  /**
   * Asynchronous step to set Domain condition to Available.
   *
   * @param reason Available reason
   * @param next Next step
   * @return Step
   */
  public static Step createAvailableStep(String reason, Step next) {
    return new AvailableStep(reason, next);
  }

  private static NextAction doDomainUpdate(
      Domain dom, DomainPresenceInfo info, Packet packet, Step conflictStep, Step next) {
    V1ObjectMeta meta = dom.getMetadata();
    NextAction na = new NextAction();

    // *NOTE* See the note in KubernetesVersion
    // If we update the CrdHelper to include the status subresource, then this code
    // needs to be modified to use replaceDomainStatusAsync.  Then, validate if onSuccess
    // should update info.

    na.invoke(
        new CallBuilder()
            .replaceDomainAsync(
                meta.getName(),
                meta.getNamespace(),
                dom,
                new DefaultResponseStep<Domain>(next) {
                  @Override
                  public NextAction onFailure(Packet packet, CallResponse<Domain> callResponse) {
                    if (callResponse.getStatusCode() == CallBuilder.NOT_FOUND) {
                      return doNext(packet); // Just ignore update
                    }
                    return super.onFailure(
                        getRereadDomainConflictStep(info, meta, conflictStep),
                        packet,
                        callResponse);
                  }

                  @Override
                  public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
                    // Update info only if using replaceDomain
                    // Skip, if we switch to using replaceDomainStatus
                    info.setDomain(callResponse.getResult());
                    return doNext(packet);
                  }
                }),
        packet);
    return na;
  }

  private static Step getRereadDomainConflictStep(
      DomainPresenceInfo info, V1ObjectMeta meta, Step next) {
    return new CallBuilder()
        .readDomainAsync(
            meta.getName(),
            meta.getNamespace(),
            new DefaultResponseStep<Domain>(next) {
              @Override
              public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
                info.setDomain(callResponse.getResult());
                return doNext(packet);
              }
            });
  }

  /**
   * Asynchronous step to set Domain condition to Failed.
   *
   * @param throwable Throwable that caused failure
   * @param next Next step
   * @return Step
   */
  static Step createFailedStep(Throwable throwable, Step next) {
    return new FailedStep(throwable, next);
  }

  static class DomainConditionStepContext {
    private final DomainPresenceInfo info;

    DomainConditionStepContext(Packet packet) {
      info = packet.getSpi(DomainPresenceInfo.class);
    }

    DomainPresenceInfo getInfo() {
      return info;
    }

    DomainStatus getStatus() {
      return getDomain().getOrCreateStatus();
    }

    Domain getDomain() {
      return info.getDomain();
    }
  }

  private static class StatusUpdateHookStep extends Step {
    private final int timeoutSeconds;

    StatusUpdateHookStep(int timeoutSeconds, Step next) {
      super(next);
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return doNext(
          ServerStatusReader.createDomainStatusReaderStep(
              info, timeoutSeconds, new StatusUpdateStep(getNext())),
          packet);
    }
  }

  static class StatusUpdateStep extends Step {
    StatusUpdateStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      final StatusUpdateContext context = new StatusUpdateContext(packet);

      DomainStatus status = context.getStatus();

      boolean isStatusModified =
          modifyDomainStatus(
              status,
              s -> {
                if (context.getDomain() != null) {
                  if (context.getDomainConfig().isPresent()) {
                    s.setServers(new ArrayList<>(context.getServerStatuses().values()));
                    s.setClusters(new ArrayList<>(context.getClusterStatuses().values()));
                    s.setReplicas(context.getReplicaSetting());
                  }

                  if (context.isHasFailedPod()) {
                    s.removeConditionIf(c -> c.getType() == Available);
                    s.removeConditionIf(c -> c.getType() == Progressing);
                    s.addCondition(
                        new DomainCondition(Failed).withStatus(TRUE).withReason("PodFailed"));
                  } else {
                    s.removeConditionIf(c -> c.getType() == Failed);
                    if (context.allIntendedServersRunning()) {
                      s.removeConditionIf(c -> c.getType() == Progressing);
                      s.addCondition(
                          new DomainCondition(Available)
                              .withStatus(TRUE)
                              .withReason(SERVERS_READY_REASON));
                    }
                  }
                }
              });

      if (isStatusModified) {
        LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getInfo().getDomainUid(), status);
      }
      LOGGER.exiting();

      return isStatusModified
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, StatusUpdateStep.this, getNext())
          : doNext(packet);
    }

    static class StatusUpdateContext extends DomainConditionStepContext {
      private final WlsDomainConfig config;
      private final Map<String, String> serverState;
      private final Map<String, ServerHealth> serverHealth;

      StatusUpdateContext(Packet packet) {
        super(packet);
        config = packet.getValue(DOMAIN_TOPOLOGY);
        serverState = packet.getValue(SERVER_STATE_MAP);
        serverHealth = packet.getValue(SERVER_HEALTH_MAP);
      }

      private boolean allIntendedServersRunning() {
        return getServerStartupInfos()
            .filter(this::shouldBeRunning)
            .map(ServerStartupInfo::getServerName)
            .noneMatch(this::isNotRunning);
      }

      private Stream<ServerStartupInfo> getServerStartupInfos() {
        return Optional.ofNullable(getInfo().getServerStartupInfo())
            .map(Collection::stream)
            .orElse(Stream.empty());
      }

      private Optional<WlsDomainConfig> getDomainConfig() {
        return Optional.ofNullable(config).or(this::getScanCacheDomainConfig);
      }

      private Optional<WlsDomainConfig> getScanCacheDomainConfig() {
        DomainPresenceInfo info = getInfo();
        Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUid());
        return Optional.ofNullable(scan).map(s -> s.getWlsDomainConfig());
      }

      private boolean shouldBeRunning(ServerStartupInfo startupInfo) {
        return !startupInfo.isServiceOnly() && RUNNING_STATE.equals(startupInfo.getDesiredState());
      }

      private boolean isNotRunning(@Nonnull String serverName) {
        return !RUNNING_STATE.equals(getRunningState(serverName));
      }

      private boolean isHasFailedPod() {
        return getInfo().getServerPods().anyMatch(PodHelper::isFailed);
      }

      private boolean hasServerPod(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName)).isPresent();
      }

      private boolean hasReadyServerPod(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName)).filter(PodHelper::getReadyStatus).isPresent();
      }

      Map<String, ServerStatus> getServerStatuses() {
        return getServerNames().stream()
            .collect(Collectors.toMap(Function.identity(), this::createServerStatus));
      }

      private ServerStatus createServerStatus(String serverName) {
        return new ServerStatus()
            .withServerName(serverName)
            .withState(getRunningState(serverName))
            .withHealth(serverHealth.get(serverName))
            .withClusterName(getClusterName(serverName))
            .withNodeName(getNodeName(serverName));
      }

      private String getRunningState(String serverName) {
        return serverState.getOrDefault(serverName, SHUTDOWN_STATE);
      }

      Integer getReplicaSetting() {
        Collection<Long> values = getClusterCounts().values();
        if (values.size() == 1) {
          return values.iterator().next().intValue();
        } else {
          return null;
        }
      }

      private Stream<String> getServers(boolean isReadyOnly) {
        return getServerNames().stream()
            .filter(isReadyOnly ? this::hasReadyServerPod : this::hasServerPod);
      }

      private Map<String, Long> getClusterCounts() {
        return getClusterCounts(false);
      }

      private Map<String, Long> getClusterCounts(boolean isReadyOnly) {
        return getServers(isReadyOnly)
            .map(this::getClusterNameFromPod)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
      }

      Map<String, ClusterStatus> getClusterStatuses() {
        return getClusterNames().stream()
            .collect(Collectors.toMap(Function.identity(), this::createClusterStatus));
      }

      private ClusterStatus createClusterStatus(String clusterName) {
        return new ClusterStatus()
            .withClusterName(clusterName)
            .withReplicas(Optional.ofNullable(getClusterCounts().get(clusterName)).map(Long::intValue).orElse(null))
            .withReadyReplicas(
                Optional.ofNullable(getClusterCounts(true).get(clusterName)).map(Long::intValue).orElse(null))
            .withMaximumReplicas(getClusterMaximumSize(clusterName));
      }


      private String getNodeName(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName))
            .map(p -> p.getSpec().getNodeName())
            .orElse(null);
      }

      private String getClusterName(String serverName) {
        return getDomainConfig()
            .map(c -> c.getClusterName(serverName))
            .orElse(getClusterNameFromPod(serverName));
      }

      private String getClusterNameFromPod(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName))
            .map(p -> p.getMetadata().getLabels().get(CLUSTERNAME_LABEL))
            .orElse(null);
      }

      private Collection<String> getServerNames() {
        Set<String> result = new HashSet<>();
        getDomainConfig().stream().forEach(config -> {
          result.addAll(config.getServerConfigs().keySet());
          for (WlsClusterConfig cluster : config.getConfiguredClusters()) {
            Optional.ofNullable(cluster.getDynamicServersConfig())
                .ifPresent(dynamicConfig -> Optional.ofNullable(dynamicConfig.getServerConfigs())
                    .ifPresent(servers -> servers.stream().forEach(item -> result.add(item.getName()))));
          }
        });
        return result;
      }

      private Collection<String> getClusterNames() {
        Set<String> result = new HashSet<>();
        getDomainConfig().stream().forEach(config -> result.addAll(config.getClusterConfigs().keySet()));
        return result;
      }

      private Integer getClusterMaximumSize(String clusterName) {
        return getDomainConfig().map(config -> Optional.ofNullable(config.getClusterConfig(clusterName)))
            .map(cluster -> cluster.map(c -> c.getMaxClusterSize()).orElse(0)).get();
      }
    }
  }

  private static class ProgressingStep extends Step {
    private final String reason;
    private final boolean isPreserveAvailable;

    private ProgressingStep(String reason, boolean isPreserveAvailable, Step next) {
      super(next);
      this.reason = reason;
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      DomainStatus status = context.getStatus();

      boolean isStatusModified =
          modifyDomainStatus(
              status,
              s -> {
                s.addCondition(
                    new DomainCondition(Progressing).withStatus(TRUE).withReason(reason));
                s.removeConditionIf(c -> c.getType() == Failed);
                if (!isPreserveAvailable) {
                  s.removeConditionIf(c -> c.getType() == Available);
                }
              });

      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUid(), status);
      LOGGER.exiting();

      return isStatusModified
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, ProgressingStep.this, getNext())
          : doNext(packet);
    }
  }

  private static class EndProgressingStep extends Step {

    EndProgressingStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      DomainStatus status = context.getStatus();

      boolean isStatusModified =
          modifyDomainStatus(
              status,
              s ->
                  s.removeConditionIf(
                      c -> c.getType() == Progressing && TRUE.equals(c.getStatus())));

      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUid(), status);
      LOGGER.exiting();

      return isStatusModified
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, EndProgressingStep.this, getNext())
          : doNext(packet);
    }
  }

  private static class AvailableStep extends Step {
    private final String reason;

    private AvailableStep(String reason, Step next) {
      super(next);
      this.reason = reason;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      DomainStatus status = context.getStatus();

      boolean isStatusModified =
          modifyDomainStatus(
              status,
              s -> {
                s.addCondition(new DomainCondition(Available).withStatus(TRUE).withReason(reason));
                s.removeConditionIf(c -> c.getType() == Failed);
              });

      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUid(), status);
      LOGGER.exiting();

      return isStatusModified
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, AvailableStep.this, getNext())
          : doNext(packet);
    }
  }

  private static boolean modifyDomainStatus(DomainStatus domainStatus, Consumer<DomainStatus> statusUpdateConsumer) {
    final DomainStatus currentStatus = new DomainStatus(domainStatus);
    synchronized (domainStatus) {
      statusUpdateConsumer.accept(domainStatus);
      return !domainStatus.equals(currentStatus);
    }
  }

  private static class FailedStep extends Step {
    private final Throwable throwable;

    private FailedStep(Throwable throwable, Step next) {
      super(next);
      this.throwable = throwable;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      final DomainStatus status = context.getStatus();

      boolean isStatusModified =
          modifyDomainStatus(
              status,
              s -> {
                s.addCondition(
                    new DomainCondition(Failed)
                        .withStatus(TRUE)
                        .withReason("Exception")
                        .withMessage(throwable.getMessage()));
                if (s.hasConditionWith(c -> c.hasType(Progressing))) {
                  s.addCondition(new DomainCondition(Progressing).withStatus(FALSE));
                }
              });


      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUid(), status);
      LOGGER.exiting();

      return isStatusModified
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, FailedStep.this, getNext())
          : doNext(packet);
    }
  }
}
