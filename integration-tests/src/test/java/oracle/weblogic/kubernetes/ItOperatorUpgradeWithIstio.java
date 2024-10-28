// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OLD_DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CleanupUtil.cleanup;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.checkIstioService;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createIstioService;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.checkDomainStatus;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.cleanUpCRD;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.installOldOperator;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.upgradeOperatorToCurrent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Install a released version of Operator from GitHub chart repository.
 * Create a domain using Model-In-Image model with a dynamic cluster.
 * Configure Itsio on the domain resource with v9 schema
 * Make sure the console is accessible thru istio ingress port
 * Upgrade operator with current Operator image build from current branch.
 * Make sure the console is accessible thru istio ingress port
 */
@DisplayName("Operator upgrade tests with Istio")
@IntegrationTest
@Tag("kind-upgrade")
class ItOperatorUpgradeWithIstio {

  private final String clusterName = "cluster-1"; // do not modify
  private static LoggingFacade logger = null;
  private String domainUid = "istio-upg-domain";
  private String adminServerPodName = domainUid + "-admin-server";
  private String managedServerPodNamePrefix = domainUid + "-managed-server";
  private int replicaCount = 2;
  private List<String> namespaces;
  private String latestOperatorImageName;
  private String adminSecretName = "weblogic-credentials";
  private String opNamespace;
  private String domainNamespace;
  private int istioIngressPort;

  /**
   * For each test:
   * Assigns unique namespaces for operator and domain.
   * @param namespaces injected by JUnit
   */
  @BeforeEach
  public void beforeEach(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    this.namespaces = namespaces;
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));
  }

  /**
   * Upgrade from Operator 4.0.9 to current with Istio enabled domain.
   */
  @Test
  @DisplayName("Upgrade 4.0.9 Istio Domain with Istio to current")
  void testOperatorWlsIstioDomainUpgradeFrom409ToCurrent() {
    logger.info("Starting testOperatorWlsIstioDomainUpgradeFrom409ToCurrent" 
         + " to upgrade Istio Image Domain with Istio with v9 schema to current");
    upgradeWlsIstioDomain("4.0.9");
  }

  /**
   * Upgrade from Operator 4.1.7 to current with Istio enabled domain.
   */
  @Test
  @DisplayName("Upgrade 4.1.7 Istio Domain(v9) with Istio to current")
  void testOperatorWlsIstioDomainUpgradeFrom417ToCurrent() {
    logger.info("Starting testOperatorWlsIstioDomainUpgradeFrom417ToCurrent"
         + " to upgrade Istio Image Domain with Istio with v9 schema to current");
    upgradeWlsIstioDomain("4.1.7");
  }

  /**
   * Upgrade from Operator v4.2.6 to current with Istio enabled domain.
   */
  @Test
  @DisplayName("Upgrade 4.2.6 Istio Domain with Istio to current")
  void testOperatorWlsIstioDomainUpgradeFrom426ToCurrent() {
    logger.info("Starting test to upgrade Istio Image Domain with Istio with v9 schema to current");
    upgradeWlsIstioDomain("4.2.6");
  }

  /**
   * Cleanup Kubernetes artifacts in the namespaces used by the test and
   * delete CRD.
   */
  @AfterEach
  public void tearDown() {
    if (!SKIP_CLEANUP) {
      cleanup(namespaces);
      cleanUpCRD();
    }
  }

  void upgradeWlsIstioDomain(String oldVersion) {
    logger.info("Upgrade version/{0} Istio Domain to current", oldVersion);
    installOldOperator(oldVersion, opNamespace, domainNamespace);
    createSecrets();

    // Create the repo secret to pull base WebLogic image
    createBaseRepoSecret(domainNamespace);
    createConfigMapAndVerify("istio-upgrade-configmap", 
          domainUid, domainNamespace, Collections.emptyList());

    // Creating an MII domain with v9 version
    // Generate a v9 version of domain.yaml file from a template file
    // by replacing domain namespace, domain uid, image 
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("MII_IMAGE", 
         MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    templateMap.put("API_VERSION", "v9");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "istio.config.template.yaml");
    Path targetDomainFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDomainFile.toString(),
        "domain.yaml", templateMap));
    logger.info("Generated Domain Resource file {0}", targetDomainFile);

    // run KUBERNETES_CLI to create the domain
    logger.info("Run " + KUBERNETES_CLI + " to create the domain");
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " apply -f "
            + Paths.get(WORK_DIR + "/domain.yaml").toString());
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, "v9", domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);
    checkDomainStarted(domainUid, domainNamespace);
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before upgrading the operator
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i, getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }
    // verify there is no status condition type Completed
    // before upgrading to Latest
    verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, OLD_DOMAIN_VERSION);
    istioIngressPort
        = createIstioService(domainUid, clusterName, adminServerPodName, domainNamespace);
    String istioHost = null;
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      try {
        istioHost = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
      } catch (UnknownHostException ex) {
        logger.severe(ex.getLocalizedMessage());
      }
      istioIngressPort = ISTIO_HTTP_HOSTPORT;
    } else {
      istioHost = K8S_NODEPORT_HOST;
    }
    checkIstioService(istioHost, istioIngressPort, domainNamespace);
    upgradeOperatorToCurrent(opNamespace);
    checkDomainStatus(domainNamespace, domainUid);
    verifyPodsNotRolled(domainNamespace, pods);
    // Re check the istio Service After Upgrade
    checkIstioService(istioHost, istioIngressPort, domainNamespace);
  }

  private void createSecrets() {
    // Create the repo secret to pull the domain image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
         ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        ENCRYPION_USERNAME_DEFAULT, ENCRYPION_PASSWORD_DEFAULT);
  }

  private void checkDomainStarted(String domainUid, String domainNamespace) {
    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }
  }

  private void checkDomainStopped(String domainUid, String domainNamespace) {
    // verify admin server pod is deleted
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    // verify managed server pods are deleted
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed pod {0} to be deleted in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodDeleted(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }

}
