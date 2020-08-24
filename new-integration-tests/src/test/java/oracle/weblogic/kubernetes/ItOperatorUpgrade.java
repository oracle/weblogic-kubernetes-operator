// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CleanupUtil;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_GITHUB_CHART_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorContainerImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operator upgrade tests.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Operator upgrade tests")
@IntegrationTest
@MustNotRunInParallel
public class ItOperatorUpgrade {

  private static ConditionFactory withStandardRetryPolicy = null;
  private static Map<String, Object> secretNameMap;
  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private String adminServerPodName = domainUid + "-admin-server";
  private String managedServerPodNamePrefix = domainUid + "-managed-server";
  private int replicaCount = 2;
  private List<String> namespaces;
  private String latestOperatorImageName;

  /**
   * Does some initialization of logger, conditionfactory, etc common
   * to all test methods.
   */
  @BeforeAll
  public static void init() {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(10, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

  }

  /**
   * Operator upgrade from 2.5.0 to latest.
   * Install 2.5.0 release Operator from GitHub chart repository and create a domain.
   * Delete Operator and install latest Operator and verify CRD version is updated
   * and the domain can be managed by scaling the cluster using operator REST api.
   */
  @Test
  @DisplayName("Upgrade Operator from 2.5.0 to latest")
  @MustNotRunInParallel
  public void testOperatorUpgradeFrom2_5_0(@Namespaces(3) List<String> namespaces) {
    this.namespaces = namespaces;
    upgradeOperator("2.5.0", false);
  }

  /**
   * Operator upgrade from 2.6.0 to latest.
   * Install 2.6.0 Operator from GitHub chart repository and create a domain.
   * Delete Operator and install latest Operator and verify CRD version is updated
   * and the domain can be managed by scaling the cluster using operator REST api.
   */
  @Test
  @DisplayName("Upgrade Operator from 2.6.0 to latest")
  @MustNotRunInParallel
  public void testOperatorUpgradeFrom2_6_0(@Namespaces(3) List<String> namespaces) {
    this.namespaces = namespaces;
    upgradeOperator("2.6.0", false);
  }

  /**
   * Operator upgrade from 3.0.0 to latest.
   * Install 3.0.0 Operator from GitHub chart repository and create a domain.
   * Upgrade operator with latest Operator image and verify CRD version and image are updated
   * and the domain can be managed by scaling the cluster using operator REST api.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.0.0 to latest")
  @MustNotRunInParallel
  public void testOperatorUpgradeFrom3_0_0(@Namespaces(3) List<String> namespaces) {
    this.namespaces = namespaces;
    upgradeOperator("3.0.0", true);
  }

  /**
   * Operator upgrade from 3.0.1 to latest.
   * Install 3.0.1 Operator from GitHub chart repository and create a domain.
   * Upgrade operator with latest Operator image and verify CRD version and image are updated
   * and the domain can be managed by scaling the cluster using operator REST api.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.0.1 to latest")
  @MustNotRunInParallel
  public void testOperatorUpgradeFrom3_0_1(@Namespaces(3) List<String> namespaces) {
    this.namespaces = namespaces;
    upgradeOperator("3.0.1", true);
  }

  /**
   * Cleanup Kubernetes artifacts in the namespaces used by the test and
   * delete CRD.
   */
  @AfterEach
  public void tearDown() {
    if (System.getenv("SKIP_CLEANUP") == null) {
      CleanupUtil.cleanup(namespaces);
      new Command()
          .withParams(new CommandParams()
              .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
          .execute();
    }
  }

  private void upgradeOperator(String operatorVersion, boolean useHelmUpgrade) {
    logger.info("Assign a unique namespace for operator {0}", operatorVersion);
    assertNotNull(namespaces.get(0), "Namespace is null");
    final String opNamespace1 = namespaces.get(0);
    logger.info("Assign a unique namespace for latest operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    final String opNamespace2 = namespaces.get(1);
    logger.info("Assign a unique namespace for domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    String domainNamespace = namespaces.get(2);
    latestOperatorImageName = getOperatorImageName();

    // delete existing CRD
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    HelmParams opHelmParams =
        new HelmParams().releaseName("weblogic-operator")
            .namespace(opNamespace1)
            .repoUrl(OPERATOR_GITHUB_CHART_REPO_URL)
            .repoName("weblogic-operator")
            .chartName("weblogic-operator")
            .chartVersion(operatorVersion);

    // install operator
    String opNamespace = opNamespace1;
    String opServiceAccount = opNamespace + "-sa";
    installAndVerifyOperator(opNamespace, opServiceAccount, true,
        0, opHelmParams, domainNamespace);

    // create domain
    createDomainHomeInImageAndVerify(domainNamespace, operatorVersion);

    if (useHelmUpgrade) {
      // upgrade to latest operator
      HelmParams upgradeHelmParams = new HelmParams()
          .releaseName(OPERATOR_RELEASE_NAME)
          .namespace(opNamespace)
          .chartDir(OPERATOR_CHART_DIR)
          .repoUrl(null)
          .chartVersion(null)
          .chartName(null);

      // operator chart values
      OperatorParams opParams = new OperatorParams()
          .helmParams(upgradeHelmParams)
          .image(latestOperatorImageName)
          .externalRestEnabled(true);

      assertTrue(upgradeAndVerifyOperator(opNamespace, opParams),
          String.format("Failed to upgrade operator in namespace %s", opNamespace));

      // check operator image name after upgrade
      logger.info("Checking image name in operator container ");
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Checking operator image name in namespace {0} after upgrade "
                      + "(elapsed time {1}ms, remaining time {2}ms)",
                  opNamespace1,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> getOpContainerImageName(opNamespace1),
              "Exception while getting the operator image name"));

    } else {
      opNamespace = opNamespace2;
      opServiceAccount = opNamespace2 + "-sa";

      // uninstall operator 2.5.0/2.6.0
      assertTrue(uninstallOperator(opHelmParams),
          String.format("Uninstall operator failed in namespace %s", opNamespace1));

      // install latest operator
      installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);
    }
    // check CRD version is updated
    logger.info("Checking CRD version ");
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for the CRD version to be updated to v8 "
                    + "(elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(checkCrdVersion());

    // restart domain for 2.5.0 for scaling to work, see OWLS-83813
    if (operatorVersion.equals("2.5.0")) {
      shutdownDomain(domainUid, domainNamespace);
      logger.info("Checking for admin server pod shutdown");
      checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);
      logger.info("Checking managed server pods were shutdown");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
      }

      startDomain(domainUid, domainNamespace);
      checkDomainStarted(domainUid, domainNamespace);
    }

    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    assertTrue(externalRestHttpsPort != -1,
        "Could not get the Operator external service node port");
    logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);

    // check domain can be managed from the operator by scaling the cluster
    scaleAndVerifyCluster("cluster-1", domainUid, domainNamespace,
        managedServerPodNamePrefix, replicaCount, 3,
        true, externalRestHttpsPort, opNamespace, opServiceAccount,
        false, "", "", 0, "", "", null, null);

    scaleAndVerifyCluster("cluster-1", domainUid, domainNamespace,
        managedServerPodNamePrefix, replicaCount, 1,
        true, externalRestHttpsPort, opNamespace, opServiceAccount,
        false, "", "", 0, "", "", null, null);
  }

  private void createDomainHomeInImageAndVerify(String domainNamespace, String operatorVersion) {

    // Create the repo secret to pull the image
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // use the checked in domain.yaml to create domain for old releases
    // copy domain.yaml to results dir
    Path srcDomainYaml = Paths.get(RESOURCE_DIR, "domain", "domain-260.yaml");
    assertDoesNotThrow(() -> Files.createDirectories(
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName())),
        String.format("Could not create directory under %s", RESULTS_ROOT));
    Path destDomainYaml =
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName() + "/" + "domain.yaml");
    assertDoesNotThrow(() -> Files.copy(srcDomainYaml, destDomainYaml, REPLACE_EXISTING),
        "File copy failed for domain.yaml");

    // replace apiVersion, namespace and image in domain.yaml
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "v7", getApiVersion(operatorVersion)),
        "Could not modify the apiVersion in the domain.yaml file");
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "weblogic-domain260", domainNamespace),
        "Could not modify the namespace in the domain.yaml file");
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "domain-home-in-image:12.2.1.4",
        WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG),
        "Could not modify image name in the domain.yaml file");

    assertTrue(new Command()
        .withParams(new CommandParams()
            .command("kubectl create -f " + destDomainYaml))
        .execute(), "kubectl create failed");

    checkDomainStarted(domainUid, domainNamespace);

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, adminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() -> {
      return adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }, "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");

  }

  private Callable<Boolean> checkCrdVersion() {
    return () -> {
      return new Command()
          .withParams(new CommandParams()
              .command("kubectl get crd domains.weblogic.oracle -o "
                  + "jsonpath='{.spec.versions[?(@.storage==true)].name}'"))
          .executeAndVerify(TestConstants.DOMAIN_VERSION);
    };
  }

  private String getApiVersion(String operatorVersion) {
    String apiVersion = null;
    switch (operatorVersion) {
      case "2.5.0":
        apiVersion = "v6";
        break;
      case "2.6.0":
        apiVersion = "v7";
        break;
      default:
        apiVersion = TestConstants.DOMAIN_VERSION;
    }
    return apiVersion;
  }

  private void checkDomainStarted(String domainUid, String domainNamespace) {
    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }
  }

  private Callable<Boolean> getOpContainerImageName(String namespace) {
    return () -> {
      String imageName = getOperatorContainerImageName(namespace);
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

}
