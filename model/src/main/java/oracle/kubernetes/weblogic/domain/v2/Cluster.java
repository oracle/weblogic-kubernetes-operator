// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 2.0
 */
public class Cluster extends BaseConfiguration {
  /** The name of the cluster. Required. */
  @SerializedName("clusterName")
  @Expose
  private String clusterName;

  /** The number of replicas to run in the cluster, if specified. */
  @SerializedName("replicas")
  @Expose
  private Integer replicas;

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(@Nonnull String clusterName) {
    this.clusterName = clusterName;
  }

  public Cluster withClusterName(@Nonnull String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }
}
