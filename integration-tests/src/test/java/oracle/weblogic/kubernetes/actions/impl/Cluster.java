// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Cluster {

  /**
   * Create a cluster custom resource.
   *
   * @param cluster Cluster custom resource model object
   * @param clusterVersion custom resource's version
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterCustomResource(ClusterResource cluster,
                                                   String clusterVersion) throws ApiException {
    return Kubernetes.createClusterCustomResource(cluster, clusterVersion);
  }

}
