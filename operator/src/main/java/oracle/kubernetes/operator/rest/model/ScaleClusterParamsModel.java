// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ScaleClusterParamsModel describes the input parameters to the WebLogic cluster scaling operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
