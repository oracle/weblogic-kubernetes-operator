// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;

import com.meterware.simplestub.Memento;

import java.net.HttpURLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

public class AsyncRequestStepTest {

  private static final int TIMEOUT_SECONDS = 10;
  private static final int MAX_RETRY_COUNT = 2;
  private Packet packet = new Packet();
  private Schedule schedule = createStrictStub(Schedule.class);
  private Engine engine = new Engine(schedule);
  private Fiber fiber = engine.createFiber();
  private RequestParams requestParams = new RequestParams("testcall", "junit", "testName", "body");
  private CallFactoryStub callFactory = new CallFactoryStub();
  private CompletionCallbackStub completionCallback = new CompletionCallbackStub();
  private TestStep nextStep = new TestStep();
  private ClientPool helper = ClientPool.getInstance();
  private List<Memento> mementos = new ArrayList<>();

  private final AsyncRequestStep<Integer> asyncRequestStep
        = new AsyncRequestStep<>(nextStep, requestParams, callFactory, helper, TIMEOUT_SECONDS, MAX_RETRY_COUNT, null, null, null);

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());

    fiber.start(asyncRequestStep, packet, completionCallback);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void afterFiberStarted_requestSent() throws Exception {
    assertTrue(callFactory.invokedWith(requestParams));
  }

  @Test
  public void afterFiber_timeoutStepScheduled() throws Exception {
    assertTrue(schedule.containsStepAt(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  public void afterTimeout_newRequestSent() throws Exception {
    callFactory.clearRequest();

    schedule.setTime(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertTrue(callFactory.invokedWith(requestParams));
  }

  @Test
  public void afterSuccessfulCallback_nextStepAppliedWithValue() throws Exception {
    callFactory.sendSuccessfulCallback(17);

    assertThat(nextStep.result, equalTo(17));
  }

  @Test
  public void afterSuccessfulCallback_packetDoesNotContainsResponse() throws Exception {
    schedule.execute(() -> callFactory.sendSuccessfulCallback(17));

    assertThat(packet.getComponents(), not(hasKey(RESPONSE_COMPONENT_NAME)));
  }

  @Test
  public void afterFailedCallback_packetContainsRetryStrategy() throws Exception {
    sendFailedCallback(HttpURLConnection.HTTP_UNAVAILABLE);

    assertThat(packet.getComponents().get(RESPONSE_COMPONENT_NAME).getSPI(RetryStrategy.class), notNullValue());
  }

  private void sendFailedCallback(int statusCode) {
    schedule.execute(() -> callFactory.sendFailedCallback(new ApiException("test failure"), statusCode));
  }

  @Test
  public void afterFailedCallback_retrySentAfterDelay() throws Exception {
    sendFailedCallback(HttpURLConnection.HTTP_UNAVAILABLE);
    callFactory.clearRequest();

    schedule.setTime(TIMEOUT_SECONDS-1, TimeUnit.SECONDS);

    assertTrue(callFactory.invokedWith(requestParams));
  }

  // todo tests
  // can new request clear timeout action?
  // what is accessContinue?
  // test CONFLICT (409) status
  // no retry if status not handled
  // test exceeded retry count


  static class TestStep extends ResponseStep<Integer> {
    private Integer result;

    TestStep() {
      super(null);
    }

    @Override
    public NextAction onSuccess(Packet packet, Integer result, int statusCode, Map<String, List<String>> responseHeaders) {
      this.result = result;
      return null;
    }
  }

  @SuppressWarnings("SameParameterValue")
  static class CallFactoryStub implements CallFactory<Integer> {

    private RequestParams requestParams;
    private ApiCallback<Integer> callback;

    void clearRequest() {
      requestParams = null;
    }

    boolean invokedWith(RequestParams requestParams) {
      return requestParams == this.requestParams;
    }

    void sendSuccessfulCallback(Integer callbackValue) {
      callback.onSuccess(callbackValue, HttpURLConnection.HTTP_OK, Collections.emptyMap());
    }

    void sendFailedCallback(ApiException exception, int statusCode) {
      callback.onFailure(exception, statusCode, Collections.emptyMap());
    }

    @Override
    public CancelableCall generate(RequestParams requestParams, ApiClient client, String cont, ApiCallback<Integer> callback) throws ApiException {
      this.requestParams = requestParams;
      this.callback = callback;

      return new CancelableCallStub();
    }
  }

  static class CancelableCallStub implements CancelableCall {
    private boolean canceled;

    @Override
    public void cancel() {
      canceled = true;
    }
  }

  static class CompletionCallbackStub implements Fiber.CompletionCallback {
    private Packet packet;
    private Throwable throwable;

    @Override
    public void onCompletion(Packet packet) {
      this.packet = packet;
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      this.packet = packet;
      this.throwable = throwable;
    }
  }

  static class ScheduledItem implements Comparable<ScheduledItem> {
    private long atTime;
    private Runnable runnable;

    ScheduledItem(long atTime, Runnable runnable) {
      this.atTime = atTime;
      this.runnable = runnable;
    }

    @Override
    public int compareTo(@Nonnull ScheduledItem o) {
      return Long.compare(atTime, o.atTime);
    }
  }

  static abstract class Schedule implements ScheduledExecutorService {
    /** current time in milliseconds. */
    private long currentTime = 0;

    private SortedSet<ScheduledItem> scheduledItems = new TreeSet<>();
    private Queue<Runnable> queue = new ArrayDeque<>();
    private Runnable current;

    @Override
    @Nonnull public ScheduledFuture<?> schedule(@Nonnull Runnable command, long delay, @Nonnull TimeUnit unit) {
      scheduledItems.add(new ScheduledItem(unit.toMillis(delay), command));
      return createStub(ScheduledFuture.class);
    }

    @Override
    public void execute(@Nullable Runnable command) {
      queue.add(command);
      if (current == null)
        runNextRunnable();
    }

    private void runNextRunnable() {
      while (queue.peek() != null) {
        current = queue.poll();
        current.run();
        current = null;
      }

    }

    void setTime(long time, TimeUnit unit) {
      long newTime = unit.toMillis(time);
      if (newTime < currentTime)
        throw new IllegalStateException("Attempt to move clock backwards from " + currentTime + " to " + newTime);

      for (Iterator<ScheduledItem> it = scheduledItems.iterator(); it.hasNext();) {
        ScheduledItem item = it.next();
        if (item.atTime > newTime) break;
        it.remove();
        execute(item.runnable);
      }

      currentTime = newTime;
    }

    boolean containsStepAt(int timeoutSeconds, TimeUnit unit) {
      for (ScheduledItem scheduledItem : scheduledItems)
        if (scheduledItem.atTime == unit.toMillis(timeoutSeconds)) return true;
      return false;
    }
  }
}