// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.HttpURLConnection;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;

/**
 * Support for writing unit tests that use CallBuilder to send requests that expect asynchronous
 * responses.
 *
 * <p>The setUp should invoke #installRequestStepFactory to modify CallBuilder for unit testing,
 * while capturing the memento to clean up during tearDown.
 *
 * <p>The test must define the simulated responses to the calls it will test, by invoking
 * #createCannedResponse, any qualifiers, and the result of the call. For example:
 *
 * <p>testSupport.createCannedResponse("deleteIngress") .withNamespace(namespace).withName(name)
 * .failingWith(HttpURLConnection.HTTP_CONFLICT);
 *
 * <p>will report a conflict failure on an attempt to delete an Ingress with the specified name and
 * namespace.
 *
 * <p>testSupport.createCannedResponse("listPod") .withNamespace(namespace) .returning(new
 * V1PodList().items(Arrays.asList(pod1, pod2, pod3);
 *
 * <p>will return a list of pods after a query with the specified namespace.
 */
@SuppressWarnings("unused")
public class AsyncCallTestSupport extends FiberTestSupport {

  private CallTestSupport callTestSupport = new CallTestSupport();

  /**
   * Installs a factory into CallBuilder to use canned responses.
   *
   * @return a memento which can be used to restore the production factory
   */
  public Memento installRequestStepFactory() {
    return new StepFactoryMemento(new RequestStepFactory());
  }

  /**
   * Primes CallBuilder to expect a request for the specified method.
   *
   * @param forMethod the name of the method
   * @return a canned response which may be qualified by parameters and defines how CallBuilder
   *     should react.
   */
  public CallTestSupport.CannedResponse createCannedResponse(String forMethod) {
    return callTestSupport.createCannedResponse(forMethod);
  }

  /**
   * Primes CallBuilder to expect a request for the specified method but not complain if none is
   * made.
   *
   * @param forMethod the name of the method
   * @return a canned response which may be qualified by parameters and defines how CallBuilder
   *     should react.
   */
  public CallTestSupport.CannedResponse createOptionalCannedResponse(String forMethod) {
    return callTestSupport.createOptionalCannedResponse(forMethod);
  }

  /**
   * Throws an exception if any of the defined responses were not invoked during the test. This
   * should generally be called during tearDown().
   */
  void verifyAllDefinedResponsesInvoked() {
    callTestSupport.verifyAllDefinedResponsesInvoked();
  }

  private static class StepFactoryMemento implements Memento {
    private AsyncRequestStepFactory oldFactory;

    StepFactoryMemento(AsyncRequestStepFactory newFactory) {
      oldFactory = CallBuilder.setStepFactory(newFactory);
    }

    @Override
    public void revert() {
      CallBuilder.resetStepFactory();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOriginalValue() {
      return (T) oldFactory;
    }
  }

  private static class CannedResponseStep extends Step {
    private CallTestSupport.CannedResponse cannedResponse;

    CannedResponseStep(Step next, CallTestSupport.CannedResponse cannedResponse) {
      super(next);
      this.cannedResponse = cannedResponse;
    }

    @Override
    public NextAction apply(Packet packet) {
      CallTestSupport.CannedResponse cannedResponse = this.cannedResponse;
      CallResponse callResponse = cannedResponse.getCallResponse();
      packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(callResponse));

      return doNext(packet);
    }
  }

  private static class SuccessStep<T> extends Step {
    private final T result;

    SuccessStep(T result, Step next) {
      super(next);
      this.result = result;
    }

    @Override
    public NextAction apply(Packet packet) {
      packet
          .getComponents()
          .put(
              RESPONSE_COMPONENT_NAME,
              Component.createFor(CallResponse.createSuccess(result, HttpURLConnection.HTTP_OK)));

      return doNext(packet);
    }
  }

  private static class FailureStep extends Step {
    private final int status;

    FailureStep(int status, Step next) {
      super(next);
      this.status = status;
    }

    @Override
    public NextAction apply(Packet packet) {
      packet
          .getComponents()
          .put(
              RESPONSE_COMPONENT_NAME,
              Component.createFor(CallResponse.createFailure(new ApiException(), status)));

      return doNext(packet);
    }
  }

  private class RequestStepFactory implements AsyncRequestStepFactory {

    @Override
    public <T> Step createRequestAsync(
        ResponseStep<T> next,
        RequestParams requestParams,
        CallFactory<T> factory,
        ClientPool helper,
        int timeoutSeconds,
        int maxRetryCount,
        String fieldSelector,
        String labelSelector,
        String resourceVersion) {
      return new CannedResponseStep(
          next, callTestSupport.getMatchingResponse(requestParams, fieldSelector, labelSelector));
    }
  }
}
