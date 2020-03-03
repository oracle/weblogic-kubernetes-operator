// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class CollectiveCompatibility implements CompatibilityCheck {
  protected final List<CompatibilityCheck> checks = new ArrayList<>();

  void add(CompatibilityCheck check) {
    checks.add(check);
  }

  protected <T> void add(String description, T expected, T actual) {
    add(new Equality(description, expected, actual));
  }

  void addAll(Collection<CompatibilityCheck> checks) {
    this.checks.addAll(checks);
  }

  @Override
  public boolean isCompatible() {
    for (CompatibilityCheck check : checks) {
      if (!check.isCompatible()) {
        return false;
      }
    }

    return true;
  }

  @Override
  public String getIncompatibility() {
    final List<String> reasons = new ArrayList<>();
    for (CompatibilityCheck check : checks) {
      if (!check.isCompatible()) {
        reasons.add(getIndent() + check.getIncompatibility());
      }
    }
    return reasons.isEmpty() ? null : getHeader() + String.join("\n", reasons);
  }

  <T> void addSets(String description, List<T> expected, List<T> actual) {
    add(CheckFactory.create(description, expected, actual));
  }

  @SuppressWarnings("SameParameterValue")
  <T> void addSetsIgnoring(String description, List<T> expected, List<T> actual, String... keys) {
    add(CheckFactory.create(description, expected, actual).ignoring(keys));
  }

  String getHeader() {
    return "";
  }

  String getIndent() {
    return "";
  }
}
