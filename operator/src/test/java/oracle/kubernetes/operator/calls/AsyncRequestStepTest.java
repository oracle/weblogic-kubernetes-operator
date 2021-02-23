// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.operator.builders.CallParams;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

public class AsyncRequestStepTest {

  private static final int TIMEOUT_SECONDS = 10;
  private static final int MAX_RETRY_COUNT = 2;
  private static final String CONTINUE = "continue-value";

  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final CallParams callParams = new CallParamsStub();
  private final RequestParams requestParams
      = new RequestParams("testcall", "junit", "testName", "body", callParams);
  private final CallFactoryStub callFactory = new CallFactoryStub();
  private final TestStep nextStep = new TestStep();
  private final ClientPool helper = ClientPool.getInstance();
  private final AsyncRequestStep<DomainList> asyncRequestStep =
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
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainList smallList = generateDomainList(5);
  private final DomainList largeListPartOne
      = generateDomainList(50).withMetadata(new V1ListMeta()._continue(CONTINUE));

  private static DomainList generateDomainList(int size) {
    List<Domain> domains = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      domains.add(new Domain().withMetadata(new V1ObjectMeta().name("domain" + i)));
    }
    return new DomainList().withItems(domains);
  }

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(ClientFactoryStub.install());

    testSupport.runSteps(asyncRequestStep);
  }

  @AfterEach
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
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
    callFactory.sendSuccessfulCallback(smallList);

    assertThat(nextStep.result, equalTo(smallList));
  }

  @Test
  public void afterSuccessfulCallbackLargeList_nextStepAppliedWithValue() {
    callFactory.sendSuccessfulCallback(largeListPartOne);

    assertThat(nextStep.result, equalTo(largeListPartOne));
    assertThat(nextStep.nextAction.getNext(), instanceOf(AsyncRequestStep.class));
  }

  @Test
  public void afterSuccessfulCallback_packetDoesNotContainsResponse() {
    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));

    assertThat(testSupport.getPacketComponents(), not(hasKey(RESPONSE_COMPONENT_NAME)));
  }

  @Test
  public void afterFailedCallback_packetContainsRetryStrategy() {
    sendFailedCallback(HttpURLConnection.HTTP_UNAVAILABLE);

    assertThat(
        testSupport.getPacketComponents().get(RESPONSE_COMPONENT_NAME).getSpi(RetryStrategy.class),
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

  @Test
  public void afterMultipleRetriesAndSuccessfulCallback_nextStepAppliedWithValue() {
    sendMultipleFailedCallback(0, 2);
    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));
    assertThat(nextStep.result, equalTo(smallList));
  }

  @SuppressWarnings("SameParameterValue")
  private void sendMultipleFailedCallback(int statusCode, int maxRetries) {
    for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
      testSupport.schedule(
          () -> callFactory.sendFailedCallback(new ApiException("test failure"), statusCode));
    }
  }

  @Test
  public void afterRetriesExhausted_fiberTerminatesWithException() {
    sendMultipleFailedCallback(0, 3);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void afterMultipleTimeoutsAndSuccessfulCallback_nextStepAppliedWithValue() {
    sendMultipleFailedCallbackWithSetTime(504, 2);
    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));
    assertThat(nextStep.result, equalTo(smallList));
  }

  @SuppressWarnings("SameParameterValue")
  private void sendMultipleFailedCallbackWithSetTime(int statusCode, int maxRetries) {
    for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
      testSupport.schedule(
          () -> callFactory.sendFailedCallback(new ApiException("test failure"), statusCode));
      testSupport.setTime(10 + retryCount * 10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void afterMultipleTimeoutsAndRetriesExhausted_fiberTerminatesWithException() {
    sendMultipleFailedCallbackWithSetTime(504, 3);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  // todo tests
  // can new request clear timeout action?
  // what is accessContinue?
  // test CONFLICT (409) status
  // no retry if status not handled

  static class TestStep extends ResponseStep<DomainList> {
    private DomainList result;
    private NextAction nextAction;

    TestStep() {
      super(null);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainList> callResponse) {
      result = callResponse.getResult();
      nextAction = doContinueListOrNext(callResponse, packet);
      return nextAction;
    }
  }

  @SuppressWarnings("SameParameterValue")
  static class CallFactoryStub implements CallFactory<DomainList> {

    private RequestParams requestParams;
    private ApiCallback<DomainList> callback;

    void clearRequest() {
      requestParams = null;
    }

    boolean invokedWith(RequestParams requestParams) {
      return requestParams == this.requestParams;
    }

    void sendSuccessfulCallback(DomainList callbackValue) {
      callback.onSuccess(callbackValue, HttpURLConnection.HTTP_OK, Collections.emptyMap());
    }

    void sendFailedCallback(ApiException exception, int statusCode) {
      callback.onFailure(exception, statusCode, Collections.emptyMap());
    }

    @Override
    public CancellableCall generate(
        RequestParams requestParams, ApiClient client, String cont, ApiCallback<DomainList> callback) {
      this.requestParams = requestParams;
      this.callback = callback;

      return new CancellableCallStub();
    }
  }

  static class CancellableCallStub implements CancellableCall {

    @Override
    public void cancel() {
    }
  }

  static class CallParamsStub implements CallParams {
    private static final Integer LIMIT = 50;
    private static final Integer TIMEOUT_SECONDS = 30;

    @Override
    public Integer getLimit() {
      return LIMIT;
    }

    @Override
    public Integer getTimeoutSeconds() {
      return TIMEOUT_SECONDS;
    }

    @Override
    public String getFieldSelector() {
      return null;
    }

    @Override
    public String getLabelSelector() {
      return null;
    }

    @Override
    public String getPretty() {
      return null;
    }

    @Override
    public String getResourceVersion() {
      return null;
    }
  }
}
