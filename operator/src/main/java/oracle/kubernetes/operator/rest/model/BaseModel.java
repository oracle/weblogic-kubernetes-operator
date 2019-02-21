// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/**
 * BaseModel is the base class of all of the WebLogic operator's model classes. It standardizes how
 * they handle toString.
 */
public class BaseModel {

  protected String propertiesToString() {
    return "";
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + propertiesToString() + ")";
  }
}
