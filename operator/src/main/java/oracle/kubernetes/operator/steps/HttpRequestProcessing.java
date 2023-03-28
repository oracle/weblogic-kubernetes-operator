// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.helpers.AuthorizationSource;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.client.HttpAsyncRequestStep;
import oracle.kubernetes.operator.http.client.HttpResponseStep;
import oracle.kubernetes.operator.wlsconfig.PortDetails;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClock;
import org.jetbrains.annotations.NotNull;

abstract class HttpRequestProcessing {

  private static final Long HTTP_TIMEOUT_SECONDS = 60L;
  private static final Map<String, CookieList> COOKIES = new ConcurrentHashMap<>();

  private final Packet packet;
  private final V1Service service;
  private final V1Pod pod;

  HttpRequestProcessing(Packet packet, @Nonnull V1Service service, V1Pod pod) {
    this.service = service;
    this.pod = pod;
    this.packet = packet;
  }

  static HttpAsyncRequestStep createRequestStep(HttpRequest request, HttpResponseStep responseStep) {
    responseStep.setCallback(HttpRequestProcessing::addCookies);
    return HttpAsyncRequestStep.create(request, responseStep)
        .withTimeoutSeconds(HTTP_TIMEOUT_SECONDS);
  }

  // For unit testing only
  static void clearCookies() {
    COOKIES.clear();
  }

  private static void addCookies(HttpResponse<?> httpResponse) {
    final URI uri = httpResponse.request().uri();
    synchronized (COOKIES) {
      COOKIES.computeIfAbsent(toCookieKey(uri), u -> new CookieList())
            .addCookies(httpResponse.headers().allValues("Set-Cookie"));
    }
  }

  private static String toCookieKey(URI uri) {
    return uri.getHost() + ':' + uri.getPort();
  }

  @Nonnull
  V1Service getService() {
    return service;
  }

  @Nonnull
  private V1ObjectMeta getServiceMeta() {
    return Objects.requireNonNull(service.getMetadata());
  }

  Packet getPacket() {
    return packet;
  }

  AuthorizationSource getAuthorizationSource() {
    return SecretHelper.getAuthorizationSource(getPacket());
  }

  final HttpRequest.Builder createRequestBuilder(String url) {
    final URI uri = URI.create(url);
    HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(uri)
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .header("X-Requested-By", "WebLogic Operator");
    Optional.ofNullable(getAuthorizationSource())
            .ifPresent(source -> builder.header("Authorization", source.createBasicAuthorizationString()));
    getCookiesFor(uri).forEach(c -> builder.header("Cookie", c));
    return builder;
  }

  @NotNull
  private List<String> getCookiesFor(URI uri) {
    synchronized (COOKIES) {
      COOKIES.values().forEach(CookieList::clearIfExpired);
      return Optional.ofNullable(COOKIES.get(toCookieKey(uri)))
            .map(CookieList::getCookieHeaders)
            .orElse(Collections.emptyList());
    }
  }

  /**
   * Returns the base URL for the service defined in this class.
   */
  String getServiceUrl() {
    return getServiceUrl(getPortDetails());
  }

  private String getServiceUrl(PortDetails portDetails) {
    return portDetails.toHttpUrl(getHost());
  }

  /**
   * Subclasses override this to provide the details for the port to which the request is to be made.
   */
  abstract PortDetails getPortDetails();

  private String getHost() {
    return toServiceHost(getServiceMeta());
  }

  private String toServiceHost(@Nonnull V1ObjectMeta meta) {
    String ns = Optional.ofNullable(meta.getNamespace()).orElse("default");
    return meta.getName() + "." + ns + ".svc";
  }

  protected V1Pod getPod() {
    return pod;
  }

  static class CookieList {
    private OffsetDateTime expirationTime;
    private final Map<String, String> cookies = new HashMap<>();

    public CookieList() {
      updateExpirationTime();
    }

    private void updateExpirationTime() {
      expirationTime = SystemClock.now().plus(50, ChronoUnit.MINUTES);
    }

    void addCookies(List<String> setCookieHeaders) {
      updateExpirationTime();
      for (String setCookieHeader : setCookieHeaders) {
        final String[] parts = setCookieHeader.split(";")[0].split("=");
        cookies.put(parts[0], parts[1]);
      }
    }

    List<String> getCookieHeaders() {
      updateExpirationTime();
      return cookies.entrySet().stream().map(e -> e.getKey() + '=' + e.getValue()).collect(Collectors.toList());
    }

    private void clearIfExpired() {
      if (expirationTime.isBefore(SystemClock.now())) {
        cookies.clear();
      }

    }
  }

}
