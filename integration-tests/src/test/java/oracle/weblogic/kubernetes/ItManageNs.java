// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
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
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.checkHelmReleaseStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorRestServiceRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createExternalRestIdentitySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



/**
 * Simple JUnit test file used for testing operator namespace management,
 * Dedicated usecase is covered by other test class.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test operator namespace management usability using Helm chart")
@IntegrationTest
class ItManageNs {

  private static String opNamespace = null;
  private static String op2Namespace = null;
  private static String op3Namespace = null;
  private static String op4Namespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domain3Namespace = null;
  private static String domain4Namespace = null;

  // domain constants
  private final String domain1Uid = "managensdomain1";
  private final String domain2Uid = "managensdomain2";
  private final String domain3Uid = "managensdomain3";
  private final String domain4Uid = "managensdomain4";

  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = "-" + MANAGED_SERVER_NAME_BASE;

  private HelmParams opHelmParams1;
  private HelmParams opHelmParams2;
  private HelmParams opHelmParams4;
  private static String adminSecretName = "weblogic-credentials-itmanagens";
  private static String encryptionSecretName = "encryptionsecret-itmanagens";
  private static Map<String, String> labels1;
  private static Map<String, String> labels2;

  private static LoggingFacade logger = null;
  private static String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
  private static org.awaitility.core.ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(5, SECONDS)
          .atMost(2, MINUTES).await();

  /**
   * Get namespaces for operator, domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(8) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain1 namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    // get a unique domain2 namespace
    logger.info("Getting a unique namespace for WebLogic domain 2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    // get a unique domain3 namespace
    logger.info("Getting a unique namespace for WebLogic domain 3");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    domain3Namespace = namespaces.get(3);

    // get a unique domain4 namespace
    logger.info("Getting a unique namespace for WebLogic domain 4");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    domain4Namespace = namespaces.get(4);

    // get a unique operator 2 namespace
    logger.info("Getting a unique namespace for operator 2");
    assertNotNull(namespaces.get(5), "Namespace list is null");
    op2Namespace = namespaces.get(5);

    // get a unique operator 3 namespace
    logger.info("Getting a unique namespace for operator 3");
    assertNotNull(namespaces.get(6), "Namespace list is null");
    op3Namespace = namespaces.get(6);

    // get a unique operator 4 namespace
    logger.info("Getting a unique namespace for operator 4");
    assertNotNull(namespaces.get(7), "Namespace list is null");
    op4Namespace = namespaces.get(7);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);
    createSecrets(domain3Namespace);
    createSecrets("default");
    labels1 = new java.util.HashMap<>();
    labels1.put(OPERATOR_RELEASE_NAME, OPERATOR_RELEASE_NAME);
    labels2 = new java.util.HashMap<>();
    labels2.put(OPERATOR_RELEASE_NAME + "2", OPERATOR_RELEASE_NAME);
    setLabelToNamespace(domain1Namespace, labels1);
    setLabelToNamespace(domain2Namespace, labels2);
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

    logger.info("Delete domain3 custom resource in namespace {0}", domain3Namespace);
    deleteDomainCustomResource(domain3Uid, domain3Namespace);
    logger.info("Deleted Domain Custom Resource " + domain3Uid);

    logger.info("Delete domain4 custom resource in namespace {0}", domain4Namespace);
    deleteDomainCustomResource(domain4Uid, domain4Namespace);
    logger.info("Deleted Domain Custom Resource " + domain4Uid);

    logger.info("Delete domain1test custom resource in namespace {0}", "test-" + domain1Namespace);
    deleteDomainCustomResource(domain1Uid + "test", "test-" + domain1Namespace);
    logger.info("Deleted Domain Custom Resource " + domain1Uid + "test from test-" + domain1Namespace);

    logger.info("Delete domain2test custom resource in namespace {0}", "test-" + domain2Namespace);
    deleteDomainCustomResource(domain2Uid + "test", "test-" + domain2Namespace);
    logger.info("Deleted Domain Custom Resource " + domain2Uid + "test from test-" + domain2Namespace);

    logger.info("Delete weblogic custom resource in namespace {0}", "weblogic" + domain2Namespace);
    deleteDomainCustomResource("weblogic", "weblogic" + domain2Namespace);
    logger.info("Deleted Domain Custom Resource weblogic from weblogic" + domain2Namespace);

    deleteSecrets("default");
    deleteSecrets("atest-" +  domain1Namespace);;
    deleteNamespace("atest-" + domain1Namespace);
    //delete operator
    uninstallOperator(opHelmParams1);
    uninstallOperator(opHelmParams2);
    uninstallOperator(opHelmParams4);

  }

  /**
   * Install the Operator successfully and verify it is deployed successfully
   * with domainNamespaceSelectionStrategy=RegExp,
   * and domainNamespaceRegExp=^test and domainNamespace= domain3NS.
   * Deploy two custom domain resources in the two different namespaces with names starting with test
   * and verify all server pods in the domains were created and ready.
   * Verify operator is able to manage these domains by scaling.
   * Try to start another domain with namespace domain3NS. Verify it is not started by operator
   * and value of domainNamespace is ignored for both specific namespace and default.
   * Upgrade helm chart to switch to using LabelSelector, start domain and verify operator can scale it.
   */
  @Test
  @Order(1)
  @DisplayName("install operator helm chart and domain, "
      + " using expression namespace management")
  public void testNsManageByExp() {
    //create domain namespace
    String manageByExp1NS = "test-" +  domain1Namespace;
    String manageByExp2NS = "test-" +  domain2Namespace;
    String manageByExpDomain1Uid = "test-" + domain1Uid;
    String manageByExpDomain2Uid = "test-" + domain2Uid;
    String manageByLabelNS = "weblogic1" + domain1Namespace;
    String manageByLabelDomainUid = "weblogic1" + domain1Uid;
    String domain3NS = "atest-" +  domain1Namespace;
    assertDoesNotThrow(() -> Kubernetes.createNamespace(manageByExp1NS));
    assertDoesNotThrow(() -> Kubernetes.createNamespace(manageByExp2NS));
    assertDoesNotThrow(() -> Kubernetes.createNamespace(domain3NS));
    installAndVerifyOperatorCanManageDomainByNSRegExp(manageByExp1NS, manageByExp2NS,
        manageByExpDomain1Uid, manageByExpDomain2Uid);

    //verify that domainNamespaces field will be ignored and domain will not start for specific NS and default
    checkPodNotCreated(domain3Uid + adminServerPrefix, domain3Uid, domain3Namespace);
    checkDomainNotStartedInDefaultNS("RegExp");
    //verify that operator can't start domain if namespace does not start from test
    createSecrets(domain3NS);
    checkPodNotCreated(domain3Uid + adminServerPrefix, domain3Uid, domain3NS);

    // install  operator sharing same domain
    checkSecondOperatorFailedToShareSameNS(manageByExp1NS);
    switchNSManagementToLabelSelectUsingUpgradeOperator(manageByLabelNS, manageByExp1NS,
        manageByLabelDomainUid, manageByExpDomain1Uid);

  }

  /**
   * Install the Operator successfully and verify it is deployed successfully
   * with domainNamespaceSelectionStrategy=LabelSelector,
   * and domainNamespaceLabelSelector=label1.
   * Deploy a custom domain resource in the namespace with label1
   * and verify all server pods in the domain were created and ready.
   * Verify operator is able to manage this domain by scaling.
   * Verify operator can't start another domain with namespace2 with label2.
   * Modify namespace2 to set label1, verify that operator can manage it.
   * Verify that domainNamespaces field will be ignored and domain will not start for namespaces:
   * (domain3Namespace) and default.
   * Add another operator using domainNamespaces sharing same namespace and verify it fails to install.
   * Upgrade operator to replace namespace management using RegExp namespaces.
   * Verify it can manage added domain and can't manage old domain by scaling .
   * Verify that upgrade helm fail if try to add domain, managed by other operator.
   */
  @Test
  @Order(2)
  @DisplayName("install operator helm chart and domain, "
      + " using label namespace management")
  public void testNsManageByLabel() {
    String manageByLabelDomain1NS = domain1Namespace;
    String manageByLabelDomain2NS = domain2Namespace;
    String manageByExpDomainUid = "weblogic2" + domain2Namespace;
    String manageByExpDomainNS = "weblogic2" + domain2Namespace;
    String manageByLabelDomain1Uid = domain1Uid;
    String manageByLabelDomain2Uid = domain2Uid;
    installAndVerifyOperatorCanManageDomainByLabelSelector(manageByLabelDomain1NS, manageByLabelDomain2NS,
        manageByLabelDomain1Uid, manageByLabelDomain2Uid);
    addExtraDomainByAddingLabelToNS(labels1, manageByLabelDomain2NS, manageByLabelDomain2Uid);
    checkDomainNotStartedInDefaultNS("SelectLabel");
    checkSecondOperatorFailedToShareSameNS(manageByLabelDomain1NS);
    switchNSManagementToRegExpUsingUpgradeOperator(manageByLabelDomain1NS, manageByExpDomainNS,
        manageByLabelDomain1Uid, manageByExpDomainUid);
    checkUpgradeFailedToAddNSManagedByAnotherOperator();
  }

  /**
   * Create namespace ns1 with no label
   * Install the Operator successfully and verify it is deployed successfully
   * with domainNamespaceSelectionStrategy=LabelSelector,
   * and domainNamespaceLabelSelector=label1 and enableRbac=false.
   * Add label1 to ns1 and verify domain can't be started
   * Call upgrade operator with reuse values to enable management for ns1
   * Deploy a custom domain resource in the namespace ns1 with label1
   * and verify all server pods in the domain were created and ready.
   * Verify operator is able to manage this domain by scaling.
   */
  @Test
  @Order(3)
  @DisplayName("install operator helm chart and domain, "
      + " with enableClusterRoleBinding")
  public void testSwitchRbac() {
    String manageByLabelDomainNS = domain1Namespace + "test1";
    String manageByLabelDomainUid = domain1Uid + "test1";
    assertDoesNotThrow(() -> createNamespace(manageByLabelDomainNS));
    opHelmParams4 = installOperatorHelmChart(OPERATOR_RELEASE_NAME,
        op4Namespace, "LabelSelector",
        "mytest", false);
    Map<String, String> labels = new HashMap<>();
    labels.put("mytest", manageByLabelDomainUid);
    assertDoesNotThrow(() -> addLabelsToNamespace(manageByLabelDomainNS, labels));
    //verify domain can't be started because operator does not have permission to manage it
    createSecrets(manageByLabelDomainNS);
    checkPodNotCreated(manageByLabelDomainUid + adminServerPrefix, manageByLabelDomainUid, manageByLabelDomainNS);
    deleteDomainCrd(manageByLabelDomainNS, manageByLabelDomainUid);
    //upgrade operator and start domain
    int externalRestHttpsPort = getServiceNodePort(op4Namespace, "external-weblogic-operator-svc");

    OperatorParams opParams = new OperatorParams()
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .helmParams(opHelmParams4);

    assertTrue(upgradeAndVerifyOperator(op4Namespace, opParams));
    assertTrue(startDomain(manageByLabelDomainNS, manageByLabelDomainUid));
    checkOperatorCanScaleDomain(op4Namespace, manageByLabelDomainUid);
  }

  private void checkUpgradeFailedToAddNSManagedByAnotherOperator() {
    //upgrade operator1 to replace managing domains using RegExp namespaces
    // for ns names starting from weblogic, there one of domains
    //in namespace weblogic* is managed by operator2
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    //set helm params to use domainNamespaceSelectionStrategy=RegExp for namespaces names started with weblogic
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams1)
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp("^" + "weblogic");

    assertFalse(upgradeAndVerifyOperator(opNamespace, opParams), "Upgrade does not fail when adding domain,"
        + " managed by other operator");
  }

  private void switchNSManagementToRegExpUsingUpgradeOperator(String manageByLabelNS,
                                                              String manageByExpNS,
                                                              String manageByLabelDomainUid,
                                                              String manageByExpDomainUid) {
    //upgrade operator1 to replace managing domains using RegExp namespaces
    assertDoesNotThrow(() -> createNamespace(manageByExpNS));
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    //set helm params to use domainNamespaceSelectionStrategy=RegExp for namespaces names started with weblogic
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams1)
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp("^" + "weblogic2");

    assertTrue(upgradeAndVerifyOperator(opNamespace, opParams));

    //verify domain is started in namespace with name starting with weblogic* and operator can scale it.
    createSecrets(manageByExpNS);
    assertTrue(startDomain(manageByExpNS,manageByExpDomainUid));
    checkOperatorCanScaleDomain(opNamespace,manageByExpDomainUid);
    //verify operator can't manage anymore domain running in the namespace with label
    assertTrue(isOperatorFailedToScaleDomain(opNamespace, manageByLabelDomainUid, manageByLabelNS),
        "Operator can still manage domain "
        + manageByLabelDomainUid + " in the namespace " + manageByLabelNS);
  }

  private void addExtraDomainByAddingLabelToNS(Map<String, String> labels, String domainNS, String domainUid) {
    deleteDomainCrd(domainNS, domainUid);

    //switch to the label1, managed by operator and verify domain is started and can be managed by operator.
    setLabelToNamespace(domainNS, labels);
    assertTrue(startDomain(domainNS, domainUid));
    checkOperatorCanScaleDomain(opNamespace, domainUid);
  }

  private void deleteDomainCrd(String domainNS, String domainUid) {
    //clean up domain resources in namespace and set namespace to label , managed by operator
    logger.info("deleting domain custom resource {0}", domainUid);
    assertTrue(deleteDomainCustomResource(domainUid, domainNS));

    // wait until domain was deleted
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNS,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainDoesNotExist(domainUid, DOMAIN_VERSION, domainNS));
  }

  private void installAndVerifyOperatorCanManageDomainByLabelSelector(String manageByLabelDomain1NS,
                                                                      String manageByLabelDomain2NS,
                                                                      String manageByLabelDomain1Uid,
                                                                      String manageByLabelDomain2Uid) {
    // install and verify operator set to manage domains based on LabelSelector strategy,
    // domainNamespaces set to domain4 will be ignored
    opHelmParams1 = installOperatorHelmChart(OPERATOR_RELEASE_NAME,
        opNamespace, "LabelSelector",
        OPERATOR_RELEASE_NAME, true, manageByLabelDomain1NS);

    logger.info("Installing and verifying domain1");
    createSecrets(manageByLabelDomain1NS);
    assertTrue(startDomain(manageByLabelDomain1NS, manageByLabelDomain1Uid),
        "can't start or verify domain in namespace " + manageByLabelDomain1NS);

    checkOperatorCanScaleDomain(opNamespace, manageByLabelDomain1Uid);

    //verify that domainNamespaces field will be ignored and domain4 will not start
    createSecrets(domain4Namespace);
    checkPodNotCreated(domain4Uid + adminServerPrefix, domain4Uid, domain4Namespace);

    //verify that domain2 in namespace with no label2 will not start
    createSecrets(manageByLabelDomain2NS);
    checkPodNotCreated(manageByLabelDomain2Uid + adminServerPrefix, manageByLabelDomain2Uid, manageByLabelDomain2NS);
  }

  private boolean isOperatorFailedToScaleDomain(String opNamespace, String domainUid, String domainNamespace) {
    try {
      //check operator can't manage domainNamespace by trying to scale domain
      int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
      String managedServerPodNamePrefix = domainUid + "-managed-server";
      String opServiceAccount = OPERATOR_RELEASE_NAME + "-sa";
      scaleAndVerifyCluster("cluster-1", domainUid, domainNamespace,
          managedServerPodNamePrefix, 2, 1,
          true, externalRestHttpsPort, opNamespace, opServiceAccount,
          false, "", "scaleDown", 1, "", "", null, null);
      return false;

    } catch (ConditionTimeoutException ex) {
      logger.info("Received expected error " + ex.getMessage());
      return true;
    }
  }

  private static void setLabelToNamespace(String domainNS, Map<String, String> labels) {
    //add label to domain namespace
    V1Namespace namespaceObject1 = assertDoesNotThrow(() -> Kubernetes.getNamespaceAsObject(domainNS));
    assertNotNull(namespaceObject1, "Can't find namespace with name " + domainNS);
    namespaceObject1.getMetadata().setLabels(labels);
    assertDoesNotThrow(() -> Kubernetes.replaceNamespace(namespaceObject1));
  }

  private void checkOperatorCanScaleDomain(String opNamespace, String domainUid) {
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    assertTrue(scaleClusterWithRestApi(domainUid, clusterName, 3,
        externalRestHttpsPort, opNamespace, OPERATOR_RELEASE_NAME + "-sa"),
        "Domain " + domainUid + " scaling operation failed");
  }

  private void checkSecondOperatorFailedToShareSameNS(String domainNamespace) {
    // try to install another operator sharing same domain namespace via different domainNsSelectionStrategy
    try {
      HelmParams opHelmParams3 = installOperatorHelmChart(OPERATOR_RELEASE_NAME,
          op3Namespace, "List",
          null, true, domainNamespace);
      assertNull(opHelmParams3, "Operator helm chart sharing same NS with other operator did not fail");
    } catch (org.opentest4j.AssertionFailedError ex) {
      //expecting to fail
      logger.info("Helm installation failed as expected " + ex.getMessage());
    }
  }

  private void checkDomainNotStartedInDefaultNS(String domainNsSelectionStrategy) {
    //verify operator can't start domain in the default namespace when domainNsSelectionStrategy not List
    // and selector does not match default
    checkPodNotCreated("defaultuid" + adminServerPrefix, "defaultuid", "default");

    logger.info("Delete defaultuid custom resource in namespace {0}", "default");
    deleteDomainCustomResource("defaultuid", "default");
    logger.info("Deleted Domain Custom Resource " + "defaultuid");
  }

  private void switchNSManagementToLabelSelectUsingUpgradeOperator(String manageByLabelNS,
                                                                   String manageByExpNS,
                                                                   String manageByLabelDomainUid,
                                                                   String manageByExpDomainUid) {

    //upgrade operator to manage domains with Labeled namespaces
    int externalRestHttpsPort = getServiceNodePort(op2Namespace, "external-weblogic-operator-svc");
    assertDoesNotThrow(() -> createNamespace(manageByLabelNS));
    Map<String, String> labels = new HashMap<>();
    labels.put("mytest", "weblogic2");
    setLabelToNamespace(manageByLabelNS, labels);
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams2)
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .domainNamespaceLabelSelector("mytest")
        .domainNamespaceSelectionStrategy("LabelSelector");

    assertTrue(upgradeAndVerifyOperator(op2Namespace, opParams));

    //verify domain is started
    createSecrets(manageByLabelNS);
    assertTrue(startDomain(manageByLabelNS,manageByLabelDomainUid));
    checkOperatorCanScaleDomain(op2Namespace,manageByLabelDomainUid);
    //check operator can't manage anymore manageByExpNS
    assertTrue(isOperatorFailedToScaleDomain(op2Namespace, manageByExpDomainUid,
        manageByExpNS), "Operator can still manage domain "
        + manageByExpNS + " in the namespace " + manageByExpNS);
  }

  private void installAndVerifyOperatorCanManageDomainByNSRegExp(String manageByExp1NS,
                                                                 String manageByExp2NS,
                                                                 String manageByExpDomain1Uid,
                                                                 String manageByExpDomain2Uid) {
    // install and verify operator with domainNsSelectStrategy=RegExp to manage domains with namespaces names,
    // starting from test
    opHelmParams2 = installOperatorHelmChart(OPERATOR_RELEASE_NAME,
        op2Namespace, "RegExp", "^test", true, domain3Namespace);

    logger.info("Installing and verifying domain1");
    createSecrets(manageByExp1NS);
    assertTrue(startDomain(manageByExp1NS, manageByExpDomain1Uid),
        "can't start or verify domain in namespace " + manageByExp1NS);
    checkOperatorCanScaleDomain(op2Namespace, manageByExpDomain1Uid);

    logger.info("Installing and verifying domain2");
    createSecrets(manageByExp2NS);
    assertTrue(startDomain(manageByExp2NS, manageByExpDomain2Uid),
        "operator can start or verify domain in namespace " + manageByExp2NS);
    checkOperatorCanScaleDomain(op2Namespace, manageByExpDomain2Uid);
  }


  private boolean startDomain(String domainNamespace, String domainUid) {

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    try {
      Domain domain = createDomainCRD(domainNamespace, domainUid);
      createVerifyDomain(domainNamespace, domainUid, miiImage, domain);
      return true;
    } catch (Exception ex) {
      logger.info("Failed to createVerifyDomain " + ex.getMessage());
      return false;
    }
  }

  /**
   * Create a model in image domain crd.
   */
  private Domain createDomainCRD(String domainNamespace, String domainUid) {

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
    return domain;
  }

  private static void createSecrets(String domainNamespace) {
    // create docker registry secret to pull the image from registry
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    if (!domainNamespace.equals("default")) {
      createDockerRegistrySecret(domainNamespace);
    }

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");
  }

  private static void deleteSecrets(String domainNamespace) {
    logger.info("Deleting docker registry secret in namespace {0}", domainNamespace);
    if (!domainNamespace.equals("default")) {
      deleteSecret(OCR_SECRET_NAME, domainNamespace);
    }

    // delete secret for admin credentials
    logger.info("Deleting secret for admin credentials");
    deleteSecret(adminSecretName, domainNamespace);

    // delete encryption secret
    logger.info("Deleting encryption secret");
    deleteSecret(encryptionSecretName, domainNamespace);
  }

  private void createVerifyDomain(String domainNamespace, String domainUid, String miiImage, Domain domain) {
    // create domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
    String adminServerPodName = domainUid + adminServerPrefix;
    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }
  }

  /**
   * Install WebLogic operator and wait up to two minutes until the operator pod is ready.
   *
   * @param operNamespace the operator namespace in which the operator will be installed
   * @param opReleaseName the operator release name
   * @param enableClusterRoleBinding operator cluster role binding
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  private static HelmParams installOperatorHelmChart(String opReleaseName, String operNamespace,
                                                     String domainNsSelectionStrategy,
                                                     String domainNsSelector,
                                                     boolean enableClusterRoleBinding,
                                                     String... domainNamespace) {
    LoggingFacade logger = getLogger();

    HelmParams opHelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(operNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // Create a service account for the unique operNamespace
    logger.info("Creating service account");
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(new V1ObjectMeta()
            .namespace(operNamespace)
            .name(opReleaseName + "-sa"))));
    logger.info("Created service account: {0}", opReleaseName + "-sa");


    // get operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("operator image name {0}", operatorImage);


    V1SecretList listSecrets = listSecrets(operNamespace);
    if (null != listSecrets) {
      for (V1Secret item : listSecrets.getItems()) {
        if (item.getMetadata().getName().equals(REPO_SECRET_NAME)) {
          break;
        }
      }
      // Create Docker registry secret in the operator namespace to pull the image from repository
      logger.info("Creating Docker registry secret in namespace {0}", operNamespace);
      createDockerRegistrySecret(operNamespace);
    }
    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", REPO_SECRET_NAME);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(java.util.Arrays.asList(domainNamespace))
        .enableClusterRoleBinding(enableClusterRoleBinding)
        .serviceAccount(opReleaseName + "-sa");
    if (domainNsSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNsSelectionStrategy);
      if (domainNsSelectionStrategy.equalsIgnoreCase("LabelSelector")) {
        opParams.domainNamespaceLabelSelector(domainNsSelector);
      } else if (domainNsSelectionStrategy.equalsIgnoreCase("RegExp")) {
        opParams.domainNamespaceRegExp(domainNsSelector);
      }
    }

    // use default image in chart when repoUrl is set, otherwise use latest/current branch operator image
    if (opHelmParams.getRepoUrl() == null) {
      opParams.image(operatorImage);
    }

    // create externalRestIdentitySecret
    assertTrue(createExternalRestIdentitySecret(operNamespace,
        DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME + operNamespace),
        "failed to create external REST identity secret");
    opParams
        .externalRestEnabled(true)
        .externalRestHttpsPort(0)
        .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME + operNamespace);


    // install operator
    logger.info("Installing operator in namespace {0}", operNamespace);

    assertTrue(installOperator(opParams),
        String.format("Failed to install operator in namespace %s ", operNamespace));
    logger.info("Operator installed in namespace {0}", operNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        opReleaseName, operNamespace);

    assertTrue(checkHelmReleaseStatus(opReleaseName, operNamespace, "deployed"),
        String.format("Operator release %s is not in %s status in namespace %s",
            opReleaseName, "deployed", operNamespace));
    logger.info("Operator release {0} status is {1} in namespace {2}",
        opReleaseName, "deployed", operNamespace);

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

    return opHelmParams;
  }

  private void checkPodNotCreated(String podName, String domainUid, String domNamespace) {
    Domain domain = createDomainCRD(domNamespace, domainUid);
    assertNotNull(domain, "Failed to create domain CRD in namespace " + domNamespace);
    createDomainAndVerify(domain, domNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be not created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
            String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));
  }
}
