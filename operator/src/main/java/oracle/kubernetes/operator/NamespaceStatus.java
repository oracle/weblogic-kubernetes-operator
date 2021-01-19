// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;

public class NamespaceStatus {
  private final AtomicBoolean isNamespaceStarting = new AtomicBoolean(false);
  private final AtomicReference<V1SubjectRulesReviewStatus> rulesReviewStatus = new AtomicReference<>();
  private final AtomicBoolean verifiedAsOperatorNamespace = new AtomicBoolean(false);
  private final AtomicBoolean verifiedAsDomainNamespace = new AtomicBoolean(false);

  public AtomicBoolean isNamespaceStarting() {
    return isNamespaceStarting;
  }

  public AtomicBoolean verifiedAsOperatorNamespace() {
    return verifiedAsOperatorNamespace;
  }

  public AtomicBoolean verifiedAsDomainNamespace() {
    return verifiedAsDomainNamespace;
  }

  public AtomicReference<V1SubjectRulesReviewStatus> getRulesReviewStatus() {
    return rulesReviewStatus;
  }

  boolean shouldStartNamespace() {
    return !isNamespaceStarting.getAndSet(true);
  }
}
