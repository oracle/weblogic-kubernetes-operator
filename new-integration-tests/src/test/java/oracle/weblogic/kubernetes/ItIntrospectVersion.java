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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.custom.V1Patch;
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
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.awaitility.core.ConditionEvaluationListener;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.EvaluatedCondition;
import org.joda.time.DateTime;
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
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.TestUtils.verifyClusterMemberCommunication;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
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
public class ItIntrospectVersion {

  private static String opNamespace = null;
  private static String introDomainNamespace = null;

  private static String nginxNamespace = null;
  private static int nodeportshttp;
  private static HelmParams nginxHelmParams = null;

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
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for Introspect Version WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    introDomainNamespace = namespaces.get(1);
    logger.info("Assign a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace is null");
    nginxNamespace = namespaces.get(2);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, introDomainNamespace);

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    int nodeportshttps = getNextFreePort(30443, 30543);

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps);

    //determine if the tests are running in Kind cluster. if true use images from Kind registry
    if (KIND_REPO != null) {
      String kindRepoImage = KIND_REPO + image.substring(TestConstants.OCR_REGISTRY.length() + 1);
      logger.info("Using image {0}", kindRepoImage);
      image = kindRepoImage;
      isUseSecret = false;
    } else {
      // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
      createOCRRepoSecret(introDomainNamespace);
    }

    // build the clusterview application
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", introDomainNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

  }


  /**
   * Test domain status gets updated when introspectVersion attribute is added under domain.spec.
   * Test Creates a domain in persistent volume using WLST.
   * Updates the cluster configuration; cluster size using online WLST.
   * Patches the domain custom resource with introSpectVersion.
   * Verifies the introspector runs and the cluster maximum replica is updated
   * under domain status.
   * Verifies that the new pod comes up and sample application deployment works.
   */
  @Order(1)
  @Test
  @DisplayName("Test introSpectVersion starting a introspector and updating domain status")
  public void testDomainIntrospectVersionNotRolling() {

    final String domainUid = "mydomain";
    final String clusterName = "mycluster";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int managedServerPort = 8001;

    int replicaCount = 2;

    // in general the node port range has to be between 30,000 to 32,767
    // to avoid port conflict because of the delay in using it, the port here
    // starts with 30100
    final int t3ChannelPort = getNextFreePort(30100, 32767);

    final String pvName = domainUid + "-pv"; // name of the persistent volume
    final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, introDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, introDomainNamespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
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
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, introDomainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(introDomainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)  // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(image)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(isUseSecret ? Arrays.asList(
                new V1LocalObjectReference()
                    .name(OCR_SECRET_NAME))
                : null)
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(introDomainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
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
    createDomainAndVerify(domain, introDomainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, introDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, introDomainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    // get the pod creation time stamps
    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(introDomainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(introDomainNamespace, managedServerPodNamePrefix + i));
    }

    logger.info("change the cluster size to 3 and verify the introspector runs and updates the domain status");
    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");
    Properties p1 = new Properties();
    p1.setProperty("admin_host", K8S_NODEPORT_HOST);
    p1.setProperty("admin_port", Integer.toString(t3ChannelPort));
    p1.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p1.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p1.setProperty("cluster_name", clusterName);
    p1.setProperty("max_cluster_size", Integer.toString(3));
    p1.setProperty("test_name", "change_server_count");
    assertDoesNotThrow(() -> p1.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    // change the server count of the cluster by running online WLST
    Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), introDomainNamespace);

    // patch the domain to increase the replicas of the cluster and add introspectVersion field
    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, introDomainNamespace));
    String patchStr =
        "["
            + "{\"op\": \"replace\", \"path\": \"/spec/clusters/0/replicas\", \"value\": 3},"
            + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
            + "]";

    logger.info("Updating replicas in cluster {0} using patch string: {1}", clusterName, patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, introDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectPodName = domainUid + "-" + "introspect-domain-job";
    checkPodExists(introspectPodName, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodName, domainUid, introDomainNamespace);

    //verify the maximum cluster size is updated to expected value
    withStandardRetryPolicy.conditionEvaluationListener(new ConditionEvaluationListener() {
      @Override
      public void conditionEvaluated(EvaluatedCondition condition) {
        logger.info("Waiting for Domain.status.clusters.{0}.maximumReplicas to be {1}",
            clusterName, 3);
      }
    })
        .until((Callable<Boolean>) () -> {
              Domain res = getDomainCustomResource(domainUid, introDomainNamespace);
              return (res.getStatus().getClusters().get(0).getMaximumReplicas() == 3);
            }
        );

    // verify the 3rd server pod comes up
    checkServiceExists(managedServerPodNamePrefix + 3, introDomainNamespace);
    checkPodReady(managedServerPodNamePrefix + 3, domainUid, introDomainNamespace);

    // verify existing managed server services are not affected
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify existing managed server pods are not affected
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    // verify existing pods are not restarted
    podStateNotChanged(adminServerPodName, domainUid, introDomainNamespace, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      podStateNotChanged(managedServerPodNamePrefix + i,
          domainUid, introDomainNamespace, pods.get(i));
    }

    //create ingress controller
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, introDomainNamespace);
    createIngressForDomainAndVerify(domainUid, introDomainNamespace, clusterNameMsPortMap);

    // deploy application and verify all servers functions normally
    logger.info("Getting node port for T3 channel");
    int t3channelNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(introDomainNamespace, adminServerPodName + "-external", "t3channel"),
        "Getting admin server t3channel node port failed");
    assertNotEquals(-1, t3ChannelPort, "admin server t3channelport is not valid");

    //deploy application
    Path archivePath = Paths.get(ITTESTS_DIR, "../src/integration-tests/apps/testwebapp.war");
    logger.info("Deploying webapp to domain {0}", archivePath);
    deployUsingWlst(K8S_NODEPORT_HOST, Integer.toString(t3channelNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + adminServerName, archivePath,
        introDomainNamespace);

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(introDomainNamespace, adminServerPodName + "-external", "default"),
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
        introDomainNamespace);

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
        domainUid + "." + introDomainNamespace + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
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
   * Test server pods are rolling restarted and updated when domain is patched
   * with introSpectVersion when non dynamic changes are made.
   * Updates the admin server listen port using online WLST.
   * Patches the domain custom resource with introSpectVersion.
   * Verifies the introspector runs and pods are restated in a rolling fashion.
   * Verifies the new admin port of the admin server in services.
   * Verifies accessing sample application in admin server works.
   */
  @Order(2)
  @Test
  @DisplayName("Test introspectVersion rolling server pods when admin server port is changed")
  public void testDomainIntrospectVersionRolling() {

    final String domainUid = "mydomain";
    final String clusterName = "mycluster";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    final int replicaCount = 3;
    final int newAdminPort = 7005;

    // get the pod creation time stamps
    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(introDomainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(introDomainNamespace, managedServerPodNamePrefix + i));
    }

    logger.info("Getting node port for default channel");
    int adminServerT3Port = assertDoesNotThrow(()
        -> getServiceNodePort(introDomainNamespace, adminServerPodName + "-external", "t3channel"),
        "Getting admin server node port failed");

    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");
    Properties p = new Properties();
    p.setProperty("admin_host", K8S_NODEPORT_HOST);
    p.setProperty("admin_port", Integer.toString(adminServerT3Port));
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("new_admin_port", Integer.toString(newAdminPort));
    p.setProperty("test_name", "change_admin_port");
    assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    // change the admin server port to a different value to force pod restart
    logger.info("changing the admin server port to a different value to force pod restart");
    Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), introDomainNamespace);

    assertTrue(assertDoesNotThrow(() ->
            patchDomainResourceWithNewIntrospectVersion(domainUid, introDomainNamespace),
        "Patch domain with new IntrospectVersion threw ApiException"),
        "Failed to patch domain with new IntrospectVersion");

    //verify the introspector pod is created and runs
    String introspectPodName = domainUid + "-" + "introspect-domain-job";

    checkPodExists(introspectPodName, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodName, domainUid, introDomainNamespace);

    //verify the pods are restarted
    verifyRollingRestartOccurred(pods, 1, introDomainNamespace);
    
    // verify the admin server service created
    checkServiceExists(adminServerPodName, introDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, introDomainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    // verify the admin port is changed to newAdminPort
    assertEquals(newAdminPort, assertDoesNotThrow(()
        -> getServicePort(introDomainNamespace, adminServerPodName + "-external", "default"),
        "Getting admin server port failed"),
        "Updated admin server port is not equal to expected value");

    logger.info("Getting node port for default channel");
    int adminServerNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(introDomainNamespace, adminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    //access application from admin server to validate the new port
    String url = "http://" + K8S_NODEPORT_HOST + ":" + adminServerNodePort + "/testwebapp/index.jsp";
    assertEquals(200,
        assertDoesNotThrow(() -> OracleHttpClient.get(url, true),
            "Accessing sample application on admin server failed")
            .statusCode(), "Status code not equals to 200");

    logger.info("Getting the list of servers using the listServers");
    String baseUri = "http://" + K8S_NODEPORT_HOST + ":" + adminServerNodePort + "/clusterview/";
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
        domainUid + "." + introDomainNamespace + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
    List<String> managedServers = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServers.add(managedServerNameBase + i);
    }
    assertThat(verifyClusterMemberCommunication(curlRequest, managedServers, 20))
        .as("Verify all managed servers can see each other")
        .withFailMessage("managed servers cannot see other")
        .isTrue();

  }

  /**
   * Test brings up a new cluster and verifies it can successfully start by doing the following.
   * a. Creates new WebLogic static cluster using WLST.
   * b. Patch the Domain Resource with cluster
   * c. Update the introspectVersion version
   * d. Verifies the servers in the new WebLogic cluster comes up without affecting any of the running servers on
   * pre-existing WebLogic cluster.
   */
  @Order(3)
  @Test
  @DisplayName("Test new cluster creation on demand using WLST and introspection")
  public void testCreateNewCluster() {

    final String domainUid = "mydomain";
    final String clusterName = "cl2";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "cl2-ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    final int replicaCount = 2;

    logger.info("Getting node port for default channel");
    int adminServerT3Port = getServiceNodePort(introDomainNamespace, adminServerPodName + "-external", "t3channel");

    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");
    Properties p = new Properties();
    p.setProperty("admin_host", K8S_NODEPORT_HOST);
    p.setProperty("admin_port", Integer.toString(adminServerT3Port));
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("test_name", "create_cluster");
    p.setProperty("cluster_name", clusterName);
    p.setProperty("server_prefix", managedServerNameBase);
    p.setProperty("server_count", "3");
    assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    // changet the admin server port to a different value to force pod restart
    Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), introDomainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, introDomainNamespace));

    logger.info("patch the domain resource with new cluster and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
        + "    {\"clusterName\" : \"" + clusterName + "\", \"replicas\": 2, \"serverStartState\": \"RUNNING\"}"
        + "},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, introDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //verify the introspector pod is created and runs
    String introspectPodName = domainUid + "-" + "introspect-domain-job";

    checkPodExists(introspectPodName, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodName, domainUid, introDomainNamespace);

    // verify new cluster managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify new cluster managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    logger.info("Getting the list of servers using the listServers");
    String baseUri = "http://" + K8S_NODEPORT_HOST + ":" + adminServerT3Port + "/clusterview/";
    String serverListUri = "ClusterViewServlet?listServers=true";
    HttpResponse<String> response = null;
    for (int i = 0; i < 5; i++) {
      assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(30));
      response = assertDoesNotThrow(() -> OracleHttpClient.get(baseUri + serverListUri, true));
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking {0} health", managedServerNameBase + i);
      assertTrue(response.body().contains(managedServerNameBase + i + ":HEALTH_OK"),
          "Didn't get " + managedServerNameBase + i + ":HEALTH_OK");
    }

  }


  /**
   * Create a WebLogic domain on a persistent volume by doing the following.
   * Create a configmap containing WLST script and property file.
   * Create a Kubernetes job to create domain on persistent volume.
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
   * Uninstall Nginx.
   * The cleanup framework does not uninstall Nginx release.
   * Do it here for now.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall NGINX release
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

}