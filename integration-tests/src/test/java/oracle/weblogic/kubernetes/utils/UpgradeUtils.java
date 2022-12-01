// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_PROGRESSING_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_GITHUB_CHART_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorContainerImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.assertions.impl.Domain.doesCrdExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Upgrade utility class for tests.
 */
public class UpgradeUtils {

  public static final String OLD_DOMAIN_VERSION = "v8";
  private static LoggingFacade logger = null;

  /**
   * Install a released WebLogic Kubernates Operator Version.
   */
  public static void installOldOperator(String operatorVersion, String opNamespace, String domainNamespace) {
    logger = getLogger();
    assertNotNull(opNamespace, "Operator Namespace is null");
    assertNotNull(opNamespace, "Domain Namespace is null");

    // install operator with older release
    HelmParams opHelmParams = installOperator(operatorVersion,
                 opNamespace, domainNamespace);
  }

  private static HelmParams installOperator(String operatorVersion,
      String opNamespace, String domainNamespace) {
    // delete existing CRD if any
    cleanUpCRD();

    // build Helm params to install the Operator
    HelmParams opHelmParams =
        new HelmParams().releaseName("weblogic-operator")
            .namespace(opNamespace)
            .repoUrl(OPERATOR_GITHUB_CHART_REPO_URL)
            .repoName("weblogic-operator")
            .chartName("weblogic-operator")
            .chartVersion(operatorVersion);

    // install operator with passed version
    String opServiceAccount = opNamespace + "-sa";
    installAndVerifyOperator(opNamespace, opServiceAccount, true,
        0, opHelmParams, domainNamespace);

    return opHelmParams;
  }

  /**
   * upgrade to operator to current version.
   */
  public static void upgradeOperatorToCurrent(String opNamespace, String domainNamespace, String domainUid) {
    String latestOperatorImageName = getOperatorImageName();
    HelmParams upgradeHelmParams = new HelmParams()
            .releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR)
            .repoUrl(null)
            .chartVersion(null)
            .chartName(null);

    // build operator chart values
    OperatorParams opParams = new OperatorParams()
            .helmParams(upgradeHelmParams)
            .image(latestOperatorImageName)
            .externalRestEnabled(true);

    assertTrue(upgradeAndVerifyOperator(opNamespace, opParams),
            String.format("Failed to upgrade operator in namespace %s", opNamespace));

    // check operator image name after upgrade
    logger.info("Checking image name in operator container ");
    testUntil(
            assertDoesNotThrow(() -> getOpContainerImageName(opNamespace),
              "Exception while getting the operator image name"),
            logger,
            "Checking operator image name in namespace {0} after upgrade",
            opNamespace);

    // check CRD version is updated
    logger.info("Checking CRD version");
    testUntil(
          checkCrdVersion(),
          logger,
          "the CRD version to be updated to current");
    // check domain status conditions
    checkDomainStatus(domainNamespace,domainUid);
  }

  private static Callable<Boolean> getOpContainerImageName(String namespace) {
    return () -> {
      String imageName = getOperatorContainerImageName(namespace);
      String latestOperatorImageName = getOperatorImageName();
      if (imageName != null) {
        if (!imageName.equals(latestOperatorImageName)) {
          logger.info("Operator image name {0} doesn't match with latest image {1}",
              imageName, latestOperatorImageName);
          return false;
        } else {
          logger.info("Operator image name {0}", imageName);
          return true;
        }
      }
      return false;
    };
  }

  /**
   * check the crd (custom resource definition) version.
   */
  public static Callable<Boolean> checkCrdVersion() {
    return () -> Command
        .withParams(new CommandParams()
            .command(KUBERNETES_CLI + " get crd domains.weblogic.oracle -o "
                + "jsonpath='{.spec.versions[?(@.storage==true)].name}'"))
        .executeAndVerify(DOMAIN_VERSION);
  }

  /**
   * check the domain resource status.
   */
  public static void checkDomainStatus(String domainNamespace, String domainUid) {

    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, OLD_DOMAIN_VERSION);
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", OLD_DOMAIN_VERSION);
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_FAILED_TYPE, OLD_DOMAIN_VERSION);
    // verify there is no status condition type Progressing
    verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_PROGRESSING_TYPE, OLD_DOMAIN_VERSION);
  }


  /**
   * Install a released WebLogic Kubernates Operator Version.
   */
  public static void cleanUpCRD() {
    boolean doesCrdExist = doesCrdExist();

    if (doesCrdExist) {
      Command
              .withParams(new CommandParams()
                      .command(KUBERNETES_CLI + " patch crd/domains.weblogic.oracle"
                              + " -p '{\"metadata\":{\"finalizers\":[]}}' --type=merge"))
              .execute();
      Command
              .withParams(new CommandParams()
                      .command(KUBERNETES_CLI + " delete crd domains.weblogic.oracle --ignore-not-found"))
              .execute();
    }
  }
}
