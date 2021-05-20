// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class Equality implements CompatibilityCheck {
  private static final List<String> DOMAIN_FIELDS = Arrays.asList("image", "imagePullPolicy");

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
    return "'" + description + "'" + " changed from '" + actual + "' to '" + expected + "'";
  }

  @Override
  public String getScopedIncompatibility(CompatibilityScope scope) {
    if (scope.contains(getScope())) {
      return getIncompatibility();
    }
    return null;
  }

  @Override
  public CompatibilityScope getScope() {
    if (DOMAIN_FIELDS.contains(description)) {
      return CompatibilityScope.DOMAIN;
    }
    return CompatibilityScope.POD;
  }

}
