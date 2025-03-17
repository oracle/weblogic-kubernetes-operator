// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleAllClustersInDomain;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.SslUtils.generateJksStores;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies usage of CustomIdentityCustomTrust on PV.
 * Create an MII domain with an attached persistent volume.
 * Configure custom identity and custom trust on server template
 * Don't explicitly set the SSL port on the server template.
 * The default will be set to 8100.
 * Put the IdentityKeyStore.jks  and TrustKeyStore.jks on /shared directory
 *  after administration server pod is started so that it can be accessible
 *  from all managed server pods
 * Once all servers are started get the JNDI initial context using cluster
 *  service URL with t3s protocol.
 * Repeat the same after scaling the cluster
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test verifies usage of CustomIdentityCustomTrust on PV")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItMiiCustomSslStore {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static int replicaCount = 2;
  private static final String domainUid = "mii-custom-ssl";
  private static final String pvName = getUniqueName(domainUid + "-pv-");
  private static final String pvcName = getUniqueName(domainUid + "-pvc-");
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

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create secret for admin credential with special characters
    // the resultant password is ##W%*}!"'"`']\\\\//1$$~x
    // let the user name be something other than weblogic say wlsadmin
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,
            "wlsadmin", "##W%*}!\"'\"`']\\\\//1$$~x", domainNamespace),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret with special characters
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "#%*!`${ls}'${DOMAIN_UID}1~3x", domainNamespace),
             String.format("createSecret failed for %s", encryptionSecretName));

    String configMapName = "mii-ssl-configmap";
    // Copy the model file to RESULTS_ROOT
    assertDoesNotThrow(() -> java.nio.file.Files.copy(
                    Paths.get(MODEL_DIR, "mii.ssl.yaml"),
                    Paths.get(RESULTS_ROOT, "mii.ssl.yaml"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING),
            "Copy mii.ssl.yaml to RESULTS_ROOT failed");
    assertDoesNotThrow(() ->
              replaceStringInFile(get(RESULTS_ROOT, "mii.ssl.yaml").toString(),
                      "/shared/", "/shared/" + domainNamespace + "/" + domainUid + "/"));

    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(RESULTS_ROOT + "/mii.ssl.yaml"));

    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create PV, PVC for logs/data
    createPV(pvName, domainUid, ItMiiCustomSslStore.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    // create the domain CR with a pre-defined configmap
    DomainResource domain = createDomainResourceWithLogHome(domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount,
        pvName, pvcName, configMapName,
        null, false, false);

    // wait for the domain to exist
    createDomainAndVerify(domain, domainNamespace);

    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // Generate JKS Keystore using openssl before
    // managed server services and pods are ready
    String uniquePath = "/shared/" + domainNamespace + "/" + domainUid;
    generateJksStores();
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESULTS_ROOT, "IdentityKeyStore.jks"),
        Paths.get(uniquePath + "/IdentityKeyStore.jks")));
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESULTS_ROOT, "TrustKeyStore.jks"),
        Paths.get(uniquePath + "/TrustKeyStore.jks")));

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Verify a standalone java client can access JNDI Context inside a pod.
   * The client uses t3s cluster URL with custom SSL TrustStore on the command line
   */
  @Test
  @DisplayName("Verify JNDI Context can be accessed using t3s cluster URL")
  void testMiiGetCustomSSLContext() {

    // build the standalone Client on Admin pod after rolling restart
    String destLocation = "/u01/SslTestClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESOURCE_DIR, "ssl", "SslTestClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(adminServerPodName, domainNamespace, destLocation);

    runClientOnAdminPod();

    boolean psuccess = scaleAllClustersInDomain(domainUid, domainNamespace, 3);
    assertTrue(psuccess,
        String.format("Cluster replica patching failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodReadyAndServiceExists(managedServerPrefix + "3", domainUid, domainNamespace);

    runClientOnAdminPod();
  }

  // Run standalone client to get initial context using t3s cluster url
  private void runClientOnAdminPod() {

    StringBuffer extOpts = new StringBuffer("");
    extOpts.append("-Dweblogic.security.SSL.ignoreHostnameVerification=true ");
    extOpts.append("-Dweblogic.security.SSL.trustedCAKeyStore=/shared/"
        + domainNamespace + "/" + domainUid + "/TrustKeyStore.jks ");
    extOpts.append("-Dweblogic.security.SSL.trustedCAKeyStorePassPhrase=changeit ");
    String managedServerPort;
    if (WEBLOGIC_IMAGE_TAG.contains("12")) {
      managedServerPort = "8100";
    } else {
      managedServerPort = "7002";
    }
    testUntil(
        runClientInsidePod(adminServerPodName, domainNamespace,
            "/u01", extOpts.toString() + " SslTestClient", "t3s://"
            + domainUid + "-cluster-cluster-1:" + managedServerPort),
        logger,
        "Wait for client to get Initial context");
  }
}
