// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test cases for "Dedicated" namespace scenarios
 * 1. Set "dedicated" to true, verify that the domains deployed in the operator's namespace come up.
 * 2. Set "dedicated" to true, verify that the domains not deployed in the operator's namespace does not come up.
 * 3. Scale the cluster with "dedicated" set to true
 * 4. Negative tests
 *    1) The CRD is not present or is deleted, verify that Operator fails with error
 *    2) The CRD is present but is a lower than expected version, verify that Operator fails with error
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test Operator and WebLogic domain with Dedicated set to true")
@IntegrationTest
class ItDedicatedMode {
  // namespace constants
  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;

  // domain constants
  private final String domainUid = "domain1";
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPodPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

  // operator constants
  private static HelmParams opHelmParams;
  private static String opServiceAccount;
  private static final String domainNamespaceSelectionStrategy = "Dedicated";

  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator and domain2.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // in the dedicated mode, the operator only manages domains in the operator's own namespace
    domain1Namespace = opNamespace;

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain2Namespace = namespaces.get(1);

    // Variables for Operator
    opServiceAccount = opNamespace + "-sa";
    opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
  }

  @AfterAll
  public void tearDownAll() {
  }

  /**
   * Set "dedicated" to true and set the domainNamespaces to something that does not contain the operator's namespace,
   * make sure that the domains in the operator's target namespaces do not come up
   * because when dedicated is set to true, the operator's domainNamespaces value is ignored.
   * 1) Install an Operator with namespace=op-ns, domainNamespaces=wls-ns
   *    and domainNamespaceSelectionStrategy=Dedicated.
   * 2) Verify the Operator is up and running.
   * 3) Create WebLogic Domain its namespace=wls-ns.
   * 4) Verify that domain whose namespace = wls-ns does not come up.
   */
  @Test
  @Order(1)
  @DisplayName("Set dedicated to true and verify that a domain not deployed in operator's namespace doesn't come up")
  public void testDedicatedModeDiffNamespace() {
    // install and verify operator
    logger.info("Installing and verifying operator");
    installAndVerifyOperator(opNamespace, opNamespace + "-sa",
        true, 0, opHelmParams, domainNamespaceSelectionStrategy,
        false, domain2Namespace);

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    createDomain(domain2Namespace);

    verifyDomainNotRunning(domain2Namespace);
  }

  /**
   * Set "dedicated" to true, make sure that the domains deployed in the operator's namespace come up.
   * 1) Using the Operator's namespace, op-wls-ns, create a WebLogic Domain
   * 2) Verify that the WebLogic domain whose namespace = op-wls-ns comes up.
   */
  @Test
  @Order(2)
  @DisplayName("Set dedicated to true and verify that the domain deployed in the operator's namespace comes up")
  public void testDedicatedModeSameNamespace() {
    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    createDomain(domain1Namespace);
    verifyDomainRunning(domain1Namespace);
  }

  /**
   * Scale up cluster-1 in domain1Namespace and verify it succeeds.
   */
  @Test
  @Order(3)
  @DisplayName("Scale up cluster-1 in domain1Namespace and verify it succeeds")
  public void testDedicatedModeSameNamespaceScale() {
    // scale the cluster and check domain can be managed from the operator
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);

    logger.info("scaling the cluster from {0} servers to {1} servers", replicaCount, replicaCount + 1);
    scaleAndVerifyCluster(clusterName, domainUid, domain1Namespace,
        managedServerPodPrefix, replicaCount, replicaCount + 1,
        true, externalRestHttpsPort, opNamespace, opNamespace + "-sa",
        false, "", "", 0, "",
        "", null, null);
  }

  /**
   * Test when a CRD with a lower than expected version is present,
   * Operator fails with error if it has not permission to overwrite the CRD.
   */
  @Test
  @Order(4)
  @Disabled("Disable the test because the Operator has permission to overwrite the CRD")
  @DisplayName("Create a CRD with a lower than expected version and verify that Operator fails with error")
  public void testDedicatedModeNlowerVersionCrd() {
    // delete existing CRD
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    // install a lower version of CRD, v2.6.0
    new Command()
        .withParams(new CommandParams()
            .command("kubectl create -f " + ITTESTS_DIR + "/../kubernetes/crd/domain-v1beta1-crdv7-260.yaml"))
        .execute();

    try {
      // install latest version of operator and verify
      logger.info("Installing and verifying operator");
      installAndVerifyOperator(opNamespace, opNamespace + "-sa",
          false, 0, opHelmParams, domainNamespaceSelectionStrategy,
          false, domain2Namespace);

      // we expect installAndVerifyOperator fails with a lower than expected version of CRD
      fail("Installing the Operator should fail with a lower than expected version of CRD");
    } catch (Exception ex) {
      logger.info("Installing the Operator with a lower than expected version of CRD failed as expected");
    } finally {
      // restore the test env
      uninstallOperatorAndVerify();
    }
  }

  /**
   * Test when she CRD is not present or is deleted,
   * Operator fails with error if it has not permission to create the CRD.
   */
  @Test
  @Order(5)
  @Disabled("Disable the test because the Operator has permission to create the CRD")
  @DisplayName("Delete the CRD and verify that Operator fails with error")
  public void testDedicatedModeNoCrd() {
    // delete existing CRD
    logger.info("Delete existing CRD");
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    try {
      // install and verify operator
      logger.info("Installing and verifying operator");
      installAndVerifyOperator(opNamespace, opNamespace + "-sa",
          false, 0, opHelmParams, domainNamespaceSelectionStrategy,
          false, domain2Namespace);

      // we expect installAndVerifyOperator fails when the CRD misses
      fail("Installing the Operator should fail when the CRD misses");
    } catch (Exception ex) {
      logger.info("Installing the Operator failed as expected when the CRD misses");
    } finally {
      // restore the test env
      uninstallOperatorAndVerify();
    }
  }

  private void createDomain(String domainNamespace) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        "weblogic", "welcome1"),
          String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc"),
          String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainNamespace, domainUid, domainNamespace);
    createDomainCrAndVerify(domainNamespace, adminSecretName, encryptionSecretName);
  }

  private void createDomainCrAndVerify(String domainNamespace,
                                       String adminSecretName,
                                       String encryptionSecretName) {
    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // construct a list of oracle.weblogic.domain.Cluster objects to be used in the domain custom resource
    List<Cluster> clusters = new ArrayList<>();
    clusters.add(new Cluster()
        .clusterName(clusterName)
        .replicas(replicaCount)
        .serverStartState("RUNNING"));

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .clusters(clusters)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private void verifyDomainRunning(String domainNamespace) {
    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private void verifyDomainNotRunning(String domainNamespace) {
    // check that admin server pod doesn't exists in the domain namespace
    logger.info("Checking that admin server pod {0} doesn't exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that managed server pod doesn't exists in the domain namespace
      logger.info("Checking that managed server pod {0} doesn't exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private void uninstallOperatorAndVerify() {
    // uninstall operator
    assertTrue(uninstallOperator(opHelmParams),
        String.format("Uninstall operator failed in namespace %s", opNamespace));

    // delete service account
    assertTrue(deleteServiceAccount(opServiceAccount,opNamespace),
        String.format("Delete service acct %s failed in namespace %s", opServiceAccount, opNamespace));

    // delete secret/ocir-secret
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete secret/ocir-secret -n " + opNamespace + " --ignore-not-found"))
        .execute();
  }
}
