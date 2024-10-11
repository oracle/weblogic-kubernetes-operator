// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
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
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.MiiDynamicUpdateHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_UPDATED_RESTART_REQUIRED_LABEL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
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
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfigViaAdminPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceRuntime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies the dynamic update use cases using data source.
 */

@DisplayName("Test dynamic updates to a model in image domain, part2")
@IntegrationTest
@Tag("olcne-srg")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-mrg")
@Tag("oke-sequential")
@Tag("oke-arm")
class ItMiiDynamicUpdatePart2 {

  static MiiDynamicUpdateHelper helper = new MiiDynamicUpdateHelper();
  private static final String domainUid = "mii-dynamic-update2";
  public static Path pathToChangReadsYaml = null;
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
    helper.initAll(namespaces, domainUid);
    logger = getLogger();

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
    helper.beforeEach();
  }

  /**
   * Mixed update by changing the DataSource URL (non-dynamic) and undeploying an application (dynamic).
   * Patched the domain resource and set onNonDynamicChanges to CommitUpdateAndRoll.
   * Verify domain will rolling restart.
   * Verify introspectVersion is updated.
   * Verify the datasource URL is updated by checking the MBean using REST api.
   * Verify the application is undeployed.
   * Verify domain status should have a condition type as "Completed".
   */
  @Test
  @DisplayName("Changing Weblogic datasource URL and deleting application with CommitUpdateAndRoll "
      + "using mii dynamic update")
  void testMiiDeleteAppChangeDBUrlWithCommitUpdateAndRoll() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods =
        helper.addDataSourceAndVerify(false);

    // check the application myear is deployed using REST API
    int adminServiceNodePort
        = getServiceNodePort(helper.domainNamespace, getExternalServicePodName(helper.adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
        "appDeployments",
        "myear"), "Application myear is not found");
    logger.info("Application myear is found");

    // write sparse yaml to undeploy application to file
    Path pathToUndeployAppYaml = Paths.get(WORK_DIR + "/undeployapp.yaml");
    String yamlToUndeployApp = "appDeployments:\n"
        + "  Application:\n"
        + "    '!myear':";

    assertDoesNotThrow(() -> Files.write(pathToUndeployAppYaml, yamlToUndeployApp.getBytes()));

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, WDT-535
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jdbc2.update2.yaml", pathToUndeployAppYaml.toString()),
        withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges
    patchDomainResourceWithOnNonDynamicChanges(domainUid, helper.domainNamespace, "CommitUpdateAndRoll");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    // Verifying the domain is rolling restarted
    assertTrue(verifyRollingRestartOccurred(pods, 1, helper.domainNamespace),
        "Rolling restart failed");

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

    // check datasource configuration using REST api
    adminServiceNodePort
        = getServiceNodePort(helper.domainNamespace, getExternalServicePodName(helper.adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDriverParams",
        "newdburl"), "JDBCSystemResource DB URL not found");
    logger.info("JDBCSystemResource DB URL found");

    // verify the application is undeployed
    assertFalse(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
        "appDeployments",
        "myear"), "Application myear found, should be undeployed");
    logger.info("Application myear is undeployed");

    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected status");
    helper.verifyDomainStatusConditionNoErrorMsg("Completed", "True");
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
  @DisplayName("Deleting Datasource")
  void testMiiDeleteDatasource() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods =
        helper.addDataSourceAndVerify(false);

    // write sparse yaml to delete datasource to file
    Path pathToDeleteDSYaml = Paths.get(WORK_DIR + "/deleteds.yaml");
    String yamlToDeleteDS = "resources:\n"
        + "  JDBCSystemResource:\n";

    assertDoesNotThrow(() -> Files.write(pathToDeleteDSYaml, yamlToDeleteDS.getBytes()));

    // Replace contents of an existing configMap with cm config
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(pathToDeleteDSYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    // Verifying the domain is not restarted
    verifyPodsNotRolled(helper.domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

    // check datasource configuration is deleted using REST api
    int adminServiceNodePort
        = getServiceNodePort(helper.domainNamespace, getExternalServicePodName(helper.adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertFalse(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
        "JDBCSystemResources",
        "TestDataSource2"), "Found JDBCSystemResource datasource, should be deleted");
    logger.info("JDBCSystemResource Datasource is deleted");

    // check that the domain status condition contains the correct type and expected status
    logger.info("verifying the domain status condition contains the correct type and expected status");
    helper.verifyDomainStatusConditionNoErrorMsg("Completed", "True");
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
  @DisplayName("Test non-dynamic changes with onNonDynamicChanges default value CommitUpdateOnly")
  void testOnNonDynamicChangesCommitUpdateOnlyDSAndReads() {

    String expectedMsgForCommitUpdateOnly =
        "Online WebLogic configuration updates complete but there are pending non-dynamic changes "
            + "that require pod restarts to take effect";

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods = helper.addDataSourceAndVerify(false);

    // make two non-dynamic changes, add  datasource JDBC driver params and change scatteredreadenabled
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jdbc2.updatejdbcdriverparams.yaml", pathToChangReadsYaml.toString()),
        withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges - update with CommitUpdateOnly so that even if previous test
    // updates onNonDynamicChanges, this test will work
    patchDomainResourceWithOnNonDynamicChanges(domainUid, helper.domainNamespace, "CommitUpdateOnly");

    // Patch a running domain with introspectVersion, uses default value for onNonDynamicChanges
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    // Verify domain is not restarted when non-dynamic change is made using default CommitUpdateOnly
    verifyPodsNotRolled(helper.domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

    // check pod label for MII_UPDATED_RESTART_REQUIRED_LABEL
    assertDoesNotThrow(() -> verifyPodLabelUpdated(pods.keySet(),
        MII_UPDATED_RESTART_REQUIRED_LABEL + "=true"),
        "Couldn't check pod label");
    logger.info("Verified pod label");

    // check the change is committed
    int adminServiceNodePort
        = getServiceNodePort(helper.domainNamespace, getExternalServicePodName(helper.adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    // check server config for ScatteredReadsEnabled is updated
    assertTrue(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
        "servers/" + helper.adminServerName,
        "\"scatteredReadsEnabled\": true"), "ScatteredReadsEnabled is not changed to true");
    logger.info("ScatteredReadsEnabled is changed to true");

    // check datasource configuration using REST api
    assertTrue(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDriverParams/properties/properties",
        "\"name\": \"testattrib\""), "JDBCSystemResource new property not found");
    logger.info("JDBCSystemResource new property found");

    // check that the domain status condition type is "ConfigChangesPendingRestart"
    // and message contains the expected msg
    logger.info("Verifying the domain status condition message contains the expected msg");
    verifyDomainStatusCondition(
        "ConfigChangesPendingRestart", expectedMsgForCommitUpdateOnly);

    // restart domain and verify the changes are effective
    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, helper.domainNamespace);
    logger.log(Level.INFO, "New restart version is {0}", newRestartVersion);
    assertTrue(verifyRollingRestartOccurred(pods, 1, helper.domainNamespace),
        "Rolling restart failed");

    // Even if pods are created, need the service to created
    for (int i = 1; i <= helper.replicaCount; i++) {
      logger.info("Check managed server service {0} created in namespace {1}",
          helper.managedServerPrefix + i, helper.domainNamespace);
      checkServiceExists(helper.managedServerPrefix + i, helper.domainNamespace);
    }

    // check datasource runtime after restart
    assertTrue(checkSystemResourceRuntime(helper.adminServerPodName, helper.domainNamespace,
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

  void verifyPodLabelUpdated(Set<String> podNames, String label) throws ApiException {
    for (String podName : podNames) {
      assertNotNull(getPod(helper.domainNamespace, label, podName),
          "Pod " + podName + " doesn't have label " + label);
    }
  }

  void verifyPodLabelRemoved(Set<String> podNames, String label) throws ApiException {
    for (String podName : podNames) {
      assertNull(getPod(helper.domainNamespace, label, podName),
          "Pod " + podName + " still have the label " + label);
    }
  }

  /**
   * Verify domain status conditions contains the given condition type and message.
   *
   * @param conditionType condition type
   * @param conditionMsg  messsage in condition
   * @return true if the condition matches
   */
  private boolean verifyDomainStatusCondition(String conditionType, String conditionMsg) {
    testUntil(
        () -> {
          DomainResource miidomain = getDomainCustomResource(domainUid, helper.domainNamespace);
          if ((miidomain != null) && (miidomain.getStatus() != null)) {
            for (DomainCondition domainCondition : miidomain.getStatus().getConditions()) {
              logger.info("Condition Type =" + domainCondition.getType()
                  + " Condition Msg =" + domainCondition.getMessage());
              if (domainCondition.getType() != null && domainCondition.getMessage() != null) {
                logger.info("condition " + domainCondition.getType().equalsIgnoreCase(conditionType)
                    + " msg " + domainCondition.getMessage().contains(conditionMsg));
              }
              if ((domainCondition.getType() != null && domainCondition.getType().equalsIgnoreCase(conditionType))
                  && (domainCondition.getMessage() != null && domainCondition.getMessage().contains(conditionMsg))) {
                return true;
              }
            }
          }
          return false;
        },
        logger,
        "domain status condition message contains the expected msg \"{0}\"",
        conditionMsg);
    return false;
  }

}
