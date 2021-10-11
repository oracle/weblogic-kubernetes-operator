// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.MiiDynamicUpdateHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG;
import static oracle.weblogic.kubernetes.TestConstants.MII_UPDATED_RESTART_REQUIRED_LABEL;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithOnNonDynamicChanges;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyIntrospectorRuns;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceRuntime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies the dynamic update use cases using data source.
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test dynamic updates to a model in image domain, part2")
@IntegrationTest
@Tag("okdenv")
class ItMiiDynamicUpdatePart2 {

  static MiiDynamicUpdateHelper dynamicUpdateHelper = new MiiDynamicUpdateHelper();
  private static final String domainUid = "mii-dynamic-update1";
  static String domainNamespace = null;
  static String adminServerPodName = null;
  static String opNamespace = null;
  static int replicaCount = dynamicUpdateHelper.replicaCount;
  static String managedServerPrefix = null;
  static String adminServerName = dynamicUpdateHelper.adminServerName;
  static String configMapName = MiiDynamicUpdateHelper.configMapName;
  public static Path pathToChangReadsYaml = null;
  private static String adminSvcExtHost = null;
  static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Create domain resource defintion.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    dynamicUpdateHelper.initAll(namespaces, domainUid);
    domainNamespace = dynamicUpdateHelper.domainNamespace;
    opNamespace = dynamicUpdateHelper.opNamespace;
    adminServerPodName = dynamicUpdateHelper.adminServerPodName;
    replicaCount = dynamicUpdateHelper.replicaCount;
    managedServerPrefix = dynamicUpdateHelper.managedServerPrefix;
    configMapName = MiiDynamicUpdateHelper.configMapName;
    logger = dynamicUpdateHelper.logger;

    // write sparse yaml to change ScatteredReadsEnabled for adminserver
    pathToChangReadsYaml = Paths.get(WORK_DIR + "/changereads.yaml");
    String yamlToChangeReads = "topology:\n"
        + "    Server:\n"
        + "        \"admin-server\":\n"
        + "            ScatteredReadsEnabled: true";
    assertDoesNotThrow(() -> Files.write(pathToChangReadsYaml, yamlToChangeReads.getBytes()));
  }

  /**
   * Verify all server pods are running.
   * Verify all k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {
    dynamicUpdateHelper.beforeEach();
    adminSvcExtHost = dynamicUpdateHelper.adminSvcExtHost;
  }

  /**
   * Recreate configmap containing datasource config.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify the datasource is added by checking the MBean using REST api.
   */
  @Test
  @Order(1)
  @DisplayName("Add datasource in MII domain using mii dynamic update")
  void testMiiAddDataSource() {
    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    addDataSourceAndVerify(true);
  }

  /**
   * Non-dynamic change using dynamic update by changing datasource parameters.
   * Set onNonDynamicChanges to CommitUpdateAndRoll.
   * Verify domain will rolling restart.
   * Verify introspectVersion is updated.
   * Verify the datasource parameter is updated by checking the MBean using REST api.
   * Verify domain status should have a condition type as "Available" and condition reason as "ServersReady".
   */
  @Test
  @Order(2)
  @DisplayName("Changing datasource parameters with CommitUpdateAndRoll using mii dynamic update")
  void testMiiChangeDataSourceParameterWithCommitUpdateAndRoll() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods =
        addDataSourceAndVerify(false);

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, WDT-535
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.update.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateAndRoll");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, domainNamespace);

    // Verifying the domain is rolling restarted
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);

    // check datasource configuration using REST api
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDataSourceParams",
        "jdbc\\/TestDataSource2-2"), "JDBCSystemResource JNDIName not found");
    logger.info("JDBCSystemResource configuration found");

    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected reason");
    dynamicUpdateHelper.verifyDomainStatusConditionNoErrorMsg("Available", "ServersReady");

    // change the datasource jndi name back to original in order to create a clean environment for the next test
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateAndRoll");

    // Patch a running domain with introspectVersion.
    introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, domainNamespace);

    // Verifying the domain is rolling restarted
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);
  }

  /**
   * Mixed update by changing the DataSource URL (non-dynamic) and undeploying an application (dynamic).
   * Patched the domain resource and set onNonDynamicChanges to CommitUpdateAndRoll.
   * Verify domain will rolling restart.
   * Verify introspectVersion is updated.
   * Verify the datasource URL is updated by checking the MBean using REST api.
   * Verify the application is undeployed.
   * Verify domain status should have a condition type as "Available" and condition reason as "ServersReady".
   */
  @Test
  @Order(3)
  @DisplayName("Changing Weblogic datasource URL and deleting application with CommitUpdateAndRoll "
      + "using mii dynamic update")
  void testMiiDeleteAppChangeDBUrlWithCommitUpdateAndRoll() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods =
        addDataSourceAndVerify(false);

    // check the application myear is deployed using REST API
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
        "appDeployments",
        "myear"), "Application myear is not found");
    logger.info("Application myear is found");

    // write sparse yaml to undeploy application to file
    Path pathToUndeployAppYaml = Paths.get(WORK_DIR + "/undeployapp.yaml");
    String yamlToUndeployApp = "appDeployments:\n"
        + "  Application:\n"
        + "    !myear:";

    assertDoesNotThrow(() -> Files.write(pathToUndeployAppYaml, yamlToUndeployApp.getBytes()));

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, WDT-535
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jdbc2.update2.yaml", pathToUndeployAppYaml.toString()),
        withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateAndRoll");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, domainNamespace);

    // Verifying the domain is rolling restarted
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);

    // check datasource configuration using REST api
    adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDriverParams",
        "newdburl"), "JDBCSystemResource DB URL not found");
    logger.info("JDBCSystemResource DB URL found");

    // verify the application is undeployed
    assertFalse(checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
        "appDeployments",
        "myear"), "Application myear found, should be undeployed");
    logger.info("Application myear is undeployed");

    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected reason");
    dynamicUpdateHelper.verifyDomainStatusConditionNoErrorMsg("Available", "ServersReady");
  }

  /**
   * Recreate configmap by deleting datasource.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete.
   * Verify the domain is not restarted.
   * Verify the introspector version is updated.
   * Verify the datasource is deleted.
   * Verify the domain status condition contains the correct type and expected reason.
   */
  @Test
  @Order(4)
  @DisplayName("Deleting Datasource")
  void testMiiDeleteDatasource() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods =
        addDataSourceAndVerify(false);

    // write sparse yaml to delete datasource to file
    Path pathToDeleteDSYaml = Paths.get(WORK_DIR + "/deleteds.yaml");
    String yamlToDeleteDS = "resources:\n"
        + "  JDBCSystemResource:\n"
        + "    !TestDataSource2:";

    assertDoesNotThrow(() -> Files.write(pathToDeleteDSYaml, yamlToDeleteDS.getBytes()));

    // Replace contents of an existing configMap with cm config
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToDeleteDSYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, domainNamespace);

    // Verifying the domain is not restarted
    verifyPodsNotRolled(domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);

    // check datasource configuration is deleted using REST api
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertFalse(checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort, "JDBCSystemResources",
        "TestDataSource2"), "Found JDBCSystemResource datasource, should be deleted");
    logger.info("JDBCSystemResource Datasource is deleted");

    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected reason");
    dynamicUpdateHelper.verifyDomainStatusConditionNoErrorMsg("Available", "ServersReady");
  }

  /**
   * Two non-dynamic changes with default CommitUpdateOnly for onNonDynamicChanges.
   * Create a configmap containing two non-dynamic changes, modified DataSource attribute
   * and Adminstration Sever ScatteredReadsEnabled attribute.
   * Patch the domain resource with the configmap, using default value CommitUpdateOnly for onNonDynamicChanges.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete.
   * Verify the domain status is updated, domain is not restarted and the change is commited.
   * Restart the domain and verify both the changes are effective using REST Api.
   */
  @Test
  @Order(5)
  @DisplayName("Test non-dynamic changes with onNonDynamicChanges default value CommitUpdateOnly")
  void testOnNonDynamicChangesCommitUpdateOnly() {

    String expectedMsgForCommitUpdateOnly =
        "Online WebLogic configuration updates complete but there are pending non-dynamic changes "
            + "that require pod restarts to take effect";

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods = addDataSourceAndVerify(false);

    // make two non-dynamic changes, add  datasource JDBC driver params and change scatteredreadenabled
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jdbc2.updatejdbcdriverparams.yaml", pathToChangReadsYaml.toString()),
        withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges - update with CommitUpdateOnly so that even if previous test
    // updates onNonDynamicChanges, this test will work
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateOnly");

    // Patch a running domain with introspectVersion, uses default value for onNonDynamicChanges
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, domainNamespace);

    // Verify domain is not restarted when non-dynamic change is made using default CommitUpdateOnly
    verifyPodsNotRolled(domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);

    // check pod label for MII_UPDATED_RESTART_REQUIRED_LABEL
    assertDoesNotThrow(() -> verifyPodLabelUpdated(pods.keySet(),
        MII_UPDATED_RESTART_REQUIRED_LABEL + "=true"),
        "Couldn't check pod label");
    logger.info("Verified pod label");

    // check the change is committed
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    // check server config for ScatteredReadsEnabled is updated
    assertTrue(checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
        "servers/" + adminServerName,
        "\"scatteredReadsEnabled\": true"), "ScatteredReadsEnabled is not changed to true");
    logger.info("ScatteredReadsEnabled is changed to true");

    // check datasource configuration using REST api
    assertTrue(checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDriverParams/properties/properties",
        "\"name\": \"testattrib\""), "JDBCSystemResource new property not found");
    logger.info("JDBCSystemResource new property found");

    // check that the domain status condition type is "ConfigChangesPendingRestart"
    // and message contains the expected msg
    logger.info("Verifying the domain status condition message contains the expected msg");
    dynamicUpdateHelper.verifyDomainStatusCondition(
        "ConfigChangesPendingRestart", expectedMsgForCommitUpdateOnly);

    // restart domain and verify the changes are effective
    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version is {0}", newRestartVersion);
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // Even if pods are created, need the service to created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check datasource runtime after restart
    assertTrue(checkSystemResourceRuntime(adminSvcExtHost, adminServiceNodePort,
        "serverRuntimes/" + MANAGED_SERVER_NAME_BASE + "1/JDBCServiceRuntime/"
            + "JDBCDataSourceRuntimeMBeans/TestDataSource2",
        "\"testattrib\": \"dummy\""), "JDBCSystemResource new property not found");
    logger.info("JDBCSystemResource new property found");

    // check pod label MII_UPDATED_RESTART_REQUIRED_LABEL should have been removed
    assertDoesNotThrow(() -> verifyPodLabelRemoved(pods.keySet(),
        MII_UPDATED_RESTART_REQUIRED_LABEL + "=true"),
        "Couldn't check pod label");
    logger.info("Verified pod label");

  }

  /**
   * Verify the operator log contains the introspector job logs.
   * When the introspector fails, it should log the correct error msg. For example,
   * the Domain resource specified 'spec.configuration.model.onlineUpdate.enabled=true',
   * but there are unsupported model changes for online update.
   */
  @Test
  @Order(6)
  @DisplayName("verify the operator logs introspector job messages")
  void testOperatorLogIntrospectorMsg() {
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("operator pod name: {0}", operatorPodName);
    String operatorPodLog = assertDoesNotThrow(() -> getPodLog(operatorPodName, opNamespace));
    logger.info("operator pod log: {0}", operatorPodLog);
    assertTrue(operatorPodLog.contains("Introspector Job Log"));
    assertTrue(operatorPodLog.contains("WebLogic version='" + WEBLOGIC_VERSION + "'"));
    assertTrue(operatorPodLog.contains("Job mii-dynamic-update-introspector has failed"));
    assertTrue(operatorPodLog.contains(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG));
  }

  void verifyPodLabelUpdated(Set<String> podNames, String label) throws ApiException {
    for (String podName : podNames) {
      assertNotNull(getPod(domainNamespace, label, podName),
          "Pod " + podName + " doesn't have label " + label);
    }
  }

  void verifyPodLabelRemoved(Set<String> podNames, String label) throws ApiException {
    for (String podName : podNames) {
      assertNull(getPod(domainNamespace, label, podName),
          "Pod " + podName + " still have the label " + label);
    }
  }

  LinkedHashMap<String, OffsetDateTime> addDataSourceAndVerify(boolean introspectorRuns) {

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, WDT-535
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    // if the config map content is not changed, its possible to miss the introspector pod creation/deletion as
    // it will be very quick, skip the check in those cases
    if (introspectorRuns) {
      verifyIntrospectorRuns(domainUid, domainNamespace);
    }

    verifyPodsNotRolled(domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);

    // check datasource configuration using REST api
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort, "JDBCSystemResources",
        "TestDataSource2", "200"), "JDBCSystemResource not found");
    logger.info("JDBCSystemResource configuration found");
    return pods;

  }
}
