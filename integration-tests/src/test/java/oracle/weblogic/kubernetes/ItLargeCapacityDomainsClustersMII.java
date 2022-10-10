// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.ItMiiDynamicUpdatePart1.pathToAddClusterYaml;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to large capacity MII domain and multiple clusters.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the Operator can handle multiple MII domains and clusters at the same time.")
@IntegrationTest
@Tag("okdenv")
class ItLargeCapacityDomainsClustersMII {

  private static String opNamespace = null;

  private static int numOfDomains = 3;
  private static int numOfClusters = 3;
  private static final String baseDomainUid = "domain";
  private static List<String> domainNamespaces;

  private static String domainNamespace;
  private static final String domainUid = "mydomain";
  private static final String cluster1Name = "mycluster";
  private static final String adminServerName = "admin-server";
  private static final String adminServerPodName = domainUid + "-" + adminServerName;
  private static final String cluster1ManagedServerNameBase = cluster1Name + "-managed-server";
  private static final String cluster1ManagedServerPodNamePrefix = domainUid + "-" + cluster1ManagedServerNameBase;

  private static int clusterReplicaCount = 2;
  private static final int t3ChannelPort = getNextFreePort();
  private static final String wlSecretName = "weblogic-credentials";
  private static String wlsUserName = ADMIN_USERNAME_DEFAULT;
  private static String wlsPassword = ADMIN_PASSWORD_DEFAULT;

  private static String adminSvcExtHost = null;
  private static LoggingFacade logger;

  private V1Patch patch;

  /**
   * Assigns unique namespaces for operator and domains. Installs operator. Creates a WebLogic domain.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(50) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespaces for WebLogic domains");
    domainNamespaces = namespaces.subList(1, numOfDomains + 1);
    domainNamespace = namespaces.get(numOfDomains + 1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, namespaces.subList(1, 50).toArray(new String[0]));

    createDomain(domainUid, domainNamespace);

  }

  /**
   * Test brings up new domains and verifies it can successfully start by doing the following.
   *
   * a. Creates new WebLogic domains using offline WLST in persistent volume. b. Creates domain resource and deploys in
   * Kubernetes cluster. c. Verifies the servers in the new WebLogic domain comes up.
   */
  @Order(1)
  @Test
  @DisplayName("Test domains creation")
  void testCreateDomains() {
    String domainUid;
    for (int i = 0; i < numOfDomains; i++) {
      domainUid = baseDomainUid + (i + 1);
      createDomain(domainUid, domainNamespaces.get(i));
    }
  }

  /**
   * Test creates new clusters and verifies it can successfully start by doing the following.
   *
   * a. Creates new WebLogic static clusters using online WLST. b. Patch the Domain Resource with clusters c. Update the
   * introspectVersion version d. Verifies the servers in the new WebLogic cluster comes up. e. Repeat the above cycle
   * for a number of clusters. Bug - OWLS-102898
   */
  @Order(2)
  @Test
  @DisplayName("Test new clusters creation on demand using model files in configmap and introspection")
  void testCreateNewClusters() {

    logger.info("Getting port for default channel");
    // Need to expose the admin server external service to access the console in OKD cluster only
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    int nodePort = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, nodePort, "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    String hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);

    String clusterBaseName = "mycluster-";
    for (int j = 1; j <= numOfClusters; j++) {
      String clusterName = clusterBaseName + j;
      String clusterManagedServerNameBase = clusterName + "-config-server";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;

      String configMapName = "configclusterconfigmap";
      createClusterConfigMap(configMapName, createModelFiles(clusterName));

      String patchStr = null;
      patchStr = "[{"
          + "\"op\": \"replace\","
          + " \"path\": \"/spec/configuration/model/configMap\","
          + " \"value\":  \"" + configMapName + "\""
          + " }]";
      logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

      patch = new V1Patch(patchStr);
      boolean cmPatched = assertDoesNotThrow(()
          -> patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
          "patchDomainCustomResource(configMap)  failed ");
      assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

      logger.info("patch the domain resource with new cluster and introspectVersion");
      patchStr
          = "["
          + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
          + "    {\"clusterName\" : \"" + clusterName + "\", \"replicas\": "
          + clusterReplicaCount + ", \"serverStartState\": \"RUNNING\"}"
          + "},"
          + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
          + "]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr.toString());
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      //verify the introspector pod is created and runs
      String introspectPodNameBase = getIntrospectJobName(domainUid);

      checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
      checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

      // verify managed server services and pods are created
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }

      List<String> managedServerNames = new ArrayList<>();
      for (int i = 1; i <= clusterReplicaCount; i++) {
        managedServerNames.add(clusterManagedServerNameBase + i);
      }
    }
  }

  /**
   * Test creates new clusters in shutdown state and verifies it can successfully start after patching the domain with
   * new introspectVersion string
   *
   * a. Creates new WebLogic static clusters using online WLST. b. Patch the Domain Resource with clusters. c. Update
   * the introspectVersion version. d. Verifies the servers in the new WebLogic clusters comes up without affecting any
   * of the running servers on pre-existing WebLogic cluster.
   */
  @Order(3)
  @Test
  @DisplayName("Test new cluster creations and starting up on introspection on demand using WLST")
  void testCreateNewClustersDontStart() {
    String patchStr = null;

    logger.info("Getting port for default channel");
    int adminServerPort
        = getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    // Need to expose the admin server external service to access the console in OKD cluster only
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName),
        domainNamespace);

    int nodePort = getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, nodePort,
        "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    String hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);

    String clusterBaseName = "sdcluster-";
    for (int j = 1; j <= numOfClusters; j++) {
      String clusterName = clusterBaseName + j;
      String clusterManagedServerNameBase = "config-server";

      String configMapName = "configclusterconfigmap";
      createClusterConfigMap(configMapName, createModelFiles(clusterName));

      patchStr = "[{"
          + "\"op\": \"replace\","
          + " \"path\": \"/spec/configuration/model/configMap\","
          + " \"value\":  \"" + configMapName + "\""
          + " }]";
      logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

      patch = new V1Patch(patchStr);
      boolean cmPatched = assertDoesNotThrow(()
          -> patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
          "patchDomainCustomResource(configMap)  failed ");
      assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

      logger.info("patch the domain resource with new cluster and introspectVersion");
      patchStr
          = "["
          + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
          + "    {\"clusterName\" : \"" + clusterName + "\", \"replicas\": "
          + clusterReplicaCount + ", \"serverStartState\": \"IF_NEEDED\"}"
          + "}"
          + "]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr.toString());
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
    }

    logger.info("Restarting admin server");
    restartAS();

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));
    logger.info("patch the domain resource with new cluster and introspectVersion");
    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    for (int j = 1; j <= numOfClusters; j++) {
      String clusterManagedServerNameBase = "config-server";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;
      // verify managed server services and pods are created
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }

      List<String> managedServerNames = new ArrayList<>();
      for (int i = 1; i <= clusterReplicaCount; i++) {
        managedServerNames.add(clusterManagedServerNameBase + i);
      }

      // verify managed server services and pods are created
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }
    }
  }

  /**
   * Test shuts down all existing clusters and starts up.
   *
   * a. Shutdowns all cluster using serverStartPolicy NEVER. b. Patch the Domain Resource with cluster serverStartPolicy
   * IF_NEEDED. c. Verifies the servers in the domain cluster comes up.
   */
  @Order(4)
  @Test
  @DisplayName("Test cluster shutdown and startup")
  void testRestartClusters() {

    String clusterBaseName = "sdcluster-";
    //shutdown all clusters in default domain
    for (int j = 1; j <= numOfClusters; j++) {
      String clusterManagedServerNameBase = "config-server";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;

      logger.info("patch the domain resource with new cluster and introspectVersion");
      String patchStr
          = "[{\"op\": \"replace\", \"path\": \"/spec/clusters/" + j + "/serverStartPolicy\", "
          + "\"value\": \"NEVER\""
          + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is deleted in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodDoesNotExist(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }
    }
    //startup all clusters in default domain
    for (int j = 1; j <= numOfClusters; j++) {
      String clusterManagedServerNameBase = "config-server";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;

      logger.info("patch the domain resource with new cluster and introspectVersion");
      String patchStr
          = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/" + j + "/serverStartPolicy\", "
          + "\"value\": \"IF_NEEDED\""
          + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      // verify managed server services and pods are created
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }
    }

  }

  private void restartAS() {
    //restart admin server
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/adminServer/serverStartPolicy\", \"value\": \"NEVER\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/adminServer/serverStartPolicy\", \"value\": \"IF_NEEDED\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    // verify the admin server service and pod created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
  }

  // Create a domain resource with a custom ConfigMap
  private static Domain createDomainResourceWithConfigMap(String domainUid,
      String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName,
      int replicaCount, String miiImage, String configmapName) {

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  private static void createDomain(String domainUid, String namespace) {

    String adminSecretName = "weblogic-credentials";
    String encryptionSecretName = "encryptionsecret";
    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";

    createSecrets(namespace);

    // create the domain object
    Domain domain = createDomainResourceWithConfigMap(domainUid, namespace,
        adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, clusterReplicaCount,
        image, null);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, image);
    createDomainAndVerify(domain, namespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, namespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, namespace);
    // check managed server pods are ready
    for (int j = 1; j <= clusterReplicaCount; j++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + j, namespace);
      checkPodReadyAndServiceExists(managedServerPrefix + j, domainUid, namespace);
    }

    // Need to expose the admin server external service to access the console in OKD cluster only
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName),
        namespace);

    int nodePort = getServiceNodePort(
        namespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, nodePort,
        "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    String hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);

    if (!WEBLOGIC_SLIM) {
      String curlCmd2 = "curl -s --show-error --noproxy '*' "
          + " http://" + hostAndPort
          + "/console/login/LoginForm.jsp --write-out %{http_code} -o /dev/null";
      logger.info("Executing default nodeport curl command {0}", curlCmd2);
      assertTrue(callWebAppAndWaitTillReady(curlCmd2, 5));
      logger.info("WebLogic console is accessible thru default service");
    } else {
      logger.info("Checking Rest API management console in WebLogic slim image");
      verifyCredentials(adminSvcExtHost, adminServerPodName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, true);
    }
  }

  // Crate a ConfigMap with a model file to add a new WebLogic cluster
  private void createClusterConfigMap(String configMapName, Path modelFile) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    Map<String, String> data = new HashMap<>();
    String cmData = null;
    cmData = assertDoesNotThrow(() -> Files.readString(modelFile),
        String.format("readString operation failed for %s", modelFile));
    assertNotNull(cmData, String.format("readString() operation failed while creating ConfigMap %s", configMapName));
    data.put(modelFile.getFileName().toString(), cmData);

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(domainNamespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed while creating ConfigMap %s", configMapName));
  }

  private static Path createModelFiles(String clusterName) {

    // write sparse yaml to file
    pathToAddClusterYaml = Paths.get(WORK_DIR + "/addcluster.yaml");

    String yamlToAddCluster = "topology:\n"
        + "    Cluster:\n"
        + "        '" + clusterName + "':\n"
        + "    Server:\n"
        + "        '" + clusterName + "-config-server1':\n"
        + "            Cluster: '" + clusterName + "'\n"
        + "            ListenPort: '8001'\n"
        + "        '" + clusterName + "-config-server2':\n"
        + "            Cluster: '" + clusterName + "'\n"
        + "            ListenPort: '8001'\n";

    return assertDoesNotThrow(() -> Files.write(pathToAddClusterYaml, yamlToAddCluster.getBytes()));
  }

  private static void createSecrets(String namespace) {
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(namespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, namespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, namespace,
        "weblogicenc", "weblogicenc");
  }

}
