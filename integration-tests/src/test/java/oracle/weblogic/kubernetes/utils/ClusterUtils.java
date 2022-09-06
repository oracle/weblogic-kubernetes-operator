// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;


import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.ClusterList;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.actions.TestActions.createClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.Cluster.listClusterCustomResources;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterDoesNotExist;
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
   * Create cluster custom resource object.
   *
   * @param clusterResName cluster resource name
   * @param namespace in which the cluster object exists
   * @param clusterSpec cluster specification
   * @return cluster resource object
   */
  public static ClusterResource createClusterResource(String clusterResName,
      String namespace, ClusterSpec clusterSpec) {
    return new ClusterResource()
        .withKind("Cluster")
        .withApiVersion(CLUSTER_API_VERSION)
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(clusterResName))
        .spec(clusterSpec);
  }

  /**
   * Create a cluster in the specified namespace and wait up to five minutes until the cluster exists.
   * @param cluster clusters.weblogic.oracle object to create cluster custom resource
   */
  public static void createClusterAndVerify(ClusterResource cluster) {
    LoggingFacade logger = getLogger();

    assertNotNull(cluster, "cluster is null");
    String clusterResourceName = cluster.getClusterResourceName();
    String namespace = cluster.getNamespace();

    // create the cluster CR
    assertNotNull(cluster.getSpec(), "cluster spec is null");
    assertNotNull(clusterResourceName, "clusterResourceName is null");

    logger.info("Creating cluster custom resource for clusterResourceName {0} in namespace {1}",
        clusterResourceName, namespace);
    assertTrue(assertDoesNotThrow(() -> createClusterCustomResource(cluster),
            String.format("Create cluster custom resource failed with ApiException for %s in namespace %s",
                clusterResourceName, namespace)),
        String.format("Create cluster custom resource failed for %s in namespace %s",
            clusterResourceName, namespace));

    // wait for the cluster to exist
    logger.info("Checking for cluster custom resource in namespace {0}", namespace);
    testUntil(
        clusterExists(clusterResourceName, CLUSTER_VERSION, namespace),
        logger,
        "cluster {0} to be created in namespace {1}",
        clusterResourceName,
        namespace);
  }  
  
  /**
   * Delete a cluster resource in the specified namespace.
   *
   * @param namespace the namespace in which the domain exists
   * @param clusterName cluster resource name
   */
  public static void deleteClusterCustomResourceAndVerify(String clusterName, String namespace) {
    //delete cluster resource in namespace and wait until it is deleted
    getLogger().info("deleting cluster custom resource {0} in namespace {1}", clusterName, namespace);
    Cluster.deleteClusterCustomResource(clusterName, namespace);

    testUntil(
        clusterDoesNotExist(clusterName, CLUSTER_VERSION, namespace),
        getLogger(),
        "cluster {0} to be created in namespace {1}",
        clusterName,
        namespace);
  }
  
  /**
   * Scale cluster by patching cluster resource replicas.
   *
   * @param clusterName name of the cluster resource
   * @param namespace namespace
   * @param replicas scale to replicas
   * @return true if patching succeeds otherwise false
   */
  public static boolean scaleCluster(String clusterName, String namespace, int replicas) {
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + replicas + "}"
        + "]";
    getLogger().info("Updating replicas in cluster {0} using patch string: {1}", clusterName, patchStr);
    V1Patch patch = new V1Patch(patchStr);
    return patchClusterCustomResource(clusterName, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Add cluster to domain resource.
   *
   * @param clusterResName name of the cluster resource
   * @param namespace namespace
   * @param domain domain resource object
   * @param replicas scale to replicas
   * @return modified domain resource object
   */
  public static DomainResource addClusterToDomain(String clusterResName, String namespace,
                                                  DomainResource domain, int replicas) {
    ClusterList clusters = listClusterCustomResources(namespace);
    if (clusters != null
        && clusters.getItems().stream().anyMatch(cluster -> cluster.getClusterResourceName().equals(clusterResName))) {
      getLogger().info("!!!Cluster {0} in namespace {1} already exists, skipping...", clusterResName, namespace);
    } else {
      getLogger().info("Creating cluster {0} in namespace {1}", clusterResName, namespace);
      createClusterAndVerify(createClusterResource(clusterResName, namespace, replicas));
    }
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    return domain;
  }
}
