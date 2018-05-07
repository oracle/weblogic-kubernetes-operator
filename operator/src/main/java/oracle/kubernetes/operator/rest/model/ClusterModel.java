// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/** ClusterModel describes a WebLogic cluster. */
public class ClusterModel extends ItemModel {

  /** Construct an empty ClusterModel. */
  public ClusterModel() {}

  /**
   * Construct a populated ClusterModel.
   *
   * @param cluster - the cluster's name.
   */
  public ClusterModel(String cluster) {
    setCluster(cluster);
  }

  private String cluster;

  /**
   * Get the cluster's name.
   *
   * @return the cluster's name.
   */
  public String getCluster() {
    return cluster;
  }

  /**
   * Set the cluster's name.
   *
   * @param cluster - the cluster's name.
   */
  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  @Override
  protected String propertiesToString() {
    return "cluster=" + getCluster() + ", " + super.propertiesToString();
  }
}
