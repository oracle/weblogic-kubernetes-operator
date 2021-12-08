// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSource;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
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
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.Model;
import oracle.kubernetes.weblogic.domain.model.OnlineUpdate;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static oracle.kubernetes.operator.DomainFailureReason.Internal;
import static oracle.kubernetes.operator.DomainFailureReason.Kubernetes;
import static oracle.kubernetes.operator.DomainFailureReason.ReplicasTooHigh;
import static oracle.kubernetes.operator.DomainFailureReason.ServerPod;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.MIINonDynamicChangesMethod.CommitUpdateOnly;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG;
import static oracle.kubernetes.operator.ProcessingConstants.INTROSPECTION_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTTING_DOWN_STATE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_COMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_FATAL_ERROR;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_MAX_ERRORS_EXCEEDED;
import static oracle.kubernetes.operator.logging.MessageKeys.TOO_MANY_REPLICAS_FAILURE;
import static oracle.kubernetes.utils.OperatorUtils.onSeparateLines;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.TRUE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Completed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ConfigChangesPendingRestart;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;

/**
 * Updates for status of Domain. This class has two modes: 1) Watching for Pod state changes by
 * listening to events from {@link PodWatcher} and 2) Factory for {@link Step}s that the main
 * processing flow can use to explicitly set the condition.
 */
@SuppressWarnings("WeakerAccess")
public class DomainStatusUpdater {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private DomainStatusUpdater() {
  }

  /**
   * Creates an asynchronous step to update domain status from the topology in the current packet.
   * @param next the next step
   * @return the new step
   */
  public static Step createStatusUpdateStep(Step next) {
    return new StatusUpdateStep(next);
  }

  /**
   * Creates an asynchronous step to initialize the domain status, if needed, to indicate that the operator has
   * seen the domain and is now working on it.
   */
  public static Step createStatusInitializationStep() {
    return new StatusInitializationStep();
  }

  /**
   * Asynchronous step to remove any current failure conditions.
   */
  public static Step createRemoveFailuresStep() {
    return new RemoveFailuresStep();
  }

  /**
   * Asynchronous steps to set Domain condition to Failed after an asynchronous call failure
   * and to generate DOMAIN_PROCESSING_FAILED event.
   *
   * @param callResponse the response from an unrecoverable call
   */
  public static Step createFailureRelatedSteps(CallResponse<?> callResponse) {
    FailureStatusSource failure = UnrecoverableErrorBuilder.fromFailedCall(callResponse);

    LOGGER.severe(MessageKeys.CALL_FAILED, failure.getMessage(), failure.getReason());
    ApiException apiException = callResponse.getE();
    if (apiException != null) {
      LOGGER.fine(MessageKeys.EXCEPTION, apiException);
    }

    return createFailureRelatedSteps(Kubernetes, failure.getMessage());
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_PROCESSING_FAILED event.
   *
   * @param throwable Throwable that caused failure
   */
  static Step createFailureRelatedSteps(Throwable throwable) {
    return throwable.getMessage() == null ? createFailureRelatedSteps(Internal, throwable.toString())
        : createFailureRelatedSteps(Internal, throwable.getMessage());
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_PROCESSING_FAILED event.
   *
   * @param reason the failure category
   * @param message a fuller description of the problem
   */
  public static Step createFailureRelatedSteps(@Nonnull DomainFailureReason reason, String message) {
    return Step.chain(
        new FailedStep(reason, message, null),
        createEventStep(new EventData(DOMAIN_PROCESSING_FAILED, getEventMessage(reason, message))));
  }

  /**
   * Asynchronous steps to set Domain condition to Failed, increment the introspector failure count if needed
   * and to generate DOMAIN_PROCESSING_FAILED event.
   *
   * @param reason the failure category
   * @param message a fuller description of the problem
   * @param domainIntrospectorJob Domain introspector job
   */
  public static Step createIntrospectionFailureRelatedSteps(@Nonnull DomainFailureReason reason, String message,
                                                            V1Job domainIntrospectorJob) {
    return Step.chain(
            new FailedStep(reason, message, null),
            new FailureCountStep(domainIntrospectorJob),
            createEventStep(new EventData(DOMAIN_PROCESSING_FAILED, getEventMessage(reason, message))));
  }

  public static Step createFailureCountStep(V1Job domainIntrospectorJob) {
    return new FailureCountStep(domainIntrospectorJob);
  }

  static class FailureCountStep extends DomainStatusUpdaterStep {

    private final V1Job domainIntrospectorJob;

    public FailureCountStep(V1Job domainIntrospectorJob) {
      super(null);
      this.domainIntrospectorJob = domainIntrospectorJob;
    }

    @Override
    void modifyStatus(DomainStatus domainStatus) {
      domainStatus.incrementIntrospectJobFailureCount(getJobUid());
      addRetryInfoToStatusMessage(domainStatus);
    }

    @Nullable
    private String getJobUid() {
      return Optional.ofNullable(domainIntrospectorJob).map(V1Job::getMetadata).map(V1ObjectMeta::getUid).orElse(null);
    }

    private void addRetryInfoToStatusMessage(DomainStatus domainStatus) {

      if (hasExceededMaxRetryCount(domainStatus)) {
        domainStatus.setMessage(createStatusMessage(domainStatus, exceededMaxRetryCountErrorMessage()));
      } else if (isFatalError(domainStatus)) {
        domainStatus.setMessage(createStatusMessage(domainStatus, LOGGER.formatMessage(DOMAIN_FATAL_ERROR)));
      } else {
        domainStatus.setMessage(createStatusMessage(domainStatus, getNonFatalRetryStatusMessage(domainStatus)));
      }
    }

    private boolean hasExceededMaxRetryCount(DomainStatus domainStatus) {
      return domainStatus.getIntrospectJobFailureCount() >= getFailureRetryMaxCount();
    }

    private String createStatusMessage(DomainStatus domainStatus, String retryStatusMessage) {
      return onSeparateLines(retryStatusMessage, INTROSPECTION_ERROR, domainStatus.getMessage());
    }

    private String getNonFatalRetryStatusMessage(DomainStatus domainStatus) {
      return LOGGER.formatMessage(MessageKeys.NON_FATAL_INTROSPECTOR_ERROR,
              domainStatus.getIntrospectJobFailureCount(), getFailureRetryMaxCount());
    }

    private boolean isFatalError(DomainStatus domainStatus) {
      return Optional.ofNullable(domainStatus.getMessage())
              .map(m -> m.contains(FATAL_INTROSPECTOR_ERROR)).orElse(false);
    }

    private String exceededMaxRetryCountErrorMessage() {
      return LOGGER.formatMessage(INTROSPECTOR_MAX_ERRORS_EXCEEDED, getFailureRetryMaxCount());
    }
  }

  private static int getFailureRetryMaxCount() {
    return DomainPresence.getDomainPresenceFailureRetryMaxCount();
  }

  static class ResetFailureCountStep extends DomainStatusUpdaterStep {

    @Override
    void modifyStatus(DomainStatus domainStatus) {
      domainStatus.resetIntrospectJobFailureCount();
    }
  }

  public static Step createResetFailureCountStep() {
    return new ResetFailureCountStep();
  }

  private static String getEventMessage(@Nonnull DomainFailureReason reason, String message) {
    return !StringUtils.isBlank(message) ? message : reason.toString();
  }

  abstract static class DomainStatusUpdaterStep extends Step {

    DomainStatusUpdaterStep() {
    }

    DomainStatusUpdaterStep(Step next) {
      super(next);
    }

    DomainStatusUpdaterContext createContext(Packet packet, Step retryStep) {
      return new DomainStatusUpdaterContext(packet, this);
    }

    abstract void modifyStatus(DomainStatus domainStatus);

    @Override
    public NextAction apply(Packet packet) {
      DomainStatusUpdaterContext context = createContext(packet, this);

      return context.isStatusUnchanged()
          ? doNext(packet)
          : doNext(context.createUpdateSteps(),
          packet);
    }

    private ResponseStep<Domain> createResponseStep(DomainStatusUpdaterContext context) {
      return new StatusReplaceResponseStep(this, context, getNext());
    }
  }

  static class StatusReplaceResponseStep extends DefaultResponseStep<Domain> {
    private final DomainStatusUpdaterStep updaterStep;
    private final DomainStatusUpdaterContext context;

    public StatusReplaceResponseStep(DomainStatusUpdaterStep updaterStep,
                                     DomainStatusUpdaterContext context, Step nextStep) {
      super(nextStep);
      this.updaterStep = updaterStep;
      this.context = context;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
      if (callResponse.getResult() != null) {
        packet.getSpi(DomainPresenceInfo.class).setDomain(callResponse.getResult());
      }
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<Domain> callResponse) {
      if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
        return super.onFailure(packet, callResponse);
      } else {
        return onFailure(createRetry(context), packet, callResponse);
      }
    }

    public Step createRetry(DomainStatusUpdaterContext context) {
      return Step.chain(createDomainRefreshStep(context), updaterStep);
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
    @Nonnull
    private final DomainPresenceInfo info;
    private final DomainStatusUpdaterStep domainStatusUpdaterStep;

    DomainStatusUpdaterContext(Packet packet, DomainStatusUpdaterStep domainStatusUpdaterStep) {
      info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      this.domainStatusUpdaterStep = domainStatusUpdaterStep;
    }

    DomainStatus getNewStatus() {
      DomainStatus newStatus = cloneStatus();
      modifyStatus(newStatus);

      if (newStatus.getMessage() == null) {
        newStatus.setMessage(info.getValidationWarningsAsString());
      }

      return newStatus;
    }

    String getDomainUid() {
      return getDomain().getDomainUid();
    }

    boolean isStatusUnchanged() {
      return getDomain() == null || getNewStatus().equals(getStatus());
    }

    private String getNamespace() {
      return getMetadata().getNamespace();
    }

    private V1ObjectMeta getMetadata() {
      return getDomain().getMetadata();
    }

    @NotNull
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

    private Step createDomainStatusReplaceStep() {
      LOGGER.fine(MessageKeys.DOMAIN_STATUS, getDomainUid(), getNewStatus());
      if (LOGGER.isFinerEnabled()) {
        LOGGER.finer("status change: " + createPatchString());
      }
      Domain oldDomain = getDomain();
      Domain newDomain = new Domain()
          .withKind(KubernetesConstants.DOMAIN)
          .withApiVersion(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE)
          .withMetadata(oldDomain.getMetadata())
          .withSpec(null)
          .withStatus(getNewStatus());

      return new CallBuilder().replaceDomainStatusAsync(
            getDomainName(),
            getNamespace(),
            newDomain,
            domainStatusUpdaterStep.createResponseStep(this));
    }

    private String createPatchString() {
      JsonPatchBuilder builder = Json.createPatchBuilder();
      getNewStatus().createPatchFrom(builder, getStatus());
      return builder.build().toString();
    }

    private Step createUpdateSteps() {
      final Step next = createDomainStatusReplaceStep();
      List<EventData> eventDataList = createDomainEvents();
      return eventDataList.isEmpty() ? next : Step.chain(createEventSteps(eventDataList), next);
    }

    private Step createEventSteps(List<EventData> eventDataList) {
      return Step.chain(
          eventDataList.stream()
              .sorted(Comparator.comparing(EventData::getItem))
              .map(EventHelper::createEventStep)
              .toArray(Step[]::new)
      );
    }

    @Nonnull
    List<EventData> createDomainEvents() {
      List<EventData> list = new ArrayList<>();
      if (hasJustExceededMaxRetryCount()) {
        list.add(new EventData(DOMAIN_PROCESSING_ABORTED).message(INTROSPECTOR_MAX_ERRORS_EXCEEDED));
      } else if (hasJustGotFatalIntrospectorError()) {
        list.add(new EventData(DOMAIN_PROCESSING_ABORTED)
              .message(FATAL_INTROSPECTOR_ERROR_MSG + getNewStatus().getMessage()));
      }
      return list;
    }

    private boolean hasJustGotFatalIntrospectorError() {
      return isFatalIntrospectorMessage(getNewStatus().getMessage())
            && !isFatalIntrospectorMessage(getStatus().getMessage());
    }

    private boolean isFatalIntrospectorMessage(String statusMessage) {
      return statusMessage != null && statusMessage.contains(FATAL_INTROSPECTOR_ERROR);
    }

    private boolean hasJustExceededMaxRetryCount() {
      return getStatus() != null
          && getNewStatus().getIntrospectJobFailureCount() == (getStatus().getIntrospectJobFailureCount() + 1)
          && getNewStatus().getIntrospectJobFailureCount() >= getFailureRetryMaxCount();
    }

  }

  public static class StatusInitializationStep extends DomainStatusUpdaterStep {

    @Override
    void modifyStatus(DomainStatus status) {
      if (status.getConditions().isEmpty()) {
        status.addCondition(new DomainCondition(Completed).withStatus(false));
        status.addCondition(new DomainCondition(Available).withStatus(false));
      }
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
    DomainStatusUpdaterContext createContext(Packet packet, Step retryStep) {
      return new StatusUpdateContext(packet, this);
    }

    @Override
    void modifyStatus(DomainStatus domainStatus) { // no-op; modification happens in the context itself.
    }

    static class StatusUpdateContext extends DomainStatusUpdaterContext {
      private final WlsDomainConfig config;
      private final Set<String> expectedRunningServers;
      private final Map<String, String> serverState;
      private final Map<String, ServerHealth> serverHealth;
      private final Packet packet;

      StatusUpdateContext(Packet packet, StatusUpdateStep statusUpdateStep) {
        super(packet, statusUpdateStep);
        this.packet = packet;
        config = packet.getValue(DOMAIN_TOPOLOGY);
        serverState = packet.getValue(SERVER_STATE_MAP);
        serverHealth = packet.getValue(SERVER_HEALTH_MAP);
        expectedRunningServers = DomainPresenceInfo.fromPacket(packet)
              .map(DomainPresenceInfo::getExpectedRunningServers)
              .orElse(Collections.emptySet());
      }

      @Override
      void modifyStatus(DomainStatus status) {
        if (getDomainConfig().isPresent()) {
          setStatusDetails(status);
        }
        if (getDomain() != null) {
          updateStatusDetails(status);
          setStatusConditions(status);
        }
      }

      @Nonnull
      @Override
      List<EventData> createDomainEvents() {
        return new Conditions(getStatus()).getNewConditions().stream()
              .map(this::toEvent)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      }

      private EventData toEvent(DomainCondition newCondition) {
        switch (newCondition.getType()) {
          case Completed:
            return new EventData(DOMAIN_COMPLETE);
          case Available:
            return new EventData(DOMAIN_AVAILABLE);
          default:
            return null;
        }
      }

      private void setStatusConditions(DomainStatus status) {
        Conditions newConditions = new Conditions(status);
        newConditions.apply();

        if (isHasFailedPod()) {
          status.addCondition(new DomainCondition(Failed).withStatus(true).withReason(ServerPod));
        } else {
          status.removeConditionsMatching(c -> c.hasType(Failed) && ServerPod.name().equals(c.getReason()));
          if (newConditions.allIntendedServersRunning() && !stillHasPodPendingRestart(status)) {
            status.removeConditionsWithType(ConfigChangesPendingRestart);
          }
        }

        if (miiNondynamicRestartRequired() && isCommitUpdateOnly()) {
          setOnlineUpdateNeedRestartCondition(status);
        }
      }

      private boolean haveServerData() {
        return this.serverState != null;
      }

      class Conditions {

        private final DomainStatus status;
        private final ClusterCheck[] clusterChecks;
        private final List<DomainCondition> conditions = new ArrayList<>();

        void apply() {
          status.removeConditionsMatching(c -> c.hasType(Failed) && ReplicasTooHigh.name().equals(c.getReason()));
          conditions.forEach(newCondition -> addCondition(status, newCondition));
        }

        private void addCondition(DomainStatus status, DomainCondition newCondition) {
          status.addCondition(newCondition);
        }

        List<DomainCondition> getNewConditions() {
          return conditions.stream()
                .filter(c -> "True".equals(c.getStatus()))
                .filter(c -> status == null || !status.hasConditionWith(matchFor(c)))
                .collect(Collectors.toList());
        }

        Predicate<DomainCondition> matchFor(DomainCondition condition) {
          return c -> c.getType().equals(condition.getType()) && "True".equals(c.getStatus());
        }

        @Nonnull
        private ClusterCheck[] createClusterChecks() {
          return status.getClusters().stream().map(this::createFrom).toArray(ClusterCheck[]::new);
        }

        private ClusterCheck createFrom(ClusterStatus clusterStatus) {
          return new ClusterCheck(status, clusterStatus);
        }

        public Conditions(DomainStatus status) {
          this.status = status != null ? status : new DomainStatus();
          this.clusterChecks = createClusterChecks();
          conditions.add(new DomainCondition(Completed).withStatus(isProcessingCompleted()));
          conditions.add(new DomainCondition(Available).withStatus(sufficientServersRunning()));
          computeTooManyReplicasFailures();
        }

        private boolean isProcessingCompleted() {
          return !haveTooManyReplicas() && allIntendedServersRunning();
        }

        private boolean haveTooManyReplicas() {
          return Arrays.stream(clusterChecks).anyMatch(ClusterCheck::hasTooManyReplicas);
        }

        private boolean allIntendedServersRunning() {
          return haveServerData()
                && allStartedServersAreRunning()
                && allNonStartedServersAreShutdown()
                && serversMarkedForRoll().isEmpty();
        }

        private boolean sufficientServersRunning() {
          return atLeastOneApplicationServerStarted() && allNonClusteredServersRunning() && allClustersAvailable();
        }

        private boolean allClustersAvailable() {
          return Arrays.stream(clusterChecks).allMatch(ClusterCheck::isAvailable);
        }

        private void computeTooManyReplicasFailures() {
          Arrays.stream(clusterChecks)
                .filter(ClusterCheck::hasTooManyReplicas)
                .forEach(check -> conditions.add(check.createFailureCondition()));
        }
      }

      private class ClusterCheck {

        private final String clusterName;
        private final int minReplicaCount;
        private final int maxReplicaCount;
        private final int specifiedReplicaCount;
        private final List<String> startedServers;

        ClusterCheck(DomainStatus domainStatus, ClusterStatus clusterStatus) {
          clusterName = clusterStatus.getClusterName();
          minReplicaCount = clusterStatus.getMinimumReplicas();
          maxReplicaCount = clusterStatus.getMaximumReplicas();
          specifiedReplicaCount = clusterStatus.getReplicasGoal();
          startedServers = getStartedServersInCluster(domainStatus, clusterName);
        }

        private List<String> getStartedServersInCluster(DomainStatus domainStatus, String clusterName) {
          return domainStatus.getServers().stream()
                .filter(s -> clusterName.equals(s.getClusterName()))
                .map(ServerStatus::getServerName)
                .filter(expectedRunningServers::contains)
                .collect(Collectors.toList());
        }

        boolean isAvailable() {
          return isClusterIntentionallyShutDown() || sufficientServersRunning();
        }

        boolean hasTooManyReplicas() {
          return maxReplicaCount > 0 && specifiedReplicaCount > maxReplicaCount;
        }

        DomainCondition createFailureCondition() {
          return new DomainCondition(Failed).withReason(ReplicasTooHigh).withMessage(createFailureMessage());
        }

        private boolean isClusterIntentionallyShutDown() {
          return startedServers.isEmpty();
        }

        private boolean sufficientServersRunning() {
          return numServersReady() >= getSufficientServerCount();
        }

        private long getSufficientServerCount() {
          return max(1, minReplicas(), specifiedReplicaCount - maxUnavailable());
        }

        private int max(Integer... inputs) {
          return Arrays.stream(inputs).reduce(0, Math::max);
        }

        private int minReplicas() {
          return getDomain().isAllowReplicasBelowMinDynClusterSize(clusterName) ? 0 : minReplicaCount;
        }

        private long numServersReady() {
          return startedServers.stream()
                .map(StatusUpdateContext.this::getRunningState)
                .filter(this::isRunning)
                .count();
        }

        private int maxUnavailable() {
          return getDomain().getMaxUnavailable(clusterName);
        }

        private boolean isRunning(String serverState) {
          return RUNNING_STATE.equals(serverState);
        }

        private String createFailureMessage() {
          return LOGGER.formatMessage(TOO_MANY_REPLICAS_FAILURE, specifiedReplicaCount, clusterName, maxReplicaCount);
        }
      }

      private void setStatusDetails(DomainStatus status) {
        getDomainConfig()
              .map(c -> new DomainStatusFactory(getDomain(), c, this::isStartedServer))
              .ifPresent(f -> f.setStatusDetails(status));
      }

      private void updateStatusDetails(DomainStatus status) {
        new StatusDetailsUpdate(status).updateStatusDetails(status);
      }

      private class StatusDetailsUpdate {
        private final DomainStatus status;

        StatusDetailsUpdate(DomainStatus status) {

          this.status = status;
        }

        private void updateServerStatus(ServerStatus status) {
          final String serverName = status.getServerName();
          status.withState(getRunningState(serverName));
          status.withHealth(serverHealth == null ? null : serverHealth.get(serverName));
          status.withNodeName(getNodeName(serverName));
        }

        private void updateClusterStatus(ClusterStatus clusterStatus) {
          final String clusterName = clusterStatus.getClusterName();
          clusterStatus
                .withReplicas(getNumReplicas(clusterName, this::hasServerPod))
                .withReadyReplicas(getNumReplicas(clusterName, this::hasReadyServerPod));
        }

        @Nullable
        private Integer getNumReplicas(String clusterName, Predicate<String> serverFilter) {
          return Optional.ofNullable(getClusterCounts(serverFilter).get(clusterName)).map(Long::intValue).orElse(null);
        }

        Integer getReplicaSetting() {
          Collection<Long> values = getClusterCounts(this::hasServerPod).values();
          if (values.size() == 1) {
            return values.iterator().next().intValue();
          } else {
            return null;
          }
        }

        private Map<String, Long> getClusterCounts(Predicate<String> serverFilter) {
          return status.getServers().stream()
                .map(ServerStatus::getServerName)
                .filter(serverFilter)
                .map(this::getClusterNameFromPod)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        }

        private boolean hasServerPod(String serverName) {
          return Optional.ofNullable(getInfo().getServerPod(serverName)).isPresent();
        }

        private boolean hasReadyServerPod(String serverName) {
          return Optional.ofNullable(getInfo().getServerPod(serverName)).filter(PodHelper::getReadyStatus).isPresent();
        }

        private String getClusterNameFromPod(String serverName) {
          return Optional.ofNullable(getInfo().getServerPod(serverName))
              .map(V1Pod::getMetadata)
              .map(V1ObjectMeta::getLabels)
              .map(l -> l.get(CLUSTERNAME_LABEL))
              .orElse(null);
        }

        private void updateStatusDetails(DomainStatus status) {
          status.getServers().forEach(this::updateServerStatus);
          status.getClusters().forEach(this::updateClusterStatus);
          status.setReplicas(getReplicaSetting());
        }
      }

      private boolean miiNondynamicRestartRequired() {
        return MII_DYNAMIC_UPDATE_RESTART_REQUIRED.equals(packet.get(MII_DYNAMIC_UPDATE));
      }

      private boolean isCommitUpdateOnly() {
        return getMiiNonDynamicChangesMethod() == CommitUpdateOnly;
      }

      private MIINonDynamicChangesMethod getMiiNonDynamicChangesMethod() {
        return DomainPresenceInfo.fromPacket(packet)
            .map(DomainPresenceInfo::getDomain)
            .map(Domain::getSpec)
            .map(DomainSpec::getConfiguration)
            .map(Configuration::getModel)
            .map(Model::getOnlineUpdate)
            .map(OnlineUpdate::getOnNonDynamicChanges)
            .orElse(MIINonDynamicChangesMethod.CommitUpdateOnly);
      }

      private void setOnlineUpdateNeedRestartCondition(DomainStatus status) {
        String dynamicUpdateRollBackFile = Optional.ofNullable((String)packet.get(
            ProcessingConstants.MII_DYNAMIC_UPDATE_WDTROLLBACKFILE))
            .orElse("");
        String message = String.format("%s\n%s",
            LOGGER.formatMessage(MessageKeys.MII_DOMAIN_UPDATED_POD_RESTART_REQUIRED), dynamicUpdateRollBackFile);
        updateDomainConditions(status, message);
      }

      private void updateDomainConditions(DomainStatus status, String message) {
        DomainCondition onlineUpdateCondition
              = new DomainCondition(ConfigChangesPendingRestart).withMessage(message).withStatus(true);

        status.removeConditionsWithType(ConfigChangesPendingRestart);
        status.addCondition(onlineUpdateCondition);
      }

      private boolean stillHasPodPendingRestart(DomainStatus status) {
        return status.getServers().stream()
            .map(this::getServerPod)
            .map(this::getLabels)
            .anyMatch(m -> m.containsKey(LabelConstants.MII_UPDATED_RESTART_REQUIRED_LABEL));
      }

      private V1Pod getServerPod(ServerStatus serverStatus) {
        return getInfo().getServerPod(serverStatus.getServerName());
      }

      private Map<String, String> getLabels(V1Pod pod) {
        return Optional.ofNullable(pod)
            .map(V1Pod::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .orElse(Collections.emptyMap());
      }

      private boolean allStartedServersAreRunning() {
        return expectedRunningServers.stream().allMatch(this::isRunning);
      }

      private boolean allNonStartedServersAreShutdown() {
        return getNonStartedServersWithState().stream().allMatch(this::isShutDown);
      }

      private List<String> getNonStartedServersWithState() {
        return serverState.keySet().stream().filter(this::isNonStartedServer).collect(Collectors.toList());
      }

      private boolean isNonStartedServer(String serverName) {
        return !isStartedServer(serverName);
      }

      private boolean atLeastOneApplicationServerStarted() {
        return getInfo().getServerStartupInfo().size() > 0;
      }

      private @Nonnull List<String> getNonClusteredServers() {
        return expectedRunningServers.stream().filter(this::isNonClusteredServer).collect(Collectors.toList());
      }

      private boolean allNonClusteredServersRunning() {
        return getNonClusteredServers().stream().noneMatch(this::isNotRunning);
      }

      private Set<String> serversMarkedForRoll() {
        return DomainPresenceInfo.fromPacket(packet)
              .map(DomainPresenceInfo::getServersToRoll)
              .map(Map::keySet)
              .orElse(Collections.emptySet());
      }

      private boolean isNonClusteredServer(String serverName) {
        return getClusterName(serverName) == null;
      }

      private Optional<WlsDomainConfig> getDomainConfig() {
        return Optional.ofNullable(config).or(this::getScanCacheDomainConfig);
      }

      private Optional<WlsDomainConfig> getScanCacheDomainConfig() {
        DomainPresenceInfo info = getInfo();
        Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUid());
        return Optional.ofNullable(scan).map(Scan::getWlsDomainConfig);
      }

      private boolean isRunning(@Nonnull String serverName) {
        return RUNNING_STATE.equals(getRunningState(serverName));
      }

      private boolean isNotRunning(@Nonnull String serverName) {
        return !RUNNING_STATE.equals(getRunningState(serverName));
      }

      private boolean isShutDown(@Nonnull String serverName) {
        return SHUTDOWN_STATE.equals(getRunningState(serverName));
      }

      private boolean isHasFailedPod() {
        return getInfo().getServerPods().anyMatch(PodHelper::isFailed);
      }

      private String getRunningState(String serverName) {
        if (!getInfo().getServerNames().contains(serverName)) {
          return SHUTDOWN_STATE;
        } else if (isDeleting(serverName)) {
          return SHUTTING_DOWN_STATE;
        } else {
          return Optional.ofNullable(serverState).map(m -> m.get(serverName)).orElse(null);
        }
      }

      private boolean isDeleting(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName)).map(PodHelper::isDeleting).orElse(false);
      }

      private boolean isStartedServer(String serverName) {
        return expectedRunningServers.contains(serverName);
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

    }
  }

  public static class RemoveFailuresStep extends DomainStatusUpdaterStep {

    private RemoveFailuresStep() {
    }

    @Override
    void modifyStatus(DomainStatus status) {
      status.removeConditionsWithType(Failed);
    }
  }

  /**
   * A factory to update a DomainStatus object from a WlsDomainConfig. This includes the clusters and servers
   * that are expected to be running, but does not include actual runtime state.
   */
  static class DomainStatusFactory {

    @Nonnull
    private final Domain domain;
    private final WlsDomainConfig domainConfig;
    private final Function<String, Boolean> isServerConfiguredToRun;

    /**
     * Creates a factory to create a DomainStatus object, initialized with state from the configuration.
     * @param domain an operator domain resource
     * @param domainConfig a WebLogic domain configuration
     * @param isServerConfiguredToRun returns true if the named server is configured to start
     */
    public DomainStatusFactory(@Nonnull Domain domain,
                               @Nonnull WlsDomainConfig domainConfig,
                               @Nonnull Function<String, Boolean> isServerConfiguredToRun) {
      this.domain = domain;
      this.domainConfig = domainConfig;
      this.isServerConfiguredToRun = isServerConfiguredToRun;
    }

    ServerStatus createServerStatus(WlsServerConfig config) {
      return new ServerStatusFactory(config).create();
    }

    public void setStatusDetails(DomainStatus status) {
      status.setServers(domainConfig.getAllServers().stream()
            .map(this::createServerStatus)
            .collect(Collectors.toList()));
      status.setClusters(domainConfig.getConfiguredClusters().stream()
            .map(this::createClusterStatus)
            .collect(Collectors.toList()));
    }

    class ServerStatusFactory {
      private final String serverName;
      private final String clusterName;
      private final boolean isAdminServer;

      public ServerStatusFactory(WlsServerConfig serverConfig) {
        this.serverName = serverConfig.getName();
        this.clusterName = domainConfig.getClusterName(serverName);
        this.isAdminServer = serverName.equals(domainConfig.getAdminServerName());
      }

      ServerStatus create() {
        return new ServerStatus()
              .withServerName(serverName)
              .withClusterName(clusterName)
              .withDesiredState(getDesiredState())
              .withIsAdminServer(isAdminServer);
      }

      private String getDesiredState() {
        return wasServerStarted() ? getDesiredState(serverName, clusterName) : SHUTDOWN_STATE;
      }

      private String getDesiredState(String serverName, String clusterName) {
        return domain.getServer(serverName, clusterName).getDesiredState();
      }

      private boolean wasServerStarted() {
        return isAdminServer || isServerConfiguredToRun.apply(serverName);
      }
    }

    private ClusterStatus createClusterStatus(WlsClusterConfig clusterConfig) {
      final String clusterName = clusterConfig.getName();
      return new ClusterStatus()
            .withClusterName(clusterName)
            .withMaximumReplicas(clusterConfig.getMaxClusterSize())
            .withMinimumReplicas(useMinimumClusterSize(clusterName) ? clusterConfig.getMinClusterSize() : 0)
            .withReplicasGoal(getClusterSizeGoal(clusterName));
    }

    private boolean useMinimumClusterSize(String clusterName) {
      return !domain.isAllowReplicasBelowMinDynClusterSize(clusterName);
    }

    private Integer getClusterSizeGoal(String clusterName) {
      return domain.getReplicaCount(clusterName);
    }

  }

  private static class FailedStep extends DomainStatusUpdaterStep {
    private final DomainFailureReason reason;
    private final String message;

    private FailedStep(DomainFailureReason reason, String message, Step next) {
      super(next);
      this.reason = reason;
      this.message = message;
    }

    @Override
    protected String getDetail() {
      return reason.name();
    }

    @Override
    void modifyStatus(DomainStatus s) {
      s.addCondition(new DomainCondition(Failed).withStatus(TRUE).withReason(reason).withMessage(message));
    }
  }
}
