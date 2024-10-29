// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.MiiDynamicUpdateHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodStatusPhase;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithOnNonDynamicChanges;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyIntrospectorRuns;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfigViaAdminPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifySystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventContainsExpectedMsg;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodLogContains;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies the runtime updates that are not supported and non-dynamic
 * changes using CommitUpdateAndRoll.
 */

@DisplayName("Test dynamic updates to a model in image domain, part3")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("oke-sequential")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-mrg")
class ItMiiDynamicUpdatePart3 {

  static MiiDynamicUpdateHelper helper = new MiiDynamicUpdateHelper();
  private static final String domainUid = "mii-dynamic-update3";
  public static Path pathToChangReadsYaml = null;
  static LoggingFacade logger = null;
  private static String operatorPodName = null;
  private static String httpHostHeader = null;

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
    logger = helper.logger;

    // write sparse yaml to change ScatteredReadsEnabled for adminserver
    pathToChangReadsYaml = Paths.get(WORK_DIR + "/changereads.yaml");
    String yamlToChangeReads = "topology:\n"
        + "    Server:\n"
        + "        \"admin-server\":\n"
        + "            ScatteredReadsEnabled: true";
    assertDoesNotThrow(() -> Files.write(pathToChangReadsYaml, yamlToChangeReads.getBytes()));

    operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, helper.opNamespace),
            "Can't get operator's pod name");


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
   * Negative test: Changing the domain name using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @DisplayName("Negative test changing domain name using mii dynamic update")
  void testMiiChangeDomainName() {
    // write sparse yaml to file
    Path pathToChangeDomainNameYaml = Paths.get(WORK_DIR + "/changedomainname.yaml");
    String yamlToChangeDomainName = "topology:\n"
        + "  Name: newdomainname\n"
        + "  AdminServerName: 'admin-server'";

    assertDoesNotThrow(() -> Files.write(pathToChangeDomainNameYaml, yamlToChangeDomainName.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(pathToChangeDomainNameYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed with the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector pods
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is deleted
    logger.info("Verifying introspector pod is deleted");
    checkPodDoesNotExist(getIntrospectJobName(domainUid), domainUid, helper.domainNamespace);
  }

  /**
   * Negative test: Changing the listen port of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @DisplayName("Negative test changing listen port of a server using mii dynamic update")
  void testMiiChangeListenPort() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    // write sparse yaml to file
    Path pathToChangeListenPortYaml = Paths.get(WORK_DIR + "/changelistenport.yaml");
    String yamlToChangeListenPort = "topology:\n"
        + "  Server:\n"
        + "    'admin-server':\n"
        + "      ListenPort: 7003";
    assertDoesNotThrow(() -> Files.write(pathToChangeListenPortYaml, yamlToChangeListenPort.getBytes()));

    OffsetDateTime timestamp = now();
    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(pathToChangeListenPortYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and operator pod log contains the expected error msg");
    checkPodLogContainsString(helper.opNamespace, operatorPodName, MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(helper.opNamespace, helper.domainNamespace, domainUid, DOMAIN_FAILED,
        "Warning", timestamp, MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is deleted
    logger.info("Verifying introspector pod is deleted");
    checkPodDoesNotExist(getIntrospectJobName(domainUid), domainUid, helper.domainNamespace);
  }

  /**
   * Negative test: Changing SSL setting of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @DisplayName("Negative test changing SSL setting of a server using mii dynamic update")
  void testMiiChangeSSL() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    // write sparse yaml to file
    Path pathToChangeSSLYaml = Paths.get(WORK_DIR + "/changessl.yaml");
    String yamlToChangeSSL = "topology:\n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "      SSL:\n"
        + "         ListenPort: 8103";
    assertDoesNotThrow(() -> Files.write(pathToChangeSSLYaml, yamlToChangeSSL.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(pathToChangeSSLYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and operator pod log contains the expected error msg");
    checkPodLogContainsString(helper.opNamespace, operatorPodName, MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is deleted
    logger.info("Verifying introspector pod is deleted");
    checkPodDoesNotExist(getIntrospectJobName(domainUid), domainUid, helper.domainNamespace);
  }

  /**
   * Verify the operator log contains the introspector job logs.
   * When the introspector fails, it should log the correct error msg. For example,
   * the Domain resource specified 'spec.configuration.model.onlineUpdate.enabled=true',
   * but there are unsupported model changes for online update.
   */
  @Test
  @DisplayName("verify the operator logs introspector job messages")
  void testOperatorLogIntrospectorMsg() {
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, helper.opNamespace));
    logger.info("operator pod name: {0}", operatorPodName);
    String operatorPodLog = assertDoesNotThrow(() -> getPodLog(operatorPodName, helper.opNamespace));
    logger.info("operator pod log: {0}", operatorPodLog);
    assertTrue(operatorPodLog.contains("Introspector Job Log"));
    if (!WEBLOGIC_SLIM) {
      assertTrue(operatorPodLog.contains("WebLogic version='" + WEBLOGIC_VERSION + "'"));
    }
    assertTrue(operatorPodLog.contains("Job " + domainUid + "-introspector in namespace " 
        + helper.domainNamespace + " failed with status"));
    assertTrue(operatorPodLog.contains(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG));
  }


  /**
   * Non-dynamic change using dynamic update by changing datasource parameters.
   * Set onNonDynamicChanges to CommitUpdateAndRoll.
   * Verify domain will rolling restart.
   * Verify introspectVersion is updated.
   * Verify the datasource parameter is updated by checking the MBean using REST api.
   * Verify domain status should have a condition type as "Complete".
   * Delete the datasource created in this test and
   * verify the domain status condition contains the correct type and expected reason.
   */
  @Test
  @DisplayName("Changing datasource parameters with CommitUpdateAndRoll using mii dynamic update")
  void testMiiChangeDataSourceParameterWithCommitUpdateAndRoll() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, OffsetDateTime> pods = helper.addDataSourceAndVerify(false);

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, WDT-535
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(MODEL_DIR + "/model.update.jdbc2.yaml"), withStandardRetryPolicy);

    // Wait until the introspector pod deleted
    checkPodDeleted(getIntrospectJobName(domainUid), domainUid, helper.domainNamespace);

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
    if (OKE_CLUSTER || OCNE) {
      assertTrue(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
          "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDataSourceParams",
          "jdbc\\/TestDataSource2-2"), "JDBCSystemResource JNDIName not found");
    } else {
      int adminServiceNodePort
          = getServiceNodePort(helper.domainNamespace, getExternalServicePodName(helper.adminServerPodName), "default");
      assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

      // create ingress for admin service
      // use traefik LB for kind cluster with ingress host header in url
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        httpHostHeader = createIngressHostRouting(helper.domainNamespace, domainUid,
            helper.adminServerName, 7001);
        StringBuffer curlString = new StringBuffer("curl -g --user ");
        curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
            .append(" --noproxy '*' "
                + " -H 'host: " + httpHostHeader + "' " + " http://" + "localhost:"
                + TRAEFIK_INGRESS_HTTP_HOSTPORT)
            .append("/management/weblogic/latest/domainConfig")
            .append("/")
            .append("JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDataSourceParams")
            .append("/");

        logger.info("curl command {0}", new String(curlString));

        assertTrue(Command
            .withParams(new CommandParams()
                .command(curlString.toString()))
            .executeAndVerify("jdbc\\/TestDataSource2-2"), "JDBCSystemResource JNDIName not found");
      } else {
        assertTrue(checkSystemResourceConfig(helper.adminSvcExtHost, adminServiceNodePort,
            "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDataSourceParams",
            "jdbc\\/TestDataSource2-2"), "JDBCSystemResource JNDIName not found");

      }
    }
    logger.info("JDBCSystemResource configuration found");


    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected status");
    helper.verifyDomainStatusConditionNoErrorMsg("Completed", "True");

    // write sparse yaml to delete datasource to file, delete ds to keep the config clean
    Path pathToDeleteDSYaml = Paths.get(WORK_DIR + "/deleteds.yaml");
    String yamlToDeleteDS = "resources:\n" + "  JDBCSystemResource:\n";

    assertDoesNotThrow(() -> Files.write(pathToDeleteDSYaml, yamlToDeleteDS.getBytes()));

    pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    pods.put(helper.adminServerPodName, getPodCreationTime(helper.domainNamespace, helper.adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= helper.replicaCount; i++) {
      pods.put(helper.managedServerPrefix + i,
          getPodCreationTime(helper.domainNamespace, helper.managedServerPrefix + i));
    }

    // Replace contents of an existing configMap with cm config
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        List.of(pathToDeleteDSYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    // Verifying the domain is not restarted
    verifyPodsNotRolled(helper.domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

    // check datasource configuration is deleted using REST api
    if (OKE_CLUSTER) {
      assertFalse(checkSystemResourceConfigViaAdminPod(helper.adminServerPodName, helper.domainNamespace,
          "JDBCSystemResources",
          "TestDataSource2"), "Found JDBCSystemResource datasource, should be deleted");
    } else {
      int adminServiceNodePort
          = getServiceNodePort(helper.domainNamespace, getExternalServicePodName(helper.adminServerPodName), "default");
      assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        assertFalse(checkSystemResourceConfig(helper.adminSvcExtHost, adminServiceNodePort, "JDBCSystemResources",
            "TestDataSource2"), "Found JDBCSystemResource datasource, should be deleted");
      } else {
        verifySystemResourceConfiguration(null, adminServiceNodePort,
            "JDBCSystemResources", "TestDataSource2", "404", httpHostHeader);
      }
    }
    logger.info("JDBCSystemResource Datasource is deleted");

    // check that the domain status condition contains the correct type and expected status
    logger.info("verifying the domain status condition contains the correct type and expected status");
    helper.verifyDomainStatusConditionNoErrorMsg("Completed", "True");
  }

  private void verifyIntrospectorFailsWithExpectedErrorMsg(String expectedErrorMsg) {

    logger.info("Verifying operator pod log for introspector error messages");
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, helper.opNamespace));
    testUntil(
        () -> assertDoesNotThrow(() -> checkPodLogContains(expectedErrorMsg, operatorPodName, helper.opNamespace),
            String.format("Checking operator pod %s log failed", operatorPodName)),
        logger,
        "Checking operator log for introspector logs contains the expected error msg {0}",
        expectedErrorMsg);

    // check that the domain status message contains the expected error msg
    logger.info("verifying the domain status message contains the expected error msg");
    testUntil(
        () -> {
          DomainResource miidomain = getDomainCustomResource(domainUid, helper.domainNamespace);
          return (miidomain != null) && (miidomain.getStatus() != null) && (miidomain.getStatus().getMessage() != null)
              && miidomain.getStatus().getMessage().contains(expectedErrorMsg);
        },
        logger,
        "domain status message contains the expected error msg \"{0}\"",
        expectedErrorMsg);

    // check that the domain status condition type is "Failed" and message contains the expected error msg
    logger.info("verifying the domain status condition message contains the expected error msg");
    testUntil(
        () -> {
          DomainResource miidomain = getDomainCustomResource(domainUid, helper.domainNamespace);
          if ((miidomain != null) && (miidomain.getStatus() != null)) {
            for (DomainCondition domainCondition : miidomain.getStatus().getConditions()) {
              if ((domainCondition.getType() != null && domainCondition.getType().equalsIgnoreCase("Failed"))
                  && (domainCondition.getMessage() != null
                  && domainCondition.getMessage().contains(expectedErrorMsg))) {
                return true;
              }
            }
          }
          return false;
        },
        logger,
        "domain status condition message contains the expected error msg \"{0}\"",
        expectedErrorMsg);
  }

  boolean podStatusPhaseContainsString(String namespace, String jobName, String expectedPhase) {
    String introspectPodName;
    V1Pod introspectorPod;

    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    try {
      introspectorPod = getPod(namespace, labelSelector, jobName);
    } catch (ApiException apiEx) {
      logger.severe("Got ApiException while getting pod: {0}", apiEx);
      return false;
    }

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      introspectPodName = introspectorPod.getMetadata().getName();
    } else {
      return false;
    }

    try {
      return getPodStatusPhase(namespace, labelSelector, introspectPodName).equals(expectedPhase);
    } catch (ApiException apiEx) {
      logger.severe("Got ApiException while getting pod status phase: {0}", apiEx);
      return false;
    }

  }

  boolean podLogContainsExpectedErrorMsg(String introspectJobName, String namespace, String errormsg) {
    String introspectPodName;
    V1Pod introspectorPod;

    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);

    try {
      introspectorPod = getPod(namespace, labelSelector, introspectJobName);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod: {0}", apiEx);
      return false;
    }

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      introspectPodName = introspectorPod.getMetadata().getName();
    } else {
      return false;
    }

    String introspectorLog;
    try {
      introspectorLog = getPodLog(introspectPodName, namespace);
      logger.info("introspector log: {0}", introspectorLog);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod log: {0}", apiEx);
      return false;
    }

    return introspectorLog.contains(errormsg);
  }
}
