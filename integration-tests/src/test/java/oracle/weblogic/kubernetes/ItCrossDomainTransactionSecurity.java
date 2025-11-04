// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.Model;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.WDTArchiveHelper;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonMiiTestUtils;
import oracle.weblogic.kubernetes.utils.ConfigMapUtils;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OperatorUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Tag("kind-parallel")
@Tag("oke-parallel")
class ItCrossDomainTransactionSecurity {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static LoggingFacade logger = null;
  private String domain1Uid = "domain1";
  private String domain2Uid = "domain2";
  private static String miiAuxiliaryImage1Tag = "auximage1" + getDateAndTimeStamp();
  private static String miiAuxiliaryImage2Tag = "auximage2" + getDateAndTimeStamp();
  private static String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage1Tag;
  private static String miiAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage2Tag;
  private final int replicaCount = 1;
  private static String adminSecretName1;
  private static String adminSecretName2;
  private static String encryptionSecretName;
  private static String storeDir;
  private static String archiveFile;

  /**
   * Install Operator, generate custom store and create auxiliary domains.
   *
   * @param namespaces list of namespaces
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) throws IOException {
    logger = getLogger();

    storeDir = Files.createTempDirectory(Paths.get(WORK_DIR), "cxtxcustom").toString();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(OperatorUtils.OperatorInstallConfig.builder()
        .opNamespace(opNamespace)
        .domainNamespaces(domainNamespace)
        .build());

    // create secret for admin credentials
    logger.info("Create secret for domain1 admin credentials");
    adminSecretName1 = "domain1-weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName1, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    logger.info("Create secret for domain2 admin credentials");
    adminSecretName2 = "domain2-weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName2, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        ENCRYPION_USERNAME_DEFAULT, ENCRYPION_PASSWORD_DEFAULT);

    generateKeyStores();
    createAuxDomain();
  }

  /*
   * "Creating Domain SelfSigned certificates, Identity Store and Trust store"
   */
  private static void generateKeyStores() throws UnknownHostException, IOException {
    String keyPass = "changeit";
    String storePass = "changeit";
    String hostname = InetAddress.getLocalHost().getHostAddress();

    //Creating Domain SelfSigned Identity Store
    String command = "keytool "
        + "-genkey "
        + "-keyalg RSA "
        + "-alias server_alias "
        + "-validity 360 "
        + "-keysize 2048 "
        + "-dname \"CN=" + hostname
        + " OU=WLS, "
        + "O=Oracle, "
        + "L=Basking Ridge, "
        + "ST=CA, C=US\" "
        + "-deststoretype pkcs12  "
        + "-storepass " + storePass
        + " -keypass " + keyPass
        + " -keystore " + storeDir + "/DomainIdentityStore.p12";
    assertTrue(runCommand(command), "Failed to create domain identity store");
    command = "keytool "
        + "-export "
        + "-alias server_alias "
        + "-file " + storeDir + "/domain.der "
        + "-keystore " + storeDir + "/DomainIdentityStore.p12 "
        + "-storepass " + storePass
        + " -keypass " + keyPass;
    assertTrue(runCommand(command), "Failed to export domain identity store");
    //Creating Domain/Client Trust Store by importing certificate
    command = "keytool "
        + "-import -trustcacerts "
        + "-alias server_trust "
        + "-file " + storeDir + "/domain.der "
        + "-keystore " + storeDir + "/DomainTrustStore.p12 "
        + "-storepass " + storePass
        + " -keypass " + keyPass
        + " -deststoretype pkcs12 -noprompt";
    assertTrue(runCommand(command), "Failed to create domain trust store");
    command = "keytool "
        + "-import "
        + "-trustcacerts "
        + "-alias client_trust "
        + "-file " + storeDir + "/domain.der "
        + "-keystore " + storeDir + "/ClientTrustStore.p12 "
        + "-storepass " + storePass
        + " -keypass " + keyPass
        + " -deststoretype pkcs12 -noprompt";
    assertTrue(runCommand(command), "Failed to import domain trust store");
  }

  private static boolean runCommand(String command) {
    return Command.withParams(
        defaultCommandParams()
            .command(command)
            .verbose(true)
            .redirect(false))
        .execute();
  }

  /**
   * create auxiliary domain images and push it to the repo with custom ssl stores.
   *
   * @throws IOException when creating certificates fails.
   */
  private static void createAuxDomain() throws IOException {

    //create the archive.zip with appliocation and cusom store files
    AppParams appParams = WDTArchiveHelper
        .defaultAppParams().appName("webapp")
        .srcDirList(List.of(WEBLOGIC_IMAGE_TO_USE_IN_SPEC.contains("15")
            ? APP_DIR + "/jakartawebapp" : APP_DIR + "/javaxwebapp"));
    boolean status = WDTArchiveHelper.withParams(appParams)
        .createArchiveWithStructuredApplication("archive");
    assertTrue(status, "Failed to create a archive of application");
    String appArchiveDir = appParams.appArchiveDir();
    status = WDTArchiveHelper.withParams(appParams)
        .addServerKeystore(appArchiveDir + "/archive.zip", "cluster-1-template",
            storeDir + "/DomainTrustStore.p12");
    assertTrue(status, "Failed to create a archive of application");
    status = WDTArchiveHelper.withParams(appParams)
        .addServerKeystore(appArchiveDir + "/archive.zip", "cluster-1-template",
            storeDir + "/DomainIdentityStore.p12");
    assertTrue(status, "Failed to create a archive of application");
    //WDTArchiveHelper.withParams(appParams).addCustom(miiImage, miiImage);

    String modelFile;
    if (WEBLOGIC_IMAGE_TO_USE_IN_SPEC.contains("14.1.2") || WEBLOGIC_IMAGE_TO_USE_IN_SPEC.contains("15")) {
      modelFile = "model.dynamic.custom.ssl.wls.yaml";
    } else {
      modelFile = "model.dynamic.demo.ssl.yaml";
    }

    // image1 with model files for domain config, ds, app and wdt install files
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/archive.zip");
    archiveFile = appParams.appArchiveDir() + "/archive.zip";
    logger.info(archiveFile);

    List<String> modelProperties = new ArrayList<>();
    String modelProperty = "model1.properties";
    modelProperties.add(RESOURCE_DIR + "/customstore/" + modelProperty);

    List<String> modelList = new ArrayList<>();
    modelList.add(RESOURCE_DIR + "/customstore/models/" + modelFile);

    WitParams witParams
        = new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage1Tag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .modelVariableFiles(modelProperties);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage1Tag, witParams);

    modelProperties.clear();
    modelProperty = "model2.properties";
    modelProperties.add(RESOURCE_DIR + "/customstore/" + modelProperty);
    witParams
        = new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage2Tag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .modelVariableFiles(modelProperties);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage2Tag, witParams);

  }
   
  /**
   * Create 2 domains using sparse models and auxiliary images.
   *
   * Verify the domain is running with custom ssl certificates and cross domain trx works with non ssl and ssl urls.
   */
  @Test
  @DisplayName("Test to do cross domain using transaction with custom SSL store")
  void testCrossDomainTxWithSSL() throws InterruptedException, IOException {
    String domain1cm = "domain1-mii-cm";
    String domain2cm = "domain2-mii-cm";

    ConfigMapUtils.createConfigMapFromFiles(domain1cm,
        List.of(
            Paths.get(RESOURCE_DIR, "customstore", "model1.properties"),
            Paths.get(RESOURCE_DIR, "customstore", "models", "sparse.application.yaml"),
            Paths.get(RESOURCE_DIR, "customstore", "models", "sparse.jdbc.yaml"),
            Paths.get(RESOURCE_DIR, "customstore", "models", "sparse.jms.yaml")), domainNamespace);

    ConfigMapUtils.createConfigMapFromFiles(domain2cm,
        List.of(
            Paths.get(RESOURCE_DIR, "customstore", "model2.properties"),
            Paths.get(RESOURCE_DIR, "customstore", "models", "sparse.application.yaml"),
            Paths.get(RESOURCE_DIR, "customstore", "models", "sparse.jdbc.yaml"),
            Paths.get(RESOURCE_DIR, "customstore", "models", "sparse.jms.yaml")), domainNamespace);

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domain1Uid, miiAuxiliaryImage1);

    DomainResource domainCR = CommonMiiTestUtils
        .createDomainResourceWithAuxiliaryImage(domain1Uid, domainNamespace,
            WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName1,
            createSecretsForImageRepos(domainNamespace),
            encryptionSecretName, auxiliaryImagePath,
            miiAuxiliaryImage1);

    HashMap<String, String> domain1Map = new HashMap<>();
    domain1Map.put("weblogic.domainUID", domain1Uid);
    domainCR.metadata()
        .name(domain1Uid)
        .namespace(domainNamespace)
        .labels(domain1Map);
    domainCR.spec()
        .configuration(new Configuration()
            .model(new Model()
                .configMap(domain1cm)
                .domainType("WLS")
                .withAuxiliaryImages(List.of(new AuxiliaryImage()
                    .image(miiAuxiliaryImage1)
                    .sourceModelHome("/auxiliary/models")))
                .runtimeEncryptionSecret(encryptionSecretName)));

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domain1Uid, miiAuxiliaryImage1, domainNamespace);
    String adminServerPodName = domain1Uid + "-adminserver";
    String managedServerPrefix = domain1Uid + "-managed-server";

    createDomainAndVerify(domain1Uid, domainCR, domainNamespace, adminServerPodName,
        managedServerPrefix, replicaCount);

    logger.info("domain1 CR\n{0}\n", Yaml.dump(domainCR));

    //create the second domain using auxiliary image 2
    AuxiliaryImage image = domainCR.spec()
        .configuration()
        .model()
        .getAuxiliaryImages().getFirst()
        .image(miiAuxiliaryImage2).sourceModelHome("/auxiliary/models");

    HashMap<String, String> domain2Map = new HashMap<>();
    domain2Map.put("weblogic.domainUID", domain2Uid);
    domainCR.metadata().name(domain2Uid)
        .namespace(domainNamespace).labels(domain2Map);

    domainCR.spec()
        .domainUid(domain2Uid)
        .configuration()
        .model()
        .configMap(domain2cm)
        .withAuxiliaryImages(List.of(image));

    logger.info("domain2 CR\n{0}\n", Yaml.dump(domainCR));

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domain2Uid, miiAuxiliaryImage2, domainNamespace);
    adminServerPodName = domain2Uid + "-adminserver";
    managedServerPrefix = domain2Uid + "-managed-server";

    createDomainAndVerify(domain2Uid, domainCR, domainNamespace, adminServerPodName,
        managedServerPrefix, replicaCount);

    //verify the cross domain transaction
    checkCrossDomainTx();
  }

  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    if (!SKIP_CLEANUP) {
      // delete images
      for (String image : List.of(miiAuxiliaryImage1, miiAuxiliaryImage2)) {
        deleteImage(image);
      }
    }
  }

  /**
   * Copies the JmsCliennt and shell script to run the clients to the admin pod and verifies the transactions.
   *
   * @throws IOException when file copying to pod fails.
   */
  private void checkCrossDomainTx() throws IOException {
    Path jmsClientSrc = Paths.get(RESOURCE_DIR, "customstore", "JmsClient.java");
    Path jmsClientDst = Paths.get(storeDir, "JmsClient.java");
    Path shellScriptSrc = Paths.get(RESOURCE_DIR, "customstore", "runtest.sh");
    Path shellScriptDst = Paths.get("/u01", "domains", "runtest.sh");
    String expectedResult = "All expected strings were found in the log";

    if (!WEBLOGIC_IMAGE_TO_USE_IN_SPEC.contains("15")) {
      Files.copy(jmsClientSrc, jmsClientDst, StandardCopyOption.REPLACE_EXISTING);
      replaceStringInFile(jmsClientDst.toString(), "jakarta", "javax");
    } else {
      Files.copy(jmsClientSrc, jmsClientDst, StandardCopyOption.REPLACE_EXISTING);
    }
    String adminServerPodName = domain1Uid + "-adminserver";
    Path destLocationInPod = Paths.get("/u01", "domains", "JmsClient.java");
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "weblogic-server",
        jmsClientDst,
        destLocationInPod
    ));
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "weblogic-server",
        shellScriptSrc,
        shellScriptDst
    ));
    assertTrue(runClientInsidePodVerifyResult(adminServerPodName, domainNamespace,
        shellScriptDst, expectedResult, "t3", "8001"), "unsecure transactiuon didn't go through");
    if (!WEBLOGIC_IMAGE_TO_USE_IN_SPEC.contains("12.2")) {
      assertTrue(runClientInsidePodVerifyResult(adminServerPodName, domainNamespace,
          shellScriptDst, expectedResult, "t3s", "6000"), "secure transactiuon didn't go through");
    }
  }

  /**
   * Run the script inside the admin server pod.
   *
   * @param podName name of the pod in which to run the command
   * @param namespace namespace in which pod is running
   * @param shellScript the shell script to run inside the pod
   * @param expectedResult expected string from the script
   * @param args arguments to the shell script
   * @return true if script return 0
   */
  private static boolean runClientInsidePodVerifyResult(String podName, String namespace,
      Path shellScript, String expectedResult, String... args) {
    final LoggingFacade logger = getLogger();
    StringBuilder shellCmd = new StringBuilder(KUBERNETES_CLI + " exec -n ");
    shellCmd.append(namespace);
    shellCmd.append(" -it ");
    shellCmd.append(" -c weblogic-server ");
    shellCmd.append(podName);
    shellCmd.append(" -- /bin/bash -c \"");
    shellCmd.append(" ");
    shellCmd.append("chmod +x " + shellScript + ";");
    shellCmd.append(shellScript);
    shellCmd.append(" ");
    for (String arg : args) {
      shellCmd.append(arg).append(" ");
    }
    shellCmd.append(" \"");
    logger.info("shell command to be run {0}", shellCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> exec(shellCmd.toString(), true));
    logger.info("command returned {0}", result.toString());
    logger.info("command returned EXIT value {0}", result.exitValue());
    return ((result.exitValue() == 0 && result.stdout().contains(expectedResult)));
  }

}

