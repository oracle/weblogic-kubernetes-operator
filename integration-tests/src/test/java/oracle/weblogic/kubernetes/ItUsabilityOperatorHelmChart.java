// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
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
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
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
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_SERVICE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.helmValuesToString;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
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
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createExternalRestIdentitySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
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
  private static String domain3Namespace = null;

  // domain constants
  private final String domain1Uid = "usabdomain1";
  private final String domain2Uid = "usabdomain2";
  private final String domain3Uid = "usabdomain3";
  private final String clusterName = "cluster-1";
  private final int managedServerPort = 8001;
  private final int replicaCount = 2;
  private final String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = "-" + MANAGED_SERVER_NAME_BASE;
  private boolean isDomain1Running = false;
  private boolean isDomain2Running = false;
  private int replicaCountDomain1 = 2;
  private int replicaCountDomain2 = 2;

  // ingress host list
  private List<String> ingressHostList;
  private static LoggingFacade logger = null;
  private static org.awaitility.core.ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Get namespaces for operator, domain.
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

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain 3");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    domain3Namespace = namespaces.get(3);

    // get a unique operator 2 namespace
    logger.info("Getting a unique namespace for operator 2");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    op2Namespace = namespaces.get(4);
  }

  @AfterAll
  public void tearDownAll() {

    // Delete domain custom resource
    logger.info("Delete domain1 custom resource in namespace {0}", domain1Namespace);
    deleteDomainCustomResource(domain1Uid, domain1Namespace);
    logger.info("Deleted Domain Custom Resource " + domain1Uid + " from " + domain1Namespace);

    logger.info("Delete domain2 custom resource in namespace {0}", domain2Namespace);
    deleteDomainCustomResource(domain2Uid, domain2Namespace);
    logger.info("Deleted Domain Custom Resource " + domain2Uid + " from " + domain2Namespace);

    logger.info("Delete domain3 custom resource in namespace {0}", domain2Namespace);

    deleteDomainCustomResource(domain3Uid, domain3Namespace);
    logger.info("Deleted Domain Custom Resource " + domain3Uid + " from " + domain3Namespace);

  }

  /**
   * Install the Operator successfully and verify it is deployed successfully.
   * Deploy a custom domain resource and verify all server pods in the domain were created and ready.
   * Uninstall operator helm chart.
   * Verify the states of all server pods in the domain were not changed.
   * Verify the access to managed server configuration via rest api in the domain after the operator was deleted.
   */
  @Test
  @DisplayName("install operator helm chart and domain, "
      + " then uninstall operator helm chart and verify the domain is still running")
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
    OffsetDateTime adminPodOriginalTimestamp =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domain1Namespace, "", adminServerPodName),
            String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                adminServerPodName, domain1Namespace));

    // get the managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps");
    List<OffsetDateTime> managedServerPodOriginalTimestampList = new ArrayList<>();
    for (int i = 1; i <= replicaCountDomain1; i++) {
      final String managedServerPodName = domain1Uid + managedServerPrefix + i;
      managedServerPodOriginalTimestampList.add(
          assertDoesNotThrow(() -> getPodCreationTimestamp(domain1Namespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domain1Namespace)));
    }
    // delete operator
    logger.info("Uninstalling operator");
    uninstallOperator(opHelmParams);
    cleanUpSA(opNamespace);
    deleteSecret(OCIR_SECRET_NAME,opNamespace);

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
    for (int i = 1; i <= replicaCountDomain1; i++) {
      String managedServerPodName = managedServerPrefix + i;
      assertThat(podStateNotChanged(managedServerPodName, domain1Uid, domain1Namespace,
              managedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}",
              managedServerPodName, domain1Namespace)
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, domain1Namespace)
          .isTrue();
    }

    // verify the managed server Mbean is still accessible via rest api
    logger.info("Verify the managed server1 MBean configuration through rest API "
        + "after the operator was deleted");
    assertTrue(checkManagedServerConfiguration(domain1Namespace, domain1Uid));

  }

  /**
   * Install the Operator helm chart successfully.
   * Create a custom domain resource and verify all server pods in the domain were created and ready.
   * Uninstall operator helm chart.
   * Verify the states of all server pods in the domain were not changed.
   * Verify rest api access to managed server configuration in the domain after the operator was deleted.
   * Install operator helm chart again reusing same helm values and scale domain to verify it can manage it
   */
  @Test
  @DisplayName("Install operator helm chart and domain, then uninstall operator helm chart, "
      + " install operator helm chart again and verify it can still manage domain")
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
        assertTrue(createVerifyDomain(domain3Namespace, domain3Uid),
            "can't start or verify domain in namespace " + domain3Namespace);
        isDomain1Running = true;
      }
      // delete operator
      logger.info("Uninstalling operator");
      uninstallOperator(opHelmParams);

      //install second time
      opHelmParams = installOperatorHelmChart(opNamespace, opServiceAccount, false, true, false,
          null,"deployed", 0, opHelmParams, domain1Namespace);

      assertNotNull(opHelmParams, "Can't install operator");
      int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
      assertTrue(externalRestHttpsPort != -1,
          "Could not get the Operator external service node port");
      logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
      //check if can still manage domain1
      assertTrue(scaleClusterWithRestApi(domain1Uid, clusterName,replicaCountDomain1 - 1,
          externalRestHttpsPort,opNamespace, opServiceAccount),
          "Domain1 " + domain1Namespace + " scaling failed");
      String managedServerPodName1 = domain1Uid + managedServerPrefix + replicaCountDomain1;
      logger.info("Checking that the managed server pod {0} exists in namespace {1}",
          managedServerPodName1, domain1Namespace);
      assertDoesNotThrow(() -> checkPodDoesNotExist(managedServerPodName1, domain1Uid, domain1Namespace),
          "operator failed to manage domain1, scaling was not succeeded");
      --replicaCountDomain1;
      logger.info("Domain1 scaled to " + replicaCountDomain1 + " servers");

    } finally {
      uninstallOperator(op1HelmParams);
      deleteSecret(OCIR_SECRET_NAME,opNamespace);
      cleanUpSA(opNamespace);
    }
  }

  /**
   * Install the Operator successfully.
   * Create domain1 and verify the domain is started
   * Upgrade the operator helm chart domainNamespaces to include namespace for domain2
   * Verify both domains are managed by the operator by making a REST API call
   * Call helm upgrade to remove the first domain from operator domainNamespaces
   * Verify it can't be managed by operator anymore.
   * Test fails when an operator fails to manage the domains as expected
   */
  @Test
  @DisplayName("Create domain1, managed by operator and domain2, upgrade operator to add domain2,"
      + "delete domain1 , verify operator management for domain2 and no access to domain1")
  public void testAddRemoveDomainNameSpacesOnOperator() {

    String opReleaseName = OPERATOR_RELEASE_NAME;
    HelmParams op1HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(op2Namespace)
        .chartDir(OPERATOR_CHART_DIR);
    try {
      // install operator
      String opServiceAccount = op2Namespace + "-sa";
      HelmParams opHelmParams = installAndVerifyOperator(op2Namespace, opServiceAccount, true,
          0, op1HelmParams, domain2Namespace);
      assertNotNull(opHelmParams, "Can't install operator");
      int externalRestHttpsPort = getServiceNodePort(op2Namespace, "external-weblogic-operator-svc");
      assertTrue(externalRestHttpsPort != -1,
          "Could not get the Operator external service node port");
      logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
      if (!isDomain2Running) {
        logger.info("Installing and verifying domain");
        assertTrue(createVerifyDomain(domain2Namespace, domain2Uid),
            "can't start or verify domain in namespace " + domain2Namespace);
        isDomain2Running = true;
      }

      // operator chart values
      OperatorParams opParams = new OperatorParams()
          .helmParams(opHelmParams)
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .serviceAccount(opServiceAccount)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME)
          .domainNamespaces(java.util.Arrays.asList(domain3Namespace, domain2Namespace));

      // upgrade operator
      assertTrue(upgradeAndVerifyOperator(op2Namespace, opParams));

      logger.info("Installing and verifying domain");
      assertTrue(createVerifyDomain(domain3Namespace, domain3Uid),
          "can't start or verify domain in namespace " + domain3Namespace);

      assertTrue(scaleClusterWithRestApi(domain3Uid, clusterName,3,
          externalRestHttpsPort,op2Namespace, opServiceAccount),
          "Domain3 " + domain3Namespace + " scaling operation failed");
      String managedServerPodName1 = domain3Uid + managedServerPrefix + 3;
      logger.info("Checking that the managed server pod {0} exists in namespace {1}",
          managedServerPodName1, domain3Namespace);
      assertDoesNotThrow(() ->
              checkPodExists(managedServerPodName1, domain3Uid, domain3Namespace),
          "operator failed to manage domain1, scaling was not succeeded");

      logger.info("Domain3 scaled to 3 servers");
      assertTrue(scaleClusterWithRestApi(domain2Uid, clusterName,replicaCountDomain2 + 1,
          externalRestHttpsPort,op2Namespace, opServiceAccount),
          "Domain2 " + domain2Namespace + " scaling operation failed");
      String managedServerPodName2 = domain2Uid + managedServerPrefix + (replicaCountDomain2 + 1);
      logger.info("Checking that the managed server pod {0} exists in namespace {1}",
          managedServerPodName2, domain2Namespace);
      assertDoesNotThrow(() -> checkPodExists(managedServerPodName2, domain2Uid, domain2Namespace),
          "operator failed to manage domain2, scaling was not succeeded");
      ++replicaCountDomain2;
      logger.info("Domain2 scaled to " + replicaCountDomain2 + " servers");
      // operator chart values for upgrade
      opParams = new OperatorParams()
          .helmParams(opHelmParams)
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .serviceAccount(opServiceAccount)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME)
          .domainNamespaces(java.util.Arrays.asList(domain2Namespace));
      assertTrue(upgradeAndVerifyOperator(op2Namespace, opParams));

      assertTrue(scaleClusterWithRestApi(domain2Uid, clusterName,replicaCountDomain2 - 1,
          externalRestHttpsPort,op2Namespace, opServiceAccount),
          "Domain2 " + domain2Namespace + " scaling execution failed");
      // check new managed server pod exists in the namespace
      logger.info("Checking that the managed server pod {0} does not exist in namespace {1}",
          managedServerPodName2, domain2Namespace);
      assertDoesNotThrow(() -> checkPodDoesNotExist(managedServerPodName2, domain2Uid, domain2Namespace),
          "operator failed to managed domain2, scaling was not succeeded, pod "
              + managedServerPodName2 +  " still exists");
      --replicaCountDomain2;
      logger.info("Domain2 scaled to " + replicaCountDomain2 + " servers");

      //verify operator can't scale domain1 anymore
      assertTrue(scaleClusterWithRestApi(domain3Uid, clusterName,2,
          externalRestHttpsPort,op2Namespace, opServiceAccount),
          "Domain1 " + domain3Namespace + " scaling execution failed ");
      // check new managed server pod exists in the namespace
      logger.info("Checking that the managed server pod {0} exists in namespace {1}",
          managedServerPodName1, domain3Namespace);
      assertDoesNotThrow(() -> checkPodExists(managedServerPodName1, domain3Uid, domain3Namespace),
          "operator can still manage domain1, scaling was succeeded for " + managedServerPodName1);

    } finally {
      uninstallOperator(op1HelmParams);
      deleteSecret(OCIR_SECRET_NAME,op2Namespace);
      cleanUpSA(op2Namespace);
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

    // install and verify operator will fail with expected error message
    logger.info("Installing and verifying operator will fail with expected error message");
    try {
      String expectedError = "Error: rendered manifests contain a resource that already exists."
          + " Unable to continue with install";
      HelmParams opHelmParam2 = installOperatorHelmChart(opNamespace, opServiceAccount, true, false,
          false,expectedError,"failed", 0,
          op2HelmParams, domain2Namespace);
      assertNull(opHelmParam2,
          "FAILURE: Helm installs operator in the same namespace as first operator installed ");
    } finally {
      uninstallOperator(opHelmParams);
      uninstallOperator(op2HelmParams);
      deleteSecret(OCIR_SECRET_NAME,opNamespace);
      cleanUpSA(opNamespace);
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
  public void testSecondOpSharingSameDomainNamespacesNegativeInstall() {
    // install and verify operator1
    logger.info("Installing and verifying operator1");
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

    // install and verify operator2 will fail
    logger.info("Installing and verifying operator2 will fail with expected error message");
    String opServiceAccount = op2Namespace + "-sa2";
    try {
      String expectedError = "Error: rendered manifests contain a resource that already exists."
          + " Unable to continue with install";
      HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, opServiceAccount, true, false, false,
          expectedError,"failed", 0, op2HelmParams,  domain2Namespace);
      assertNull(opHelmParam2, "FAILURE: Helm installs operator in the same namespace as first operator installed ");
    } finally {
      uninstallOperator(opHelmParams);
      uninstallOperator(op2HelmParams);
      deleteSecret(OCIR_SECRET_NAME,opNamespace);
      cleanUpSA(opNamespace);
      cleanUpSA(op2Namespace);
    }
  }

  /**
   * Intitialize two operators op1 and op2 with same ExternalRestHttpPort.
   * Install operator op1.
   * Install operator op2.
   * Installation of second operator should fail.
   */
  @Test
  @DisplayName("Negative test to try to create the operator with not preexisted namespace")
  public void testSecondOpSharingSameExternalRestPortNegativeInstall() {
    String opServiceAccount = opNamespace + "-sa";
    String op2ServiceAccount = op2Namespace + "-sa2";
    String opReleaseName = OPERATOR_RELEASE_NAME + "1";
    HelmParams op1HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    HelmParams opHelmParams = installOperatorHelmChart(opNamespace, opServiceAccount, true, true,
        true,null,"deployed", 0, op1HelmParams,  domain1Namespace);
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    assertTrue(externalRestHttpsPort != -1,
        "Could not get the Operator external service node port");
    logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
    String op2ReleaseName = OPERATOR_RELEASE_NAME + "2";
    HelmParams op2HelmParams = new HelmParams().releaseName(op2ReleaseName)
        .namespace(op2Namespace)
        .chartDir(OPERATOR_CHART_DIR);

    // install and verify operator2 will fail
    logger.info("Installing and verifying operator2 fails");
    try {
      String expectedError = "Error: Service \"external-weblogic-operator-svc\" "
          + "is invalid: spec.ports[0].nodePort: Invalid value";
      HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, op2ServiceAccount,
          true, true, true,
          expectedError,"failed",
          externalRestHttpsPort, op2HelmParams,  domain2Namespace);
      assertNull(opHelmParam2, "FAILURE: Helm installs operator in the same namespace as first operator installed ");
      uninstallOperator(op2HelmParams);
    } finally {
      uninstallOperator(opHelmParams);
      deleteSecret(OCIR_SECRET_NAME,opNamespace);
      deleteSecret(OCIR_SECRET_NAME,op2Namespace);
      cleanUpSA(opNamespace);
      cleanUpSA(op2Namespace);
    }
  }

  /**
   * Install the operator with non existing operator namespace.
   * The helm install command should fail.
   * Test reports failure when helm install does not fail
   */
  @Test
  @DisplayName("Negative test to try to create the operator with not preexisted namespace")
  public void testNotPreCreatedOpNsCreateOperatorNegativeInstall() {
    String opReleaseName = OPERATOR_RELEASE_NAME + "2";
    HelmParams op2HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace("ns-somens")
        .chartDir(OPERATOR_CHART_DIR);;

    // install operator
    logger.info("Installing and verifying operator will fail with expected error message");
    String opServiceAccount = op2Namespace + "-sa2";
    try {
      String expectedError = "Error: create: failed to create: namespaces \"ns-somens\" not found";
      HelmParams opHelmParam2 = installOperatorHelmChart("ns-somens", opServiceAccount, false, false,
          false, expectedError,"failed", 0, op2HelmParams,  domain2Namespace);
      assertNull(opHelmParam2, "FAILURE: Helm installs operator in the same namespace as first operator installed ");
    } finally {
      uninstallOperator(op2HelmParams);
    }
  }

  /**
   * Install the operator with empty string as domains namespaces.
   * This is equivalent of QuickStart guide does when it installs the operator
   * with ' --set "domainNamespaces={}" '.
   * Add new domain namespace and make sure that the the WebLogic domain is activated by Operator.
   *
   */
  @Test
  @DisplayName("Test to create the operator with empty string for domains namespace")
  public void testCreateWithEmptyDomainNamespaceInstall() {
    String opReleaseName = OPERATOR_RELEASE_NAME;
    String opServiceAccount = op2Namespace + "-sa";
    HelmParams op2HelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(op2Namespace)
        .chartDir(OPERATOR_CHART_DIR);;

    try {
      // install and verify operator
      logger.info("Installing and verifying operator");
      HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, opServiceAccount, true, true,
          true,null, "deployed", 0, op2HelmParams,  "");
      assertNotNull(opHelmParam2, "FAILURE: Helm can't installs operator with empty set for target domainnamespaces ");

      int externalRestHttpsPort = getServiceNodePort(op2Namespace, "external-weblogic-operator-svc");
      assertTrue(externalRestHttpsPort != -1,
          "Could not get the Operator external service node port");
      logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
      //upgrade operator to add domain
      OperatorParams opParams = new OperatorParams()
          .helmParams(opHelmParam2)
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .serviceAccount(opServiceAccount)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME)
          .domainNamespaces(java.util.Arrays.asList(domain2Namespace));

      assertTrue(upgradeAndVerifyOperator(op2Namespace, opParams));

      if (!isDomain2Running) {
        logger.info("Installing and verifying domain");
        assertTrue(createVerifyDomain(domain2Namespace, domain2Uid),
            "can't start or verify domain in namespace " + domain2Namespace);
        isDomain2Running = true;
      }
      //verify operator can scale domain
      assertTrue(scaleClusterWithRestApi(domain2Uid, clusterName,replicaCountDomain2 - 1,
          externalRestHttpsPort,op2Namespace, opServiceAccount),
          "Domain2 " + domain2Namespace + " scaling operation failed");
      String managedServerPodName2 = domain2Uid + managedServerPrefix + replicaCountDomain2;
      logger.info("Checking that the managed server pod {0} exists in namespace {1}",
          managedServerPodName2, domain2Namespace);
      assertDoesNotThrow(() -> checkPodDoesNotExist(managedServerPodName2, domain2Uid, domain2Namespace),
          "operator failed to manage domain2, scaling was not succeeded for " + managedServerPodName2);
      --replicaCountDomain2;
      logger.info("Domain2 scaled to " + replicaCountDomain2 + " servers");


    } finally {
      uninstallOperator(op2HelmParams);
      deleteSecret(OCIR_SECRET_NAME,op2Namespace);
      cleanUpSA(op2Namespace);
    }
  }

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
  public void testNotPreexistedOpServiceAccountCreateOperatorNegativeInstall() {
    String opServiceAccount = op2Namespace + "-sa";
    String opReleaseName = OPERATOR_RELEASE_NAME + "1";
    String errorMsg = null;
    HelmParams opHelmParams =
        new HelmParams().releaseName(opReleaseName)
            .namespace(op2Namespace)
            .chartDir(OPERATOR_CHART_DIR);
    try {
      try {
        // install and verify operator will not start
        logger.info("Installing  operator %s in namespace %s", opReleaseName, op2Namespace);
        errorMsg = String.format("ServiceAccount %s not found in namespace %s", opServiceAccount, op2Namespace);
        HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, opServiceAccount, false, false,
            true, errorMsg,"failed", 0, opHelmParams,  domain2Namespace);
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

      logger.info("Installing operator %s in namespace %s again", opReleaseName, op2Namespace);
      HelmParams opHelmParam2 = installOperatorHelmChart(op2Namespace, opServiceAccount, false, false,
          false,null,"deployed", 0, opHelmParams,  domain2Namespace);

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

      // Helm reports error message status
      assertNotNull(errorMsg, "Expected error message for missing ServiceAccount not found");
    } finally {
      //uninstall operator helm chart
      uninstallOperator(opHelmParams);
      deleteSecret(OCIR_SECRET_NAME,op2Namespace);
      cleanUpSA(op2Namespace);
    }
  }

  private boolean createVerifyDomain(String domainNamespace, String domainUid) {

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");

    createAndVerifyMiiDomain(domainNamespace, domainUid);
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
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

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
                .name(OCIR_SECRET_NAME))
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
                .serverStartState("RUNNING")
                .adminService(new oracle.weblogic.domain.AdminService()
                    .addChannelsItem(new oracle.weblogic.domain.Channel()
                        .channelName("default")
                        .nodePort(0))))
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
    //check the access to managed server mbean via rest api
    checkManagedServerConfiguration(domainNamespace, domainUid);
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
      // Create Docker registry secret in the operator namespace to pull the image from repository
      // this secret is used only for non-kind cluster
      logger.info("Creating Docker registry secret in namespace {0}", operNamespace);
      createOcirRepoSecret(operNamespace);

    }
    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", OCIR_SECRET_NAME);

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
          String.format("Operator install failed with error  :%s", helmErrorMsg));
      return null;
    } else {
      boolean succeeded = installOperator(opParams);
      checkReleaseStatus(operNamespace, helmStatus, logger, opReleaseName);
      assertTrue(succeeded,
          String.format("Failed to install operator in namespace %s ", operNamespace));
      logger.info("Operator installed in namespace {0}", operNamespace);
    }
    checkReleaseStatus(operNamespace, helmStatus, logger, opReleaseName);
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

  private static void checkReleaseStatus(
      String operNamespace, 
      String helmStatus, 
      LoggingFacade logger, 
      String opReleaseName) {
    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        opReleaseName, operNamespace);
    assertTrue(checkHelmReleaseStatus(opReleaseName, operNamespace, helmStatus),
        String.format("Operator release %s is not in %s status in namespace %s",
            opReleaseName, helmStatus, operNamespace));
    logger.info("Operator release {0} status is {1} in namespace {2}",
        opReleaseName, helmStatus, operNamespace);
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

  /*
   * Verify the server MBEAN configuration through rest API.
   * @param managedServer name of the managed server
   * @returns true if MBEAN is found otherwise false
   **/
  private boolean checkManagedServerConfiguration(String domainNamespace, String domainUid) {
    ExecResult result = null;
    String adminServerPodName = domainUid + adminServerPrefix;
    String managedServer = "managed-server1";
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    StringBuffer checkCluster = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    checkCluster.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append("/management/tenant-monitoring/servers/")
        .append(managedServer)
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkManagedServerConfiguration: curl command {0}", new String(checkCluster));
    try {
      result = exec(new String(checkCluster), true);
    } catch (Exception ex) {
      logger.info("Exception in checkManagedServerConfiguration() {0}", ex);
      return false;
    }
    logger.info("checkManagedServerConfiguration: curl command returned {0}", result.toString());
    if (result.stdout().equals("200")) {
      return true;
    } else {
      return false;
    }
  }
}
