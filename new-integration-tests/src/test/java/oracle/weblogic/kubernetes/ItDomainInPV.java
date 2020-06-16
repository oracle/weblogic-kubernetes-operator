// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1SecurityContext;
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
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import oracle.weblogic.kubernetes.utils.DeployUtil;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
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
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getContainerRestartCount;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests to create domain in persistent volume using WLST and WDT.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the WebLogic server pods can run with domain created in persistent volume")
@IntegrationTest
public class ItDomainInPV implements LoggedTest {

  private static String opNamespace = null;
  private static String wlstDomainNamespace = null;
  private static String wdtDomainNamespace = null;
  private final String wdtDomainUid = "wdtdomain-inpv";
  private final String wdtManagedServerNameBase = "wdt-ms-";
  private static String nginxNamespace = null;
  private static int nodeportshttp;
  private static HelmParams nginxHelmParams = null;

  private static String image = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;
  private static boolean isUseSecret = true;

  private final String wlSecretName = "weblogic-credentials";
  private boolean previousTestSuccessful = false;
  private String wdtCurlRequest = null;
  private List<String> wdtDomainManagedServers = new ArrayList<>();

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for WLST WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    wlstDomainNamespace = namespaces.get(1);
    logger.info("Assign a unique namespace for WDT WebLogic domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    wdtDomainNamespace = namespaces.get(2);
    logger.info("Assign a unique namespace for NGINX");
    assertNotNull(namespaces.get(3), "Namespace is null");
    nginxNamespace = namespaces.get(3);


    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, wdtDomainNamespace, wlstDomainNamespace);

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
    }
  }

  /**
   * Create a WebLogic domain using WDT in a persistent volume.
   * Create a domain custom resource with domainHomeSourceType as PersistentVolume.
   * Verify domain pods runs in ready state and services are created.
   * Verify login to WebLogic console is successful.
   */
  @Test
  @Order(1)
  @DisplayName("Create WebLogic domain in PV using WDT")
  public void testDomainInPvUsingWdt() {
    previousTestSuccessful = false;
    final String clusterName = "cluster-wdtdomain-inpv";
    final String adminServerName = "wdt-admin-server";
    final String adminServerPodName = wdtDomainUid + "-" + adminServerName;
    final int managedServerPort = 8001;
    String managedServerPodNamePrefix = wdtDomainUid + "-" + wdtManagedServerNameBase;
    final int replicaCount = 2;
    final int t3ChannelPort = getNextFreePort(31000, 32767);  // the port range has to be between 30,000 to 32,767

    final String pvName = wdtDomainUid + "-pv"; // name of the persistent volume
    final String pvcName = wdtDomainUid + "-pvc"; // name of the persistent volume claim

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    if (isUseSecret) {
      createOCRRepoSecret(wdtDomainNamespace);
    }

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, wdtDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, wdtDomainUid);
    createPVC(pvName, pvcName, wdtDomainUid, wdtDomainNamespace);

    // create a temporary WebLogic domain property file as a input for WDT model file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", wdtDomainUid);
    p.setProperty("adminServerName", adminServerName);
    p.setProperty("productionModeEnabled", "true");
    p.setProperty("clusterName", clusterName);
    p.setProperty("configuredManagedServerCount", "4");
    p.setProperty("managedServerNameBase", wdtManagedServerNameBase);
    p.setProperty("t3ChannelPort", Integer.toString(t3ChannelPort));
    p.setProperty("t3PublicAddress", K8S_NODEPORT_HOST);
    p.setProperty("managedServerPort", "8001");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    // shell script to download WDT and run the WDT createDomain script
    Path wdtScript = Paths.get(RESOURCE_DIR, "bash-scripts", "wdt-create-domain-onpv.sh");
    // WDT model file containing WebLogic domain configuration
    Path wdtModelFile = Paths.get(RESOURCE_DIR, "wdt-models", "domain-onpv-wdt-model.yaml");

    // create configmap and domain on persistent volume using WDT
    createDomainOnPVUsingWdt(wdtScript, wdtModelFile, domainPropertiesFile.toPath(),
        wdtDomainUid, pvName, pvcName, wdtDomainNamespace);

    // create the domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta() //metadata
            .name(wdtDomainUid)
            .namespace(wdtDomainNamespace))
        .spec(new DomainSpec() //spec
            .domainUid(wdtDomainUid)
            .domainHome("/shared/domains/" + wdtDomainUid)  // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(image)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(isUseSecret ? Arrays.asList(
                new V1LocalObjectReference()
                    .name(OCR_SECRET_NAME))
                : null)
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(wdtDomainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + wdtDomainUid)
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
    createDomainAndVerify(domain, wdtDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, wdtDomainUid, wdtDomainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, wdtDomainNamespace);

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, wdtDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, wdtDomainUid, wdtDomainNamespace);
    }

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, wdtDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, wdtDomainNamespace);
    }

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        wdtDomainNamespace, adminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() -> {
      return adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }, "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");

    logger.info("Getting node port for T3 channel");
    final int t3channelNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        wdtDomainNamespace, adminServerPodName + "-external", "t3channel"),
        "Getting admin server t3channel node port failed");
    assertNotEquals(-1, t3ChannelPort, "admin server t3channelport is not valid");

    //create ingress controller
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    logger.info("Creating ingress for domain {0} in namespace {1}", wdtDomainUid, wdtDomainNamespace);
    createIngressForDomainAndVerify(wdtDomainUid, wdtDomainNamespace, clusterNameMsPortMap);

    //deploy application
    Path archivePath = Paths.get(ITTESTS_DIR, "../src/integration-tests/apps/testwebapp.war");
    logger.info("Deploying webapp to domain {0}", archivePath);
    DeployUtil.deployUsingWlst(K8S_NODEPORT_HOST, Integer.toString(t3channelNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + adminServerName, archivePath,
        wdtDomainNamespace);

    //access application from admin server
    String url = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort + "/testwebapp/index.jsp";
    assertEquals(200,
        assertDoesNotThrow(() -> OracleHttpClient.get(url, true),
            "Accessing sample application on admin server failed")
            .statusCode(), "Status code not equals to 200");

    //access application in managed servers through NGINX load balancer
    logger.info("Accessing the sample app through NGINX load balancer");
    wdtCurlRequest = String.format("curl --silent --show-error --noproxy '*' "
            + "-H 'host: %s' http://%s:%s/testwebapp/index.jsp",
        wdtDomainUid + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
    for (int i = 1; i <= replicaCount; i++) {
      wdtDomainManagedServers.add(wdtDomainUid + "-" + wdtManagedServerNameBase + i);
    }
    assertThat(callWebAppAndCheckForServerNameInResponse(wdtCurlRequest, wdtDomainManagedServers, 20))
        .as("Verify NGINX can access the test web app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the test web app from one or more of the managed servers")
        .isTrue();
    previousTestSuccessful = true;
  }

  /**
   * Verify liveness probe by killing managed server process 3 times to kick
   * pod container auto-restart.
   */
  @Test
  @Order(2)
  @DisplayName("Test liveness probe of Pod")
  public void testLivenessProbe() {
    Assumptions.assumeTrue(previousTestSuccessful);
    String serverName = wdtDomainUid + "-" + wdtManagedServerNameBase + "1";
    // create file to kill server process
    File killServerScript = assertDoesNotThrow(() ->
            createScriptToKillServer(),
        "Failed to create script to kill server");
    logger.info("File/script created to kill server {0}", killServerScript);

    checkPodReady(serverName, wdtDomainUid, wdtDomainNamespace);

    // copy script to pod
    String destLocation = "/u01/killserver.sh";
    assertDoesNotThrow(() -> copyFileToPod(wdtDomainNamespace, serverName, "weblogic-server",
        killServerScript.toPath(), Paths.get(destLocation)),
        String.format("Failed to copy file %s to pod %s in namespace %s",
            killServerScript, serverName, wdtDomainNamespace));
    logger.info("File copied to Pod {0} in namespace {1}", serverName, wdtDomainNamespace);

    // get the restart count of the container in pod before liveness probe restarts
    final int beforeRestartCount =
        assertDoesNotThrow(() -> getContainerRestartCount(wdtDomainNamespace, null, serverName, null),
            String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
                serverName, wdtDomainNamespace));
    logger.info("Restart count before liveness probe {0}", beforeRestartCount);
    // change file permissions
    ExecResult execResult = assertDoesNotThrow(() -> execCommand(wdtDomainNamespace, serverName, null,
        true, "/bin/sh", "-c", "chmod +x " + destLocation),
        String.format("Failed to change permissions for file %s in pod %s", destLocation, serverName));
    assertTrue(execResult.exitValue() == 0,
        String.format("Failed to change file %s permissions, stderr %s stdout %s", destLocation,
            execResult.stderr(), execResult.stdout()));
    logger.info("File permissions changed inside pod");
    /* First, kill the mgd server process in the container three times to cause the node manager to
     * mark the server 'failed not restartable'. This in turn is detected by the liveness probe, which
     * initiates a container restart.
     */
    for (int i = 0; i < 3; i++) {
      execResult = assertDoesNotThrow(() -> execCommand(wdtDomainNamespace, serverName, null,
          true, "/bin/sh", "-c", destLocation + " " + serverName),
          String.format("Failed to execute script %s in pod %s namespace %s", destLocation,
              serverName, wdtDomainNamespace));
      logger.info("Command executed to kill server inside pod, exit value {0}, stdout {1}, stderr {2}",
          execResult.exitValue(), execResult.stdout(), execResult.stderr());

      try {
        Thread.sleep(2 * 1000);
      } catch (InterruptedException ie) {
        // ignore
      }
    }
    // check pod is ready
    checkPodReady(serverName, wdtDomainUid, wdtDomainNamespace);

    // get the restart count of the container in pod after liveness probe restarts
    int afterRestartCount = assertDoesNotThrow(() ->
            getContainerRestartCount(wdtDomainNamespace, null, serverName, null),
        String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
            serverName, wdtDomainNamespace));
    assertTrue(afterRestartCount - beforeRestartCount == 1,
        String.format("Liveness probe did not start the container in pod {0} in namespace {1}",
            serverName, wdtDomainNamespace));

    //access application in managed servers through NGINX load balancer
    assertThat(callWebAppAndCheckForServerNameInResponse(wdtCurlRequest, wdtDomainManagedServers, 20))
        .as("Verify NGINX can access the test web app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the test web app from one or more of the managed servers")
        .isTrue();

  }

  /**
   * Create a WebLogic domain using WLST in a persistent volume.
   * Create a domain custom resource with domainHomeSourceType as PersistentVolume.
   * Verify domain pods runs in ready state and services are created.
   * Verify login to WebLogic console is successful.
   */
  @Test
  @DisplayName("Create WebLogic domain in PV using WLST script")
  public void testDomainInPvUsingWlst() {

    final String domainUid = "wlstdomain-inpv";
    final String clusterName = "cluster-wlstdomain-inpv";
    final String adminServerName = "wlst-admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String managedServerNameBase = "wlst-ms-";
    final int managedServerPort = 8001;
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int replicaCount = 2;
    final int t3ChannelPort = getNextFreePort(30000, 32767);  // the port range has to be between 30,000 to 32,767

    final String pvName = domainUid + "-pv"; // name of the persistent volume
    final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    if (isUseSecret) {
      createOCRRepoSecret(wlstDomainNamespace);
    }

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, wlstDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid);
    createPVC(pvName, pvcName, domainUid, wlstDomainNamespace);

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
    p.setProperty("number_of_ms", "4");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "wlst properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, wlstDomainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(wlstDomainNamespace))
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
                .namespace(wlstDomainNamespace))
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
    createDomainAndVerify(domain, wlstDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, wlstDomainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, wlstDomainNamespace);

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, wlstDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, wlstDomainNamespace);
    }

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, wlstDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, wlstDomainNamespace);
    }

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        wlstDomainNamespace, adminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() -> {
      return adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }, "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");

    logger.info("Getting node port for T3 channel");
    final int t3channelNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        wlstDomainNamespace, adminServerPodName + "-external", "t3channel"),
        "Getting admin server t3channel node port failed");
    assertNotEquals(-1, t3ChannelPort, "admin server t3channelport is not valid");

    //create ingress controller
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, wlstDomainNamespace);
    createIngressForDomainAndVerify(domainUid, wlstDomainNamespace, clusterNameMsPortMap);

    //deploy application
    Path archivePath = Paths.get(ITTESTS_DIR, "../src/integration-tests/apps/testwebapp.war");
    logger.info("Deploying webapp to domain {0}", archivePath);
    DeployUtil.deployUsingWlst(K8S_NODEPORT_HOST, Integer.toString(t3channelNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + adminServerName, archivePath,
        wlstDomainNamespace);

    //access application from admin server
    String url = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort + "/testwebapp/index.jsp";
    assertEquals(200,
        assertDoesNotThrow(() -> OracleHttpClient.get(url, true),
            "Accessing sample application on admin server failed")
            .statusCode(), "Status code not equals to 200");

    //access application in managed servers through NGINX load balancer
    logger.info("Accessing the sample app through NGINX load balancer");
    String curlRequest = String.format("curl --silent --show-error --noproxy '*' "
            + "-H 'host: %s' http://%s:%s/testwebapp/index.jsp",
        domainUid + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
    List<String> managedServers = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServers.add(domainUid + "-" + managedServerNameBase + i);
    }
    assertThat(callWebAppAndCheckForServerNameInResponse(curlRequest, managedServers, 20))
        .as("Verify NGINX can access the test web app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the test web app from one or more of the managed servers")
        .isTrue();
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
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles, namespace),
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
    createDomainJob(pvName, pvcName, domainScriptConfigMapName, namespace, jobCreationContainer);

  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following.
   * Create a configmap containing WDT model file, property file and shell script to download and run WDT.
   * Create a Kubernetes job to create domain on persistent volume.
   *
   * @param domainCreationScriptFile path of the shell script to download and run WDT
   * @param modelFile path of the WDT model file
   * @param domainPropertiesFile property file holding properties referenced in WDT model file
   * @param domainUid unique id of the WebLogic domain
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   */
  private void createDomainOnPVUsingWdt(Path domainCreationScriptFile, Path modelFile,
                                        Path domainPropertiesFile, String domainUid,
                                        String pvName, String pvcName, String namespace) {
    logger.info("Preparing to run create domain job using WDT");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(domainCreationScriptFile);
    domainScriptFiles.add(domainPropertiesFile);
    domainScriptFiles.add(modelFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles, namespace),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/weblogic/" + domainCreationScriptFile.getFileName()) //shell script to run WDT
        .addEnvItem(new V1EnvVar()
            .name("WDT_VERSION")
            .value(WDT_VERSION)) // WDT version to use
        .addEnvItem(new V1EnvVar()
            .name("WDT_MODEL_FILE")
            .value("/u01/weblogic/" + modelFile.getFileName())) // WDT model file
        .addEnvItem(new V1EnvVar()
            .name("WDT_VAR_FILE")
            .value("/u01/weblogic/" + domainPropertiesFile.getFileName())) // WDT model property file
        .addEnvItem(new V1EnvVar()
            .name("DOMAIN_HOME_DIR")
            .value("/shared/domains/" + domainUid)); // domain location

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(pvName, pvcName, domainScriptConfigMapName, namespace, jobCreationContainer);

  }

  /**
   * Create configmap containing domain creation scripts.
   *
   * @param configMapName name of the configmap to create
   * @param files files to add in configmap
   * @param namespace name of the namespace in which to create configmap
   * @throws IOException when reading the domain script files fail
   * @throws ApiException if create configmap fails
   */
  private void createConfigMapForDomainCreation(String configMapName, List<Path> files, String namespace)
      throws ApiException, IOException {
    logger.info("Creating configmap {0}", configMapName);

    Path domainScriptsDir = Files.createDirectories(
        Paths.get(TestConstants.LOGS_DIR, this.getClass().getSimpleName(), namespace));

    // add domain creation scripts and properties files to the configmap
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      logger.info("Adding file {0} in configmap", file);
      data.put(file.getFileName().toString(), Files.readString(file));
      logger.info("Making a copy of file {0} to {1} for diagnostic purposes", file,
          domainScriptsDir.resolve(file.getFileName()));
      Files.copy(file, domainScriptsDir.resolve(file.getFileName()));
    }
    V1ObjectMeta meta = new V1ObjectMeta()
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Failed to create configmap %s with files %s", configMapName, files));
    assertTrue(cmCreated, String.format("Failed while creating ConfigMap %s", configMapName));
  }

  /**
   * Create a job to create a domain in persistent volume.
   *
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param domainScriptCM configmap holding domain creation script files
   * @param namespace name of the domain namespace in which the job is created
   * @param jobContainer V1Container with job commands to create domain
   */
  private void createDomainJob(String pvName,
                               String pvcName, String domainScriptCM, String namespace, V1Container jobContainer) {
    logger.info("Running Kubernetes job to create domain");

    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job-" + pvName) // name of the create domain job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .initContainers(Arrays.asList(new V1Container()
                        .name("fix-pvc-owner") // change the ownership of the pv to opc:opc
                        .image(image)
                        .addCommandItem("/bin/sh")
                        .addArgsItem("-c")
                        .addArgsItem("chown -R 1000:1000 /shared")
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name(pvName)
                                .mountPath("/shared")))
                        .securityContext(new V1SecurityContext()
                            .runAsGroup(0L)
                            .runAsUser(0L))))
                    .containers(Arrays.asList(jobContainer  // container containing WLST or WDT details
                        .name("create-weblogic-domain-onpv-container")
                        .image(image)
                        .imagePullPolicy("Always")
                        .ports(Arrays.asList(new V1ContainerPort()
                            .containerPort(7001)))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                .mountPath("/u01/weblogic"), // availble under /u01/weblogic inside pod
                            new V1VolumeMount()
                                .name(pvName) // location to write domain
                                .mountPath("/shared"))))) // mounted under /shared inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName)),
                        new V1Volume()
                            .name("create-weblogic-domain-job-cm-volume")
                            .configMap(
                                new V1ConfigMapVolumeSource()
                                    .name(domainScriptCM)))) //config map containing domain scripts
                    .imagePullSecrets(isUseSecret ? Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null))));
    String jobName = assertDoesNotThrow(()
        -> createNamespacedJob(jobBody), "Failed to create Job");

    logger.info("Checking if the domain creation job {0} completed in namespace {1}",
        jobName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for job {0} to be completed in namespace {1} "
                    + "(elapsed time {2} ms, remaining time {3} ms)",
                jobName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(jobCompleted(jobName, null, namespace));

    // check job status and fail test if the job failed to create domain
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to create domain", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty()) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          logger.severe(podLog);
          fail("Domain create job failed");
        }
      }
    }

  }

  /**
   * Create a persistent volume.
   *
   * @param pvName name of the persistent volume to create
   * @param domainUid domain UID
   * @throws IOException when creating pv path fails
   */
  private void createPV(String pvName, String domainUid) {
    logger.info("creating persistent volume");

    Path pvHostPath = null;
    try {
      pvHostPath = Files.createDirectories(Paths.get(
          PV_ROOT, this.getClass().getSimpleName(), pvName));
      logger.info("Creating PV directory host path {0}", pvHostPath);
      FileUtils.deleteDirectory(pvHostPath.toFile());
      Files.createDirectories(pvHostPath);
    } catch (IOException ioex) {
      logger.severe(ioex.getMessage());
      fail("Create persistent volume host path failed");
    }

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("weblogic-domain-storage-class")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("5Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .accessModes(Arrays.asList("ReadWriteMany"))
            .hostPath(new V1HostPathVolumeSource()
                .path(pvHostPath.toString())))
        .metadata(new V1ObjectMeta()
            .name(pvName)
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));
    boolean success = assertDoesNotThrow(() -> createPersistentVolume(v1pv),
        "Failed to create persistent volume");
    assertTrue(success, "PersistentVolume creation failed");
  }

  /**
   * Create a persistent volume claim.
   *
   * @param pvName name of the persistent volume
   * @param pvcName name of the persistent volume to create
   * @param domainUid UID of the WebLogic domain
   * @param namespace name of the namespace in which to create the persistent volume claim
   */
  private void createPVC(String pvName, String pvcName, String domainUid, String namespace) {
    logger.info("creating persistent volume claim");

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("weblogic-domain-storage-class")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("5Gi"))))
        .metadata(new V1ObjectMeta()
            .name(pvcName)
            .namespace(namespace)
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    boolean success = assertDoesNotThrow(() -> createPersistentVolumeClaim(v1pvc),
        "Failed to create persistent volume claim");
    assertTrue(success, "PersistentVolumeClaim creation failed");
  }

  /**
   * Create secret for docker credentials.
   *
   * @param namespace name of the namespace in which to create secret
   */
  private void createOCRRepoSecret(String namespace) {
    CommonTestUtils.createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD,
        OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, namespace);
  }

  private File createScriptToKillServer() throws IOException {
    File killServerScript = File.createTempFile("killserver", ".sh");
    //deletes the file when VM terminates
    killServerScript.deleteOnExit();
    FileWriter fw = new FileWriter(killServerScript);
    fw.write("#!/bin/bash\n");
    fw.write("kill -9 `jps | grep Server | awk '{print $1}'`");
    fw.close();
    killServerScript.setExecutable(true, false);
    return killServerScript;
  }

}
