// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ProbeTuning;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterStatusConditionsMatchesDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterStatusMatchesDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.kubernetesCLIScaleCluster;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.startCluster;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.stopCluster;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createCustomConditionFactory;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainFailedEventWithReason;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 
 * Test various Cluster resource management usecases in a MiiDomain. 
 * Add a cluster reference (C1) after domain is started
 * Replace a cluster reference (C1) with a new cluster reference (C2)
 * Delete a cluster reference (C2)
 * Add same cluster reference to 2 domains to generate "Validation Error Event"
 * Add a (non-existing) cluster reference to a domain resource
 * Start a domain with both domain and cluster defined in single yaml file
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create model in image domain with Cluster Resourcees")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
class ItMiiClusterResource {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private String miiImage = null;
  private static LoggingFacade logger = null;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  final int replicaCount = 2;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
    
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");
  }

  /**
   * Create WebLogic domain DR with domain level replica set to zero.
   * Do not associate any Cluster Resource with DR even if two 
   * WebLogic clusters (cluster-1 and cluster-2) are configuted in config.xml
   * where cluster-1 is a dynamic cluster and cluster-2 is a configured cluster
   * Create two kubernates cluster resources CR1 and CR2 
   * corresponding to WebLogic clusters cluster-1 and cluster-2 respectively.
   * Start the domain and make sure no managed servers are started from either 
   * WebLogic Cluster. 
   * Patch the domain resource to add cluster resource CR1
   * Make sure only managed servers from cluster-1 comes up
   * Patch the domain resource to replace the resource  CR1 with CR2
   * Make sure managed servers from CR1 goes down and managed servers 
   * from CR2 comes up.
   * Scale up the cluster CR2 
   */
  @Test
  @DisplayName("Verify dynamic add/replace cluster resource on domain")
  void testAddReplaceClusterResource() {

    String domainUid = "domain1";
    String adminServerPodName = domainUid + "-admin-server";
    String managedServer1Prefix = domainUid +  "-c1-managed-server";
    String managedServer2Prefix = domainUid + "-c2-managed-server";

    String cluster1Res   = domainUid + "-cluster-1";
    String cluster2Res   = domainUid + "-cluster-2";
    String cluster1Name  = "cluster-1";
    String cluster2Name  = "cluster-2";
  
    String configMapName = domainUid + "-configmap";

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);
 
    // create and deploy cluster resource(s)
    ClusterResource cluster = createClusterResource(
        cluster1Res, cluster1Name, domainNamespace, replicaCount);
    logger.info("Creating ClusterResource {0} in namespace {1}",cluster1Res, domainNamespace);
    createClusterAndVerify(cluster);

    ClusterResource cluster2 = createClusterResource(
        cluster2Res, cluster2Name, domainNamespace, replicaCount);
    logger.info("Creating ClusterResource {0} in namespace {1}",cluster2Res, domainNamespace);
    createClusterAndVerify(cluster2);

    createModelConfigMap(domainUid,configMapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    // Do not set cluster references in domain resource
    // check only admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are not started
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServer1Prefix + i, domainUid, domainNamespace);
    }

    logger.info("Patch the domain resource with new cluster resource");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": {\"name\" : \"" + cluster1Res + "\"}"
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    ConditionFactory customConditionFactory = createCustomConditionFactory(0, 1, 5);
    checkPodExists(customConditionFactory, introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);
    
    // verify managed server services and pods are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServer1Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServer1Prefix + i, domainUid, domainNamespace);
    }
    logger.info("Patch domain resource by replacing cluster-1 with cluster-2");
    String patchStr2 = "["
        + "{\"op\": \"replace\",\"path\": \"/spec/clusters/0/name\", \"value\":"
        + " \"" + cluster2Res + "\""  
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr2);
    V1Patch patch2 = new V1Patch(patchStr2);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch2, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    //verify the introspector pod is created and runs
    String introspectPodNameBase2 = getIntrospectJobName(domainUid);
    checkPodExists(customConditionFactory, introspectPodNameBase2, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase2, domainUid, domainNamespace);

    // check managed server pods from cluster-1 are shutdown
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServer1Prefix + i,domainUid,domainNamespace);
    }

    // verify managed server pods from cluster-2 are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServer2Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServer2Prefix + i, domainUid, domainNamespace);
    }

    kubernetesCLIScaleCluster(cluster2Res,domainNamespace,3);
    checkPodReadyAndServiceExists(managedServer2Prefix + 3, domainUid, domainNamespace);
    logger.info("Cluster is scaled up to replica count 3");

    // Clean up resources
    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);
  }

  /**
   * Create WebLogic domain DR with domain level replica set to zero.
   * Patch the domain resource to add cluster resource CR1
   * Make sure only managed servers from cluster-1 comes up
   * Make sure cluster resource cluster-1 status matches domain status
   * Patch the domain resource to replace the resource  CR1 with CR2
   * Make sure managed servers from CR1 goes down and managed servers
   * from CR2 comes up.
   * Make sure cluster resource cluster-2 status matches domain status
   * Scale up the cluster CR2
   * Make sure cluster resource cluster-2 status matches domain status
   */
  @Test
  @DisplayName("Verify domain status for clusters matches cluster resource status")
  void testDomainStatusMatchesClusterResourceStatus() {

    String domainUid = "domain10";
    String adminServerPodName = domainUid + "-admin-server";
    String managedServer1Prefix = domainUid +  "-c1-managed-server";
    String managedServer2Prefix = domainUid + "-c2-managed-server";

    String cluster1Res   = domainUid + "-cluster-1";
    String cluster2Res   = domainUid + "-cluster-2";
    String cluster1Name  = "cluster-1";
    String cluster2Name  = "cluster-2";

    String configMapName = domainUid + "-configmap";

    // create and deploy cluster resource(s)
    ClusterResource cluster = createClusterResource(
        cluster1Res, cluster1Name, domainNamespace, replicaCount);
    logger.info("Creating ClusterResource {0} in namespace {1}",cluster1Res, domainNamespace);
    createClusterAndVerify(cluster);

    ClusterResource cluster2 = createClusterResource(
        cluster2Res, cluster2Name, domainNamespace, replicaCount);
    logger.info("Creating ClusterResource {0} in namespace {1} ",cluster2Res, domainNamespace);
    createClusterAndVerify(cluster2);
    createModelConfigMap(domainUid,configMapName);
    
    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
        domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster1Res));
    createDomainAndVerify(domain, domainNamespace);

    // verify managed server services and pods are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServer1Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServer1Prefix + i, domainUid, domainNamespace);
    }
    logger.info("Check cluster resource status is mirror of domain.status");
    assertDoesNotThrow(() -> {
          testUntil(
              clusterStatusMatchesDomain(domainUid, domainNamespace, cluster1Name), getLogger(),
              "Cluster Resource status matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster1Name,
                      "Available", "True"), getLogger(),
                  "Cluster Resource status condition type matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster1Name,
                      "Completed", "True"), getLogger(),
                  "Cluster Resource status condition type matches domain.status");
        }
    );
    logger.info("Patch domain resource by replacing cluster-1 with cluster-2");
    String patchStr2 = "["
        + "{\"op\": \"replace\",\"path\": \"/spec/clusters/0/name\", \"value\":"
        + " \"" + cluster2Res + "\""
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr2);
    V1Patch patch2 = new V1Patch(patchStr2);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch2, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    //verify the introspector pod is created and runs
    String introspectPodNameBase2 = getIntrospectJobName(domainUid);
    ConditionFactory customConditionFactory = createCustomConditionFactory(0, 1, 5);
    checkPodExists(customConditionFactory, introspectPodNameBase2, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase2, domainUid, domainNamespace);

    // check managed server pods from cluster-1 are shutdown
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServer1Prefix + i,domainUid,domainNamespace);
    }

    // verify managed server pods from cluster-2 are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServer2Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServer2Prefix + i, domainUid, domainNamespace);
    }
    logger.info("Check cluster resource status is mirror of domain.status");
    assertDoesNotThrow(() -> {

      testUntil(clusterStatusMatchesDomain(domainUid, domainNamespace, cluster2Name), getLogger(),
          "Cluster Resource status matches domain.status");
      testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Available", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
      testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Completed", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
    });
    kubernetesCLIScaleCluster(cluster2Res,domainNamespace,3);
    checkPodReadyAndServiceExists(managedServer2Prefix + 3, domainUid, domainNamespace);
    logger.info("Cluster is scaled up to replica count 3");
    logger.info("Check cluster resource status is mirror of domain.status");
    assertDoesNotThrow(() -> {
          testUntil(clusterStatusMatchesDomain(domainUid, domainNamespace, cluster2Name), getLogger(),
              "Cluster Resource status matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Available", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Completed", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
        }
    );
    // Clean up resources
    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);
  }

  /**
   * Create a WebLogic Domain Resource with domain level replica set to zero.
   * Create  kubernates cluster resources CR which corresponds to a 
   * WebLogic cluster cluster-1 in model file.
   * Associate Cluster Resource CR with Domain Resource DR  
   * Make sure only managed servers from cluster-1 comes up
   * Delete the cluster Resource CR
   * Make sure Domain Validation Error event is generated. 
   *
   * message: 'Domain domain2 failed due to 'Domain validation error': 
   * Cluster resource 'domain2-cluster-1' not found in namespace 
   * 'ns-ptkhxk'. Update the domain resource to correct the validation error.'
   *
   * However the managed servers should not stop.
   */
  @Test
  @DisplayName("Verify removal of a cluster resource generate Error Event")
  void testDeleteClusterResource() {

    String domainUid = "domain2";
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid +  "-c1-managed-server";

    String clusterRes   = domainUid + "-cluster-1";
    String clusterName  = "cluster-1";
    String configMapName = domainUid + "-configmap";

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);
 
    // create and deploy cluster resource(s)
    ClusterResource cluster = createClusterResource(
        clusterRes, clusterName, domainNamespace, replicaCount);
    logger.info("Creating ClusterResource {0} in namespace {1}",clusterRes, domainNamespace);
    createClusterAndVerify(cluster);
    createModelConfigMap(domainUid,configMapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterRes));

    createDomainAndVerify(domain, domainNamespace);
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are started
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // Delete the cluster resource with out de-associating it from domain 
    // In this case a Domain Validation event will be generated 
    // The managed server does not go down, and cluster becomes un-managable
    // as the clusster Resource does not exist.

    OffsetDateTime timestamp = now();
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);

    testUntil(withLongRetryPolicy,
        checkDomainFailedEventWithReason(opNamespace, domainNamespace,
        domainUid, "Domain validation error", "Warning", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        "Domain validation error",
        domainNamespace);

    // Clean up resources
    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);
  }

  /**
   * Create a cluster resource SC corresponding to WebLogic cluster cluster-1.
   * Create a domain resource DR1 with cluster reference set to SC.
   * Create a domain resource DR2 with cluster reference set to SC.
   * Start the domain resource DR1 with all manged servers in the cluster.
   * A Domain Validation Failed Event MUST be generated for domain DR2.
   *
   * message: 'Domain domain8 failed due to 'Domain validation error': 
   * Cannot reference cluster resource 'shared-cluster' because it is used 
   * by 'domain7'. Update the domain resource to correct the validation error.'
   *
   */
  @Test
  @DisplayName("Verify Domain Validation Failed Event for sharing Cluster Reference across domains")
  void testSharedClusterResource() {

    String domainUid   = "domain7";
    String domain2Uid  = "domain8";
    String clusterRes  =  "shared-cluster";
    String clusterName = "cluster-1";

    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid +  "-c1-managed-server";

    String config1MapName  = domainUid + "-configmap";
    String config2MapName = domain2Uid + "-configmap";

    OffsetDateTime timestamp = now();

    // Delete any pre-existing resources if any
    deleteDomainResource(domainUid, domainNamespace);
    deleteDomainResource(domain2Uid, domainNamespace);

    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);

    // create and deploy cluster resource
    ClusterResource cluster = createClusterResource(
        clusterRes, clusterName, domainNamespace, replicaCount);
    logger.info("Creating ClusterResource {0} in namespace {1}",clusterRes, domainNamespace);
    createClusterAndVerify(cluster);

    createModelConfigMap(domainUid,config1MapName);
    createModelConfigMap(domain2Uid,config2MapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, config1MapName);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterRes));
    // create model in image domain
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName,domainUid,domainNamespace);

    // verify managed server services and pods are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // create and deploy domain resource with cluster reference
    DomainResource domain2 = createDomainResource(domain2Uid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, config2MapName);
    // set cluster references
    domain2.getSpec().withCluster(new V1LocalObjectReference().name(clusterRes));
    // create model in image domain
    logger.info("Creating mii domain {0} in namespace {1} using image {2}",
        domain2Uid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain2, domainNamespace);

    testUntil(withLongRetryPolicy,
        checkDomainFailedEventWithReason(opNamespace, domainNamespace, 
        domain2Uid, "Domain validation error", "Warning", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        "Domain validation error",
        domainNamespace);

    deleteDomainResource(domainUid, domainNamespace);
    deleteDomainResource(domain2Uid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);
  }

  /**
   * Create a cluster resource CR3 with reference to WLS cluster cluster-3.
   * Here WebLogic Cluster cluster-3 doesn't exists in model/config file.
   * Create a domain resource DR with cluster reference set to CR3.
   * Deploy the domain DR. 
   * Make sure a Domain Configuration Mismatch Failed Event MUST be 
   * generated for the domain resource.
   */
  @Test
  @DisplayName("Verify WebLogic domain configuration mismatch error Failed Event for mismatch Cluster Reference")
  void testMismatchClusterResource() {

    String domainUid     = "domain3"; 
    String clusterName   = "cluster-3"; 
    String clusterRes    = domainUid + "cluster-3"; 
    String configMapName = domainUid + "-configmap"; 
    
    OffsetDateTime timestamp = now();

    // Delete any pre-existing resources if any
    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);

    ClusterResource cluster = createClusterResource(
        clusterRes, clusterName, domainNamespace, replicaCount);
    logger.info("Creating Cluster Resource {0} in namespace {1}",clusterRes, domainNamespace);
    createClusterAndVerify(cluster);
    createModelConfigMap(domainUid,configMapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterRes));
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);
    
    testUntil(withLongRetryPolicy,
        checkDomainFailedEventWithReason(opNamespace, domainNamespace, 
        domainUid, "WebLogic domain configuration mismatch error", 
        "Warning", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        "Domain validation error",
        domainNamespace);

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);
  }

  /*
   * Create a domain resource DR with two cluster resources CR1 and CR2.
   * Deploy only CR1 before deploying resource DR. 
   * Here we execpet a Domain Validation Error
   *
   *   message: 'Domain domain4 failed due to 'Domain validation error': 
   *   Cluster resource 'domain4-cluster-2' not found in namespace 'ns-mroefg'
   *   Update the domain resource to correct the validation error.'
   *
   * None of the server including admin server should start.
   */
  @Test
  @DisplayName("Verify servers on missing cluster resource are picked up when the resource is available")
  void testMissingClusterResource() {

    String domainUid = "domain4"; 
    String cluster1Name = "cluster-1"; 
    String cluster1Res = domainUid + "-cluster-1"; 
    String cluster2Name = "cluster-2"; 
    String cluster2Res = domainUid + "-cluster-2"; 

    String configMapName = domainUid + "-configmap"; 
   
    // Delete any pre-existing resources if any
    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);

    // Create and deploy cluster resource for one cluster (cluster-1)
    ClusterResource cluster = createClusterResource(
        cluster1Res, cluster1Name, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",cluster1Res, domainNamespace);
    createClusterAndVerify(cluster);

    // create and deploy domain resource
    createModelConfigMap(domainUid,configMapName);
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);
    // set cluster references to a non-existing cluster resource
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster1Res));
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster2Res));
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);

    OffsetDateTime timestamp = now();
    createDomainAndVerify(domain, domainNamespace);

    testUntil(withLongRetryPolicy,
        checkDomainFailedEventWithReason(opNamespace, domainNamespace, 
        domainUid, "Domain validation error", 
        "Warning", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        "Domain validation error",
        domainNamespace);

    //verify the introspector pod is not started
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    String managedServerPrefix = domainUid + "-c1-managed-server";
    String adminPodName   = domainUid + "-admin-server";

    // check no server pod is started
    checkPodDoesNotExist(adminPodName,domainUid,domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i,domainUid,domainNamespace);
    }

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);
  }

  /**
   * Start WebLogic domain using a single yaml with domain and cluster resource.
   */
  @Test
  @DisplayName("Start a WebLogic doamin with a single yaml with both domain and cluster resource")
  void testDomainYamlWithClusterResource() {

    String domainUid = "domain5"; 
    String clusterName = "cluster-1"; 
    String clusterRes = domainUid + "-cluster-5"; 

    String managedServerPrefix = domainUid + "-managed-server";
    String adminPodName   = domainUid + "-admin-server";
    
    // Delete any pre-existing resources if any
    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);

    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_API_VERSION", DOMAIN_API_VERSION);
    templateMap.put("WEBLOGIC-CREDENTIALS", adminSecretName);
    templateMap.put("RUNTIME-ENCRYPTION-SECRET", encryptionSecretName);
    templateMap.put("NAMESPACE", domainNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("CLUSTER_RES", clusterRes);
    templateMap.put("CLUSTERNAME", clusterName);
    templateMap.put("IMAGE", MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);

    Path srcDomainYaml = Paths.get(RESOURCE_DIR,"domain","mii-cluster-domain.yaml");
    Path destDomainYaml = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDomainYaml.toString(), "domain.yaml", templateMap));
    logger.info("Generated domain yaml file path is {0}", destDomainYaml);

    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(KUBERNETES_CLI + " create -f " + destDomainYaml))
        .execute(), KUBERNETES_CLI + " create failed");

    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminPodName,domainUid,domainNamespace);
    // check managed server pods are started
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i,domainUid,domainNamespace);
    }
    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);
  }

  /**
   * Create a WebLogic domain resource DR with domain level replica set to zero.
   * Create and deploy two cluster resources CR1 and CR2
   * Create and deploy the domain with two cluster resources CR1 and CR2
   * Verify status and conditions are matching for domain.status and cluster resource status
   * Scale only the cluster CR2 and make sure no new server from CR1 is up
   * Verify status and conditions are matching for domain.status and cluster resource status
   * Scale all the clusters in the namesapce using 
   *   KUBERNETES_CLI scale cluster --replicas=4  --all -n namespace
   * Scale all the clusters in the namesapce with replica count 1
   *   KUBERNETES_CLI scale cluster --initial-replicas=1 --replicas=5  --all -n ns
   * This command must fail as there is no cluster with currentreplica set to 1
   */
  @Test
  @DisplayName("Verify various kubernetes CLI scale options")
  void testKubernetesCLIScaleClusterResource() {

    String domainUid     = "domain6"; 
    String cluster1Name  = "cluster-1";
    String cluster2Name  = "cluster-2";

    String cluster1Res     = domainUid + "-cluster-1";
    String cluster2Res     = domainUid + "-cluster-2";
    String config1MapName  = domainUid + "-configmap";
    String config2MapName  = domainUid + "-configmap2";

    String adminPodName      = domainUid + "-admin-server";
    String managedPod1Prefix = domainUid + "-c1-managed-server";
    String managedPod2Prefix = domainUid + "-c2-managed-server";

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);

    // create and deploy cluster resource(s)
    ClusterResource cluster = createClusterResource(
        cluster1Res, cluster1Name, domainNamespace, replicaCount);
    logger.info("Creating Cluster Resource {0} in namespace {1}",cluster1Res, domainNamespace);
    createClusterAndVerify(cluster);

    ClusterResource cluster2 = createClusterResource(
        cluster2Res, cluster2Name, domainNamespace, replicaCount);
    logger.info("Creating Cluster Resource {0} in namespace {1}",cluster2Res, domainNamespace);
    createClusterAndVerify(cluster2);
    createModelConfigMap(domainUid,config1MapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, config1MapName);
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster1Res));
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster2Res));
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedPod1Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedPod1Prefix + i, domainUid, domainNamespace);
    }

    // verify managed server pods from cluster-2 are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedPod2Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedPod2Prefix + i, domainUid, domainNamespace);
    }
    //verify status and conditions are matching for domain.status and cluster resource status
    assertDoesNotThrow(() -> {
          testUntil(clusterStatusMatchesDomain(domainUid, domainNamespace, cluster1Name), getLogger(),
              "Cluster Resource status matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster1Name,
                  "Available", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster1Name,
                  "Completed", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
          testUntil(clusterStatusMatchesDomain(domainUid, domainNamespace, cluster2Name), getLogger(),
              "Cluster Resource status matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Available", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Completed", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
        }
    );
    // Scaling one Cluster(2) does not affect other Cluster(1)
    kubernetesCLIScaleCluster(cluster2Res,domainNamespace,3);
    checkPodReadyAndServiceExists(managedPod2Prefix + "3", domainUid, domainNamespace);
    checkPodDoesNotExist(managedPod1Prefix + "3", domainUid, domainNamespace);

    //verify status and conditions after scaling
    assertDoesNotThrow(() -> {
          testUntil(clusterStatusMatchesDomain(domainUid, domainNamespace, cluster2Name), getLogger(),
              "Cluster Resource status matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Available", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
          testUntil(clusterStatusConditionsMatchesDomain(domainUid, domainNamespace, cluster2Name,
                  "Completed", "True"), getLogger(),
              "Cluster Resource status condition type matches domain.status");
        }
    );

    // Scale all clusters in the namesapce to replicas set to 4
    // KUBERNETES_CLI scale cluster --replicas=4 --all -n namesapce
    String cmd = " --replicas=4 --all ";
    kubernetesCLIScaleCluster(cmd, domainNamespace,true);
    checkPodReadyAndServiceExists(managedPod1Prefix + "3", domainUid, domainNamespace);
    checkPodReadyAndServiceExists(managedPod1Prefix + "4", domainUid, domainNamespace);
    checkPodReadyAndServiceExists(managedPod1Prefix + "4", domainUid, domainNamespace);

    // KUBERNETES_CLI command must fail since non of the cluster has the 
    // current replicacount set to 1. All have the count of 4 
    cmd = " --replicas=5 --current-replicas=1 --all ";
    kubernetesCLIScaleCluster(cmd, domainNamespace,false);

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);
  }

  /**
   * Create a WebLogic domain resource DR with domain level replica set to zero.
   * Create and deploy two cluster resources CR1 and CR2
   * Create and deploy the domain with two cluster resources CR1 and CR2
   * Restart only the cluster CR2 by incrementing restart version on 
   * Cluster resource and make sure no effect on CR1 
   * Stop the cluster CR1 by updating the serverStartPolicy to Never
   * Start the cluster CR1 by updating the serverStartPolicy to IfNeeded
   */
  @Test
  @DisplayName("Verify restart/stop/start operation on cluster resource")
  void testManageClusterResource() {

    String domainUid     = "domain9"; 
    String cluster1Name  = "cluster-1";
    String cluster2Name  = "cluster-2";

    String cluster1Res     = domainUid + "-cluster-1";
    String cluster2Res     = domainUid + "-cluster-2";
    String config1MapName  = domainUid + "-configmap";
    String config2MapName  = domainUid + "-configmap2";

    String adminPodName      = domainUid + "-admin-server";
    String managedPod1Prefix = domainUid + "-c1-managed-server";
    String managedPod2Prefix = domainUid + "-c2-managed-server";

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);

    // create and deploy cluster resource(s)
    ClusterResource cluster = createClusterResource(
        cluster1Res, cluster1Name, domainNamespace, replicaCount);
    logger.info("Creating Cluster Resource {0} in namespace {1}",cluster1Res, domainNamespace);
    createClusterAndVerify(cluster);

    ClusterResource cluster2 = createClusterResource(
        cluster2Res, cluster2Name, domainNamespace, replicaCount);
    logger.info("Creating Cluster Resource {0} in namespace {1}",cluster2Res, domainNamespace);
    createClusterAndVerify(cluster2);
    createModelConfigMap(domainUid,config1MapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, config1MapName);
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster1Res));
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster2Res));
    createDomainAndVerify(domain, domainNamespace);

    LinkedHashMap<String, OffsetDateTime> c1Time = new LinkedHashMap<>();
    LinkedHashMap<String, OffsetDateTime> c2Time = new LinkedHashMap<>();

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminPodName, domainUid, domainNamespace);

    c1Time.put(adminPodName, getPodCreationTime(domainNamespace, adminPodName));

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedPod1Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedPod1Prefix + i, domainUid, domainNamespace);
      c1Time.put(managedPod1Prefix + i, 
           getPodCreationTime(domainNamespace, managedPod1Prefix + i));
    }

    // verify managed server pods from cluster-2 are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedPod2Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedPod2Prefix + i, domainUid, domainNamespace);
      c2Time.put(managedPod2Prefix + i, 
           getPodCreationTime(domainNamespace, managedPod2Prefix + i));
    }

    // Restart the Cluster(2) make sure it does not affect other Cluster(1)
    patchClusterResourceWithNewRestartVersion(cluster2Res,domainNamespace);

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    ConditionFactory customConditionFactory = createCustomConditionFactory(0, 1, 5);
    checkPodExists(customConditionFactory, introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    verifyPodsNotRolled(domainNamespace,c1Time);
    assertTrue(verifyRollingRestartOccurred(c2Time, 1, domainNamespace),
        String.format("Rolling restart failed for cluster %s in namespace %s", cluster2Res, domainNamespace));

    stopCluster(cluster1Res,domainNamespace);
    // check managed server pods are removed
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedPod1Prefix + i,domainUid,domainNamespace);
    }
    startCluster(cluster1Res,domainNamespace);
    // check managed server pods are created
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedPod1Prefix + i, domainUid, domainNamespace);
    }

    deleteDomainResource(domainUid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster1Res,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);
  }

  /**
   * Create a new Cluster resource with custom livenessProbe successThreshold value in serverPod to an invalid value.
   * Verify the create operation failed.
   */
  @Test
  @DisplayName("Test custom livenessProbe invalid successThreshold in serverPod")
  void testCustomLivenessProbeNegativeSuccessThreshold() {
    String domainUid     = "domain10";
    String cluster1Name  = "cluster-1";

    String cluster1Res     = domainUid + "-cluster-1";

    // create and deploy cluster resource(s)
    ClusterResource cluster = createClusterResource(
        cluster1Res, cluster1Name, domainNamespace, replicaCount);
    cluster.getSpec().serverPod(new ServerPod().livenessProbe(new ProbeTuning().successThreshold(2)));
    logger.info("Creating Cluster Resource {0} in namespace {1}",cluster1Res, domainNamespace);

    Exception exception = null;
    try {
      createClusterCustomResource(cluster);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception,
        String.format("Create cluster resource %s in namespace %s should fail", cluster1Res, domainNamespace));
  }

  // Create a domain resource with replicas count ZERO
  private DomainResource createDomainResource(String domainUid,
          String domNamespace, String adminSecretName,
          String repoSecretName, String encryptionSecretName,
          String miiImage, String configmapName) {

    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .replicas(0)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminChannelPortForwardingEnabled(false)
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap))
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .configMap(configmapName)
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  // create a ConfigMap with a model that add a cluster 
  private static void createModelConfigMap(String domainid, String cfgMapName) {
    String yamlString = "topology:\n"
        + "  Cluster:\n"
        + "    'cluster-1':\n"
        + "       DynamicServers: \n"
        + "         ServerTemplate: 'cluster-1-template' \n"
        + "         ServerNamePrefix: 'c1-managed-server' \n"
        + "         DynamicClusterSize: 5 \n"
        + "         MaxDynamicClusterSize: 5 \n"
        + "         CalculatedListenPorts: false \n"
        + "    'cluster-2':\n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "       Cluster: 'cluster-1' \n"
        + "       ListenPort : 8001 \n"
        + "  Server:\n"
        + "    'c2-managed-server1':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server2':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server3':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server4':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server5':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n";

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainid);
    Map<String, String> data = new HashMap<>();
    data.put("model.cluster.yaml", yamlString);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(cfgMapName)
            .namespace(domainNamespace));
    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", cfgMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", cfgMapName));
  }

  private static void deleteDomainResource(String duid, String namespace) {
    deleteDomainCustomResource(duid, namespace);
    testUntil(
        domainDoesNotExist(duid, DOMAIN_API_VERSION, namespace),
        getLogger(),
        "Doamin {0} to be deleted in namespace {1}",
        duid,
        namespace);
  }
}
