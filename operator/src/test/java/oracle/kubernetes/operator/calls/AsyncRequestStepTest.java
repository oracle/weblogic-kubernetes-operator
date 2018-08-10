// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AsyncRequestStepTest {

  private static final int TIMEOUT_SECONDS = 10;
  private static final int MAX_RETRY_COUNT = 2;
  private FiberTestSupport testSupport = new FiberTestSupport();
  private RequestParams requestParams = new RequestParams("testcall", "junit", "testName", "body");
  private CallFactoryStub callFactory = new CallFactoryStub();
  private TestStep nextStep = new TestStep();
  private ClientPool helper = ClientPool.getInstance();
  private List<Memento> mementos = new ArrayList<>();

  private final AsyncRequestStep<Integer> asyncRequestStep =
      new AsyncRequestStep<>(
          nextStep,
          requestParams,
          callFactory,
          helper,
          TIMEOUT_SECONDS,
          MAX_RETRY_COUNT,
          null,
          null,
          null);

  @Before
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());

    testSupport.runSteps(asyncRequestStep);
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void afterFiberStarted_requestSent() {
    assertTrue(callFactory.invokedWith(requestParams));
  }

  @Test
  public void afterFiberStarted_timeoutStepScheduled() {
    assertTrue(testSupport.hasItemScheduledAt(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  public void afterTimeout_newRequestSent() {
    callFactory.clearRequest();

    testSupport.setTime(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertTrue(callFactory.invokedWith(requestParams));
  }

  @Test
  public void afterSuccessfulCallback_nextStepAppliedWithValue() {
    callFactory.sendSuccessfulCallback(17);

    assertThat(nextStep.result, equalTo(17));
  }

  @Test
  public void afterSuccessfulCallback_packetDoesNotContainsResponse() {
    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(17));

    assertThat(testSupport.getPacketComponents(), not(hasKey(RESPONSE_COMPONENT_NAME)));
  }

  @Test
  public void afterFailedCallback_packetContainsRetryStrategy() {
    sendFailedCallback(HttpURLConnection.HTTP_UNAVAILABLE);

    assertThat(
        testSupport.getPacketComponents().get(RESPONSE_COMPONENT_NAME).getSPI(RetryStrategy.class),
        notNullValue());
  }

  @SuppressWarnings("SameParameterValue")
  private void sendFailedCallback(int statusCode) {
    testSupport.schedule(
        () -> callFactory.sendFailedCallback(new ApiException("test failure"), statusCode));
  }

  @Test
  public void afterFailedCallback_retrySentAfterDelay() {
    sendFailedCallback(HttpURLConnection.HTTP_UNAVAILABLE);
    callFactory.clearRequest();

    testSupport.setTime(TIMEOUT_SECONDS - 1, TimeUnit.SECONDS);

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
    public NextAction onSuccess(Packet packet, CallResponse<Integer> callResponse) {
      result = callResponse.getResult();
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
    public CancellableCall generate(
        RequestParams requestParams, ApiClient client, String cont, ApiCallback<Integer> callback) {
      this.requestParams = requestParams;
      this.callback = callback;

      return new CancellableCallStub();
    }
  }

  static class CancellableCallStub implements CancellableCall {

    @Override
    public void cancel() {}
  }
}
