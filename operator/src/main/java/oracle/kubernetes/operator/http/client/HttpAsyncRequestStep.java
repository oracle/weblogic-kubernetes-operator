// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.AsyncFiber;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_OK;
import static oracle.kubernetes.operator.http.client.TrustAllX509ExtendedTrustManager.getTrustingSSLContext;
import static oracle.kubernetes.operator.logging.ThreadLoggingContext.setThreadContext;

/**
 * An asynchronous step to handle http requests.
 */
public class HttpAsyncRequestStep extends Step {

  interface FutureFactory {
    CompletableFuture<HttpResponse<String>> createFuture(HttpRequest request);
  }

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static FutureFactory DEFAULT_FACTORY = HttpAsyncRequestStep::createFuture;

  private static final long DEFAULT_TIMEOUT_SECONDS = 5;

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static FutureFactory factory = DEFAULT_FACTORY;
  private final HttpRequest request;
  private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private static final HttpClient httpClient = HttpClient.newBuilder()
      .sslContext(getTrustingSSLContext())
      .build();

  private HttpAsyncRequestStep(HttpRequest request, HttpResponseStep responseStep) {
    super(responseStep);
    this.request = request;
  }

  /**
   * Creates a step to send a GET request to a server. If a response is received, processing
   * continues with the response step. If none is received within the timeout, the fiber is terminated.
   * @param url the URL of the targeted server
   * @param responseStep the step to handle the response
   * @return a new step to run as part of a fiber, linked to the response step
   */
  static HttpAsyncRequestStep createGetRequest(String url, HttpResponseStep responseStep) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    return create(request, responseStep);
  }

  /**
   * Creates a step to send a request to a server. If a response is received, processing
   * continues with the response step. If none is received within the timeout, the fiber is terminated.
   * @param request the http request to send
   * @param responseStep the step to handle the response
   * @return a new step to run as part of a fiber, linked to the response step
   */
  public static HttpAsyncRequestStep create(HttpRequest request, HttpResponseStep responseStep) {
    return new HttpAsyncRequestStep(request, responseStep);
  }

  /**
   * Overrides the default timeout for this request.
   * @param timeoutSeconds the new timeout, in seconds
   * @return this step
   */
  public HttpAsyncRequestStep withTimeoutSeconds(long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  @Override
  public NextAction apply(Packet packet) {
    AsyncProcessing processing = new AsyncProcessing(packet);
    return doSuspend(processing::process);
  }

  class AsyncProcessing {
    private final Packet packet;
    private CompletableFuture<HttpResponse<String>> future;

    AsyncProcessing(Packet packet) {
      this.packet = packet;
    }

    void process(AsyncFiber fiber) {
      HttpResponseStep.removeResponse(packet);
      future = factory.createFuture(request);
      future.whenComplete((response, throwable) -> resume(fiber, response, throwable));
      fiber.scheduleOnce(timeoutSeconds, TimeUnit.SECONDS, () -> checkTimeout(fiber));
    }

    private void checkTimeout(AsyncFiber fiber) {
      if (!future.isDone()) {
        resume(fiber, null, new HttpTimeoutException(request.method(), request.uri()));
      }
    }

    private String getDomainUIDFromInfo(DomainPresenceInfo info) {
      return Optional.ofNullable(info).map(DomainPresenceInfo::getDomainUid).orElse(null);
    }

    private String getNamespaceFromInfo(DomainPresenceInfo info) {
      return Optional.ofNullable(info).map(DomainPresenceInfo::getNamespace).orElse(null);
    }

    private void resume(AsyncFiber fiber, HttpResponse<String> response, Throwable throwable) {
      DomainPresenceInfo info = getDomainPresenceInfo();
      try (ThreadLoggingContext ignored =
               setThreadContext().namespace(getNamespaceFromInfo(info)).domainUid(getDomainUIDFromInfo(info))) {
        if (throwable instanceof HttpTimeoutException) {
          HttpResponseStep.addToPacket(packet, throwable);
          LOGGER.fine(MessageKeys.HTTP_REQUEST_TIMED_OUT, throwable.getMessage());
        } else if (response != null) {
          recordResponse(response);
        } else if (throwable != null) {
          recordThrowableResponse(throwable);
        }
      }

      fiber.resume(packet);
    }

    private String getServerName() {
      return packet.getValue(ProcessingConstants.SERVER_NAME);
    }

    private boolean isServerShuttingDown() {
      return Optional.ofNullable(getServerName()).map(this::isShuttingDown).orElse(false);
    }

    private boolean isShuttingDown(String serverName) {
      return getDomainPresenceInfo().isServerPodBeingDeleted(serverName)
          || podHasDeletionTimestamp(getDomainPresenceInfo().getServerPod(serverName));
    }

    private boolean failureCountExceedsThreshold() {
      return Optional.ofNullable(getServerName())
          .map(s -> getDomainPresenceInfo().getHttpRequestFailureCount(s) > getHttpRequestFailureThreshold())
          .orElse(false);
    }

    private DomainPresenceInfo getDomainPresenceInfo() {
      return packet.getSpi(DomainPresenceInfo.class);
    }

    private int getHttpRequestFailureThreshold() {
      return TuningParameters.getInstance().getHttpRequestFailureCountThreshold();
    }

    private boolean podHasDeletionTimestamp(V1Pod serverPod) {
      return Optional.ofNullable(serverPod).map(V1Pod::getMetadata)
          .map(m -> m.getDeletionTimestamp() != null)
          .orElse(false);
    }

    private void recordResponse(HttpResponse<String> response) {
      if (response.statusCode() != HTTP_OK) {
        LOGGER.fine(MessageKeys.HTTP_METHOD_FAILED, request.method(), request.uri(), response.statusCode());
      }
      HttpResponseStep.addToPacket(packet, response);
    }

    private void recordThrowableResponse(Throwable throwable) {
      if (!isServerShuttingDown() && failureCountExceedsThreshold()) {
        LOGGER.warning(MessageKeys.HTTP_REQUEST_GOT_THROWABLE, request.method(), request.uri(), throwable.getMessage());
      }
      HttpResponseStep.addToPacket(packet, throwable);
    }
  }

  private static CompletableFuture<HttpResponse<String>> createFuture(HttpRequest request) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
  }

  static class HttpTimeoutException extends RuntimeException {
    private final String method;
    private final URI uri;

    HttpTimeoutException(String method, URI uri) {
      this.method = method;
      this.uri = uri;
    }

    @Override
    public String getMessage() {
      return method + " request to " + uri + " timed out";
    }
  }
}
