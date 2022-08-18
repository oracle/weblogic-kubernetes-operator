// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

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
        patch, patchFormat);
  }

  /**
   * Scale the cluster in the specified namespace.
   *
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param namespace namespace in which the domain exists
   * @param numOfServers number of servers to be scaled to
   * @return true if patch domain custom resource succeeds, false otherwise
   */
  public static boolean scaleCluster(String clusterName, String namespace, int numOfServers) {
    LoggingFacade logger = getLogger();

    // construct the patch string for scaling the cluster
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"replace\", ")
        .append("\"path\": \"/spec/replicas\", ")
        .append("\"value\": ")
        .append(numOfServers)
        .append("}]");

    logger.info("Scaling cluster {0} using patch string: {1}",
        clusterName, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return Kubernetes.patchClusterCustomResource(clusterName, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }
}
