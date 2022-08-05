// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.actions.TestActions.createClusterCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusterUtils {

  /**
   * Create cluster custom resource object.
   * @param clusterResName cluster resource name
   * @param namespace in which the cluster object exists
   * @param replicaCount replica count
   * @return cluster resource object
   */
  public static ClusterResource createClusterResource(String clusterResName, String namespace, int replicaCount) {
    return new ClusterResource()
        .withKind("Cluster")
        .withApiVersion(CLUSTER_API_VERSION)
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(clusterResName))
        .spec(new ClusterSpec().withClusterName(clusterResName).replicas(replicaCount));
  }

  /**
   * Create a cluster in the specified namespace and wait up to five minutes until the cluster exists.
   * @param cluster clusters.weblogic.oracle object to create cluster custom resource
   */
  public static void createClusterAndVerify(ClusterResource cluster) {
    LoggingFacade logger = getLogger();
    String clusterName = cluster.getSpec().getClusterName();
    String namespace = cluster.getNamespace();
    // create the cluster CR
    assertNotNull(cluster, "cluster is null");
    assertNotNull(cluster.getSpec(), "cluster spec is null");
    assertNotNull(clusterName, "clusterName is null");

    logger.info("Creating cluster custom resource for clusterName {0} in namespace {1}",
        clusterName, namespace);
    assertTrue(assertDoesNotThrow(() -> createClusterCustomResource(cluster),
            String.format("Create cluster custom resource failed with ApiException for %s in namespace %s",
                clusterName, namespace)),
        String.format("Create cluster custom resource failed with ApiException for %s in namespace %s",
            clusterName, namespace));

    // wait for the cluster to exist
    logger.info("Checking for cluster custom resource in namespace {0}", namespace);
    testUntil(
        clusterExists(clusterName, CLUSTER_VERSION, namespace),
        logger,
        "cluster {0} to be created in namespace {1}",
        clusterName,
        namespace);
  }
}
