// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.annotations.DisabledOnSlimImage;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.DomainUtils;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.INGRESS_CLASS_FILE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_EXTERNAL_REST_HTTPSPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesDomainExist;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.exeAppInServerPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.getRouteHost;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates WebLogic domains with the models, domain-on-pv (verify the cluster using WLDF policy)
 * Verify the basic lifecycle operations of the WebLogic server pods by scaling the domain
 * Also verify the sample application can be accessed via NGINX ingress controller.
 */
@DisplayName("Verify scaling the clusters in the domain with the domain type, domain-on-pv"
    + "and the sample application can be accessed via NGINX ingress controller")
@Tag("kind-parallel")
@Tag("oke-gate")
@IntegrationTest
class ItMultiDomainModelsScaleWithWLDFDomainOnPV {

  // domain constants
  private static final String adminServerName = "admin-server";
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int ADMIN_SERVER_PORT = 7001;
  private static final int replicaCount = 1;
  private static final String SAMPLE_APP_CONTEXT_ROOT = "sample-war";
  private static final String WLDF_OPENSESSION_APP = "opensessionapp";
  private static final String WLDF_OPENSESSION_APP_CONTEXT_ROOT = "opensession";
  private static final String wlSecretName = "weblogic-credentials";
  private static final String miiImageName = "mdlb-mii-image";
  private static final String wdtModelFileForMiiDomain = "model-multiclusterdomain-sampleapp-wls.yaml";
  private static final String domainOnPVUid = "mdlb-domainonpv";

  private static String opNamespace = null;
  private static String opServiceAccount = null;
  private static NginxParams nginxHelmParams = null;
  private static String nginxNamespace = null;
  private static int nodeportshttp = 0;
  private static LoggingFacade logger = null;
  private static String domainOnPVNamespace = null;
  private static String miiImage = null;
  private String curlCmd = null;
  private static String hostHeader = null;

  /**
   * Install operator and NGINX.
   * Create domain type: domain in image and ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    nginxNamespace = namespaces.get(1);

    // get unique namespaces for domains
    logger.info("Getting unique namespaces for domains");
    assertNotNull(namespaces.get(2));
    domainOnPVNamespace = namespaces.get(2);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // create mii image
    miiImage = createAndPushMiiImage();

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true,
        OPERATOR_EXTERNAL_REST_HTTPSPORT, domainOnPVNamespace);

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    createRouteForOKD("external-weblogic-operator-svc", opNamespace);

    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    if (!OKD) {
      if (WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        // install and verify NGINX
        nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
        String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
        logger.info("NGINX service name: {0}", nginxServiceName);
        nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
        logger.info("NGINX http node port: {0}", nodeportshttp);
      } else {
        // if not using docker, use pre-installed Traefik controller
        nodeportshttp = TRAEFIK_INGRESS_HTTP_HOSTPORT;
      }
    }
  }

  /**
   * Scale cluster using WLDF policy for domain type domain-on-pv.
   */
  @Test
  @DisplayName("scale cluster using WLDF policy for domain type, domainOnPV")
  @DisabledOnSlimImage
  void testScaleClustersWithWLDF() {

    DomainResource domain = createOrStartDomainBasedOnDomainType();

    // get domain properties
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();
    String domainHome = domain.getSpec().getDomainHome();
    int numClusters = domain.getSpec().getClusters().size();
    String clusterName = domain.getSpec().getClusters().get(0).getName();

    String managedServerPodNamePrefix = generateMsPodNamePrefix(numClusters, domainUid, clusterName);

    curlCmd = generateCurlCmd(domainUid, domainNamespace, clusterName, SAMPLE_APP_CONTEXT_ROOT);
    logger.info("Generated curlCmd = {0}", curlCmd);

    // scale up the cluster by 1 server
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, replicaCount, replicaCount + 1);
    List<String> managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, replicaCount);
    String curlCmdForWLDFScript =
        generateCurlCmd(domainUid, domainNamespace, clusterName, WLDF_OPENSESSION_APP_CONTEXT_ROOT);
    logger.info("Generated curlCmdForWLDFScript = {0}", curlCmdForWLDFScript);

    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        replicaCount, replicaCount + 1, false, OPERATOR_EXTERNAL_REST_HTTPSPORT, opNamespace, opServiceAccount,
        true, domainHome, "scaleUp", 1,
        WLDF_OPENSESSION_APP, curlCmdForWLDFScript, curlCmd, managedServersBeforeScale);

    // scale down the cluster by 1 server
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, replicaCount + 1, replicaCount);
    managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, replicaCount + 1);

    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        replicaCount + 1, replicaCount, false, 0, opNamespace, opServiceAccount,
        true, domainHome, "scaleDown", 1,
        WLDF_OPENSESSION_APP, curlCmdForWLDFScript, curlCmd, managedServersBeforeScale);

    // verify admin console login
    if (OKE_CLUSTER) {
      String resourcePath = "/weblogic/ready";
      final String adminServerPodName = domainUid + "-admin-server";
      ExecResult result = exeAppInServerPod(domainNamespace, adminServerPodName,7002, resourcePath);
      logger.info("result in OKE_CLUSTER is {0}", result.toString());
      assertEquals(0, result.exitValue(), "Failed to access WebLogic readyapp");

      // verify admin console login using ingress controller
      verifyReadyAppUsingIngressController(domainUid, domainNamespace);
    } else if (!WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRoutingIfNotExists(domainNamespace, domainUid);
      assertDoesNotThrow(()
          -> verifyAdminServerRESTAccess("localhost", TRAEFIK_INGRESS_HTTP_HOSTPORT, false, hostHeader));
    } else {
      verifyReadyAppUsingAdminNodePort(domainUid, domainNamespace);
      // verify admin console login using ingress controller
      verifyReadyAppUsingIngressController(domainUid, domainNamespace);
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
  }

  /**
   * Create a domain in PV using WDT.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static DomainResource createDomainOnPvUsingWdt(String domainUid, String domainNamespace) {

    final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String clusterName = "dopcluster-1";
    DomainResource domain = DomainUtils.createDomainOnPvUsingWdt(domainUid, domainNamespace, wlSecretName,
        clusterName,
        replicaCount, ItMultiDomainModelsScale.class.getSimpleName());

    // build application sample-app and opensessionapp
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);

    for (String appName : appSrcDirList) {
      AppParams appParams = defaultAppParams()
          .srcDirList(Collections.singletonList(appName))
          .appArchiveDir(ARCHIVE_DIR + ItMultiDomainModelsScale.class.getSimpleName())
          .appName(appName);

      assertTrue(buildAppArchive(appParams),
          String.format("Failed to create app archive for %s", appName));

      logger.info("Getting port for default channel");
      int defaultChannelPort = assertDoesNotThrow(()
              -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server default port failed");
      logger.info("default channel port: {0}", defaultChannelPort);
      assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

      //deploy application
      Path archivePath = get(appParams.appArchiveDir(), "wlsdeploy", "applications", appName + ".ear");
      logger.info("Deploying webapp {0} to domain {1}", archivePath, domainUid);
      deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + ADMIN_SERVER_NAME_BASE, archivePath,
          domainNamespace);
    }

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;

      // check managed server pod is ready and service exists in the namespace
      logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

    return domain;
  }

  /**
   * Generate the curl command to access the sample app from the ingress controller.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace the namespace in which the domain exists
   * @param clusterName WebLogic cluster name which is the backend of the ingress
   * @param appContextRoot the context root of the application
   * @return curl command string
   */
  private static String generateCurlCmd(String domainUid,
                                        String domainNamespace,
                                        String clusterName,
                                        String appContextRoot) {
    if (OKD) {
      String routeHost = getRouteHost(domainNamespace, domainUid + "-cluster-" + clusterName);
      logger.info("routeHost = {0}", routeHost);
      return String.format("curl -g -v --show-error --noproxy '*' http://%s/%s/index.jsp", routeHost, appContextRoot);
    } else {
      String host = K8S_NODEPORT_HOST;
      if (host.contains(":")) {
        host = "[" + host + "]";
      }
      if (OKE_CLUSTER) {
        String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";

        return String.format("curl -g -v --show-error --noproxy '*' -H 'host: %s' http://%s/%s/index.jsp",
            domainUid + "." + domainNamespace + "." + clusterName + ".test",
            getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace), appContextRoot);
      } else {
        return String.format("curl -g -v --show-error --noproxy '*' -H 'host: %s' http://%s/%s/index.jsp",
            domainUid + "." + domainNamespace + "." + clusterName + ".test",
            getHostAndPort(host, nodeportshttp), appContextRoot);
      }
    }
  }

  /**
   * Generate a server list which contains all managed servers in the cluster before scale.
   *
   * @param numClusters         number of clusters in the domain
   * @param clusterName         the name of the WebLogic cluster
   * @param replicasBeforeScale the replicas of WebLogic cluster before scale
   * @return list of managed servers in the cluster before scale
   */
  private static List<String> listManagedServersBeforeScale(int numClusters, String clusterName,
                                                            int replicasBeforeScale) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      if (numClusters <= 1) {
        managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
      } else {
        managedServerNames.add(clusterName + "-" + MANAGED_SERVER_NAME_BASE + i);
      }
    }

    return managedServerNames;
  }

  /**
   * Assert the specified domain and domain spec, metadata and clusters not null.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private static void assertDomainNotNull(DomainResource domain) {
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), domain + " spec is null");
    assertNotNull(domain.getMetadata(), domain + " metadata is null");
    assertNotNull(domain.getSpec().getClusters(), domain.getSpec() + " getClusters() is null");
  }

  /**
   * Generate the managed server pod name prefix.
   *
   * @param numClusters number of clusters in the domain
   * @param domainUid   uid of the domain
   * @param clusterName the cluster name of the domain
   * @return prefix of managed server pod name
   */
  private String generateMsPodNamePrefix(int numClusters, String domainUid, String clusterName) {
    String managedServerPodNamePrefix;
    if (numClusters <= 1) {
      managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    } else {
      managedServerPodNamePrefix = domainUid + "-" + clusterName + "-" + MANAGED_SERVER_NAME_BASE;
    }

    return managedServerPodNamePrefix;
  }

  /**
   * Create mii image and push it to the registry.
   *
   * @return mii image created
   */
  private static String createAndPushMiiImage() {
    // create image with model files
    logger.info("Creating image with model file {0} and verify", wdtModelFileForMiiDomain);
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);
    miiImage =
        createMiiImageAndVerify(miiImageName, Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForMiiDomain),
            appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    return miiImage;
  }

  private static DomainResource createOrStartDomainBasedOnDomainType() {
    DomainResource domain = null;

    if (!doesDomainExist(domainOnPVUid, DOMAIN_VERSION, domainOnPVNamespace)) {
      // create domain-on-pv domain
      domain = createDomainOnPvUsingWdt(domainOnPVUid, domainOnPVNamespace);

      // create route for OKD or ingress for the domain
      createRouteForOKDOrIngressForDomain(domain);
    } else {
      domain = assertDoesNotThrow(() -> getDomainCustomResource(domainOnPVUid, domainOnPVNamespace));
      startDomainAndVerify(domainOnPVNamespace, domainOnPVUid, replicaCount, domain.getSpec().getClusters().size());
    }

    assertDomainNotNull(domain);

    return domain;
  }

  private static void createRouteForOKDOrIngressForDomain(DomainResource domain) {

    assertDomainNotNull(domain);
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();

    //create route for external admin service
    createRouteForOKD(domainUid + "-admin-server-ext", domainNamespace);

    // create ingress using host based routing
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    int numClusters = domain.getSpec().getClusters().size();
    for (int i = 1; i <= numClusters; i++) {
      String clusterName = domain.getSpec().getClusters().get(i - 1).getName();
      logger.info("DEBUG: get clusterName = {0}", clusterName);
      clusterNameMsPortMap.put(clusterName, MANAGED_SERVER_PORT);
      createRouteForOKD(domainUid + "-cluster-" + clusterName, domainNamespace);
    }

    if (!OKD) {
      logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
      if (OKE_CLUSTER) {
        createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap,
            false, nginxHelmParams.getIngressClassName(), false, 0);
      } else if (WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        createIngressForDomainAndVerify(domainUid, domainNamespace, nodeportshttp, clusterNameMsPortMap,
            true, nginxHelmParams.getIngressClassName(), true, ADMIN_SERVER_PORT);
      } else {
        assertDoesNotThrow(()
            -> createIngressForDomainAndVerify(domainUid, domainNamespace, TRAEFIK_INGRESS_HTTP_HOSTPORT,
            clusterNameMsPortMap, true, Files.readString(INGRESS_CLASS_FILE_NAME), true, ADMIN_SERVER_PORT));
      }
    }
  }

  /**
   * Start domain and verify all the server pods were started.
   *
   * @param domainNamespace the namespace where the domain exists
   * @param domainUid the uid of the domain to shutdown
   * @param replicaCount replica count of the domain cluster
   * @param numClusters number of clusters in the domain
   */
  private static void startDomainAndVerify(String domainNamespace,
                                           String domainUid,
                                           int replicaCount,
                                           int numClusters) {
    // start domain
    getLogger().info("Starting domain {0} in namespace {1}", domainUid, domainNamespace);
    startDomain(domainUid, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    getLogger().info("Checking that admin service/pod {0} exists in namespace {1}",
        domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainNamespace);
    checkPodReadyAndServiceExists(domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainUid, domainNamespace);

    String managedServerPodName;
    if (numClusters > 1) {
      for (int i = 1; i <= numClusters; i++) {
        for (int j = 1; j <= replicaCount; j++) {
          managedServerPodName = domainUid + "-cluster-" + i + "-" + MANAGED_SERVER_NAME_BASE + j;
          // check that ms service/pod exists in the domain namespace
          getLogger().info("Checking that clustered ms service/pod {0} exists in namespace {1}",
              managedServerPodName, domainNamespace);
          checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
        }
      }
    } else {
      for (int i = 1; i <= replicaCount; i++) {
        managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;

        // check that ms service/pod exists in the domain namespace
        getLogger().info("Checking that clustered ms service/pod {0} exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }
  }

  // verify the admin console login using admin node port
  private void verifyReadyAppUsingAdminNodePort(String domainUid, String domainNamespace) {

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    // In OKD cluster, we need to get the routeHost for the external admin service
    String routeHost = getRouteHost(domainNamespace, getExternalServicePodName(adminServerPodName));

    logger.info("Validating WebLogic readyapp");
    testUntil(
        assertDoesNotThrow(() -> {
          return adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, routeHost);
        }, "Access to admin server node port failed"),
        logger,
        "readyapp validation");
  }

  // Verify admin console login using ingress controller
  private void verifyReadyAppUsingIngressController(String domainUid, String domainNamespace) {

    if (!OKD) {
      if (OKE_CLUSTER) {
        final String adminServerPodName = domainUid + "-admin-server";
        String resourcePath = "/weblogic/ready";
        ExecResult result = exeAppInServerPod(domainNamespace, adminServerPodName, 7002, resourcePath);
        logger.info("result in OKE_CLUSTER is {0}", result.toString());
        assertEquals(0, result.exitValue(), "Failed to access WebLogic ready app");
      } else {
        String host = K8S_NODEPORT_HOST;
        if (host.contains(":")) {
          host = "[" + host + "]";
        }

        String curlCmd = "curl -g --silent --show-error --noproxy '*' -H 'host: "
            + domainUid + "." + domainNamespace + ".adminserver.test"
            + "' http://" + host + ":" + nodeportshttp
            + "/weblogic/ready --write-out %{http_code} -o /dev/null";

        logger.info("Executing curl command {0}", curlCmd);
        testUntil(() -> callWebAppAndWaitTillReady(curlCmd, 5),
            logger,
            "Ready app on domain {0} in namespace {1} is accessible",
            domainUid,
            domainNamespace);
      }

      logger.info("Ready app on domain1 is accessible");
    } else {
      logger.info("Skipping the admin console login test using ingress controller in OKD environment");
    }
  }

  private String  createIngressHostRoutingIfNotExists(String domainNamespace,
                                                      String domainUid) {
    String ingressName = domainNamespace + "-" + domainUid + "-" + adminServerName;
    String hostHeader = "";
    try {
      List<String> ingresses = listIngresses(domainNamespace);
      Optional<String> ingressFound = ingresses.stream().filter(ingress -> ingress.equals(ingressName)).findAny();
      if (ingressFound.isEmpty()) {
        hostHeader = createIngressHostRouting(domainNamespace, domainUid, adminServerName, 7001);
      }
    } catch (Exception ex) {
      logger.severe(ex.getMessage());
    }
    return hostHeader;
  }
}
