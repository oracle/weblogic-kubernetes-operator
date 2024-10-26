// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
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
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V2;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V3;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.MII_TWO_APP_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTPS_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageRepoLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleAllClustersInDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appNotAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainResourceImagePatched;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podImagePatched;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminConsoleAccessible;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isAppInServerPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withQuickRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTargetPortForRoute;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain and verify the domain started successfully
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create model in image domain and start the domain")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
@Tag("oke-weekly-sequential")
class ItMiiDomain {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainNamespace1 = null;

  private String domainUid = "domain1";
  private String domainUid1 = "domain2";
  private String miiImagePatchAppV2 = null;
  private String miiImageAddSecondApp = null;
  private static LoggingFacade logger = null;
  private static volatile boolean mainThreadDone = false;
  private static String miiDomainNegativeNamespace = null;
  private String encryptionSecretName = "encryptionsecret";
  private AppParams appParams = defaultAppParams().appArchiveDir(ARCHIVE_DIR + this.getClass().getSimpleName());

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace1 = namespaces.get(2);
    assertNotNull(namespaces.get(3), "Namespace list is null");
    miiDomainNegativeNamespace = namespaces.get(3);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace, domainNamespace1, miiDomainNegativeNamespace);
  }

  /**
   * Create a WebLogic domain with SSL enabled in WebLogic configuration by
   * configuring an additional configmap to the domain resource.
   * Add two channels to the domain resource with name `default-secure` and `default`.
   * Make sure the pre-packaged application in domain image gets deployed to
   * the cluster and accessible from all the managed server pods
   * Make sure two external NodePort services are created in domain namespace.
   * Make sure ready app is accessible through both
   *   `default-secure` service and `default` service.
   *
   * Negative test case for when domain resource attribute domain.spec.adminServer.adminChannelPortForwardingEnabled
   * is set to false, the WLS admin console can not be accessed using the forwarded port, like
   * http://localhost:localPort/weblogic/ready.
   */
  @Test
  @Order(1)
  @DisplayName("Create model in image domain and verify external admin services")
  void testCreateMiiDomain() {
    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;
    final String hostName = "localhost";
    final int adminServerPort = 7001;
    final int adminServerSecurePort = 7008;
    String httpHostHeader = "";
    String httpsHostHeader = "";

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

    String configMapName = "default-secure-configmap";
    String yamlString = "topology:\n"
        + "  Server:\n"
        + "    'admin-server':\n"
        + "       SSL: \n"
        + "         Enabled: true \n"
        + "         ListenPort: '" + adminServerSecurePort + "' \n";
    createModelConfigMap(configMapName, yamlString, domainUid);

    // create cluster object
    String clusterName = "cluster-1";

    // create the domain object
    DomainResource domain = createDomainResourceWithConfigMap(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);

    domain = createClusterResourceAndAddReferenceToDomain(
        domainUid + "-" + clusterName, clusterName, domainNamespace, domain, replicaCount);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // Need to expose the admin server external service to access the console in OKD cluster only
    // We will create one route for sslport and another for default port
    String adminSvcSslPortExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName),
                    domainNamespace, "domain1-admin-server-sslport-ext");
    setTlsTerminationForRoute("domain1-admin-server-sslport-ext", domainNamespace);
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);

    // check and wait for the application to be accessible in all server pods
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V1 + i);
    }

    logger.info("All the servers in Domain {0} are running and application is available", domainUid);

    int sslNodePort = getServiceNodePort(
         domainNamespace, getExternalServicePodName(adminServerPodName), "default-secure");
    // In OKD cluster, we need to set the target port of the route to be the ssl port
    // By default, when a service is exposed as a route, the endpoint is set to the default port.
    int sslPort = getServicePort(
         domainNamespace, getExternalServicePodName(adminServerPodName), "default-secure");
    setTargetPortForRoute("domain1-admin-server-sslport-ext", domainNamespace, sslPort);
    assertNotEquals(-1, sslNodePort,
          "Could not get the default-secure external service node port");
    logger.info("Found the administration service nodePort {0}", sslNodePort);
    String hostAndPort = getHostAndPort(adminSvcSslPortExtHost, sslNodePort);

    // create ingress for admin service
    // use traefik LB for kind cluster with ingress host header in url
    String headers = "";
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      httpHostHeader = createIngressHostRouting(domainNamespace, domainUid,
          "admin-server", adminServerPort);
      httpsHostHeader = createIngressHostRouting(domainNamespace, domainUid,
          "admin-server", adminServerSecurePort);
      hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTPS_HOSTPORT;
      headers = " -H 'host: " + httpsHostHeader + "' ";
    }

    final String resourcePath = "/weblogic/ready";
    if (OKE_CLUSTER) {
      testUntil(
          isAppInServerPodReady(domainNamespace,
             adminServerPodName, 7001, resourcePath, ""),
          logger, "verify EM console access {0} in server {1}",
          resourcePath,
          adminServerPodName);
    } else {
      String curlCmd = "curl -skg --show-error --noproxy '*' "
          + headers + " https://" + hostAndPort
          + "/weblogic/ready --write-out %{http_code} -o /dev/null";
      logger.info("Executing default-admin nodeport curl command {0}", curlCmd);
      assertTrue(callWebAppAndWaitTillReady(curlCmd, 10));
      logger.info("ready app is accessible thru default-secure service");
    }

    int nodePort = getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, nodePort,
        "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      headers = " -H 'host: " + httpHostHeader + "' ";
    }
    if (OKE_CLUSTER) {
      testUntil(
          isAppInServerPodReady(domainNamespace,
              adminServerPodName, 7001, resourcePath, ""),
          logger, "verify EM console access {0} in server {1}",
          resourcePath,
          adminServerPodName);
    } else {
      String curlCmd2 = "curl -skg --show-error --noproxy '*' "
          + headers + " http://" + hostAndPort
          + "/weblogic/ready --write-out %{http_code} -o /dev/null";
      logger.info("Executing default nodeport curl command {0}", curlCmd2);
      assertTrue(callWebAppAndWaitTillReady(curlCmd2, 5));
    }
    logger.info("ready app is accessible thru default service");


    // Test that `kubectl port-foward` is able to forward a local port to default channel port (7001 in this test)
    // and default secure channel port (7002 in this test)
    // Verify that the WLS admin console can not be accessed using http://localhost:localPort/weblogic/ready
    String forwardedPortNo = startPortForwardProcess(hostName, domainNamespace, domainUid, adminServerPort);
    verifyAdminConsoleAccessible(domainNamespace, hostName, forwardedPortNo, false, Boolean.FALSE);

    forwardedPortNo = startPortForwardProcess(hostName, domainNamespace, domainUid, adminServerSecurePort);
    verifyAdminConsoleAccessible(domainNamespace, hostName, forwardedPortNo, true, Boolean.FALSE);

    stopPortForwardProcess(domainNamespace);
  }

  @Test
  @Order(2)
  @DisplayName("Create a second domain with the image from the the first test")
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  void testCreateMiiSecondDomainDiffNSSameImage() {
    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid1 + "-admin-server";
    final String managedServerPrefix = domainUid1 + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace1);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace1,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace1,
            "weblogicenc", "weblogicenc");

    String clusterName = "cluster-1";
    // create the domain object
    DomainResource domain = createDomainResource(domainUid1,
                domainNamespace1,
                adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
                encryptionSecretName,
                replicaCount,
                MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);

    // set low introspectorJobActiveDeadlineSeconds and verify introspector retries on timeouts
    domain.getSpec().configuration().introspectorJobActiveDeadlineSeconds(30L);

    domain = createClusterResourceAndAddReferenceToDomain(domainUid1 + "-" + clusterName,
        clusterName, domainNamespace1, domain, replicaCount);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid1, domainNamespace1, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace1);

    // check admin server pod is ready
    // as low value is used for introspectorJobActiveDeadlineSeconds, wait longer for services and pods to come
    // to give enough time for make-right intervals and retries
    logger.info("Check admin service {0} is created in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodReadyAndServiceExists(withLongRetryPolicy, adminServerPodName, domainUid1, domainNamespace1);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodReadyAndServiceExists(withLongRetryPolicy, managedServerPrefix + i, domainUid1, domainNamespace1);
    }

    // check operator pod log contains message for introspectorJobActiveDeadlineSeconds as a low value is used
    String operatorPodName = assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    checkPodLogContainsString(opNamespace, operatorPodName,
        "introspectorJobActiveDeadlineSeconds");
  }

  @Test
  @Order(3)
  @DisplayName("Update the sample-app application to version 2")
  void testPatchAppV2() {

    // application in the new image contains what is in the original application directory sample-app,
    // plus the replacements or/and additions in the second application directory sample-app-2.
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    List<Integer> appAvailability = new ArrayList<Integer>();

    logger.info("Start a thread to keep track of the application's availability");
    // start a new thread to collect the availability data of the application while the
    // main thread performs patching operation, and checking of the results.
    Thread accountingThread =
        new Thread(
            () -> {
              collectAppAvailability(
                  domainNamespace,
                  appAvailability,
                  managedServerPrefix,
                  replicaCount,
                  "8001",
                  "sample-war/index.jsp");
            });
    accountingThread.start();

    try {
      logger.info("Check that V1 application is still running");
      for (int i = 1; i <= replicaCount; i++) {
        quickCheckAppRunning(
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            MII_APP_RESPONSE_V1 + i);
      }

      logger.info("Check that the version 2 application is NOT running");
      for (int i = 1; i <= replicaCount; i++) {
        quickCheckAppNotRunning(
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            MII_APP_RESPONSE_V2 + i);
      }

      logger.info("Create a new image with application V2");
      miiImagePatchAppV2 = updateImageWithAppV2Patch(
          String.format("%s-%s", MII_BASIC_IMAGE_NAME, "test-patch-app-v2"),
          Arrays.asList(appDir1, appDir2));

      // push the image to a registry to make the test work in multi node cluster
      pushImageIfNeeded(miiImagePatchAppV2);

      // patch the domain resource with the new image and verify that the domain resource is patched,
      // and all server pods are patched as well.
      logger.info("Patch domain resource with image {0}, and verify the results", miiImagePatchAppV2);
      patchAndVerify(
          domainUid,
          domainNamespace,
          adminServerPodName,
          managedServerPrefix,
          replicaCount,
          miiImagePatchAppV2);

      logger.info("Check and wait for the V2 application to become available");
      for (int i = 1; i <= replicaCount; i++) {
        checkAppRunning(
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            MII_APP_RESPONSE_V2 + i);
      }
    } finally {
      mainThreadDone = true;
      if (accountingThread != null) {
        try {
          accountingThread.join();
        } catch (InterruptedException ie) {
          // do nothing
        }

        // check the application availability data that we have collected, and see if
        // the application has been available all the time since the beginning of this test method
        logger.info("Verify that V2 application was available when domain {0} was being patched with image {1}",
            domainUid, miiImagePatchAppV2);
        assertTrue(appAlwaysAvailable(appAvailability),
            String.format("Application V2 was not always available when domain %s was being patched with image %s",
                domainUid, miiImagePatchAppV2));
      }
    }

    logger.info("The version 2 application has been deployed correctly on all server pods");
  }

  @Test
  @Order(4)
  @DisplayName("Update the domain with another application")
  void testAddSecondApp() {

    // the existing application is the combination of what are in appDir1 and appDir2 as in test case number 4,
    // the second application is in appDir3.
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    final String appDir3 = "sample-app-3";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    logger.info("Check that V2 application is still running after the previous test");
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V2 + i);
    }

    logger.info("Check that the new application is NOT already running");
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppNotRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war-3/index.jsp",
          MII_APP_RESPONSE_V3 + i);
    }

    logger.info("Create a new image that contains the additional application");
    miiImageAddSecondApp = updateImageWithSampleApp3(
        String.format("%s-%s", MII_BASIC_IMAGE_NAME, "test-add-second-app"),
        Arrays.asList(appDir1, appDir2),
        Collections.singletonList(appDir3),
        MII_TWO_APP_WDT_MODEL_FILE);

    // push the image to a registry to make the test work in multi node cluster
    pushImageIfNeeded(miiImageAddSecondApp);

    // patch the domain resource with the new image and verify that the domain resource is patched,
    // and all server pods are patched as well.
    logger.info("Patch the domain with image {0}, and verify the results", miiImageAddSecondApp);
    patchAndVerify(
        domainUid,
        domainNamespace,
        adminServerPodName,
        managedServerPrefix,
        replicaCount,
        miiImageAddSecondApp);

    logger.info("Check and wait for the new application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war-3/index.jsp",
          MII_APP_RESPONSE_V3 + i);
    }

    logger.info("Check and wait for the original application V2 to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V2 + i);
    }
    logger.info("Both of the applications are running correctly after patching");
  }

  @Test
  @Order(5)
  @DisplayName("Check admin service annotations and labels")
  void testAdminServiceAnnotationsLabels() {
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertTrue(
        domain1.getSpec().getAdminServer().getServerService().getAnnotations().containsKey("testkey"),
        "Missing expected annotation on admin service");
    assertTrue(
        domain1.getSpec().getAdminServer().getServerService().getLabels().containsKey("testkey"),
        "Missing expected label on admin service");
  }

  /** The default replicas at domain resource level is 1. Patching domain1 with new replicas 3.
   * Verify managed server pods count at cluster resource level are not affected by domain resource
   * level patching.
   * Replicas count at cluster resource level is defined as 2.
   * Scale up the cluster by patching replicas count to 5 at cluster resource level.
   * Scale down the cluster by patching replicas count to 1 at cluster resource level.
   * Verify cluster resource level scaling is successful even though replicas count at domain resource
   * level is specified as 3
   */
  @Test
  @Order(6)
  @DisplayName("Verify scaling by patching replicas at domain resource and cluster resource level")
  void testClusterScale() {
    final int replicaCount = 2;
    String clusterName = domainUid + "-" + "cluster-1";
    final String managedServerPrefix = domainUid + "-managed-server";

    //patch replicas at domain resource level to 3
    boolean scalingSuccess = scaleAllClustersInDomain(domainUid, domainNamespace, 3);
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, domainNamespace));

    //managed server pods at cluster resource level should not be affected by domain resource level patching
    //verify at cluster resource level there are only 2 managed server pods exist and running
    //verify managedServer3 doesn't exist
    logger.info("Check dynamic managed server pods are not affected");
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(clusterName,
        domainNamespace, replicaCount)));
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    //verify managedServer3 doesn't exist
    checkPodDoesNotExist(managedServerPrefix + 3, domainUid, domainNamespace);


    //scaling by patching replicas at cluster resource level should scale up/down
    //the cluster even the domain resource has replicas defined
    logger.info("Updating the cluster {0} replica count to 5", clusterName);
    boolean p1Success = scaleCluster(clusterName, domainNamespace,5);
    assertTrue(p1Success,
        String.format("Patching replica to 5 failed for cluster %s in namespace %s",
            clusterName, domainNamespace));
    //verify 5 managed servers are up and running
    for (int i = 1; i <= 5; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    logger.info("Updating the cluster {0} replica count to 1", clusterName);
    p1Success = scaleCluster(clusterName, domainNamespace,1);
    assertTrue(p1Success,
        String.format("Patching replica to 1 failed for cluster %s in namespace %s",
            clusterName, domainNamespace));
    //verify only one managed server is up and running
    for (int i = 5; i >= 2; i--) {
      logger.info("Wait for managed server pod {0} to be shutdown in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodReadyAndServiceExists(managedServerPrefix + 1, domainUid, domainNamespace);
  }

  /**
   * Negative test case for creating a model-in-image domain without encryption secret created.
   * The admin server service/pod will not be created.
   * Verify the error message should be logged in the operator log.
   */
  @Test
  @Order(7)
  @DisplayName("verify the operator log has expected error msg when encryption secret not created for a mii domain")
  void testOperatorLogSevereMsg() {
    String domainUid = "miidomainnegative";
    createMiiDomainNegative(domainUid, miiDomainNegativeNamespace);

    // verify the error msg is logged in the operator log
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    checkPodLogContainsString(opNamespace, operatorPodName,
        "Domain miidomainnegative is not valid: RuntimeEncryption secret '" + encryptionSecretName
            + "' not found in namespace '" + miiDomainNegativeNamespace + "'");

    // verify the admin server is not created
    checkPodDoesNotExist(domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainUid, miiDomainNegativeNamespace);
  }

  @AfterAll
  public void tearDownAll() {
    // delete the domain images created in the test class
    if (miiImagePatchAppV2 != null) {
      deleteImage(miiImagePatchAppV2);
    }
    if (miiImageAddSecondApp != null) {
      deleteImage(miiImageAddSecondApp);
    }
  }

  private void pushImageIfNeeded(String image) {
    // push the image to a registry to make the test work in multi node cluster
    logger.info(WLSIMG_BUILDER + " login to registry {0}", TEST_IMAGES_REPO);
    assertTrue(imageRepoLogin(TEST_IMAGES_REPO, TEST_IMAGES_REPO_USERNAME, 
                TEST_IMAGES_REPO_PASSWORD), WLSIMG_BUILDER + " login failed");
    // push image
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info(WLSIMG_BUILDER + " push image {0} to registry", image);
      assertTrue(imagePush(image), String.format(WLSIMG_BUILDER + " push failed for image %s", image));
    }
  }

  private String createUniqueImageTag() {
    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    return dateFormat.format(date) + "-" + System.currentTimeMillis();
  }

  private String updateImageWithAppV2Patch(
      String imageName,
      List<String> appDirList
  ) {
    logger.info("Build the model file list that contains {0}", MII_BASIC_WDT_MODEL_FILE);
    List<String> modelList =
        Collections.singletonList(String.format("%s/%s", MODEL_DIR, MII_BASIC_WDT_MODEL_FILE));

    logger.info("Build an application archive using what is in {0}", appDirList);

    assertTrue(
        buildAppArchive(appParams.srcDirList(appDirList)),
        String.format("Failed to create application archive for %s", MII_BASIC_APP_NAME));

    logger.info("Build the archive list that contains {0}",
        String.format("%s/%s.zip", appParams.appArchiveDir(), MII_BASIC_APP_NAME));
    List<String> archiveList =
        Collections.singletonList(
            String.format("%s/%s.zip", appParams.appArchiveDir(), MII_BASIC_APP_NAME));

    return createImageAndVerify(
      imageName,
      createUniqueImageTag(),
      modelList,
      archiveList);
  }

  private String updateImageWithSampleApp3(
      String imageName,
      List<String> appDirList1,
      List<String> appDirList2,
      String modelFile
  ) {
    logger.info("Build the model file list that contains {0}", modelFile);
    List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);

    String appName1 = appDirList1.get(0);
    String appName2 = appDirList2.get(0);

    logger.info("Build the first application archive using what is in {0}", appDirList1);
    assertTrue(
        buildAppArchive(
            appParams
                .srcDirList(appDirList1)
                .appName(appName1)),
        String.format("Failed to create application archive for %s",
            appName1));

    logger.info("Build the second application archive usingt what is in {0}", appDirList2);
    assertTrue(
        buildAppArchive(
            appParams
                .srcDirList(appDirList2)
                .appName(appName2)),
        String.format("Failed to create application archive for %s",
            appName2));

    logger.info("Build the archive list with two zip files: {0} and {1}",
        String.format("%s/%s.zip", appParams.appArchiveDir(), appName1),
        String.format("%s/%s.zip", appParams.appArchiveDir(), appName2));
    List<String> archiveList = Arrays.asList(
        String.format("%s/%s.zip", appParams.appArchiveDir(), appName1),
        String.format("%s/%s.zip", appParams.appArchiveDir(), appName2));

    return createImageAndVerify(
      imageName,
      createUniqueImageTag(),
      modelList,
      archiveList);
  }

  /**
   * Patch the domain resource with a new image.
   * Here is an example of the JSON patch string that is constructed in this method.
   * [
   *   {"op": "replace", "path": "/spec/image", "value": "mii-image:v2" }
   * ]
   *
   * @param domainResourceName name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param image name of the new image
   */
  private void patchDomainResourceImage(
      String domainResourceName,
      String namespace,
      String image
  ) {
    String patch =
        String.format("[\n  {\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"%s\"}\n]\n",
            image);
    logger.info("About to patch the domain resource {0} in namespace {1} with:{2}\n",
        domainResourceName, namespace, patch);

    assertTrue(patchDomainCustomResource(
            domainResourceName,
            namespace,
            new V1Patch(patch),
            V1Patch.PATCH_FORMAT_JSON_PATCH),
        String.format("Failed to patch the domain resource %s in namespace %s with image %s",
            domainResourceName, namespace, image));
  }

  private String createImageAndVerify(
      String imageName,
      String imageTag,
      List<String> modelList,
      List<String> archiveList
  ) {
    String image = String.format("%s:%s", imageName, imageTag);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
    // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
    // the next JDK 14 versions are released.
    if (WIT_JAVA_HOME != null) {
      env.put("JAVA_HOME", WIT_JAVA_HOME);
    }

    String witTarget = ((OKD) ? "OpenShift" : "Default");

    // build an image using WebLogic Image Tool
    logger.info("Create image {0} using model list {1} and archive list {2}",
        image, modelList, archiveList);
    boolean result = createImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtModelOnly(true)
            .wdtVersion(WDT_VERSION)
            .target(witTarget)
            .target(witTarget)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create image %s using WebLogic Image Tool", image));

    /* Check image exists using 'WLSIMG_BUILDER images | grep image tag'.
     * Tag name is unique as it contains date and timestamp.
     * This is a workaround for the issue on Jenkins machine
     * as 'WLSIMG_BUILDER images imagename:imagetag' is not working and
     * the test fails even though the image exists.
     */
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s doesn't exist", image));

    return image;
  }


  private DomainResource createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                              String repoSecretName, String encryptionSecretName, int replicaCount,
                                              String miiImage) {

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
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
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
                .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  // Create a domain resource with a custom ConfigMap
  private DomainResource createDomainResourceWithConfigMap(String domainUid,
                                                           String domNamespace, String adminSecretName,
                                                           String repoSecretName, String encryptionSecretName,
                                                           String miiImage, String configmapName) {

    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

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
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminChannelPortForwardingEnabled(false)
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap))
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default-secure")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  private void patchAndVerify(
      final String domainUid,
      final String namespace,
      final String adminServerPodName,
      final String managedServerPrefix,
      final int replicaCount,
      final String image
  ) {
    logger.info(
        "Patch the domain resource {0} in namespace {1} to use the new image {2}",
        domainUid, namespace, image);

    patchDomainResourceImage(domainUid, namespace, image);

    logger.info(
        "Check that domain resource {0} in namespace {1} has been patched with image {2}",
        domainUid, namespace, image);
    checkDomainPatched(domainUid, namespace, image);

    // check and wait for the admin server pod to be patched with the new image
    logger.info(
        "Check that admin server pod for domain resource {0} in namespace {1} has been patched with image {2}",
        domainUid, namespace, image);

    checkPodImagePatched(
        domainUid,
        namespace,
        adminServerPodName,
        image);

    // check and wait for the managed server pods to be patched with the new image
    logger.info(
        "Check that server pods for domain resource {0} in namespace {1} have been patched with image {2}",
        domainUid, namespace, image);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodImagePatched(
          domainUid,
          namespace,
          managedServerPrefix + i,
          image);
    }
  }

  private void checkAppRunning(
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {

    // check if the application is accessible inside of a server pod using standard retry policy
    checkAppIsRunning(withStandardRetryPolicy, namespace, podName, internalPort, appPath, expectedStr);
  }

  private void quickCheckAppRunning(
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {
    // check if the application is accessible inside of a server pod using quick retry policy
    checkAppIsRunning(withQuickRetryPolicy, namespace, podName, internalPort, appPath, expectedStr);
  }

  private void checkAppIsRunning(
      ConditionFactory conditionFactory,
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {

    // check if the application is accessible inside of a server pod
    testUntil(conditionFactory,
        () -> appAccessibleInPod(namespace, podName, internalPort, appPath, expectedStr),
        logger,
        "application {0} is running on pod {1} in namespace {2}",
        appPath,
        podName,
        namespace);
  }

  private void quickCheckAppNotRunning(
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {

    // check that the application is NOT running inside of a server pod
    testUntil(
        withQuickRetryPolicy, () -> appNotAccessibleInPod(
          namespace, podName, internalPort, appPath, expectedStr),
        logger,
        "Checking if application {0} is not running on pod {1} in namespace {2}",
        appPath,
        podName,
        namespace);
  }

  private void checkDomainPatched(
      String domainUid,
      String namespace,
      String image
  ) {

    // check if the domain resource has been patched with the given image
    testUntil(
        assertDoesNotThrow(
            () -> domainResourceImagePatched(domainUid, namespace, image),
              String.format("Domain %s is not patched in namespace %s with image %s", domainUid, namespace, image)),
        logger,
        "domain {0} to be patched in namespace {1}",
        domainUid,
        namespace);
  }

  private void checkPodImagePatched(
      String domainUid,
      String namespace,
      String podName,
      String image
  ) {
    // check if the server pod has been patched with the given image
    testUntil(
        assertDoesNotThrow(() -> podImagePatched(domainUid, namespace, podName, "weblogic-server", image),
          String.format(
            "Pod %s is not patched with image %s in namespace %s.",
            podName,
            image,
            namespace)),
        logger,
        "pod {0} to be patched in namespace {1}",
        podName,
        namespace);
  }

  private static void collectAppAvailability(
      String namespace,
      List<Integer> appAvailability,
      String managedServerPrefix,
      int replicaCount,
      String internalPort,
      String appPath
  ) {
    testUntil(
        with().pollDelay(2, SECONDS)
            .and().with().pollInterval(200, MILLISECONDS)
            .atMost(15, MINUTES).await(),
        assertDoesNotThrow(() -> checkContinuousAvailability(
            namespace, appAvailability, managedServerPrefix, replicaCount, internalPort, appPath),
        String.format(
            "App is not available on all managed servers in namespace %s.",
            namespace)),
        logger,
        "patched application running on all managed servers in namespace {0}",
        namespace);
  }

  private static Callable<Boolean> checkContinuousAvailability(
      String namespace,
      List<Integer> appAvailability,
      String managedServerPrefix,
      int replicaCount,
      String internalPort,
      String appPath) {
    return () -> {
      boolean v2AppAvailable = true;

      for (int i = 1; i <= replicaCount; i++) {
        v2AppAvailable = v2AppAvailable && appAccessibleInPod(
                            namespace,
                            managedServerPrefix + i,
                            internalPort,
                            appPath,
                            MII_APP_RESPONSE_V2 + i);
      }

      int count = 0;
      for (int i = 1; i <= replicaCount; i++) {
        if (appAccessibleInPod(
            namespace,
            managedServerPrefix + i,
            internalPort,
            appPath,
            "Hello World")) {
          count++;
        }
      }
      appAvailability.add(count);

      if (count == 0) {
        logger.info("NNNNNNNNNNN: application not available NNNNNNNN");
        return true;
      }

      logger.fine("YYYYYYYYYYY: application available YYYYYYYY count = " + count);
      return v2AppAvailable || mainThreadDone;
    };
  }

  private static boolean appAlwaysAvailable(List<Integer> appAvailability) {
    for (Integer count: appAvailability) {
      if (count == 0) {
        logger.warning("Application was not available during patching.");
        return false;
      }
    }
    return true;
  }

  // create a ConfigMap with a model that enable SSL on the Administration server
  private static void createModelConfigMap(String configMapName, String model, String domainUid) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    Map<String, String> data = new HashMap<>();
    data.put("model.ssl.yaml", model);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(configMapName)
            .namespace(domainNamespace));

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", configMapName));
  }

  /**
   * Negative test case for creating a model-in-image domain without encryption secret created.
   * The admin server service/pod will not be created.
   * The error message should be logged in the operator log.
   *
   * @param domainUid the uid of the domain to be created
   * @param domainNamespace namespace in which the domain will be created
   */
  private void createMiiDomainNegative(String domainUid, String domainNamespace) {

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR without encryption secret created
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/u01/" + domainNamespace + "/domains/" + domainUid)
            .domainHomeSourceType("FromModel")
            .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
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
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    setPodAntiAffinity(domain);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);
  }
}
