// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_GITHUB_CHART_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operator upgrade tests.
 */
@DisplayName("Operator upgrade tests")
@IntegrationTest
public class ItOperatorUpgrade {

  private static ConditionFactory withStandardRetryPolicy = null;
  private static Map<String, Object> secretNameMap;
  private static LoggingFacade logger = null;

  /**
   * Does some initialization of logger, conditionfactory, etc common
   * to all test methods.
   */
  @BeforeAll
  public static void init() {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

  }

  /**
   * Operator upgrade from 2.6.0 to latest.
   * Install Operator 2.6.0 from GitHub chart repository and create a domain.
   * Delete 2.6.0 Operator and install latest Operator and verify CRD version is updated
   * and the domain can be managed by scaling the domain.
   */
  @Test
  @DisplayName("Upgrade Operator 2.6.0 to latest")
  @MustNotRunInParallel
  public void testUpgradeOperatorFrom2_6_0(@Namespaces(3) List<String> namespaces) {
    logger.info("Assign a unique namespace for operator 2.6.0");
    assertNotNull(namespaces.get(0), "Namespace is null");
    final String opNamespace1 = namespaces.get(0);
    logger.info("Assign a unique namespace for latest operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    final String opNamespace2 = namespaces.get(1);
    logger.info("Assign a unique namespace for domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    String domainNamespace  = namespaces.get(2);

    HelmParams opHelmParams =
        new HelmParams().releaseName("weblogic-operator-260")
            .namespace(opNamespace1)
            .repoUrl(OPERATOR_GITHUB_CHART_REPO_URL)
            .repoName("weblogic-operator")
            .chartName("weblogic-operator")
            .chartVersion("2.6.0");

    // install operator
    installAndVerifyOperator(opNamespace1, opHelmParams, domainNamespace);

    // create domain
    createDomainHomeInImageAndVerify(domainNamespace);

    // uninstall operator 2.6.0
    uninstallOperator(opHelmParams);

    // install latest operator
    installAndVerifyOperator(opNamespace2, domainNamespace);

    // check CRD version is updated
    checkCrdVersion();

    // check domain can be managed from the operator by scaling the cluster

  }

  private void createDomainHomeInImageAndVerify(String domainNamespace) {
    String domainUid = "domain1";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPodNamePrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // replace namespace and image in domain.yaml
    Path srcDomainYaml = Paths.get(RESOURCE_DIR, "domain", "domain-260.yaml");
    assertDoesNotThrow(() -> Files.createDirectories(
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName())),
        String.format("Could not create directory under %s", RESULTS_ROOT));
    Path destDomainYaml =
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName() + "/" + "domain-260.yaml");
    assertDoesNotThrow(() -> Files.copy(srcDomainYaml, destDomainYaml, REPLACE_EXISTING),
        "File copy failed for domain-260.yaml");
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "weblogic-domain260", domainNamespace), "Could not modify the domain.yaml file");
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "domain-home-in-image:12.2.1.4",
        WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG), "Could not modify the domain.yaml file");

    assertTrue(new Command()
        .withParams(new CommandParams()
            .command("kubectl create -f " + destDomainYaml))
        .execute(), "kubectl create failed");

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

  private void checkCrdVersion() {
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command("kubectl get crd domains.weblogic.oracle -o jsonpath='{.spec.versions[?(@.storage==true)].name}'"))
        .executeAndVerify("v8"), "CRD version is not updated to v8");
  }
}
