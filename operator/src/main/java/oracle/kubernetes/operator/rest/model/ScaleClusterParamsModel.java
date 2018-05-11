// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/**
 * ScaleClusterParamsModel describes the input parameters to the WebLogic cluster scaling operation.
 */
public class ScaleClusterParamsModel extends BaseModel {

  private int managedServerCount;

  /**
   * Get the desired number of managed servers in the WebLogic cluster.
   *
   * @return the desired number of managed servers.
   */
  public int getManagedServerCount() {
    return managedServerCount;
  }

  /**
   * Set the desired number of managed servers in the WebLogic cluster.
   *
   * @param managedServerCount - the desired number of managed servers.
   */
  public void setManagedServerCount(int managedServerCount) {
    this.managedServerCount = managedServerCount;
  }

  @Override
  protected String propertiesToString() {
    return "managedServerCount=" + getManagedServerCount(); // super has no properties
  }
}
