// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public enum OverrideDistributionStrategy implements Labeled {
  DYNAMIC, ON_RESTART;

  public static final OverrideDistributionStrategy DEFAULT = DYNAMIC;

  @Override
  public String label() {
    return name();
  }

  @Override
  public String toString() {
    return label();
  }
}