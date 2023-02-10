// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.introspection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodConditionBuilder;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.DomainProcessor;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTOR_POD_FAILED;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.LabelConstants.JOBNAME_LABEL;
import static oracle.kubernetes.operator.helpers.LegalNames.toJobIntrospectorName;
import static oracle.kubernetes.operator.introspection.IntrospectionStatusTest.IntrospectorJobPodBuilder.createPodAddedEvent;
import static oracle.kubernetes.operator.introspection.IntrospectionStatusTest.IntrospectorJobPodBuilder.createPodModifiedEvent;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** Tests updates to a domain status from progress of the introspection job. */
class IntrospectionStatusTest {
  private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
  private static final Random random = new Random();

  private static final String IMAGE_NAME = "abc";
  private static final String IMAGE_PULL_FAILURE = "ErrImagePull";
  private static final String UNSCHEDULABLE = "Unschedulable";
  private static final String IMAGE_PULL_BACKOFF = "ImagePullBackoff";
  private static final String DEADLINE_EXCEEDED = "DeadlineExceeded";
  private static final int MESSAGE_LENGTH = 10;
  private static final String JOB_NAME = toJobIntrospectorName(UID);
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final DomainProcessor processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));
  private final List<LogRecord> logRecords = new java.util.ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  private static String getMessage() {
    return randomAlphabetic(MESSAGE_LENGTH);
  }

  @BeforeEach
  void setUp() throws Exception {
    consoleHandlerMemento = TestUtils.silenceOperatorLogger();
    mementos.add(consoleHandlerMemento);
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domains", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());

    final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
    domain.setStatus(new DomainStatus().withMessage("").withReason(""));
    HashMap<String, DomainPresenceInfo> infoMap = new HashMap<>();
    infoMap.put(UID, new DomainPresenceInfo(domain));
    presenceInfoMap.put(NS, infoMap);
    testSupport.defineResources(domain);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenNewIntrospectorJobPodStatusContainerStatusesNull_dontUpdateDomainStatus() {
    IntrospectorJobPodBuilder.createPodAddedEvent().dispatch(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  private DomainResource getDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
  }

  @Test
  void whenNewIntrospectorJobPodStatusNull_dontUpdateDomainStatus() {
    IntrospectorJobPodBuilder.createPodAddedEvent().withNullStatus().dispatch(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenNewIntrospectorJobPodWhenInfoIsMissingFromMap_dontUpdateDomainStatus() {
    presenceInfoMap.get(NS).remove(UID);
    IntrospectorJobPodBuilder.createPodAddedEvent().withNullStatus().dispatch(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenNewIntrospectorJobPodWhenInfoMissingDomainObject_dontUpdateDomainStatus() {
    presenceInfoMap.get(NS).get(UID).setDomain(null);
    IntrospectorJobPodBuilder.createPodAddedEvent().withNullStatus().dispatch(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenNewIntrospectorJobPodJobNameLabelMissing_dontUpdateDomainStatus() {
    IntrospectorJobPodBuilder.createPodAddedEvent().withNullStatus().dispatchWithMissingJobLabel(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenNewIntrospectorJobPodWhenInfoIsMissingFromMapAndJobNameLabelMissing_dontUpdateDomainStatus() {
    presenceInfoMap.get(NS).remove(UID);
    IntrospectorJobPodBuilder.createPodAddedEvent().withNullStatus().dispatchWithMissingJobLabel(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenPodReady_dontLogFailureMessage() {
    consoleHandlerMemento.collectLogMessages(logRecords, INTROSPECTOR_POD_FAILED);

    createPodModifiedEvent().withReady().dispatch(processor);

    assertThat(logRecords, not(containsInfo(INTROSPECTOR_POD_FAILED)));
  }

  @Test
  void whenPodReady_dontUpdateDomainStatus() {
    createPodModifiedEvent().withReady().dispatch(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenPodReadyAfterFailure_doNotClearFailureStatus() {
    createPodAddedEvent().withWaitingState(IMAGE_PULL_FAILURE, getMessage()).dispatch(processor);

    createPodModifiedEvent().withReady().dispatch(processor);

    assertThat(getDomain(), not(hasStatus().withEmptyReasonAndMessage()));
  }

  @Test
  void whenPodWaitingWithNullReasonAndMessage_dontLogFailureMessage() {
    consoleHandlerMemento.collectLogMessages(logRecords, INTROSPECTOR_POD_FAILED);

    createPodModifiedEvent().withWaitingState(null, null).dispatch(processor);

    assertThat(logRecords, not(containsInfo(INTROSPECTOR_POD_FAILED)));
  }

  @Test
  void whenPodWaitingWithNullReasonAndMessage_dontSetStatus() {
    createPodModifiedEvent().withWaitingState(null, null).dispatch(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenPodWaitingWithNullReasonAndMessageAfterFailure_doNotClearFailureStatus() {
    createPodAddedEvent().withWaitingState(IMAGE_PULL_FAILURE, getMessage()).dispatch(processor);

    createPodModifiedEvent().withWaitingState(null, null).dispatch(processor);

    assertThat(getDomain(), not(hasStatus().withEmptyReasonAndMessage()));
  }

  @Test
  void whenPodPendingWithUnschedulableStatus_updateDomainStatus() {
    final String message = getMessage();
    createPodModifiedEvent().withCondition(UNSCHEDULABLE, message).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, message));
  }

  @Test
  void whenPodPendingWithUnschedulableStatus_logIntrospectionFailure() {
    consoleHandlerMemento.collectLogMessages(logRecords, INTROSPECTOR_POD_FAILED);

    createPodModifiedEvent().withCondition(UNSCHEDULABLE, getMessage()).dispatch(processor);

    assertThat(logRecords, containsInfo(INTROSPECTOR_POD_FAILED));
  }

  @Test
  void whenPodHasNonNullWaitingMessage_updateDomainStatus() {
    final String message = getMessage();
    createPodAddedEvent().withWaitingState(IMAGE_PULL_FAILURE, message).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, message));
  }

  @Test
  void whenPodHasInitContainerImagePullErrorWaitingMessage_updateDomainStatus() {
    final String message = getMessage();
    createPodAddedEvent().withInitContainerWaitingState(IMAGE_PULL_FAILURE, message).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, message));
  }

  @Test
  void whenNewIntrospectorJobPodCreatedWithNullWaitingMessage_dontUpdateDomainStatus() {
    createPodAddedEvent().withWaitingState(IMAGE_PULL_BACKOFF, null).dispatch(processor);

    assertThat(getDomain(), hasStatus().withEmptyReasonAndMessage());
  }

  @Test
  void whenIntrospectorJobPodPhaseFailed_updateDomainStatus() {
    final String message = getMessage();
    createPodModifiedEvent().withFailedPhase().withReason(DEADLINE_EXCEEDED).withMessage(message).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, message));
  }

  @Test
  void whenIntrospectorJobPodPhaseFailed_logErrorMessage() {
    consoleHandlerMemento.collectLogMessages(logRecords, INTROSPECTOR_POD_FAILED);

    createPodModifiedEvent().withFailedPhase().withReason("abc").withMessage(getMessage()).dispatch(processor);

    assertThat(logRecords, containsInfo(INTROSPECTOR_POD_FAILED));
  }

  @Test
  void whenIntrospectorPodHasTerminatedState_updateDomainStatus() {
    final String message = getMessage();
    createPodModifiedEvent().withTerminatedState(message).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, message));
  }

  @Test
  void whenIntrospectorPodHasTerminatedState_logPodStatus() {
    consoleHandlerMemento.collectLogMessages(logRecords, INTROSPECTOR_POD_FAILED);

    createPodModifiedEvent().withTerminatedState(getMessage()).dispatch(processor);

    assertThat(logRecords, containsInfo(INTROSPECTOR_POD_FAILED));
  }

  @Test
  void whenIntrospectPodHasWaitingState_dontLogErrorMessage() {
    consoleHandlerMemento.collectLogMessages(logRecords, INTROSPECTOR_POD_FAILED);
    createPodModifiedEvent().withWaitingState("aReason", "aMessage").dispatch(processor);

    assertThat(logRecords, not(containsInfo(INTROSPECTOR_POD_FAILED)));
  }

  @Test
  void whenIntrospectPodHasWaitingState_updateDomainStatus() {
    final String message = getMessage();
    createPodModifiedEvent().withWaitingState("aReason", message).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, message));
  }

  @Test
  void whenPodTerminatedByOperatorAfterDeadlineExceeded_retainInitialUpdatedStatus() {
    final String initialMessage = getMessage();
    getDomain().setStatus(new DomainStatus().withReason("ServerPod").withMessage(JOB_NAME + NS + initialMessage));

    final String newMessage = getMessage();
    createPodModifiedEvent().withFailedPhase()
          .withTerminatedByOperatorState(newMessage).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, initialMessage));
  }

  @Test
  void whenPodFailsAfterDeadlineExceeded_updateDomainStatus() {
    final String initialMessage = getMessage();
    getDomain().setStatus(new DomainStatus().withReason("ServerPod").withMessage(JOB_NAME + NS + initialMessage));
    
    final String newMessage = getMessage();
    createPodModifiedEvent().withFailedPhase()
          .withReason("Unknown").withMessage(newMessage).dispatch(processor);

    assertThat(getDomain(), hasStatus().withReason("ServerPod").withMessageContaining(JOB_NAME, NS, newMessage));
  }

  static class IntrospectorJobPodBuilder {

    enum EventType {
      ADDED {
        @Override
        Watch.Response<V1Pod> toWatchResponse(V1Pod pod) {
          return WatchEvent.createAddedEvent(pod).toWatchResponse();
        }
      },
      MODIFIED {
        @Override
        Watch.Response<V1Pod> toWatchResponse(V1Pod pod) {
          return WatchEvent.createModifiedEvent(pod).toWatchResponse();
        }
      };

      abstract Watch.Response<V1Pod> toWatchResponse(V1Pod pod);
    }

    private V1PodStatusBuilder builder;
    private final EventType eventType;

    private IntrospectorJobPodBuilder(EventType eventType) {
      this.eventType = eventType;
      builder = new V1PodStatusBuilder();
    }

    static IntrospectorJobPodBuilder createPodAddedEvent() {
      return new IntrospectorJobPodBuilder(EventType.ADDED);
    }

    static IntrospectorJobPodBuilder createPodModifiedEvent() {
      return new IntrospectorJobPodBuilder(EventType.MODIFIED);
    }

    IntrospectorJobPodBuilder withNullStatus() {
      builder = null;
      return this;
    }

    IntrospectorJobPodBuilder withFailedPhase() {
      builder.withPhase("Failed");
      return this;
    }

    IntrospectorJobPodBuilder withReason(String reason) {
      builder.withReason(reason);
      return this;
    }

    IntrospectorJobPodBuilder withMessage(String message) {
      builder.withMessage(message);
      return this;
    }

    IntrospectorJobPodBuilder withReady() {
      return withContainerState(true, null);
    }

    IntrospectorJobPodBuilder withWaitingState(String reason, String message) {
      return withContainerState(false, createWaitingState(reason, message));
    }

    IntrospectorJobPodBuilder withInitContainerWaitingState(String reason, String message) {
      return withInitContainerState(false, createWaitingState(reason, message));
    }

    IntrospectorJobPodBuilder withTerminatedState(String message) {
      return withContainerState(false, createTerminatedState("Error", message));
    }

    IntrospectorJobPodBuilder withTerminatedByOperatorState(String message) {
      return withReason(null).withMessage(null)
            .withContainerState(false, createTerminatedState("Error", message));
    }

    private IntrospectorJobPodBuilder withContainerState(boolean ready, V1ContainerState containerState) {
      builder.addNewContainerStatus()
            .withImage(IMAGE_NAME)
            .withName(JOB_NAME)
            .withReady(ready)
            .withState(containerState)
            .endContainerStatus();
      return this;
    }

    private IntrospectorJobPodBuilder withInitContainerState(boolean ready, V1ContainerState containerState) {
      builder.addNewInitContainerStatus()
              .withImage(IMAGE_NAME)
              .withName(JOB_NAME)
              .withReady(ready)
              .withState(containerState)
              .endInitContainerStatus();
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    IntrospectorJobPodBuilder withCondition(String reason, String message) {
      builder.withConditions(
            new V1PodConditionBuilder().withReason(reason).withMessage(message).build());
      return this;
    }

    void dispatch(DomainProcessor processor) {
      processor.dispatchPodWatch(eventType.toWatchResponse(build()));
    }

    void dispatchWithMissingJobLabel(DomainProcessor processor) {
      V1Pod pod = build();
      pod.getMetadata().getLabels().remove(JOBNAME_LABEL);
      processor.dispatchPodWatch(eventType.toWatchResponse(pod));
    }

    private V1Pod build() {
      return createIntrospectorJobPod().status(createStatus());
    }

    private static V1Pod createIntrospectorJobPod() {
      return AnnotationHelper.withSha256Hash(
          new V1Pod()
              .metadata(
                  withIntrospectorJobLabels(
                      new V1ObjectMeta()
                          .name(toJobIntrospectorName(UID) + getPodSuffix())
                          .namespace(NS)
                  ))
              .spec(new V1PodSpec()));
    }

    private static V1ObjectMeta withIntrospectorJobLabels(V1ObjectMeta meta) {
      return KubernetesUtils.withOperatorLabels(UID, meta)
          .putLabelsItem(JOBNAME_LABEL, toJobIntrospectorName(UID));
    }

    @Nullable
    private V1PodStatus createStatus() {
      return builder == null ? null : builder.build();
    }
  }

  @SuppressWarnings("SameParameterValue")
  private static V1ContainerState createTerminatedState(String reason, String message) {
    return new V1ContainerStateBuilder()
        .withNewTerminated().withReason(reason)
        .withMessage(message)
        .endTerminated()
        .build();
  }

  private static V1ContainerState createWaitingState(String reason, String message) {
    return new V1ContainerStateBuilder()
        .withNewWaiting()
        .withReason(reason)
        .withMessage(message)
        .endWaiting()
        .build();
  }

  private static String getPodSuffix() {
    return "-" + randomAlphabetic(5);
  }

  @SuppressWarnings("SameParameterValue")
  private static String randomAlphabetic(int length) {
    if (length < 1) {
      throw new IllegalArgumentException();
    }

    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int rndCharAt = random.nextInt(CHAR_LOWER.length());
      char rndChar = CHAR_LOWER.charAt(rndCharAt);
      sb.append(rndChar);
    }

    return sb.toString();
  }
}
