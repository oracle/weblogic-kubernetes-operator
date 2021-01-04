// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public enum OverrideDistributionStrategy {
  DYNAMIC, ON_RESTART;

  public static final OverrideDistributionStrategy DEFAULT = DYNAMIC;
}