// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.client;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.AsyncFiber;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.common.logging.MessageKeys.HTTP_METHOD_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.HTTP_REQUEST_GOT_THROWABLE;
import static oracle.kubernetes.common.logging.MessageKeys.HTTP_REQUEST_TIMED_OUT;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.typeCompatibleWith;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/**
 * Tests async processing of http requests during step processing.
 */
class HttpAsyncRequestStepTest {

  public static final String MANAGED_SERVER1 = "ms1";
  private final HttpResponseStepImpl responseStep = new HttpResponseStepImpl(null);
  private final Packet packet = new Packet();
  private final List<Memento> mementos = new ArrayList<>();
  private final TestFiber fiber = createStub(TestFiber.class);
  private final HttpResponse<String> response = createStub(HttpResponseStub.class, 200);
  private HttpAsyncRequestStep requestStep;
  private final CompletableFuture<HttpResponse<String>> responseFuture = new CompletableFuture<>();
  private final HttpAsyncRequestStep.FutureFactory futureFactory = r -> responseFuture;
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleMemento;

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(consoleMemento = TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, HTTP_METHOD_FAILED, HTTP_REQUEST_TIMED_OUT)
          .withLogLevel(Level.FINE)
          .ignoringLoggedExceptions(HttpAsyncRequestStep.HttpTimeoutException.class));
    mementos.add(StaticStubSupport.install(HttpAsyncRequestStep.class, "factory", futureFactory));
    mementos.add(TuningParametersStub.install());

    requestStep = createStep();
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void classImplementsStep() {
    assertThat(HttpAsyncRequestStep.class, typeCompatibleWith(Step.class));
  }

  @Test
  void constructorReturnsInstanceLinkedToResponse() {
    assertThat(requestStep.getNext(), sameInstance(responseStep));
  }

  @Nonnull
  private HttpAsyncRequestStep createStep() {
    return HttpAsyncRequestStep.createGetRequest("http://localhost/nothing", responseStep);
  }

  @Test
  void whenRequestMade_suspendProcessing() {
    NextAction action = requestStep.apply(packet);

    assertThat(FiberTestSupport.isSuspendRequested(action), is(true));
  }


  // Note: in the following tests, the call to doOnExit simulates the behavior of the fiber
  // when it receives a doSuspend()
  @Test
  void whenResponseReceived_resumeFiber() {
    final NextAction nextAction = requestStep.apply(packet);

    receiveResponseBeforeTimeout(nextAction, response);

    assertThat(fiber.wasResumed(), is(true));
  }

  private void receiveResponseBeforeTimeout(NextAction nextAction, HttpResponse<String> response) {
    responseFuture.complete(response);
    FiberTestSupport.doOnExit(nextAction, fiber);
  }

  private void completeWithThrowableBeforeTimeout(NextAction nextAction, Throwable throwable) {
    responseFuture.completeExceptionally(throwable);
    FiberTestSupport.doOnExit(nextAction, fiber);
  }


  @Test
  void whenErrorResponseReceived_logMessage() {
    final NextAction nextAction = requestStep.apply(packet);

    receiveResponseBeforeTimeout(nextAction, createStub(HttpResponseStub.class, 500));

    assertThat(logRecords, containsFine(HTTP_METHOD_FAILED));
  }

  @Test
  void whenThrowableResponseReceivedAndServerNotShuttingDownAndFailureCountExceedsThreshold_logMessage() {
    collectHttpWarningMessage();
    createDomainPresenceInfo(new V1Pod().metadata(new V1ObjectMeta()), 11).addToPacket(packet);

    final NextAction nextAction = requestStep.apply(packet);

    completeWithThrowableBeforeTimeout(nextAction, new Throwable("Test"));

    assertThat(logRecords, containsWarning(HTTP_REQUEST_GOT_THROWABLE));
  }

  private DomainPresenceInfo createDomainPresenceInfo(V1Pod msPod, int httpRequestFailureCount) {
    packet.put(ProcessingConstants.SERVER_NAME, MANAGED_SERVER1);
    DomainPresenceInfo info = new DomainPresenceInfo(NS, UID);
    info.setServerPod(MANAGED_SERVER1, msPod);
    info.setHttpRequestFailureCount(MANAGED_SERVER1, httpRequestFailureCount);
    return info;
  }

  private void collectHttpWarningMessage() {
    consoleMemento
        .collectLogMessages(logRecords, HTTP_REQUEST_GOT_THROWABLE)
        .withLogLevel(Level.WARNING);
  }

  @Test
  void whenThrowableResponseReceivedAndPodBeingDeletedByOperator_dontLogMessage() {
    collectHttpWarningMessage();
    DomainPresenceInfo info = createDomainPresenceInfo(new V1Pod().metadata(new V1ObjectMeta()), 0);
    info.setServerPodBeingDeleted(MANAGED_SERVER1, true);
    info.addToPacket(packet);
    final NextAction nextAction = requestStep.apply(packet);

    completeWithThrowableBeforeTimeout(nextAction, new Throwable("Test"));

    assertThat(logRecords, not(containsWarning(HTTP_REQUEST_GOT_THROWABLE)));
  }

  @Test
  void whenThrowableResponseReceivedAndPodHasDeletionTimestamp_dontLogMessage() {
    collectHttpWarningMessage();
    createDomainPresenceInfo(new V1Pod()
        .metadata(new V1ObjectMeta().deletionTimestamp(OffsetDateTime.now())), 0).addToPacket(packet);
    final NextAction nextAction = requestStep.apply(packet);

    completeWithThrowableBeforeTimeout(nextAction, new Throwable("Test"));

    assertThat(logRecords, not(containsWarning(HTTP_REQUEST_GOT_THROWABLE)));
  }

  @Test
  void whenThrowableResponseReceivedServerNotShuttingDownAndFailureCountLowerThanThreshold_dontLogMessage() {
    collectHttpWarningMessage();
    createDomainPresenceInfo(new V1Pod().metadata(new V1ObjectMeta()), 0).addToPacket(packet);
    final NextAction nextAction = requestStep.apply(packet);

    completeWithThrowableBeforeTimeout(nextAction, new Throwable("Test"));

    assertThat(logRecords, not(containsWarning(HTTP_REQUEST_GOT_THROWABLE)));
  }

  @Test
  void whenResponseReceived_populatePacket() {
    NextAction nextAction = requestStep.apply(packet);

    receiveResponseBeforeTimeout(nextAction, response);

    assertThat(getResponse(), sameInstance(response));
  }

  @Test
  void whenResponseTimesOut_resumeFiber() {
    consoleMemento.ignoreMessage(HTTP_REQUEST_TIMED_OUT);
    NextAction nextAction = requestStep.apply(packet);

    receiveTimeout(nextAction);

    assertThat(fiber.wasResumed(), is(true));
  }

  @Test
  void whenResponseTimesOut_packetHasNoResponse() {
    consoleMemento.ignoreMessage(HTTP_REQUEST_TIMED_OUT);
    HttpResponseStep.addToPacket(packet, response);
    NextAction nextAction = requestStep.apply(packet);

    receiveTimeout(nextAction);

    assertThat(getResponse(), nullValue());
  }

  @Test
  void whenResponseTimesOut_logWarning() {
    HttpResponseStep.addToPacket(packet, response);
    NextAction nextAction = requestStep.apply(packet);

    receiveTimeout(nextAction);

    assertThat(logRecords, containsFine(HTTP_REQUEST_TIMED_OUT));
  }

  @Test
  void whenTestSupportEnabled_retrieveCannedResult() throws NoSuchFieldException {
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://nowhere")).build();
    HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
    httpSupport.install();

    httpSupport.defineResponse(request, createStub(HttpResponseStub.class, 200, "It works for testing!"));

    HttpAsyncRequestStep step = HttpAsyncRequestStep.createGetRequest("http://nowhere", null);
    FiberTestSupport.doOnExit(step.apply(packet), fiber);

    assertThat(getResponse().body(), equalTo("It works for testing!"));
  }

  private void receiveTimeout(NextAction nextAction) {
    FiberTestSupport.doOnExit(nextAction, fiber);
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> getResponse() {
    return packet.getSpi(HttpResponse.class);
  }

  abstract static class TestFiber implements AsyncFiber {
    private Packet packet;
    private Throwable terminationCause;

    boolean wasResumed() {
      return terminationCause == null && packet != null;
    }

    @Override
    public void resume(Packet resumePacket) {
      packet = resumePacket;
    }

    @Override
    public void terminate(Throwable terminationCause, Packet packet) {
      this.terminationCause = terminationCause;
      this.packet = packet;
    }

    @Override
    public void scheduleOnce(long timeout, TimeUnit unit, Runnable runnable) {
      runnable.run();
    }
  }

}

