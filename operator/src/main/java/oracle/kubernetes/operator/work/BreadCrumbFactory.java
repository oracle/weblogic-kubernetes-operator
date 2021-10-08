// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

/**
 * An interface for an object which can generate a bread crumb to debug a fiber execution.
 */
interface BreadCrumbFactory {

  // Creates a fiber bread crumb for this object
  BreadCrumb createBreadCrumb();
}
