// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Objects;

class Equality implements CompatibilityCheck {
  private final String description;
  private final Object expected;
  private final Object actual;

  Equality(String description, Object expected, Object actual) {
    this.description = description;
    this.expected = expected;
    this.actual = actual;
  }

  @Override
  public boolean isCompatible() {
    return Objects.equals(expected, actual);
  }

  @Override
  public String getIncompatibility() {
    return description + " expected: " + expected + " but was: " + actual;
  }
}
