// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1SecretReference;
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
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
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

  private static String[] opNamespaces = new String[4];

  // domain constants
  private static final String[] domainsUid = {"managensdomain1","managensdomain2","managensdomain3","managensdomain4"};
  private static String[] domainNamespaces = new String[4];
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = "-" + MANAGED_SERVER_NAME_BASE;

  private HelmParams[] opHelmParams = new HelmParams[3];
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
    for (int i = 0; i < 4; i++) {
      // get a unique domain namespace
      assertNotNull(namespaces.get(i), "Namespace list is null");
      logger.info("Getting a unique namespace {0} for WebLogic domain {1}", namespaces.get(i), i);
      domainNamespaces[i] = namespaces.get(i);
    }
    for (int i = 4; i < 8; i++) {
      // get a unique operator namespace
      logger.info("Getting a unique namespace for operator {0}", (i - 4));
      assertNotNull(namespaces.get(i), "Namespace list is null");
      opNamespaces[(i - 4)] = namespaces.get(i);
    }

    createSecrets(domainNamespaces[2]);
    createSecrets("default");
    labels1 = new java.util.HashMap<>();
    labels1.put(OPERATOR_RELEASE_NAME, OPERATOR_RELEASE_NAME);
    labels2 = new java.util.HashMap<>();
    labels2.put(OPERATOR_RELEASE_NAME + "2", OPERATOR_RELEASE_NAME);
    setLabelToNamespace(domainNamespaces[0], labels1);
    setLabelToNamespace(domainNamespaces[1], labels2);
  }

  @AfterAll
  public void tearDownAll() {
    try {
      // Delete domain custom resource
      for (int i = 0; i < 4; i++) {
        logger.info("Delete domain1 custom resource in namespace {0}", domainNamespaces[i]);
        deleteDomainCustomResource(domainsUid[i], domainNamespaces[i]);
        logger.info("Deleted Domain Custom Resource " + domainsUid[i] + " from " + domainNamespaces[i]);
      }
      logger.info("Delete domain1test custom resource in namespace {0}", "test-" + domainNamespaces[0]);
      deleteDomainCustomResource(domainsUid[0] + "test", "test-" + domainNamespaces[0]);
      logger.info("Deleted Domain Custom Resource " + domainsUid[0] + "test from test-" + domainNamespaces[0]);
  
      logger.info("Delete domain2test custom resource in namespace {0}", "test-" + domainNamespaces[1]);
      deleteDomainCustomResource(domainsUid[1] + "test", "test-" + domainNamespaces[1]);
      logger.info("Deleted Domain Custom Resource " + domainsUid[1] + "test from test-" + domainNamespaces[1]);
  
      logger.info("Delete weblogic custom resource in namespace {0}", "weblogic" + domainNamespaces[1]);
      deleteDomainCustomResource("weblogic", "weblogic" + domainNamespaces[1]);
      logger.info("Deleted Domain Custom Resource weblogic from weblogic" + domainNamespaces[1]);
    } finally {
      deleteSecrets("default");
      deleteSecrets("atest-" + domainNamespaces[0]);
     
      deleteNamespace("atest-" + domainNamespaces[0]);
      //delete operator
      for (HelmParams helmParam : opHelmParams) {
        uninstallOperator(helmParam);
      }
    }
  }

  /**
   * Install the Operator successfully and verify it is deployed successfully
   * with domainNamespaceSelectionStrategy=RegExp,
   * and domainNamespaceRegExp=^test and domainNamespace= manageByExp3NS.
   * Deploy two custom domain resources in two different namespaces with names starting with test
   * and verify all server pods in the domains were created and ready.
   * Verify operator is able to manage these domains by scaling.
   * Try to start another domain with namespace manageByExp3NS. Verify it is not started by operator
   * and value of domainNamespace is ignored for both specific namespace and default.
   * Add another operator using domainNamespaces sharing same namespace and verify it fails to install.
   * Upgrade helm chart to switch to using LabelSelector, start domain and verify operator can scale it.
   */
  @Test
  @Order(1)
  @DisplayName("install operator helm chart and domain, "
      + " using expression namespace management")
  public void testNameSpaceManageByRegularExpression() {
    //create domain namespace
    String manageByExp1NS = "test-" +  domainNamespaces[0];
    String manageByExp2NS = "test-" +  domainNamespaces[1];
    String manageByExpDomain1Uid = "test-" + domainsUid[0];
    String manageByExpDomain2Uid = "test-" + domainsUid[1];
    String manageByExp3NS = "atest-" +  domainNamespaces[0];

    Map<String,String> managedByExpDomains = new HashMap<>();
    managedByExpDomains.put(manageByExp1NS,manageByExpDomain1Uid);
    managedByExpDomains.put(manageByExp2NS,manageByExpDomain2Uid);
    Map<String,String> unmanagedByExpDomains = new HashMap<>();
    unmanagedByExpDomains.put(manageByExp3NS,manageByExp3NS);
    String manageByLabelNS = "weblogic1" + domainNamespaces[0];
    String manageByLabelDomainUid = "weblogic1" + domainsUid[0];

    assertDoesNotThrow(() -> Kubernetes.createNamespace(manageByExp1NS));
    assertDoesNotThrow(() -> Kubernetes.createNamespace(manageByExp2NS));
    assertDoesNotThrow(() -> Kubernetes.createNamespace(manageByExp3NS));

    opHelmParams[1] = installAndVerifyOperatorCanManageDomainBySelector(managedByExpDomains,unmanagedByExpDomains,
        "RegExp","^test",
        opNamespaces[1], null);


    //verify that domainNamespaces field will be ignored and domain will not start for default
    checkDomainNotStartedInDefaultNS();

    // install  operator sharing same domain
    checkSecondOperatorFailedToShareSameNS(manageByExp1NS);

    //upgrade operator to manage domains with Labeled namespaces
    int externalRestHttpsPort = getServiceNodePort(opNamespaces[1], "external-weblogic-operator-svc");
    assertDoesNotThrow(() -> createNamespace(manageByLabelNS));
    Map<String, String> labels = new HashMap<>();
    labels.put("mytest", "weblogic2");
    setLabelToNamespace(manageByLabelNS, labels);
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams[1])
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .domainNamespaceLabelSelector("mytest")
        .domainNamespaceSelectionStrategy("LabelSelector");

    assertTrue(upgradeAndVerifyOperator(opNamespaces[1], opParams));

    //verify domain is started
    createSecrets(manageByLabelNS);
    assertTrue(createDomainResourceAndVerifyDomainIsRunning(manageByLabelNS,manageByLabelDomainUid));
    checkOperatorCanScaleDomain(opNamespaces[1],manageByLabelDomainUid);

    //check operator can't manage anymore manageByExp1NS
    assertTrue(isOperatorFailedToScaleDomain(opNamespaces[1], manageByExpDomain1Uid,
        manageByExp1NS), "Operator can still manage domain "
        + manageByExp1NS + " in the namespace " + manageByExp1NS);

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
  public void testNameSpaceManagedByLabelSelector() {
    Map<String, String> managedByLabelDomains = new HashMap<>();
    managedByLabelDomains.put(domainNamespaces[0], domainsUid[0]);
    Map<String, String> unmanagedByLabelDomains = new HashMap<>();
    unmanagedByLabelDomains.put(domainNamespaces[1], domainsUid[1]);
    opHelmParams[0] = installAndVerifyOperatorCanManageDomainBySelector(managedByLabelDomains,unmanagedByLabelDomains,
        "LabelSelector",OPERATOR_RELEASE_NAME,
        opNamespaces[0], domainNamespaces[3]);
    assertNotNull(opHelmParams[0], "Can't install or verify operator with SelectLabel namespace management");

    String manageByExpDomainUid = "weblogic2" + domainNamespaces[1];
    String manageByExpDomainNS = "weblogic2" + domainNamespaces[1];


    //switch namespace domainsNamespaces[1] to the label1,
    // managed by operator and verify domain is started and can be managed by operator.
    setLabelToNamespace(domainNamespaces[1], labels1);
    assertTrue(createDomainResourceAndVerifyDomainIsRunning(domainNamespaces[1], domainsUid[1]),
        "Failed to create domain CRD or "
        + "verify that domain " + domainsUid[1]
        + " is running in namespace " + domainNamespaces[1]);
    checkOperatorCanScaleDomain(opNamespaces[0], domainsUid[1]);

    //check that with specific Selector default namespace is not under operator management
    checkDomainNotStartedInDefaultNS();

    //check that another operator with selector=List matching domain namespace,
    // managed by first operator fails to install
    checkSecondOperatorFailedToShareSameNS(domainNamespaces[0]);

    //upgrade operator1 to replace managing domains using RegExp namespaces
    assertDoesNotThrow(() -> createNamespace(manageByExpDomainNS));
    int externalRestHttpsPort = getServiceNodePort(opNamespaces[0], "external-weblogic-operator-svc");
    //set helm params to use domainNamespaceSelectionStrategy=RegExp for namespaces names started with weblogic
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams[0])
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp("^" + "weblogic2");

    assertTrue(upgradeAndVerifyOperator(opNamespaces[0], opParams));

    //verify domain is started in namespace with name starting with weblogic* and operator can scale it.
    createSecrets(manageByExpDomainNS);
    assertTrue(createDomainResourceAndVerifyDomainIsRunning(manageByExpDomainNS,manageByExpDomainUid));
    checkOperatorCanScaleDomain(opNamespaces[0],manageByExpDomainUid);
    //verify operator can't manage anymore domain running in the namespace with label
    assertTrue(isOperatorFailedToScaleDomain(opNamespaces[0], domainsUid[0], domainNamespaces[0]),
        "Operator can still manage domain "
            + domainsUid[0] + " in the namespace " + domainNamespaces[0]);

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
  public void testNameSpaceWithOperatorRbacFalse() {
    String manageByLabelDomainNS = domainNamespaces[0] + "test4";
    String manageByLabelDomainUid = domainsUid[0] + "test4";
    assertDoesNotThrow(() -> createNamespace(manageByLabelDomainNS));
    opHelmParams[2] = installAndVerifyOperator(OPERATOR_RELEASE_NAME,
        opNamespaces[3], "LabelSelector",
        "mytest4", false);
    Map<String, String> labels = new HashMap<>();
    labels.put("mytest4", manageByLabelDomainUid);
    assertDoesNotThrow(() -> addLabelsToNamespace(manageByLabelDomainNS, labels));
    //verify domain can't be started because operator does not have permission to manage it
    createSecrets(manageByLabelDomainNS);
    checkPodNotCreated(manageByLabelDomainUid + adminServerPrefix, manageByLabelDomainUid, manageByLabelDomainNS);
    deleteDomainResource(manageByLabelDomainNS, manageByLabelDomainUid);
    //upgrade operator and start domain
    int externalRestHttpsPort = getServiceNodePort(opNamespaces[3], "external-weblogic-operator-svc");

    OperatorParams opParams = new OperatorParams()
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .helmParams(opHelmParams[2]);

    assertTrue(upgradeAndVerifyOperator(opNamespaces[3], opParams));
    assertTrue(createDomainResourceAndVerifyDomainIsRunning(manageByLabelDomainNS, manageByLabelDomainUid));
    checkOperatorCanScaleDomain(opNamespaces[3], manageByLabelDomainUid);
  }

  private void checkUpgradeFailedToAddNSManagedByAnotherOperator() {
    //upgrade operator1 to replace managing domains using RegExp namespaces
    // for ns names starting from weblogic, there one of domains
    //in namespace weblogic* is managed by operator2
    int externalRestHttpsPort = getServiceNodePort(opNamespaces[0], "external-weblogic-operator-svc");
    //set helm params to use domainNamespaceSelectionStrategy=RegExp for namespaces names started with weblogic
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams[0])
        .externalRestEnabled(true)
        .externalRestHttpsPort(externalRestHttpsPort)
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp("^" + "weblogic");

    assertFalse(upgradeAndVerifyOperator(opNamespaces[0], opParams), "Upgrade does not fail when adding domain,"
        + " managed by other operator");
  }

  private void deleteDomainResource(String domainNS, String domainUid) {
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

  private HelmParams installAndVerifyOperatorCanManageDomainBySelector(Map<String,String> managedDomains,
                                                                       Map<String,String> unmanagedDomains,
                                                                       String selector, String selectorValue,
                                                                       String opNamespace,
                                                                       String domainNamespacesValue) {
    // install and verify operator set to manage domains based on LabelSelector strategy,
    // domainNamespaces value expected to be ignored
    HelmParams opHelmParam = installAndVerifyOperator(OPERATOR_RELEASE_NAME,
        opNamespace, selector,
        selectorValue, true, domainNamespacesValue);
    managedDomains.forEach((domainNS, domainUid) -> {
          logger.info("Installing and verifying domain {0} in namespace {1}", domainUid, domainNS);
          createSecrets(domainNS);
          assertTrue(createDomainResourceAndVerifyDomainIsRunning(domainNS, domainUid),
              "can't start or verify domain in namespace " + domainNS);

          checkOperatorCanScaleDomain(opNamespace, domainUid);
        }
    );
    if (domainNamespacesValue != null) {
      //verify that domainNamespaces field will be ignored and domain will not start
      createSecrets(domainNamespacesValue);
      checkPodNotCreated(domainNamespacesValue + adminServerPrefix, domainNamespacesValue, domainNamespacesValue);
    }
    //verify that domains in namespaces not matching selector value will not start
    unmanagedDomains.forEach((domainNS, domainUid) -> {
      createSecrets(domainNS);
      checkPodNotCreated(domainUid + adminServerPrefix, domainUid, domainNS);
      deleteDomainResource(domainNS, domainUid);
    }
    );
    return opHelmParam;
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
    HelmParams opHelmParams = new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespaces[2])
        .chartDir(OPERATOR_CHART_DIR);
    try {
      HelmParams opHelmParams2 = installAndVerifyOperator(OPERATOR_RELEASE_NAME,
          opNamespaces[2], "List",
          null, true, domainNamespace);
      assertNull(opHelmParams2, "Operator helm chart sharing same NS with other operator did not fail");
    } catch (org.opentest4j.AssertionFailedError ex) {
      //expecting to fail
      logger.info("Helm installation failed as expected " + ex.getMessage());
      uninstallOperator(opHelmParams);
    }
  }

  private void checkDomainNotStartedInDefaultNS() {
    //verify operator can't start domain in the default namespace when domainNsSelectionStrategy not List
    // and selector does not match default
    checkPodNotCreated("defaultuid" + adminServerPrefix, "defaultuid", "default");

    logger.info("Delete defaultuid custom resource in namespace {0}", "default");
    deleteDomainCustomResource("defaultuid", "default");
    logger.info("Deleted Domain Custom Resource " + "defaultuid");
  }

  private boolean createDomainResourceAndVerifyDomainIsRunning(String domainNamespace, String domainUid) {

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    Domain domain = createDomainResource(domainNamespace, domainUid);
    assertDoesNotThrow(() -> createVerifyDomain(domainNamespace, domainUid, miiImage, domain));
    return true;
  }

  /**
   * Create a model in image domain resource.
   */
  private Domain createDomainResource(String domainNamespace, String domainUid) {

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
    return domain;
  }

  private static void createSecrets(String domainNamespace) {
    // create docker registry secret to pull the image from registry
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    if (!domainNamespace.equals("default")) {
      createOcirRepoSecret(domainNamespace);
    }

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

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

  private void checkPodNotCreated(String podName, String domainUid, String domNamespace) {
    Domain domain = createDomainResource(domNamespace, domainUid);
    assertNotNull(domain, "Failed to create domain CRD in namespace " + domNamespace);
    createDomainAndVerify(domain, domNamespace);
    checkPodDoesNotExist(podName,domainUid, domNamespace);
  }
}
