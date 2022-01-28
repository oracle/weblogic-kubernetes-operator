// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Istio;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createService;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createAndPushMiiImage;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainWithIstioMultiClusters;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.IstioUtils.isLocalHostBindingsEnabled;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to associate a Coherence Cluster with multiple WebLogic server clusters.
@DisplayName("Test to associate a Coherence Cluster with multiple WebLogic server clusters")
@IntegrationTest
class ItIstioManagedCoherence {

  // constants for Coherence
  private static final String COHERENCE_APP_NAME = "CoherenceApp";

  // constants for creating domain image using model in image
  private static final String COHERENCE_MODEL_FILE = "coherence-managed-wdt-config.yaml";
  private static final String MII_COHERENCE_MODEL_FILE = "coherence-managed-wdt-config-mii.yaml";
  private static final String MII_COHERENCE_MODEL2_FILE = "coherence-managed-wdt-config-mii2.yaml";
  private static final String COHERENCE_MODEL_PROP = "coherence-managed-wdt-config.properties";
  private static final String COHERENCE_IMAGE_NAME = "coherence-managed-image";

  // constants for WebLogic domain
  private static final String domainUid = "coherence-managed-domain";
  private static final int NUMBER_OF_CLUSTERS = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int replicaCount = 2;
  private static String adminServerPodName = domainUid + "-admin-server";
  private static final String miiImageName = "mii-image";
  private static final String miiDomainUid = "miidomain";
  private static final String multiDomainsPrefix = "multidomain";

  private static String opNamespace = null;
  private static String domainInImageNamespace = null;
  private static String miiDomainNamespace = null;
  private static String multiDomainsNamespace = null;
  private static String miiImage = null;
  private static String encryptionSecretName = "encryptionsecret";

  private static LoggingFacade logger = null;

  /**
   * Install operator, build two Coherence applications
   * 1. Coherence applications are packaged as Grid ARchives (GAR) and
   *    deployed on storage-enabled managed Coherence servers in cluster-2
   * 2. Coherence application GAR is packaged within an EAR and
   *    deployed on storage-disabled managed Coherence servers in cluster-1.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique namespace for domain-in-image domain
    logger.info("Assigning a unique namespace for domain-in-image domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainInImageNamespace = namespaces.get(1);

    // get a new unique namespace for model-in-image domain
    logger.info("Assigning a unique namespace for model-in-image domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    miiDomainNamespace = namespaces.get(2);

    // get a new unique namespace for multiple model-in-image domains
    logger.info("Assigning a unique namespace for multiple model-in-image domains");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    multiDomainsNamespace = namespaces.get(3);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(domainInImageNamespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(miiDomainNamespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(multiDomainsNamespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace, labelMap));

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainInImageNamespace, miiDomainNamespace, multiDomainsNamespace);

    // build Coherence applications
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, COHERENCE_APP_NAME),
        null, null, "builddir", domainInImageNamespace);
    Path coherenceAppGarPath = Paths.get(distDir.toString(), COHERENCE_APP_NAME + ".gar");
    Path coherenceAppEarPath = Paths.get(distDir.toString(), COHERENCE_APP_NAME + ".ear");
    assertTrue(coherenceAppGarPath.toFile().exists(), "Application archive is not available");
    assertTrue(coherenceAppEarPath.toFile().exists(), "Application archive is not available");
    logger.info("Path of CoherenceApp EAR " + coherenceAppEarPath.toString());
    logger.info("Path of CoherenceApp GAR " + coherenceAppGarPath.toString());
  }

  /**
   * Create domain-in-image domain with two clusters, cluster-1 and cluster-2.
   * Associate them with a Coherence cluster
   * Deploy the EAR file to cluster-1 that has no storage enabled
   * Deploy the GAR file to cluster-2 that has storage enabled
   * Verify that data can be added and stored in the cache 
   * and can also be retrieved from cache.
   */
  @Test
  @DisplayName("Two cluster domain-in-image domain with a Coherence cluster with ISTIO and "
      + "test interaction with cache data")
  void testIstioMultiClusterCoherenceDomainInImageDomain() {

    // create a DomainHomeInImage image using WebLogic Image Tool
    String domImage = createAndVerifyDomainImage();

    // create and verify a two-cluster WebLogic domain with a Coherence cluster
    createAndVerifyDomain(domImage);

    String clusterService = domainUid + "-cluster-cluster-1";
    Map<String, String> templateMap  = new HashMap();
    templateMap.put("NAMESPACE", domainInImageNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE", adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-coh-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);
    
    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile)); 
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    // Make sure ready app is accessible thru Istio Ingress Port
    String curlCmd = "curl --silent --show-error --noproxy '*' http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort
        + "/weblogic/ready --write-out %{http_code} -o /dev/null";
    logger.info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));

    // test adding data to the cache and retrieving them from the cache
    boolean testCompletedSuccessfully = assertDoesNotThrow(()
          -> coherenceCacheTest(istioIngressPort), "Test Coherence cache failed");
    assertTrue(testCompletedSuccessfully, "Test Coherence cache failed");
  }


  /**
   * Create model-in-image domain with two clusters, cluster-1 and cluster-2.
   * Associate them with a Coherence cluster
   * Deploy the EAR file to cluster-1 that has no storage enabled
   * Deploy the GAR file to cluster-2 that has storage enabled
   * Verify that data can be added and stored in the cache
   * and can also be retrieved from cache.
   */
  @Test
  @DisplayName("Two cluster mii domain with a Coherence cluster with ISTIO and test interaction with cache data")
  void testIstioMultiClusterCoherenceMiiDomain() {

    // create a model-in-image domain image using WebLogic Image Tool
    miiImage = createAndPushMiiImage(miiImageName, MII_COHERENCE_MODEL_FILE, COHERENCE_APP_NAME, COHERENCE_MODEL_PROP);

    // create and verify a two-clusters WebLogic domain with a Coherence cluster
    createMiiDomainWithIstioMultiClusters(miiDomainUid, miiDomainNamespace, miiImage, NUMBER_OF_CLUSTERS, replicaCount);

    Map<String, String> templateMap  = new HashMap();
    templateMap.put("NAMESPACE", miiDomainNamespace);
    templateMap.put("DUID", miiDomainUid);
    templateMap.put("ADMIN_SERVICE", miiDomainUid + "-" + ADMIN_SERVER_NAME_BASE);
    templateMap.put("CLUSTER_SERVICE", miiDomainUid + "-cluster-cluster-1");

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-coh-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    // Make sure ready app is accessible thru Istio Ingress Port
    String curlCmd = "curl --silent --show-error --noproxy '*' http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort
        + "/weblogic/ready --write-out %{http_code} -o /dev/null";
    logger.info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));

    // test adding data to the cache and retrieving them from the cache
    boolean testCompletedSuccessfully = assertDoesNotThrow(()
        -> coherenceCacheTest(istioIngressPort), "Test Coherence cache failed");
    assertTrue(testCompletedSuccessfully, "Test Coherence cache failed");
  }

  /**
   * Create two model-in-image domains with two clusters each, cluster-1 and cluster-2.
   * Associate them with a Coherence cluster
   * Deploy the EAR file to cluster-1 that has no storage enabled
   * Deploy the GAR file to cluster-2 that has storage enabled
   * Verify that data can be added and stored in the cache and can also be retrieved from cache.
   */
  @Test
  @DisplayName("A Coherence cluster associate with two mii domains with two-clusters "
      + "and test interaction with cache data")
  void testIstioMultiClusterCoherenceMultiMiiDomain() {

    // create WKA service for the coherence cluster
    Map<String, String> selectors = new HashMap<>();
    selectors.put("multi.domain.wka.pods", "true");

    List<V1ServicePort> ports = new ArrayList<>();
    ports.add(new V1ServicePort()
        .name("tcp-coherence")
        .port(7777)
        .targetPort(new IntOrString(7777))
        .protocol("TCP"));

    V1Service service = new V1Service()
        .metadata(new V1ObjectMeta()
            .name("multi-domain-svc")
            .namespace(multiDomainsNamespace))
        .spec(new V1ServiceSpec()
            .clusterIP("None")
            .ports(ports)
            .publishNotReadyAddresses(true)
            .selector(selectors)
            .type("ClusterIP"));

    assertNotNull(service, "Can't create multi-domain-svc service, returns null");
    assertDoesNotThrow(() -> createService(service), "Can't create multi-domain-svc service");
    checkServiceExists("multi-domain-svc", multiDomainsNamespace);

    // create a model-in-image domain image using WebLogic Image Tool
    miiImage = createAndPushMiiImage(miiImageName, MII_COHERENCE_MODEL2_FILE, COHERENCE_APP_NAME, COHERENCE_MODEL_PROP);

    // create and verify a two-clusters WebLogic domain with a Coherence cluster
    Map<String, String> serverPodLabels = new HashMap<>();
    serverPodLabels.put("multi.domain.wka.pods", "true");
    createMiiDomainWithIstioMultiClusters(multiDomainsPrefix + "1", multiDomainsNamespace, miiImage,
        NUMBER_OF_CLUSTERS, replicaCount, serverPodLabels);
    createMiiDomainWithIstioMultiClusters(multiDomainsPrefix + "2", multiDomainsNamespace, miiImage,
        NUMBER_OF_CLUSTERS, replicaCount, serverPodLabels);

    // create a service to target to cluster-1 pods for both domains which hosts coherenceApp
    selectors.clear();
    selectors.put("weblogic.clusterName", "cluster-1");

    ports = new ArrayList<>();
    ports.add(new V1ServicePort()
        .name("tcp-coherenceapp")
        .port(8001)
        .targetPort(new IntOrString(8001))
        .protocol("TCP"));

    V1Service coherenceappService = new V1Service()
        .metadata(new V1ObjectMeta()
            .name("multi-domain-coherenceapp-svc")
            .namespace(multiDomainsNamespace))
        .spec(new V1ServiceSpec()
            .ports(ports)
            .selector(selectors)
            .type("ClusterIP"));

    assertNotNull(service, "Can't create multi-domain-coherenceapp-svc service, returns null");
    assertDoesNotThrow(() -> createService(coherenceappService),
        "Can't create multi-domain-coherenceapp-svc service");
    checkServiceExists("multi-domain-coherenceapp-svc", multiDomainsNamespace);

    Map<String, String> templateMap  = new HashMap();
    templateMap.put("NAMESPACE", multiDomainsNamespace);
    templateMap.put("DUID", multiDomainsPrefix + "1");
    templateMap.put("ADMIN_SERVICE", multiDomainsPrefix + "1-" + ADMIN_SERVER_NAME_BASE);
    templateMap.put("CLUSTER_SERVICE", "multi-domain-coherenceapp-svc");

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-coh-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    // Make sure ready app is accessible thru Istio Ingress Port
    String curlCmd = "curl --silent --show-error --noproxy '*' http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort
        + "/weblogic/ready --write-out %{http_code} -o /dev/null";
    logger.info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));

    // test adding data to the cache and retrieving them from the cache
    boolean testCompletedSuccessfully = assertDoesNotThrow(()
        -> coherenceCacheTest(istioIngressPort), "Test Coherence cache failed");
    assertTrue(testCompletedSuccessfully, "Test Coherence cache failed");
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String domImage = createImageAndVerify(
        COHERENCE_IMAGE_NAME, COHERENCE_MODEL_FILE,
        COHERENCE_APP_NAME, COHERENCE_MODEL_PROP, domainUid);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", domainInImageNamespace);
    createOcirRepoSecret(domainInImageNamespace);
    return domImage;
  }

  private static void createAndVerifyDomain(String domImage) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainInImageNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainInImageNamespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainInImageNamespace, domImage);
    createDomainCrAndVerify(adminSecretName, domImage);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainInImageNamespace);
    checkServiceExists(adminServerPodName, domainInImageNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainInImageNamespace);
    checkPodReady(adminServerPodName, domainUid, domainInImageNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check that the managed server service exists in the domain namespace
        logger.info("Checking that managed server service {0} exists in namespace {1}",
            managedServerPodName, domainInImageNamespace);
        checkServiceExists(managedServerPodName, domainInImageNamespace);

        // check that the managed server pod is ready
        logger.info("Checking that managed server pod {0} is ready in namespace {1}",
            managedServerPodName, domainInImageNamespace);
        checkPodReady(managedServerPodName, domainUid, domainInImageNamespace);
      }
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName, String domImage) {
    // construct the cluster list used for domain custom resource
    List<Cluster> clusterList = new ArrayList<>();
    for (int i = NUMBER_OF_CLUSTERS; i >= 1; i--) {
      clusterList.add(new Cluster()
          .clusterName(CLUSTER_NAME_PREFIX + i)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainInImageNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("Image")
            .image(domImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(OCIR_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainInImageNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .clusters(clusterList)
            .configuration(new Configuration()
                .istio(new Istio()
                   .enabled(Boolean.TRUE)
                   .readinessPort(8888)
                   .localhostBindingsEnabled(isLocalHostBindingsEnabled()))
                .model(new Model()
                    .domainType("WLS"))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainInImageNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainInImageNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domainInImageNamespace));
  }

  private boolean coherenceCacheTest(int ingressServiceNodePort) {
    String hostAndPort = K8S_NODEPORT_HOST + ":" + ingressServiceNodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);

    // add the data to cache
    String[] firstNameList = {"Frodo", "Samwise", "Bilbo", "peregrin", "Meriadoc", "Gandalf"};
    String[] secondNameList = {"Baggins", "Gamgee", "Baggins", "Took", "Brandybuck", "TheGrey"};
    ExecResult result = null;
    for (int i = 0; i < firstNameList.length; i++) {
      result = addDataToCache(firstNameList[i], secondNameList[i], hostAndPort);
      logger.info("Data added to the cache " + result.stdout());
      assertTrue(result.stdout().contains(firstNameList[i]), "Did not add the expected record");
    }

    // check if cache size is 6
    result = getCacheSize(hostAndPort);
    logger.info("number of records in cache = " + result.stdout());
    if (!(result.stdout().equals("6"))) {
      logger.info("number of records in cache = " + result.stdout());
      assertTrue("6".equals(result.stdout()), "Expected 6 records");
    }

    // get the data from cache
    result = getCacheContents(hostAndPort);
    logger.info("Cache contains the following entries \n" + result.stdout());

    // Now clear the cache
    result = clearCache(hostAndPort);
    logger.info("Cache is cleared and should be empty" + result.stdout());
    if (!(result.stdout().trim().equals("0"))) {
      logger.info("number of records in cache = " + result.stdout());
      assertFalse("0".equals(result.stdout()), "Expected 0 records");
    }
    return true;
  }

  private ExecResult addDataToCache(String firstName,
                                    String secondName,
                                    String hostAndPort) {
    logger.info("Add initial data to cache");
    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=add&first=")
        .append(firstName)
        .append("&second=")
        .append(secondName)
        .append("' ")
        .append("http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to add initial data to cache {0} ", curlCmd.toString());
    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(curlCmd.toString(), true),
        String.format("Failed to add initial data to cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to add initial data to cache. Error is %s ", result.stderr()));
    return result;
  }

  private ExecResult getCacheSize(String hostAndPort) {
    logger.info("Get the number of records in cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=size' ")
        .append(" http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to get the number of records in cache " + curlCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(curlCmd.toString(), true),
        String.format("Failed to get the number of records in cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to get the number of records in cache. Error is %s ", result.stderr()));

    return result;
  }

  private ExecResult getCacheContents(String hostAndPort) {
    logger.info("Get the records from cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=get' ")
        .append(" http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to get the records from cache " + curlCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(curlCmd.toString(), true),
        String.format("Failed to get the records from cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to get the records from cache. Error is %s ", result.stderr()));

    return result;
  }

  private ExecResult clearCache(String hostAndPort) {
    logger.info("Clean the cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy '*' ");
    curlCmd
        .append("-d 'action=clear' ")
        .append(" http://")
        .append(hostAndPort)
        .append("/")
        .append(COHERENCE_APP_NAME)
        .append("/")
        .append(COHERENCE_APP_NAME);
    logger.info("Command to clean the cache " + curlCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(curlCmd.toString(), true),
        String.format("Failed to clean the cache by running command %s", curlCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to clean the cache. Error is %s ", result.stderr()));

    return result;
  }

}
