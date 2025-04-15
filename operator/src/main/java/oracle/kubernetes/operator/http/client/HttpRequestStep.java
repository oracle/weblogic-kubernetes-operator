// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_OK;
import static oracle.kubernetes.operator.http.client.TrustAllX509ExtendedTrustManager.getTrustingSSLContext;
import static oracle.kubernetes.operator.logging.ThreadLoggingContext.setThreadContext;

/**
 * A step to handle http requests.
 */
public class HttpRequestStep extends Step {

  interface RequestSender {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
  }

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static final long DEFAULT_TIMEOUT_SECONDS = 5;

  private static final RequestSender DEFAULT_SENDER = HttpRequestStep::send;

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static RequestSender sender = DEFAULT_SENDER;

  private final HttpRequest request;
  private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private static final HttpClient httpClient = HttpClient.newBuilder()
      .sslContext(getTrustingSSLContext())
      .build();

  private HttpRequestStep(HttpRequest request, HttpResponseStep responseStep) {
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
  static HttpRequestStep createGetRequest(String url, HttpResponseStep responseStep) {
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
  public static HttpRequestStep create(HttpRequest request, HttpResponseStep responseStep) {
    return new HttpRequestStep(request, responseStep);
  }

  /**
   * Overrides the default timeout for this request.
   * @param timeoutSeconds the new timeout, in seconds
   * @return this step
   */
  public HttpRequestStep withTimeoutSeconds(long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  @Override
  @SuppressWarnings("try")
  public @Nonnull Result apply(Packet packet) {
    HttpResponseStep.removeResponse(packet);
    DomainPresenceInfo info = getDomainPresenceInfo(packet);
    try (ThreadLoggingContext ignored =
             setThreadContext().namespace(getNamespaceFromInfo(info)).domainUid(getDomainUIDFromInfo(info))) {
      HttpResponse<String> response = sender.send(request);
      recordResponse(response, packet);
    } catch (IOException | InterruptedException e) {
      recordThrowableResponse(e, packet);
    }
    return doNext(packet);
  }

  public static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String getDomainUIDFromInfo(DomainPresenceInfo info) {
    return Optional.ofNullable(info).map(DomainPresenceInfo::getDomainUid).orElse(null);
  }

  private String getNamespaceFromInfo(DomainPresenceInfo info) {
    return Optional.ofNullable(info).map(DomainPresenceInfo::getNamespace).orElse(null);
  }

  private String getServerName(Packet packet) {
    return packet.getValue(ProcessingConstants.SERVER_NAME);
  }

  private boolean isServerShuttingDown(Packet packet) {
    return Optional.ofNullable(getServerName(packet)).map(s -> this.isShuttingDown(s, packet)).orElse(false);
  }

  private boolean isShuttingDown(String serverName, Packet packet) {
    return getDomainPresenceInfo(packet).isServerPodBeingDeleted(serverName)
        || podHasDeletionTimestamp(getDomainPresenceInfo(packet).getServerPod(serverName));
  }

  private boolean failureCountExceedsThreshold(Packet packet) {
    return Optional.ofNullable(getServerName(packet))
        .map(s -> getDomainPresenceInfo(packet).getHttpRequestFailureCount(s) > getHttpRequestFailureThreshold())
        .orElse(false);
  }

  private DomainPresenceInfo getDomainPresenceInfo(Packet packet) {
    return (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
  }

  private int getHttpRequestFailureThreshold() {
    return TuningParameters.getInstance().getHttpRequestFailureCountThreshold();
  }

  private boolean podHasDeletionTimestamp(V1Pod serverPod) {
    return Optional.ofNullable(serverPod).map(V1Pod::getMetadata)
        .map(m -> m.getDeletionTimestamp() != null)
        .orElse(false);
  }

  private void recordResponse(HttpResponse<String> response, Packet packet) {
    if (response.statusCode() != HTTP_OK) {
      LOGGER.fine(MessageKeys.HTTP_METHOD_FAILED, request.method(), request.uri(), response.statusCode());
    }
    HttpResponseStep.addToPacket(packet, response);
  }

  private void recordThrowableResponse(Throwable throwable, Packet packet) {
    if (!isServerShuttingDown(packet) && failureCountExceedsThreshold(packet)) {
      LOGGER.warning(MessageKeys.HTTP_REQUEST_GOT_THROWABLE, request.method(), request.uri(), throwable.getMessage());
    }
    HttpResponseStep.addToPacket(packet, throwable);
  }
}
