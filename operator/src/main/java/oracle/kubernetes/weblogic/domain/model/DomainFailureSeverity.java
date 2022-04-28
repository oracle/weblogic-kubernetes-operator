// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

public enum DomainFailureSeverity {
  FATAL("Fatal"), SEVERE("Severe"), WARNING("Warning");

  private final String value;

  DomainFailureSeverity(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }

  public static DomainFailureSeverity getDefault() {
    return SEVERE;
  }
}
