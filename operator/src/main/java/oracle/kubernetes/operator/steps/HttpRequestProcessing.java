// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.helpers.AuthorizationHeaderFactory;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.HttpAsyncRequestStep;
import oracle.kubernetes.operator.http.HttpResponseStep;
import oracle.kubernetes.operator.wlsconfig.PortDetails;
import oracle.kubernetes.operator.work.Packet;

abstract class HttpRequestProcessing {

  public static final Integer HTTP_TIMEOUT_SECONDS = 60;
  protected final Packet packet;
  private final V1Service service;
  protected final V1Pod pod;

  HttpRequestProcessing(Packet packet, @Nonnull V1Service service, V1Pod pod) {
    this.service = service;
    this.pod = pod;
    this.packet = packet;
  }

  static HttpAsyncRequestStep createRequestStep(HttpRequest request, HttpResponseStep responseStep) {
    return HttpAsyncRequestStep.create(request, responseStep)
          .withTimeoutSeconds(HTTP_TIMEOUT_SECONDS);
  }

  @Nonnull
  V1Service getService() {
    return service;
  }

  @Nonnull
  private V1ObjectMeta getServiceMeta() {
    return Objects.requireNonNull(service.getMetadata());
  }

  public Packet getPacket() {
    return packet;
  }

  AuthorizationHeaderFactory getAuthorizationHeaderFactory() {
    return SecretHelper.getAuthorizationHeaderFactory(packet);
  }

  final HttpRequest.Builder createRequestBuilder(String url) {
    return HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Authorization", getAuthorizationHeaderFactory().createBasicAuthorizationString())
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .header("X-Requested-By", "WebLogic Operator");
  }

  /**
   * Returns the base URL for the service defined in this class.
   */
  String getServiceUrl() {
    return getServiceUrl(getPortDetails());
  }

  private String getServiceUrl(PortDetails portDetails) {
    return Optional.ofNullable(getPortalIP())
          .map(portDetails::toHttpUrl)
          .orElse(null);
  }

  /**
   * Subclasses override this to provide the details for the port to which the request is to be made.
   */
  abstract PortDetails getPortDetails();

  private String getPortalIP() {
    return hasClusterIP() ? getClusterIP() : getServerIP();
  }

  private boolean hasClusterIP() {
    return Optional.ofNullable(service.getSpec())
          .map(V1ServiceSpec::getClusterIP)
          .map(ip -> !ip.equalsIgnoreCase("None"))
          .orElse(false);
  }

  private String getClusterIP() {
    return Objects.requireNonNull(service.getSpec()).getClusterIP();
  }

  private String getServerIP() {
    return Optional.ofNullable(pod)
          .map(V1Pod::getStatus)
          .map(V1PodStatus::getPodIP)
          .orElse(toServiceHost(getServiceMeta()));
  }

  private String toServiceHost(@Nonnull V1ObjectMeta meta) {
    return meta.getName() + "." + meta.getNamespace();
  }
}
