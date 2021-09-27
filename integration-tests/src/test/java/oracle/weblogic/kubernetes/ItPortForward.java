// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.SSL_PROPERTIES;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.isFileExistAndNotEmpty;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify that using kubectl port-forward, users are able to access the admin console via localhost:localport.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test configurations for accessing the console via localhost:localport through 'kubectl port-forward'")
@IntegrationTest
@Tag("okdenv")
class ItPortForward {

  // constants for creating domain image using model in image
  private static final String PORTFORWARD_ADMIN_DEFAULT_MODEL_FILE = "model.portforward.yaml";
  private static final String PORTFORWARD_ADMIN_DEFAULT_IMAGE_NAME = "mii-portforward-admindefault-image";
  private static final String PORTFORWARD_ADMINISTRATION_MODEL_FILE = "model.portforward.adminport.yaml";
  private static final String PORTFORWARD_ADMINISTRATION_IMAGE_NAME = "mii-portforward-adminport-image";

  // constants for operator and WebLogic domain
  private static String adminDefaultPortDomainUid = "admindefault-port-domain1";
  private static String administrationPortDomainUid = "administration-port-domain1";
  private static String clusterName = "cluster-1";
  private static int replicaCount = 2;
  private static String opNamespace = null;
  private static String adminDefaultPortDomainNamespace = null;
  private static String administrationPortDomainNamespace = null;
  private static LoggingFacade logger = null;
  private static String portForwardFileNameProfix = RESULTS_ROOT + "/port-foward";
  private static String hostName = "localhost";

  /**
   * Install operator, create two custom images using model in image with model files
   * and create two one cluster domains, one for testing forwarding a local port to
   * admin default port(non-secure and secure port) and another one for testing
   * forwarding a local port to administration port.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace for adminDefaultPortDomain
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    adminDefaultPortDomainNamespace = namespaces.get(1);

    // get a unique domain namespace for administrationPortDomain
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    administrationPortDomainNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace,
        adminDefaultPortDomainNamespace, administrationPortDomainNamespace);

    // create and verify WebLogic domain image for adminDefaultPortDomain
    // using model in image with model files
    String imageName = createAndVerifyDomainImage(adminDefaultPortDomainNamespace,
        PORTFORWARD_ADMIN_DEFAULT_IMAGE_NAME, PORTFORWARD_ADMIN_DEFAULT_MODEL_FILE);

    // create and verify the domain is created and ready to use
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName, adminDefaultPortDomainNamespace, adminDefaultPortDomainUid);

    // create and verify WebLogic domain image for administrationPortDomain
    // using model in image with model files
    imageName = createAndVerifyDomainImage(administrationPortDomainNamespace,
        PORTFORWARD_ADMINISTRATION_IMAGE_NAME, PORTFORWARD_ADMINISTRATION_MODEL_FILE);

    // create and verify the domain is created and ready to use
    createAndVerifyDomain(imageName, administrationPortDomainNamespace, administrationPortDomainUid);
  }

  /**
   * kill port-forward process.
   */
  @AfterAll
  void tearDown() {
    stopPortForwardProcess();
  }

  /**
   * The test that `kubectl port-foward` is able to forward a local port to default channel port
   * (7001 in this test).
   * Verify that the WLS admin console can be accessed using http://localhost:localPort/console/login/LoginForm.jsp
   */
  @Order(1)
  @Test
  @DisplayName("Forward a local port to default channel port and verify WLS admin console is accessible")
  void testPortForwardDefaultAdminChannel() {
    final int adminDefaultChannelPort = 7001;

    // Verify that using a local port admin forwarded to admin's default channel (7001),
    // admin console is accessible via http://localhost:localPort/console/login/LoginForm.jsp
    String portForwardFileName = portForwardFileNameProfix + "-1.out";
    startPortForwardProcess(adminDefaultPortDomainNamespace,
        adminDefaultPortDomainUid, adminDefaultChannelPort, portForwardFileName);
    verifyAdminConsoleAccessible(adminDefaultPortDomainNamespace, portForwardFileName, false);
  }

  /**
   * The test that `kubectl port-foward` is able to forward a local port to default secure channel port
   * (7002 in this test).
   * Verify that the WLS admin console can be accessed using http://localhost:localPort/console/login/LoginForm.jsp
   */
  @Order(2)
  @Test
  @DisplayName("Forward a local port to default secure channel port and verify WLS admin console is accessible")
  void testPortForwardDefaultAdminSecureChannel() {
    final int adminDefaultChannelPort = 7002;

    // Verify that using a local port admin forwarded to admin's default secure channel (7002),
    // admin console is accessible via https://localhost:localPort/console/login/LoginForm.jsp
    String portForwardFileName = portForwardFileNameProfix + "-2.out";
    startPortForwardProcess(adminDefaultPortDomainNamespace,
        adminDefaultPortDomainUid, adminDefaultChannelPort, portForwardFileName);
    verifyAdminConsoleAccessible(adminDefaultPortDomainNamespace, portForwardFileName, true);

    // Verify that using a local port admin forwarded to admin's default channel (7001),
    // admin console is still accessible via http://localhost:localPort/console/login/LoginForm.jsp
    portForwardFileName = portForwardFileNameProfix + "-1.out";
    verifyAdminConsoleAccessible(adminDefaultPortDomainNamespace, portForwardFileName, false);
  }

  /**
   * The test that `kubectl port-foward` is able to forward a local port to WLS administration port
   * (9002 in this test).
   * Verify that the WLS admin console can be accessed using http://localhost:localPort/console/login/LoginForm.jsp
   */
  @Order(3)
  @Test
  @DisplayName("Forward a local port to WLS administration port and verify WLS admin console is accessible")
  void testPortForwardAdministrationPort() {
    final int adminDefaultChannelPort = 9002;

    // Verify that using a local port admin forwarded to administration port (9002),
    // admin console is accessible via https://localhost:localPort/console/login/LoginForm.jsp
    String portForwardFileName = portForwardFileNameProfix + "-3.out";
    startPortForwardProcess(administrationPortDomainNamespace,
        administrationPortDomainUid, adminDefaultChannelPort, portForwardFileName);
    verifyAdminConsoleAccessible(administrationPortDomainNamespace, portForwardFileName, true);

    // Verify that when a local port is forwarded to the administration port,
    // admin console is not accessible using a local port forwarded to admin's default channel (7001)
    portForwardFileName = portForwardFileNameProfix + "-1.out";
    verifyAdminConsoleAccessible(adminDefaultPortDomainNamespace, portForwardFileName, true, "false");

    // Verify that when a local port is forwarded to the administration port,
    // admin console is not accessible using a local port forwarded to admin's default secure channel (7002)
    portForwardFileName = portForwardFileNameProfix + "-2.out";
    verifyAdminConsoleAccessible(adminDefaultPortDomainNamespace, portForwardFileName, false, "false");
  }

  private static String createAndVerifyDomainImage(String domainNamespace,
                                                   String imageName,
                                                   String modelFileName) {
    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(imageName, modelFileName, MII_BASIC_APP_NAME);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    return miiImage;
  }

  private static void createAndVerifyDomain(String miiImage, String domainNamespace, String domainUid) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
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
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainCrAndVerify(adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, miiImage, domainNamespace, domainUid);

    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check that admin server pod is ready and admin service exists in the domain namespace
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod is ready and the service exists in the domain namespace
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage,
                                              String domainNamespace,
                                              String domainUid) {

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
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
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default-admin")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private void startPortForwardProcess(String domainNamespace,
                                       String domainUid,
                                       int port,
                                       String portForwardFileName) {
    logger.info("Start port forward process");
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // Let kubectl choose and allocate a local port number that is not in use
    StringBuffer cmd = new StringBuffer("kubectl port-forward --address ")
        .append(hostName)
        .append(" pod/")
        .append(adminServerPodName)
        .append(" -n ")
        .append(domainNamespace)
        .append(" :")
        .append(String.valueOf(port))
        .append(" > ")
        .append(portForwardFileName)
        .append(" 2>&1 &");
    logger.info("Command to forward port {0} ", cmd.toString());

    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(cmd.toString(), true),
        String.format("Failed to forward port by running command %s", cmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to forward a local port to admin port. Error is %s ", result.stderr()));
  }

  private void stopPortForwardProcess() {
    logger.info("Stop port forward process");
    final String getPids = "ps -ef|grep 'kubectl* port-forward' | awk '{print $2}'";
    ExecResult result = assertDoesNotThrow(() -> exec(getPids, true));

    if (result.exitValue() == 0) {
      String[] pids = result.stdout().split(System.lineSeparator());

      for (String pid : pids) {
        logger.info("Command to kill port forward process: {0}", "kill -9 " + pid);
        result = assertDoesNotThrow(() -> exec("kill -9 " + pid, true));
        logger.info("stopPortForwardProcess command returned {0}", result.toString());
      }
    }
  }

  private String getForwardedPort(String portForwardFileName) {
    //wait until forwarded port number is written to the file upto 5 minutes
    assertDoesNotThrow(() ->
        testUntil(
            isFileExistAndNotEmpty(portForwardFileName),
            logger,
            "forwarded port number is written to the file " + portForwardFileName));

    String portFile = assertDoesNotThrow(() -> Files.readAllLines(Paths.get(portForwardFileName)).get(0));
    logger.info("Port forward info:\n {0}", portFile);

    String forwardedPortNo = null;
    String regex = ".*Forwarding.*:(\\d+).*";
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(portFile);
    if (matcher.find()) {
      forwardedPortNo = matcher.group(1);
    }

    return forwardedPortNo;
  }

  private void verifyAdminConsoleAccessible(String domainNamespace,
                                            String portForwardFileName,
                                            boolean secureMode,
                                            String... checkNotAccessible) {
    String forwardedPortNo = getForwardedPort(portForwardFileName);
    String httpKey = "http://";
    if (secureMode) {
      // Since WLS servers use self-signed certificates, it's ok to use --insecure option
      // to ignore SSL/TLS certificate errors:
      // curl: (60) SSL certificate problem: Invalid certificate chain
      // and explicitly allows curl to perform “insecure” SSL connections and transfers
      httpKey = " --insecure https://";
    }
    String consoleUrl = httpKey + hostName + ":" + forwardedPortNo + "/console/login/LoginForm.jsp";

    boolean checkConsole = assertDoesNotThrow(() ->
        checkAppUsingHostHeader(consoleUrl, domainNamespace + ".org", checkNotAccessible));
    if (checkNotAccessible.length == 0) {
      assertTrue(checkConsole, "Failed to access WebLogic console");
      logger.info("WebLogic console is accessible");
    } else {
      assertFalse(checkConsole, "Shouldn't be able to access WebLogic console");
      logger.info("WebLogic console is not accessible");
    }
  }
}
