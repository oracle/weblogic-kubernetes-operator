// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

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
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.ActionConstants;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_DEPLOYMENT_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.SSL_PROPERTIES;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkWeblogicMBean;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyIntrospectorRuns;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.exeAppInServerPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTargetPortForRoute;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test verifies the enablement of ProductionSecureMode in WebLogic Operator
 * environment. Make sure all the servers in the domain comes up and WebLogic
 * readyapp is accessible thru default-admin NodePort service
 * In order to enable ProductionSecureMode in WebLogic Operator environment
 * (a) add channel called `default-admin` to domain resource
 * (b) JAVA_OPTIONS to -Dweblogic.security.SSL.ignoreHostnameVerification=true
 * (c) add ServerStartMode: secure to domainInfo section of model file
 *     Alternativley add SecurityConfiguration/SecureMode to topology section
 * (d) add a SSL Configuration to the server template
 */

@DisplayName("Test Secure NodePort service through admin port and default-admin channel in a mii domain")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-parallel")
class ItProductionSecureMode {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static int replicaCount = 1;
  private static final String domainUid = "mii-default-admin";
  private static final String configMapName = "default-admin-configmap";
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";

  private static Path pathToEnableSSLYaml;
  private static LoggingFacade logger = null;
  private static String adminSvcSslPortExtHost = null;

  /**
   * Install Operator.
   * Create domain resource.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assigning unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");

    // Add ServerStartMode section in domainInfo section 
    // instead of SecurityConfiguration in topology section
    // This will trigger a new path while WebLogic configuration
    pathToEnableSSLYaml = Paths.get(WORK_DIR + "/enablessl.yaml");
    String yamlString = "domainInfo:\n"
        + "         ServerStartMode: 'secure' \n"
        + "topology: \n"
        + "  ServerTemplate: \n"
        + "    \"cluster-1-template\": \n"
        + "       ListenPort: '7001' \n"
        + "       SSL: \n"
        + "         Enabled: true \n"
        + "         ListenPort: '7002' \n";

    assertDoesNotThrow(() -> Files.write(pathToEnableSSLYaml, yamlString.getBytes()));
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Arrays.asList(pathToEnableSSLYaml.toString()));

    // create the domain CR with a pre-defined configmap
    createDomainResource(domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        replicaCount, configMapName);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);
  }

  @AfterAll
  public static void cleanup() {
    Path dstFile = Paths.get(TestConstants.RESULTS_ROOT, "traefik/traefik-ingress-rules-tcp.yaml");
    assertDoesNotThrow(() -> {
      String command = KUBERNETES_CLI + " delete -f " + dstFile;
      logger.info("Running {0}", command);
      ExecResult result;
      try {
        result = ExecCommand.exec(command, true);
        String response = result.stdout().trim();
        logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        assertEquals(0, result.exitValue(), "Command didn't succeed");
      } catch (IOException | InterruptedException ex) {
        logger.severe(ex.getMessage());
      }
    });
  }
  
  /**
   * Verify all server pods are running.
   * Verify all k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {
    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Create a WebLogic domain with ProductionModeEnabled.
   * Create a domain resource with a channel with the name `default-admin`.
   * Verify a NodePort service is available thru default-admin channel.
   * Verify WebLogic readyapp is accessible through the `default-admin` service.
   * Verify no NodePort service is available thru default channel since
   * clear text default port (7001) is disabled.
   * Check the `default-secure` and `default-admin` port on cluster service.
   * Make sure kubectl port-forward works thru Administration port(9002)
   * Make sure kubectl port-forward does not work thru default SSL Port(7002)
   * when Administration port(9002) is enabled.
   */
  @Test
  @DisplayName("Verify the secure service through administration port")
  void testVerifyProductionSecureMode() {
    int defaultAdminPort = getServiceNodePort(
         domainNamespace, getExternalServicePodName(adminServerPodName), "default-admin");
    assertNotEquals(-1, defaultAdminPort,
          "Could not get the default-admin external service node port");
    logger.info("Found the administration service nodePort {0}", defaultAdminPort);

    // Here the SSL port is explicitly set to 7002 (on-prem default) in
    // in ServerTemplate section on topology file. Here the generated
    // config.xml has no SSL port assigned, but the default-secure service
    // must be active with port 7002
    int defaultClusterSecurePort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace,
              domainUid + "-cluster-cluster-1", "default-secure"),
              "Getting Default Secure Cluster Service port failed");
    assertEquals(7002, defaultClusterSecurePort, "Default Secure Cluster port is not set to 7002");

    int defaultAdminSecurePort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace,
              domainUid + "-cluster-cluster-1", "default-admin"),
              "Getting Default Admin Cluster Service port failed");
    assertEquals(9002, defaultAdminSecurePort, "Default Admin Cluster port is not set to 9002");

    //expose the admin server external service to access the readyapp in OKD cluster
    //set the sslPort as the target port
    adminSvcSslPortExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName),
                    domainNamespace, "admin-server-sslport-ext");
    setTlsTerminationForRoute("admin-server-sslport-ext", domainNamespace);
    setTargetPortForRoute("admin-server-sslport-ext", domainNamespace, defaultAdminSecurePort);
    String hostAndPort = getHostAndPort(adminSvcSslPortExtHost, defaultAdminPort);
    logger.info("The hostAndPort is {0}", hostAndPort);

    String resourcePath = "/weblogic/ready";
    ExecResult result = exeAppInServerPod(domainNamespace, adminServerPodName, 7002, resourcePath);
    logger.info("result in OKE_CLUSTER is {0}", result.toString());
    assertEquals(0, result.exitValue(), "Failed to access WebLogic readyapp");
    logger.info("WebLogic readyapp is accessible thru default-admin service");

    String localhost = "localhost";
    String forwardPort = startPortForwardProcess(localhost, domainNamespace, domainUid, 9002);
    assertNotNull(forwardPort, "port-forward fails to assign local port");
    logger.info("Forwarded admin-port is {0}", forwardPort);
    String curlCmd = "curl -sk --show-error --noproxy '*' "
        + " https://" + localhost + ":" + forwardPort
        + "/weblogic/ready --write-out %{http_code} "
        + " -o /dev/null";
    logger.info("Executing default-admin port-fwd curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 10));
    logger.info("WebLogic readyapp is accessible thru admin port forwarding");

    // When port-forwarding is happening on admin-port, port-forwarding will
    // not work for SSL port i.e. 7002
    forwardPort = startPortForwardProcess(localhost, domainNamespace, domainUid, 7002);
    assertNotNull(forwardPort, "port-forward fails to assign local port");
    logger.info("Forwarded ssl port is {0}", forwardPort);
    curlCmd = "curl -g -sk --show-error --noproxy '*' "
        + " https://" + localhost + ":" + forwardPort
        + "/weblogic/ready --write-out %{http_code} "
        + " -o /dev/null";
    logger.info("Executing default-admin port-fwd curl command {0}", curlCmd);
    assertFalse(callWebAppAndWaitTillReady(curlCmd, 10));
    logger.info("WebLogic readyapp should not be accessible thru ssl port forwarding");
    stopPortForwardProcess(domainNamespace);

    int nodePort = getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertEquals(-1, nodePort,
        "Default external service node port service must not be available");
    logger.info("Default service nodePort is not available as expected");
  }

  /**
   * Test dynamic update in a domain with secure mode enabled.
   * Specify SSL related environment variables in serverPod for JAVA_OPTIONS and WLSDEPLPOY_PROPERTIES
   * e.g.
   * - name:  WLSDEPLOY_PROPERTIES
   *   value: "-Dweblogic.security.SSL.ignoreHostnameVerification=true -Dweblogic.security.TrustKeyStore=DemoTrust"
   * - name:  JAVA_OPTIONS
   *    value: "-Dweblogic.security.SSL.ignoreHostnameVerification=true -Dweblogic.security.TrustKeyStore=DemoTrust"
   * Create a configmap containing both the model yaml, and a sparse model file to add
   * a new work manager, a min threads constraint, and a max threads constraint.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Verify new work manager is configured.
   * Verify the pods are not restarted.
   * Verify the introspect version is updated.
   */
  @Test
  @DisplayName("Verify MII dynamic update with SSL enabled")
  void testMiiDynamicChangeWithSSLEnabled() {

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToEnableSSLYaml.toString(), MODEL_DIR + "/model.config.wm.yaml"), withStandardRetryPolicy);

    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    verifyIntrospectorRuns(domainUid, domainNamespace);
    String sslChannelName = "default-admin";
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      createTraefikIngressRoutingRules(domainNamespace);
    }

    String resourcePath = "/management/weblogic/latest/domainRuntime/serverRuntimes/"
        + MANAGED_SERVER_NAME_BASE + "1"
        + "/applicationRuntimes/" + MII_BASIC_APP_DEPLOYMENT_NAME
        + "/workManagerRuntimes/newWM";
    if (OKE_CLUSTER || OCNE) {
      ExecResult result = exeAppInServerPod(domainNamespace, managedServerPrefix + "1",9002, resourcePath);
      logger.info("result in OKE_CLUSTER or OCNE cluster is {0}", result.toString());
      assertEquals(0, result.exitValue(), "Failed to access WebLogic rest endpoint");
    } else {
      testUntil(
          () -> checkWeblogicMBean(
              adminSvcSslPortExtHost,
              domainNamespace,
              adminServerPodName,
              "/management/weblogic/latest/domainRuntime/serverRuntimes/"
                  + MANAGED_SERVER_NAME_BASE + "1"
                  + "/applicationRuntimes/" + MII_BASIC_APP_DEPLOYMENT_NAME
                  + "/workManagerRuntimes/newWM",
              "200", true, sslChannelName),
              logger, "work manager configuration to be updated.");
    }

    logger.info("Found new work manager configuration");
    verifyPodsNotRolled(domainNamespace, pods);
    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);
  }

  private static void createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName,
      int replicaCount, String configmapName) {

    // create the domain CR
    DomainResource domain = new DomainResource()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
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
                                    .value(SSL_PROPERTIES))
                            .addEnvItem(new V1EnvVar()
                                    .name("WLSDEPLOY_PROPERTIES")
                                    .value(SSL_PROPERTIES))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("default")
                                            .nodePort(getNextFreePort()))
                                    .addChannelsItem(new Channel()
                                            .channelName("default-admin")
                                            .nodePort(getNextFreePort()))))
                    .configuration(new Configuration()
                            .model(new Model()
                                    .domainType("WLS")
                                    .configMap(configmapName)
                                    .runtimeEncryptionSecret(encryptionSecretName)
                                    .onlineUpdate(new OnlineUpdate()
                                            .enabled(true)))
                            .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }

  private static void createTraefikIngressRoutingRules(String domainNamespace) {
    logger.info("Creating ingress rules for domain traffic routing");
    Path srcFile = Paths.get(ActionConstants.RESOURCE_DIR, "traefik/traefik-ingress-rules-tcp.yaml");
    Path dstFile = Paths.get(TestConstants.RESULTS_ROOT, "traefik/traefik-ingress-rules-tcp.yaml");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", domainNamespace)
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = KUBERNETES_CLI + " create -f " + dstFile;
    logger.info("Running {0}", command);
    ExecResult result;
    try {
      result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      assertEquals(0, result.exitValue(), "Command didn't succeed");
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }
}
