// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;

public class NamespaceStatus {
  private final AtomicBoolean isNamespaceStarting = new AtomicBoolean(false);
  private final AtomicReference<V1SubjectRulesReviewStatus> rulesReviewStatus = new AtomicReference<>();

  public AtomicBoolean isNamespaceStarting() {
    return isNamespaceStarting;
  }

  public AtomicReference<V1SubjectRulesReviewStatus> getRulesReviewStatus() {
    return rulesReviewStatus;
  }
}
