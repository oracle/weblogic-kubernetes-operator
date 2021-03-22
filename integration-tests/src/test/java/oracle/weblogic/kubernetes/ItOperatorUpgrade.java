// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CleanupUtil;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.weblogic.kubernetes.TestConstants.OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
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
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.collectAppAvailability;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.deployAndAccessApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
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
 * Install a released version of Operator from GitHub chart repository.
 * Create a domain using Domain-In-Image model with a dynamic cluster.
 * Deploy an application to the cluster in domain and verify the application 
 * can be accessed while the operator is upgraded and after the upgrade.
 * Upgrade operator with latest Operator image from develop branch.
 * Verify CRD version and image are updated.
 * Scale the cluster in upgraded environment.
 * Restart the entire domain in upgraded environment.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Operator upgrade tests")
@IntegrationTest
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
   * For each test:
   * Assigns unique namespaces for operator and domain.
   * @param namespaces injected by JUnit
   */
  @BeforeEach
  public void beforeEach(@Namespaces(3) List<String> namespaces) {
    this.namespaces = namespaces;
    assertNotNull(namespaces.get(0), "Namespace[0] is null");
    assertNotNull(namespaces.get(1), "Namespace[1] is null");
    assertNotNull(namespaces.get(2), "Namespace[2] is null");
  }
  
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
   * Operator upgrade from 2.6.0 to latest.
   */
  @Test
  @DisplayName("Upgrade Operator from 2.6.0 to develop")
  public void testOperatorWlsUpgradeFrom260ToDevelop() {
    upgradeOperator("2.6.0", OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX,  false);
  }

  /**
   * Operator upgrade from 3.0.3 to latest.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.0.3 to develop")
  public void testOperatorWlsUpgradeFrom303ToDevelop() {
    upgradeOperator("3.0.3", OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX, true);
  }

  /**
   * Operator upgrade from 3.0.4 to latest.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.0.4 to develop")
  public void testOperatorWlsUpgradeFrom304ToDevelop() {
    upgradeOperator("3.0.4", OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX, true);
  }

  /**
   * Operator upgrade from 3.1.2 to latest.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.1.2 to develop")
  public void testOperatorWlsUpgradeFrom312ToDevelop() {
    upgradeOperator("3.1.2", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX, true);
  }

  /**
   * Operator upgrade from 3.1.3 to latest.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.1.3 to develop")
  public void testOperatorWlsUpgradeFrom313ToDevelop() {
    upgradeOperator("3.1.3", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX, true);
  }

  /**
   * Operator upgrade from 3.1.4 to latest.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.1.4 to develop")
  public void testOperatorWlsUpgradeFrom314ToDevelop() {
    upgradeOperator("3.1.4", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX, true);
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

  // Since Operator version 3.1.0 the service pod prefix has been changed 
  // from -external to -ext e.g.
  // domain1-adminserver-ext  NodePort    10.96.46.242   30001:30001/TCP 
  private void upgradeOperator(String operatorVersion, String externalServiceNameSuffix, boolean useHelmUpgrade) {
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
        domainNamespace, operatorVersion, externalServiceNameSuffix);
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i, getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }

    if (useHelmUpgrade) {
      // deploy application and access the application once to make sure the app is accessible
      deployAndAccessApplication(domainNamespace,
                                 domainUid,
                                "cluster-1",
                                "admin-server",
                                 adminServerPodName,
                                 managedServerPodNamePrefix,
                                 replicaCount,
                                "7001",
                                "8001");

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
                    adminServerPodName,
                    managedServerPodNamePrefix,
                    replicaCount,
                    "7001",
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
        verifyPodsNotRolled(domainNamespace, pods);
      } finally {
        if (accountingThread != null) {
          try {
            accountingThread.join();
          } catch (InterruptedException ie) {
            // do nothing
          }
          // check the app availability data that we have collected, and see if
          // the application has been available all the time during the upgrade
          logger.info("Verify that application was available during upgrade");
          assertTrue(appAlwaysAvailable(appAvailability),
              "Application was not always available during operator upgrade");
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
        managedServerPodNamePrefix, replicaCount, 2,
        true, externalRestHttpsPort, opNamespace, opServiceAccount,
        false, "", "", 0, "", "", null, null);

    restartDomain(domainUid, domainNamespace);
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

  private void checkDomainStopped(String domainUid, String domainNamespace) {
    // verify admin server pod is deleted
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    // verify managed server pods are deleted
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be deleted in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodDeleted(managedServerPodNamePrefix + i, domainUid, domainNamespace);
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

  private static boolean appAlwaysAvailable(List<Integer> appAvailability) {
    for (Integer count : appAvailability) {
      if (count == 0) {
        logger.warning("Application was not available during operator upgrade.");
        return false;
      }
    }
    return true;
  }

  /**
   * Restart the domain after upgrade by changing serverStartPolicy. 
   */
  private void restartDomain(String domainUid, String domainNamespace) {

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/serverStartPolicy", "NEVER"),
         "Failed to patch Domain's serverStartPolicy to NEVER");
    logger.info("Domain is patched to shutdown");
    checkDomainStopped(domainUid, domainNamespace);

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/serverStartPolicy", "IF_NEEDED"),
         "Failed to patch Domain's serverStartPolicy to IF_NEEDED");
    logger.info("Domain is patched to re start");
    checkDomainStarted(domainUid, domainNamespace);
  }

}
