// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.helpers.SemanticVersion;

/**
 * Definition of an interface that returns values that the Main class requires.
 */
interface MainDelegate {

  /**
   * Returns the version of the operator.
   */
  SemanticVersion getProductVersion();
}
