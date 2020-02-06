// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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
