// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoggingFormatterTest {

  private final LogRecord logRecord = new LogRecord(Level.INFO, "A simple one");
  private final LoggingFormatter formatter = new LoggingFormatter();
  private final FiberTestSupport testSupport = new FiberTestSupport();

  @Test
  public void formatLogRecordWithParameters() throws JsonProcessingException {
    logRecord.setMessage("Insert {0} and {1}");
    logRecord.setParameters(new Object[]{"here", "there"});

    assertThat(getFormattedMessage().get("message"), equalTo("Insert here and there"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getFormattedMessage() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(formatter.format(logRecord), Map.class);
  }

  @Test
  public void extractLogLevel() throws JsonProcessingException {
    logRecord.setLevel(Level.FINER);
    assertThat(getFormattedMessage().get("level"), equalTo("FINER"));

    logRecord.setLevel(Level.WARNING);
    assertThat(getFormattedMessage().get("level"), equalTo("WARNING"));
  }

  @Test
  public void extractSourceIndicators() throws JsonProcessingException {
    logRecord.setSourceClassName("theClass");
    logRecord.setSourceMethodName("itsMethod");

    assertThat(getFormattedMessage().get("class"), equalTo("theClass"));
    assertThat(getFormattedMessage().get("method"), equalTo("itsMethod"));
  }

  @Test
  public void whenThrowableNotApiException_extractItsName() throws JsonProcessingException {
    logRecord.setThrown(new RuntimeException("in the test"));

    assertThat(getFormattedMessage().get("exception"), containsString("java.lang.RuntimeException: in the test"));
  }

  @Test
  public void whenThrowableIsApiException_extractAttributes() throws JsonProcessingException {
    logRecord.setThrown(new ApiException(420, Collections.emptyMap(), "a response"));

    assertThat(getFormattedMessage(), allOf(hasEntry("code", "420"), hasEntry("body", "a response")));
  }

  @Test
  public void whenPacketLacksDomainPresence_domainUidIsEmpty() {
    assertThat(getFormattedMessageInFiber().get("domainUID"), equalTo(""));
  }

  @Test
  public void whenPacketContainsDomainPresence_retrieveDomainUid() {
    testSupport.addDomainPresenceInfo(new DomainPresenceInfo("test-ns", "test-uid"));

    assertThat(getFormattedMessageInFiber().get("domainUID"), equalTo("test-uid"));
  }

  @Test
  public void whenPacketContainsLoggingContext_retrieveDomainUid() {
    testSupport.addLoggingContext(new LoggingContext().domainUid("test-lc-uid"));

    assertThat(getFormattedMessageInFiber().get("domainUID"), equalTo("test-lc-uid"));
  }

  @Test
  public void whenNotInFiber_retrieveDomainUidFromThread() throws JsonProcessingException {
    try (LoggingContext stack = LoggingContext.setThreadContext().domainUid("test-uid")) {
      assertThat(getFormattedMessage().get("domainUID"), equalTo("test-uid"));
    }
  }

  @Test
  public void whenPacketContainsDomainPresenceAndLoggingContext_retrieveDomainUidFromDomainPresence() {
    testSupport.addDomainPresenceInfo(new DomainPresenceInfo("test-ns", "test-uid"));
    testSupport.addLoggingContext(new LoggingContext().namespace("test-lc-ns").domainUid("test-lc-uid"));

    assertThat(getFormattedMessageInFiber().get("domainUID"), equalTo("test-uid"));
  }

  @Test
  public void whenPacketContainsLoggingContextAndThreadLocalIsDefined_retrieveDomainUidFromLoggingContext() {
    testSupport.addLoggingContext(new LoggingContext().domainUid("test-lc-uid1"));
    try (LoggingContext stack = LoggingContext.setThreadContext().namespace("test-lc-tl-uid")) {
      assertThat(getFormattedMessageInFiber().get("domainUID"), equalTo("test-lc-uid1"));
    }
  }

  @Test
  public void whenThreadLocalDefinedAndPacketContainsNoDomainPresenceOrLoggingContext_retrieveDomainUidFromThread() {
    try (LoggingContext stack = LoggingContext.setThreadContext().domainUid("test-lc-tl-uid1")) {
      assertThat(getFormattedMessageInFiber().get("domainUID"), equalTo("test-lc-tl-uid1"));
    }
  }

  @Test
  public void whenThreadLocalDefinedAndPacketNoDomainPresenceAndLoggingContextNoDUid_retrieveDomainUidFromThread() {
    testSupport.addLoggingContext(new LoggingContext().namespace("test-lc-namespace"));
    try (LoggingContext stack = LoggingContext.setThreadContext().domainUid("test-lc-tl-uid1")) {
      assertThat(getFormattedMessageInFiber().get("domainUID"), equalTo("test-lc-tl-uid1"));
    }
  }

  @Test
  public void whenPacketLacksDomainPresence_domainNamespaceIsEmpty() {
    assertThat(getFormattedMessageInFiber().get("namespace"), equalTo(""));
  }

  @Test
  public void whenPacketContainsDomainPresence_retrieveDomainNamespace() {
    testSupport.addDomainPresenceInfo(new DomainPresenceInfo("test-ns", "test-uid"));

    assertThat(getFormattedMessageInFiber().get("namespace"), equalTo("test-ns"));
  }

  @Test
  public void whenPacketContainsDomainPresenceAndLoggingContext_retrieveDomainNamespaceFromDomainPresence() {
    testSupport.addDomainPresenceInfo(new DomainPresenceInfo("test-ns", "test-uid"));
    testSupport.addLoggingContext(new LoggingContext().namespace("test-lc-ns"));

    assertThat(getFormattedMessageInFiber().get("namespace"), equalTo("test-ns"));
  }

  @Test
  public void whenPacketContainsLoggingContext_retrieveDomainNamespace() {
    testSupport.addLoggingContext(new LoggingContext().namespace("test-lc-ns"));

    assertThat(getFormattedMessageInFiber().get("namespace"), equalTo("test-lc-ns"));
  }

  @Test
  public void whenPacketContainsLoggingContextAndThreadLocalIsDefined_retrieveNamespaceFromLoggingContext() {
    testSupport.addLoggingContext(new LoggingContext().namespace("test-lc-ns1"));
    try (LoggingContext stack = LoggingContext.setThreadContext().namespace("test-lc-tl-ns")) {
      assertThat(getFormattedMessageInFiber().get("namespace"), equalTo("test-lc-ns1"));
    }
  }

  @Test
  public void whenThreadLocalDefinedAndPacketContainsNoDomainPresenceOrLoggingContext_retrieveNamespaceFromThread() {
    try (LoggingContext stack = LoggingContext.setThreadContext().namespace("test-lc-tl-ns1")) {
      assertThat(getFormattedMessageInFiber().get("namespace"), equalTo("test-lc-tl-ns1"));
    }
  }

  @Test
  public void whenNotInFiber_retrieveNamespaceFromThread() throws JsonProcessingException {
    try (LoggingContext stack = LoggingContext.setThreadContext().namespace("test-lc-tl-ns1")) {
      assertThat(getFormattedMessage().get("namespace"), equalTo("test-lc-tl-ns1"));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getFormattedMessageInFiber() {
    final Packet packet = testSupport.runSteps(new LoggingStep());
    return (Map<String, String>) packet.get("MESSAGE");
  }

  class LoggingStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      try {
        packet.put("MESSAGE", getFormattedMessage());
        return doNext(packet);
      } catch (JsonProcessingException e) {
        return doTerminate(e, packet);
      }
    }
  }
}
