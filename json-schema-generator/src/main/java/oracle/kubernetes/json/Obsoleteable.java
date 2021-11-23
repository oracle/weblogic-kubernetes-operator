// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

public interface Obsoleteable {
  /**
   * Determines if this field or enum constant is obsolete.
   * @return true, if the field or enum constant is obsolete
   */
  default boolean isObsolete() {
    return false;
  }
}
