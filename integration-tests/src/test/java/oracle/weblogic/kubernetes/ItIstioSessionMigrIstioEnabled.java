// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownManagedServerUsingServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createIstioDomainResource;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Test WLS Session Migration via istio enabled")
@IntegrationTest
class ItIstioSessionMigrIstioEnabled {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  // constants for creating domain image using model in image
  private static final String SESSMIGR_MODEL_FILE = "model.sessmigr.yaml";
  private static final String SESSMIGR_IMAGE_NAME = "sessmigr-mii-image";

  // constants for web service
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static final String SESSMIGR_APP_WAR_NAME = "sessmigr-war";
  private static final int SESSION_STATE = 4;
  private static Map<String, String> httpAttrMap;

  // constants for operator and WebLogic domain
  private static String domainUid = "sessmigr-domain-1";
  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static int managedServerPort = 7100;
  private static String finalPrimaryServerName = null;
  private static String configMapName = "istio-configmap";
  private static int replicaCount = 2;

  private static LoggingFacade logger = null;

  /**
   * Install operator, create a custom image using model in image with model files
   * and create a one cluster domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assign unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    List<String> appList = new ArrayList();
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + SESSMIGR_MODEL_FILE);

    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage = createMiiImageAndVerify(SESSMIGR_IMAGE_NAME, modelList, appList);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // config the domain with Istio ingress with Istio gateway
    String managedServerPrefix = domainUid + "-managed-server";
    assertDoesNotThrow(() ->
        configIstioModelInImageDomain(miiImage, domainNamespace, domainUid, managedServerPrefix),
        "setup for istio based domain failed");

    // map to save HTTP response data
    httpAttrMap = new HashMap<String, String>();
    httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
    httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
    httpAttrMap.put("primary", "(.*)primary>(.*)</primary(.*)");
    httpAttrMap.put("secondary", "(.*)secondary>(.*)</secondary(.*)");
    httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");
  }

  /**
   * When Istio is enabled, test sends a HTTP request to set http session state(count number),
   * get the primary and secondary server name, session create time and session state and from the util method
   * and save HTTP session info, then stop the primary server by changing ServerStartPolicy to NEVER and
   * patching domain. Send another HTTP request to get http session state (count number), primary server
   * and session create time. Verify that a new primary server is selected and HTTP session state is migrated.
   */
  @Test
  @DisplayName("When Istio is enabled, stop the primary server, verify that a new primary server is picked and "
      + "HTTP session state is migrated")
  void testSessionMigrationIstioEnabled() {
    final String primaryServerAttr = "primary";
    final String secondaryServerAttr = "secondary";
    final String sessionCreateTimeAttr = "sessioncreatetime";
    final String countAttr = "count";
    final String webServiceSetUrl = SESSMIGR_APP_WAR_NAME + "/?setCounter=" + SESSION_STATE;
    final String webServiceGetUrl = SESSMIGR_APP_WAR_NAME + "/?getCounter";
    String serverName = managedServerPrefix + "1";

    // send a HTTP request to set http session state(count number) and save HTTP session info
    // before shutting down the primary server
    Map<String, String> httpDataInfo =
        getServerAndSessionInfoAndVerify(serverName, webServiceSetUrl, " -c ");
    // get server and session info from web service deployed on the cluster
    String origPrimaryServerName = httpDataInfo.get(primaryServerAttr);
    String origSecondaryServerName = httpDataInfo.get(secondaryServerAttr);
    String origSessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    logger.info("Got the primary server {0}, the secondary server {1} "
        + "and session create time {2} before shutting down the primary server",
        origPrimaryServerName, origSecondaryServerName, origSessionCreateTime);

    // stop the primary server by changing ServerStartPolicy to NEVER and patching domain
    logger.info("Shut down the primary server {0}", origPrimaryServerName);
    shutdownServerUsingServerStartPolicy(origPrimaryServerName);

    // send a HTTP request to get server and session info after shutting down the primary server
    serverName = domainUid + "-" + origSecondaryServerName;
    httpDataInfo = getServerAndSessionInfoAndVerify(serverName, webServiceGetUrl, " -b ");
    // get server and session info from web service deployed on the cluster
    String primaryServerName = httpDataInfo.get(primaryServerAttr);
    String sessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    String countStr = httpDataInfo.get(countAttr);
    int count = Optional.ofNullable(countStr).map(Integer::valueOf).orElse(0);
    logger.info("After patching the domain, the primary server changes to {0} "
        + ", session create time {1} and session state {2}",
        primaryServerName, sessionCreateTime, countStr);

    // verify that a new primary server is picked and HTTP session state is migrated
    assertAll("Check that WebLogic server and session vars is not null or empty",
        () -> assertNotEquals(origPrimaryServerName, primaryServerName,
            "After the primary server stopped, another server should become the new primary server"),
        () -> assertEquals(origSessionCreateTime, sessionCreateTime,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server"),
        () -> assertEquals(count, SESSION_STATE,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server")
    );

    finalPrimaryServerName = primaryServerName;

    logger.info("Done testSessionMigration \nThe new primary server is {0}, it was {1}. "
        + "\nThe session state was set to {2}, it is migrated to the new primary server.",
        primaryServerName, origPrimaryServerName, SESSION_STATE);
  }

  private static int configIstioModelInImageDomain(String miiImage,
                                                   String domainNamespace,
                                                   String domainUid,
                                                   String managedServerPrefix) {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        "weblogic",
        "welcome1"),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.EMPTY_LIST);

    // create the domain object
    Domain domain = createIstioDomainResource(domainUid,
        domainNamespace,
        adminSecretName,
        OCIR_SECRET_NAME,
        encryptionSecretName,
        replicaCount,
        miiImage,
        configMapName,
        clusterName);

    // create model in image domain
    createDomainAndVerify(domain, domainNamespace);
    String adminServerPodName = domainUid + "-admin-server";
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    return 0;
  }

  private Map<String, String> processHttpRequest(String curlUrlPath,
                                                 String headerOption) {
    String[] httpAttrArray = {"sessioncreatetime", "sessionid", "primary", "secondary", "count"};
    Map<String, String> httpAttrInfo = new HashMap<String, String>();

    // build curl command
    String curlCmd = buildCurlCommand(curlUrlPath, headerOption);
    logger.info("==== Command to set HTTP request and get HTTP response {0} ", curlCmd);

    // set HTTP request and get HTTP response
    ExecResult execResult = assertDoesNotThrow(
        () -> execCommand(domainNamespace, adminServerPodName,
        null, true, "/bin/sh", "-c", curlCmd));
    if (execResult.exitValue() == 0) {
      logger.info("\n ===== HTTP response is \n " + execResult.stdout());
      assertAll("Check that primary server name is not null or empty",
          () -> assertNotNull(execResult.stdout(), "Primary server name shouldn’t be null"),
          () -> assertFalse(execResult.stdout().isEmpty(), "Primary server name shouldn’t be  empty")
      );

      for (String httpAttrKey : httpAttrArray) {
        String httpAttrValue = getHttpResponseAttribute(execResult.stdout(), httpAttrKey);
        httpAttrInfo.put(httpAttrKey, httpAttrValue);
      }
    } else {
      fail("==== Failed to process HTTP request " + execResult.stderr());
    }

    return httpAttrInfo;
  }

  private String buildCurlCommand(String curlUrlPath, String headerOption) {
    final String httpHeaderFile = "/u01/domains/header";
    final String clusterAddress = domainUid + "-cluster-" + clusterName;
    logger.info("Build a curl command with pod name {0}, curl URL path {1} and HTTP header option {2}",
        clusterAddress, curlUrlPath, headerOption);

    int waittime = 5;
    return new StringBuilder()
        .append("curl --silent --show-error")
        .append(" --connect-timeout ").append(waittime).append(" --max-time ").append(waittime)
        .append(" http://")
        .append(clusterAddress)
        .append(":")
        .append(managedServerPort)
        .append("/")
        .append(curlUrlPath)
        .append(headerOption)
        .append(httpHeaderFile).toString();
  }

  private String getHttpResponseAttribute(String httpResponseString, String attribute) {
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

  private void shutdownServerUsingServerStartPolicy(String msName) {
    final String podName = domainUid + "-" + msName;

    // shutdown a server by changing the it's serverStartPolicy property.
    logger.info("Shutdown the server {0}", msName);
    boolean serverStopped = assertDoesNotThrow(() ->
        shutdownManagedServerUsingServerStartPolicy(domainUid, domainNamespace, msName));
    assertTrue(serverStopped, String.format("Failed to shutdown server %s ", msName));

    // check that the managed server pod shutdown successfylly
    logger.info("Check that managed server pod {0} stopped in namespace {1}", podName, domainNamespace);
    checkPodDoesNotExist(podName, domainUid, domainNamespace);
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
  private Map<String, String> getServerAndSessionInfoAndVerify(String serverName,
                                                               String webServiceUrl,
                                                               String headerOption) {
    final String primaryServerAttr = "primary";
    final String secondaryServerAttr = "secondary";
    final String sessionCreateTimeAttr = "sessioncreatetime";
    final String countAttr = "count";

    // send a HTTP request to set http session state(count number) and save HTTP session info
    logger.info("Process HTTP request with web service URL {0} in the pod {1} ", webServiceUrl, serverName);
    Map<String, String> httpAttrInfo = processHttpRequest(webServiceUrl, headerOption);

    // get HTTP response data
    String primaryServerName = httpAttrInfo.get(primaryServerAttr);
    String secondaryServerName = httpAttrInfo.get(secondaryServerAttr);
    String sessionCreateTime = httpAttrInfo.get(sessionCreateTimeAttr);
    String countStr = httpAttrInfo.get(countAttr);

    // verify that the HTTP response data are not null
    assertAll("Check that WebLogic server and session vars is not null or empty",
        () -> assertNotNull(primaryServerName,"Primary server name shouldn’t be null"),
        () -> assertNotNull(secondaryServerName,"Second server name shouldn’t be null"),
        () -> assertNotNull(sessionCreateTime,"Session create time shouldn’t be null"),
        () -> assertNotNull(countStr,"Session state shouldn’t be null")
    );

    // map to save server and session info
    Map<String, String> httpDataInfo = new HashMap<String, String>();
    httpDataInfo.put(primaryServerAttr, primaryServerName);
    httpDataInfo.put(secondaryServerAttr, secondaryServerName);
    httpDataInfo.put(sessionCreateTimeAttr, sessionCreateTime);
    httpDataInfo.put(countAttr, countStr);

    return httpDataInfo;
  }
}
