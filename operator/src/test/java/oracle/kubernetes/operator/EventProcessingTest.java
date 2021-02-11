// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class EventProcessingTest {
  private static final String NS = "namespace";
  private static final String UID = "uid";
  private static final String ADMIN_NAME = "admin";
  private final V1ObjectReference serverReference =
      new V1ObjectReference().name(LegalNames.toEventName(UID, ADMIN_NAME)).kind("Pod");
  private final CoreV1Event event =
      new CoreV1Event()
          .metadata(new V1ObjectMeta().namespace(NS))
          .involvedObject(serverReference)
          .message(createReadinessProbeMessage(WebLogicConstants.UNKNOWN_STATE));
  private final List<Memento> mementos = new ArrayList<>();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final Domain domain = new Domain().withMetadata(new V1ObjectMeta().name(UID).namespace(NS));
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(createStrictStub(DomainProcessorDelegate.class));

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));

    presenceInfoMap.put(NS, Map.of(UID, info));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void onNewEventWithNoInvolvedObject_doNothing() {
    event.setInvolvedObject(null);

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  public void onNewEventWithNoMessage_doNothing() {
    event.setMessage(null);

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  public void onNewEventWithNonReadinessProbeMessage_doNothing() {
    event.setMessage("ignore this");

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  public void onNewEventWithReadinessProbeMessageButNoMatchingNamespace_doNothing() {
    presenceInfoMap.remove(NS);

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  public void onNewEventWithNoMatchingDomain_doNothing() {
    presenceInfoMap.put(NS, Collections.emptyMap());

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  public void onNewEventThatDoesNotMatchDomain_doNothing() {
    serverReference.setName(LegalNames.toEventName("uid2", ADMIN_NAME));

    dispatchEventWatch();

    assertThat(info.getLastKnownServerStatus(ADMIN_NAME), nullValue());
  }

  @Test
  public void onNewEventThatMatches_updateLastKnownStatus() {
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
