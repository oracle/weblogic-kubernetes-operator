// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
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
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSource;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.LastKnownStatus;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.OperatorUtils;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterCondition;
import oracle.kubernetes.weblogic.domain.model.ClusterConditionType;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.Model;
import oracle.kubernetes.weblogic.domain.model.OnlineUpdate;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;

import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_NOT_READY;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_FATAL_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_ROLL_START;
import static oracle.kubernetes.common.logging.MessageKeys.NON_CLUSTERED_SERVERS_NOT_READY;
import static oracle.kubernetes.common.logging.MessageKeys.NO_APPLICATION_SERVERS_READY;
import static oracle.kubernetes.common.logging.MessageKeys.PODS_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.PODS_NOT_READY;
import static oracle.kubernetes.common.logging.MessageKeys.PODS_NOT_RUNNING;
import static oracle.kubernetes.operator.ClusterResourceStatusUpdater.createClusterResourceStatusUpdaterStep;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.MINIMUM_CLUSTER_COUNT;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.TO_BE_ROLLED_LABEL;
import static oracle.kubernetes.operator.MIINonDynamicChangesMethod.COMMIT_UPDATE_ONLY;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTTING_DOWN_STATE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_STARTING;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.COMPLETED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.CONFIG_CHANGES_PENDING_RESTART;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ROLLING;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTERNAL;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.PERSISTENT_VOLUME_CLAIM;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.REPLICAS_TOO_HIGH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.SERVER_POD;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.TOPOLOGY_MISMATCH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureSeverity.SEVERE;

/**
 * Updates for status of Domain. This class has two modes: 1) Watching for Pod state changes by
 * listening to events from {@link PodWatcher} and 2) Factory for {@link Step}s that the main
 * processing flow can use to explicitly set the condition.
 */
@SuppressWarnings("WeakerAccess")
public class DomainStatusUpdater {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  public static final int SERVER_DISPLAY_LIMIT = 5;
  public static final int CLUSTER_MESSAGE_LIMIT = 2;

  private DomainStatusUpdater() {
  }

  public interface StepWithRetryCount {
    @SuppressWarnings("unused")
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
   * Creates an asynchronous step to update domain status at the end of domain processing from the topology in
   * the current packet.
   *
   * @param next the next step
   * @return the new step
   */
  public static Step createLastStatusUpdateStep(Step next) {
    return new StatusUpdateStep(next).markEndOfProcessing();
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
      status.addCondition(new DomainCondition(ROLLING));
      status.addCondition(new DomainCondition(COMPLETED).withStatus(false));
    }

    @Override
    DomainStatusUpdaterContext createContext(Packet packet) {
      return new StartRollUpdaterContext(packet, this);
    }

    static class StartRollUpdaterContext extends DomainStatusUpdaterContext {

      StartRollUpdaterContext(Packet packet, DomainStatusUpdaterStep domainStatusUpdaterStep) {
        super(packet, domainStatusUpdaterStep);
      }

      @Nonnull
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
   * @param hasEventData true if the make right operation is associated with an event.
   */
  public static Step createStatusInitializationStep(boolean hasEventData) {
    return new StatusInitializationStep(hasEventData);
  }

  /**
   * Asynchronous step to remove selected failure conditions.
   */
  public static Step createRemoveSelectedFailuresStep(Step next, DomainFailureReason... selectedReasons) {
    return new RemoveSelectedFailuresStep(next, Arrays.asList(selectedReasons));
  }

  /**
   * Asynchronous step to remove failure conditions other than those specified.
   * @param next the next step to execute after this one.
   * @param excludedReasons the failure reasons not to be removed; others will be.
   */
  public static Step createRemoveUnSelectedFailuresStep(Step next, DomainFailureReason... excludedReasons) {
    List<DomainFailureReason> selectedReasons = new ArrayList<>(Arrays.asList(DomainFailureReason.values()));
    Arrays.stream(excludedReasons).forEach(selectedReasons::remove);
    return new RemoveSelectedFailuresStep(next, selectedReasons);
  }

  /**
   * Asynchronous step to remove any current failure conditions.
   */
  public static Step createRemoveFailuresStep(Step next) {
    return createRemoveUnSelectedFailuresStep(next, TOPOLOGY_MISMATCH, REPLICAS_TOO_HIGH, INTROSPECTION);
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

    return new FailureStep(KUBERNETES, failure.getMessage());
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param throwable Throwable that caused failure
   */
  static Step createInternalFailureSteps(Throwable throwable) {
    return new FailureStep(INTERNAL, throwable);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createServerPodFailureSteps(String message) {
    return new FailureStep(SERVER_POD, message);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate PERSISTENT_VOLUME_CLAIM error event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createPersistentVolumeClaimFailureSteps(String message) {
    return new FailureStep(PERSISTENT_VOLUME_CLAIM, message);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createDomainInvalidFailureSteps(String message) {
    return new FailureStep(DOMAIN_INVALID, message).removingOldFailures(DOMAIN_INVALID);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   * @param next the next step to run. May be null.
   */
  public static Step createTopologyMismatchFailureSteps(String message, Step next) {
    return new FailureStep(TOPOLOGY_MISMATCH, message, next).removingOldFailures(TOPOLOGY_MISMATCH, REPLICAS_TOO_HIGH);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   * @param next the next step to run. May be null.
   */
  public static Step createReplicasTooHighFailureSteps(String message, Step next) {
    return new FailureStep(REPLICAS_TOO_HIGH, message, next).removingOldFailures(REPLICAS_TOO_HIGH);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed, increment the introspector failure count if needed
   * and to generate DOMAIN_FAILED event.
   *
   * @param throwable               the exception that describes the problem
   * @param domainIntrospectorJob Domain introspector job
   */
  public static Step createIntrospectionFailureSteps(Throwable throwable, V1Job domainIntrospectorJob) {
    return new FailureStep(INTROSPECTION, throwable).forIntrospection(domainIntrospectorJob);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed, increment the introspector failure count if needed
   * and to generate DOMAIN_FAILED event.
   *
   * @param message               a fuller description of the problem
   * @param domainIntrospectorJob Domain introspector job
   */
  public static Step createIntrospectionFailureSteps(String message, V1Job domainIntrospectorJob) {
    return new FailureStep(INTROSPECTION, message)
        .forIntrospection(domainIntrospectorJob).removingOldFailures(INTROSPECTION);
  }

  /**
   * Asynchronous steps to set Domain condition to Failed, will not increment the introspector failure count
   * and to generate DOMAIN_FAILED event.
   *
   * @param message a fuller description of the problem
   */
  public static Step createIntrospectionFailureSteps(String message) {
    return new FailureStep(INTROSPECTION, message);
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

    void modifyStatus(DomainStatus domainStatus) {
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(createContext(packet).createUpdateSteps(getNext()), packet);
    }

    // Note: this step is created with no next step, as that is added via a call to Step.chain, later.
    private ResponseStep<DomainResource> createResponseStep(DomainStatusUpdaterContext context) {
      return new StatusReplaceResponseStep(this, context, null);
    }
  }

  static class StatusReplaceResponseStep extends DefaultResponseStep<DomainResource> {
    private final DomainStatusUpdaterStep updaterStep;
    private final DomainStatusUpdaterContext context;

    public StatusReplaceResponseStep(DomainStatusUpdaterStep updaterStep,
                                     DomainStatusUpdaterContext context, Step nextStep) {
      super(nextStep);
      this.updaterStep = updaterStep;
      this.context = context;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainResource> callResponse) {
      if (callResponse.getResult() != null) {
        packet.getSpi(DomainPresenceInfo.class).setDomain(callResponse.getResult());
      }
      return doNext(createClusterResourceStatusUpdaterStep(getNext()), packet);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<DomainResource> callResponse) {
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

  static class DomainUpdateStep extends ResponseStep<DomainResource> {
    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainResource> callResponse) {
      if (callResponse.getResult() != null) {
        packet.getSpi(DomainPresenceInfo.class).setDomain(callResponse.getResult());
      }
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<DomainResource> callResponse) {
      return callResponse.getStatusCode() == HTTP_NOT_FOUND
          ? doNext(null, packet)
          : super.onFailure(packet, callResponse);
    }
  }

  static class DomainStatusUpdaterContext {
    @Nonnull
    private final DomainPresenceInfo info;
    final boolean isMakeRight;
    private final DomainStatusUpdaterStep domainStatusUpdaterStep;
    private DomainStatus newStatus;
    private final List<EventData> newEvents = new ArrayList<>();
    final boolean endOfProcessing;

    DomainStatusUpdaterContext(Packet packet, DomainStatusUpdaterStep domainStatusUpdaterStep) {
      info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      isMakeRight = MakeRightDomainOperation.isMakeRight(packet);
      this.domainStatusUpdaterStep = domainStatusUpdaterStep;
      endOfProcessing = (Boolean) packet.getOrDefault(ProcessingConstants.END_OF_PROCESSING, Boolean.FALSE);
    }

    DomainStatus getNewStatus() {
      if (newStatus == null) {
        newStatus = createNewStatus();
      }
      return newStatus;
    }

    @Nonnull
    private DomainStatus createNewStatus() {
      DomainStatus cloneStatus = cloneStatus();
      modifyStatus(cloneStatus);
      return cloneStatus;
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

    @Nonnull
    DomainPresenceInfo getInfo() {
      return info;
    }

    DomainStatus getStatus() {
      return getDomain().getStatus();
    }

    DomainResource getDomain() {
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
      LOGGER.finer("status change: " + createPatchString());

      DomainResource oldDomain = getDomain();
      DomainStatus status = getNewStatus();
      if (isMakeRight) {
        // Only set observedGeneration during a make-right, but not during a background status update
        status.setObservedGeneration(oldDomain.getMetadata().getGeneration());
      }

      return getCallStep(oldDomain, status);
    }

    Step createDomainStatusObservedGenerationReplaceStep() {
      DomainResource oldDomain = getDomain();
      DomainStatus status = oldDomain.getStatus();

      if (isGenerationChanged(oldDomain, status)) {
        // Only set observedGeneration during a make-right, but not during a background status update
        status.setObservedGeneration(getDomainGeneration(oldDomain));

        return getCallStep(oldDomain, status);
      }

      return null;
    }

    private Step getCallStep(DomainResource oldDomain, DomainStatus status) {
      DomainResource newDomain = new DomainResource()
          .withKind(KubernetesConstants.DOMAIN)
          .withApiVersion(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE)
          .withMetadata(oldDomain.getMetadata())
          .withSpec(null)
          .withStatus(status);

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

    Step createUpdateSteps(Step next) {
      final List<Step> result = new ArrayList<>();
      if (!isStatusUnchanged()) {
        result.add(createDomainStatusReplaceStep());
      } else {
        if (endOfProcessing && isMakeRight) {
          Optional.ofNullable(createDomainStatusObservedGenerationReplaceStep()).ifPresent(result::add);
        }
      }
      createDomainEvents().stream().map(EventHelper::createEventStep).forEach(result::add);
      Optional.ofNullable(next).ifPresent(result::add);
      return result.isEmpty() ? null : Step.chain(result);
    }


    private boolean isGenerationChanged(DomainResource domain, DomainStatus status) {
      return !getDomainGeneration(domain).equals(getObservedGeneration(status));
    }

    private Long getObservedGeneration(DomainStatus status) {
      return status.getObservedGeneration();
    }

    @Nonnull
    private Long getDomainGeneration(@Nonnull DomainResource domain) {
      return Optional.ofNullable(domain.getMetadata()).map(V1ObjectMeta::getGeneration).orElse(0L);
    }

    @Nonnull
    List<EventData> createDomainEvents() {
      newEvents.sort(Comparator.comparing(EventData::getOrdering));
      return newEvents;
    }

    void addFailure(DomainStatus status, DomainCondition condition) {
      addFailureCondition(status, condition);
      if (hasReachedRetryLimit(status, condition)) {
        addFailureCondition(status, new DomainCondition(FAILED).withReason(ABORTED).withMessage(getFatalMessage()));
      }
    }

    private void addFailureCondition(DomainStatus status, DomainCondition condition) {
      status.addCondition(condition).updateSummaryMessage(getDomain());
      addDomainEvent(condition);
    }

    private boolean hasReachedRetryLimit(DomainStatus status, DomainCondition condition) {
      return condition.getSeverity() == SEVERE
          && status.getInitialFailureTime() != null
          && status.getMinutesFromInitialToLastFailure() >= getDomain().getFailureRetryLimitMinutes();
    }

    private String getFatalMessage() {
      return LOGGER.formatMessage(DOMAIN_FATAL_ERROR, getDomain().getFailureRetryLimitMinutes());
    }

    void addDomainEvent(DomainCondition condition) {
      newEvents.add(new EventData(DOMAIN_FAILED, condition.getMessage()).failureReason(condition.getReason()));
    }

  }

  public static class StatusInitializationStep extends DomainStatusUpdaterStep {
    private final boolean hasEventData;

    StatusInitializationStep(boolean hasEventData) {
      super();
      this.hasEventData = hasEventData;
    }

    @Override
    void modifyStatus(DomainStatus status) {
      if (status.getConditions().isEmpty()) {
        if (hasEventData) {
          status.addCondition(new DomainCondition(COMPLETED).withStatus(false));
          status.addCondition(new DomainCondition(AVAILABLE).withStatus(false));
        }
      } else {
        status.markFailuresForRemoval(KUBERNETES);
        status.removeMarkedFailures();
      }
    }
  }

  /**
   * A step which updates the domain status from the domain topology in the current packet.
   */
  private static class StatusUpdateStep extends DomainStatusUpdaterStep {
    boolean endOfProcessing = false;

    StatusUpdateStep(Step next) {
      super(next);
    }

    StatusUpdateStep markEndOfProcessing() {
      this.endOfProcessing = true;
      return this;
    }

    @Override
    DomainStatusUpdaterContext createContext(Packet packet) {
      return new StatusUpdateContext(packet, this);
    }

    @Override
    public NextAction apply(Packet packet) {
      if (isDomainNotPresent(packet)) {
        return doNext(packet);
      }
      packet.put(ProcessingConstants.SKIP_STATUS_UPDATE, shouldSkipDomainStatusUpdate(packet));
      if (endOfProcessing) {
        packet.put(ProcessingConstants.END_OF_PROCESSING, Boolean.TRUE);
      }
      return super.apply(packet);
    }

    private boolean shouldSkipDomainStatusUpdate(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return info.getServerStartupInfo() == null
          && info.clusterStatusInitialized()
          && !endOfProcessing;
    }

    private boolean isDomainNotPresent(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return info == null || info.getDomain() == null;
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
      Step createUpdateSteps(Step next) {
        return shouldSkipUpdate(packet)
            ? next
            : super.createUpdateSteps(next);
      }

      boolean shouldSkipUpdate(Packet packet) {
        Boolean skip = (Boolean) packet.remove(ProcessingConstants.SKIP_STATUS_UPDATE);
        return skip != null && skip;
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
        List<EventData> list = super.createDomainEvents();
        Conditions conditions = new Conditions(getNewStatus());
        conditions.apply();

        list.addAll(getRemovedConditionEvents(conditions));
        list.addAll(getNewConditionEvents(conditions));
        list.sort(Comparator.comparing(EventData::getOrdering));
        return list;
      }

      @Nonnull
      private List<EventData> getNewConditionEvents(@Nonnull  Conditions conditions) {
        return conditions.getNewConditions().stream()
            .map(this::toEvent)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }

      private EventData toEvent(DomainCondition newCondition) {
        return Optional.ofNullable(newCondition.getType().getAddedEvent()).map(EventData::new).orElse(null);
      }

      private List<EventData> getRemovedConditionEvents(@Nonnull  Conditions conditions) {
        return conditions.getRemovedConditions().stream()
            .map(this::toRemovedEvent)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }

      private EventData toRemovedEvent(DomainCondition removedCondition) {
        return Optional.ofNullable(removedCondition.getType().getRemovedEvent()).map(EventData::new).orElse(null);
      }

      private void setStatusConditions(DomainStatus status) {
        Conditions newConditions = new Conditions(status);
        newConditions.apply();

        if (isHasFailedPod()) {
          addFailure(status, new DomainCondition(FAILED).withReason(SERVER_POD).withMessage(getPodFailedMessage()));
        } else if (hasPodNotRunningInTime()) {
          addFailure(status, new DomainCondition(FAILED).withReason(SERVER_POD).withMessage(getPodNotRunningMessage()));
        } else if (hasPodNotReadyInTime()) {
          addFailure(status, new DomainCondition(FAILED).withReason(SERVER_POD).withMessage(getPodNotReadyMessage()));
        } else {
          status.removeConditionsMatching(c -> c.hasType(FAILED) && SERVER_POD == c.getReason());
          if (newConditions.allIntendedServersReady() && !stillHasPodPendingRestart(status)) {
            status.removeConditionsWithType(CONFIG_CHANGES_PENDING_RESTART);
          }
        }

        if (miiNondynamicRestartRequired() && isCommitUpdateOnly()) {
          setOnlineUpdateNeedRestartCondition(status);
        }
      }

      private String getPodNotRunningMessage() {
        return LOGGER.formatMessage(PODS_NOT_RUNNING);
      }

      private String getPodNotReadyMessage() {
        return LOGGER.formatMessage(PODS_NOT_READY);
      }

      private String getPodFailedMessage() {
        return LOGGER.formatMessage(PODS_FAILED);
      }

      class Conditions {

        private final DomainStatus status;
        private final ClusterCheck[] clusterChecks;
        private final List<DomainCondition> conditionList = new ArrayList<>();
        private final DomainStatus oldStatus;

        public Conditions(DomainStatus status) {
          this.status = status != null ? status : new DomainStatus();
          this.clusterChecks = createClusterChecks();
          boolean isCompleted = isProcessingCompleted() && !this.status.hasConditionWithType(FAILED);
          conditionList.add(new DomainCondition(COMPLETED).withStatus(isCompleted));
          conditionList.add(createAvailableCondition());
          if (allIntendedServersReady()) {
            this.status.removeConditionsWithType(ROLLING);
          }
          this.oldStatus = getStatus();
        }

        void apply() {
          conditionList.forEach(newCondition -> addCondition(status, newCondition));
          Arrays.stream(clusterChecks).forEach(ClusterCheck::addClusterConditions);
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
          return !c1.getType().equals(FAILED)
              || (c1.getReason() == c2.getReason() && getMessage(c1).equals(getMessage(c2)));
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
          return isDomainWithNeverStartPolicy() || (!haveTooManyReplicas() && allIntendedServersReady());
        }

        private boolean isDomainWithNeverStartPolicy() {
          return getDomain().getSpec().getServerStartPolicy() == ServerStartPolicy.NEVER;
        }

        private boolean haveTooManyReplicas() {
          return Arrays.stream(clusterChecks).anyMatch(ClusterCheck::hasTooManyReplicas);
        }

        private boolean allStartedServersAreComplete() {
          return expectedRunningServers.stream().allMatch(StatusUpdateContext.this::isServerComplete);
        }

        private boolean allNonStartedServersAreShutdown() {
          return getNonStartedServersWithState().stream().allMatch(StatusUpdateContext.this::isShutDown);
        }

        private List<String> getNonStartedServersWithState() {
          return serverState.keySet().stream().filter(this::isNonStartedServer).collect(Collectors.toList());
        }

        private boolean isNonStartedServer(String serverName) {
          return !isStartedServer(serverName);
        }

        private boolean haveServerData() {
          return StatusUpdateContext.this.serverState != null;
        }

        private @Nonnull List<String> getNonClusteredServers() {
          return expectedRunningServers.stream().filter(
              StatusUpdateContext.this::isNonClusteredServer).collect(Collectors.toList());
        }

        private boolean allIntendedServersReady() {
          return haveServerData()
              && allStartedServersAreComplete()
              && allNonStartedServersAreShutdown()
              && serversMarkedForRoll().isEmpty();
        }

        private DomainCondition createAvailableCondition() {
          final List<String> unreadyNonClusteredServers = getUnreadyNonClusteredServers();
          final List<ClusterCheck> unavailableClusters = getUnavailableClusters();

          if (noApplicationServersReady()) {
            return createNotAvailableCondition(LOGGER.formatMessage(NO_APPLICATION_SERVERS_READY));
          } else if (!unreadyNonClusteredServers.isEmpty()) {
            return createNotAvailableCondition(
                LOGGER.formatMessage(NON_CLUSTERED_SERVERS_NOT_READY, formatServers(unreadyNonClusteredServers)));
          } else if (!unavailableClusters.isEmpty()) {
            return createNotAvailableCondition(formatClusters(unavailableClusters));
          } else {
            return new DomainCondition(AVAILABLE).withStatus(true);
          }
        }

        private String formatServers(List<String> servers) {
          return OperatorUtils.joinListGrammaticallyWithLimit(SERVER_DISPLAY_LIMIT, servers);
        }

        private String formatClusters(List<ClusterCheck> unavailableClusters) {
          return OperatorUtils.joinListGrammaticallyWithLimit(
              CLUSTER_MESSAGE_LIMIT, createUnavailableClustersMessage(unavailableClusters));
        }

        @Nonnull
        private List<String> createUnavailableClustersMessage(List<ClusterCheck> unavailableClusters) {
          return unavailableClusters.stream().map(ClusterCheck::createNotReadyMessage).collect(Collectors.toList());
        }

        private DomainCondition createNotAvailableCondition(String message) {
          return new DomainCondition(AVAILABLE).withStatus(false).withMessage(message);
        }

        private List<String> getUnreadyNonClusteredServers() {
          return getNonClusteredServers().stream().filter(this::isServerNotReady).collect(Collectors.toList());
        }

        private List<ClusterCheck> getUnavailableClusters() {
          return Arrays.stream(clusterChecks).filter(this::isUnavailable).collect(Collectors.toList());
        }

        private boolean isServerNotReady(@Nonnull String serverName) {
          return !isServerReady(serverName);
        }

        private boolean isUnavailable(@Nonnull ClusterCheck clusterCheck) {
          return !clusterCheck.isAvailableOrIntentionallyShutdown();
        }

        private boolean noApplicationServersReady() {
          return isAdminOnlyDomain() && getInfo().getAdminServerName() != null
                  ? isServerNotReady(getInfo().getAdminServerName()) : noManagedServersReady();
        }

        private boolean noManagedServersReady() {
          return getServerStartupInfos()
                  .stream()
                  .map(DomainPresenceInfo.ServerInfo::getName)
                  .filter(this::isApplicationServer)
                  .noneMatch(StatusUpdateContext.this::isServerReady);
        }

        private Collection<DomainPresenceInfo.ServerStartupInfo> getServerStartupInfos() {
          return Optional.ofNullable(getInfo().getServerStartupInfo()).orElse(Collections.emptyList());
        }

        // when the domain start policy is ADMIN_ONLY, the admin server is considered to be an application server.
        private boolean isApplicationServer(String serverName) {
          return !isAdminServer(serverName);
        }

        private boolean isAdminOnlyDomain() {
          return isAdminOnlyServerStartPolicy()
                  || isOnlyAdminServerRunningInDomain();
        }

        private boolean isAdminOnlyServerStartPolicy() {
          return getDomain().getSpec().getServerStartPolicy() == ServerStartPolicy.ADMIN_ONLY;
        }

        private boolean isOnlyAdminServerRunningInDomain() {
          return expectedRunningServers.size() == 1
                  && expectedRunningServers.contains(getInfo().getAdminServerName());
        }

        private boolean isAdminServer(String serverName) {
          return status.getServers().stream()
              .filter(s -> s.getServerName().equals(serverName))
              .anyMatch(ServerStatus::isAdminServer);
        }
      }

      private class ClusterCheck {

        private final String clusterName;
        private final int maxReplicaCount;
        private final int specifiedReplicaCount;
        private final List<String> startedServers;
        private final List<String> nonStartedServers;
        private final ClusterStatus clusterStatus;

        ClusterCheck(DomainStatus domainStatus, ClusterStatus clusterStatus) {
          this.clusterStatus = clusterStatus;
          clusterName = clusterStatus.getClusterName();
          maxReplicaCount = clusterStatus.getMaximumReplicas();
          specifiedReplicaCount = clusterStatus.getReplicasGoal();
          startedServers = getStartedServersInCluster(domainStatus, clusterName);
          nonStartedServers = getNonStartedClusteredServers(domainStatus, clusterName);
        }

        private List<String> getStartedServersInCluster(DomainStatus domainStatus, String clusterName) {
          return domainStatus.getServers().stream()
              .filter(s -> clusterName.equals(s.getClusterName()))
              .map(ServerStatus::getServerName)
              .filter(expectedRunningServers::contains)
              .collect(Collectors.toList());
        }

        private List<String> getNonStartedClusteredServers(DomainStatus domainStatus, String clusterName) {
          return domainStatus.getServers().stream()
              .filter(s -> clusterName.equals(s.getClusterName()))
              .map(ServerStatus::getServerName)
              .filter(name -> !expectedRunningServers.contains(name))
              .collect(Collectors.toList());
        }

        boolean isAvailable() {
          return sufficientServersReady();
        }

        boolean isAvailableOrIntentionallyShutdown() {
          return isClusterIntentionallyShutDown() || sufficientServersReady();
        }

        private boolean isProcessingCompleted() {
          return !hasTooManyReplicas() && allIntendedClusterServersReady();
        }

        boolean hasTooManyReplicas() {
          return maxReplicaCount > 0 && specifiedReplicaCount > maxReplicaCount;
        }

        private boolean allIntendedClusterServersReady() {
          return isClusterIntentionallyShutDown()
              ? allNonStartedClusterServersAreShutdown()
              : isClusterStartupCompleted();
        }

        private boolean isClusterStartupCompleted() {
          return allStartedClusterServersAreComplete()
              && allNonStartedClusterServersAreShutdown()
              && clusteredServersMarkedForRoll().isEmpty();
        }

        private boolean allStartedClusterServersAreComplete() {
          return (startedServers.size() == specifiedReplicaCount)
              && startedServers.stream().allMatch(StatusUpdateContext.this::isServerComplete);
        }

        private boolean allNonStartedClusterServersAreShutdown() {
          return nonStartedServers.stream().allMatch(StatusUpdateContext.this::isShutDown);
        }

        private Set<String> clusteredServersMarkedForRoll() {
          return serversMarkedForRoll().stream()
              .filter(this::isServerInThisCluster)
              .collect(Collectors.toSet());
        }

        private boolean isServerInThisCluster(String serverName) {
          return clusterName.equals(getClusterName(serverName));
        }

        private boolean isClusterIntentionallyShutDown() {
          return getInfo().getServerStartupInfo() != null && startedServers.isEmpty();
        }

        private boolean sufficientServersReady() {
          return numServersReady() >= getSufficientServerCount();
        }

        private long getSufficientServerCount() {
          return max(1, MINIMUM_CLUSTER_COUNT, specifiedReplicaCount - maxUnavailable());
        }

        private int max(Integer... inputs) {
          return Arrays.stream(inputs).reduce(0, Math::max);
        }

        private long numServersReady() {
          return startedServers.stream()
              .filter(StatusUpdateContext.this::isServerReady)
              .count();
        }

        private int maxUnavailable() {
          return getInfo().getMaxUnavailable(clusterName);
        }

        void addClusterConditions() {
          clusterStatus.addCondition(
              new ClusterCondition(ClusterConditionType.AVAILABLE)
                  .withStatus(isAvailable()));
          clusterStatus.addCondition(
              new ClusterCondition(ClusterConditionType.COMPLETED)
                  .withStatus(isProcessingCompleted()));
        }

        private String createNotReadyMessage() {
          return LOGGER.formatMessage(CLUSTER_NOT_READY, clusterName, getSufficientServerCount(), numServersReady());
        }
      }

      private void setStatusDetails(DomainStatus status) {
        getDomainConfig()
            .map(c -> new DomainStatusFactory(getInfo(), c, this::isStartedServer))
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

        private String getNodeName(String serverName) {
          return Optional.ofNullable(getInfo().getServerPod(serverName))
              .map(V1Pod::getSpec)
              .map(V1PodSpec::getNodeName)
              .orElse(null);
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
          return (condition instanceof V1PodCondition)
              && "Ready".equals(((V1PodCondition)condition).getType());
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
        return getMiiNonDynamicChangesMethod() == COMMIT_UPDATE_ONLY;
      }

      private MIINonDynamicChangesMethod getMiiNonDynamicChangesMethod() {
        return DomainPresenceInfo.fromPacket(packet)
            .map(DomainPresenceInfo::getDomain)
            .map(DomainResource::getSpec)
            .map(DomainSpec::getConfiguration)
            .map(Configuration::getModel)
            .map(Model::getOnlineUpdate)
            .map(OnlineUpdate::getOnNonDynamicChanges)
            .orElse(MIINonDynamicChangesMethod.COMMIT_UPDATE_ONLY);
      }

      private void setOnlineUpdateNeedRestartCondition(DomainStatus status) {
        String dynamicUpdateRollBackFile = Optional.ofNullable((String) packet.get(
                ProcessingConstants.MII_DYNAMIC_UPDATE_WDTROLLBACKFILE))
            .orElse("");
        String message = String.format("%s%n%s",
            LOGGER.formatMessage(MessageKeys.MII_DOMAIN_UPDATED_POD_RESTART_REQUIRED), dynamicUpdateRollBackFile);
        updateDomainConditions(status, message);
      }

      private void updateDomainConditions(DomainStatus status, String message) {
        DomainCondition onlineUpdateCondition
            = new DomainCondition(CONFIG_CHANGES_PENDING_RESTART).withMessage(message).withStatus(true);

        status.removeConditionsWithType(CONFIG_CHANGES_PENDING_RESTART);
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

      private boolean isNonClusteredServer(String serverName) {
        return getClusterName(serverName) == null;
      }

      private Optional<WlsDomainConfig> getDomainConfig() {
        return Optional.ofNullable(config);
      }

      private boolean isServerReady(@Nonnull String serverName) {
        return RUNNING_STATE.equals(getRunningState(serverName))
              && PodHelper.hasReadyStatus(getInfo().getServerPod(serverName));
      }

      // A server is complete if it is ready, is in the WLS running state and
      // does not need to roll to accommodate changes to the domain.
      private boolean isServerComplete(@Nonnull String serverName) {
        return isServerReady(serverName)
            && isNotMarkedForRoll(serverName);
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

      private Set<String> serversMarkedForRoll() {
        return DomainPresenceInfo.fromPacket(packet)
            .map(DomainPresenceInfo::getServersToRoll)
            .map(Map::keySet)
            .orElse(Collections.emptySet());
      }

      private boolean isHasFailedPod() {
        return getInfo().getServerPods().anyMatch(PodHelper::isFailed);
      }

      private boolean hasPodNotReadyInTime() {
        return getInfo().getServerPods().anyMatch(this::isNotReadyInTime);
      }

      private boolean isNotReadyInTime(V1Pod pod) {
        return !PodHelper.isReady(pod) && hasBeenUnreadyExceededWaitTime(pod);
      }

      private boolean hasPodNotRunningInTime() {
        return getInfo().getServerPods().anyMatch(this::isNotRunningInTime);
      }

      private boolean isNotRunningInTime(V1Pod pod) {
        return PodHelper.isPending(pod) && hasBeenPendingExceededWaitTime(pod);
      }

      private boolean hasBeenUnreadyExceededWaitTime(V1Pod pod) {
        OffsetDateTime creationTime = getCreationTimestamp(pod);
        return SystemClock.now()
            .isAfter(getLater(creationTime, getReadyConditionLastTransitTimestamp(pod, creationTime))
                .plusSeconds(getMaxReadyWaitTime(pod)));
      }

      private boolean hasBeenPendingExceededWaitTime(V1Pod pod) {
        OffsetDateTime creationTime = getCreationTimestamp(pod);
        return SystemClock.now()
            .isAfter(getLater(creationTime, getReadyConditionLastTransitTimestamp(pod, creationTime))
                .plusSeconds(getMaxPendingWaitTime(pod)));
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
            .map(d -> getInfo().getServer(getServerName(pod), getClusterNameFromPod(pod)))
            .map(EffectiveServerSpec::getMaximumReadyWaitTimeSeconds)
            .orElse(TuningParameters.getInstance().getMaxReadyWaitTimeSeconds());
      }

      private long getMaxPendingWaitTime(V1Pod pod) {
        return Optional.ofNullable(getInfo().getDomain())
            .map(d -> getInfo().getServer(getServerName(pod), getClusterNameFromPod(pod)))
            .map(EffectiveServerSpec::getMaximumPendingWaitTimeSeconds)
            .orElse(TuningParameters.getInstance().getMaxPendingWaitTimeSeconds());
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
          return Optional.ofNullable(getInfo().getLastKnownServerStatus(serverName))
              .map(LastKnownStatus::getStatus).orElse(getStateFromPacket(serverName));
        }
      }

      private String getStateFromPacket(String serverName) {
        return Optional.ofNullable(serverState).map(m -> m.get(serverName)).orElse(null);
      }

      private boolean isDeleting(String serverName) {
        return Optional.ofNullable(getInfo().getServerPod(serverName)).map(PodHelper::isDeleting).orElse(false);
      }

      private boolean isStartedServer(String serverName) {
        return expectedRunningServers.contains(serverName);
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

  static class RemoveSelectedFailuresStep extends DomainStatusUpdaterStep {

    private final List<DomainFailureReason> selectedReasons;

    private RemoveSelectedFailuresStep(Step next, List<DomainFailureReason> selectedReasons) {
      super(next);
      this.selectedReasons = selectedReasons;
    }

    @Override
    protected String getDetail() {
      return selectedReasons.stream().map(DomainFailureReason::toString).collect(Collectors.joining(", "));
    }

    @Override
    void modifyStatus(DomainStatus status) {
      selectedReasons.forEach(status::markFailuresForRemoval);
      status.removeMarkedFailures();
    }
  }

  /**
   * A factory to update a DomainStatus object from a WlsDomainConfig. This includes the clusters and servers
   * that are expected to be running, but does not include actual runtime state.
   */
  static class DomainStatusFactory {

    private final DomainPresenceInfo info;
    private final WlsDomainConfig domainConfig;
    private final Function<String, Boolean> isServerConfiguredToRun;

    /**
     * Creates a factory to create a DomainStatus object, initialized with state from the configuration.
     *
     * @param info the domain presence info
     * @param domainConfig a WebLogic domain configuration
     * @param isServerConfiguredToRun returns true if the named server is configured to start
     */
    public DomainStatusFactory(
        @Nonnull DomainPresenceInfo info,
        @Nonnull WlsDomainConfig domainConfig,
        @Nonnull Function<String, Boolean> isServerConfiguredToRun) {
      this.info = info;
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
            .withStateGoal(getStateGoal())
            .withIsAdminServer(isAdminServer);
      }

      private String getStateGoal() {
        return wasServerStarted() ? getStateGoal(serverName, clusterName) : SHUTDOWN_STATE;
      }

      private String getStateGoal(String serverName, String clusterName) {
        return info.getServer(serverName, clusterName).getStateGoal();
      }

      private boolean wasServerStarted() {
        return isAdminServer || isServerConfiguredToRun.apply(serverName);
      }
    }

    private ClusterStatus createClusterStatus(WlsClusterConfig clusterConfig) {
      final String clusterName = clusterConfig.getName();
      return new ClusterStatus()
          .withClusterName(clusterName)
          .withLabelSelector(DOMAINUID_LABEL + "=" + info.getDomainUid() + "," + CLUSTERNAME_LABEL + "=" + clusterName)
          .withMaximumReplicas(clusterConfig.getClusterSize())
          .withMinimumReplicas(MINIMUM_CLUSTER_COUNT)
          .withReplicasGoal(getClusterSizeGoal(clusterName))
          .withObservedGeneration(getClusterObservedGeneration(clusterName));
    }

    private Integer getClusterSizeGoal(String clusterName) {
      return info.getReplicaCount(clusterName);
    }

    private Long getClusterObservedGeneration(String clusterName) {
      return Optional.ofNullable(info.getClusterResource(clusterName)).map(ClusterResource::getStatus)
              .map(ClusterStatus::getObservedGeneration).orElse(1L);
    }
  }

  static class FailureStep extends DomainStatusUpdaterStep implements StepWithRetryCount {
    private final DomainFailureReason reason;
    private final String message;
    private String jobUid;
    private final List<DomainFailureReason> removingReasons = new ArrayList<>();

    @Override
    public FailureStep forIntrospection(@Nullable V1Job introspectorJob) {
      jobUid = Optional.ofNullable(introspectorJob).map(V1Job::getMetadata).map(V1ObjectMeta::getUid).orElse("");
      return this;
    }

    private FailureStep(DomainFailureReason reason, String message, Step next) {
      super(next);
      this.reason = reason;
      this.message = message;
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
      return reason.toString();
    }

    @Override
    DomainStatusUpdaterContext createContext(Packet packet) {
      return new FailureStatusUpdaterContext(packet, this, reason, message, jobUid);
    }

    public Step removingOldFailures(DomainFailureReason... removingReasons) {
      this.removingReasons.addAll(Arrays.asList(removingReasons));
      return this;
    }

    class FailureStatusUpdaterContext extends DomainStatusUpdaterContext {
      private final DomainFailureReason reason;
      private final String message;
      private final String jobUid;

      public FailureStatusUpdaterContext(Packet packet, DomainStatusUpdaterStep statusUpdaterStep,
                                         DomainFailureReason reason, String message, String jobUid) {
        super(packet, statusUpdaterStep);
        this.jobUid = jobUid;
        this.reason = reason;
        this.message = message;
      }

      @Override
      void modifyStatus(DomainStatus status) {
        removingReasons.forEach(status::markFailuresForRemoval);
        addFailure(status, status.createAdjustedFailedCondition(reason, message, isInitializeDomainOnPV()));
        status.addCondition(new DomainCondition(COMPLETED).withStatus(false), isInitializeDomainOnPV());
        Optional.ofNullable(jobUid).ifPresent(status::setFailedIntrospectionUid);
        status.removeMarkedFailures();
      }

      private boolean isInitializeDomainOnPV() {
        return getDomain().isInitializeDomainOnPV();
      }

    }
  }

  public static class ClearCompletedConditionSteps extends StatusUpdateStep {
    public ClearCompletedConditionSteps() {
      super(null);
    }

    @Override
    DomainStatusUpdaterContext createContext(Packet packet) {
      return new UpdateCompletedConditionContext(packet, this);
    }

    static class UpdateCompletedConditionContext extends StatusUpdateStep.StatusUpdateContext {

      UpdateCompletedConditionContext(Packet packet, StatusUpdateStep statusUpdateStep) {
        super(packet, statusUpdateStep);
      }

      @Override
      void modifyStatus(DomainStatus status) {
        status.addCondition(new DomainCondition(COMPLETED).withStatus(false));
      }

      @Override
      boolean shouldSkipUpdate(Packet packet) {
        return false;
      }
    }
  }
}

