// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.common.Labeled;

public enum ServerStartState implements Labeled {
  RUNNING,
  ADMIN;

  @Override
  public String label() {
    return name();
  }

  @Override
  public String toString() {
    return label();
  }
}
