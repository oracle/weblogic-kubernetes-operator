// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import javax.json.Json;
import javax.json.JsonPatchBuilder;

import io.kubernetes.client.ApiException;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.unprocessable.UnprocessableEntityBuilder;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class DomainStatusPatch {
  private Domain domain;

  /**
   * Returns true if the specified call response indicates an unprocessable entity response from Kubernetes.
   * @param callResponse the response from a Kubernetes call
   * @return true if an unprocessable entity failure has been reported
   */
  static <T> boolean isUnprocessableEntityFailure(CallResponse<T> callResponse) {
    return callResponse.isFailure() && UnprocessableEntityBuilder.isUnprocessableEntity(callResponse.getE());
  }

  /**
   * Update the domain status. This may involve either replacing the current status or adding to it.
   * @param domain the domain to update
   * @param reason the reason, a camel-cased string with no spaces
   * @param message a text description of the new status; may include multiple lines
   */
  public static void updateDomainStatus(Domain domain, String reason, String message) {
    new DomainStatusPatch(domain).update(reason, message);
  }

  /**
   * Update the domain status in response to an unprocessable entity error. This may involve either replacing
   * the current status or adding to it.
   * @param domain the domain to update
   * @param apiException the exception reporting an unprocessable entity
   */
  static void updateDomainStatus(Domain domain, ApiException apiException) {
    UnprocessableEntityBuilder builder = UnprocessableEntityBuilder.fromException(apiException);
    new DomainStatusPatch(domain).update(builder.getReason(), builder.getMessage());
  }

  private DomainStatusPatch(Domain domain) {
    this.domain = domain;
  }

  private void update(String reason, String message) {
    if (reason == null || message == null) return;

    try {
      JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
      if (domain.getStatus() != null && domain.getStatus().getReason() != null) {
        patchBuilder.replace("/status/reason", reason);
        patchBuilder.replace("/status/message", message);
      } else {
        patchBuilder.add("/status/reason", reason);
        patchBuilder.add("/status/message", message);
      }
      new CallBuilder()
            .patchDomain(
                  domain.getMetadata().getName(), domain.getMetadata().getNamespace(), patchBuilder.build());
    } catch (ApiException ignored) {
      /* extraneous comment to fool checkstyle into thinking that this is not an empty catch block. */
    }
  }
}
