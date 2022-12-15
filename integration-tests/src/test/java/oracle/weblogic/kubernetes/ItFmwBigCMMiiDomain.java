// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.getDBNodePort;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FmwUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to creat a FMW domain in model in image with over 1Mb data
 * to test that generated introspector Config Maps will be splitted to smaller than 1Mb
 * and domain will be started and running.
 */
@DisplayName("Test to a create FMW model in image domain "
    + "with introspect Config Map bigger then 1 Mb and start the domain")
@Tag("kind-parallel")
@Tag("okd-fmw-cert")
@IntegrationTest
@Tag("olcne")
class ItFmwBigCMMiiDomain {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String jrfDomainNamespace = null;
  private static String jrfMiiImage = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainmii";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String modelFile = "model-bigcm-jrf.yaml";
  private static final String PROPS_TEMP_DIR = RESULTS_ROOT + "/fwmdomaintemp";
  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private final String domainUid = "jrfdomain-mii";
  private final int replicaCount = 1;
  private final String adminSecretName = domainUid + "-weblogic-credentials";
  private final String encryptionSecretName = domainUid + "-encryptionsecret";
  private final String rcuaccessSecretName = domainUid + "-rcu-access";
  private final String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
  static int dbNodePort;

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domain.
   * Pull FMW image and Oracle DB image.
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
    jrfDomainNamespace = namespaces.get(2);

    logger.info("Start DB and create RCU schema for namespace: {0}, dbListenerPort: {1}, RCU prefix: {2}, "
         + "dbUrl: {3}, dbImage: {4},  fmwImage: {5} ", dbNamespace, dbListenerPort, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, getNextFreePort(), dbUrl, dbListenerPort),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbUrl %s, dbListenerPost %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl, dbListenerPort));

    dbNodePort = getDBNodePort(dbNamespace, "oracledb");
    logger.info("DB Node Port = {0}", dbNodePort);
    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, jrfDomainNamespace);

    logger.info("For ItFmwMiiDomain using DB image: {0}, FMW image {1}",
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);

  }

  /**
   * Create a FMW model in image domain with big data.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   * Verify that multiple introspector CMs are produced if data is > 1Mb
   */
  @Test
  @DisplayName("Create FMW Domain model in image with big introspector CM")
  void testFmwBigCMModelInImage() {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(jrfDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        jrfDomainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        jrfDomainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX, RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        jrfDomainNamespace,
        RCUSCHEMAPREFIX,
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        jrfDomainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    logger.info("Create an image with jrf model file");

    String text = "SOMEVERYVERYVERYBIGDATAFORPROPERTY";
    int numberOfLines = 100000;
    StringBuffer propVal = new StringBuffer();
    for (int i = 0; i < numberOfLines; i++) {
      propVal.append(text);
    }
    // create a temporary model file with 1M data stored
    File testmodelFile = assertDoesNotThrow(() ->
            File.createTempFile("modelBigCM", ".yaml"),
        "Failed to create domain properties file");

    final Path srcModelFile = Paths.get(MODEL_DIR, modelFile);
    final Path targetModelFile = Paths.get(testmodelFile.toString());
    assertDoesNotThrow(() ->  Files.copy(srcModelFile, targetModelFile, StandardCopyOption.REPLACE_EXISTING),
        "Failed to copy file " + srcModelFile + " to file " + targetModelFile);

    assertDoesNotThrow(() -> replaceStringInFile(targetModelFile.toString(),
        "BIGDATAREPLACE",
        propVal.toString()), "Can't replace the string BIGDATAREPLACE in " + targetModelFile);
    assertDoesNotThrow(() -> replaceStringInFile(targetModelFile.toString(),
        "@@PROP:K8S_NODEPORT_HOST@@:@@PROP:DBPORT@@",
        String.format("%s:%s", K8S_NODEPORT_HOST, dbNodePort)),
        "Can't replace the string @@PROP:K8S_NODEPORT_HOST@@:@@PROP:DBPORT@@ in " + targetModelFile);

    final List<String> modelList = Collections.singletonList(targetModelFile.toString());
    String jrfMii1Image = createMiiImageAndVerify(
        "jrf-mii-image",
        modelList,
        Collections.singletonList(MII_BASIC_APP_NAME),
        FMWINFRA_IMAGE_NAME,
        FMWINFRA_IMAGE_TAG,
        "JRF",
        false);

    // push the image to a registry to make it accessible in multi-node cluster
    imageRepoLoginAndPushImageToRegistry(jrfMii1Image);

    // create the domain object
    DomainResource domain = createDomainResource(domainUid,
        jrfDomainNamespace,
        adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        encryptionSecretName,
        rcuaccessSecretName,
        opsswalletpassSecretName,
        jrfMii1Image);

    createDomainAndVerify(domain, jrfDomainNamespace);
    verifyDomainReady(jrfDomainNamespace, domainUid, replicaCount);
    // check if multiple configmaps are created
    try {
      if (!Kubernetes.listConfigMaps(jrfDomainNamespace).getItems().isEmpty()) {
        logger.info("Getting Config Maps List");
        int cmTotalSize = 0;
        List<V1ConfigMap> items = Kubernetes.listConfigMaps(jrfDomainNamespace).getItems();
        List<V1ConfigMap> itemsCM = new ArrayList<>();
        for (var item : items) {
          if (item.getMetadata().getName().contains("introspect")) {
            logger.info("Found ConfigMap " + item.getMetadata().getName());
            logger.info("Found ConfigMap size " + item.toString().getBytes(UTF_8).length + " bytes");
            cmTotalSize = cmTotalSize + item.toString().getBytes(UTF_8).length;
            itemsCM.add(item);
          }
        }
        assertTrue((cmTotalSize > 1000000) && (itemsCM.size() > 1),
            "Produced introspector domain cm is bigger than 1M and was not splitted");
      }
    } catch (Exception ex) {
      throw new RuntimeException("Failed to process config maps" + ex.getMessage());
    }
  }
}
