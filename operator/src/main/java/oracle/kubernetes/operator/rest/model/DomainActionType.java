// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import oracle.kubernetes.common.Labeled;

public enum DomainActionType implements Labeled {
  UNKNOWN, INTROSPECT, RESTART;

  @Override
  public String label() {
    return name();
  }

  @Override
  public String toString() {
    return label();
  }
}
