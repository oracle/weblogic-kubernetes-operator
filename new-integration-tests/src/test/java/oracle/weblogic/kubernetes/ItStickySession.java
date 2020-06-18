// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.primitives.Ints;
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
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.LOGS_DIR;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallVoyager;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyVoyager;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installVoyagerIngressAndVerify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test is used for testing the affinity between a web client and a WebLogic server
 * for the duration of a HTTP session.
 */
@DisplayName("Test that Voyager is installed and the Voyager ingress is created successfully")
@IntegrationTest
class ItStickySession implements LoggedTest {

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
  private static int managedServerPort = 8001;
  private static int replicaCount = 2;
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;

  // constants for Voyager
  private static String cloudProvider = "baremetal";
  private static boolean enableValidatingWebhook = false;
  private static HelmParams voyagerHelmParams = null;

  /**
   * Install Voyager and operator, create a custom image using model in image with model files
   * and create a one cluster domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(3) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

    // get a unique Voyager namespace
    logger.info("Get a unique namespace for Voyager");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String voyagerNamespace = namespaces.get(0);

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    opNamespace = namespaces.get(1);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace = namespaces.get(2);

    // install and verify Voyager
    voyagerHelmParams =
      installAndVerifyVoyager(voyagerNamespace, cloudProvider, enableValidatingWebhook);

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
    // uninstall Voyager
    if (voyagerHelmParams != null) {
      assertThat(uninstallVoyager(voyagerHelmParams))
          .as("Test uninstallVoyager returns true")
          .withFailMessage("uninstallVoyager() did not return true")
          .isTrue();
    }
  }

  /**
   * Verify that two HTTP requests sent to WebLogic are directed to same WebLogic server.
   * The test uses a web application deployed on WebLogic cluster to track HTTP session.
   * server-affinity is achieved by Voyager ingress controller based on HTTP session information.
   */
  @Test
  @DisplayName("Create a Voyager ingress controller and verify that two HTTP connections are sticky to the same server")
  @Slow
  @MustNotRunInParallel
  public void testSameSessionStickiness() {
    final String ingressName = domainUid + "-ingress-host-routing";
    final String ingressServiceName = VOYAGER_CHART_NAME + "-" + ingressName;
    final String channelName = "tcp-80";
    final int counterNum = 4;
    final String webServiceSetUrl = SESSMIGR_APP_WAR_NAME + "/?setCounter=" + counterNum;
    final String webServiceGetUrl = SESSMIGR_APP_WAR_NAME + "/?getCounter";
    final String serverNameAttr = "servername";
    final String sessionIdAttr = "sessionid";
    final String countAttr = "count";

    // create Voyager ingress resource
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    List<String>  hostNames =
        installVoyagerIngressAndVerify(domainUid, domainNamespace, ingressName, clusterNameMsPortMap);

    // get ingress service Nodeport
    int ingressServiceNodePort = assertDoesNotThrow(
        () -> getServiceNodePort(domainNamespace, ingressServiceName, channelName),
            "Getting admin server node port failed");
    logger.info("Node port for {0} is: {1} :", ingressServiceName, ingressServiceNodePort);

    // send a HTTP request to set http session state(count number) and save HTTP session info
    Map<String, String> httpDataInfo =
        getServerAndSessionInfoAndVerify(hostNames.get(0),
            ingressServiceNodePort, webServiceSetUrl, " -D ");
    // get server and session info from web service deployed on the cluster
    String serverName1 = httpDataInfo.get(serverNameAttr);
    String sessionId1 = httpDataInfo.get(sessionIdAttr);
    logger.info("Got the server {0} and session ID {1} from the first HTTP connection",
            serverName1, sessionId1);

    // send a HTTP request again to get server and session info
    httpDataInfo =
      getServerAndSessionInfoAndVerify(hostNames.get(0),
          ingressServiceNodePort, webServiceGetUrl, " -b ");
    // get server and session info from web service deployed on the cluster
    String serverName2 = httpDataInfo.get(serverNameAttr);
    String sessionId2 = httpDataInfo.get(sessionIdAttr);
    String countStr = httpDataInfo.get(countAttr);
    int count = Optional.ofNullable(countStr).map(Ints::tryParse).orElse(0);
    logger.info("Got the server {0}, session ID {1} and session state {2} "
        + "from the second HTTP connection", serverName2, sessionId2, count);

    // verify that two HTTP connections are sticky to the same server
    assertAll("Check that teh sticky session is supported",
        () -> assertEquals(serverName1, serverName2,
            "HTTP connections should be sticky to the server " + serverName1),
        () -> assertEquals(sessionId1, sessionId2,
            "HTTP session ID should be same for all HTTP connections " + sessionId1),
        () -> assertEquals(count, SESSION_STATE,
            "HTTP session state should equels " + SESSION_STATE)
    );

    logger.info("SUCCESS --- testSameSessionStickiness \n"
        + "Two HTTP connections are sticky to server {0} The session state "
        + "from the econd HTTP connections is {2}", serverName2, SESSION_STATE);
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(SESSMIGR_IMAGE_NAME, SESSMIGR_MODEL_FILE, SESSMIGR_APP_NAME);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    logger.info("Create docker registry secret in namespace {0}", domainNamespace);
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
        String.format("create Docker Registry Secret failed for %s", REPO_SECRET_NAME));

    return miiImage;
  }

  private static void createAndVerifyDomain(String miiImage) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        "weblogic", "welcome1"),
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
    createDomainCrAndVerify(adminSecretName, REPO_SECRET_NAME, encryptionSecretName, miiImage);

    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodExists(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage) {
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
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  /**
   * An util method referred by the test method testSessionMigration. It sends a HTTP request
   * to set or get http session state (count number) and return the primary server,
   * the secondary server, session create time and session state(count number).
   *
   * @param serverName server name in the cluster on which the web app is running
   * @param webServiceUrl fully qualified URL to the server on which the web app is running
   * @param headerOption option to save or use HTTP session info
   *
   * @return map that contains primary and secondary server names, session create time and session state
   */
  private Map<String, String> getServerAndSessionInfoAndVerify(String hostName,
                                                               int ingressServiceNodePort,
                                                               String curlUrlPath,
                                                               String headerOption) {
    final String serverNameAttr = "servername";
    final String sessionIdAttr = "sessionid";
    final String countAttr = "count";

    // send a HTTP request
    logger.info("Process HTTP request in host {0} and ingressServiceNodePort {1} ",
        hostName, ingressServiceNodePort);
    Map<String, String> httpAttrInfo =
        processHttpRequest(hostName, ingressServiceNodePort, curlUrlPath, headerOption);

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
                                                        int ingressServiceNodePort,
                                                        String curlUrlPath,
                                                        String headerOption) {
    String[] httpAttrArray =
        {"sessioncreatetime", "sessionid", "servername", "count"};
    Map<String, String> httpAttrInfo = new HashMap<String, String>();

    // build curl command
    String curlCmd =
        buildCurlCommand(hostName, ingressServiceNodePort, curlUrlPath, headerOption);
    logger.info("Command to set HTTP request or get HTTP response {0} ", curlCmd);

    // set HTTP request and get HTTP response
    ExecResult execResult = assertDoesNotThrow(
        () -> ExecCommand.exec(curlCmd, true));
    assertNotNull(execResult, "curl command returns null");

    if (execResult.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + execResult.stdout());
      assertAll("Check that primary server name is not null or empty",
          () -> assertNotNull(execResult.stdout(), "Primary server name shouldn’t be null"),
          () -> assertFalse(execResult.stdout().isEmpty(), "Primary server name shouldn’t be  empty")
      );

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
                                         int ingressServiceNodePort,
                                         String curlUrlPath,
                                         String headerOption) {
    logger.info("Build a curl command with hostname {0} and ingress service NodePort {1}",
        hostName, ingressServiceNodePort);

    final String httpHeaderFile = LOGS_DIR + "/headers";

    StringBuffer curlCmd =
        new StringBuffer("curl --show-error --noproxy '*' -H 'host: ");
    curlCmd.append(hostName)
        .append("' http://")
        .append(K8S_NODEPORT_HOST)
        .append(":")
        .append(ingressServiceNodePort)
        .append("/")
        .append(curlUrlPath)
        .append(headerOption)
        .append(httpHeaderFile);

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
}
