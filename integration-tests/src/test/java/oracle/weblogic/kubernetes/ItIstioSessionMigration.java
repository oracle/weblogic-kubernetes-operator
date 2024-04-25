// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.custom.Quantity;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.configIstioModelInImageDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateNewModelFileWithUpdatedDomainUid;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.getOrigModelFile;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.getServerAndSessionInfoAndVerify;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.shutdownServerAndVerify;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Test WLS Session Migration via istio enabled")
@IntegrationTest
@Tag("kind-parallel")
@Tag("oke-arm")
@Tag("olcne-mrg")
@Tag("oke-parallel")
class ItIstioSessionMigration {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  // constants for creating domain image using model in image
  private static final String SESSMIGR_IMAGE_NAME = "istio-sessmigr-mii-image";

  // constants for web service
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static final String SESSMIGR_APP_WAR_NAME = "sessmigr-war";
  private static final int SESSION_STATE = 4;
  private static Map<String, String> httpAttrMap;

  // constants for operator and WebLogic domain
  private static String domainUid = "istio-sessmigr-domain";
  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static int managedServerPort = 7100;
  private static String finalPrimaryServerName = null;
  private static String configMapName = "istio-configmap";
  private static int replicaCount = 2;

  private static LoggingFacade logger = null;

  private static Map<String, Quantity> resourceRequest = new HashMap<>();
  private static Map<String, Quantity> resourceLimit = new HashMap<>();

  /**
   * Install operator, create a custom image using model in image with model files
   * and create a WebLlogic domain with a dynamic cluster.
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
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Generate the model.sessmigr.yaml file at RESULTS_ROOT
    String destSessionMigrYamlFile =
        generateNewModelFileWithUpdatedDomainUid(domainUid, "ItIstioSessionMigration", getOrigModelFile());

    List<String> appList = new ArrayList<>();
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    //final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + SESSMIGR_MODEL_FILE);
    final List<String> modelList = Collections.singletonList(destSessionMigrYamlFile);

    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage = createMiiImageAndVerify(SESSMIGR_IMAGE_NAME, modelList, appList);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // set resource request and limit
    resourceRequest.put("cpu", new Quantity("250m"));
    resourceRequest.put("memory", new Quantity("768Mi"));
    resourceLimit.put("cpu", new Quantity("2"));
    resourceLimit.put("memory", new Quantity("2Gi"));

    // config the domain with Istio ingress with Istio gateway
    String managedServerPrefix = domainUid + "-managed-server";
    assertDoesNotThrow(() -> configIstioModelInImageDomain(miiImage, domainNamespace,
        domainUid, managedServerPrefix, clusterName, configMapName, replicaCount),
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
   * In an istio enabled Environment, test sends a HTTP request to set http session state(count number),
   * get the primary and secondary server name, session create time and session state and from the util method
   * and save HTTP session info, then stop the primary server by changing ServerStartPolicy to Never and
   * patching domain. Send another HTTP request to get http session state (count number), primary server
   * and session create time. Verify that a new primary server is selected and HTTP session state is migrated.
   */
  @Test
  @DisplayName("Verify session migration in an istio enabled environment")
  void testSessionMigrationIstioEnabled() {
    final String primaryServerAttr = "primary";
    final String secondaryServerAttr = "secondary";
    final String sessionCreateTimeAttr = "sessioncreatetime";
    final String countAttr = "count";
    final String webServiceSetUrl = SESSMIGR_APP_WAR_NAME + "/?setCounter=" + SESSION_STATE;
    final String webServiceGetUrl = SESSMIGR_APP_WAR_NAME + "/?getCounter";
    final String clusterAddress = domainUid + "-cluster-" + clusterName;
    String serverName = managedServerPrefix + "1";

    // send a HTTP request to set http session state(count number) and save HTTP session info
    // before shutting down the primary server
    Map<String, String> httpDataInfo = getServerAndSessionInfoAndVerify(domainNamespace,
        adminServerPodName, serverName, clusterAddress, managedServerPort, webServiceSetUrl, " -c ");

    // get server and session info from web service deployed on the cluster
    String origPrimaryServerName = httpDataInfo.get(primaryServerAttr);
    String origSecondaryServerName = httpDataInfo.get(secondaryServerAttr);
    String origSessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    logger.info("Got the primary server {0}, the secondary server {1} "
        + "and session create time {2} before shutting down the primary server.",
        origPrimaryServerName, origSecondaryServerName, origSessionCreateTime);

    // stop the primary server by changing ServerStartPolicy to Never and patching domain
    logger.info("Shut down the primary server {0}", origPrimaryServerName);
    shutdownServerAndVerify(domainUid, domainNamespace, origPrimaryServerName);

    // send a HTTP request to get server and session info after shutting down the primary server
    serverName = domainUid + "-" + origSecondaryServerName;
    httpDataInfo = getServerAndSessionInfoAndVerify(domainNamespace, adminServerPodName,
        serverName, clusterAddress, managedServerPort, webServiceGetUrl, " -b ");

    // get server and session info from web service deployed on the cluster
    String primaryServerName = httpDataInfo.get(primaryServerAttr);
    String sessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    String countStr = httpDataInfo.get(countAttr);
    int count;
    if (countStr.equalsIgnoreCase("null")) {
      count = managedServerPort;
    } else {
      count = Optional.ofNullable(countStr).map(Integer::valueOf).orElse(managedServerPort);
    }
    logger.info("After patching the domain, the primary server changes to {0} "
        + ", session create time {1} and session state {2}",
        primaryServerName, sessionCreateTime, countStr);

    // verify that a new primary server is picked and HTTP session state is migrated
    assertAll("Check that WebLogic server and session vars is not null or empty",
        () -> assertNotEquals(origPrimaryServerName, primaryServerName,
            "After the primary server stopped, another server should become the new primary server"),
        () -> assertEquals(origSessionCreateTime, sessionCreateTime,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server"),
        () -> assertEquals(SESSION_STATE, count,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server")
    );

    finalPrimaryServerName = primaryServerName;

    logger.info("Done testSessionMigration \nThe new primary server is {0}, it was {1}. "
        + "\nThe session state was set to {2}, it is migrated to the new primary server.",
        primaryServerName, origPrimaryServerName, SESSION_STATE);
  }
}
