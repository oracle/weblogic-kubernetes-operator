// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonValue;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

public class DomainStatusPatch {

  private final String name;
  private final String namespace;
  private final JsonPatchBuilder patchBuilder;

  /**
   * Update the domain status synchronously. This may involve either replacing the current status or adding to it.
   * @param domain the domain to update
   * @param reason the reason, a camel-cased string with no spaces
   * @param message a text description of the new status; may include multiple lines
   */
  static void updateSynchronously(Domain domain, String reason, String message) {
    new DomainStatusPatch(domain, reason, message).update();
    updateCachedDomainStatus(domain, reason, message);
  }

  private DomainStatusPatch(Domain domain, String reason, String message) {
    name = domain.getMetadata().getName();
    namespace = domain.getMetadata().getNamespace();
    patchBuilder = getPatchBuilder(domain, reason, message);
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

  private static void updateCachedDomainStatus(Domain domain, String reason, String message) {
    if (domain.getStatus() == null) {
      domain.setStatus(new DomainStatus());
    }
    domain.getStatus().setReason(reason);
    domain.getStatus().setMessage(message);
  }

  private static void setSubField(JsonPatchBuilder patchBuilder, String path, String oldValue, String newValue) {
    if (oldValue == null) {
      patchBuilder.add(path, newValue);
    } else {
      patchBuilder.replace(path, newValue);
    }
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
