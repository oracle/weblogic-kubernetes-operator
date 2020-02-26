// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

public interface PatchableComponent<T> {

  /**
   * Returns true if it is possible to patch the specified component to create this one.
   * @param other the component to compare
   * @return false if the component are not patch-compatible
   */
  boolean isPatchableFrom(T other);

}
