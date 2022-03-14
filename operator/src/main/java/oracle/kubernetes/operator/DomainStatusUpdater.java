// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
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
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
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
import oracle.kubernetes.utils.SystemClock;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static oracle.kubernetes.operator.DomainFailureReason.Aborted;
import static oracle.kubernetes.operator.DomainFailureReason.DomainInvalid;
import static oracle.kubernetes.operator.DomainFailureReason.Internal;
import static oracle.kubernetes.operator.DomainFailureReason.Introspection;
import static oracle.kubernetes.operator.DomainFailureReason.Kubernetes;
import static oracle.kubernetes.operator.DomainFailureReason.ReplicasTooHigh;
import static oracle.kubernetes.operator.DomainFailureReason.ServerPod;
import static oracle.kubernetes.operator.DomainFailureReason.TopologyMismatch;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.TO_BE_ROLLED_LABEL;
import static oracle.kubernetes.operator.MIINonDynamicChangesMethod.CommitUpdateOnly;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTTING_DOWN_STATE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_COMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILURE_RESOLVED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_INCOMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_COMPLETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_UNAVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_ROLL_START;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_MAX_ERRORS_EXCEEDED;
import static oracle.kubernetes.operator.logging.MessageKeys.PODS_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.PODS_NOT_READY;
import static oracle.kubernetes.operator.logging.MessageKeys.TOO_MANY_REPLICAS_FAILURE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Completed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ConfigChangesPendingRestart;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Rolling;

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

  public interface StepWithRetryCount {
    Step forIntrospection(@Nullable V1Job introspectorJob);
  }

  /**
   * Creates an asynchronous step to update domain status from the topology in the current packet.
   *
   * @param next the next step
   * @return the new step
   */
  public static Step createStatusUpdateStep(Step next) {
    return new StatusUpdateStep(next);
  }


  /**
   * Creates an asynchronous step to update the domain status to indicate that a roll is starting.
   */
  public static Step createStartRollStep() {
    return new StartRollStep();
  }

  public static class StartRollStep extends DomainStatusUpdaterStep {

    private StartRollStep() {
    }

    @Override
    void modifyStatus(DomainStatus status) {
      status.addCondition(new DomainCondition(Rolling));
    }

    @Override
    DomainStatusUpdaterContext createContext(Packet packet) {
      return new StartRollUpdaterContext(packet, this);
    }

    static class StartRollUpdaterContext extends DomainStatusUpdaterContext {

      StartRollUpdaterContext(Packet packet, DomainStatusUpdaterStep domainStatusUpdaterStep) {
        super(packet, domainStatusUpdaterStep);
      }

      @NotNull
      @Override
      List<EventData> createDomainEvents() {
        final List<EventData> result = new ArrayList<>();
        if (wasNotRolling()) {
          LOGGER.info(DOMAIN_ROLL_START);
          result.add(new EventData(DOMAIN_ROLL_STARTING));
        }
        return result;
      }

      private boolean wasNotRolling() {
        return !Optional.ofNullable(getStatus()).map(DomainStatus::isRolling).orElse(false);
      }
    }


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
   * Asynchronous step to create a failure condition.
   */
  @SuppressWarnings("unchecked")
  public static <S extends Step & StepWithRetryCount> S createFailedStep(DomainFailureReason reason, String message) {
    return (S) new FailureStep(reason, message);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed after an asynchronous call failure
   * and to generate DOMAIN_FAILED event.
   *
   * @param callResponse the response from an unrecoverable call
   */
  public static Step createKubernetesFailureSteps(CallResponse<?> callResponse) {
    FailureStatusSource failure = UnrecoverableErrorBuilder.fromFailedCall(callResponse);

    LOGGER.severe(MessageKeys.CALL_FAILED, failure.getMessage(), failure.getReason());
    ApiException apiException = callResponse.getE();
    if (apiException != null) {
      LOGGER.fine(MessageKeys.EXCEPTION, apiException);
    }

    return createFailureSteps(Kubernetes, failure.getMessage());
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param throwable Throwable that caused failure
   */
  static Step createInternalFailureSteps(Throwable throwable) {
    return new FailureStep(Internal, throwable);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createServerPodFailureSteps(String message) {
    return createFailureSteps(ServerPod, message);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createDomainInvalidFailureSteps(String message) {
    return createFailureSteps(DomainInvalid, message);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   */
  public static Step createAbortedFailureSteps() {
    return createEventStep(new EventData(DOMAIN_FAILED, INTROSPECTOR_MAX_ERRORS_EXCEEDED).failureReason(Aborted));
  }


  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createTopologyMismatchFailureSteps(String message) {
    return createFailureSteps(TopologyMismatch, message);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param reason the failure category
   * @param message a fuller description of the problem
   */
  public static Step createFailureSteps(@Nonnull DomainFailureReason reason, String message) {
    return createFailedStep(reason, message);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed, increment the introspector failure count if needed
   * and to generate DOMAIN_FAILED event.
   *
   * @param throwable               the exception that describes the problem
   * @param domainIntrospectorJob Domain introspector job
   */
  public static Step createIntrospectionFailureSteps(Throwable throwable, V1Job domainIntrospectorJob) {
    return new FailureStep(Introspection, throwable).forIntrospection(domainIntrospectorJob);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed, increment the introspector failure count if needed
   * and to generate DOMAIN_FAILED event.
   *
   * @param message               a fuller description of the problem
   * @param domainIntrospectorJob Domain introspector job
   */
  public static Step createIntrospectionFailureSteps(String message, V1Job domainIntrospectorJob) {
    return new FailureStep(Introspection, message).forIntrospection(domainIntrospectorJob);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed, will not increment the introspector failure count
   * and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createIntrospectionFailureSteps(String message) {
    return new FailureStep(Introspection, message);
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

  abstract static class DomainStatusUpdaterStep extends Step {

    DomainStatusUpdaterStep() {
    }

    DomainStatusUpdaterStep(Step next) {
      super(next);
    }

    DomainStatusUpdaterContext createContext(Packet packet) {
      return new DomainStatusUpdaterContext(packet, this);
    }

    abstract void modifyStatus(DomainStatus domainStatus);

    @Override
    public NextAction apply(Packet packet) {
      return doNext(createContext(packet).createUpdateSteps(getNext()), packet);
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

    @Override
    public NextAction onFailure(Packet packet, CallResponse<Domain> callResponse) {
      return callResponse.getStatusCode() == HTTP_NOT_FOUND
          ? doNext(null, packet)
          : super.onFailure(packet, callResponse);
    }
  }

  static class DomainStatusUpdaterContext {
    @Nonnull
    private final DomainPresenceInfo info;
    private final DomainStatusUpdaterStep domainStatusUpdaterStep;
    private DomainStatus newStatus;

    DomainStatusUpdaterContext(Packet packet, DomainStatusUpdaterStep domainStatusUpdaterStep) {
      info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      this.domainStatusUpdaterStep = domainStatusUpdaterStep;
    }

    DomainStatus getNewStatus() {
      if (newStatus == null) {
        newStatus = createNewStatus();
      }
      return newStatus;
    }

    @NotNull
    private DomainStatus createNewStatus() {
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

    private Step createUpdateSteps(Step next) {
      final List<Step> result = new ArrayList<>();
      createDomainEvents().stream().map(EventHelper::createEventStep).forEach(result::add);
      if (!isStatusUnchanged()) {
        result.add(createDomainStatusReplaceStep());
      }
      Optional.ofNullable(next).ifPresent(result::add);
      return result.isEmpty() ? null : Step.chain(result);
    }

    @Nonnull
    List<EventData> createDomainEvents() {
      return Collections.emptyList();
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
    DomainStatusUpdaterContext createContext(Packet packet) {
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
        expectedRunningServers = getInfo().getExpectedRunningServers();
      }

      @Override
      void modifyStatus(DomainStatus status) {
        if (getDomainConfig().isPresent()) {
          setStatusDetails(status);
        }
        if (getDomain() != null) {
          updateStatusDetails(status, packet.getSpi(DomainPresenceInfo.class));
          setStatusConditions(status);
        }
      }

      @Nonnull
      @Override
      List<EventData> createDomainEvents() {
        Conditions conditions = new Conditions(getNewStatus());
        conditions.apply();

        List<EventData> list = getRemovedConditionEvents(conditions);
        list.addAll(getNewConditionEvents(conditions));
        list.sort(Comparator.comparing(EventData::getOrdering));
        return list;
      }

      @NotNull
      private List<EventData> getNewConditionEvents(@Nonnull  Conditions conditions) {
        return conditions.getNewConditions().stream()
            .map(this::toEvent)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }

      private List<EventData> getRemovedConditionEvents(@Nonnull  Conditions conditions) {
        return conditions.getRemovedConditions().stream()
            .map(this::toRemovedEvent)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }

      private EventData toEvent(DomainCondition newCondition) {
        switch (newCondition.getType()) {
          case Completed:
            return new EventData(DOMAIN_COMPLETE);
          case Available:
            return new EventData(DOMAIN_AVAILABLE);
          case Failed:
            if (ReplicasTooHigh.name().equals(newCondition.getReason())) {
              return new EventData(DOMAIN_FAILED).failureReason(ReplicasTooHigh);
            } else if (ServerPod.name().equals(newCondition.getReason())) {
              return new EventData(DOMAIN_FAILED).failureReason(ServerPod).message(newCondition.getMessage());
            }
            return null;
          default:
            return null;
        }
      }

      private EventData toRemovedEvent(DomainCondition removedCondition) {
        switch (removedCondition.getType()) {
          case Completed:
            return new EventData(DOMAIN_INCOMPLETE);
          case Available:
            return new EventData(DOMAIN_UNAVAILABLE);
          case Rolling:
            return new EventData(DOMAIN_ROLL_COMPLETED);
          case Failed:
            return new EventData(DOMAIN_FAILURE_RESOLVED);
          default:
            return null;
        }
      }

      private void setStatusConditions(DomainStatus status) {
        Conditions newConditions = new Conditions(status);
        newConditions.apply();

        if (isHasFailedPod()) {
          status.addCondition(new DomainCondition(Failed)
              .withStatus(true)
              .withReason(ServerPod)
              .withMessage(forPodFailedMessage()));
        } else if (hasPodNotReadyInTime()) {
          status.addCondition(new DomainCondition(Failed)
              .withStatus(true)
              .withReason(ServerPod)
              .withMessage(formatPodNotReadyMessage()));
        } else {
          status.removeConditionsMatching(c -> c.hasType(Failed) && ServerPod.name().equals(c.getReason()));
          if (newConditions.allIntendedServersReady() && !stillHasPodPendingRestart(status)) {
            status.removeConditionsWithType(ConfigChangesPendingRestart);
          }
        }

        if (miiNondynamicRestartRequired() && isCommitUpdateOnly()) {
          setOnlineUpdateNeedRestartCondition(status);
        }
      }

      private String formatPodNotReadyMessage() {
        return LOGGER.formatMessage(PODS_NOT_READY);
      }

      private String forPodFailedMessage() {
        return LOGGER.formatMessage(PODS_FAILED);
      }

      private boolean haveServerData() {
        return this.serverState != null;
      }

      class Conditions {

        private final DomainStatus status;
        private final ClusterCheck[] clusterChecks;
        private final List<DomainCondition> conditions = new ArrayList<>();
        private final DomainStatus oldStatus;

        public Conditions(DomainStatus status) {
          this.status = status != null ? status : new DomainStatus();
          this.clusterChecks = createClusterChecks();
          conditions.add(new DomainCondition(Completed).withStatus(isProcessingCompleted()));
          conditions.add(new DomainCondition(Available).withStatus(sufficientServersRunning()));
          computeTooManyReplicasFailures();
          if (allIntendedServersReady()) {
            this.status.removeConditionsWithType(Rolling);
          }
          this.oldStatus = getStatus();
        }

        void apply() {
          status.removeConditionsMatching(c -> c.hasType(Failed) && ReplicasTooHigh.name().equals(c.getReason()));
          conditions.forEach(newCondition -> addCondition(status, newCondition));
        }

        private void addCondition(DomainStatus status, DomainCondition newCondition) {
          status.addCondition(newCondition);
        }

        List<DomainCondition> getNewConditions() {
          return Optional.ofNullable(status).map(DomainStatus::getConditions).orElse(Collections.emptyList())
              .stream()
              .filter(c -> "True".equals(c.getStatus()))
              .filter(c -> oldStatus == null || oldStatus.lacksConditionWith(matchFor(c)))
              .collect(Collectors.toList());
        }

        List<DomainCondition> getRemovedConditions() {
          return Optional.ofNullable(oldStatus)
              .map(DomainStatus::getConditions).orElse(Collections.emptyList())
              .stream()
              .filter(c -> "True".equals(c.getStatus()))
              .filter(c -> status == null || status.lacksConditionWith(matchFor(c)))
              .collect(Collectors.toList());
        }

        private boolean failureReasonMatch(DomainCondition c1, DomainCondition c2) {
          return !c1.getType().equals(Failed)
              || (getReasonString(c1).equals(getReasonString(c2)) && getMessage(c1).equals(getMessage(c2)));
        }

        private String getReasonString(DomainCondition condition) {
          return Optional.ofNullable(condition).map(DomainCondition::getReason).orElse("");
        }

        private String getMessage(DomainCondition condition) {
          return Optional.ofNullable(condition).map(DomainCondition::getMessage).orElse("");
        }

        Predicate<DomainCondition> matchFor(DomainCondition condition) {
          return c -> c.getType().equals(condition.getType())
              && failureReasonMatch(c, condition) && "True".equals(c.getStatus());
        }

        @Nonnull
        private ClusterCheck[] createClusterChecks() {
          return status.getClusters().stream().map(this::createFrom).toArray(ClusterCheck[]::new);
        }

        private ClusterCheck createFrom(ClusterStatus clusterStatus) {
          return new ClusterCheck(status, clusterStatus);
        }

        private boolean isProcessingCompleted() {
          return !haveTooManyReplicas() && allIntendedServersReady();
        }

        private boolean haveTooManyReplicas() {
          return Arrays.stream(clusterChecks).anyMatch(ClusterCheck::hasTooManyReplicas);
        }

        private boolean allIntendedServersReady() {
          return haveServerData()
              && allStartedServersAreComplete()
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
          return isClusterIntentionallyShutDown() || sufficientServersReady();
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

        private boolean sufficientServersReady() {
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
              .filter(StatusUpdateContext.this::isServerReady)
              .count();
        }

        private int maxUnavailable() {
          return getDomain().getMaxUnavailable(clusterName);
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

      private void updateStatusDetails(DomainStatus status, DomainPresenceInfo info) {
        new StatusDetailsUpdate(status, info).updateStatusDetails();
      }

      private class StatusDetailsUpdate {
        private final DomainStatus status;
        private final DomainPresenceInfo info;

        StatusDetailsUpdate(DomainStatus status, DomainPresenceInfo info) {
          this.info = info;
          this.status = status;
        }

        private void updateServerStatus(ServerStatus status) {
          final String serverName = status.getServerName();
          status.withState(getRunningState(serverName));
          status.withHealth(serverHealth == null ? null : serverHealth.get(serverName));
          status.withNodeName(getNodeName(serverName));
          Optional.ofNullable(info).map(i -> i.getServerPod(serverName))
              .map(V1Pod::getStatus)
              .map(s -> status.withPodReady(getReadyStatus(s)).withPodPhase(s.getPhase()));
        }

        private String getReadyStatus(V1PodStatus podStatus) {
          return Optional.ofNullable(podStatus)
              .filter(this::isPhaseRunning)
              .map(V1PodStatus::getConditions)
              .orElse(Collections.emptyList())
              .stream().filter(this::isReadyCondition)
              .map(V1PodCondition::getStatus)
              .findFirst().orElse("Unknown");
        }

        private boolean isReadyCondition(Object condition) {
          return (condition instanceof V1PodCondition) && "Ready".equals(((V1PodCondition)condition).getType());
        }

        private boolean isPhaseRunning(V1PodStatus status) {
          return "Running".equals(status.getPhase());
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
          return Optional.ofNullable(getInfo().getServerPod(serverName)).filter(PodHelper::hasReadyStatus).isPresent();
        }

        private String getClusterNameFromPod(String serverName) {
          return Optional.ofNullable(getInfo().getServerPod(serverName))
              .map(V1Pod::getMetadata)
              .map(V1ObjectMeta::getLabels)
              .map(l -> l.get(CLUSTERNAME_LABEL))
              .orElse(null);
        }

        private void updateStatusDetails() {
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
        String dynamicUpdateRollBackFile = Optional.ofNullable((String) packet.get(
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

      private boolean allStartedServersAreComplete() {
        return expectedRunningServers.stream().allMatch(this::isServerComplete);
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
        return getNonClusteredServers().stream().allMatch(this::isServerReady);
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

      // A server is complete if it is ready, is in the WLS running state and
      // does not need to roll to accommodate changes to the domain.
      private boolean isServerComplete(@Nonnull String serverName) {
        return isServerReady(serverName)
              && isNotMarkedForRoll(serverName);
      }

      private boolean isServerReady(@Nonnull String serverName) {
        return RUNNING_STATE.equals(getRunningState(serverName))
              && PodHelper.hasReadyStatus(getInfo().getServerPod(serverName));
      }

      // returns true if the server pod does not have a label indicating that it needs to be rolled
      private boolean isNotMarkedForRoll(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName))
              .map(V1Pod::getMetadata)
              .map(V1ObjectMeta::getLabels)
              .map(Map::keySet).orElse(Collections.emptySet()).stream()
              .noneMatch(k -> k.equals(TO_BE_ROLLED_LABEL));
      }

      private boolean isShutDown(@Nonnull String serverName) {
        return SHUTDOWN_STATE.equals(getRunningState(serverName));
      }

      private boolean isHasFailedPod() {
        return getInfo().getServerPods().anyMatch(PodHelper::isFailed);
      }

      private boolean hasPodNotReadyInTime() {
        return getInfo().getServerPods().anyMatch(this::isNotReadyInTime);
      }

      public boolean isNotReadyInTime(V1Pod pod) {
        return !PodHelper.isReady(pod) && hasBeenUnreadyExceededWaitTime(pod);
      }

      private boolean hasBeenUnreadyExceededWaitTime(V1Pod pod) {
        OffsetDateTime creationTime = getCreationTimestamp(pod);
        return SystemClock.now()
            .isAfter(getLater(creationTime, getReadyConditionLastTransitTimestamp(pod, creationTime))
                .plusSeconds(getMaxReadyWaitTime(pod)));
      }

      private OffsetDateTime getLater(OffsetDateTime time1, OffsetDateTime time2) {
        return time1.isAfter(time2) ? time1 : time2;
      }

      private OffsetDateTime getCreationTimestamp(V1Pod pod) {
        return Optional.ofNullable(pod.getMetadata()).map(V1ObjectMeta::getCreationTimestamp)
            .orElse(SystemClock.now().minusNanos(10L));
      }

      private OffsetDateTime getReadyConditionLastTransitTimestamp(V1Pod pod, OffsetDateTime creationTime) {
        return Optional.ofNullable(PodHelper.getReadyCondition(pod)).map(V1PodCondition::getLastTransitionTime)
            .orElse(creationTime);
      }

      private long getMaxReadyWaitTime(V1Pod pod) {
        return Optional.ofNullable(getInfo().getDomain())
            .map(d -> d.getMaxReadyWaitTimeSeconds(getServerName(pod), getClusterNameFromPod(pod)))
            .orElse(TuningParameters.getInstance().getPodTuning().maxReadyWaitTimeSeconds);
      }

      private String getServerName(V1Pod pod) {
        return Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
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
        return getClusterNameFromPod(getInfo().getServerPod(serverName));
      }

      private String getClusterNameFromPod(V1Pod pod) {
        return Optional.ofNullable(pod)
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
     *
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

  static class FailureStep extends DomainStatusUpdaterStep implements StepWithRetryCount {
    private final DomainFailureReason reason;
    private final String message;
    String jobUid;

    @Override
    public FailureStep forIntrospection(@Nullable V1Job introspectorJob) {
      jobUid = Optional.ofNullable(introspectorJob).map(V1Job::getMetadata).map(V1ObjectMeta::getUid).orElse("");
      return this;
    }

    private FailureStep(DomainFailureReason reason, String message) {
      this.reason = reason;
      this.message = message;
    }

    private FailureStep(DomainFailureReason reason, Throwable throwable) {
      this.reason = reason;
      this.message = Optional.ofNullable(throwable.getMessage()).orElse(throwable.toString());
    }

    @Override
    protected String getDetail() {
      return reason.name();
    }

    @Override
    void modifyStatus(DomainStatus s) {
      final DomainCondition condition = new DomainCondition(Failed).withReason(reason).withMessage(message);
      Optional.ofNullable(jobUid).ifPresent(condition::setIntrospectionUid);
      s.addCondition(condition);

      if (jobUid != null) {
        s.incrementIntrospectJobFailureCount(jobUid);
        addRetryInfoToStatusMessage(s, jobUid, s.getMessage());
      }
    }

    private void addRetryInfoToStatusMessage(DomainStatus domainStatus, String jobUid, String message) {
      domainStatus.setMessage(domainStatus.createDomainStatusMessage(jobUid, message));
    }

    @Override
    DomainStatusUpdaterContext createContext(Packet packet) {
      return new FailureStatusUpdaterContext(packet, this, reason, message);
    }

    static class FailureStatusUpdaterContext extends DomainStatusUpdaterContext {
      private final DomainFailureReason reason;
      private final String message;

      public FailureStatusUpdaterContext(Packet packet, DomainStatusUpdaterStep statusUpdaterStep,
                                         DomainFailureReason reason, String message) {
        super(packet, statusUpdaterStep);
        if (hasJustGotFatalIntrospectorError(message)) {
          this.reason = Aborted;
          this.message = FATAL_INTROSPECTOR_ERROR_MSG + message;
        } else if (hasJustExceededMaxRetryCount()) {
          this.reason = Aborted;
          this.message = INTROSPECTOR_MAX_ERRORS_EXCEEDED;
        } else {
          this.reason = reason;
          this.message = message;
        }
      }

      @Nonnull
      List<EventData> createDomainEvents() {
        return Collections.singletonList(new EventData(DOMAIN_FAILED, message).failureReason(reason));
      }

      private boolean hasJustGotFatalIntrospectorError(String message) {
        return isFatalIntrospectorMessage(message)
            && !isFatalIntrospectorMessage(getStatus().getMessage());
      }

      private boolean isFatalIntrospectorMessage(String statusMessage) {
        return statusMessage != null && statusMessage.contains(FATAL_INTROSPECTOR_ERROR);
      }

      private boolean hasJustExceededMaxRetryCount() {
        return getDomain() != null
              && getStatus() != null
              && getNewStatus().hasReachedMaximumFailureCount()
              && !getStatus().hasReachedMaximumFailureCount();
      }

    }
  }


}

