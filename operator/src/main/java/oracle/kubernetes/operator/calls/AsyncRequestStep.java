// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ListMeta;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * A Step driven by an asynchronous call to the Kubernetes API, which results in a series of
 * callbacks until canceled.
 */
public class AsyncRequestStep<T> extends Step {
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
  private int timeoutSeconds;
  private final int maxRetryCount;
  private final String fieldSelector;
  private final String labelSelector;
  private final String resourceVersion;

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
    next.setPrevious(this);
  }

  @Override
  protected String getDetail() {
    return requestParams.call;
  }

  @Override
  public NextAction apply(Packet packet) {
    // clear out earlier results
    String cont = null;
    RetryStrategy retry = null;
    Component oldResponse = packet.getComponents().remove(RESPONSE_COMPONENT_NAME);
    if (oldResponse != null) {
      @SuppressWarnings("unchecked")
      CallResponse<T> old = oldResponse.getSPI(CallResponse.class);
      if (old != null && old.getResult() != null) {
        // called again, access continue value, if available
        cont = accessContinue(old.getResult());
      }

      retry = oldResponse.getSPI(RetryStrategy.class);
    }
    String c = (cont != null) ? cont : "";
    if (retry == null) {
      retry = new DefaultRetryStrategy();
      retry.setRetryStep(this);
    }
    RetryStrategy r = retry;

    LOGGER.fine(
        MessageKeys.ASYNC_REQUEST,
        requestParams.call,
        requestParams.namespace,
        requestParams.name,
        requestParams.body,
        fieldSelector,
        labelSelector,
        resourceVersion);

    AtomicBoolean didResume = new AtomicBoolean(false);
    ApiClient client = helper.take();
    return doSuspend(
        (fiber) -> {
          ApiCallback<T> callback =
              new BaseApiCallback<T>() {
                @Override
                public void onFailure(
                    ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                  if (didResume.compareAndSet(false, true)) {
                    if (statusCode != CallBuilder.NOT_FOUND) {
                      LOGGER.info(
                          MessageKeys.ASYNC_FAILURE,
                          e,
                          statusCode,
                          responseHeaders,
                          requestParams.call,
                          requestParams.namespace,
                          requestParams.name,
                          requestParams.body,
                          fieldSelector,
                          labelSelector,
                          resourceVersion);
                    }

                    helper.recycle(client);
                    packet
                        .getComponents()
                        .put(
                            RESPONSE_COMPONENT_NAME,
                            Component.createFor(
                                RetryStrategy.class,
                                r,
                                new CallResponse<Void>(null, e, statusCode, responseHeaders)));
                    fiber.resume(packet);
                  }
                }

                @Override
                public void onSuccess(
                    T result, int statusCode, Map<String, List<String>> responseHeaders) {
                  if (didResume.compareAndSet(false, true)) {
                    LOGGER.fine(MessageKeys.ASYNC_SUCCESS, result, statusCode, responseHeaders);

                    helper.recycle(client);
                    packet
                        .getComponents()
                        .put(
                            RESPONSE_COMPONENT_NAME,
                            Component.createFor(
                                new CallResponse<>(result, null, statusCode, responseHeaders)));
                    fiber.resume(packet);
                  }
                }
              };

          try {
            CancellableCall cc = factory.generate(requestParams, client, c, callback);

            // timeout handling
            fiber
                .owner
                .getExecutor()
                .schedule(
                    () -> {
                      if (didResume.compareAndSet(false, true)) {
                        try {
                          cc.cancel();
                        } finally {
                          LOGGER.info(
                              MessageKeys.ASYNC_TIMEOUT,
                              requestParams.call,
                              requestParams.namespace,
                              requestParams.name,
                              requestParams.body,
                              fieldSelector,
                              labelSelector,
                              resourceVersion);
                          packet
                              .getComponents()
                              .put(
                                  RESPONSE_COMPONENT_NAME,
                                  Component.createFor(RetryStrategy.class, r));
                          fiber.resume(packet);
                        }
                      }
                    },
                    timeoutSeconds,
                    TimeUnit.SECONDS);
          } catch (Throwable t) {
            LOGGER.warning(
                MessageKeys.ASYNC_FAILURE,
                t,
                0,
                null,
                requestParams,
                requestParams.namespace,
                requestParams.name,
                requestParams.body,
                fieldSelector,
                labelSelector,
                resourceVersion);
            if (didResume.compareAndSet(false, true)) {
              packet
                  .getComponents()
                  .put(RESPONSE_COMPONENT_NAME, Component.createFor(RetryStrategy.class, r));
              fiber.resume(packet);
            }
          }
        });
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

  private final class DefaultRetryStrategy implements RetryStrategy {
    private long retryCount = 0;
    private Step retryStep = null;

    @Override
    public void setRetryStep(Step retryStep) {
      this.retryStep = retryStep;
    }

    @Override
    public NextAction doPotentialRetry(
        Step conflictStep,
        Packet packet,
        ApiException e,
        int statusCode,
        Map<String, List<String>> responseHeaders) {
      // Check statusCode, many statuses should not be retried
      // https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#http-status-codes
      if (statusCode == 0 /* simple timeout */
          || statusCode == 429 /* StatusTooManyRequests */
          || statusCode == 500 /* StatusInternalServerError */
          || statusCode == 503 /* StatusServiceUnavailable */
          || statusCode == 504 /* StatusServerTimeout */) {

        // exponential back-off
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);

        if (statusCode == 0 || statusCode == 504 /* StatusServerTimeout */) {
          // increase server timeout
          timeoutSeconds *= 2;
        }

        NextAction na = new NextAction();
        if (statusCode == 0 && retryCount <= maxRetryCount) {
          na.invoke(retryStep, packet);
        } else {
          LOGGER.info(MessageKeys.ASYNC_RETRY, String.valueOf(waitTime));
          na.delay(retryStep, packet, waitTime, TimeUnit.MILLISECONDS);
        }
        return na;
      } else if (statusCode == 409 /* Conflict */ && conflictStep != null) {
        // Conflict is an optimistic locking failure.  Therefore, we can't
        // simply retry the request.  Instead, application code needs to rebuild
        // the request based on latest contents.  If provided, a conflict step will do that.

        // exponential back-off
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);

        LOGGER.info(MessageKeys.ASYNC_RETRY, String.valueOf(waitTime));
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

  private abstract static class BaseApiCallback<T> implements ApiCallback<T> {
    @Override
    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
      // no-op
    }

    @Override
    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
      // no-op
    }
  }
}
