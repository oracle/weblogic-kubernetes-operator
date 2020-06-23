// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ListMeta;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.AsyncFiber;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_COMPONENT_NAME;
import static oracle.kubernetes.operator.calls.CallResponse.createFailure;
import static oracle.kubernetes.operator.calls.CallResponse.createSuccess;
import static oracle.kubernetes.operator.logging.MessageKeys.ASYNC_SUCCESS;

/**
 * A Step driven by an asynchronous call to the Kubernetes API, which results in a series of
 * callbacks until canceled.
 */
public class AsyncRequestStep<T> extends Step implements RetryStrategyListener {
  public static final String RESPONSE_COMPONENT_NAME = "response";
  private static final Random R = new Random();
  private static final int HIGH = 200;
  private static final int LOW = 10;
  private static final int SCALE = 100;
  private static final int MAX = 10000;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final ClientPool helper;
  private final RequestParams requestParams;
  private final CallFactory<T> factory;
  private final int maxRetryCount;
  private final String fieldSelector;
  private final String labelSelector;
  private final String resourceVersion;
  private int timeoutSeconds;

  /**
   * Construct async step.
   *
   * @param next Next
   * @param requestParams Request parameters
   * @param factory Factory
   * @param helper Client pool
   * @param timeoutSeconds Timeout
   * @param maxRetryCount Max retry count
   * @param fieldSelector Field selector
   * @param labelSelector Label selector
   * @param resourceVersion Resource version
   */
  public AsyncRequestStep(
      ResponseStep<T> next,
      RequestParams requestParams,
      CallFactory<T> factory,
      ClientPool helper,
      int timeoutSeconds,
      int maxRetryCount,
      String fieldSelector,
      String labelSelector,
      String resourceVersion) {
    super(next);
    this.helper = helper;
    this.requestParams = requestParams;
    this.factory = factory;
    this.timeoutSeconds = timeoutSeconds;
    this.maxRetryCount = maxRetryCount;
    this.fieldSelector = fieldSelector;
    this.labelSelector = labelSelector;
    this.resourceVersion = resourceVersion;

    // TODO, RJE: consider reimplementing the connection between the response and request steps using just
    // elements in the packet so that all step implementations are stateless.
    next.setPrevious(this);
  }

  private static String accessContinue(Object result) {
    String cont = "";
    if (result != null) {
      try {
        Method m = result.getClass().getMethod("getMetadata");
        Object meta = m.invoke(result);
        if (meta instanceof V1ListMeta) {
          return ((V1ListMeta) meta).getContinue();
        }
      } catch (NoSuchMethodException
          | SecurityException
          | IllegalAccessException
          | IllegalArgumentException
          | InvocationTargetException e) {
        // no-op, no-log
      }
    }
    return cont;
  }

  @Override
  protected String getDetail() {
    return requestParams.call;
  }

  public RequestParams getRequestParams() {
    return requestParams;
  }

  @Override
  public void listenTimeoutDoubled() {
    timeoutSeconds *= 2;
  }

  class AsyncRequestStepProcessing {

    final Packet packet;
    final RetryStrategy retryStrategy;
    final String cont;
    final AtomicBoolean didResume = new AtomicBoolean(false);
    final ApiClient client;

    public AsyncRequestStepProcessing(Packet packet, RetryStrategy retry, String cont) {
      this.packet = packet;
      retryStrategy = Optional.ofNullable(retry)
            .orElse(new DefaultRetryStrategy(maxRetryCount, AsyncRequestStep.this, AsyncRequestStep.this));
      this.cont = Optional.ofNullable(cont).orElse("");
      client = helper.take();
    }

    // Create a call to Kubernetes that we can cancel if it doesn't succeed in time.
    private CancellableCall createCall(AsyncFiber fiber) throws ApiException {
      return factory.generate(requestParams, client, cont, new ApiCallbackImpl(this, fiber));
    }

    // The Kubernetes request succeeded. Recycle the client, add the response to the packet, and proceed.
    void onSuccess(AsyncFiber fiber, T result, int statusCode, Map<String, List<String>> responseHeaders) {
      if (firstTimeResumed()) {
        if (LOGGER.isFinerEnabled()) {
          logSuccess(result, statusCode, responseHeaders);
        }

        helper.recycle(client);
        addResponseComponent(Component.createFor(
            createSuccess(requestParams, result, statusCode).withResponseHeaders(responseHeaders)));
        fiber.resume(packet);
      }
    }

    // We received a failure from Kubernetes. Recycle the client,
    // add the failure into the packet and prepare to try again.
    void onFailure(AsyncFiber fiber, ApiException ae, int statusCode, Map<String, List<String>> responseHeaders) {
      if (firstTimeResumed()) {
        if (statusCode != CallBuilder.NOT_FOUND && LOGGER.isFineEnabled()) {
          logFailure(ae, statusCode, responseHeaders);
        }

        helper.recycle(client);
        addResponseComponent(Component.createFor(
              RetryStrategy.class, retryStrategy,
              createFailure(requestParams, ae, statusCode).withResponseHeaders(responseHeaders)));
        fiber.resume(packet);
      }
    }

    // If this is the first event after the fiber resumes, it indicates that we did not receive
    // a callback within the timeout. So cancel the call and prepare to try again.
    private void handleTimeout(RequestParams requestParams, AsyncFiber fiber, CancellableCall cc) {
      if (firstTimeResumed()) {
        try {
          cc.cancel();
        } finally {
          try (LoggingContext stack = LoggingContext.setThreadContext().namespace(requestParams.namespace)) {
            if (LOGGER.isFinerEnabled()) {
              logTimeout();
            }
            addResponseComponent(Component.createFor(RetryStrategy.class, retryStrategy));
          }
          fiber.resume(packet);
        }
      }
    }

    // A throwable occurred while attempting to set up the call. So prepare to try again.
    private void resumeAfterThrowable(AsyncFiber fiber) {
      if (firstTimeResumed()) {
        addResponseComponent(Component.createFor(RetryStrategy.class, retryStrategy));
        fiber.resume(packet);
      }
    }

    private void addResponseComponent(Component component) {
      packet.getComponents().put(RESPONSE_COMPONENT_NAME, component);
    }

    private boolean firstTimeResumed() {
      return didResume.compareAndSet(false, true);
    }
  }

  @Override
  public NextAction apply(Packet packet) {
    // we don't have the domain presence information and logging context information yet,
    // add a logging context to pass the namespace information to the LoggingFormatter
    if (requestParams.namespace != null
        && packet.getComponents().get(DOMAIN_COMPONENT_NAME) == null
        && packet.getComponents().get(LoggingContext.LOGGING_CONTEXT_KEY) == null) {
      packet.getComponents().put(
          LoggingContext.LOGGING_CONTEXT_KEY,
          Component.createFor(new LoggingContext().namespace(requestParams.namespace)));
    }

    // clear out earlier results
    String cont = null;
    RetryStrategy retry = null;
    Component oldResponse = packet.getComponents().remove(RESPONSE_COMPONENT_NAME);
    if (oldResponse != null) {
      @SuppressWarnings("unchecked")
      CallResponse<T> old = oldResponse.getSpi(CallResponse.class);
      if (old != null && old.getResult() != null) {
        // called again, access continue value, if available
        cont = accessContinue(old.getResult());
      }

      retry = oldResponse.getSpi(RetryStrategy.class);
    }

    if (LOGGER.isFinerEnabled()) {
      logAsyncRequest();
    }

    AsyncRequestStepProcessing processing = new AsyncRequestStepProcessing(packet, retry, cont);

    return doSuspend(
        (fiber) -> {
          try {
            CancellableCall cc = processing.createCall(fiber);
            scheduleTimeoutCheck(fiber, timeoutSeconds, () -> processing.handleTimeout(requestParams, fiber, cc));
          } catch (ApiException t) {
            logAsyncFailure(t, t.getResponseBody());
            processing.resumeAfterThrowable(fiber);
          } catch (Throwable t) {
            logAsyncFailure(t, "");
            processing.resumeAfterThrowable(fiber);
          }
        });
  }

  // Schedule the timeout check to happen on the fiber at some number of seconds in the future.
  private void scheduleTimeoutCheck(AsyncFiber fiber, int timeoutSeconds, Runnable timeoutCheck) {
    fiber.scheduleOnce(timeoutSeconds, TimeUnit.SECONDS, timeoutCheck);
  }

  private void logAsyncRequest() {
    LOGGER.finer(
        MessageKeys.ASYNC_REQUEST,
        identityHash(),
        requestParams.call,
        requestParams.namespace,
        requestParams.name,
        requestParams.body != null ? LoggingFactory.getJson().serialize(requestParams.body) : "",
        fieldSelector,
        labelSelector,
        resourceVersion);
  }

  private void logAsyncFailure(Throwable t, String responseBody) {
    LOGGER.warning(
        MessageKeys.ASYNC_FAILURE,
        t.getMessage(),
        0,
        null,
        requestParams,
        requestParams.namespace,
        requestParams.name,
        requestParams.body != null
            ? LoggingFactory.getJson().serialize(requestParams.body)
            : "",
        fieldSelector,
        labelSelector,
        resourceVersion,
        responseBody);
  }

  private void logTimeout() {
    LOGGER.finer(
        MessageKeys.ASYNC_TIMEOUT,
        identityHash(),
        requestParams.call,
        requestParams.namespace,
        requestParams.name,
        requestParams.body != null
            ? LoggingFactory.getJson().serialize(requestParams.body)
            : "",
        fieldSelector,
        labelSelector,
        resourceVersion);
  }

  private void logSuccess(T result, int statusCode, Map<String, List<String>> responseHeaders) {
    LOGGER.finer(
        ASYNC_SUCCESS,
        identityHash(),
        requestParams.call,
        result,
        statusCode,
        responseHeaders);
  }

  private void logFailure(ApiException ae, int statusCode, Map<String, List<String>> responseHeaders) {
    LOGGER.fine(
        MessageKeys.ASYNC_FAILURE,
        identityHash(),
        ae.getMessage(),
        statusCode,
        responseHeaders,
        requestParams.call,
        requestParams.namespace,
        requestParams.name,
        requestParams.body != null
            ? LoggingFactory.getJson().serialize(requestParams.body)
            : "",
        fieldSelector,
        labelSelector,
        resourceVersion,
        ae.getResponseBody());
  }

  // creates a unique ID that allows matching requests to responses
  private String identityHash() {
    return Integer.toHexString(System.identityHashCode(this));
  }

  private final class DefaultRetryStrategy implements RetryStrategy {
    private long retryCount = 0;
    private final int maxRetryCount;
    private final Step retryStep;
    private final RetryStrategyListener listener;

    DefaultRetryStrategy(int maxRetryCount, Step retryStep, RetryStrategyListener listener) {
      this.maxRetryCount = maxRetryCount;
      this.retryStep = retryStep;
      this.listener = listener;
    }

    @Override
    public NextAction doPotentialRetry(Step conflictStep, Packet packet, int statusCode) {
      // Check statusCode, many statuses should not be retried
      // https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#http-status-codes
      if (statusCode == 0 /* simple timeout */
          || statusCode == 429 /* StatusTooManyRequests */
          || statusCode == 500 /* StatusInternalServerError */
          || statusCode == 503 /* StatusServiceUnavailable */
          || statusCode == 504 /* StatusServerTimeout */) {

        // exponential back-off
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);

        if (statusCode == 0 || statusCode == 504 /* StatusServerTimeout */) {
          listener.listenTimeoutDoubled();
        }

        NextAction na = new NextAction();
        if (statusCode == 0 && retryCount <= maxRetryCount) {
          na.invoke(Optional.ofNullable(conflictStep).orElse(retryStep), packet);
        } else {
          LOGGER.finer(MessageKeys.ASYNC_RETRY, identityHash(), String.valueOf(waitTime));
          na.delay(retryStep, packet, waitTime, TimeUnit.MILLISECONDS);
        }
        return na;
      } else if (statusCode == 409 /* Conflict */ && conflictStep != null) {
        // Conflict is an optimistic locking failure.  Therefore, we can't
        // simply retry the request.  Instead, application code needs to rebuild
        // the request based on latest contents.  If provided, a conflict step will do that.

        // exponential back-off
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);

        LOGGER.finer(MessageKeys.ASYNC_RETRY, identityHash(), String.valueOf(waitTime));
        NextAction na = new NextAction();
        na.delay(conflictStep, packet, waitTime, TimeUnit.MILLISECONDS);
        return na;
      }

      // otherwise, we will not retry
      return null;
    }

    @Override
    public void reset() {
      retryCount = 0;
    }
  }

  private class ApiCallbackImpl implements ApiCallback<T> {

    private final AsyncRequestStepProcessing processing;
    private final AsyncFiber fiber;

    public ApiCallbackImpl(AsyncRequestStepProcessing processing, AsyncFiber fiber) {
      this.processing = processing;
      this.fiber = fiber;
    }

    @Override
    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
      // no-op
    }

    @Override
    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
      // no-op
    }

    @Override
    public void onFailure(
        ApiException ae, int statusCode, Map<String, List<String>> responseHeaders) {
      // make sure that the domain namespace is added to the thread local so that
      // it can be passed to the LoggingFormatter
      try (LoggingContext stack = LoggingContext.setThreadContext().namespace(requestParams.namespace)) {
        processing.onFailure(fiber, ae, statusCode, responseHeaders);
      }
    }

    @Override
    public void onSuccess(
        T result, int statusCode, Map<String, List<String>> responseHeaders) {
      // make sure that the domain namespace is added to the thread local so that
      // it can be passed to the LoggingFormatter
      try (LoggingContext stack = LoggingContext.setThreadContext().namespace(requestParams.namespace)) {
        processing.onSuccess(fiber, result, statusCode, responseHeaders);
      }
    }
  }
}
