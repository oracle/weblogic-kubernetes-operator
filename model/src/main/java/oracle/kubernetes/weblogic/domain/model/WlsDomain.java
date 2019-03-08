// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import javax.annotation.Nonnull;

/**
 * Represents information from a WLS domain that is needed in order to validate an operator domain.
 */
public interface WlsDomain {

  /**
   * Returns the names of all of the clusters in the domain.
   *
   * @return a possibly empty array
   */
  @Nonnull
  String[] getClusterNames();

  /**
   * Returns the maximum number of replicas in the specified cluster. A domain configuration may not
   * request more replicas than this.
   *
   * @param clusterName the name of the cluster
   * @return a non-negative integer value
   */
  int getReplicaLimit(String clusterName);
}
