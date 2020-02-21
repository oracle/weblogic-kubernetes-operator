// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.json.Json;
import javax.json.JsonPatchBuilder;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSource;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
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

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
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
@SuppressWarnings("WeakerAccess")
public class DomainStatusUpdater {
  public static final String INSPECTING_DOMAIN_PROGRESS_REASON = "InspectingDomainPresence";
  public static final String MANAGED_SERVERS_STARTING_PROGRESS_REASON = "ManagedServersStarting";
  public static final String SERVERS_READY_REASON = "ServersReady";
  public static final String ALL_STOPPED_AVAILABLE_REASON = "AllServersStopped";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String TRUE = "True";
  private static final String FALSE = "False";

  private DomainStatusUpdater() {
  }

  /**
   * Creates an asynchronous step to update domain status from the topology in the current packet.
   * @param next the next step
   * @return the new step
   */
  static Step createStatusUpdateStep(Step next) {
    return new StatusUpdateStep(next);
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

  /**
   * Asynchronous step to set Domain condition to Failed after an asynchronous call failure.
   *
   * @param callResponse the response from an unrecoverable call
   * @param next Next step
   * @return Step
   */
  public static Step createFailedStep(CallResponse<?> callResponse, Step next) {
    FailureStatusSource failure = UnrecoverableErrorBuilder.fromException(callResponse.getE());
    return createFailedStep(failure.getReason(), failure.getMessage(), next);
  }

  /**
   * Asynchronous step to set Domain condition to Failed.
   *
   * @param throwable Throwable that caused failure
   * @param next Next step
   * @return Step
   */
  static Step createFailedStep(Throwable throwable, Step next) {
    return createFailedStep("Exception", throwable.getMessage(), next);
  }

  /**
   * Asynchronous step to set Domain condition to Failed.
   *
   * @param reason the reason for the failure
   * @param message a fuller description of the problem
   * @param next Next step
   * @return Step
   */
  public static Step createFailedStep(String reason, String message, Step next) {
    return new FailedStep(reason, message, next);
  }

  abstract static class DomainStatusUpdaterStep extends Step {

    DomainStatusUpdaterStep(Step next) {
      super(next);
    }

    DomainStatusUpdaterContext createContext(Packet packet) {
      return new DomainStatusUpdaterContext(packet, this);
    }

    abstract void modifyStatus(DomainStatus domainStatus);

    @Override
    public NextAction apply(Packet packet) {
      DomainStatusUpdaterContext context = createContext(packet);
      DomainStatus newStatus = context.getNewStatus();

      return context.isStatusChanged(newStatus)
            ? doNext(packet)
            : doNext(createDomainStatusPatchStep(context, newStatus), packet);
    }

    private Step createDomainStatusPatchStep(DomainStatusUpdaterContext context, DomainStatus newStatus) {
      JsonPatchBuilder builder = Json.createPatchBuilder();
      newStatus.createPatchFrom(builder, context.getStatus());
      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomainUid(), newStatus);

      return new CallBuilder().patchDomainAsync(
            context.getDomainName(),
            context.getNamespace(),
            new V1Patch(builder.build().toString()),
            createResponseStep(context, getNext()));
    }

    private ResponseStep<Domain> createResponseStep(DomainStatusUpdaterContext context, Step next) {
      return new PatchResponseStep(this, context, next);
    }
  }

  static class PatchResponseStep extends DefaultResponseStep<Domain> {
    private final DomainStatusUpdaterStep updaterStep;
    private final DomainStatusUpdaterContext context;

    public PatchResponseStep(DomainStatusUpdaterStep updaterStep, DomainStatusUpdaterContext context, Step nextStep) {
      super(nextStep);
      this.updaterStep = updaterStep;
      this.context = context;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<Domain> callResponse) {
      if (!isPatchFailure(callResponse)) {
        return super.onFailure(packet, callResponse);
      }

      return doNext(createRetry(context, getNext()), packet);
    }

    public Step createRetry(DomainStatusUpdaterContext context, Step next) {
      return Step.chain(createDomainRefreshStep(context), updaterStep, next);
    }

    private boolean isPatchFailure(CallResponse<Domain> callResponse) {
      return callResponse.getStatusCode() == HTTP_INTERNAL_ERROR;
    }

    private Step createDomainRefreshStep(DomainStatusUpdaterContext context) {
      return new CallBuilder().readDomainAsync(context.getDomainName(), context.getNamespace(), new DomainUpdateStep());
    }
  }

  static class DomainUpdateStep extends ResponseStep<Domain> {
    @Override
    public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
      packet.getSpi(DomainPresenceInfo.class).setDomain(callResponse.getResult());
      return doNext(packet);
    }
  }

  static class DomainStatusUpdaterContext {
    private final DomainPresenceInfo info;
    private final DomainStatusUpdaterStep domainStatusUpdaterStep;

    DomainStatusUpdaterContext(Packet packet, DomainStatusUpdaterStep domainStatusUpdaterStep) {
      info = packet.getSpi(DomainPresenceInfo.class);
      this.domainStatusUpdaterStep = domainStatusUpdaterStep;
    }

    DomainStatus getNewStatus() {
      DomainStatus newStatus = cloneStatus();
      modifyStatus(newStatus);
      return newStatus;
    }

    String getDomainUid() {
      return getDomain().getDomainUid();
    }

    boolean isStatusChanged(DomainStatus newStatus) {
      return newStatus.equals(getStatus());
    }

    private String getNamespace() {
      return getMetadata().getNamespace();
    }

    private V1ObjectMeta getMetadata() {
      return getDomain().getMetadata();
    }

    DomainPresenceInfo getInfo() {
      return info;
    }

    DomainStatus getStatus() {
      return getDomain().getStatus();
    }

    Domain getDomain() {
      return info.getDomain();
    }

    void modifyStatus(DomainStatus status) {
      domainStatusUpdaterStep.modifyStatus(status);
    }

    private String getDomainName() {
      return getMetadata().getName();
    }

    DomainStatus cloneStatus() {
      return Optional.ofNullable(getStatus()).map(DomainStatus::new).orElse(new DomainStatus());
    }
  }

  /**
   * A step which updates the domain status from the domain topology in the current packet.
   */
  private static class StatusUpdateStep extends DomainStatusUpdaterStep {
    StatusUpdateStep(Step next) {
      super(next);
    }

    @Override
    DomainStatusUpdaterContext createContext(Packet packet) {
      return new StatusUpdateContext(packet, this);
    }

    @Override
    void modifyStatus(DomainStatus domainStatus) { // no-op; modification happens in the context itself.
    }

    static class StatusUpdateContext extends DomainStatusUpdaterContext {
      private final WlsDomainConfig config;
      private final Map<String, String> serverState;
      private final Map<String, ServerHealth> serverHealth;

      StatusUpdateContext(Packet packet, StatusUpdateStep statusUpdateStep) {
        super(packet, statusUpdateStep);
        config = packet.getValue(DOMAIN_TOPOLOGY);
        serverState = packet.getValue(SERVER_STATE_MAP);
        serverHealth = packet.getValue(SERVER_HEALTH_MAP);
      }

      @Override
      void modifyStatus(DomainStatus status) {
        if (getDomain() == null) {
          return;
        }
        
        if (getDomainConfig().isPresent()) {
          status.setServers(new ArrayList<>(getServerStatuses().values()));
          status.setClusters(new ArrayList<>(getClusterStatuses().values()));
          status.setReplicas(getReplicaSetting());
        }

        if (isHasFailedPod()) {
          status.addCondition(new DomainCondition(Failed).withStatus(TRUE).withReason("PodFailed"));
        } else if (allIntendedServersRunning()) {
          status.addCondition(new DomainCondition(Available).withStatus(TRUE).withReason(SERVERS_READY_REASON));
        } else if (!status.hasConditionWith(c -> c.hasType(Progressing))) {
          status.addCondition(new DomainCondition(Progressing).withStatus(TRUE)
                .withReason(MANAGED_SERVERS_STARTING_PROGRESS_REASON));
        }
      }

      private boolean allIntendedServersRunning() {
        return getServerStartupInfos()
            .filter(this::shouldBeRunning)
            .map(ServerStartupInfo::getServerName)
            .noneMatch(this::isNotRunning);
      }

      private Stream<ServerStartupInfo> getServerStartupInfos() {
        return Optional.ofNullable(getInfo().getServerStartupInfo()).stream().flatMap(Collection::stream);
      }

      private Optional<WlsDomainConfig> getDomainConfig() {
        return Optional.ofNullable(config).or(this::getScanCacheDomainConfig);
      }

      private Optional<WlsDomainConfig> getScanCacheDomainConfig() {
        DomainPresenceInfo info = getInfo();
        Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUid());
        return Optional.ofNullable(scan).map(Scan::getWlsDomainConfig);
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
            .map(V1Pod::getSpec)
            .map(V1PodSpec::getNodeName)
            .orElse(null);
      }

      private String getClusterName(String serverName) {
        return getDomainConfig()
            .map(c -> c.getClusterName(serverName))
            .orElse(getClusterNameFromPod(serverName));
      }

      private String getClusterNameFromPod(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName))
            .map(V1Pod::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .map(l -> l.get(CLUSTERNAME_LABEL))
            .orElse(null);
      }

      private Collection<String> getServerNames() {
        Set<String> result = new HashSet<>();
        getDomainConfig()
              .ifPresent(config -> {
                result.addAll(config.getServerConfigs().keySet());
                for (WlsClusterConfig cluster : config.getConfiguredClusters()) {
                  Optional.ofNullable(cluster.getDynamicServersConfig())
                        .flatMap(dynamicConfig -> Optional.ofNullable(dynamicConfig.getServerConfigs()))
                        .ifPresent(servers -> servers.forEach(item -> result.add(item.getName())));
                }
              });
        return result;
      }

      private Collection<String> getClusterNames() {
        Set<String> result = new HashSet<>();
        getDomainConfig().ifPresent(config -> result.addAll(config.getClusterConfigs().keySet()));
        return result;
      }

      private Integer getClusterMaximumSize(String clusterName) {
        return getDomainConfig().map(config -> Optional.ofNullable(config.getClusterConfig(clusterName)))
            .map(cluster -> cluster.map(WlsClusterConfig::getMaxClusterSize).orElse(0)).get();
      }
    }
  }

  private static class ProgressingStep extends DomainStatusUpdaterStep {
    private final String reason;
    private final boolean isPreserveAvailable;

    private ProgressingStep(String reason, boolean isPreserveAvailable, Step next) {
      super(next);
      this.reason = reason;
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    void modifyStatus(DomainStatus status) {
      status.addCondition(new DomainCondition(Progressing).withStatus(TRUE).withReason(reason));
      if (!isPreserveAvailable) {
        status.removeConditionIf(c -> c.getType() == Available);
      }
    }
  }

  private static class EndProgressingStep extends DomainStatusUpdaterStep {

    EndProgressingStep(Step next) {
      super(next);
    }

    @Override
    void modifyStatus(DomainStatus status) {
      status.removeConditionIf(
          c -> c.getType() == Progressing && TRUE.equals(c.getStatus()));
    }
  }

  private static class AvailableStep extends DomainStatusUpdaterStep {
    private final String reason;

    private AvailableStep(String reason, Step next) {
      super(next);
      this.reason = reason;
    }

    @Override
    void modifyStatus(DomainStatus status) {
      status.addCondition(new DomainCondition(Available).withStatus(TRUE).withReason(reason));
    }
  }

  private static class FailedStep extends DomainStatusUpdaterStep {
    private final String reason;
    private final String message;

    private FailedStep(String reason, String message, Step next) {
      super(next);
      this.reason = reason;
      this.message = message;
    }

    @Override
    void modifyStatus(DomainStatus s) {
      s.addCondition(new DomainCondition(Failed).withStatus(TRUE).withReason(reason).withMessage(message));
      if (s.hasConditionWith(c -> c.hasType(Progressing))) {
        s.addCondition(new DomainCondition(Progressing).withStatus(FALSE));
      }
    }
  }
}
