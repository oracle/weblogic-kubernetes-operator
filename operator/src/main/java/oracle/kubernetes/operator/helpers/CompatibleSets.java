// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;

import static oracle.kubernetes.operator.helpers.PodCompatibility.asSet;
import static oracle.kubernetes.operator.helpers.PodCompatibility.getMissingElements;

class CompatibleSets<T> implements CompatibilityCheck {
  private final Collection<T> expected;
  private final Collection<T> actual;
  private final String description;

  CompatibleSets(String description, Collection<T> expected, Collection<T> actual) {
    this.description = description;
    this.expected = expected;
    this.actual = actual;
  }

  @Override
  public boolean isCompatible() {
    if (expected == actual) {
      return true;
    }
    return asSet(actual).containsAll(asSet(expected));
  }

  @Override
  public String getIncompatibility() {
    return String.format(
        "actual %s does not have %s", description, getMissingElements(expected, actual));
  }
}
