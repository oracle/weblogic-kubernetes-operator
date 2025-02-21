// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.configIstioModelInImageDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateNewModelFileWithUpdatedDomainUid;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isAppInServerPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingRest;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test WLS Session Migration via istio enabled using Istio gateway")
@IntegrationTest
@Tag("kind-parallel")
@Tag("olcne-mrg")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItIstioGatewaySessionMigration {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  // constants for creating domain image using model in image
  private static final String SESSMIGR_IMAGE_NAME = "istiogateway-sessmigr-mii-image";

  // constants for web service
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static final String SESSMIGR_APP_WAR_NAME = "sessmigr-war";
  private static final int SESSION_STATE = 4;
  private static Map<String, String> httpAttrMap;

  // constants for operator and WebLogic domain
  private static String domainUid = "istiogateway-sessmigr-domain";
  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static String finalPrimaryServerName = null;
  private static String configMapName = "istio-configmap";
  private static String istioGatewayConfigFile = "istio-sessmigr-template.yaml";
  private static int replicaCount = 2;
  private static int istioIngressPort = 0;
  private static String testWebAppWarLoc = null;
  private static int managedServerPort = 7100;
  
  static {
    if (!WEBLOGIC_IMAGE_TAG.startsWith("12")) {
      managedServerPort = 7001;
    }
  }

  private static final String istioNamespace = "istio-system";
  private static final String istioIngressServiceName = "istio-ingressgateway";

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
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // create testwebapp.war
    testWebAppWarLoc = createTestWebAppWarFile(domainNamespace);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Generate the model.sessmigr.yaml file at RESULTS_ROOT
    String destSessionMigrYamlFile = generateNewModelFileWithUpdatedDomainUid(domainUid,
        "ItIstioGatewaySessionMigration", getOrigModelFile());

    List<String> appList = new ArrayList<>();
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    final List<String> modelList = Collections.singletonList(destSessionMigrYamlFile);

    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage = createMiiImageAndVerify(SESSMIGR_IMAGE_NAME, modelList, appList);

    // repo login and push image to repo registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // config the domain with Istio ingress with Istio gateway
    String managedServerPrefix = domainUid + "-managed-server";
    assertDoesNotThrow(() ->
        configIstioGatewayModelInImageDomain(miiImage, domainNamespace, domainUid, managedServerPrefix),
        "setup for istio based domain failed");
    istioIngressPort = getIstioHttpIngressPort();

    // map to save HTTP response data
    httpAttrMap = new HashMap<String, String>();
    httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
    httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
    httpAttrMap.put("primary", "(.*)primary>(.*)</primary(.*)");
    httpAttrMap.put("secondary", "(.*)secondary>(.*)</secondary(.*)");
    httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");
  }

  /**
   * When istio is enabled using Istio gateway, the test sends a HTTP request to set http session state(count number),
   * get the primary and secondary server name, session create time and session state and from the util method and
   * save HTTP session info, then stop the primary server by changing serverStartPolicy to Never and patching domain.
   * Send another HTTP request to get http session state (count number), primary server and session create time.
   * Verify that a new primary server is selected and HTTP session state is migrated.
   */
  @Test
  @DisplayName("When istio is enabled using Istio gateway, stop the primary server, "
      + "verify that a new primary server is picked and HTTP session state is migrated")
  void testSessionMigrationIstioGateway() throws UnknownHostException {
    final String primaryServerAttr = "primary";
    final String secondaryServerAttr = "secondary";
    final String sessionCreateTimeAttr = "sessioncreatetime";
    final String countAttr = "count";
    final String webServiceSetUrl = SESSMIGR_APP_WAR_NAME + "/?setCounter=" + SESSION_STATE;
    final String webServiceGetUrl = SESSMIGR_APP_WAR_NAME + "/?getCounter";
    String serverName = managedServerPrefix + "1";

    // In internal OKE env, use Istio EXTERNAL-IP; in non-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    String istioIngressIP = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : formatIPv6Host(K8S_NODEPORT_HOST);
    int servicePort = istioIngressPort;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      istioIngressIP = domainUid + "-cluster-cluster-1." + domainNamespace + ".svc.cluster.local";
      servicePort = 7001;      
    }

    // send a HTTP request to set http session state(count number) and save HTTP session info
    // before shutting down the primary server
    // the NodePort services created by the operator are not usable, because they would expose ports
    // on the worker node’s private IP addresses only, which are not reachable from outside the cluster
    Map<String, String> httpDataInfo = OKE_CLUSTER ? getServerAndSessionInfoAndVerify(domainNamespace,
            adminServerPodName, serverName, istioIngressIP, 0, webServiceSetUrl, " -c ")
        : getServerAndSessionInfoAndVerify(domainNamespace,
            adminServerPodName, serverName, istioIngressIP, servicePort, webServiceSetUrl, " -c ");
    // get server and session info from web service deployed on the cluster
    String origPrimaryServerName = httpDataInfo.get(primaryServerAttr);
    String origSecondaryServerName = httpDataInfo.get(secondaryServerAttr);
    String origSessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    logger.info("Got the primary server {0}, the secondary server {1} "
        + "and session create time {2} before shutting down the primary server",
            origPrimaryServerName, origSecondaryServerName, origSessionCreateTime);

    // stop the primary server by changing ServerStartPolicy to Never and patching domain
    logger.info("Shut down the primary server {0}", origPrimaryServerName);
    shutdownServerAndVerify(domainUid, domainNamespace, origPrimaryServerName);

    // send a HTTP request to get server and session info after shutting down the primary server
    serverName = domainUid + "-" + origSecondaryServerName;
    httpDataInfo = OKE_CLUSTER ? getServerAndSessionInfoAndVerify(domainNamespace,
            adminServerPodName, serverName, istioIngressIP, 0, webServiceGetUrl, " -b ")
        : getServerAndSessionInfoAndVerify(domainNamespace,
            adminServerPodName, serverName, istioIngressIP, servicePort, webServiceGetUrl, " -b ");
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
        () -> assertEquals(SESSION_STATE, count,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server")
    );

    finalPrimaryServerName = primaryServerName;

    logger.info("Done testSessionMigration \nThe new primary server is {0}, it was {1}. "
        + "\nThe session state was set to {2}, it is migrated to the new primary server.",
            primaryServerName, origPrimaryServerName, SESSION_STATE);
  }

  private static int configIstioGatewayModelInImageDomain(String miiImage,
                                                          String domainNamespace,
                                                          String domainUid,
                                                          String managedServerPrefix) {
    // config Istio MII domain
    assertDoesNotThrow(() -> configIstioModelInImageDomain(miiImage, domainNamespace,
        domainUid, managedServerPrefix, clusterName, configMapName, replicaCount),
            "setup for istio based domain failed");

    String clusterService = domainUid + "-cluster-" + clusterName + "." + domainNamespace + ".svc.cluster.local";

    Map<String, String> templateMap = new HashMap<>();
    templateMap.put("NAMESPACE", domainNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE", adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);
    if (!WEBLOGIC_IMAGE_TAG.startsWith("12")) {
      templateMap.put("7100", String.valueOf(managedServerPort));
      templateMap.put("8001", String.valueOf(managedServerPort));
    }

    // create Istio gateway
    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", istioGatewayConfigFile);
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(() -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    // deploy Istio DestinationRule
    Path srcDrFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetDrFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "istio-dr.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(() -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    String host;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      host = "localhost";
      istioIngressPort = ISTIO_HTTP_HOSTPORT;
    } else {
      host = formatIPv6Host(K8S_NODEPORT_HOST);
    }
    // In internal OKE env, use Istio EXTERNAL-IP; in non-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : host + ":" + istioIngressPort;

    String restUrl = "http://" + hostAndPort + "/management/tenant-monitoring/servers/";
    boolean checlReadyApp = checkAppUsingHostHeader(restUrl, domainNamespace + ".org");
    assertTrue(checlReadyApp, "Failed to access WebLogic REST interface");

    Path archivePath = Paths.get(testWebAppWarLoc);
    String target = "{identity: [clusters,'" + clusterName + "']}";
    // Use WebLogic restful management services to deploy Web App
    ExecResult result = OKE_CLUSTER
        ? deployUsingRest(hostAndPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            target, archivePath, domainNamespace + ".org", "testwebapp")
        : deployToClusterUsingRest(host,
            String.valueOf(istioIngressPort),
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            clusterName, archivePath, domainNamespace + ".org", "testwebapp");

    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");
    logger.info("Application {0} deployed successfully at {1}", "testwebapp.war", domainUid + "-" + clusterName);

    if (OKE_CLUSTER) {
      testUntil(
          isAppInServerPodReady(domainNamespace,
              managedServerPrefix + 1, managedServerPort, "/testwebapp/index.jsp","testwebapp"),
          logger, "Check Deployed App {0} in server {1}",
          archivePath,
          target);
    } else {
      String url = "http://" + hostAndPort + "/testwebapp/index.jsp";
      logger.info("Application Access URL {0}", url);
      boolean checkApp = checkAppUsingHostHeader(url, domainNamespace + ".org");
      assertTrue(checkApp, "Failed to access WebLogic application");
    }
    logger.info("Application /testwebapp/index.jsp is accessble to {0}", domainUid);

    return istioIngressPort;
  }
}
