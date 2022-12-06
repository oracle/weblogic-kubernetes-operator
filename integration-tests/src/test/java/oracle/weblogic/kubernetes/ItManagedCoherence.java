// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createTraefikIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to associate a Coherence Cluster with multiple WebLogic server clusters.
@DisplayName("Test to associate a Coherence Cluster with multiple WebLogic server clusters")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItManagedCoherence {

  // constants for Coherence
  private static final String COHERENCE_APP_NAME = "CoherenceApp";

  // constants for creating domain image using model in image
  private static final String COHERENCE_MODEL_FILE = "coherence-managed-wdt-config.yaml";
  private static final String COHERENCE_MODEL_PROP = "coherence-managed-wdt-config.properties";
  private static final String COHERENCE_IMAGE_NAME = "coherence-managed-image";

  // constants for WebLogic domain
  private static final String domainUid = "coherence-managed-domain";
  private static final int NUMBER_OF_CLUSTERS = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int replicaCount = 2;
  private static String adminServerPodName = domainUid + "-admin-server";

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static HelmParams traefikHelmParams = null;
  private static LoggingFacade logger = null;

  /**
   * Install Traefik and operator, build two Coherence applications
   * 1. Coherence applications are packaged as Grid ARchives (GAR) and
   *    deployed on storage-enabled managed Coherence servers in cluster-2
   * 2. Coherence application GAR is packaged within an EAR and
   *    deployed on storage-disabled managed Coherence servers in cluster-1.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a unique Traefik namespace
    logger.info("Get a unique namespace for Traefik");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String traefikNamespace  = namespaces.get(0);

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    opNamespace = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace = namespaces.get(2);

    // install and verify Traefik if not running on OKD
    if (!OKD) {
      traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0);
    }

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // build Coherence applications
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, COHERENCE_APP_NAME),
        null, null, "builddir", domainNamespace);
    Path coherenceAppGarPath = Paths.get(distDir.toString(), COHERENCE_APP_NAME + ".gar");
    Path coherenceAppEarPath = Paths.get(distDir.toString(), COHERENCE_APP_NAME + ".ear");
    assertTrue(coherenceAppGarPath.toFile().exists(), "Application archive is not available");
    assertTrue(coherenceAppEarPath.toFile().exists(), "Application archive is not available");
    logger.info("Path of CoherenceApp EAR " + coherenceAppEarPath.toString());
    logger.info("Path of CoherenceApp GAR " + coherenceAppGarPath.toString());
  }

  /**
   * Create domain with two clusters, cluster-1 and cluster-2 and associate them with a Coherence cluster
   * Deploy the EAR file to cluster-1 that has no storage enabled
   * Deploy the GAR file to cluster-2 that has storage enabled
   * Verify that data can be added and stored in the cache and can also be retrieved from cache.
   */
  @Test
  @DisplayName("Two cluster domain with a Coherence cluster and test interaction with cache data")
  void testMultiClusterCoherenceDomain() {
    // create a DomainHomeInImage image using WebLogic Image Tool
    String domImage = createAndVerifyDomainImage();

    // create and verify a two-cluster WebLogic domain with a Coherence cluster
    createAndVerifyDomain(domImage);

    if (OKD) {
      String cluster1HostName = domainUid + "-cluster-cluster-1";

      final String cluster1IngressHost = createRouteForOKD(cluster1HostName, domainNamespace);

      // test adding data to the cache and retrieving them from the cache
      boolean testCompletedSuccessfully = assertDoesNotThrow(()
          -> coherenceCacheTest(cluster1IngressHost), "Test Coherence cache failed");
      assertTrue(testCompletedSuccessfully, "Test Coherence cache failed");
    } else {

      Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
      for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
        clusterNameMsPortMap.put(CLUSTER_NAME_PREFIX + i, MANAGED_SERVER_PORT);
      }
      // clusterNameMsPortMap.put(clusterName, managedServerPort);
      logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
      createTraefikIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap, true, null,
          traefikHelmParams.getReleaseName());

      String clusterHostname = domainUid + "." + domainNamespace + ".cluster-1.test";
      // get ingress service Nodeport
      String ingressServiceName = traefikHelmParams.getReleaseName();
      String traefikNamespace = traefikHelmParams.getNamespace();

      int ingressServiceNodePort = assertDoesNotThrow(()
              -> getServiceNodePort(traefikNamespace, ingressServiceName, "web"),
          "Getting Ingress Service node port failed");
      logger.info("Node port for {0} is: {1} :", ingressServiceName, ingressServiceNodePort);

      String hostAndPort = getHostAndPort(clusterHostname, ingressServiceNodePort);
      assertTrue(checkCoheranceApp(clusterHostname, hostAndPort), "Failed to access Coherance App cation");
      // test adding data to the cache and retrieving them from the cache
      boolean testCompletedSuccessfully = assertDoesNotThrow(()
          -> coherenceCacheTest(clusterHostname, ingressServiceNodePort), "Test Coherence cache failed");
      assertTrue(testCompletedSuccessfully, "Test Coherence cache failed");
    }
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String domImage = createImageAndVerify(
        COHERENCE_IMAGE_NAME, COHERENCE_MODEL_FILE,
          COHERENCE_APP_NAME, COHERENCE_MODEL_PROP, domainUid);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(domImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    return domImage;
  }

  private static void createAndVerifyDomain(String domImage) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, domImage);
    createDomainCrAndVerify(adminSecretName, domImage);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check that the managed server service exists in the domain namespace
        logger.info("Checking that managed server service {0} exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkServiceExists(managedServerPodName, domainNamespace);

        // check that the managed server pod is ready
        logger.info("Checking that managed server pod {0} is ready in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReady(managedServerPodName, domainUid, domainNamespace);
      }
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName, String domImage) {
    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("Image")
            .image(domImage)
            .replicas(replicaCount)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS"))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domainNamespace));
  }

  private boolean coherenceCacheTest(String hostName) {
    return coherenceCacheTest(hostName,0); //OKD does not need a port number
  }

  private boolean coherenceCacheTest(String hostName, int ingressServiceNodePort) {
    logger.info("Starting to test the cache");

    String hostAndPort = getHostAndPort(hostName, ingressServiceNodePort);
    logger.info("hostAndPort = {0} ", hostAndPort);


    // add the data to cache
    String[] firstNameList = {"Frodo", "Samwise", "Bilbo", "peregrin", "Meriadoc", "Gandalf"};
    String[] secondNameList = {"Baggins", "Gamgee", "Baggins", "Took", "Brandybuck", "TheGrey"};
    ExecResult result = null;
    for (int i = 0; i < firstNameList.length; i++) {
      result = addDataToCache(firstNameList[i], secondNameList[i], hostName, hostAndPort);
      assertTrue(result.stdout().contains(firstNameList[i]), "Did not add the expected record");
      logger.info("Data added to the cache " + result.stdout());
    }

    // check if cache size is 6
    result = getCacheSize(hostName, hostAndPort);
    logger.info("number of records in cache = " + result.stdout());
    if (!(result.stdout().equals("6"))) {
      logger.info("number of records in cache = " + result.stdout());
      assertEquals("6", result.stdout(), "Expected 6 records");
    }

    // get the data from cache
    result = getCacheContents(hostName, hostAndPort);
    logger.info("Cache contains the following entries \n" + result.stdout());

    // Now clear the cache
    result = clearCache(hostName, hostAndPort);
    logger.info("Cache is cleared and should be empty" + result.stdout());
    if (!(result.stdout().trim().equals("0"))) {
      logger.info("number of records in cache = " + result.stdout());
      assertNotEquals("0", result.stdout(), "Expected 0 records");
    }

    return true;
  }

  private ExecResult addDataToCache(String firstName,
                                    String secondName,
                                    String hostName,
                                    String hostAndPort) {
    logger.info("Add initial data to cache");
    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=add&first=")
        .append(firstName)
        .append("&second=")
        .append(secondName)
        .append("' ")
        .append("-X POST -H 'host: ")
        .append(hostName)
        .append("' http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to add initial data to cache {0} ", curlCmd.toString());
    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd.toString(), true),
        String.format("Failed to add initial data to cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to add initial data to cache. Error is %s ", result.stderr()));

    return result;
  }

  private ExecResult getCacheSize(String hostName, String hostAndPort) {
    logger.info("Get the number of records in cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=size' ")
        .append("-H 'host: ")
        .append(hostName)
        .append("' http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to get the number of records in cache " + curlCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd.toString(), true),
        String.format("Failed to get the number of records in cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to get the number of records in cache. Error is %s ", result.stderr()));

    return result;
  }

  private ExecResult getCacheContents(String hostName, String hostAndPort) {
    logger.info("Get the records from cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=get' ")
        .append("-H 'host: ")
        .append(hostName)
        .append("' http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to get the records from cache " + curlCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd.toString(), true),
        String.format("Failed to get the records from cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to get the records from cache. Error is %s ", result.stderr()));

    return result;
  }

  private ExecResult clearCache(String hostName, String hostAndPort) {
    logger.info("Clean the cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=clear' ")
        .append("-H 'host: ")
        .append(hostName)
        .append("' http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to clean the cache " + curlCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd.toString(), true),
        String.format("Failed to clean the cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to clean the cache. Error is %s ", result.stderr()));

    return result;
  }

  private boolean checkCoheranceApp(String hostName, String hostAndPort) {

    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=clear' ")
        .append("-X POST -H 'host: ")
        .append(hostName)
        .append("' http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append(" -o /dev/null")
        .append(" -w %{http_code};")
        .append("echo ${status}");
    logger.info("Command to check the Application {0} ", curlCmd.toString());
    testUntil(
        assertDoesNotThrow(() -> () -> exec(new String(curlCmd), true).stdout().contains("200")),
        logger,
        "application to be ready {0}",
        curlCmd);
    return true;
  }

}
