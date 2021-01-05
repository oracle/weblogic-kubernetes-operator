// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Optional;

import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public abstract class HttpResponseStep extends Step {
  private static final String RESPONSE = "httpResponse";

  public HttpResponseStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    return Optional.ofNullable(getResponse(packet)).map(r -> doApply(packet, r)).orElse(doNext(packet));
  }

  private NextAction doApply(Packet packet, HttpResponse<String> response) {
    return isSuccess(response) ? onSuccess(packet, response) : onFailure(packet, response);
  }

  private boolean isSuccess(HttpResponse<String> response) {
    return response.statusCode() == HttpURLConnection.HTTP_OK;
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> getResponse(Packet packet) {
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
