// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.SslUtils.generateJksStores;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies usage of CustomIdentityCustomTrust on PV.
 * Create a MII domain with an attached persistent volume.
 * Configure custom identity and custom trust on server template
 * Enable SSL on server template with port 8002 (default 7002 does not work) 
 * Put the IdentityKeyStore.jks  and TrustKeyStore.jks on /shared directory 
 *  after administration server pod is started so that it can be accessible 
 *  from all managed server pods
 * Once all servers are started get the JNDI initial context using cluster 
 *  service URL with t3s protocol.
 * Repeat the same after scaling the cluster 
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test verifies usage of CustomIdentityCustomTrust on PV")
@IntegrationTest
class ItMiiCustomSslStore {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static int replicaCount = 2;
  private static final String domainUid = "mii-custom-ssl";
  private static String pvName = domainUid + "-pv"; 
  private static String pvcName = domainUid + "-pvc"; 
  private static final String adminServerPodName = domainUid + "-admin-server";
  private static final String managedServerPrefix = domainUid + "-managed-server";
  private static LoggingFacade logger = null;
  private static String cpUrl;

  /**
   * Install Operator.
   * Create domain resource definition.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the 
   *     JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
 
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName, 
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, domainNamespace),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "weblogicenc", domainNamespace),
             String.format("createSecret failed for %s", encryptionSecretName));

    String configMapName = "mii-ssl-configmap";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/mii.ssl.yaml"));

    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create PV, PVC for logs/data
    createPV(pvName, domainUid, ItMiiCustomSslStore.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    // create the domain CR with a pre-defined configmap
    createDomainResourceWithLogHome(domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, OCIR_SECRET_NAME, encryptionSecretName,
        replicaCount, pvName, pvcName, "cluster-1", configMapName,
        null, false, false, false);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // Generate JKS Keystore using openssl before 
    // managed server services and pods are ready
    generateJksStores();
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESULTS_ROOT, "IdentityKeyStore.jks"),
        Paths.get("/shared/IdentityKeyStore.jks")));
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESULTS_ROOT, "TrustKeyStore.jks"),
        Paths.get("/shared/TrustKeyStore.jks")));

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Verify a standalone java client can access JNDI Context inside a pod.
   * The client uses t3s cluster URL with custom SSL TrustStore on commandline  
   */
  @Test
  @Order(1)
  @DisplayName("Verify JNDI Context can be accessed using t3s cluster URL")
  public void testMiiGetCustomSSLContext() {
   
    // build the standalone Client on Admin pod after rolling restart
    String destLocation = "/u01/SslTestClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESOURCE_DIR, "ssl", "SslTestClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(adminServerPodName, domainNamespace, destLocation);
    
    runClientOnAdminPod();

    boolean psuccess = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 3),
        String.format("replica patching to 3 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(psuccess,
        String.format("Cluster replica patching failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodReadyAndServiceExists(managedServerPrefix + "3", domainUid, domainNamespace);

    runClientOnAdminPod();
  }

  // Run standalone client to get initial context using t3s cluster url
  private void runClientOnAdminPod() {

    StringBuffer extOpts = new StringBuffer("");
    extOpts.append("-Dweblogic.security.SSL.ignoreHostnameVerification=true ");
    extOpts.append("-Dweblogic.security.SSL.trustedCAKeyStore=/shared/TrustKeyStore.jks ");
    extOpts.append("-Dweblogic.security.SSL.trustedCAKeyStorePassPhrase=changeit ");
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Wait for client to get Initial context "
                    + "(elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(runClientInsidePod(adminServerPodName, domainNamespace,
            "/u01", extOpts.toString() + " SslTestClient", "t3s://" + domainUid + "-cluster-cluster-1:8002"));
  }
}
