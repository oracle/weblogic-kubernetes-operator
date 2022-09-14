// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainResourceForDomainInImage;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates WebLogic domains with domain-in-image ( using WDT )
 * Verify that WKO Retry Improvements handles Severe Failures as designed.
 * Also verify admin console login using admin node port.
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
  private static final int replicaCount = 2;
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

    // get unique namespaces for three different type of domains
    logger.info("Getting unique namespaces for three different type of domains");
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
    deleteDomainResource(domainNamespace, domainUid);
  }

  /**
   * Create a domain-in-image domain before the secret for admin credentials is created,
   * the domain should fail to start with SEVERE error and the Operator should start retrying
   * in a specified failure.retry.interval.seconds and failure.retry.limit.minutes.
   * Also log a clear message in the domain Failed condition with the cause,
   * actions to fix the problem, and indicates that the next retry time and when the retry will stop.
   */
  //@Disabled("")
  @Test
  @DisplayName("Create a domain without WLS secret. Verify that retry occurs and handles SEVERE error as designed.")
  void testRetryOccursAsExpectedAndThrowSevereFailures() {
    // verify that the operator starts retrying in the intervals specified in domain.spec.failureRetryIntervalSeconds
    // when a SEVERE error occurs and clear message is logged.
    Long failureRetryLimitMinutes = Long.valueOf("1");
    createDomainWithoutWlsSecret(failureRetryLimitMinutes);

    String retryOccurRegex = new StringBuffer(".*WebLogicCredentials.*\\s*secret.*")
        .append(wlSecretName)
        .append(".*not\\s*found\\s*in\\s*namespace\\s*.*")
        .append(domainNamespace)
        .append(".*Will\\s*retry\\s*next\\s*at.*and\\s*approximately\\s*every\\s*")
        .append(FAILURE_RETRY_INTERVAL_SECONDS)
        .append("\\s*seconds\\s*afterward\\s*until.*\\s*if\\s*the\\s*failure\\s*is\\s*not\\s*resolved").toString();

    testUntil(() -> findStringInDomainMessage(retryOccurRegex), logger, "retry occurs as expected");

    // verify that the operator stops retrying when the maximum retry time is reached
    String retryMaxValueRegex = new StringBuffer(".*operator\\s*failed\\s*after\\s*retrying\\s*for\\s*.*")
        .append(failureRetryLimitMinutes)
        .append(".*\\s*minutes.*\\s*Please\\s*resolve.*error\\s*and.*update\\s*domain.spec.introspectVersion")
        .append(".*to\\s*force\\s*another\\s*retry\\s*.*").toString();

    testUntil(() -> findStringInDomainMessage(retryMaxValueRegex),
        logger, "retry ends as expected after {0} minutes retry", failureRetryLimitMinutes);

    // verify that SEVERE level error message is logged in the Operator log
    String opLogSevereErrRegex = new StringBuffer(".*SEVERE")
        .append(".*WebLogicCredentials.*\\s*secret.*")
        .append(wlSecretName)
        .append(".*not\\s*found\\s*in\\s*namespace\\s*.*")
        .append(domainNamespace).toString();

    testUntil(() -> findStringInOperatorLog(opLogSevereErrRegex), logger, "SEVERE error found in Operator log");

    // restore env
    //deleteDomainResource(domainNamespace, domainUid);
  }

  /**
   * Create a domain-in-image domain before the secret for admin credentials is created.
   * Verify that retry stops after the issue is fixed and the domain starts successfully.
   */
  @Test
  @DisplayName("Verify that retry stops after the issue is fixed and the domain starts successfully.")
  void testRetryStoppedAfterIssueFixed() {
    // verify that the operator starts retrying when a SEVERE error occurs
    Long failureRetryLimitMinutes = Long.valueOf("5");
    createDomainWithoutWlsSecret(failureRetryLimitMinutes);

    String retryOccurRegex = new StringBuffer(".*WebLogicCredentials.*\\s*secret.*")
        .append(wlSecretName)
        .append(".*not\\s*found\\s*in\\s*namespace\\s*.*")
        .append(domainNamespace)
        .append(".*Will\\s*retry\\s*next\\s*at.*and\\s*approximately\\s*every\\s*")
        .append(FAILURE_RETRY_INTERVAL_SECONDS)
        .append("\\s*seconds\\s*afterward\\s*until.*\\s*if\\s*the\\s*failure\\s*is\\s*not\\s*resolved").toString();

    testUntil(() -> findStringInDomainMessage(retryOccurRegex), logger, "retry occurs as expected");

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
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

  private boolean findStringInDomainMessage(String regex) {
    // get the domain status message
    StringBuffer getDomainInfoCmd = new StringBuffer("kubectl get domain/");
    getDomainInfoCmd
        .append(domainUid)
        .append(" -n ")
        .append(domainNamespace)
        .append(" -o jsonpath='{.status.message}' --ignore-not-found");
    logger.info("Command to get domain status message: " + getDomainInfoCmd);

    CommandParams params = new CommandParams().defaults();
    params.command(getDomainInfoCmd.toString());
    ExecResult execResult = Command.withParams(params).executeAndReturnResult();
    logger.info("Search: {0} in Domain status message: {1}", regex, execResult.stdout());

    // match regex in domain info
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(execResult.stdout());

    return matcher.find();
  }

  private boolean findStringInOperatorLog(String regex) {
    // get operator pod name
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertNotNull(operatorPodName, "Operator pod name returned is null");
    logger.info("Operator pod name {0}", operatorPodName);

    // get the Operator logs
    StringBuffer getOpLogsCmd = new StringBuffer("kubectl logs ");
    getOpLogsCmd
        .append(operatorPodName)
        .append(" -n ")
        .append(opNamespace)
        .append(" --since=30s");
    logger.info("Command to get Operator log: " + getOpLogsCmd);

    CommandParams params = new CommandParams().defaults();
    params.command(getOpLogsCmd.toString());
    ExecResult execResult = Command.withParams(params).executeAndReturnResult();
    logger.info("Search: {0} in Operator log", regex);

    // match regex in Operator log
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(execResult.stdout());

    return matcher.find();
  }

  private static Domain createDomainWithoutWlsSecret(Long failureRetryLimitMinutes) {
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
    Domain domain = createDomainResourceForDomainInImage(domainUid, domainNamespace,
        domainInImageWithWdtImage, wlSecretName, clusterName, replicaCount, failureRetryLimitMinutes);

    // create the domain CR
    assertNotNull(domain, "domain is null");

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain, DOMAIN_VERSION),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    return domain;
  }
}
