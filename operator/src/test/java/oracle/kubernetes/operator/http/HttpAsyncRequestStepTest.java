// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.pseudoserver.HttpUserAgentTest;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.work.AsyncFiber;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.logging.MessageKeys.HTTP_METHOD_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.HTTP_REQUEST_TIMED_OUT;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.typeCompatibleWith;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/**
 * Tests async processing of http requests during step processing.
 */
public class HttpAsyncRequestStepTest extends HttpUserAgentTest {

  private HttpResponseStepImpl responseStep = new HttpResponseStepImpl(null);
  private Packet packet = new Packet();
  private List<Memento> mementos = new ArrayList<>();
  private TestFiber fiber = createStub(TestFiber.class);
  private HttpResponse<String> response = createStub(HttpResponseStub.class, 200);
  private HttpAsyncRequestStep requestStep;
  private CompletableFuture<HttpResponse<String>> responseFuture = new CompletableFuture<>();
  private HttpAsyncRequestStep.FutureFactory futureFactory = r -> responseFuture;
  private Collection<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleMemento;

  /**
   * Checkstyle insists on a javadoc comment here. In a unit test *headdesk*.
   */
  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(consoleMemento = TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, HTTP_METHOD_FAILED, HTTP_REQUEST_TIMED_OUT)
          .withLogLevel(Level.FINE)
          .ignoringLoggedExceptions(HttpAsyncRequestStep.HttpTimeoutException.class));
    mementos.add(StaticStubSupport.install(HttpAsyncRequestStep.class, "factory", futureFactory));

    requestStep = createStep();
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void classImplementsStep() {
    assertThat(HttpAsyncRequestStep.class, typeCompatibleWith(Step.class));
  }

  @Test
  public void constructorReturnsInstanceLinkedToResponse() {
    assertThat(requestStep.getNext(), sameInstance(responseStep));
  }

  @NotNull
  private HttpAsyncRequestStep createStep() {
    return HttpAsyncRequestStep.createGetRequest("http://localhost/nothing", responseStep);
  }

  @Test
  public void whenRequestMade_suspendProcessing() {
    NextAction action = requestStep.apply(packet);

    assertThat(FiberTestSupport.isSuspendRequested(action), is(true));
  }


  // Note: in the following tests, the call to doOnExit simulates the behavior of the fiber
  // when it receives a doSuspend()
  @Test
  public void whenResponseReceived_resumeFiber() {
    final NextAction nextAction = requestStep.apply(packet);

    receiveResponseBeforeTimeout(nextAction, response);

    assertThat(fiber.wasResumed(), is(true));
  }

  private void receiveResponseBeforeTimeout(NextAction nextAction, HttpResponse<String> response) {
    responseFuture.complete(response);
    FiberTestSupport.doOnExit(nextAction, fiber);
  }

  @Test
  public void whenErrorResponseReceived_logMessage() {
    final NextAction nextAction = requestStep.apply(packet);

    receiveResponseBeforeTimeout(nextAction, createStub(HttpResponseStub.class, 500));

    assertThat(logRecords, containsFine(HTTP_METHOD_FAILED));
  }


  @Test
  public void whenResponseReceived_populatePacket() {
    NextAction nextAction = requestStep.apply(packet);

    receiveResponseBeforeTimeout(nextAction, response);

    assertThat(getResponse(), sameInstance(response));
  }

  @Test
  public void whenResponseTimesOut_resumeFiber() {
    consoleMemento.ignoreMessage(HTTP_REQUEST_TIMED_OUT);
    NextAction nextAction = requestStep.apply(packet);

    receiveTimeout(nextAction);

    assertThat(fiber.wasResumed(), is(true));
  }

  @Test
  public void whenResponseTimesOut_packetHasNoResponse() {
    consoleMemento.ignoreMessage(HTTP_REQUEST_TIMED_OUT);
    HttpResponseStep.addToPacket(packet, response);
    NextAction nextAction = requestStep.apply(packet);

    receiveTimeout(nextAction);

    assertThat(getResponse(), nullValue());
  }

  @Test
  public void whenResponseTimesOut_logWarning() {
    HttpResponseStep.addToPacket(packet, response);
    NextAction nextAction = requestStep.apply(packet);

    receiveTimeout(nextAction);

    assertThat(logRecords, containsFine(HTTP_REQUEST_TIMED_OUT));
  }

  @Test
  public void whenTestSupportEnabled_retrieveCannedResult() throws NoSuchFieldException {
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

    boolean wasTerminated() {
      return terminationCause != null;
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

