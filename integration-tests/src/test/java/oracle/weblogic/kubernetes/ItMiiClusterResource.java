// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.HashMap;
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
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
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
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainFailedEventWithReason;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain with Cluster Resources
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
  private String domainUid = "domain1";
  private String domain2Uid = "domain2";
  private String miiImage = null;
  private static LoggingFacade logger = null;
  final String adminServerPodName = domainUid + "-admin-server";
  final String managedServerPrefix = domainUid +  "-c1-managed-server";
  final String managedServerPrefix2 = domainUid + "-c2-managed-server";
  final int replicaCount = 2;
  private String clusterRes = "cluster-1";
  private String cluster2Res = "cluster-2";
  private String clusterName = "cluster-1";
  private String clusterName2 = "cluster-2";
  private static String configMapName = "cluster-configmap";
  private static String config2MapName = "cluster-configmap2";
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";

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
   * Create a (mii) WebLogic domain with domain level replica set to zero.
   * Do not not associate any Cluster Resource with domain even if two 
   * WebLogic clusters (cluster-1 and cluster-2) are configuted in config.xml
   * Create two kubernates cluster resources CR1 and CR2 
   * corresponding to WebLogic clusters cluster-1 and cluster-2.
   * Start the domain and make sure no managed servers are started from either 
   * WebLogic Cluster. 
   * Patch the domain resource to add cluster resource CR1
   * Make sure only managed servers from cluster-1 comes up
   * Patch the domain resource to replace the resource  CR1 with CR2
   * Make sure managed servers from cluster-1 goes down and managed servers 
   * from cluster-2 comes up.
   * Delete the cluster Resource (CR2)
   * Make sure managed servers from cluster-2 goes down 
   */
  @Test
  @DisplayName("Verify dynamic add/remove of cluster resource on domain")
  void testManageClusterResource() {

    // create and deploy cluster resource(s)
    ClusterResource cluster = createClusterResource(
        clusterRes, clusterName, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",clusterRes, domainNamespace);
    createClusterAndVerify(cluster);

    ClusterResource cluster2 = createClusterResource(
        cluster2Res, clusterName2, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",cluster2Res, domainNamespace);
    createClusterAndVerify(cluster2);

    createModelConfigMap(domainUid,configMapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);
    logger.info("Creating mii domain {0} in namespace {1} using image {2}",
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
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Patch the domain resource with new cluster resource");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": {\"name\" : \"" + clusterRes + "\"}"
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);
    
    // verify managed server services and pods are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
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

    patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);

    //verify the introspector pod is created and runs
    String introspectPodNameBase2 = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase2, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    // check managed server pods from cluster-1 are shutdown
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // verify managed server pods from cluster-2 are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix2 + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix2 + i, domainUid, domainNamespace);
    }

    kubectlScaleCluster(cluster2Res,domainNamespace,3);
    checkPodReadyAndServiceExists(managedServerPrefix2 + 3, domainUid, domainNamespace);
    logger.info("Cluster is scaled up to replica count 3");
   
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);
    // check managed server pods from cluster-2 are shutdown
    for (int i = 1; i <= replicaCount + 1; i++) {
      checkPodDoesNotExist(managedServerPrefix2 + i,domainUid,domainNamespace);
    }
    deleteDomainResource(domainUid, domainNamespace);
  }

  /**
   * Create a cluster resorce say cluser-1.
   * Create a domain recource domain1 with cluster reference set to cluser-1.
   * Create a domain recource domain2 with cluster reference set to cluser-1.
   * Start the domain domain1 with all manged servers in the cluster.
   * A Domain Validation Failed Event MUST be generated for domain domain2.
   */
  @Test
  @DisplayName("Verify Domain Validation Failed Event for sharing Cluster Reference across domains")
  void testSharedClusterResource() {

    OffsetDateTime timestamp = now();

    // Delete any pre-existing resources if any
    deleteDomainResource(domainUid, domainNamespace);
    deleteDomainResource(domain2Uid, domainNamespace);
    deleteClusterCustomResourceAndVerify(clusterRes,domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster2Res,domainNamespace);

    // create and deploy cluster resource
    ClusterResource cluster = createClusterResource(
        clusterRes, clusterName, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",clusterRes, domainNamespace);
    createClusterAndVerify(cluster);

    createModelConfigMap(domainUid,configMapName);
    createModelConfigMap(domain2Uid,config2MapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterRes));
    // create model in image domain
    logger.info("Creating mii domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);
    
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

    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName,domainUid,domainNamespace);

    // verify managed server services and pods are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
                 managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    testUntil(withLongRetryPolicy,
        checkDomainFailedEventWithReason(opNamespace, domainNamespace, 
        domain2Uid, "Domain validation error", "Warning", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        "Domain validation error",
        domainNamespace);
  }

  /**
   * Create a cluster resource CR3 with reference to WLS cluster cluster-3.
   * Here WebLogic Cluster cluster-3 doesn't exists in model/config file.
   * Create a domain recource DR3 with cluster reference set to CR3.
   * Start the domain DR3. 
   * Make sure a Domain Configuration Mismatch Failed Event MUST be 
   * generated for the domain resource.
   */
  @Test
  @DisplayName("Verify WebLogic domain configuration mismatch error Failed Event for mismatch Cluster Reference")
  void testMismatchClusterResource() {

    String domain3Uid = "domain3"; 
    String cluster3Res = "cluster-3"; 
    String cluster3Name = "cluster-3"; 
    String config3MapName = "config-cluster-3"; 
    
    OffsetDateTime timestamp = now();

    // Delete any pre-existing resources if any
    deleteDomainResource(domain3Uid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster3Res,domainNamespace);

    ClusterResource cluster = createClusterResource(
        cluster3Res, cluster3Name, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",cluster3Res, domainNamespace);
    createClusterAndVerify(cluster);
    createModelConfigMap(domain3Uid,config3MapName);

    // create and deploy domain resource
    DomainResource domain = createDomainResource(domain3Uid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, config3MapName);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster3Res));
    logger.info("Creating mii domain {0} in namespace {1} using image {2}",
        domain3Uid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);
    
    testUntil(withLongRetryPolicy,
        checkDomainFailedEventWithReason(opNamespace, domainNamespace, 
        domain3Uid, "WebLogic domain configuration mismatch error", 
        "Warning", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        "Domain validation error",
        domainNamespace);
  }

  /*
   * Create a domain recource DR4 with cluster resource CR4.
   * Here the cluster resource CR4 refers to WebLogic cluster in model file.
   * Note here the CR4 is not deployed before deploying resource DR4. 
   * Deploy resource DR4 and make sure only admin server is started.
   * (Note) Should we generate a Warning Event (OWLS-102704) 
   * Deploy resource CR4 and make sure that all servers in CR4 comes up. 
   */
  @Test
  @DisplayName("Verify servers on missing clsuer resource are picked up when the resource is available")
  void testMissingClusterResource() {

    String domain4Uid = "domain4"; 
    String cluster4Res = "cluster-4"; 
    String cluster4Name = "cluster-1"; 
    String config4MapName = "config-cluster-4"; 
    OffsetDateTime timestamp = now();
   
    // Delete any pre-existing resources if any
    deleteDomainResource(domain4Uid, domainNamespace);
    deleteClusterCustomResourceAndVerify(cluster4Res,domainNamespace);

    // create and deploy domain resource
    createModelConfigMap(domain4Uid,config4MapName);
    DomainResource domain = createDomainResource(domain4Uid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, config4MapName);
    // set cluster references to a non-existing cluster resource
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster4Res));
    logger.info("Creating mii domain {0} in namespace {1} using image {2}",
        domain4Uid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    String managedServerPrefix4 = domain4Uid + "-c1-managed-server";
    String adminPodName   = domain4Uid + "-admin-server";

    String managedPodName = "domain4-managed-server1";
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminPodName,domain4Uid,domainNamespace);

    // check managed server pods are not started
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix4 + i,domain4Uid,domainNamespace);
    }

    // create and deploy cluster resource
    ClusterResource cluster = createClusterResource(
        cluster4Res, cluster4Name, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",cluster4Res, domainNamespace);
    createClusterAndVerify(cluster);

    // check managed server pods are not started
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix4 + i,domain4Uid,domainNamespace);
    }
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
        + "         DynamicClusterSize: 3 \n"
        + "         MaxDynamicClusterSize: 3 \n"
        + "         CalculatedListenPorts: false \n"
        + "    'cluster-2':\n"
        + "       DynamicServers: \n"
        + "         ServerTemplate: 'cluster-2-template' \n"
        + "         ServerNamePrefix: 'c2-managed-server' \n"
        + "         DynamicClusterSize: 4 \n"
        + "         MaxDynamicClusterSize: 4 \n"
        + "         CalculatedListenPorts: false \n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "       Cluster: 'cluster-1' \n"
        + "       ListenPort : 8001 \n"
        + "    'cluster-2-template':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 9001 \n";

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
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", cfgMapName));
  }

  private static void kubectlScaleCluster(String clusterRef, String namespace, int replica) {
    getLogger().info("Scaling cluster resource {0} in namespace {1} using kubectl scale command", 
          clusterRef, namespace);
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl scale  clusters/" + clusterRef 
        + " --replicas=" + replica + " -n " + namespace);
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed scale the cluster");
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
