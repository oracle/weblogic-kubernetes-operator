// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.DeployUtil;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTraefikIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.TestUtils.verifyClusterMemberCommunication;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to introspectVersion attribute.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the introspectVersion runs the introspector")
@IntegrationTest
public class ItTraefikLoadBalancer {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainNamespace2 = null;

  private static String traefikNamespace = null;
  private static int nodeportshttp;
  private static HelmParams traefikHelmParams = null;

  private static String image = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;
  private static boolean isUseSecret = true;

  private final String wlSecretName = "weblogic-credentials";

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  private static Path clusterViewAppPath;
  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains. Pull WebLogic image if running tests in Kind cluster. Installs
   * operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain domain1");
    domainNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for Traefik");
    traefikNamespace = namespaces.get(2);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);

    // get a free web and websecure ports for Traefik
    nodeportshttp = getNextFreePort(30380, 30405);
    int nodeportshttps = getNextFreePort(31443, 31743);

    // install and verify Traefik
    traefikHelmParams = installAndVerifyTraefik(traefikNamespace, nodeportshttp, nodeportshttps);

    //determine if the tests are running in Kind cluster. if true use images from Kind registry
    if (KIND_REPO != null) {
      String kindRepoImage = KIND_REPO + image.substring(TestConstants.OCR_REGISTRY.length() + 1);
      logger.info("Using image {0}", kindRepoImage);
      image = kindRepoImage;
      isUseSecret = false;
    } else {
      // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
      createOCRRepoSecret(domainNamespace);
    }

    // build the clusterview application
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", domainNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testTraefikLoadbalancer() {

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    for (int n = 1; n <= 2; n++) {

      String domainUid = "domain" + n;
      // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
      String adminServerPodName = domainUid + "-admin-server";
      String managedServerPrefix = domainUid + "-managed-server";
      int replicaCount = 2;
      String managedServerNameBase = "managed-server";

      // create the domain object
      Domain domain = createDomainResource(domainUid,
          domainNamespace,
          REPO_SECRET_NAME,
          encryptionSecretName,
          replicaCount,
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);

      // create model in image domain
      logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
          domainUid, domainNamespace, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
      createDomainAndVerify(domain, domainNamespace);

      // check admin server pod is ready
      logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
          adminServerPodName, domainNamespace);
      checkPodReady(adminServerPodName, domainUid, domainNamespace);

      // check managed server pods are ready
      for (int i = 1; i <= replicaCount; i++) {
        logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
            managedServerPrefix + i, domainNamespace);
        checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
      }

      logger.info("Check admin service {0} is created in namespace {1}",
          adminServerPodName, domainNamespace);
      checkServiceExists(adminServerPodName, domainNamespace);

      // check managed server services created
      for (int i = 1; i <= replicaCount; i++) {
        logger.info("Check managed server service {0} is created in namespace {1}",
            managedServerPrefix + i, domainNamespace);
        checkServiceExists(managedServerPrefix + i, domainNamespace);
      }

      //create ingress resource - rules for loadbalancing
      Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
      clusterNameMsPortMap.put("cluster-1", 8001);
      logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
      createTraefikIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap, true);

      logger.info("Getting node port for default channel");
      int serviceNodePort = assertDoesNotThrow(()
          -> getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default"),
          "Getting admin server node port failed");

      ExecResult result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST,
          String.valueOf(serviceNodePort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
          "cluster-1", clusterViewAppPath, null, "clusterview");
      assertNotNull(result, "Application deployment failed");
      logger.info("Application deployment returned {0}", result.toString());
      assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

      //access application in managed servers through NGINX load balancer
      logger.info("Accessing the clusterview app through traefik load balancer");
      String curlRequest = String.format("curl --silent --show-error --noproxy '*' "
          + "-H 'host: %s' http://%s:%s/clusterview/ClusterViewServlet",
          domainUid + "." + domainNamespace + "." + "cluster-1" + ".test", K8S_NODEPORT_HOST, nodeportshttp);
      List<String> managedServers = new ArrayList<>();
      for (int i = 1; i <= replicaCount; i++) {
        managedServers.add(managedServerNameBase + i);
      }
      assertThat(verifyClusterMemberCommunication(curlRequest, managedServers, 20))
          .as("Verify all managed servers can see each other")
          .withFailMessage("managed servers cannot see other")
          .isTrue();
    }
  }

  private Domain createDomainResource(String domainUid, String domNamespace,
      String repoSecretName, String encryptionSecretName, int replicaCount,
      String miiImage) {
    // create the domain CR
    return new Domain()
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
                .name(wlSecretName)
                .namespace(domNamespace))
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
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

  }

  /**
   * Test clusters from 2 domains can be load balanced by a single Traefik loadbalancer. Test Creates 2 domains in
   * persistent volume using WLST. Verifies that the new pod comes up and sample application deployment works when
   * accessed through the traefik LB
   */
  @Order(1)
  @Test
  @DisplayName("Test loadbalancing of 2 clusters in 2 domains using traefik loadbalancer")
  public void testTraefikLoadbalancing() {

    final String domainUid1 = "mydomain";
    final String clusterName = "mycluster";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid1 + "-" + adminServerName;

    final String managedServerNameBase = "ms-";
    String managedServerPodNamePrefix = domainUid1 + "-" + managedServerNameBase;
    final int managedServerPort = 8001;

    int replicaCount = 2;

    // in general the node port range has to be between 30,000 to 32,767
    // to avoid port conflict because of the delay in using it, the port here
    // starts with 30100
    final int t3ChannelPort = getNextFreePort(30100, 32767);

    final String pvName = domainUid1 + "-pv"; // name of the persistent volume
    final String pvcName = domainUid1 + "-pvc"; // name of the persistent volume claim

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid1, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid1, domainNamespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid1);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", Integer.toString(managedServerPort));
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "2");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(()
        -> p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, domainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid1)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid1)
            .domainHome("/shared/domains/" + domainUid1) // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(image)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(isUseSecret ? Arrays.asList(
                new V1LocalObjectReference()
                    .name(OCR_SECRET_NAME))
                : null)
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid1)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));

    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid1, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid1, domainNamespace);
    }
    //create ingress controller
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    logger.info("Creating ingress for domain {0} in namespace {1}", domainUid1, domainNamespace);
    createTraefikIngressForDomainAndVerify(domainUid1, domainNamespace, 0, clusterNameMsPortMap, true);

    // deploy application and verify all servers functions normally
    logger.info("Getting node port for T3 channel");
    int t3channelNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, adminServerPodName + "-external", "t3channel"),
        "Getting admin server t3channel node port failed");
    assertNotEquals(-1, t3ChannelPort, "admin server t3channelport is not valid");

    //deploy application
    Path archivePath = Paths.get(ITTESTS_DIR, "../src/integration-tests/apps/testwebapp.war");
    logger.info("Deploying webapp to domain {0}", archivePath);
    deployUsingWlst(K8S_NODEPORT_HOST, Integer.toString(t3channelNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + adminServerName, archivePath,
        domainNamespace);

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    //access application from admin server
    String url = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort + "/testwebapp/index.jsp";
    assertEquals(200,
        assertDoesNotThrow(() -> OracleHttpClient.get(url, true),
            "Accessing sample application on admin server failed")
            .statusCode(), "Status code not equals to 200");

    //deploy clusterview application
    logger.info("Deploying clusterview app {0} to cluster {1}",
        clusterViewAppPath, clusterName);
    deployUsingWlst(K8S_NODEPORT_HOST, Integer.toString(t3channelNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, adminServerName + "," + clusterName, clusterViewAppPath,
        domainNamespace);

    logger.info("Getting the list of servers using the listServers");
    String baseUri = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort + "/clusterview/";
    String serverListUri = "ClusterViewServlet?listServers=true";
    for (int i = 0; i < 5; i++) {
      assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(30));
      HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(baseUri + serverListUri, true));
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
    }

    //access application in managed servers through NGINX load balancer
    logger.info("Accessing the clusterview app through NGINX load balancer");
    String curlRequest = String.format("curl --silent --show-error --noproxy '*' "
        + "-H 'host: %s' http://%s:%s/clusterview/ClusterViewServlet",
        domainUid1 + "." + domainNamespace + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
    List<String> managedServers = new ArrayList<>();
    for (int i = 1; i <= replicaCount + 1; i++) {
      managedServers.add(managedServerNameBase + i);
    }
    assertThat(verifyClusterMemberCommunication(curlRequest, managedServers, 20))
        .as("Verify all managed servers can see each other")
        .withFailMessage("managed servers cannot see other")
        .isTrue();

  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following. Create a configmap containing WLST script
   * and property file. Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile python script to create domain
   * @param domainPropertiesFile properties file containing domain configuration
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   */
  private void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
      String pvName, String pvcName, String namespace) {
    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(
            domainScriptConfigMapName, domainScriptFiles, namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(image, isUseSecret, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer);

  }

  /**
   * Create secret for docker credentials.
   *
   * @param namespace name of the namespace in which to create secret
   */
  private static void createOCRRepoSecret(String namespace) {
    boolean secretExists = false;
    V1SecretList listSecrets = listSecrets(namespace);
    if (null != listSecrets) {
      for (V1Secret item : listSecrets.getItems()) {
        if (item.getMetadata().getName().equals(OCR_SECRET_NAME)) {
          secretExists = true;
          break;
        }
      }
    }
    if (!secretExists) {
      createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD,
          OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, namespace);
    }
  }

  /**
   * Uninstall Traefik. The cleanup framework does not uninstall Traefik release. Do it here for now.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall Traefik loadbalancer
    if (traefikHelmParams != null) {
      assertThat(uninstallTraefik(traefikHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

}
