// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/**
 * A class which implements different behavior based on the strategy defined for finding domain namespaces.
 * Uses the Visitor pattern (see https://en.wikipedia.org/wiki/Visitor_pattern). Implementations should
 * either define all of the strategy-specific methods or at least one of them as well as the default selection.
 */
public interface NamespaceStrategyVisitor<T> {

  default T getListStrategySelection() {
    return getDefaultSelection();
  }

  default T getDedicatedStrategySelection() {
    return getDefaultSelection();
  }

  default T getRegexpStrategySelection() {
    return getDefaultSelection();
  }

  default T getLabelSelectorStrategySelection() {
    return getDefaultSelection();
  }

  default T getDefaultSelection() {
    return null;
  }
}
