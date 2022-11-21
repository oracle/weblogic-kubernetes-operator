// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterService;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.ActionConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.LOGS_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test is used for testing the affinity between a web client and a WebLogic server
 * for the duration of a HTTP session using Traefik ingress controllers
 * as well as cluster service.
 */
@DisplayName("Test sticky sessions management with Traefik and ClusterService")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItStickySession {

  // constants for creating domain image using model in image
  private static final String SESSMIGR_MODEL_FILE = "model.stickysess.yaml";
  private static final String SESSMIGR_IMAGE_NAME = "mii-image";

  // constants for web service
  private static final String SESSMIGR_APP_NAME = "stickysess-app";
  private static final String SESSMIGR_APP_WAR_NAME = "stickysess-war";
  private static final int SESSION_STATE = 4;
  private static Map<String, String> httpAttrMap;

  // constants for operator and WebLogic domain
  private static String domainUid = "stickysess-domain-1";
  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domainUid + "-admin-server";
  private static String managedServerPrefix = domainUid + "-managed-server";
  private static int replicaCount = 1;
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;

  // constants for Traefik
  private static HelmParams traefikHelmParams = null;
  private static LoggingFacade logger = null;

  /**
   * Install Traefik and operator, create a custom image using model in image
   * with model files and create a one cluster domain.
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
    traefikNamespace = namespaces.get(0);

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    opNamespace = namespaces.get(1);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace = namespaces.get(2);

    // install and verify Traefik
    if (!OKD) {
      traefikHelmParams =
          installAndVerifyTraefik(traefikNamespace, 0, 0);
    }

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create and verify WebLogic domain image using model in image with model files
    String imageName = createAndVerifyDomainImage();

    // create and verify one cluster domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName);

    // map to save HTTP response data
    httpAttrMap = new HashMap<String, String>();
    httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
    httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
    httpAttrMap.put("servername", "(.*)connectedservername>(.*)</connectedservername(.*)");
    httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");
  }

  @AfterAll
  void tearDown() {
    // uninstall Traefik
    if (traefikHelmParams != null) {
      assertThat(uninstallTraefik(traefikHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }
  }

  /**
   * Verify that using Traefik ingress controller, two HTTP requests sent to WebLogic
   * are directed to same WebLogic server.
   * The test uses a web application deployed on WebLogic cluster to track HTTP session.
   * server-affinity is achieved by Traefik ingress controller based on HTTP session information.
   */
  @Test
  @DisplayName("Create a Traefik ingress resource and verify that two HTTP connections are sticky to the same server")
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  void testSameSessionStickinessUsingTraefik() {
    final String ingressServiceName = traefikHelmParams.getReleaseName();
    final String channelName = "web";

    // create Traefik ingress resource
    createTraefikIngressRoutingRules();

    String hostName = new StringBuffer()
        .append(domainUid)
        .append(".")
        .append(domainNamespace)
        .append(".")
        .append(clusterName)
        .append(".test").toString();

    // get Traefik ingress service Nodeport
    int ingressServiceNodePort =
        getIngressServiceNodePort(traefikNamespace, ingressServiceName, channelName);

    // verify that two HTTP connections are sticky to the same server
    sendHttpRequestsToTestSessionStickinessAndVerify(hostName, ingressServiceNodePort);
  }

  /**
   * Verify that using OKD routes, two HTTP requests sent to WebLogic
   * are directed to same WebLogic server.
   * The test uses a web application deployed on WebLogic cluster to track HTTP session.
   * server-affinity is achieved by Traefik ingress controller based on HTTP session information.
   */
  @Test
  @DisplayName("Create a Traefik ingress resource and verify that two HTTP connections are sticky to the same server")
  @EnabledIfEnvironmentVariable(named = "OKD", matches = "true")
  void testSameSessionStickinessinOKD() {
    final String serviceName = domainUid + "-cluster-" + clusterName;
    //final String channelName = "web";

    // create route for cluster service
    String ingressHost = createRouteForOKD(serviceName, domainNamespace);

    // Since the app seems to take a bit longer to be available,
    // checking if the app is running by executing the curl command
    String curlString
        = buildCurlCommand(ingressHost, 0, SESSMIGR_APP_WAR_NAME + "/?getCounter", " -b ");
    logger.info("Command to set HTTP request or get HTTP response {0} ", curlString);
    testUntil(
        assertDoesNotThrow(()
            -> () -> exec(curlString, true).stdout().contains("managed-server")),
        logger,
        "Checking if app is available");
    // verify that two HTTP connections are sticky to the same server
    sendHttpRequestsToTestSessionStickinessAndVerify(ingressHost, 0);
  }

  /**
   * Verify that using cluster service, two HTTP requests sent to WebLogic
   * are directed to same WebLogic server.
   * The test uses a web application deployed on WebLogic cluster to track HTTP session.
   * server-affinity is achieved by cluster service based on HTTP session information.
   */
  @Test
  @DisplayName("Verify that two HTTP connections are sticky to the same server using cluster service")
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  void testSameSessionStickinessUsingClusterService() {
    //build cluster hostname
    String hostName = new StringBuffer()
        .append(domainUid)
        .append(".")
        .append(domainNamespace)
        .append(".")
        .append(clusterName)
        .append(".test").toString();

    //build cluster address
    final String clusterAddress = domainUid + "-cluster-" + clusterName;
    //get cluster port
    int clusterPort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace, clusterAddress, "default"),
        "Getting admin server default port failed");
    assertFalse(clusterPort == 0 || clusterPort < 0, "cluster Port is an invalid number");
    logger.info("cluster port for cluster server {0} is: {1}", clusterAddress, clusterPort);

    // verify that two HTTP connections are sticky to the same server
    sendHttpRequestsToTestSessionStickinessAndVerify(hostName, clusterPort, clusterAddress);
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(SESSMIGR_IMAGE_NAME, SESSMIGR_MODEL_FILE, SESSMIGR_APP_NAME);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    return miiImage;
  }

  private static void createAndVerifyDomain(String miiImage) {
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
        domainUid, domainNamespace, miiImage);
    createDomainCrAndVerify(adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, miiImage);

    // check that admin server pod is ready and the service exists in the domain namespace
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
                                              String miiImage) {

    ClusterService myClusterService = new ClusterService();
    myClusterService.setSessionAffinity("ClientIP");

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
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
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    setPodAntiAffinity(domain);

    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private Map<String, String> getServerAndSessionInfoAndVerify(String hostName,
                                                               int servicePort,
                                                               String curlUrlPath,
                                                               String headerOption,
                                                               String... clusterAddress) {
    final String serverNameAttr = "servername";
    final String sessionIdAttr = "sessionid";
    final String countAttr = "count";

    // send a HTTP request
    logger.info("Process HTTP request in host {0} and servicePort {1} ",
        hostName, servicePort);
    Map<String, String> httpAttrInfo =
        processHttpRequest(hostName, servicePort, curlUrlPath, headerOption, clusterAddress);

    // get HTTP response data
    String serverName = httpAttrInfo.get(serverNameAttr);
    String sessionId = httpAttrInfo.get(sessionIdAttr);
    String countStr = httpAttrInfo.get(countAttr);

    // verify that the HTTP response data are not null
    assertAll("Check that WebLogic server and session vars is not null or empty",
        () -> assertNotNull(serverName,"Server name shouldn’t be null"),
        () -> assertNotNull(sessionId,"Session ID shouldn’t be null"),
        () -> assertNotNull(countStr,"Session state shouldn’t be null")
    );

    // map to save server and session info
    Map<String, String> httpDataInfo = new HashMap<String, String>();
    httpDataInfo.put(serverNameAttr, serverName);
    httpDataInfo.put(sessionIdAttr, sessionId);
    httpDataInfo.put(countAttr, countStr);

    return httpDataInfo;
  }

  private static Map<String, String> processHttpRequest(String hostName,
                                                        int servicePort,
                                                        String curlUrlPath,
                                                        String headerOption,
                                                        String... clusterAddress) {
    String[] httpAttrArray =
        {"sessioncreatetime", "sessionid", "servername", "count"};
    Map<String, String> httpAttrInfo = new HashMap<String, String>();

    // build curl command
    String curlCmd =
        buildCurlCommand(hostName, servicePort, curlUrlPath, headerOption, clusterAddress);
    logger.info("Command to set HTTP request or get HTTP response {0} ", curlCmd);
    ExecResult execResult = null;

    if (clusterAddress.length == 0) {
      // set HTTP request and get HTTP response in a local machine
      execResult = assertDoesNotThrow(() -> ExecCommand.exec(curlCmd, true));
    } else {
      // set HTTP request and get HTTP response in admin pod
      execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, adminServerPodName,
          null, true, "/bin/sh", "-c", curlCmd));
    }

    if (execResult.exitValue() == 0) {
      assertNotNull(execResult.stdout(), "Primary server name should not be null");
      assertFalse(execResult.stdout().isEmpty(), "Primary server name should not be empty");
      logger.info("\n HTTP response is \n " + execResult.stdout());

      for (String httpAttrKey : httpAttrArray) {
        String httpAttrValue = getHttpResponseAttribute(execResult.stdout(), httpAttrKey);
        httpAttrInfo.put(httpAttrKey, httpAttrValue);
      }
    } else {
      fail("Failed to process HTTP request " + execResult.stderr());
    }

    return httpAttrInfo;
  }

  private static String buildCurlCommand(String hostName,
                                         int servicePort,
                                         String curlUrlPath,
                                         String headerOption,
                                         String... clusterAddress) {

    StringBuffer curlCmd = new StringBuffer("curl --show-error");
    logger.info("Build a curl command with hostname {0} and port {1}", hostName, servicePort);

    if (clusterAddress.length == 0) {
      //use a LBer ingress controller to build the curl command to run on local
      final String httpHeaderFile = LOGS_DIR + "/headers";
      logger.info("Build a curl command with hostname {0} and port {1}", hostName, servicePort);

      String hostAndPort = getHostAndPort(hostName, servicePort);

      curlCmd.append(" --noproxy '*' -H 'host: ")
          .append(hostName)
          .append("' http://")
          .append(hostAndPort)
          .append("/")
          .append(curlUrlPath)
          .append(headerOption)
          .append(httpHeaderFile);
    } else {
      //use cluster service to build the curl command to run in admin pod
      // save the cookie file to /u01 in order to run the test on openshift env
      final String httpHeaderFile = "/u01/header";
      logger.info("Build a curl command with pod name {0}, curl URL path {1} and HTTP header option {2}",
          clusterAddress[0], curlUrlPath, headerOption);

      int waittime = 5;
      curlCmd.append(" --silent --connect-timeout ")
          .append(waittime)
          .append(" --max-time ").append(waittime)
          .append(" http://")
          .append(clusterAddress[0])
          .append(":")
          .append(servicePort)
          .append("/")
          .append(curlUrlPath)
          .append(headerOption)
          .append(httpHeaderFile);
    }

    return curlCmd.toString();
  }

  private static String getHttpResponseAttribute(String httpResponseString, String attribute) {
    // retrieve the search pattern that matches the given HTTP data attribute
    String attrPatn = httpAttrMap.get(attribute);
    assertNotNull(attrPatn,"HTTP Attribute key shouldn’t be null");

    // search the value of given HTTP data attribute
    Pattern pattern = Pattern.compile(attrPatn);
    Matcher matcher = pattern.matcher(httpResponseString);
    String httpAttribute = null;

    if (matcher.find()) {
      httpAttribute = matcher.group(2);
    }

    return httpAttribute;
  }

  private boolean createTraefikIngressRoutingRules() {
    logger.info("Creating ingress resource");

    // prepare Traefik ingress resource file
    final String ingressResourceFileName = "traefik/traefik-ingress-rules-stickysession.yaml";
    Path srcFile =
        Paths.get(ActionConstants.RESOURCE_DIR, ingressResourceFileName);
    Path dstFile =
        Paths.get(TestConstants.RESULTS_ROOT, ingressResourceFileName);
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", domainNamespace)
          .replaceAll("@domain1uid@", domainUid)
          .getBytes(StandardCharsets.UTF_8));
    });

    // create Traefik ingress resource
    String createIngressCmd = KUBERNETES_CLI + " create -f " + dstFile;
    logger.info("Command to create Traefik ingress routing rules " + createIngressCmd);
    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(createIngressCmd, true),
        String.format("Failed to create Traefik ingress routing rules %s", createIngressCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to create Traefik ingress routing rules. Error is %s ", result.stderr()));

    // get Traefik ingress service name
    String  getServiceName = KUBERNETES_CLI + " get services -n " + traefikNamespace + " -o name";
    logger.info("Command to get Traefik ingress service name " + getServiceName);
    result = assertDoesNotThrow(() -> ExecCommand.exec(getServiceName, true),
        String.format("Failed to get Traefik ingress service name %s", getServiceName));
    assertEquals(0, result.exitValue(),
        String.format("Failed to Traefik ingress service name . Error is %s ", result.stderr()));
    String traefikServiceName = result.stdout().trim().split("/")[1];

    // check that Traefik service exists in the Traefik namespace
    logger.info("Checking that Traefik service {0} exists in namespace {1}",
        traefikServiceName, traefikNamespace);
    checkServiceExists(traefikServiceName, traefikNamespace);

    return true;
  }

  private int getIngressServiceNodePort(String nameSpace, String ingressServiceName, String channelName) {
    // get ingress service Nodeport
    int ingressServiceNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(nameSpace, ingressServiceName, channelName),
        "Getting web node port for Traefik loadbalancer failed");
    logger.info("Node port for {0} is: {1} :", ingressServiceName, ingressServiceNodePort);

    return ingressServiceNodePort;
  }

  private void sendHttpRequestsToTestSessionStickinessAndVerify(String hostname,
                                                                int servicePort,
                                                                String... clusterAddress) {
    final int counterNum = 4;
    final String webServiceSetUrl = SESSMIGR_APP_WAR_NAME + "/?setCounter=" + counterNum;
    final String webServiceGetUrl = SESSMIGR_APP_WAR_NAME + "/?getCounter";
    final String serverNameAttr = "servername";
    final String sessionIdAttr = "sessionid";
    final String countAttr = "count";

    // send a HTTP request to set http session state(count number) and save HTTP session info
    Map<String, String> httpDataInfo = getServerAndSessionInfoAndVerify(hostname,
            servicePort, webServiceSetUrl, " -c ", clusterAddress);
    // get server and session info from web service deployed on the cluster
    String serverName1 = httpDataInfo.get(serverNameAttr);
    String sessionId1 = httpDataInfo.get(sessionIdAttr);
    logger.info("Got the server {0} and session ID {1} from the first HTTP connection",
        serverName1, sessionId1);

    // send a HTTP request again to get server and session info
    httpDataInfo = getServerAndSessionInfoAndVerify(hostname,
        servicePort, webServiceGetUrl, " -b ", clusterAddress);
    // get server and session info from web service deployed on the cluster
    String serverName2 = httpDataInfo.get(serverNameAttr);
    String sessionId2 = httpDataInfo.get(sessionIdAttr);
    String countStr = httpDataInfo.get(countAttr);
    int count = Optional.ofNullable(countStr).map(Integer::valueOf).orElse(0);
    logger.info("Got the server {0}, session ID {1} and session state {2} "
        + "from the second HTTP connection", serverName2, sessionId2, count);

    // verify that two HTTP connections are sticky to the same server
    assertAll("Check that the sticky session is supported",
        () -> assertEquals(serverName1, serverName2,
            "HTTP connections should be sticky to the server " + serverName1),
        () -> assertEquals(sessionId1, sessionId2,
            "HTTP session ID should be same for all HTTP connections " + sessionId1),
        () -> assertEquals(SESSION_STATE, count,
            "HTTP session state should equels " + SESSION_STATE)
    );

    logger.info("SUCCESS --- test same session stickiness \n"
        + "Two HTTP connections are sticky to server {0} The session state "
        + "from the second HTTP connections is {2}", serverName2, SESSION_STATE);
  }
}
