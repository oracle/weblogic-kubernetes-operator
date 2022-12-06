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
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.actions.TestActions.createClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.Cluster.listClusterCustomResources;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.OKDUtils.getRouteHost;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusterUtils {

  /**
   * Create cluster custom resource object.
   * @param clusterResName cluster resource name
   * @param clusterName name of the cluster as in WebLogic config
   * @param namespace in which the cluster object exists
   * @param replicaCount replica count
   * @return cluster resource object
   */
  public static ClusterResource createClusterResource(
      String clusterResName, String clusterName, String namespace, int replicaCount) {
    return new ClusterResource()
        .withKind("Cluster")
        .withApiVersion(CLUSTER_API_VERSION)
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(clusterResName))
        .spec(new ClusterSpec().withClusterName(clusterName).replicas(replicaCount));
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
   * @param clusterName name of the cluster as in WebLogic config
   * @param namespace namespace
   * @param domain domain resource object
   * @param replicas scale to replicas
   * @return modified domain resource object
   */
  public static DomainResource createClusterResourceAndAddReferenceToDomain(
      String clusterResName, String clusterName, String namespace,
                 DomainResource domain, int replicas) {
    ClusterList clusters = listClusterCustomResources(namespace);
    if (clusters != null
        && clusters.getItems().stream().anyMatch(cluster -> cluster.getClusterResourceName().equals(clusterResName))) {
      getLogger().info("!!!Cluster Resource {0} in namespace {1} already exists, skipping...",
          clusterResName, namespace);
    } else {
      getLogger().info("Creating cluster resource {0} in namespace {1}", clusterResName, namespace);
      createClusterAndVerify(createClusterResource(clusterResName, clusterName, namespace, replicas));
    }
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    return domain;
  }

  /**
   * Remove the replicas setting from a cluster resource.
   * @param domainUid uid of the domain
   * @param clusterName name of the cluster resource
   * @param namespace namespace
   * @param replicaCount original replicaCount
   * @param msPodNamePrefix prefix of managed server pod names
   * */
  public static void removeReplicasSettingAndVerify(String domainUid, String clusterName, String namespace,
                                                    int replicaCount, String msPodNamePrefix) {
    getLogger().info("Remove replicas setting from cluster resource {0} in namespace {1}", clusterName, namespace);
    String patchStr = "[{\"op\": \"remove\",\"path\": \"/spec/replicas\"}]";
    assertTrue(patchClusterCustomResource(clusterName, namespace, new V1Patch(patchStr),
        V1Patch.PATCH_FORMAT_JSON_PATCH), "Patch cluster resource failed");

    // verify there is no pod created larger than max size of cluster
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(msPodNamePrefix + i, domainUid, namespace);
    }
  }

  /**
   * Scale the cluster of the domain in the specified namespace with REST API.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param numOfServers number of servers to be scaled to
   * @param externalRestHttpsPort node port allocated for the external operator REST HTTPS interface
   * @param opNamespace namespace of WebLogic operator
   * @param decodedToken decoded secret token from operator sa
   * @param expectedMsg expected message in the http response
   * @param hasAuthHeader true or false to include auth header
   * @param hasHeader    true or false to include header
   * @return true if REST call generate expected response message, false otherwise
   */
  public static boolean scaleClusterWithRestApi(String domainUid,
                                                String clusterName,
                                                int numOfServers,
                                                int externalRestHttpsPort,
                                                String opNamespace,
                                                String decodedToken,
                                                String expectedMsg,
                                                boolean hasHeader,
                                                boolean hasAuthHeader) {
    LoggingFacade logger = getLogger();

    String opExternalSvc = getRouteHost(opNamespace, "external-weblogic-operator-svc");


    // build the curl command to scale the cluster
    StringBuffer command = new StringBuffer()
        .append("curl --noproxy '*' -v -k ");
    if (hasAuthHeader) {
      command.append("-H \"Authorization:Bearer ")
          .append(decodedToken)
          .append("\" ");
    }
    command.append("-H Accept:application/json ")
        .append("-H Content-Type:application/json ");
    if (hasHeader) {
      command.append("-H X-Requested-By:MyClient ");
    }
    command.append("-d '{\"spec\": {\"replicas\": ")
        .append(numOfServers)
        .append("}}' ")
        .append("-X POST https://")
        .append(getHostAndPort(opExternalSvc, externalRestHttpsPort))
        .append("/operator/latest/domains/")
        .append(domainUid)
        .append("/clusters/")
        .append(clusterName)
        .append("/scale");

    CommandParams params = Command
        .defaultCommandParams()
        .command(command.toString())
        .saveResults(true)
        .redirect(true);

    logger.info("Calling curl to scale the cluster");
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    logger.info("Return values {0}, errors {1}", result.stdout(), result.stderr());
    if (result != null) {
      logger.info("Return values {0}, errors {1}", result.stdout(), result.stderr());
      if (result.stdout().contains(expectedMsg) || result.stderr().contains(expectedMsg)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Scale the cluster of the domain in the specified namespace with the KUBERNETES_CLI.
   *
   * @param clusterRes name of the cluster resource to be scaled.
   * @param namespace  namespace where the cluster is deployed.
   * @param replica   the replica count.
   */
  public static void kubernetesCLIScaleCluster(String clusterRes, String namespace, int replica) {
    getLogger().info("Scaling cluster resource {0} in namespace {1} using " + KUBERNETES_CLI + " scale command", 
        clusterRes, namespace);
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " scale  clusters/" + clusterRes 
        + " --replicas=" + replica + " -n " + namespace);
    boolean result = Command.withParams(params).execute();
    assertTrue(result, KUBERNETES_CLI + " scale command failed");
  }

  /**
   * Scale the cluster of the domain in the specified namespace with the KUBERNETES_CLI.
   *
   * @param cmd    custom command line including cluster resource to be executed.
   * @param namespace  namespace where the cluster is deployed.
   * @param expectSuccess  expected result of the KUBERNETES_CLI command.
   */
  public static void kubernetesCLIScaleCluster(String cmd, String namespace, boolean expectSuccess) {
    getLogger().info("Scaling cluster resource in namespace {1} using " + KUBERNETES_CLI + " scale command",
        namespace);
    String excommand = KUBERNETES_CLI + " scale cluster " + cmd + "-n " + namespace;
    CommandParams params = new CommandParams().defaults();
    params.command(excommand);
    boolean result = Command.withParams(params).execute();
    if (expectSuccess) {
      assertTrue(result, KUBERNETES_CLI + " scale command should not fail");
    } else {
      assertFalse(result, KUBERNETES_CLI + " scale command should fail");
    }
  }

  /**
   * Stop the  cluster managed server(s) in a cluster resource.
   * @param clusterResource name of the cluster resource
   * @param namespace namespace
   * */
  public static void stopCluster(String clusterResource, 
            String namespace) {
    getLogger().info("Stop the server(s) on cluster resource {0} in namespace {1}", clusterResource, namespace);

    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/serverStartPolicy\",")
        .append(" \"value\": \"")
        .append("Never")
        .append("\"")
        .append(" }]");

    getLogger().info("Cluster Patch string: {0}", patchStr);
    assertTrue(patchClusterCustomResource(clusterResource, namespace, new V1Patch(new String(patchStr)),
        V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to stop cluster resource");
  }

  /**
   * Start the cluster managed server(s) in a cluster resource.
   * @param clusterResource name of the cluster resource
   * @param namespace namespace
   * */
  public static void startCluster(String clusterResource, 
            String namespace) {
    getLogger().info("Stop the server(s) on cluster resource {0} in namespace {1}", clusterResource, namespace);

    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/serverStartPolicy\",")
        .append(" \"value\": \"")
        .append("IfNeeded")
        .append("\"")
        .append(" }]");

    getLogger().info("Cluster Patch string: {0}", patchStr);
    assertTrue(patchClusterCustomResource(clusterResource, namespace, new V1Patch(new String(patchStr)),
        V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to start cluster resource");
  }

}
