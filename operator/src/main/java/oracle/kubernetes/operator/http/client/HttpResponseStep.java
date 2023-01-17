// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.client;

import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.function.Consumer;

import oracle.kubernetes.operator.helpers.AuthorizationSource;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_OK;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAUTHORIZED;

public abstract class HttpResponseStep extends Step {
  private static final String RESPONSE = "httpResponse";

  private Consumer<HttpResponse<?>> callback;

  protected HttpResponseStep(Step next) {
    super(next);
  }

  public void setCallback(Consumer<HttpResponse<?>> callback) {
    this.callback = callback;
  }

  @Override
  public NextAction apply(Packet packet) {
    return Optional.ofNullable(getResponse(packet))
        .map(r -> doApply(packet, r))
        .orElse(handlePossibleThrowableOrContinue(packet));
  }

  private NextAction handlePossibleThrowableOrContinue(Packet packet) {
    return Optional.ofNullable(getThrowableResponse(packet))
        .map(t -> wrapOnFailure(packet, null))
        .orElse(doNext(packet));
  }

  protected Throwable getThrowableResponse(Packet packet) {
    return packet.getSpi(Throwable.class);
  }

  private NextAction doApply(Packet packet, HttpResponse<String> response) {
    Optional.ofNullable(callback).ifPresent(c -> c.accept(response));
    return isSuccess(response) ? onSuccess(packet, response) : wrapOnFailure(packet, response);
  }

  private NextAction wrapOnFailure(Packet packet, HttpResponse<String> response) {
    if (response != null && (response.statusCode() == HTTP_FORBIDDEN || response.statusCode() == HTTP_UNAUTHORIZED)) {
      Optional.ofNullable(SecretHelper.getAuthorizationSource(packet)).ifPresent(AuthorizationSource::onFailure);
    }
    return onFailure(packet, response);
  }

  private boolean isSuccess(HttpResponse<String> response) {
    return response.statusCode() == HTTP_OK;
  }

  @SuppressWarnings("unchecked")
  protected HttpResponse<String> getResponse(Packet packet) {
    return packet.getSpi(HttpResponse.class);
  }

  /**
   * Adds the specified response to a packet so that this step can access it via {@link Packet#getSpi(Class)} call.
   * @param packet the packet to which the response should be added
   * @param response the response from the server
   */
  static void addToPacket(Packet packet, HttpResponse<String> response) {
    packet.getComponents().put(RESPONSE, Component.createFor(HttpResponse.class, response));
  }

  /**
   * Adds the specified throwable to a packet so that this step can access it via {@link Packet#getSpi(Class)} call.
   * @param packet the packet to which the response should be added
   * @param throwable the throwable from the server
   */
  static void addToPacket(Packet packet, Throwable throwable) {
    packet.getComponents().put(RESPONSE, Component.createFor(Throwable.class, throwable));
  }

  /**
   * Removes any current response from the packet.
   * @param packet the packet from which the response should be removed
   */
  static void removeResponse(Packet packet) {
    packet.getComponents().remove(RESPONSE);
  }


  /**
   * Processes a successful response.
   * @param packet the packet from the fiber
   * @param response the response from the server
   * @return the next action for the fiber to take
   */
  public abstract NextAction onSuccess(Packet packet, HttpResponse<String> response);

  /**
   * Processes a failure response.
   * @param packet the packet from the fiber
   * @param response the response from the server
   * @return the next action for the fiber to take
   */
  public abstract NextAction onFailure(Packet packet, HttpResponse<String> response);
}
