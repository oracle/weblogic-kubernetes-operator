// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
import oracle.weblogic.kubernetes.utils.DeployUtil;
import oracle.weblogic.kubernetes.utils.ExecResult;
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
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_GITHUB_CHART_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorContainerImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.checkHelmReleaseRevision;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkAppIsRunning;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

  private static ConditionFactory withStandardRetryPolicy;
  private static ConditionFactory withQuickRetryPolicy;
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

    // create a reusable quick retry policy
    withQuickRetryPolicy = with().pollDelay(0, SECONDS)
        .and().with().pollInterval(4, SECONDS)
        .atMost(10, SECONDS).await();

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
   * Deploy an application to the cluster in domain and verify the application can be
   * accessed while the operator is upgraded and after the upgrade.
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
   * Deploy an application to the cluster in domain and verify the application can be
   * accessed while the operator is upgraded and after the upgrade.
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
   * Operator upgrade from 3.0.2 to latest.
   * Install 3.0.2 Operator from GitHub chart repository and create a domain.
   * Deploy an application to the cluster in domain and verify the application can be
   * accessed while the operator is upgraded and after the upgrade.
   * Upgrade operator with latest Operator image and verify CRD version and image are updated
   * and the domain can be managed by scaling the cluster using operator REST api.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.0.2 to latest")
  @MustNotRunInParallel
  public void testOperatorUpgradeFrom3_0_2(@Namespaces(3) List<String> namespaces) {
    this.namespaces = namespaces;
    upgradeOperator("3.0.2", true);
  }


  /**
   * Operator upgrade from 3.0.3 to latest.
   * Install 3.0.3 Operator from GitHub chart repository and create a domain.
   * Deploy an application to the cluster in domain and verify the application can be
   * accessed while the operator is upgraded and after the upgrade.
   * Upgrade operator with latest Operator image and verify CRD version and image are updated
   * and the domain can be managed by scaling the cluster using operator REST api.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.0.3 to latest")
  @MustNotRunInParallel
  public void testOperatorUpgradeFrom3_0_3(@Namespaces(3) List<String> namespaces) {
    this.namespaces = namespaces;
    upgradeOperator("3.0.3", true);
  }

  /**
   * Cleanup Kubernetes artifacts in the namespaces used by the test and
   * delete CRD.
   */
  @AfterEach
  public void tearDown() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
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
    createDomainHomeInImageAndVerify(
        domainNamespace, operatorVersion, TestConstants.OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);

    if (useHelmUpgrade) {
      // application high availability check only for 3.x releases and later
      // deploy application and access the application once to make sure the app is accessible
      deployAndAccessApplication(domainNamespace);

      // start a new thread to collect the availability data of the application while the
      // main thread performs operator upgrade
      List<Integer> appAvailability = new ArrayList<Integer>();
      logger.info("Start a thread to keep track of the application's availability");
      Thread accountingThread =
          new Thread(
              () -> {
                collectAppAvailability(
                    domainNamespace,
                    opNamespace1,
                    appAvailability,
                    managedServerPodNamePrefix,
                    replicaCount,
                    "8001",
                    "testwebapp/index.jsp");
              });
      accountingThread.start();

      try {
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
      } finally {
        if (accountingThread != null) {
          try {
            accountingThread.join();
          } catch (InterruptedException ie) {
            // do nothing
          }
          // check the application availability data that we have collected, and see if
          // the application has been available all the time during the upgrade
          logger.info("Verify that the application was available when the operator was being upgraded");
          assertTrue(appAlwaysAvailable(appAvailability),
              "Application was not always available when the operator was getting upgraded");
        }
      }
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

  private void createDomainHomeInImageAndVerify(
      String domainNamespace, String operatorVersion, String externalServiceNameSuffix) {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

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
        domainNamespace, getExternalServicePodName(adminServerPodName, externalServiceNameSuffix), "default"),
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

  private void deployAndAccessApplication(String namespace) {
    logger.info("Getting node port for admin server default channel");
    int serviceNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(namespace, getExternalServicePodName(adminServerPodName,
                TestConstants.OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX), "default"),
        "Getting admin server node port failed");
    assertNotEquals(-1, serviceNodePort, "admin server default node port is not valid");

    Path archivePath = Paths.get(ITTESTS_DIR, "../src/integration-tests/apps/testwebapp.war");
    logger.info("Deploying application {0} to domain {1} cluster target cluster-1 in namespace {2}",
        archivePath, domainUid, namespace);
    ExecResult result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST,
        String.valueOf(serviceNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        "cluster-1", archivePath, null, "testwebapp");
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

    // check if the application is accessible inside of a server pod using quick retry policy
    logger.info("Check and wait for the application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppIsRunning(withQuickRetryPolicy, namespace, managedServerPodNamePrefix + i,
          "8001", "testwebapp/index.jsp", managedServerPodNamePrefix + i);
    }
  }

  /**
   * Check application availability while the operator upgrade is happening and once the ugprade is complete
   * by accessing the application inside the managed server pods.
   */
  private static void collectAppAvailability(
      String domainNamespace,
      String operatorNamespace,
      List<Integer> appAvailability,
      String managedServerPrefix,
      int replicaCount,
      String internalPort,
      String appPath
  ) {
    // Access the pod periodically to check application's availability while upgrade is happening
    // and after upgrade is complete.
    // appAccessedAfterUpgrade is used to access the app once after upgrade is complete
    boolean appAccessedAfterUpgrade = false;
    while (!appAccessedAfterUpgrade) {
      boolean isUpgradeComplete = checkHelmReleaseRevision(OPERATOR_RELEASE_NAME, operatorNamespace, "2");
      // upgrade is not complete or app is not accessed after upgrade
      if (!isUpgradeComplete || !appAccessedAfterUpgrade) {
        for (int i = 1; i <= replicaCount; i++) {
          if (appAccessibleInPod(
              domainNamespace,
              managedServerPrefix + i,
              internalPort,
              appPath,
              managedServerPrefix + i)) {
            appAvailability.add(1);
            logger.fine("application is accessible in pod " + managedServerPrefix + i);
          } else {
            appAvailability.add(0);
            logger.fine("application is not accessible in pod " + managedServerPrefix + i);
          }
        }
      }
      if (isUpgradeComplete) {
        logger.info("Upgrade is complete and app is accessed after upgrade");
        appAccessedAfterUpgrade = true;
      }

    }
  }

  private static boolean appAlwaysAvailable(List<Integer> appAvailability) {
    for (Integer count : appAvailability) {
      if (count == 0) {
        logger.warning("Application was not available during operator upgrade.");
        return false;
      }
    }
    return true;
  }

}
