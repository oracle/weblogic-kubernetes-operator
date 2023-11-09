// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesDomainExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test implicit upgrade of auximage domain resource with api version v8")
@IntegrationTest
@Tag("kind-upgrade")
class ItAuxV8DomainImplicitUpgrade {
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static LoggingFacade logger = null;
  private final int replicaCount = 2;
  private static String adminSecretName;
  private static String encryptionSecretName;
  private static Map<String, OffsetDateTime> podsWithTimeStamps = null;
  private boolean foundCompatiblityContainer = false;
  private String domainUid = "implicit-upg";
  private static AppParams appParams = defaultAppParams()
      .appArchiveDir(ARCHIVE_DIR + ItAuxV8DomainImplicitUpgrade.class.getSimpleName());

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
    createTestRepoSecret(domainNamespace);
    createBaseRepoSecret(domainNamespace); // needed for AuxDomain

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
    assertTrue(buildAppArchive(appParams
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));
    // Remove any weblogic crd creaated from previous test class
    // Note: This class must not be run in parallel with other class
    Command.withParams(new CommandParams()
        .command(KUBERNETES_CLI + " delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();
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
   * Check the upgraded domain schema for a Compatiblity InitContainer in
   * Spec/ServerPod section of the domain resource
   */
  @Test
  @DisplayName("Test implicit upgrade of v8 version of Auxiliary Domain")
  void testMultipleAuxImagesV8Domain() {

    if (doesDomainExist(domainUid, DOMAIN_VERSION, domainNamespace)) {
      deleteDomainResource(domainNamespace, domainUid);
    }
    String modelOnlyImageTag = "model-only-image";
    String wdtOnlyImageTag = "wdt-only-image";
    String configOnlyImageTag = "config-only-image";

    String modelOnlyImage = MII_AUXILIARY_IMAGE_NAME + ":" +  modelOnlyImageTag;
    String wdtOnlyImage = MII_AUXILIARY_IMAGE_NAME + ":" +  wdtOnlyImageTag;
    String configOnlyImage = MII_AUXILIARY_IMAGE_NAME + ":" +  configOnlyImageTag;
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

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
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("MODEL_ONLY_IMAGE", modelOnlyImage);
    templateMap.put("WDT_ONLY_IMAGE", wdtOnlyImage);
    templateMap.put("CONFIG_ONLY_IMAGE", configOnlyImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxilary.multi.images.template.yaml");
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

    DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain, "Got null domain resource");
    List<V1Container> containerList = domain.getSpec().getServerPod().getInitContainers();
    assertNotNull(containerList, "/spec/serverPod/InitContainers is null");
    containerList.forEach(container -> {
      logger.info("The Init Container name is: {0} ", container.getName());
      if (container.getName().equalsIgnoreCase("compat-operator-aux-container1")) {
        logger.info("The Compatiblity Init Container found");
        foundCompatiblityContainer = true;
      }
    }
    );
    assertTrue(foundCompatiblityContainer, "The Compatiblity Init Container NOT found");
  }

  /**
   * Negative Test to create domain without WDT binary.
   * Check the error message is in domain events and operator pod log.
   */
  @Test
  @DisplayName("Negative Test to create domain without WDT binary")
  void testErrorPathV8DomainMissingWDTBinary() {

    if (doesDomainExist(domainUid, DOMAIN_VERSION, domainNamespace)) {
      deleteDomainResource(domainNamespace, domainUid);
    }

    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";

    String missingWdtTag = "missing-wdtbinary-image";
    String missingWdtImage = MII_AUXILIARY_IMAGE_NAME + ":" +  missingWdtTag;


    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(missingWdtTag)
            .modelFiles(modelList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, missingWdtTag, witParams);

    // Generate a v8 version of domain.yaml file from a template file
    // replacing domain namespace, domain uid, base image and aux image
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("AUX_IMAGE", missingWdtImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxilary.single.image.template.yaml");
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

    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "The domain resource 'spec.domainHomeSourceType' is 'FromModel'  and "
        + "a WebLogic Deploy Tool (WDT) install is not located at  'spec.configuration.model.wdtInstallHome'  "
        + "which is currently set to '/auxiliary/weblogic-deploy'";

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);
    logger.info("Auxiliary Image Error(missing wdt) reported in operator log");

  }

  /**
   * Negative Test to create domain without domain model file.
   * The auxiliary image contains only sparse JMS config.
   * Check the error message is in domain events and operator pod log
   */
  @Test
  @DisplayName("Negative Test to create domain without model file")
  void testErrorPathV8DomainMissingDomainConfig() {

    if (doesDomainExist(domainUid, DOMAIN_VERSION, domainNamespace)) {
      deleteDomainResource(domainNamespace, domainUid);
    }

    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";

    String missingModelTag = "missing-model-image";
    String missingModelImage = MII_AUXILIARY_IMAGE_NAME + ":" + missingModelTag;

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(missingModelTag)
            .modelFiles(modelList)
            .wdtVersion("latest");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, missingModelTag, witParams);

    // Generate a v8 version of domain.yaml file from a template file
    // replacing domain namespace, domain uid, base image and aux image
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("AUX_IMAGE", missingModelImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxilary.single.image.template.yaml");
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

    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg =
        "createDomain did not find the required domainInfo section in the model file /auxiliary/models/model.jms2.yaml";

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);
    logger.info("Auxiliary Image Error(missing model) reported in operator log");
  }

  /**
   * Negative Test to create domain with model file with wrong permission.
   * Check the error message is in domain events and operator pod log
   */
  @Test
  @DisplayName("Negative Test to create domain with file in auxiliary image not accessible by oracle user")
  void testErrorPathV8DomainFilePermission() {

    if (doesDomainExist(domainUid, DOMAIN_VERSION, domainNamespace)) {
      deleteDomainResource(domainNamespace, domainUid);
    }

    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";

    String permModelTag = "perm-model-image";
    String permModelImage = MII_AUXILIARY_IMAGE_NAME + ":" + permModelTag;

    //In case the previous test failed, ensure the created domain in the
    //same namespace is deleted.
    if (doesDomainExist(domainUid, DOMAIN_VERSION, domainNamespace)) {
      deleteDomainResource(domainNamespace, domainUid);
    }
    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(permModelTag)
            .modelFiles(modelList)
            .additionalBuildCommands(RESOURCE_DIR + "/auxiliaryimage/addBuildCommand.txt")
            .wdtVersion("latest");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, permModelTag, witParams);

    // Generate a v8 version of domain.yaml file from a template file
    // replacing domain namespace, domain uid, base image and aux image
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("AUX_IMAGE", permModelImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxilary.single.image.template.yaml");
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

    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "cp: can't open '/auxiliary/models/multi-model-one-ds.20.yaml': "
        + "Permission denied";

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);
    logger.info("Auxiliary Image Error(permission issue model) reported in operator log");
  }

  /**
   * Patch the domain with the different base image name.
   * Verify all the pods are restarted and back to ready state.
   *  Verify configured JMS and JDBC resources.
   */
  @Test
  @DisplayName("Test to update Base Weblogic Image Name")
  void testUpdateBaseImageV8AuxDomain() {

    if (doesDomainExist(domainUid, DOMAIN_VERSION, domainNamespace)) {
      deleteDomainResource(domainNamespace, domainUid);
    }

    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";

    String patchBaseTag = "patch-base-image";
    String patchBaseImage = MII_AUXILIARY_IMAGE_NAME + ":" + patchBaseTag;

    //In case the previous test failed, ensure the created domain in the
    //same namespace is deleted.
    if (doesDomainExist(domainUid, DOMAIN_VERSION, domainNamespace)) {
      deleteDomainResource(domainNamespace, domainUid);
    }

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");
    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(patchBaseTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion("latest");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, patchBaseTag, witParams);

    // Generate a v8 version of domain.yaml file from a template file
    // replacing domain namespace, domain uid, base image and aux image
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("AUX_IMAGE", patchBaseImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxilary.single.image.template.yaml");
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

    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain, "Got null domain resource");

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodName, managedServerPrefix, replicaCount);

    //print out the original image name
    String imageName = domain.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    String imageTag = getDateAndTimeStamp();
    String imageUpdate = KIND_REPO != null ? KIND_REPO
             + (WEBLOGIC_IMAGE_NAME + ":" + imageTag).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
             : WEBLOGIC_IMAGE_NAME + ":" + imageTag;

    imageTag(imageName, imageUpdate);
    imageRepoLoginAndPushImageToRegistry(imageUpdate);

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/image\",")
        .append("\"value\": \"")
        .append(imageUpdate)
        .append("\"}]");
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    assertTrue(patchDomainResource(domainUid, domainNamespace, patchStr),
        "patchDomainCustomResource(imageUpdate) failed");

    domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain, "Got null domain resource after patching");
    assertNotNull(domain.getSpec(), domain + " /spec is null");

    //print out image name in the new patched domain
    logger.info("In the new patched domain image name is: {0}", domain.getSpec().getImage());

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);
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
}
