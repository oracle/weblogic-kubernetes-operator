// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
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
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.builders.CallParams;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * This class tests the AsyncRequestStep, used to dispatch requests to Kubernetes and respond asynchronously. The per-
 * test setup simulates a kubernetes call, which then blocks. Each test verifies the behavior as a result of a
 * different response.
 */
class AsyncRequestStepTest {

  private static final int TIMEOUT_SECONDS = 10;
  private static final int MAX_RETRY_COUNT = 2;
  private static final String CONTINUE = "continue-value";
  private static final String OP_NAME = "read";
  private static final String RESOURCE_TYPE = "zork";
  private static final String CALL_STRING = OP_NAME + StringUtils.capitalize(RESOURCE_TYPE);
  private static final String RESOURCE_NAME = "foo";
  private static final String EXPLANATION = "test failure";

  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final CallParams callParams = new CallParamsStub();
  private final RequestParams requestParams = new RequestParams(CALL_STRING, NS, RESOURCE_NAME, "body", callParams);
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
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  private static DomainList generateDomainList(int size) {
    List<DomainResource> domains = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      domains.add(new DomainResource().withMetadata(new V1ObjectMeta().name("domain" + i)));
    }
    return new DomainList().withItems(domains);
  }

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(ClientFactoryStub.install());
    mementos.add(SystemClockTestSupport.installClock());

    testSupport.runSteps(asyncRequestStep);
  }

  @AfterEach
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  void afterFiberStarted_requestSent() {
    assertThat(callFactory.invokedWith(requestParams), is(true));
  }

  @Test
  void afterFiberStarted_timeoutStepScheduled() {
    assertThat(testSupport.hasItemScheduledAt(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
  }

  @Test
  void afterSuccessfulCallback_nextStepAppliedWithValue() {
    callFactory.sendSuccessfulCallback(smallList);

    assertThat(nextStep.result, equalTo(smallList));
  }

  @Test
  void afterSuccessfulCallbackLargeList_nextStepAppliedWithValue() {
    callFactory.sendSuccessfulCallback(largeListPartOne);

    assertThat(nextStep.result, equalTo(largeListPartOne));
    assertThat(nextStep.nextAction.getNext(), instanceOf(AsyncRequestStep.class));
  }

  @Test
  void afterSuccessfulCallback_packetDoesNotContainsResponse() {
    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));

    assertThat(testSupport.getPacketComponents(), not(hasKey(RESPONSE_COMPONENT_NAME)));
  }

  @Test
  void afterFailedCallback_packetContainsRetryStrategy() {
    sendFailedCallback(HttpURLConnection.HTTP_UNAVAILABLE);

    assertThat(
        testSupport.getPacketComponents().get(RESPONSE_COMPONENT_NAME).getSpi(RetryStrategy.class),
        notNullValue());
  }

  private void sendFailedCallback(int statusCode) {
    sendFailedCallback(statusCode, EXPLANATION);
  }

  private void sendFailedCallback(int statusCode, String explanation) {
    testSupport.schedule(
        () -> callFactory.sendFailedCallback(new ApiException(explanation), statusCode));
  }

  @Test
  void afterFailedCallback_retrySentAfterDelay() {
    sendFailedCallback(HttpURLConnection.HTTP_UNAVAILABLE);
    callFactory.clearRequest();

    testSupport.setTime(TIMEOUT_SECONDS - 1, TimeUnit.SECONDS);

    assertThat(callFactory.invokedWith(requestParams), is(true));
  }

  @Test
  void afterMultipleRetriesAndSuccessfulCallback_nextStepAppliedWithValue() {
    sendMultipleFailedCallbackWithSetTime(0, 2);
    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));
    assertThat(nextStep.result, equalTo(smallList));
  }

  @Test
  void afterFailedCallback_failedStatusConditionSet() {
    testSupport.addDomainPresenceInfo(info);
    sendFailedCallback(HttpURLConnection.HTTP_BAD_REQUEST);

    assertThat(domain.getStatus().hasConditionWithType(FAILED), is(true));
    assertThat(domain.getStatus().getReason(), equalTo(KUBERNETES.toString()));
    assertThat(domain.getStatus().getMessage(), allOf(
        containsString(OP_NAME), containsString(RESOURCE_TYPE),
        containsString(RESOURCE_NAME), containsString(NS), containsString(EXPLANATION)
    ));
  }

  @Test
  void afterFailedCallback409_failedStatusConditionNotSet() {
    testSupport.addDomainPresenceInfo(info);
    sendFailedCallback(HttpURLConnection.HTTP_CONFLICT);

    assertThat(domain.getStatus().hasConditionWithType(FAILED), is(false));
  }

  @Test
  void whenDomainStatusIsNull_ignoreSuccess() {
    assertDoesNotThrow(() -> {
      info.getDomain().setStatus(null);
      testSupport.addDomainPresenceInfo(info);

      testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));
    });
  }

  @Test
  void afterMultipleRetriesAndSuccessfulCallback_failureIsRemovedFromStatus() {
    testSupport.addDomainPresenceInfo(info);
    info.getDomain().getStatus().addCondition(new DomainCondition(FAILED).withReason(INTROSPECTION));
    sendMultipleFailedCallbackWithSetTime(0, 2);

    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));

    assertThat(domain, hasCondition(FAILED).withReason(INTROSPECTION));
    assertThat(domain, not(hasCondition(FAILED).withReason(KUBERNETES)));
  }

  @Test
  void afterRetriesExhausted_fiberTerminatesWithException() {
    sendMultipleFailedCallbackWithSetTime(0, 3);

    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void afterMultipleTimeoutsAndSuccessfulCallback_nextStepAppliedWithValue() {
    sendMultipleFailedCallbackWithSetTime(HTTP_GATEWAY_TIMEOUT, 2);
    testSupport.schedule(() -> callFactory.sendSuccessfulCallback(smallList));
    assertThat(nextStep.result, equalTo(smallList));
  }

  @SuppressWarnings("SameParameterValue")
  private void sendMultipleFailedCallbackWithSetTime(int statusCode, int maxRetries) {
    for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
      sendFailedCallback(statusCode);
      testSupport.setTime(10 + retryCount * 10, TimeUnit.SECONDS);
    }
  }

  @Test
  void afterMultipleTimeoutsAndRetriesExhausted_fiberTerminatesWithException() {
    sendMultipleFailedCallbackWithSetTime(HTTP_GATEWAY_TIMEOUT, 3);

    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
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
