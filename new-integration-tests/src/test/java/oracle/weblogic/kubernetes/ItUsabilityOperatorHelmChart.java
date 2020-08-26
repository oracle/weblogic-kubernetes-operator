// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.JAVA_LOGGING_LEVEL_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_SERVICE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.helmValuesToString;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.checkHelmReleaseStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorRestServiceRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createExternalRestIdentitySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



/**
 * Simple JUnit test file used for testing operator usability.
 * Use Helm chart to install operator(s)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test operator usability using Helm chart installation")
@IntegrationTest
class ItUsabilityOperatorHelmChart {

  private static String opNamespace = null;
  private static String op2Namespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;

  // domain constants
  private final String domain1Uid = "usabdomain1";
  private final String domain2Uid = "usabdomain2";
  private final String clusterName = "cluster-1";
  private final int managedServerPort = 8001;
  private final int replicaCount = 2;
  private final String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = "-" + MANAGED_SERVER_NAME_BASE;
  private boolean isDomain1Running = false;
  private boolean isDomain2Running = false;

  // ingress host list
  private List<String> ingressHostList;
  private static LoggingFacade logger = null;
  private static org.awaitility.core.ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Get namespaces for operator, domain1 and NGINX.
   * Install and verify NGINX.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(5) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain 2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    // get a unique NGINX namespace
    logger.info("Getting a unique namespace for NGINX");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    String nginxNamespace = namespaces.get(3);

    // get a unique operator 2 namespace
    logger.info("Getting a unique namespace for operator 2");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    op2Namespace = namespaces.get(4);

    // install and verify NGINX
    logger.info("Installing and verifying NGINX");
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);

    String nginxServiceName = nginxHelmParams.getReleaseName() + "-nginx-ingress-controller";
    logger.info("NGINX service name: {0}", nginxServiceName);
    nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
    logger.info("NGINX http node port: {0}", nodeportshttp);
  }

  @AfterAll
  public void tearDownAll() {
    // uninstall NGINX release
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

  /**
   * Create operator and verify it is deployed successfully.
   * Create a custom domain resource and verify all server pods in the domain were created and ready.
   * Verify NGINX can access the sample app from all managed servers in the domain.
   * Delete operator.
   * Verify the states of all server pods in the domain were not changed.
   * Verify NGINX can access the sample app from all managed servers in the domain after the operator was deleted.
   * Test fails if the state of any pod in the domain was changed or NGINX can not access the sample app from
   * all managed servers in the absence of operator.
   */
  @Test
  @DisplayName("Create operator and domain, then delete operator and verify the domain is still running")
  @Slow
  @MustNotRunInParallel
  public void testDeleteOperatorButNotDomain() {
    // install and verify operator
    logger.info("Installing and verifying operator");
    HelmParams opHelmParams = installAndVerifyOperator(opNamespace, domain1Namespace);
    if (!isDomain1Running) {
      logger.info("Installing and verifying domain");
      assertTrue(createVerifyDomain(domain1Namespace, domain1Uid),
          "can't start or verify domain in namespace " + domain1Namespace);
      isDomain1Running = true;
    }
    // get the admin server pod original creation timestamp
    logger.info("Getting admin server pod original creation timestamp");
    String adminServerPodName = domain1Uid + adminServerPrefix;
    DateTime adminPodOriginalTimestamp =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domain1Namespace, "", adminServerPodName),
            String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                adminServerPodName, domain1Namespace));

    // get the managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps");
    List<DateTime> managedServerPodOriginalTimestampList = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      final String managedServerPodName = domain1Uid + managedServerPrefix + i;
      managedServerPodOriginalTimestampList.add(
          assertDoesNotThrow(() -> getPodCreationTimestamp(domain1Namespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domain1Namespace)));
    }
    // delete operator
    logger.info("Uninstalling operator");
    cleanUpSA(opNamespace);
    deleteSecret("ocir-secret",opNamespace);
    uninstallOperator(opHelmParams);

    // verify the operator pod does not exist in the operator namespace
    logger.info("Checking that operator pod does not exist in operator namespace");
    checkPodDoesNotExist("weblogic-operator-", null, opNamespace);

    // verify the operator service does not exist in the operator namespace
    logger.info("Checking that operator service does not exist in operator namespace");
    checkServiceDoesNotExist(OPERATOR_SERVICE_NAME, opNamespace);

    // check that the state of admin server pod in the domain was not changed
    // wait some time here to ensure the pod state is not changed
    try {
      Thread.sleep(15000);
    } catch (InterruptedException e) {
      // ignore
    }

    logger.info("Checking that the admin server pod state was not changed after the operator was deleted");
    assertThat(podStateNotChanged(adminServerPodName, domain1Uid,
        domain1Namespace, adminPodOriginalTimestamp))
        .as("Test state of pod {0} was not changed in namespace {1}", adminServerPodName, domain1Namespace)
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            adminServerPodName, domain1Namespace)
        .isTrue();

    // check that the states of managed server pods in the domain were not changed
    logger.info("Checking that the managed server pod state was not changed after the operator was deleted");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      assertThat(podStateNotChanged(managedServerPodName, domain1Uid, domain1Namespace,
              managedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}",
              managedServerPodName, domain1Namespace)
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, domain1Namespace)
          .isTrue();
    }

    // verify the sample app in the domain is still accessible from all managed servers through NGINX
    logger.info("Checking that the sample app can be accessed from all managed servers through NGINX "
        + "after the operator was deleted");
    verifySampleAppAccessThroughNginx();

  }

  /**
   * Create operator and verify it is deployed successfully.
   * Create a custom domain resource and verify all server pods in the domain were created and ready.
   * Verify NGINX can access the sample app from all managed servers in the domain.
   * Delete operator.
   * Verify the states of all server pods in the domain were not changed.
   * Verify NGINX can access the sample app from all managed servers in the domain after the operator was deleted.
   * Test fails if the state of any pod in the domain was changed or NGINX can not access the sample app from
   * all managed servers in the absence of operator.
   */
  @Test
  @DisplayName("Create operator and domain, then delete operator and"
      + " create again operator and verify it can still manage domain")
  @Slow
  @MustNotRunInParallel
  public void testCreateDeleteCreateOperatorButNotDomain() {
    String opReleaseName = OPERATOR_RELEASE_NAME;
    HelmParams op1HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    String opServiceAccount = opNamespace + "-sa";
    try {
      // install operator

      HelmParams opHelmParams = installAndVerifyOperator(opNamespace, opServiceAccount, true,
          0, op1HelmParams, domain1Namespace);
      assertNotNull(opHelmParams, "Can't install operator");

      if (!isDomain1Running) {
        logger.info("Installing and verifying domain");
        assertTrue(createVerifyDomain(domain1Namespace, domain1Uid),
            "can't start or verify domain in namespace " + domain1Namespace);
        isDomain1Running = true;
      }
      // delete operator
      logger.info("Uninstalling operator");
      uninstallOperator(opHelmParams);

      //install second time
      opHelmParams = installOperatorHelmChart(opNamespace, opServiceAccount, false, true, false,
          null,"deployed", 0, opHelmParams, false, domain1Namespace);

      assertNotNull(opHelmParams, "Can't install operator");
      int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
      //check if can still manage domain1
      assertTrue(scaleDomain(domain1Namespace, domain1Uid,2,1,
          opNamespace, opServiceAccount, externalRestHttpsPort), "Domain1 " + domain1Namespace + " scaling failed");
    } finally {
      deleteSecret("ocir-secret",opNamespace);
      cleanUpSA(opNamespace);
      uninstallOperator(op1HelmParams);
    }
  }

  /**
   * Create operator and verify it is deployed successfully
   * Create domain1 and verify the domain is started
   * Upgrade the operator domainNamespaces to include namespace for domain2
   * Verify both domains are managed by the operator by making a REST API call
   * Call helm upgrade to remove the first domain from operator domainNamespaces
   * Verify it can't be managed by operator anymore.
   * Test fails when an operator fails to manage the domains as expected
   */
  @Test
  @DisplayName("Create domain1, managed by operator and domain2, upgrade operator for domain2,"
      + "delete domain1 , verify management for domain2 and no access to domain1")
  @Slow
  @MustNotRunInParallel
  public void testAddRemoveDomainUpdateOperatorHC() throws Exception {

    String opReleaseName = OPERATOR_RELEASE_NAME;
    HelmParams op1HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    try {
      // install operator
      String opServiceAccount = opNamespace + "-sa";
      HelmParams opHelmParams = installAndVerifyOperator(opNamespace, opServiceAccount, true,
          0, op1HelmParams, domain1Namespace);
      assertNotNull(opHelmParams, "Can't install operator");
      int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
      if (!isDomain1Running) {
        logger.info("Installing and verifying domain");
        assertTrue(createVerifyDomain(domain1Namespace, domain1Uid),
            "can't start or verify domain in namespace " + domain1Namespace);
        isDomain1Running = true;
      }

      // operator chart values
      OperatorParams opParams = new OperatorParams()
          .helmParams(opHelmParams)
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .serviceAccount(opServiceAccount)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME)
          .domainNamespaces(java.util.Arrays.asList(domain1Namespace, domain2Namespace));

      // upgrade operator
      assertTrue(upgradeAndVerifyOperator(opNamespace, opParams));

      //assertTrue(upgradeOperator(opParams), "Helm Upgrade failed to add domain to operator");
      if (!isDomain2Running) {
        logger.info("Installing and verifying domain");
        assertTrue(createVerifyDomain(domain2Namespace, domain2Uid),
            "can't start or verify domain in namespace " + domain2Namespace);
        isDomain2Running = true;
      }
      assertTrue(scaleDomain(domain1Namespace, domain1Uid,2,3,
          opNamespace, opServiceAccount, externalRestHttpsPort), "Domain1 " + domain1Namespace + " scaling failed");
      assertTrue(scaleDomain(domain2Namespace,domain2Uid,2,3,
          opNamespace, opServiceAccount, externalRestHttpsPort), "Domain2 " + domain2Namespace + " scaling failed");
      // operator chart values
      opParams = new OperatorParams()
          .helmParams(opHelmParams)
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .serviceAccount(opServiceAccount)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME)
          .domainNamespaces(java.util.Arrays.asList(domain2Namespace));
      assertTrue(upgradeAndVerifyOperator(opNamespace, opParams));

      assertTrue(scaleDomain(domain2Namespace,domain2Uid,3,2,
          opNamespace, opServiceAccount, externalRestHttpsPort),"Domain " + domain2Namespace + " scaling failed");
      //verify operator can't scale domain1 anymore
      assertFalse(scaleDomain(domain1Namespace,domain1Uid,3,2,
          opNamespace, opServiceAccount, externalRestHttpsPort), "operator still can manage domain1");
    } finally {
      cleanUpSA(opNamespace);
      deleteSecret("ocir-secret",opNamespace);
      uninstallOperator(op1HelmParams);
    }
  }


  /**
   * Install operator1 with namespace op2Namespace.
   * Install operator2 with same namesapce op2Namespace.
   * Second operator should fail to install with following exception
   * Error: rendered manifests contain a resource that already exists.
   * Unable to continue with install: existing resource conflict: existing resource conflict: namespace
   *
   */
  @Test
  @DisplayName("Negative test to install two operators sharing the same namespace")
  @Slow
  public void testCreateSecondOperatorUsingSameOperatorNsNegativeInstall() {
    HelmParams opHelmParams = installAndVerifyOperator(opNamespace, domain1Namespace);
    if (!isDomain1Running) {
      logger.info("Installing and verifying domain");
      assertTrue(createVerifyDomain(domain1Namespace, domain1Uid),
          "can't start or verify domain in namespace " + domain1Namespace);
      isDomain1Running = true;
    }
    String opReleaseName = OPERATOR_RELEASE_NAME + "2";
    String opServiceAccount = opNamespace + "-sa2";
    HelmParams op2HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);;

    // install and verify operator
    logger.info("Installing and verifying operator");
    try {
      String expectedError = "Error: rendered manifests contain a resource that already exists."
          + " Unable to continue with install: existing resource conflict: namespace";
      HelmParams opHelmParam2 = installOperatorHelmChart(opNamespace, opServiceAccount, true, false,
          false,expectedError,"failed", 0,
          op2HelmParams, false, domain2Namespace);
      assertNull(opHelmParam2,
          "FAILURE: Helm installs operator in the same namespace as first operator installed ");
    } finally {
      deleteSecret("ocir-secret",opNamespace);
      cleanUpSA(opNamespace);
      uninstallOperator(opHelmParams);
      uninstallOperator(op2HelmParams);
    }
  }

  /**
   * Install operator1 with Domain Namespace [domain2Namespace].
   * Install operator2 with same Domain Namespace [domain2Namespace].
   * Second operator should fail to install with following exception.
   * Error: rendered manifests contain a resource that already exists.
   * Unable to continue with install: existing resource conflict: namespace.
   * Test fails when second operator installation does not fail.
   */
  @Test
  @DisplayName("Negative test to install two operators sharing the same domain namespace")
  @Slow
  public void testSecondOpSharingSameDomainNamespacesNegativeInstall() {
    HelmParams opHelmParams = installAndVerifyOperator(opNamespace, domain2Namespace);
    if (!isDomain2Running) {
      logger.info("Installing and verifying domain");
      assertTrue(createVerifyDomain(domain2Namespace, domain2Uid),
          "can't start or verify domain in namespace " + domain2Namespace);
      isDomain2Running = true;
    }
    String opReleaseName = OPERATOR_RELEASE_NAME + "2";
    HelmParams op2HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(op2Namespace)
        .chartDir(OPERATOR_CHART_DIR);

    // install and verify operator
    logger.info("Installing and verifying operator");
    String opServiceAccount = op2Namespace + "-sa2";
    try {
      String expectedError = "Error: rendered manifests contain a resource that already exists."
          + " Unable to continue with install: existing resource conflict: namespace";
      HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, opServiceAccount, true, false, false,
          expectedError,"failed", 0, op2HelmParams, false, domain2Namespace);
      assertNull(opHelmParam2, "FAILURE: Helm installs operator in the same namespace as first operator installed ");
    } finally {
      deleteSecret("ocir-secret",opNamespace);
      cleanUpSA(opNamespace);
      cleanUpSA(op2Namespace);
      uninstallOperator(opHelmParams);
      uninstallOperator(op2HelmParams);
    }
  }

  /**
   * Intitialize two operators op1 and op2 with same ExternalRestHttpPort.
   * Install operator op1.
   * Install operator op2.
   * Installation of second operator should fail.
   *
   * @throws Exception when second operator installation does not fail
   */
  @Test
  @DisplayName("Negative test to try to create the operator with not preexisted namespace")
  @Slow
  public void testSecondOpSharingSameExternalRestPortNegativeInstall() {
    String opServiceAccount = opNamespace + "-sa";
    String op2ServiceAccount = op2Namespace + "-sa2";
    String opReleaseName = OPERATOR_RELEASE_NAME + "1";
    HelmParams op1HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    HelmParams opHelmParams = installOperatorHelmChart(opNamespace, opServiceAccount, true, true,
        true,null,"deployed", 0, op1HelmParams, false, domain1Namespace);
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    String op2ReleaseName = OPERATOR_RELEASE_NAME + "2";
    HelmParams op2HelmParams = new HelmParams().releaseName(op2ReleaseName)
        .namespace(op2Namespace)
        .chartDir(OPERATOR_CHART_DIR);

    // install and verify operator2
    logger.info("Installing and verifying operator2 fails");
    try {
      String expectedError = "Error: Service \"external-weblogic-operator-svc\" "
          + "is invalid: spec.ports[0].nodePort: Invalid value";
      HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, op2ServiceAccount,
          true, true, true,
          expectedError,"failed",
          externalRestHttpsPort, op2HelmParams, false, domain2Namespace);
      assertNull(opHelmParam2, "FAILURE: Helm installs operator in the same namespace as first operator installed ");
    } finally {
      cleanUpSA(opNamespace);
      cleanUpSA(op2Namespace);
      deleteSecret("ocir-secret",opNamespace);
      deleteSecret("ocir-secret",op2Namespace);
      uninstallOperator(opHelmParams);
      uninstallOperator(op2HelmParams);
    }
  }

  /**
   * Install the operator with non existing operator namespace.
   * The helm install command should fail.
   *
   * @throws Exception when helm install does not fail
   */
  @Test
  @DisplayName("Negative test to try to create the operator with not preexisted namespace")
  @Slow
  public void testNotPreCreatedOpNsCreateOperatorNegativeInstall() {
    // install and verify operator
    logger.info("Installing and verifying operator");
    String opReleaseName = OPERATOR_RELEASE_NAME + "2";
    HelmParams op2HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace("ns-somens")
        .chartDir(OPERATOR_CHART_DIR);;

    // install and verify operator
    logger.info("Installing and verifying operator");
    String opServiceAccount = op2Namespace + "-sa2";
    try {
      String expectedError = "Error: create: failed to create: namespaces \"ns-somens\" not found";
      HelmParams opHelmParam2 = installOperatorHelmChart("ns-somens", opServiceAccount, false, false,
          false, expectedError,"failed", 0, op2HelmParams, false, domain2Namespace);
      assertNull(opHelmParam2, "FAILURE: Helm installs operator in the same namespace as first operator installed ");
    } finally {
      uninstallOperator(op2HelmParams);
    }
  }

  /**
   * Install the operator with empty string as domains namespaces
   * This is equivalent of QuickStart guide does when it installs the operator
   * with ' --set "domainNamespaces={}" '.
   *
   */
  @Test
  @DisplayName("Test to create the operator with empty string for domains namespace")
  @Slow
  @MustNotRunInParallel
  public void testCreateWithEmptyDomainNamespaceInstall() {
    String opReleaseName = OPERATOR_RELEASE_NAME + "2";
    String opServiceAccount = op2Namespace + "-sa2";
    HelmParams op2HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(op2Namespace)
        .chartDir(OPERATOR_CHART_DIR);;

    // install and verify operator
    logger.info("Installing and verifying operator");
    try {

      HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, opServiceAccount, true, false,
          true,null,"deployed", 0, op2HelmParams, false, "");
      assertNotNull(opHelmParam2, "FAILURE: Helm can't installs operator with empty set for target domainnamespaces ");
    } finally {
      deleteSecret("ocir-secret",op2Namespace);
      cleanUpSA(op2Namespace);
      uninstallOperator(op2HelmParams);
    }
  }

  /**
   /**
   * Install the operator with non existing operator service account.
   * Operator installation should fail.
   * Create the service account.
   * Make sure operator pod is in ready state.
   *
   */
  @Test
  @DisplayName("Install operator with non existing operator service account and verify helm chart is failed, "
      + "create service account and verify the operator is running")
  @Slow
  public void testNotPreexistedOpServiceAccountCreateOperatorNegativeInstall() {
    String opServiceAccount = op2Namespace + "-sa";
    String opReleaseName = OPERATOR_RELEASE_NAME + "1";
    // install and verify operator
    logger.info("Installing and verifying operator %s in namespace %s", opReleaseName, op2Namespace);
    String errorMsg = null;
    HelmParams opHelmParams =
        new HelmParams().releaseName(opReleaseName)
            .namespace(op2Namespace)
            .chartDir(OPERATOR_CHART_DIR);
    try {
      try {
        HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, opServiceAccount, false, false,
            true,null,"failed", 0, opHelmParams, false, domain2Namespace);
        assertNull(opHelmParam2, "FAILURE: Helm installs operator with not preexisted service account ");
      } catch (AssertionError ex) {
        logger.info(" Receieved assertion error " + ex.getMessage());
        errorMsg = ex.getMessage() + " when operator service account not created";
      }
      // Create a service account for the unique op2Namespace
      logger.info("Creating service account");
      assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
          .metadata(new V1ObjectMeta()
              .namespace(op2Namespace)
              .name(opServiceAccount))));
      logger.info("Created service account: {0}", opServiceAccount);
      // list Helm releases matching operator release name in operator namespace
      logger.info("Checking operator release {0} status in namespace {1}",
          opReleaseName, op2Namespace);
      assertTrue(isHelmReleaseDeployed(opReleaseName, op2Namespace),
          String.format("Operator release %s is not in deployed status in namespace %s",
              opReleaseName, op2Namespace));
      logger.info("Operator release {0} status is deployed in namespace {1}",
          opReleaseName, op2Namespace);

      // wait for the operator to be ready
      logger.info("Wait for the operator pod is ready in namespace {0}", op2Namespace);
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for operator to be running in namespace {0} "
                      + "(elapsed time {1}ms, remaining time {2}ms)",
                  op2Namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> operatorIsReady(op2Namespace),
              "operatorIsReady failed with ApiException"));
      //comment it out due OWLS-84294, helm does not report failed status
      //assertNull(errorMsg, errorMsg);
    } finally {
      //uninstall operator
      deleteSecret("ocir-secret",op2Namespace);
      cleanUpSA(op2Namespace);
      uninstallOperator(opHelmParams);
    }
  }

  private boolean createVerifyDomain(String domainNamespace, String domainUid) {

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");

    createAndVerifyMiiDomain(domainNamespace, domainUid);

    // create ingress for the domain
    logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    ingressHostList =
        createIngressForDomainAndVerify(domainUid, domainNamespace, clusterNameMsPortMap);

    // verify the sample apps for the domain
    logger.info("Checking that the sample app can be accessed from all managed servers through NGINX");
    verifySampleAppAccessThroughNginx();
    return true;
  }

  /**
   * Create a model in image domain and verify the domain pods are ready.
   */
  private void createAndVerifyMiiDomain(String domainNamespace, String domainUid) {

    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

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
    String adminServerPodName = domainUid + adminServerPrefix;
    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);
    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodExists(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);
    }
  }

  /**
   * Verify the sample app can be accessed from all managed servers in the domain through NGINX.
   */
  private void verifySampleAppAccessThroughNginx() {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
            ingressHostList.get(0), K8S_NODEPORT_HOST, nodeportshttp);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50))
        .as("Verify NGINX can access the sample app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
        .isTrue();
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   * Method is to test positive and negative testcases for operator helm install
   *
   * @param operNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param createOpSA option to create the service account for operator
   * @param withRestAPI whether to use REST API
   * @param createSecret option to create secret
   * @param errMsg   expected helm chart error message for negative scenario, null for positive testcases
   * @param helmStatus expected helm status
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  private static HelmParams installOperatorHelmChart(String operNamespace,
                                                    String opServiceAccount,
                                                    boolean createOpSA,
                                                    boolean withRestAPI,
                                                    boolean createSecret,
                                                    String errMsg,
                                                    String helmStatus,
                                                    int externalRestHttpsPort,
                                                    HelmParams opHelmParams,
                                                    boolean elkIntegrationEnabled,
                                                    String... domainNamespace) {
    LoggingFacade logger = getLogger();
    String opReleaseName = opHelmParams.getReleaseName();

    if (createOpSA) {
      // Create a service account for the unique operNamespace
      logger.info("Creating service account");
      assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
          .metadata(new V1ObjectMeta()
              .namespace(operNamespace)
              .name(opServiceAccount))));
      logger.info("Created service account: {0}", opServiceAccount);
    }

    // get operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("operator image name {0}", operatorImage);
    if (createSecret) {
      boolean secretExists = false;
      V1SecretList listSecrets = listSecrets(operNamespace);
      if (null != listSecrets) {
        for (V1Secret item : listSecrets.getItems()) {
          if (item.getMetadata().getName().equals(REPO_SECRET_NAME)) {
            secretExists = true;
            break;
          }
        }
      }
      if (!secretExists) {

        // Create Docker registry secret in the operator namespace to pull the image from repository
        logger.info("Creating Docker registry secret in namespace {0}", operNamespace);
        createDockerRegistrySecret(operNamespace);
      }
    }
    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", REPO_SECRET_NAME);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(java.util.Arrays.asList(domainNamespace))
        .serviceAccount(opServiceAccount);

    // use default image in chart when repoUrl is set, otherwise use latest/current branch operator image
    if (opHelmParams.getRepoUrl() == null) {
      opParams.image(operatorImage);
    }

    // enable ELK Stack
    if (elkIntegrationEnabled) {
      opParams
          .elkIntegrationEnabled(elkIntegrationEnabled);
      opParams
          .elasticSearchHost(ELASTICSEARCH_HOST);
      opParams
          .elasticSearchPort(ELASTICSEARCH_HTTP_PORT);
      opParams
          .javaLoggingLevel(JAVA_LOGGING_LEVEL_VALUE);
      opParams
          .logStashImage(LOGSTASH_IMAGE);
    }

    if (withRestAPI) {
      // create externalRestIdentitySecret
      assertTrue(createExternalRestIdentitySecret(operNamespace,
          DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME + operNamespace),
          "failed to create external REST identity secret");
      opParams
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME + operNamespace);
    }

    // install operator
    logger.info("Installing operator in namespace {0}", operNamespace);
    if (errMsg != null) {
      String helmErrorMsg = installNegative(opHelmParams, opParams.getValues());
      assertNotNull(helmErrorMsg, "helm chart install successful, but expected to fail");
      assertTrue(helmErrorMsg.contains(errMsg),
          String.format("Operator install failed with unexpected error  :%s", helmErrorMsg));
      return null;
    } else {
      assertTrue(installOperator(opParams),
          String.format("Failed to install operator in namespace %s ", operNamespace));
      logger.info("Operator installed in namespace {0}", operNamespace);
    }
    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        opReleaseName, operNamespace);
    assertTrue(checkHelmReleaseStatus(opReleaseName, operNamespace, helmStatus),
        String.format("Operator release %s is not in %s status in namespace %s",
            opReleaseName, helmStatus, operNamespace));
    logger.info("Operator release {0} status is {1} in namespace {2}",
        opReleaseName, helmStatus, operNamespace);
    if (helmStatus.equalsIgnoreCase("deployed")) {
      // wait for the operator to be ready
      logger.info("Wait for the operator pod is ready in namespace {0}", operNamespace);
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for operator to be running in namespace {0} "
                      + "(elapsed time {1}ms, remaining time {2}ms)",
                  operNamespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> operatorIsReady(operNamespace),
              "operatorIsReady failed with ApiException"));

      if (withRestAPI) {
        logger.info("Wait for the operator external service in namespace {0}", operNamespace);
        withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for operator external service in namespace {0} "
                        + "(elapsed time {1}ms, remaining time {2}ms)",
                    operNamespace,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(assertDoesNotThrow(() -> operatorRestServiceRunning(operNamespace),
                "operator external service is not running"));
      }
      return opHelmParams;
    }
    return null;
  }

  /**
   * Installs a Helm chart and expected to fail.
   * @param helmParams the parameters to Helm install command like namespace, release name,
   *                   repo url or chart dir, chart name
   * @param chartValues the values to override in a chart
   * @return error Message on success, null otherwise
   */
  public static String installNegative(HelmParams helmParams, Map<String, Object> chartValues) {
    String namespace = helmParams.getNamespace();

    //chart reference to be used in Helm install
    String chartRef = helmParams.getChartDir();

    getLogger().fine("Installing a chart in namespace {0} using chart reference {1}", namespace, chartRef);

    // build Helm install command
    String installCmd = String.format("helm install %1s %2s --namespace %3s ",
        helmParams.getReleaseName(), chartRef, helmParams.getNamespace());

    // if we have chart values file
    String chartValuesFile = helmParams.getChartValuesFile();
    if (chartValuesFile != null) {
      installCmd = installCmd + " --values " + chartValuesFile;
    }

    // if we have chart version
    String chartVersion = helmParams.getChartVersion();
    if (chartVersion != null) {
      installCmd = installCmd + " --version " + chartVersion;
    }

    // add override chart values
    installCmd = installCmd + helmValuesToString(chartValues);

    if (helmParams.getChartVersion() != null) {
      installCmd = installCmd + " --version " + helmParams.getChartVersion();
    }

    // run the command
    return getExecError(installCmd);

  }

  /**
   * Executes the given command and return error message if command failed.
   * @param command the command to execute
   * @return Error message string for failed command or null if no failure
   */
  private static String getExecError(String command) {
    getLogger().info("Running command - \n" + command);
    ExecResult result = null;
    try {
      result = ExecCommand.exec(command, true);
      getLogger().info("The command returned exit value: "
          + result.exitValue() + " command output: "
          + result.stderr() + "\n" + result.stdout());
      if (result.exitValue() != 0) {
        getLogger().info("Command failed with errors " + result.stderr() + "\n" + result.stdout());
        return result.stderr();
      }
    } catch (Exception e) {
      getLogger().info("Got exception, command failed with errors " + e.getMessage());
      return result.stderr();
    }
    return null;
  }

  /**
   * Scale domain .
   */
  private boolean scaleDomain(String domainNS, String domainUid,
                              int replicaCount,
                              int replicasAfterScale,
                              String opNamespace,
                              String opServiceAccount,
                              int externalRestHttpsPort) {

    // scale domain
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domainUid, domainNS, replicasAfterScale);
    assertTrue(externalRestHttpsPort != -1,
        "Could not get the Operator external service node port");
    logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
    String managedServerPodNamePrefix = domainUid + "-managed-server";
    try {
      // check domain can be managed from the operator by scaling the cluster
      scaleAndVerifyCluster("cluster-1", domainUid, domainNS,
          managedServerPodNamePrefix, replicaCount, replicasAfterScale,
          true, externalRestHttpsPort, opNamespace, opServiceAccount,
          false, "", "", 0, "", "", null, null);
    } catch (Exception ex) {
      logger.info("Getting error during scale " + ex.getMessage());
      return false;
    }
    return true;
  }

  private void cleanUpSA(String namespace) {
    V1ServiceAccountList sas = Kubernetes.listServiceAccounts(namespace);
    if (sas != null) {
      for (V1ServiceAccount sa : sas.getItems()) {
        String saName = sa.getMetadata().getName();
        deleteServiceAccount(saName, namespace);
        checkServiceDoesNotExist(saName, namespace);
      }
    }
  }
}
