// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainCondition;
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

import static oracle.weblogic.kubernetes.TestConstants.MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodStatusPhase;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies the following scenarios
 *
 * <p>testMiiAddWorkManager
 * Add a new work manager to a running WebLogic domain
 *
 * <p>testMiiUpdateWorkManager
 * Update dynamic work manager configurations in a running WebLogic domain.
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test dynamic updates to a model in image domain, part2")
@IntegrationTest
@Tag("okdenv")
class ItMiiDynamicUpdatePart3 {

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
   * Negative test: Changing the domain name using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(1)
  @DisplayName("Negative test changing domain name using mii dynamic update")
  void testMiiChangeDomainName() {
    // write sparse yaml to file
    Path pathToChangeDomainNameYaml = Paths.get(WORK_DIR + "/changedomainname.yaml");
    String yamlToChangeDomainName = "topology:\n"
        + "  Name: newdomainname\n"
        + "  AdminServerName: 'admin-server'";

    assertDoesNotThrow(() -> Files.write(pathToChangeDomainNameYaml, yamlToChangeDomainName.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeDomainNameYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed with the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector pods
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is deleted
    logger.info("Verifying introspector pod is deleted");
    checkPodDoesNotExist(getIntrospectJobName(domainUid), domainUid, domainNamespace);
  }

  /**
   * Negative test: Changing the listen port of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(2)
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

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeListenPortYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and the pod log contains the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is deleted
    logger.info("Verifying introspector pod is deleted");
    checkPodDoesNotExist(getIntrospectJobName(domainUid), domainUid, domainNamespace);
  }

  /**
   * Negative test: Changing the listen address of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(3)
  @DisplayName("Negative test changing listen address of a server using mii dynamic update")
  void testMiiChangeListenAddress() {
    // write sparse yaml to file
    Path pathToChangeListenAddressYaml = Paths.get(WORK_DIR + "/changelistenAddress.yaml");
    String yamlToChangeListenAddress = "topology:\n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "       ListenAddress: myAddress";
    assertDoesNotThrow(() -> Files.write(pathToChangeListenAddressYaml, yamlToChangeListenAddress.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeListenAddressYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and the pod log contains the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is deleted
    logger.info("Verifying introspector pod is deleted");
    checkPodDoesNotExist(getIntrospectJobName(domainUid), domainUid, domainNamespace);
  }

  /**
   * Negative test: Changing SSL setting of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(4)
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
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeSSLYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and the pod log contains the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is deleted
    logger.info("Verifying introspector pod is deleted");
    checkPodDoesNotExist(getIntrospectJobName(domainUid), domainUid, domainNamespace);
  }

  /**
   * Verify the operator log contains the introspector job logs.
   * When the introspector fails, it should log the correct error msg. For example,
   * the Domain resource specified 'spec.configuration.model.onlineUpdate.enabled=true',
   * but there are unsupported model changes for online update.
   */
  @Test
  @Order(5)
  @DisplayName("verify the operator logs introspector job messages")
  void testOperatorLogIntrospectorMsg() {
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("operator pod name: {0}", operatorPodName);
    String operatorPodLog = assertDoesNotThrow(() -> getPodLog(operatorPodName, opNamespace));
    logger.info("operator pod log: {0}", operatorPodLog);
    assertTrue(operatorPodLog.contains("Introspector Job Log"));
    assertTrue(operatorPodLog.contains("WebLogic version='" + WEBLOGIC_VERSION + "'"));
    assertTrue(operatorPodLog.contains("Job mii-dynamic-update1-introspector has failed"));
    assertTrue(operatorPodLog.contains(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG));
  }

  private void verifyIntrospectorFailsWithExpectedErrorMsg(String expectedErrorMsg) {
    // verify the introspector pod is created
    logger.info("Verifying introspector pod is created");
    String introspectJobName = getIntrospectJobName(domainUid);

    // check whether the introspector log contains the expected error message
    logger.info("verifying that the introspector log contains the expected error message");
    testUntil(
        () -> podLogContainsExpectedErrorMsg(introspectJobName, domainNamespace, expectedErrorMsg),
        logger,
        "Checking for the log of introspector pod contains the expected error msg {0}",
        expectedErrorMsg);

    // check the status phase of the introspector pod is failed
    logger.info("verifying the status phase of the introspector pod is failed");
    testUntil(
        () -> podStatusPhaseContainsString(domainNamespace, introspectJobName, "Failed"),
        logger,
        "Checking for status phase of introspector pod is failed");

    // check that the domain status message contains the expected error msg
    logger.info("verifying the domain status message contains the expected error msg");
    testUntil(
        () -> {
          Domain miidomain = getDomainCustomResource(domainUid, domainNamespace);
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
          Domain miidomain = getDomainCustomResource(domainUid, domainNamespace);
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
    //String introspectPodName = getPodNameFromJobName(namespace, jobName);
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
      return getPodStatusPhase(namespace, labelSelector, introspectPodName).equalsIgnoreCase(expectedPhase);
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
