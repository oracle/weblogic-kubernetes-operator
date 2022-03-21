// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResouceByPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test implicit upgrade of domain resource with auximage in v8 format")
@IntegrationTest
class ItAuxDomainImplicitUpgrade {
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private final int replicaCount = 2;
  private static String adminSecretName;
  private static String encryptionSecretName;

  /**
   * Install Operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        ENCRYPION_USERNAME_DEFAULT, ENCRYPION_PASSWORD_DEFAULT);

    // build app
    assertTrue(buildAppArchive(defaultAppParams()
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));
  }

  /**
   * Create v8 domain resource with auxiliary image(s).
   * The first image (model-only-image) only contains wls model file 
   * The second image (wdt-only-image) only contains wdt installation
   * The third image (config-only-image) only contains JMS/JDBC configuration
   * Use an domain.yaml file with API Version expliciltly set to v8.
   * Use the v8 style auxililiary configuration supported in WKO v3.3.x
   * Start the Operator with latest version
   * Here the webhook infra started in Operator namespace should implicitly 
   *  upgrade the domain resource to native k8s format with initContainer 
   *  configuration in ServerPod section and start the domain 
   */
  @Test
  @DisplayName("Test implicit upgrade of v8 version of Auxiliary Domain")
  void testImplicitV8MutipleAuxImagesDomainUpgrade() {

    String modelOnlyImageTag = "model-only-image";
    String wdtOnlyImageTag = "wdt-only-image";
    String configOnlyImageTag = "config-only-image";

    String modelOnlyImage = MII_AUXILIARY_IMAGE_NAME + ":" +  modelOnlyImageTag;
    String wdtOnlyImage = MII_AUXILIARY_IMAGE_NAME + ":" +  wdtOnlyImageTag;
    String configOnlyImage = MII_AUXILIARY_IMAGE_NAME + ":" +  configOnlyImageTag;
    List<String> archiveList = Collections.singletonList(ARCHIVE_DIR + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);

    // Create auxiliary image(s) using imagetool command if does not exists
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(modelOnlyImageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion("NONE");
    logger.info("Creating auxiliary image {0} using imagetool.sh ", modelOnlyImage);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, modelOnlyImageTag, witParams);

    modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelFiles(modelList)
            .modelImageTag(configOnlyImageTag)
            .wdtVersion("NONE");
    logger.info("Creating auxiliary image {0} using imagetool.sh ", configOnlyImage);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, configOnlyImageTag, witParams);

    witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(wdtOnlyImageTag)
            .wdtVersion("latest");
    logger.info("Creating auxiliary image {0} using imagetool.sh ", wdtOnlyImage);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, wdtOnlyImageTag, witParams);

    // Generate a v8 version of domain.yaml file from a template file 
    // replacing domain namespace, domain uid, base image and aux image
    Map<String, String> templateMap  = new HashMap();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("MODEL_ONLY_IMAGE", modelOnlyImage);
    templateMap.put("WDT_ONLY_IMAGE", wdtOnlyImage);
    templateMap.put("CONFIG_ONLY_IMAGE", configOnlyImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxiliary.domain.template.v8.yaml");
    Path targetDomainFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDomainFile.toString(),
        "domain.yaml", templateMap));
    logger.info("Generated Domain Resource file {0}", targetDomainFile);

    // run kubectl to create the domain
    logger.info("Run kubectl to create the domain");
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl apply -f "
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

    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";
    
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    StringBuffer getDomain = new StringBuffer("kubectl get domain ");
    getDomain.append(domainUid);
    getDomain.append(" -n " + domainNamespace);
    getDomain.append(" -o yaml | grep compatibility-mode-operator");
    // Get the domain in yaml format
    ExecResult dresult = assertDoesNotThrow(
                 () -> exec(new String(getDomain), true));
    logger.info("Get domain command {0}", getDomain.toString());
    logger.info("kubectl get domain returned {0}", dresult.toString());
    assertTrue(dresult.stdout().contains("compatibility-mode-operator"), "Failed to implicitly upgrade v8 aux domain");
    
    //create router for admin service on OKD
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);
    // check the sample app is accessible from managed servers
    logger.info("Check and wait for the sample application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      int index = i;
      testUntil(withStandardRetryPolicy,
          () -> appAccessibleInPod(domainNamespace, managedServerPrefix + index, "8001",
              "sample-war/index.jsp", MII_APP_RESPONSE_V1 + index),
          logger,
          "application {0} is running on pod {1} in namespace {2}",
          "sample-war",
          managedServerPrefix + index,
          domainNamespace);
    }
  }

  /**
   * Check Configured JMS Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  private static void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName,
                                               String adminSvcExtHost) {
    verifyConfiguredSystemResource(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JMSSystemResources", "TestClusterJmsModule2", "200");
  }

  /**
   * Check Configured JDBC Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  public static void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName,
                                                String adminSvcExtHost) {

    verifyConfiguredSystemResouceByPath(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB");
  }
}
