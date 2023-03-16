// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Collections;
import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomain;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceDomainConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainInImageUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainOnPvUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test creating MII domain with different AdminServerName and Server settings in WDT model file.
 * The test contains the following usecases:
 * 1) AdminServerName is not specified and no Server section in the model file.
 * 2) AdminServerName is not specified and no 'AdminServer' specified in Server section.
 *    There is a server 'myadmin' specified in the Server instead.
 * 3) AdminServerName is not specified and 'AdminServer' is specified in the Server section.
 * 4) AdminServerName is not specified and 'adminserver' (all lower case) is specified in the Server section.
 * 5) AdminServerName is specified and the named server is not set in the Server section.
 * 6) Configured cluster defined but no managed server associated with it in the Server section.
 * 7) Empty topology (no AdminServerName, Server and Cluster section)
 */
@DisplayName("Test creating MII domain with different AdminServerName and Server settings in WDT model file")
@IntegrationTest
@Tag("kind-parallel")
class ItWDTModelNoServer {

  // domain constants
  private static final String domainUid = "wdtmodelnoserver";
  private static final String MII_IMAGE_NAME = "wdtmodelnoserver-mii";
  private static final int replicaCount = 2;
  private static final String internalPort = "8001";
  private static final String appPath = "sample-war/index.jsp";
  private static final String adminServerPodName = domainUid + "-adminserver";
  private static final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static final String clusterName = "cluster-1";

  private static String domainNamespace = null;
  private static String opNamespace = null;
  private static LoggingFacade logger = null;
  private static String imageName = null;

  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {

    logger = getLogger();

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Test in the WDT model file there is no AdminServerName and no Server section defined.
   * Verify AdminServer Pod "domainUid-adminserver" is running and the defined WebLogic cluster is up and running.
   */
  @Test
  @DisplayName("Test in the WDT model file there is no AdminServerName and no Server section defined")
  void testWdtModelNoAdminServerNameNoServer() {

    String wdtModelFile = "model-noadminservername-noserver.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    // Verify the domain is created. The admin server pod "domainUid-adminserver" is up and running.
    // Also verify the cluster is up and running.
    createMiiDomainAndVerify(domainNamespace, domainUid,
        imageName, adminServerPodName, managedServerPrefix, replicaCount);

    // check the admin server name is 'AdminServer'
    checkAdminServerName(adminServerPodName, "AdminServer");

    //check and wait for the application to be accessible in all server pods
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = managedServerPrefix + j;
      String expectedStr = "Hello World, you have reached server " + MANAGED_SERVER_NAME_BASE + j;

      logger.info("Checking that application is running on managed server pod {0}  in namespace {1} with "
          + "expectedString {3}", managedServerPodName, domainNamespace, expectedStr);
      checkAppIsRunning(
          domainNamespace,
          managedServerPodName,
          expectedStr);
    }

    logger.info("Domain {0} is fully started in namespace {1} - servers are running and application is available",
        domainUid, domainNamespace);

    // delete domain and cluster
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    deleteDomainResource(domainNamespace, domainUid);
    deleteClusterCustomResource(clusterName, domainNamespace);
  }

  /**
   * Test in the WDT model file AdminServerName is not specified and in Server section there is a server 'myadmin' set.
   * Verify that AdminServerName is set to 'AdminServer' and the admin server pod with name domainuid-adminserver
   * is created. A managed server pod domainuid-myadmin is also created.
   */
  @Test
  @DisplayName("Test in the WDT model file there is no AdminServerName and in Server section myadmin defined")
  void testWdtModelNoAdminServerNameWithMyAdminInServer() {

    String wdtModelFile = "model-noadminservername-hasmyadminserver.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    // Verify the domain is created and the admin server pod "domainUid-adminserver" is up and running.
    // Also verify the defined cluster is up and running.
    createMiiDomainAndVerify(domainNamespace, domainUid,
        imageName, adminServerPodName, managedServerPrefix, replicaCount);

    // check the admin server name is 'AdminServer'
    checkAdminServerName(adminServerPodName, "AdminServer");

    // check there is also a pod with name domainUid-myadmin running
    checkPodReadyAndServiceExists(domainUid + "-myadmin", domainUid, domainNamespace);

    //check and wait for the application to be accessible in all server pods
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = managedServerPrefix + j;
      String expectedStr = "Hello World, you have reached server " + MANAGED_SERVER_NAME_BASE + j;

      logger.info("Checking that application is running on managed server pod {0}  in namespace {1} with "
          + "expectedString {3}", managedServerPodName, domainNamespace, expectedStr);
      checkAppIsRunning(
          domainNamespace,
          managedServerPodName,
          expectedStr);
    }

    logger.info("Domain {0} is fully started in namespace {1} - servers are running and application is available",
        domainUid, domainNamespace);

    // delete domain and cluster
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    deleteDomainResource(domainNamespace, domainUid);
    deleteClusterCustomResource(clusterName, domainNamespace);
  }

  /**
   * Test in the WDT model file there is no AdminServerName and in Server section the server 'AdminServer' defined.
   * Verify that AdminServerName is set to 'AdminServer' and the admin server pod is created.
   */
  @Test
  @DisplayName("Test in the WDT model file there is no AdminServerName and in Server section AdminServer defined")
  void testWdtModelNoAdminServerNameWithAdminServerInServer() {

    String wdtModelFile = "model-noadminservername-hasadminserver.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    // Verify the domain is created and the admin server pod "domainUid-adminserver" is up and running.
    // Also verify the defined cluster is up and running.
    createMiiDomainAndVerify(domainNamespace, domainUid,
        imageName, adminServerPodName, managedServerPrefix, replicaCount);

    // check the admin server name is 'AdminServer'
    checkAdminServerName(adminServerPodName, "AdminServer");

    //check and wait for the application to be accessible in all server pods
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = managedServerPrefix + j;
      String expectedStr = "Hello World, you have reached server " + MANAGED_SERVER_NAME_BASE + j;

      logger.info("Checking that application is running on managed server pod {0}  in namespace {1} with "
          + "expectedString {3}", managedServerPodName, domainNamespace, expectedStr);
      checkAppIsRunning(
          domainNamespace,
          managedServerPodName,
          expectedStr);
    }

    logger.info("Domain {0} is fully started in namespace {1} - servers are running and application is available",
        domainUid, domainNamespace);

    // delete domain and cluster
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    deleteDomainResource(domainNamespace, domainUid);
    deleteClusterCustomResource(clusterName, domainNamespace);
  }

  /**
   * Test in the WDT model file there is no AdminServerName and in Server 'adminserver' (all lower case) defined.
   * Note that the server name 'adminserver' is all lower case.
   * Disabled due to bug.
   */
  @Disabled
  @Test
  @DisplayName("Test in the WDT model file there is no AdminServerName and in Server section 'adminserver' defined")
  void testWdtModelNoAdminServerNameWithAdminServerLowercaseInServer() {

    String wdtModelFile = "model-noadminservername-hasadminserverlowercase.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    // Verify the domain is created and the admin server pod "domainUid-adminserver" is up and running.
    // Also verify the defined cluster is up and running.
    createMiiDomainAndVerify(domainNamespace, domainUid,
        imageName, adminServerPodName, managedServerPrefix, replicaCount);

    // check the admin server name is 'AdminServer'
    checkAdminServerName(adminServerPodName, "AdminServer");

    //check and wait for the application to be accessible in all server pods
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = managedServerPrefix + j;
      String expectedStr = "Hello World, you have reached server " + MANAGED_SERVER_NAME_BASE + j;

      logger.info("Checking that application is running on managed server pod {0}  in namespace {1} with "
          + "expectedString {3}", managedServerPodName, domainNamespace, expectedStr);
      checkAppIsRunning(
          domainNamespace,
          managedServerPodName,
          expectedStr);
    }

    logger.info("Domain {0} is fully started in namespace {1} - servers are running and application is available",
        domainUid, domainNamespace);

    // delete domain and cluster
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    deleteDomainResource(domainNamespace, domainUid);
    deleteClusterCustomResource(clusterName, domainNamespace);
  }

  /**
   * Test in the WDT model file there is AdminServerName set and the named server is not in Server section.
   * In the model file, the AdminServerName is set to 'new-admin-server'. In the Server section, there is no
   * 'new-admin-server' set. There is a server 'admin-server' set instead.
   * Verify WDT will create the named server 'new-admin-server' as in the AdminServerName.
   */
  @Test
  @DisplayName("Test in the WDT model file there is AdminServerName set and the named server is not in Server section")
  void testWdtModelAdminServerNameSetNamedServerNotInServer() {

    String wdtModelFile = "model-hasadminservername-namedservernotinserver.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);
    String namedAdminServerPodName = domainUid + "-new-admin-server";

    // Verify the domain is created and the admin server pod "domainUid-new-admin-server" is up and running.
    // Also verify the defined cluster is up and running.
    createMiiDomainAndVerify(domainNamespace, domainUid, imageName, namedAdminServerPodName,
        managedServerPrefix, replicaCount);

    // check the admin server name is 'new-admin-server'
    checkAdminServerName(namedAdminServerPodName, "new-admin-server");

    // check that there is another pod domainUid-admin-server running in 7001 since it is defined in Server section
    checkPodReadyAndServiceExists(domainUid + "-admin-server", domainUid, domainNamespace);

    //check and wait for the application to be accessible in all server pods
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = managedServerPrefix + j;
      String expectedStr = "Hello World, you have reached server " + MANAGED_SERVER_NAME_BASE + j;

      logger.info("Checking that application is running on managed server pod {0}  in namespace {1} with "
          + "expectedString {3}", managedServerPodName, domainNamespace, expectedStr);
      checkAppIsRunning(
          domainNamespace,
          managedServerPodName,
          expectedStr);
    }

    logger.info("Domain {0} is fully started in namespace {1} - servers are running and application is available",
        domainUid, domainNamespace);

    // delete domain and cluster
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    deleteDomainResource(domainNamespace, domainUid);
    deleteClusterCustomResource(clusterName, domainNamespace);
  }

  /**
   * Test in the WDT model file a configured cluster is defined but no managed server associated with it in Server.
   * Create a model-in-image type domain using this model file.
   * Verify the operator will generate validation error in the log.
   */
  @Test
  @DisplayName("Test in the WDT model file there is configured cluster set but no managed servers in Server section")
  void testWdtModelConfiguredClusterNoManagedServersInServerMII() {

    String wdtModelFile = "model-configuredcluster-noserver.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    createMiiDomain(
        domainNamespace,
        domainUid,
        imageName,
        replicaCount,
        Collections.singletonList(clusterName),
        false,
        null);

    // verify the error msg is logged in the operator log
    String expectedErrorMsg = "The WebLogic configured cluster \\\""
        + clusterName
        + "\\\" is not referenced by any servers.  You must have managed servers defined that belong to this cluster";
    String operatorPodName = assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // delete the domain and cluster
    deleteClusterCustomResource(clusterName, domainNamespace);
    deleteDomainResource(domainNamespace, domainUid);
  }

  /**
   * Test in the WDT model file a configured cluster is defined but no managed server associated with it in Server.
   * Create a domain-in-image type of domain using this WDT model file.
   * Verify the operator will generate validation error in the log.
   */
  @Test
  @DisplayName("Test in the WDT model file there is configured cluster set but no managed servers in Server section")
  void testWdtModelConfiguredClusterNoManagedServersInServerWithDII() {

    String wdtModelFile = "model-configuredcluster-noserver.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    String wlSecretName = "weblogic-credentials";
    createDomainInImageUsingWdt(domainUid, domainNamespace,
        wdtModelFile, null, null, wlSecretName, clusterName, replicaCount);

    // verify the error msg is logged in the operator log
    String expectedErrorMsg = "The WebLogic configured cluster \\\""
        + clusterName
        + "\\\" is not referenced by any servers.  You must have managed servers defined that belong to this cluster";
    String operatorPodName = assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // delete the domain and cluster
    deleteClusterCustomResource(clusterName, domainNamespace);
    deleteDomainResource(domainNamespace, domainUid);
  }

  /**
   * Test in the WDT model file a configured cluster is defined but no managed server associated with it in Server.
   * Create a domain-on-pv type of domain using this WDT model file.
   * Verify the operator will generate validation error in the log.
   */
  @Test
  @DisplayName("Test in the WDT model file there is configured cluster set but no managed servers in Server section")
  void testWdtModelConfiguredClusterNoManagedServersInServerWithDomainOnPV() {

    String wdtModelFile = "model-configuredcluster-noserver.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    String wlSecretName = "weblogic-credentials";
    createDomainOnPvUsingWdt(domainUid, domainNamespace, wlSecretName,
        clusterName, replicaCount, ItWDTModelNoServer.class.getSimpleName(), wdtModelFile, false);

    // verify the error msg is logged in the operator log
    String expectedErrorMsg = "The WebLogic configured cluster \\\""
        + clusterName
        + "\\\" is not referenced by any servers.  You must have managed servers defined that belong to this cluster";
    String operatorPodName = assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // delete the domain and cluster
    deleteClusterCustomResource(clusterName, domainNamespace);
    deleteDomainResource(domainNamespace, domainUid);
  }

  /**
   * Test in the WDT model file there is no AdminServerName, no Server and no Cluster section defined.
   * Verify AdminServer Pod "domainUid-adminserver" is running.
   * Verify there are no cluster service or managed server pods/services existing.
   */
  @Test
  @DisplayName("Test in the WDT model file there is no AdminServerName, no Server and no Cluster section defined")
  void testWdtModelNoAdminServerNameNoServerNoCluster() {

    String wdtModelFile = "model-emptytopology.yaml";
    imageName = createAndVerifyDomainImage(wdtModelFile);

    // Verify the domain is created. The admin server pod "domainUid-adminserver" is up and running.
    createMiiDomain(
        domainNamespace,
        domainUid,
        imageName,
        replicaCount,
        null,
        false,
        null);

    // verify the admin server is started
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check cluster-1 service does not exist
    checkServiceDoesNotExist(domainUid + "-cluster-" + clusterName, domainNamespace);
    // check managed server pods and services do not exist
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, domainNamespace);
    }

    // check the admin server name is 'AdminServer'
    checkAdminServerName(adminServerPodName, "AdminServer");

    // delete domain
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    deleteDomainResource(domainNamespace, domainUid);
  }

  private static void checkAppIsRunning(
      String namespace,
      String podName,
      String expectedStr
  ) {

    // check that the application is NOT running inside of a server pod
    testUntil(
        () -> appAccessibleInPod(
          namespace,
          podName,
          internalPort,
          appPath,
          expectedStr),
        logger,
        "application {0} is running on pod {1} in namespace {2}",
        appPath,
        podName,
        namespace);
  }

  private static String createAndVerifyDomainImage(String wdtModelFileForMiiDomain) {

    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(MII_IMAGE_NAME, wdtModelFileForMiiDomain, MII_BASIC_APP_NAME);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);
    return miiImage;
  }

  private void checkAdminServerName(String adminServerPodName, String expectedAdminServerName) {
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceDomainConfig(adminSvcExtHost, adminServiceNodePort,
        "\"adminServerName\": \"" + expectedAdminServerName + "\""),
        "Admin server name is not '" + expectedAdminServerName + "'");
    logger.info("AdminServerName is {0}", expectedAdminServerName);
  }
}
