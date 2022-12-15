// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.FmwUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getCurrentIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyUpdateWebLogicCredential;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.DbUtils.updateRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.updateRcuPassword;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyEMconsoleAccess;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.restartOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResourceServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create FMW model in image domain and start the domain")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-fmw-cert")
class ItFmwMiiDomain {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String fmwDomainNamespace = null;
  private static String fmwMiiImage = null;

  private static final String RCUSCHEMAPREFIX = "FMWDOMAINMII";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAPASSWORDNEW = "Oradoc_db2";
  private static final String modelFile = "model-singleclusterdomain-sampleapp-jrf.yaml";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private String domainUid = "fmwdomain-mii";
  private String adminServerPodName = domainUid + "-admin-server";
  private String managedServerPrefix = domainUid + "-managed-server";
  private int replicaCount = 1;
  private String adminSecretName = domainUid + "-weblogic-credentials";
  private String encryptionSecretName = domainUid + "-encryptionsecret";
  private String rcuaccessSecretName = domainUid + "-rcu-access";
  private String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
  private String opsswalletfileSecretName = domainUid + "opss-wallet-file-secret";
  private String adminSvcExtHost = null;

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {

    logger = getLogger();
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    final int dbListenerPort = getNextFreePort();
    ORACLEDBSUFFIX = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    fmwDomainNamespace = namespaces.get(2);

    logger.info("Start DB and create RCU schema for namespace: {0}, dbListenerPort: {1}, RCU prefix: {2}, "
         + "dbUrl: {3}, dbImage: {4},  fmwImage: {5} ", dbNamespace, dbListenerPort, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, getNextFreePort(), dbUrl, dbListenerPort),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbUrl %s, dbListenerPost %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl, dbListenerPort));

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, fmwDomainNamespace);

    logger.info("For ItFmwMiiDomain using DB image: {0}, FMW image {1}",
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);

  }

  /**
   * Create a basic FMW model in image domain.
   * Create the FMW domain with introspectVersion
   * After domain is created and introspector exists restart the operator.
   * Verify the restarted operator can find the existing introspector and wait for it to complete
   * rather than replacing a new introspector.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  @Order(1)
  @Test
  @DisplayName("Create FMW Domain model in image")
  void testFmwModelInImage() {
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(fmwDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        fmwDomainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        fmwDomainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX, RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        fmwDomainNamespace,
        RCUSCHEMAPREFIX,
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        fmwDomainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    logger.info("Create an image with jrf model file");
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);
    fmwMiiImage = createMiiImageAndVerify(
        "jrf-mii-image",
        modelList,
        Collections.singletonList(MII_BASIC_APP_NAME),
        FMWINFRA_IMAGE_NAME,
        FMWINFRA_IMAGE_TAG,
        "JRF",
        false);

    // push the image to a registry to make it accessible in multi-node cluster
    imageRepoLoginAndPushImageToRegistry(fmwMiiImage);

    // create the domain object
    DomainResource domain = FmwUtils.createDomainResource(domainUid,
        fmwDomainNamespace,
        adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        encryptionSecretName,
        rcuaccessSecretName,
        opsswalletpassSecretName,
        fmwMiiImage);

    createDomainAndVerify(domain, fmwDomainNamespace);

    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs");
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    logger.info("Checking introspector pod exists and introspect version");
    checkPodExists(introspectPodNameBase, domainUid, fmwDomainNamespace);
    String introspectPodName = assertDoesNotThrow(() -> Kubernetes.listPods(fmwDomainNamespace, null)
        .getItems().get(0).getMetadata().getName(),
        String.format("Get intrspector pod name failed with ApiException in namespace %s", fmwDomainNamespace));
    String introspectVersion1 =
        assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, fmwDomainNamespace));
    logger.info("Before restarting operator introspector pod name is: {0}, introspectVersion is: {1}",
        introspectPodName, introspectVersion1);

    logger.info("Restarting operator in the namespace: " + opNamespace);
    restartOperator(opNamespace);
    //verify the exact same introspector pod exists and Version does not change
    logger.info("Checking the exact same introspector pod {0} exists in the namespace {1}",
        introspectPodName, fmwDomainNamespace);
    checkPodExists(introspectPodName, domainUid, fmwDomainNamespace);
    String introspectVersion2 =
        assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, fmwDomainNamespace));
    logger.info("After operator restart introspectVersion is: " + introspectVersion2);
    assertEquals(introspectVersion1, introspectVersion2, "introspectVersion changes after operator restart");

    verifyDomainReady(fmwDomainNamespace, domainUid, replicaCount);
    // Expose the admin service external node port as  a route for OKD
    adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), fmwDomainNamespace);
    verifyEMconsoleAccess(fmwDomainNamespace, domainUid, adminSvcExtHost);
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file
   * Restore the OPSS key wallet file to a Kubernetes secret.
   * Shutdown the domain.
   * Using the same RCU schema to restart the same JRF domain with restored OPSS key wallet file secret.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  @Order(2)
  @Test
  @DisplayName("Reuse the same RCU schema to restart JRF domain")
  void testReuseRCUschemaToRestartDomain() {
    saveAndRestoreOpssWalletfileSecret(fmwDomainNamespace, domainUid, opsswalletfileSecretName);
    shutdownDomain();
    patchDomainWithWalletFileSecret(opsswalletfileSecretName);
    startupDomain();
    verifyDomainReady(fmwDomainNamespace, domainUid, replicaCount);
  }

  /**
   * Shutdown the FMW domain completely.
   * Update all the passwords for the RCU schema.
   * Update the RCU access secret with new RCU schema password.
   * Start the domain and verify domain is up and running.
   */
  @Order(3)
  @Test
  @DisplayName("Update RCU schema password")
  void testUpdateRcuSchemaPassword() {
    shutdownDomain();
    logger.info("Updating RCU schema password with dbNamespace: {0}, RCU prefix: {1}, new schemapassword: {2}",
        dbNamespace, RCUSCHEMAPREFIX, RCUSCHEMAPASSWORDNEW);
    updateRcuPassword(dbNamespace, RCUSCHEMAPREFIX, RCUSCHEMAPASSWORDNEW);
    logger.info("Updating RCU access secret: {0}, with prefix: {1}, new schemapassword: {2}, dbUrl: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX, RCUSCHEMAPASSWORDNEW, dbUrl);
    assertDoesNotThrow(() -> updateRcuAccessSecret(
        rcuaccessSecretName,
        fmwDomainNamespace,
        RCUSCHEMAPREFIX,
        RCUSCHEMAPASSWORDNEW,
        dbUrl),
        String.format("update Secret failed for %s with new schema password %s", rcuaccessSecretName,
            RCUSCHEMAPASSWORDNEW));
    startupDomain();
    verifyDomainReady(fmwDomainNamespace, domainUid, replicaCount);
  }

  /**
   * After updating RCU schema password change the WebLogic Admin credential of the domain.
   * Update domainRestartVersion to trigger a rolling restart of server pods.
   * Verify all the server pods are re-started in a rolling fashion.
   * Check the validity of new credentials by accessing WebLogic RESTful Service.
   */
  @Order(4)
  @Test
  @DisplayName("Update WebLogic Credentials after updating RCU schema password")
  void testUpdateWebLogicCredentialAfterUpdateRcuSchemaPassword() {
    verifyUpdateWebLogicCredential(adminSvcExtHost, fmwDomainNamespace, domainUid, adminServerPodName,
        managedServerPrefix, replicaCount, "-c1");
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file.
   * @param namespace namespace where JRF domain exists
   * @param domainUid unique domain Uid
   * @param walletfileSecretName name of wallet file secret
   */
  private void saveAndRestoreOpssWalletfileSecret(String namespace, String domainUid,
       String walletfileSecretName) {
    Path saveAndRestoreOpssPath =
         Paths.get(RESOURCE_DIR, "bash-scripts", "opss-wallet.sh");
    String script = saveAndRestoreOpssPath.toString();
    logger.info("Script for saveAndRestoreOpss is {0)", script);

    //save opss wallet file
    String command1 = script + " -d " + domainUid + " -n " + namespace + " -s";
    logger.info("Save wallet file command: {0}", command1);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command1)
            .saveResults(true)
            .redirect(true))
        .execute());

    //restore opss wallet password secret
    String command2 = script + " -d " + domainUid + " -n " + namespace + " -r" + " -ws " + walletfileSecretName;
    logger.info("Restore wallet file command: {0}", command2);
    assertTrue(() -> Command.withParams(
          defaultCommandParams()
            .command(command2)
            .saveResults(true)
            .redirect(true))
        .execute());

  }

  /**
   * Shutdown the domain by setting serverStartPolicy as "Never".
   */
  private void shutdownDomain() {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "Never", fmwDomainNamespace, domainUid);
    logger.info("Domain is patched to stop entire WebLogic domain");

    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, fmwDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, fmwDomainNamespace);
    }

    logger.info("Domain shutdown success");

  }

  /**
   * Startup the domain by setting serverStartPolicy as "IfNeeded".
   */
  private void startupDomain() {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "IfNeeded", fmwDomainNamespace, domainUid);
    logger.info("Domain is patched to start all servers in the domain");
  }

  /**
   * Patch the domain with opss wallet file secret.
   * @param opssWalletFileSecretName the name of opps wallet file secret
   * @return true if patching succeeds, false otherwise
   */
  private boolean patchDomainWithWalletFileSecret(String opssWalletFileSecretName) {
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/configuration/opss/walletFileSecret\", ")
        .append("\"value\": \"")
        .append(opssWalletFileSecretName)
        .append("\"}]");

    logger.info("Adding opssWalletPasswordSecretName for domain {0} in namespace {1} using patch string: {2}",
        domainUid, fmwDomainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, fmwDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

}
