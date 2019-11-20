// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import javax.json.Json;
import javax.json.JsonPatchBuilder;
import javax.json.JsonValue;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.custom.V1Patch;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSource;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class DomainStatusPatch extends Step {
  static final String BAD_DOMAIN = "ErrBadDomain";
  static final String ERR_INTROSPECTOR = "ErrIntrospector";
  private final String name;
  private final String namespace;
  private JsonPatchBuilder patchBuilder;

  /**
   * Update the domain status. This may involve either replacing the current status or adding to it.
   * @param domain the domain to update
   * @param reason the reason, a camel-cased string with no spaces
   * @param message a text description of the new status; may include multiple lines
   */
  static Step createStep(Domain domain, String reason, String message) {
    return new DomainStatusPatch(domain, reason, message);
  }

  /**
   * Update the domain status in response to an unprocessable entity error. This may involve either replacing
   * the current status or adding to it.
   * @param domain the domain to update
   * @param apiException the exception reporting an unprocessable entity
   */
  static Step createStep(Domain domain, ApiException apiException) {
    FailureStatusSource failure = UnrecoverableErrorBuilder.fromException(apiException);
    return createStep(domain, failure.getReason(), failure.getMessage());
  }

  /**
   * Update the domain status synchronously. This may involve either replacing the current status or adding to it.
   * @param domain the domain to update
   * @param reason the reason, a camel-cased string with no spaces
   * @param message a text description of the new status; may include multiple lines
   */
  public static void updateSynchronously(Domain domain, String reason, String message) {
    new DomainStatusPatch(domain, reason, message).update();
  }

  private DomainStatusPatch(Domain domain, String reason, String message) {
    name = domain.getMetadata().getName();
    namespace = domain.getMetadata().getNamespace();
    patchBuilder = getPatchBuilder(domain, reason, message);
  }

  @Override
  public NextAction apply(Packet packet) {
    Step step = new CallBuilder().patchDomainAsync(name, namespace, getPatchBody(), createResponseStep());
    return doNext(step, packet);
  }

  private DefaultResponseStep<Domain> createResponseStep() {
    return new DefaultResponseStep<>(getNext());
  }

  private static JsonPatchBuilder getPatchBuilder(Domain domain, String reason, String message) {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    if (domain.getStatus() == null) {
      patchBuilder.add("/status", JsonValue.EMPTY_JSON_OBJECT);
      patchBuilder.add("/status/reason", reason);
      patchBuilder.add("/status/message", message);
    } else {
      setSubField(patchBuilder, "/status/reason", domain.getStatus().getReason(), reason);
      setSubField(patchBuilder, "/status/message", domain.getStatus().getMessage(), message);
    }
    return patchBuilder;
  }

  private static void setSubField(JsonPatchBuilder patchBuilder, String path, String oldValue, String newValue) {
    if (oldValue == null)
      patchBuilder.add(path, newValue);
    else
      patchBuilder.replace(path, newValue);
  }

  private void update() {
    try {
      new CallBuilder().patchDomain(name, namespace, getPatchBody());
    } catch (ApiException ignored) {
      /* extraneous comment to fool checkstyle into thinking that this is not an empty catch block. */
    }
  }

  private V1Patch getPatchBody() {
    return new V1Patch(patchBuilder.build().toString());
  }
}
