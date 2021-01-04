// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The current class verifies various use cases related to domainNamespaceSelectionStrategy.
 * For more detail regarding the feature, please refer to
 * https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/docs-source/content/
 * userguide/managing-operators/using-the-operator/using-helm.md#overall-operator-information.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test Operator and WebLogic domain with Dedicated set to true")
@IntegrationTest
class ItDedicatedMode {
  // namespace constants
  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;

  private static final String CRD_V16 = "domain-crd.yaml";
  private static final String CRD_V15 = "domain-v1beta1-crd.yaml";

  // domain constants
  private final String domainUid = "dedicated-domain-1";
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
   * Get namespaces for operator and domain2. Create CRD based on the k8s version.
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

    // get k8s version
    CommandParams k8sVersionCommand = new CommandParams();
    new Command()
        .withParams(k8sVersionCommand
            .saveResults(true)
            .command("kubectl version"))
        .execute();

    String k8sVersion = k8sVersionCommand.stdout();
    boolean k8sV15 = k8sVersion.contains("v1.15");
    String installedK8sVersion = (k8sV15) ? CRD_V15 : CRD_V16;

    // delete existing CRD
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    // install CRD
    String createCrdCommand = "kubectl create -f " + ITTESTS_DIR + "/../kubernetes/crd/" + installedK8sVersion;
    logger.info("Creating CRD with command {0}", createCrdCommand);
    new Command()
        .withParams(new CommandParams().command(createCrdCommand))
        .execute();
  }

  @AfterAll
  public void tearDownAll() {
  }

  /**
   * When installing the Operator via helm install,
   * set the Operator Helm Chart parameter domainNamespaceSelectionStrategy to Dedicated and
   * set domainNamespaces to something that is different from the operator's namespace.
   * Make sure that the domain which is not in the operator's target namespaces do not come up.
   *   Install an Operator with a namespace and set domainNamespaces in a different namespace
   *     from the Operator's namespace, also set domainNamespaceSelectionStrategy to Dedicated
   *     for the Operator Helm Chart.
   *   Verify the Operator is up and running.
   *   Create WebLogic Domain in a namespace that is different from the Operator's namespace.
   *   Verify that the domain does not come up.
   */
  @Test
  @Order(1)
  @DisplayName("Set domainNamespaceSelectionStrategy to Dedicated for the Operator Helm Chart and "
      + "verify that a domain not deployed in operator's namespace doesn't come up")
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
   * When installing the Operator via helm install,
   * set domainNamespaceSelectionStrategy to Dedicated for the Operator Helm Chart.
   * Make sure that the domains in the operator's target namespaces comes up.
   *   Operator is installed in the test case testDedicatedModeDiffNamespace.
   *   Create a WebLogic Domain with the same namespace as Operator's namespace.
   *   Verify that the WebLogic domain whose namespace is same as Operator's namespace comes up.
   */
  @Test
  @Order(2)
  @DisplayName("Set domainNamespaceSelectionStrategy to Dedicated for the Operator Helm Chart and "
      + "verify that the domain deployed in the operator's namespace comes up")
  public void testDedicatedModeSameNamespace() {
    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    createDomain(domain1Namespace);
    verifyDomainRunning(domain1Namespace);
  }

  /**
   * Test that when domainNamespaceSelectionStrategy is set to Dedicated for the Operator Helm Chart,
   * scaling up cluster-1 in domain1Namespace succeeds.
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
   * Test when domainNamespaceSelectionStrategy is set to Dedicated for the Operator Helm Chart and
   * the CRD with a lower than expected version is present, Operator fails with error
   * if it has no permission to overwrite the CRD.
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
   * Test when domainNamespaceSelectionStrategy is set to Dedicated for the Operator Helm Chart and
   * the CRD is not present or is deleted, Operator fails with error if it has no permission to create the CRD.
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
    createDomainCrAndVerify(domainNamespace, OCIR_SECRET_NAME, adminSecretName, encryptionSecretName);
  }

  private void createDomainCrAndVerify(String domainNamespace,
                                       String repoSecretName,
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
                .name(repoSecretName))
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
    setPodAntiAffinity(domain);
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private void verifyDomainRunning(String domainNamespace) {
    // check that admin server pod is ready and the service exists in the domain namespace
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that the managed server pod is ready and the service exists in the domain namespace
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
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
