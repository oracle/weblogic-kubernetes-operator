// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.ManagedServer;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResourceReturnResponse;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainResourceForDomainInImage;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.findStringInDomainStatusMessage;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.findStringInOperatorLog;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates WebLogic domains with domain-in-image ( using WDT )
 * Verify that WKO Retry Improvements handles Severe Failures as designed.
 */
@DisplayName("Verify that WKO Retry Improvements handles Severe Failures as designed.")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-srg")
class ItRetryImprovements {

  // domain constants
  private static final String clusterName = "cluster-1";
  private static final String wlSecretName = "weblogic-credentials";
  private static final String domainUid = "domaininimage";
  private static final String wdtModelFileForDomainInImage = "wdt-singlecluster-sampleapp-usingprop-wls.yaml";

  private static LoggingFacade logger = null;
  private static String opNamespace = null;
  private static String domainNamespace = null;

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
    opNamespace = namespaces.get(0);

    // get unique namespaces for domains
    logger.info("Getting unique namespaces for domain");
    assertNotNull(namespaces.get(1));
    domainNamespace = namespaces.get(1);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator
    installAndVerifyOperator(opNamespace, opServiceAccount, false, 0, domainNamespace);
  }

  // This method is needed in this test class to delete uncompleted domain to restore the env
  @AfterEach
  public void tearDown() {
    deleteSecret(wlSecretName, domainNamespace);
    deleteDomainResource(domainNamespace, domainUid);
  }

  /**
   * Create a domain-in-image domain before the secret for admin credentials is created,
   * the domain should fail to start with SEVERE error and the Operator should start retrying
   * in a specified failure.retry.interval.seconds and failure.retry.limit.minutes.
   * Also log a clear message in the domain Failed condition with the cause,
   * actions to fix the problem, and indicates that the next retry time and when the retry will stop.
   */
  @Test
  @DisplayName("Create a domain without WLS secret. Verify that retry occurs and handles SEVERE error as designed.")
  void testRetryOccursAsExpectedAndThrowSevereFailures() {
    int replicaCount = 2;
    // verify that the operator starts retrying in the intervals specified in domain.spec.failureRetryIntervalSeconds
    // when a SEVERE error occurs and clear message is logged.
    Long failureRetryLimitMinutes = Long.valueOf("1");
    DomainResource domain = createDomainResourceForRetryTest(failureRetryLimitMinutes, replicaCount, false);
    createDomainForRetryTest(domain);

    String retryOccurRegex = new StringBuffer(".*WebLogicCredentials.*\\s*secret.*")
        .append(wlSecretName)
        .append(".*not\\s*found\\s*in\\s*namespace\\s*.*")
        .append(domainNamespace)
        .append(".*Will\\s*retry\\s*next\\s*at.*and\\s*approximately\\s*every\\s*")
        .append(FAILURE_RETRY_INTERVAL_SECONDS)
        .append("\\s*seconds\\s*afterward\\s*until.*\\s*if\\s*the\\s*failure\\s*is\\s*not\\s*resolved").toString();

    testUntil(() -> findStringInDomainStatusMessage(domainNamespace, domainUid, retryOccurRegex),
        logger, "retry occurs as expected");

    // verify that the operator stops retrying when the maximum retry time is reached
    String retryMaxValueRegex = new StringBuffer(".*operator\\s*failed\\s*after\\s*retrying\\s*for\\s*.*")
        .append(failureRetryLimitMinutes)
        .append(".*\\s*minutes.*\\s*Please\\s*resolve.*error\\s*and.*update\\s*domain.spec.introspectVersion")
        .append(".*to\\s*force\\s*another\\s*retry\\s*.*").toString();

    testUntil(() -> findStringInDomainStatusMessage(domainNamespace, domainUid, retryMaxValueRegex),
        logger, "retry ends as expected after {0} minutes retry", failureRetryLimitMinutes);

    // verify that SEVERE level error message is logged in the Operator log
    String opLogSevereErrRegex = new StringBuffer(".*SEVERE")
        .append(".*WebLogicCredentials.*\\s*secret.*")
        .append(wlSecretName)
        .append(".*not\\s*found\\s*in\\s*namespace\\s*.*")
        .append(domainNamespace).toString();

    testUntil(() -> findStringInOperatorLog(opNamespace, opLogSevereErrRegex),
        logger, "SEVERE error found in Operator log");
  }

  /**
   * Create a domain-in-image domain before the secret for admin credentials is created.
   * Verify that retry stops after the issue is fixed and the domain starts successfully.
   */
  @Test
  @DisplayName("Verify that retry stops after the issue is fixed and the domain starts successfully.")
  void testRetryStoppedAfterIssueFixed() {
    int replicaCount = 2;
    // verify that the operator starts retrying when a SEVERE error occurs
    Long failureRetryLimitMinutes = Long.valueOf("5");
    DomainResource domain = createDomainResourceForRetryTest(failureRetryLimitMinutes, replicaCount,false);
    createDomainForRetryTest(domain);

    String retryOccurRegex = new StringBuffer(".*WebLogicCredentials.*\\s*secret.*")
        .append(wlSecretName)
        .append(".*not\\s*found\\s*in\\s*namespace\\s*.*")
        .append(domainNamespace)
        .append(".*Will\\s*retry\\s*next\\s*at.*and\\s*approximately\\s*every\\s*")
        .append(FAILURE_RETRY_INTERVAL_SECONDS)
        .append("\\s*seconds\\s*afterward\\s*until.*\\s*if\\s*the\\s*failure\\s*is\\s*not\\s*resolved").toString();

    testUntil(() -> findStringInDomainStatusMessage(domainNamespace, domainUid, retryOccurRegex),
        logger, "retry occurs as expected");

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    verifyDomainExistsAndServerStarted(replicaCount);
  }

  /**
   * Create a domain-in-image domain with an invalid domain resource that has duplicate server names
   * the domain should fail to start with SEVERE error and FatalDomainInvalidError
   * the Operator should stop retrying immediately.
   * Also a message is logged into Operator Log indicating FatalDomainInvalidError
   * and the domain Failed condition with the cause, actions to fix the problem.
   * Verify that retry resume after the issue is fixed and the domain starts successfully.
   */
  @Test
  @DisplayName("Create a domain with duplicate server names."
       + "Verify that retry stopped and handles FATAL error as designed.")
  void testRetryOccursAsExpectedAndThrowFatalFailures() {
    int replicaCount = 2;
    String duplicateServerName = "managed-server1";
    // verify that the operator stops retrying immediately when a FATAL error occurs and clear message is logged.
    DomainResource domain = createDomainResourceForRetryTest(FAILURE_RETRY_LIMIT_MINUTES, replicaCount, true);

    // create an invalid domain resource with duplicate server names
    domain.getSpec().addManagedServersItem(new ManagedServer().serverName(duplicateServerName));
    domain.getSpec().addManagedServersItem(new ManagedServer().serverName(duplicateServerName));
    createDomainForRetryTest(domain);

    String fatalDomainInvalidErrorRegex =
        new StringBuffer(".*FatalDomainInvalidError.*More than one.*spec.managedServers.*")
          .append(duplicateServerName)
          .append(".*").toString();

    // verify that FatalDomainInvalidError message found in domain status message
    testUntil(() -> findStringInDomainStatusMessage(domainNamespace, domainUid, fatalDomainInvalidErrorRegex),
        logger, "FatalDomainInvalidError is found in domain status message");

    // verify that FatalDomainInvalidError message found in Operator log
    testUntil(() -> findStringInOperatorLog(opNamespace, fatalDomainInvalidErrorRegex),
        logger, "FatalDomainInvalidError is found in Operator log");

    // patch domain CR to delete duplicate server and have Operator retry
    int introspectVersion = 0;
    if (domain.getSpec().getIntrospectVersion() != null) {
      introspectVersion = Integer.parseInt(domain.getSpec().getIntrospectVersion()) + 1;
    }

    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"remove\",")
        .append(" \"path\": \"/spec/managedServers/0\"},")
        .append("{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"")
        .append(introspectVersion)
        .append("\"}]");
    logger.info("PatchStr for : {0}", patchStr.toString());

    boolean cmPatched = patchDomainResource(domainUid, domainNamespace, patchStr);
    assertTrue(cmPatched, "Patch domain CR to delete duplicate server failed");

    verifyDomainExistsAndServerStarted(replicaCount);
  }

  /**
   * Create a domain-in-image domain with replica count = 6 that exceeds the maximum cluster size
   * of 5. The domain should start but with WARNING message.
   * Also log a clear message in Operator logs and the domain with the cause.
   */
  @Test
  @DisplayName("Create a domain with replica count = 6 that exceeds the maximum cluster size "
      + "Verify domain starts and WARNING message is logged")
  void testRetryOccursAsExpectedAndThrowWarning() {
    int replicaMaxCount = 5;
    int replicaCount = 6;
    // create a domain with replicas = 6 that exceeds the maximum cluster size of 5
    DomainResource domain = createDomainResourceForRetryTest(FAILURE_RETRY_LIMIT_MINUTES, replicaCount, true);
    createDomainForRetryTest(domain);

    String warningMsgRegex = new StringBuffer(".*")
        .append(replicaCount)
        .append("\\s*replicas.*")
        .append(clusterName)
        .append(".*maximum.*size.*")
        .append(replicaMaxCount)
        .append(".*").toString();

    // verify that warningMsgRegex message found in domain status message
    testUntil(() -> findStringInDomainStatusMessage(domainNamespace, domainUid, warningMsgRegex),
        logger, "warningMsgRegex is found in domain status message");

    // verify that WARNING and warningMsgRegex message found in Operator log
    testUntil(() -> findStringInOperatorLog(opNamespace, ".*WARNING" + warningMsgRegex),
        logger, "warningMsgRegex is found in Operator log");

    // verify that the cluster is up and running
    verifyDomainExistsAndServerStarted(replicaMaxCount);

    // reduce replicas back to the maximum cluster size
    StringBuffer patchStr =
        new StringBuffer("[{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": ")
            .append(replicaMaxCount).append("}]");
    V1Patch patch = new V1Patch(patchStr.toString());
    logger.info("Patching the cluster resource using patching string {0}", patchStr);
    String response = patchClusterCustomResourceReturnResponse(clusterName, domainNamespace, patch,
        V1Patch.PATCH_FORMAT_JSON_PATCH);
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching cluster %s in namespace %s failed with error msg: %s",
            clusterName, domainNamespace, response));

    // update introspectVersion to have Operator start retry
    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));
    patchStr = new StringBuffer("[{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"")
        .append(introspectVersion)
        .append("\"}]");
    logger.info("Updating introspectVersion in domain resource using patch string: {0}", patchStr);

    boolean cmPatched = patchDomainResource(domainUid, domainNamespace, patchStr);
    assertTrue(cmPatched, "Patch domain CR to update introspectVersion in domain resource failed");

    // verify that warningMsgRegex message is gone in domain status message
    testUntil(() -> ! findStringInDomainStatusMessage(domainNamespace, domainUid, warningMsgRegex),
        logger, "warningMsgRegex is not found in domain status message");

    // verify that the cluster is still up and running
    verifyDomainExistsAndServerStarted(replicaMaxCount);
  }

  private void verifyDomainExistsAndServerStarted(int replicaCount) {
    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger, "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);

    // check that admin service/pod exists in the domain namespace
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private static DomainResource createDomainResourceForRetryTest(Long failureRetryLimitMinutes,
                                                                 int replicaCount,
                                                                 boolean createSecret) {
    if (createSecret) {
      // create secret for admin credentials
      logger.info("Create secret for admin credentials");
      createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }

    // create image with model files
    logger.info("Creating image with model file and verify");
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    String domainInImageWithWdtImage = createImageAndVerify("domaininimage-wdtimage",
        Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForDomainInImage), appSrcDirList,
        Collections.singletonList(MODEL_DIR + "/" + WDT_BASIC_MODEL_PROPERTIES_FILE),
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false,
        domainUid, false);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domainInImageWithWdtImage);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create the domain custom resource
    DomainResource domain = createDomainResourceForDomainInImage(domainUid, domainNamespace,
        domainInImageWithWdtImage, wlSecretName, clusterName, replicaCount, failureRetryLimitMinutes);
    assertNotNull(domain, "domain is null");

    return domain;
  }

  private static void createDomainForRetryTest(DomainResource domain) {
    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain, DOMAIN_VERSION),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));
  }
}
