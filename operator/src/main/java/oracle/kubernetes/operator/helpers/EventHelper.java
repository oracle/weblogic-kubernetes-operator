// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.DomainNamespaces;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.common.logging.MessageKeys.BEGIN_MANAGING_NAMESPACE;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_AVAILABLE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_CHANGED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_COMPLETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_CREATED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_DELETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_INCOMPLETE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_UNAVAILABLE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_AVAILABLE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CHANGED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_COMPLETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CONVERSION_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CREATED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_DELETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_FAILED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_FAILURE_RESOLVED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INCOMPLETE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_ROLL_COMPLETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_ROLL_STARTING_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_UNAVAILABLE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.EXCEPTION;
import static oracle.kubernetes.common.logging.MessageKeys.NAMESPACE_WATCHING_STARTED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.NAMESPACE_WATCHING_STOPPED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.PERSISTENT_VOUME_CLAIM_BOUND_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.POD_CYCLE_STARTING_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.START_MANAGING_NAMESPACE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.START_MANAGING_NAMESPACE_FAILED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.STOP_MANAGING_NAMESPACE_EVENT_PATTERN;
import static oracle.kubernetes.operator.DomainProcessorImpl.getClusterEventK8SObjects;
import static oracle.kubernetes.operator.DomainProcessorImpl.getEventK8SObjects;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_CHANGED_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_DELETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_INCOMPLETE_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_UNAVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_DELETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILURE_RESOLVED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_INCOMPLETE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_UNAVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.EVENT_NORMAL;
import static oracle.kubernetes.operator.EventConstants.EVENT_WARNING;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STOPPED_EVENT;
import static oracle.kubernetes.operator.EventConstants.OPERATOR_WEBHOOK_COMPONENT;
import static oracle.kubernetes.operator.EventConstants.PERSISTENT_VOUME_CLAIM_BOUND_EVENT;
import static oracle.kubernetes.operator.EventConstants.POD_CYCLE_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.WEBHOOK_STARTUP_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STARTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STOPPED;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorPodName;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorPodUID;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookPodName;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookPodUID;

/** A Helper Class for the operator to create Kubernetes Events at the key points in the operator's workflow. */
public class EventHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Factory for {@link Step} that asynchronously create an event.
   *
   * @param eventData event data
   * @return Step for creating an event
   */
  public static Step createEventStep(EventData eventData) {
    return new CreateEventStep(eventData);
  }

  /**
   * Factory for {@link Step} that asynchronously create an event.
   *
   * @param domainNamespaces DomainNamespaces instance
   * @param eventData event item
   * @param next next step
   * @return Step for creating an event
   */
  public static Step createEventStep(DomainNamespaces domainNamespaces, EventData eventData, Step next) {
    return new CreateEventStep(domainNamespaces, eventData, next);
  }

  public static class CreateEventStep extends Step {
    private final EventData eventData;
    private final DomainNamespaces domainNamespaces;

    CreateEventStep(EventData eventData) {
      this(null, eventData, null);
    }

    CreateEventStep(DomainNamespaces domainNamespaces, EventData eventData, Step next) {
      super(next);
      this.eventData = eventData;
      this.domainNamespaces = domainNamespaces;
    }

    @Override
    protected String getDetail() {
      return eventData.eventItem.toString();
    }

    private static String getAdditionalMessageFromFailureReason(EventData eventData, DomainPresenceInfo info) {
      return Optional.ofNullable(eventData.failureReason)
          .map(f -> getLocalizedAdditionalMessage(f, info))
          .orElse("");
    }

    private static String getLocalizedAdditionalMessage(@Nonnull DomainFailureReason domainFailureReason,
        DomainPresenceInfo info) {
      return Optional.ofNullable(domainFailureReason.getEventSuggestion())
          .map(k -> LOGGER.formatMessage(k, domainFailureReason.getEventSuggestionParam(info)))
          .orElse("");
    }

    private static String getAdditionalMessage(EventData eventData, DomainPresenceInfo info) {
      return Optional.ofNullable(eventData.additionalMessage)
          .orElse(getAdditionalMessageFromFailureReason(eventData, info));
    }

    private static V1ObjectMeta createMetadata(EventData eventData) {
      final V1ObjectMeta metadata =
          new V1ObjectMeta().name(eventData.eventItem.generateEventName(eventData)).namespace(eventData.getNamespace());

      eventData.eventItem.addLabels(metadata, eventData);

      return metadata;
    }

    private static void addAdditionalMessage(@Nonnull EventData eventData, DomainPresenceInfo info) {
      eventData.additionalMessage(getAdditionalMessage(eventData, info));
    }

    private static CoreV1Event createEventModel(
        Packet packet,
        EventData eventData) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      EventItem eventItem = eventData.eventItem;
      eventData.domainPresenceInfo(info);
      addAdditionalMessage(eventData, info);

      return new CoreV1Event()
          .metadata(createMetadata(eventData))
          .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
          .reportingInstance(getOperatorPodName())
          .lastTimestamp(eventItem.getCurrentTimestamp())
          .type(eventItem.getType())
          .reason(eventItem.getReason())
          .message(eventItem.getMessage(eventData))
          .involvedObject(eventItem.createInvolvedObject(eventData))
          .count(1);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(createEventAPICall(createEventModel(packet, eventData)), packet);
    }

    private Step createEventAPICall(CoreV1Event event) {
      CoreV1Event existingEvent = getExistingEvent(event);
      return existingEvent != null ? createReplaceEventCall(event, existingEvent) : createCreateEventCall(event);
    }

    private Step createCreateEventCall(CoreV1Event event) {
      LOGGER.fine(MessageKeys.CREATING_EVENT, eventData.eventItem);
      event.firstTimestamp(event.getLastTimestamp());
      return new CallBuilder()
          .createEventAsync(
              event.getMetadata().getNamespace(),
              event,
              new CreateEventResponseStep(getNext()));
    }

    private Step createReplaceEventCall(CoreV1Event event, @NotNull CoreV1Event existingEvent) {
      LOGGER.fine(MessageKeys.REPLACING_EVENT, eventData.eventItem);
      existingEvent.count(Optional.ofNullable(existingEvent.getCount()).map(c -> c + 1).orElse(1));
      existingEvent.lastTimestamp(event.getLastTimestamp());
      return new CallBuilder()
          .replaceEventAsync(
              existingEvent.getMetadata().getName(),
              existingEvent.getMetadata().getNamespace(),
              existingEvent,
              new ReplaceEventResponseStep(this, existingEvent, getNext()));
    }

    private CoreV1Event getExistingEvent(CoreV1Event event) {
      return Optional.ofNullable(getEventK8SObjects(event))
          .map(o -> o.getExistingEvent(event)).orElse(null);
    }

    private class CreateEventResponseStep extends ResponseStep<CoreV1Event> {
      CreateEventResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<CoreV1Event> callResponse) {
        if (NAMESPACE_WATCHING_STARTED == eventData.eventItem) {
          LOGGER.info(BEGIN_MANAGING_NAMESPACE, eventData.getNamespace());
          domainNamespaces.shouldStartNamespace(eventData.getNamespace());
        }
        return doNext(packet);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<CoreV1Event> callResponse) {
        if (hasLoggedForbiddenNSWatchStoppedEvent(this, callResponse)) {
          return doNext(packet);
        }

        if (NAMESPACE_WATCHING_STARTED == eventData.eventItem) {
          clearNamespaceStartingFlag();
          if (isForbidden(callResponse)) {
            LOGGER.warning(MessageKeys.CREATING_EVENT_FORBIDDEN,
                eventData.eventItem.getReason(), eventData.getNamespace());
            return doNext(createStartManagingNSFailedEventStep(), packet);
          }
        }

        return super.onFailure(packet, callResponse);
      }

      private Step createStartManagingNSFailedEventStep() {
        return createEventStep(
            new EventData(EventItem.START_MANAGING_NAMESPACE_FAILED)
                .namespace(getOperatorNamespace()).resourceName(eventData.getNamespace()));
      }

      private void clearNamespaceStartingFlag() {
        if (domainNamespaces != null) {
          domainNamespaces.clearNamespaceStartingFlag(eventData.getNamespace());
        }
      }
    }

    private class ReplaceEventResponseStep extends ResponseStep<CoreV1Event> {
      final Step replaceEventStep;
      final CoreV1Event existingEvent;

      ReplaceEventResponseStep(Step replaceEventStep, CoreV1Event existingEvent, Step next) {
        super(next);
        this.existingEvent = existingEvent;
        this.replaceEventStep = replaceEventStep;
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<CoreV1Event> callResponse) {
        return doNext(packet);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<CoreV1Event> callResponse) {
        restoreExistingEvent();
        if (hasLoggedForbiddenNSWatchStoppedEvent(this, callResponse)) {
          return doNext(packet);
        }
        if (UnrecoverableErrorBuilder.isAsyncCallNotFoundFailure(callResponse)
            || UnrecoverableErrorBuilder.isAsyncCallConflictFailure(callResponse)) {
          return doNext(Step.chain(createCreateEventCall(createEventModel(packet, eventData)), getNext()), packet);
        } else if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
          return onFailureNoRetry(packet, callResponse);
        } else {
          return onFailure(createRetry(existingEvent), packet, callResponse);
        }
      }

      private void restoreExistingEvent() {
        if (existingEvent == null || existingEvent.getCount() == null) {
          return;
        }
        existingEvent.count(existingEvent.getCount() - 1);
      }

      Step createRetry(CoreV1Event event) {
        return Step.chain(createEventRefreshStep(event), replaceEventStep);
      }

      private Step createEventRefreshStep(CoreV1Event event) {
        return new CallBuilder().readEventAsync(
            event.getMetadata().getName(),
            event.getMetadata().getNamespace(),
            new ReadEventResponseStep(getNext()));
      }
    }

    private boolean isForbiddenForNSWatchStoppedEvent(
        ResponseStep<CoreV1Event> responseStep, CallResponse<CoreV1Event> callResponse) {
      return responseStep.isForbidden(callResponse) && NAMESPACE_WATCHING_STOPPED == eventData.eventItem;
    }

    private boolean hasLoggedForbiddenNSWatchStoppedEvent(
        ResponseStep<CoreV1Event> responseStep, CallResponse<CoreV1Event> callResponse) {
      if (isForbiddenForNSWatchStoppedEvent(responseStep, callResponse)) {
        LOGGER.info(MessageKeys.CREATING_EVENT_FORBIDDEN, eventData.eventItem.getReason(), eventData.getNamespace());
        return true;
      }
      return false;
    }

    private static class ReadEventResponseStep extends ResponseStep<CoreV1Event> {
      ReadEventResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<CoreV1Event> callResponse) {
        DomainProcessorImpl.updateEventK8SObjects(callResponse.getResult());
        return doNext(packet);
      }
    }
  }

  private static long generateRandomLong() {
    return (long) (Math.random() * Long.MAX_VALUE);
  }

  public enum EventItem {
    CLUSTER_AVAILABLE {
      @Override
      public String getReason() {
        return CLUSTER_AVAILABLE_EVENT;
      }

      @Override
      int getOrdering() {
        return 1;
      }

      @Override
      public String getPattern() {
        return CLUSTER_AVAILABLE_EVENT_PATTERN;
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForClusterResource(eventData);
      }
    },
    CLUSTER_COMPLETE {
      @Override
      public String getReason() {
        return CLUSTER_COMPLETED_EVENT;
      }

      @Override
      int getOrdering() {
        return 20;
      }

      @Override
      public String getPattern() {
        return CLUSTER_COMPLETED_EVENT_PATTERN;
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForClusterResource(eventData);
      }
    },
    CLUSTER_CREATED {
      @Override
      public String getReason() {
        return CLUSTER_CREATED_EVENT;
      }

      @Override
      public String getPattern() {
        return CLUSTER_CREATED_EVENT_PATTERN;
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForClusterResource(eventData);
      }
    },
    CLUSTER_DELETED {
      @Override
      public String getReason() {
        return CLUSTER_DELETED_EVENT;
      }

      @Override
      public String getPattern() {
        return CLUSTER_DELETED_EVENT_PATTERN;
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForClusterResource(eventData);
      }
    },
    CLUSTER_CHANGED {
      @Override
      public String getReason() {
        return CLUSTER_CHANGED_EVENT;
      }

      @Override
      public String getPattern() {
        return CLUSTER_CHANGED_EVENT_PATTERN;
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForClusterResource(eventData);
      }
    },
    CLUSTER_INCOMPLETE {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return CLUSTER_INCOMPLETE_EVENT;
      }

      @Override
      public String getPattern() {
        return CLUSTER_INCOMPLETE_EVENT_PATTERN;
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForClusterResource(eventData);
      }
    },
    CLUSTER_UNAVAILABLE {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return CLUSTER_UNAVAILABLE_EVENT;
      }

      @Override
      public String getPattern() {
        return CLUSTER_UNAVAILABLE_EVENT_PATTERN;
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForClusterResource(eventData);
      }
    },
    DOMAIN_AVAILABLE {
      @Override
      public String getReason() {
        return DOMAIN_AVAILABLE_EVENT;
      }

      @Override
      int getOrdering() {
        return 1;
      }

      @Override
      public String getPattern() {
        return DOMAIN_AVAILABLE_EVENT_PATTERN;
      }
    },
    DOMAIN_CREATED {
      @Override
      public String getReason() {
        return DOMAIN_CREATED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_CREATED_EVENT_PATTERN;
      }
    },
    DOMAIN_CHANGED {
      @Override
      public String getReason() {
        return DOMAIN_CHANGED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_CHANGED_EVENT_PATTERN;
      }
    },
    DOMAIN_COMPLETE {
      @Override
      public String getReason() {
        return DOMAIN_COMPLETED_EVENT;
      }

      @Override
      int getOrdering() {
        return 20;
      }

      @Override
      public String getPattern() {
        return DOMAIN_COMPLETED_EVENT_PATTERN;
      }
    },
    DOMAIN_DELETED {
      @Override
      public String getReason() {
        return DOMAIN_DELETED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_DELETED_EVENT_PATTERN;
      }
    },
    DOMAIN_FAILED {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return DOMAIN_FAILED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_FAILED_EVENT_PATTERN;
      }

      @Override
      public String getMessage(EventData eventData) {
        return getMessageFromFailedEventData(eventData);
      }
    },
    DOMAIN_FAILURE_RESOLVED {
      @Override
      public String getReason() {
        return DOMAIN_FAILURE_RESOLVED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_FAILURE_RESOLVED_EVENT_PATTERN;
      }
    },
    DOMAIN_UNAVAILABLE {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return DOMAIN_UNAVAILABLE_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_UNAVAILABLE_EVENT_PATTERN;
      }
    },
    DOMAIN_INCOMPLETE {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return DOMAIN_INCOMPLETE_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_INCOMPLETE_EVENT_PATTERN;
      }
    },
    DOMAIN_ROLL_STARTING {
      @Override
      public String getReason() {
        return DOMAIN_ROLL_STARTING_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_ROLL_STARTING_EVENT_PATTERN;
      }

      @Override
      public String getMessage(EventData eventData) {
        return getMessageFromEventData(eventData);
      }
    },
    DOMAIN_ROLL_COMPLETED {
      @Override
      public String getReason() {
        return EventConstants.DOMAIN_ROLL_COMPLETED_EVENT;
      }

      @Override
      int getOrdering() {
        return 10;
      }

      @Override
      public String getPattern() {
        return DOMAIN_ROLL_COMPLETED_EVENT_PATTERN;
      }
    },
    POD_CYCLE_STARTING {
      @Override
      public String getReason() {
        return POD_CYCLE_STARTING_EVENT;
      }

      @Override
      public String getPattern() {
        return POD_CYCLE_STARTING_EVENT_PATTERN;
      }

      @Override
      public String getMessage(EventData eventData) {
        return getMessageFromEventDataWithPod(eventData);
      }
    },
    NAMESPACE_WATCHING_STARTED {
      @Override
      public String getReason() {
        return EventConstants.NAMESPACE_WATCHING_STARTED_EVENT;
      }

      @Override
      public String getPattern() {
        return NAMESPACE_WATCHING_STARTED_EVENT_PATTERN;
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        addCreatedByOperatorLabel(metadata);
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createNSEventInvolvedObject(eventData);
      }
    },
    NAMESPACE_WATCHING_STOPPED {
      @Override
      public String getReason() {
        return NAMESPACE_WATCHING_STOPPED_EVENT;
      }

      @Override
      public String getPattern() {
        return NAMESPACE_WATCHING_STOPPED_EVENT_PATTERN;
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        addCreatedByOperatorLabel(metadata);
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createNSEventInvolvedObject(eventData);
      }
    },
    START_MANAGING_NAMESPACE {
      @Override
      public String getReason() {
        return EventConstants.START_MANAGING_NAMESPACE_EVENT;
      }

      @Override
      public String getPattern() {
        return START_MANAGING_NAMESPACE_EVENT_PATTERN;
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        addCreatedByOperatorLabel(metadata);
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createOperatorEventInvolvedObject();
      }

      @Override
      protected String generateEventName(EventData eventData) {
        return generateOperatorNSEventName(eventData);
      }
    },
    START_MANAGING_NAMESPACE_FAILED {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return EventConstants.START_MANAGING_NAMESPACE_FAILED_EVENT;
      }

      @Override
      public String getPattern() {
        return START_MANAGING_NAMESPACE_FAILED_EVENT_PATTERN;
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        addCreatedByOperatorLabel(metadata);
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createOperatorEventInvolvedObject();
      }

      @Override
      protected String generateEventName(EventData eventData) {
        return generateOperatorNSEventName(eventData);
      }
    },
    STOP_MANAGING_NAMESPACE {
      @Override
      public String getReason() {
        return EventConstants.STOP_MANAGING_NAMESPACE_EVENT;
      }

      @Override
      public String getPattern() {
        return STOP_MANAGING_NAMESPACE_EVENT_PATTERN;
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        addCreatedByOperatorLabel(metadata);
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createOperatorEventInvolvedObject();
      }

      @Override
      protected String generateEventName(EventData eventData) {
        return generateOperatorNSEventName(eventData);
      }
    },
    CONVERSION_WEBHOOK_FAILED {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return CONVERSION_WEBHOOK_FAILED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_CONVERSION_FAILED;
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        addCreatedByWebhookLabel(metadata);
      }

      @Override
      public String getMessage(EventData eventData) {
        return getMessageFromFailedConversionEventData(eventData);
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForWebhook();
      }
    },
    WEBHOOK_STARTUP_FAILED {
      @Override
      protected String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return WEBHOOK_STARTUP_FAILED_EVENT;
      }

      @Override
      public String getPattern() {
        return MessageKeys.WEBHOOK_STARTUP_FAILED;
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        addCreatedByWebhookLabel(metadata);
      }

      @Override
      public String getMessage(EventData eventData) {
        return getMessageFromFailedWebhookFailedEventData(eventData);
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return createInvolvedObjectForWebhook();
      }
    },
    PERSISTENT_VOLUME_CLAIM_BOUND {
      @Override
      public String getReason() {
        return PERSISTENT_VOUME_CLAIM_BOUND_EVENT;
      }

      @Override
      public String getPattern() {
        return PERSISTENT_VOUME_CLAIM_BOUND_EVENT_PATTERN;
      }
    };

    private static String getMessageFromEventData(EventData eventData) {
      return LOGGER.formatMessage(eventData.eventItem.getPattern(),
          eventData.getResourceNameFromInfo(), Optional.ofNullable(eventData.message).orElse(""));
    }

    private static String getMessageFromFailedEventData(EventData eventData) {
      return LOGGER.formatMessage(eventData.eventItem.getPattern(),
          eventData.getResourceNameFromInfo(),
          getEffectiveReasonDetail(eventData),
          getEffectiveMessage(eventData),
          getAdditionalMessage(eventData));
    }

    private static String getMessageFromFailedConversionEventData(EventData eventData) {
      return LOGGER.formatMessage(eventData.eventItem.getPattern(),
          Optional.ofNullable(eventData.message).orElse(""),
          getAdditionalMessage(eventData));
    }

    private static String getMessageFromFailedWebhookFailedEventData(EventData eventData) {
      return LOGGER.formatMessage(eventData.eventItem.getPattern(), eventData.getResourceName(),
          Optional.ofNullable(eventData.message).orElse(""),
          getAdditionalMessage(eventData));
    }

    private static String getAdditionalMessage(EventData eventData) {
      return Optional.ofNullable(eventData.additionalMessage).orElse("");
    }

    private static String getEffectiveMessage(EventData eventData) {
      return Optional.ofNullable(eventData.message).orElse(getFailureReason(eventData));
    }

    private static String getFailureReason(EventData eventData) {
      return Optional.ofNullable(eventData.failureReason)
          .map(DomainFailureReason::getEventError)
          .map(EventItem::getLocalizedMessage)
          .orElse("");
    }

    private static String getEffectiveReasonDetail(EventData eventData) {
      return eventData.message == null ? "" : getFailureReason(eventData);
    }

    private static String getMessageFromEventDataWithPod(EventData eventData) {
      return LOGGER.formatMessage(eventData.eventItem.getPattern(),
          eventData.getPodName(), Optional.ofNullable(eventData.message).orElse(""));
    }

    private static void addCreatedByOperatorLabel(V1ObjectMeta metadata) {
      metadata.putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    private static void addCreatedByWebhookLabel(V1ObjectMeta metadata) {
      metadata.putLabelsItem(LabelConstants.CREATEDBY_CONVERSION_WEBHOOK_LABEL, "true");
    }

    private static String getLocalizedMessage(String messageKey) {
      return LOGGER.formatMessage(messageKey);
    }

    protected String generateEventName(EventData eventData) {
      return String.format("%s.%s.%h%h",
          eventData.getResourceName(),
          eventData.eventItem.getReason(),
          System.currentTimeMillis(),
          generateRandomLong());
    }

    protected static V1ObjectReference createOperatorEventInvolvedObject() {
      return new V1ObjectReference()
          .name(getOperatorPodName())
          .namespace(getOperatorNamespace())
          .uid(getOperatorPodUID())
          .kind(KubernetesConstants.POD);
    }


    private static V1ObjectReference createNSEventInvolvedObject(EventData eventData) {
      return new V1ObjectReference()
          .name(eventData.getResourceName())
          .namespace(eventData.getNamespace())
          .kind(KubernetesConstants.NAMESPACE);
    }

    String generateOperatorNSEventName(EventData eventData) {
      return String.format("%s.%s.%s.%h%h",
          getOperatorPodName(),
          eventData.eventItem.getReason(),
          eventData.getResourceName(),
          System.currentTimeMillis(),
          generateRandomLong());
    }

    public String getMessage(EventData eventData) {
      return LOGGER.formatMessage(getPattern(), eventData.getResourceName());
    }

    OffsetDateTime getCurrentTimestamp() {
      return SystemClock.now();
    }

    void addLabels(V1ObjectMeta metadata, EventData eventData) {
      metadata
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, eventData.getResourceNameFromInfo())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    V1ObjectReference createInvolvedObject(EventData eventData) {
      return new V1ObjectReference()
          .name(eventData.getResourceNameFromInfo())
          .namespace(eventData.getNamespace())
          .kind(KubernetesConstants.DOMAIN)
          .apiVersion(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE)
          .uid(eventData.getUID());
    }

    String getType() {
      return EVENT_NORMAL;
    }

    /**
     * Returns the desired relative order of events generated. Events with lower numbers will be generated earlier.
     */
    int getOrdering() {
      return Integer.MAX_VALUE;
    }

    public abstract String getPattern();

    public abstract String getReason();
  }

  public static class EventData {
    private final EventItem eventItem;
    private DomainFailureReason failureReason;
    private String message;
    private String additionalMessage;
    private String namespace;
    private String resourceName;
    private String podName;
    private DomainPresenceInfo info;

    public EventData(EventItem eventItem) {
      this(eventItem, "");
    }

    public EventData(EventItem eventItem, String message) {
      this.eventItem = eventItem;
      this.message = message;
    }

    public EventData failureReason(DomainFailureReason failureReason) {
      this.failureReason = failureReason;
      return this;
    }

    public EventData message(String message) {
      this.message = message;
      return this;
    }

    public EventData additionalMessage(String additionalMessage) {
      this.additionalMessage = additionalMessage;
      return this;
    }

    public EventData namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public EventData resourceName(String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public EventData podName(String podName) {
      this.podName = podName;
      return this;
    }

    public EventData domainPresenceInfo(DomainPresenceInfo info) {
      this.info = info;
      return this;
    }

    public EventItem getItem() {
      return eventItem;
    }

    public int getOrdering() {
      return eventItem.getOrdering();
    }

    public String getNamespace() {
      return Optional.ofNullable(namespace).orElse(Optional.ofNullable(info)
          .map(DomainPresenceInfo::getNamespace).orElse(""));
    }

    public String getPodName() {
      return podName;
    }

    public String getResourceName() {
      return Optional.ofNullable(resourceName).orElse(this.getResourceNameFromInfo());
    }

    @Override
    public String toString() {
      return "EventData: " + eventItem + "; with reason: " + failureReason;
    }

    /**
     * Get the UID from the domain metadata.
     *
     * @return domain resource's UID
     */
    public String getUID() {
      return Optional.ofNullable(info)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getMetadata)
          .map(V1ObjectMeta::getUid)
          .orElse("");
    }

    String getResourceNameFromInfo() {
      return Optional.ofNullable(info).map(DomainPresenceInfo::getDomainUid).orElse("");
    }
  }

  /**
   * Create the conversion webhook related event.
   *
   * @param eventData Data for the event to be created.
   */
  public static void createConversionWebhookEvent(EventData eventData) {
    try {
      new CallBuilder()
          .createEvent(getWebhookNamespace(), createConversionWebhookEventModel(eventData.getItem(), eventData));
    } catch (ApiException apiException) {
      LOGGER.warning(EXCEPTION, apiException);
    }
  }

  private static CoreV1Event createConversionWebhookEventModel(EventItem eventItem, EventData eventData) {
    return new CoreV1Event()
                .reportingComponent(OPERATOR_WEBHOOK_COMPONENT)
                .reportingInstance(getWebhookPodName())
                .firstTimestamp(eventItem.getCurrentTimestamp())
                .lastTimestamp(eventItem.getCurrentTimestamp())
                .type(eventItem.getType())
                .reason(eventItem.getReason())
                .message(eventItem.getMessage(eventData))
                .involvedObject(eventItem.createInvolvedObject(eventData))
                .metadata(CreateEventStep.createMetadata(eventData))
                .count(1);
  }

  private static V1ObjectReference createInvolvedObjectForWebhook() {
    return new V1ObjectReference()
        .name(getWebhookPodName())
        .namespace(getWebhookNamespace())
        .kind(KubernetesConstants.POD)
        .uid(getWebhookPodUID())
        .apiVersion("v1");
  }

  private static V1ObjectReference createInvolvedObjectForClusterResource(EventData eventData) {
    return new V1ObjectReference()
        .name(eventData.getResourceName())
        .namespace(eventData.getNamespace())
        .kind(KubernetesConstants.CLUSTER)
        .uid(eventData.getUID())
        .apiVersion("v1");
  }

  /**
   * Factory for {@link Step} that asynchronously creates a Cluster Resource event.
   *
   * @param eventData event data
   * @return Step for creating an event
   */
  public static Step createClusterResourceEventStep(EventData eventData) {
    return new CreateClusterResourceEventStep(eventData);
  }

  public static class CreateClusterResourceEventStep extends Step {
    private final EventData eventData;

    CreateClusterResourceEventStep(EventData eventData) {
      this(eventData, null);
    }

    CreateClusterResourceEventStep(EventData eventData, Step next) {
      super(next);
      this.eventData = eventData;
    }

    @Override
    protected String getDetail() {
      return eventData.eventItem.toString();
    }

    private static CoreV1Event createEventModel(EventData eventData) {
      EventItem eventItem = eventData.eventItem;

      return new CoreV1Event()
          .metadata(CreateEventStep.createMetadata(eventData))
          .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
          .reportingInstance(getOperatorPodName())
          .lastTimestamp(eventItem.getCurrentTimestamp())
          .type(eventItem.getType())
          .reason(eventItem.getReason())
          .message(eventItem.getMessage(eventData))
          .involvedObject(eventItem.createInvolvedObject(eventData))
          .count(1);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(createEventAPICall(createEventModel(eventData)), packet);
    }

    private Step createEventAPICall(CoreV1Event event) {
      CoreV1Event existingEvent = getExistingClusterEvent(event);
      return existingEvent != null ? createReplaceEventCall(event, existingEvent) : createCreateEventCall(event);
    }

    private Step createCreateEventCall(CoreV1Event event) {
      LOGGER.fine(MessageKeys.CREATING_EVENT, eventData.eventItem);
      event.firstTimestamp(event.getLastTimestamp());
      return new CallBuilder()
          .createEventAsync(
              event.getMetadata().getNamespace(),
              event,
              new CreateClusterResourceEventResponseStep(getNext()));
    }

    private Step createReplaceEventCall(CoreV1Event event, @NotNull CoreV1Event existingEvent) {
      LOGGER.fine(MessageKeys.REPLACING_EVENT, eventData.eventItem);
      existingEvent.count(Optional.ofNullable(existingEvent.getCount()).map(c -> c + 1).orElse(1));
      existingEvent.lastTimestamp(event.getLastTimestamp());
      return new CallBuilder()
          .replaceEventAsync(
              existingEvent.getMetadata().getName(),
              existingEvent.getMetadata().getNamespace(),
              existingEvent,
              new ReplaceClusterResourceEventResponseStep(this, existingEvent, getNext()));
    }

    private CoreV1Event getExistingClusterEvent(CoreV1Event event) {
      return Optional.ofNullable(getClusterEventK8SObjects(event))
          .map(o -> o.getExistingEvent(event)).orElse(null);
    }

    private static class CreateClusterResourceEventResponseStep extends ResponseStep<CoreV1Event> {
      CreateClusterResourceEventResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<CoreV1Event> callResponse) {
        return doNext(packet);
      }
    }

    private class ReplaceClusterResourceEventResponseStep extends ResponseStep<CoreV1Event> {
      final Step replaceClusterEventStep;
      final CoreV1Event existingClusterEvent;

      ReplaceClusterResourceEventResponseStep(Step replaceClusterEventStep, CoreV1Event existingClusterEvent,
          Step next) {
        super(next);
        this.existingClusterEvent = existingClusterEvent;
        this.replaceClusterEventStep = replaceClusterEventStep;
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<CoreV1Event> callResponse) {
        return doNext(packet);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<CoreV1Event> callResponse) {
        restoreExistingClusterEvent();
        if (UnrecoverableErrorBuilder.isAsyncCallNotFoundFailure(callResponse)
            || UnrecoverableErrorBuilder.isAsyncCallConflictFailure(callResponse)) {
          return doNext(Step.chain(createCreateEventCall(createEventModel(eventData)), getNext()), packet);
        } else if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
          return onFailureNoRetry(packet, callResponse);
        } else {
          return onFailure(createClusterEventRetryStep(existingClusterEvent), packet, callResponse);
        }
      }

      private void restoreExistingClusterEvent() {
        if (existingClusterEvent == null || existingClusterEvent.getCount() == null) {
          return;
        }
        existingClusterEvent.count(existingClusterEvent.getCount() - 1);
      }

      Step createClusterEventRetryStep(CoreV1Event event) {
        return Step.chain(createClusterEventRefreshStep(event), replaceClusterEventStep);
      }

      private Step createClusterEventRefreshStep(CoreV1Event event) {
        return new CallBuilder().readEventAsync(
            event.getMetadata().getName(),
            event.getMetadata().getNamespace(),
            new ReadEventResponseStep(getNext()));
      }
    }

    private static class ReadEventResponseStep extends ResponseStep<CoreV1Event> {
      ReadEventResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<CoreV1Event> callResponse) {
        DomainProcessorImpl.updateEventK8SObjects(callResponse.getResult());
        return doNext(packet);
      }
    }
  }

  public static ClusterResourceEventData createClusterResourceEventData(
      EventItem clusterEvent, ClusterResource cluster, String domainUid) {
    return clusterEvent == null ? null : new ClusterResourceEventData(clusterEvent, cluster, domainUid);
  }

  public static class ClusterResourceEventData extends EventData {

    private final ClusterResource resource;
    private final String domainUid;

    private ClusterResourceEventData(EventItem eventItem, ClusterResource resource, String domainUid) {
      super(eventItem);
      this.resource = resource;
      this.domainUid = domainUid;
    }

    @Override
    public String getNamespace() {
      return Optional.of(resource)
          .map(ClusterResource::getMetadata)
          .map(V1ObjectMeta::getNamespace)
          .orElse("");
    }

    @Override
    public String getResourceName() {
      return Optional.of(resource)
          .map(ClusterResource::getMetadata)
          .map(V1ObjectMeta::getName)
          .orElse("");
    }

    @Override
    public String getUID() {
      return Optional.of(resource)
          .map(ClusterResource::getMetadata)
          .map(V1ObjectMeta::getUid)
          .orElse("");
    }

    @Override
    public String getResourceNameFromInfo() {
      return Optional.ofNullable(domainUid).orElse("");
    }
  }
}
