// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import oracle.kubernetes.operator.calls.AsyncRequestStep;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import java.util.List;
import java.util.Map;

/**
 * Step to receive response of Kubernetes API server call.
 * 
 * Most implementations will only need to implement {@link #onSuccess(Packet, Object, int, Map)}.
 * 
 * @param <T> Response type
 */
public abstract class ResponseStep<T> extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private Step previousStep = null;

  /**
   * Constructor specifying next step
   * @param nextStep Next step
   */
  public ResponseStep(Step nextStep) {
    super(nextStep);
  }

  public final void setPrevious(Step previousStep) {
    this.previousStep = previousStep;
  }
  
  @Override
  public final NextAction apply(Packet packet) {
    NextAction nextAction = null;
    
    @SuppressWarnings("unchecked")
    CallResponse<T> callResponse = packet.getSPI(CallResponse.class);
    if (callResponse != null) {
      if (callResponse.result != null) {
        // success
        nextAction = onSuccess(packet, callResponse.result, callResponse.statusCode, callResponse.responseHeaders);
      }
      if (callResponse.e != null) {
        // exception
        nextAction = onFailure(packet, callResponse.e, callResponse.statusCode, callResponse.responseHeaders);
      }
    }

    if (nextAction == null) {
      // call timed-out
      nextAction = doPotentialRetry(null, packet, null, 0, null);
      if (nextAction == null) {
        nextAction = doEnd(packet);
      }
    }
    
    if (previousStep != nextAction.getNext()) {
      // not a retry, clear out old response
      packet.getComponents().remove(AsyncRequestStep.RESPONSE_COMPONENT_NAME);
    }

    return nextAction;
  }
  
  /**
   * Returns next action that can be used to get the next batch of results from a
   * list search that specified a "continue" value.
   * @param packet Packet
   * @return Next action for list continue
   */
  protected final NextAction doContinueList(Packet packet) {
    RetryStrategy retryStrategy = packet.getSPI(RetryStrategy.class);
    if (retryStrategy != null) {
      retryStrategy.reset();
    }
    return doNext(previousStep, packet);
  }
  
  /**
   * Returns next action when the Kubernetes API server call should be retried, null otherwise.
   * @param conflictStep Conflict step
   * @param packet Packet
   * @param e API Exception received
   * @param statusCode HTTP status code received
   * @param responseHeaders HTTP response headers received
   * @return Next action for retry or null, if no retry is warranted
   */
  private NextAction doPotentialRetry(Step conflictStep, Packet packet, ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
    RetryStrategy retryStrategy = packet.getSPI(RetryStrategy.class);
    if (retryStrategy != null) {
      return retryStrategy.doPotentialRetry(conflictStep, packet, e, statusCode, responseHeaders);
    }
    
    LOGGER.warning(MessageKeys.ASYNC_NO_RETRY, e != null ? e.getMessage() : "", statusCode, responseHeaders != null ? responseHeaders.toString() : "");
    return null;
  }
  
  /**
   * Callback for API server call failure.  The ApiException and HTTP status code and response headers are provided; however,
   * these will be null or 0 when the client simply timed-out.
   * 
   * The default implementation tests if the request could be retried and, if not, ends fiber processing.
   * @param packet Packet
   * @param e API Exception
   * @param statusCode HTTP status code
   * @param responseHeaders HTTP response headers
   * @return Next action for fiber processing, which may be a retry
   */
  public NextAction onFailure(Packet packet, ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
    return onFailure(null, packet, e, statusCode, responseHeaders);
  }
  
  /**
   * Callback for API server call failure.  The ApiException and HTTP status code and response headers are provided; however,
   * these will be null or 0 when the client simply timed-out.
   * 
   * The default implementation tests if the request could be retried and, if not, ends fiber processing.
   * @param conflictStep Conflict step
   * @param packet Packet
   * @param e API Exception
   * @param statusCode HTTP status code
   * @param responseHeaders HTTP response headers
   * @return Next action for fiber processing, which may be a retry
   */
  public NextAction onFailure(Step conflictStep, Packet packet, ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
    NextAction nextAction = doPotentialRetry(conflictStep, packet, e, statusCode, responseHeaders);
    if (nextAction == null) {
      nextAction = doTerminate(e, packet);
    }
    return nextAction;
  }

  /**
   * Callback for API server call success.
   * 
   * @param packet Packet
   * @param result Result value
   * @param statusCode HTTP status code
   * @param responseHeaders HTTP response headers
   * @return Next action for fiber processing
   */
  public abstract NextAction onSuccess(Packet packet, T result, int statusCode, Map<String, List<String>> responseHeaders);
}
