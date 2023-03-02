// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainOnPvUsingWdt;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class verifies that a new dynamic cluster added by using an online WLST script
 * is not considered as a configured cluster and server in the newly added dynamic cluster is started successfully.
 */
@DisplayName("Verify that server in newly added dynamic cluster is started successfully")
@IntegrationTest
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("olcne")
class ItAddNewDynamicClusterUsingWlst {

  // domain constants
  private static final String domainUid = "dynasconfigcluster-domain-1";
  private static final String adminServerPodName = domainUid + "-admin-server";
  private static final String newManagedServerPrefix = "new-managed-server";
  private static final String newManagedServerPodPrefix = domainUid + "-" + newManagedServerPrefix;
  private static final String clusterName = "cluster-1";
  private static final String newClusterName = "cluster-2";
  private static final int replicaCount = 2;
  private static final String wlSecretName = "weblogic-credentials";
  private static String domainNamespace = null;

  private static LoggingFacade logger = null;

  /**
   * Install operator.
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
    String opNamespace = namespaces.get(0);

    // get unique namespaces for Weblogic domain
    logger.info("Getting unique namespace for domain-on-pv domain");
    assertNotNull(namespaces.get(1));
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Create a domain in PV domain, use an online WLST script to create a new dynamic cluster in the domain,
   * Patch the domain with a different introspectVersion to trigger introspector
   * and then verify that server from new cluster is started successfully.
   */
  @Test
  @DisplayName("Create WebLogic domain in PV domain, add a dynamic cluster and "
      + "verify that server from new cluster is started successfully")
  void testDynamicClusterNotAsConfigCluster() {
    // create a domain in PV domain
    DomainResource domain = createDomainOnPvUsingWdt(domainUid, domainNamespace, wlSecretName,
        clusterName, replicaCount, ItAddNewDynamicClusterUsingWlst.class.getSimpleName());
    assertDomainNotNull(domain);

    // get admin service node port
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    // In OKD cluster, we need to get the routeHost for the external admin service
    String routeHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    
    logger.info("Validating WebLogic admin server access by login to console");
    testUntil(
        assertDoesNotThrow(() -> {
          return adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, routeHost);
        }, "Access to admin server node port failed"),
        logger,
        "Console login validation");

    // create a new dynamic cluster using an online WLST script
    createNewDynamicCluster();

    // verify the managed server pod in newly added dynamic cluster comes up
    checkPodReadyAndServiceExists(newManagedServerPodPrefix + 1, domainUid, domainNamespace);
  }

  private void createNewDynamicCluster() {
    // get admin service hostname
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // get port for default channel
    logger.info("Getting port for default channel");
    int defaultChannelPort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server default port failed");
    assertNotEquals(-1, defaultChannelPort, "Couldn't get valid port for default channel");
    logger.info("default channel port: {0}", defaultChannelPort);

    // create WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");

    Properties p1 = new Properties();
    p1.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p1.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p1.setProperty("admin_host", adminServerPodName);
    p1.setProperty("admin_port", Integer.toString(defaultChannelPort));
    p1.setProperty("new_cluster_name", newClusterName);
    p1.setProperty("new_ms_name_prefix", newManagedServerPrefix);
    assertDoesNotThrow(() -> p1.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");
    logger.info("WLST property file is: {0} ", wlstPropertiesFile.getAbsolutePath());

    // create a new dynamic cluster using an online WLST script
    logger.info("Creating a new dynamic cluster using an online WLST script");
    Path createDynClusterScript = Paths.get(RESOURCE_DIR, "python-scripts", "create-dynamic-cluster.py");
    executeWLSTScript(createDynClusterScript, wlstPropertiesFile.toPath(), domainNamespace);

    // patch the domain to increase the introspectVersion value to triage the introspector and verify
    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));
    int newIntrospectVersion = Integer.parseInt(introspectVersion) + 1;
    String patchStr =
        "[{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + newIntrospectVersion + "\"}]";

    logger.info("Updating introspectVersion {0} using patch string: {1}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
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
}
