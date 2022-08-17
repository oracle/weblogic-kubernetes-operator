// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.ClusterList;
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
  
  /**
   * Delete cluster custom resource.
   *
   * @param clusterName name of the cluster custom resource
   * @param namespace namespace in which cluster custom resource exists
   * @return true if successful otherwise false
   */
  public static boolean deleteClusterCustomResource(String clusterName, String namespace) {
    return Kubernetes.deleteClusterCustomResource(clusterName, namespace);
  }
  
  /**
   * Patch the Cluster Custom Resource.
   *
   * @param clusterName unique cluster identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document: "application/json-patch+json",
     "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchClusterCutomResource(String clusterName, String namespace,
      V1Patch patch, String patchFormat) {
    return Kubernetes.patchClusterCustomResource(clusterName, namespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }
    

  /**
   * List all Custom Resource Clusters in a namespace.
   *
   * @param namespace name of namespace
   * @return list of Custom Resource Clusters for a given namespace
   */
  public static ClusterList listClusterCustomResources(String namespace) {
    return Kubernetes.listClusters(namespace);
  }
}
