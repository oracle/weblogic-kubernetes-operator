// Copyright (c) 2019, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class EventProcessingTest {
  private static final String NS = "namespace";
  private static final String UID = "uid";
  private static final String ADMIN_NAME = "admin";
  private final V1ObjectReference serverReference =
      new V1ObjectReference().name(LegalNames.toEventName(UID, ADMIN_NAME)).kind("Pod");
  private final EventsV1Event event =
      new EventsV1Event()
          .metadata(new V1ObjectMeta().namespace(NS))
          .regarding(serverReference)
          .note(createReadinessProbeMessage(WebLogicConstants.UNKNOWN_STATE));
  private final List<Memento> mementos = new ArrayList<>();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final DomainResource domain = new DomainResource().withMetadata(new V1ObjectMeta().name(UID).namespace(NS));
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(createStrictStub(DomainProcessorDelegate.class));

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domains", presenceInfoMap));

    presenceInfoMap.put(NS, Map.of(UID, info));
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void onNewEventWithNoInvolvedObject_doNothing() {
    event.setRegarding(null);

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  void onNewEventWithNoMessage_doNothing() {
    event.setNote(null);

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  void onNewEventWithNonReadinessProbeMessage_doNothing() {
    event.setNote("ignore this");

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  void onNewEventWithReadinessProbeMessageButNoMatchingNamespace_doNothing() {
    presenceInfoMap.remove(NS);

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  void onNewEventWithNoMatchingDomain_doNothing() {
    presenceInfoMap.put(NS, Collections.emptyMap());

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  void onNewEventThatDoesNotMatchDomain_doNothing() {
    serverReference.setName(LegalNames.toEventName("uid2", ADMIN_NAME));

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  void onNewEventThatMatches_updateLastKnownStatus() {
    info.setServerPod(ADMIN_NAME, new V1Pod());

    dispatchEventWatch();

    assertThat(
        info.getLastKnownServerStatus(ADMIN_NAME).getStatus(),
        equalTo(WebLogicConstants.UNKNOWN_STATE));
  }

  @SuppressWarnings("SameParameterValue")
  private String createReadinessProbeMessage(String message) {
    return WebLogicConstants.READINESS_PROBE_NOT_READY_STATE + ":" + message;
  }

  private void dispatchEventWatch() {
    processor.dispatchEventWatch(WatchEvent.createAddedEvent(event).toWatchResponse());
  }
}
