// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.UniqueName;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_NAME_OPERATOR;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePull;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.backupReports;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.restoreReports;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.SampleUtils.createPVHostPathAndChangePermissionInKindCluster;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test and verify Domain on PV FMW domain sample.
 */
@DisplayName("test domain on pv sample for FMW domain")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
@Tag("kind-sequential")
@Tag("olcne-sequential")
@DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
class ItFmwDomainOnPVSample {

  private static final String domainOnPvSampleScript = "../operator/integration-tests/domain-on-pv/run-test.sh";
  private static final String DOMAIN_CREATION_IMAGE_NAME = "wdt-domain-image";
  private static final String DOMAIN_CREATION_IMAGE_JRF_TAG = "JRF-v1";
  private static String traefikNamespace = null;
  private static Map<String, String> envMap = null;
  private static LoggingFacade logger = null;

  private boolean previousTestSuccessful = true;

  /**
   * Create namespaces and set environment variables for the test.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    String domainNamespace = namespaces.get(1);

    logger.info("Creating unique namespace for Traefik");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    traefikNamespace = namespaces.get(2);

    logger.info("Creating unique namespace for db");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    String dbNamespace = namespaces.get(3);

    String domainOnPvSampleWorkDir =
        RESULTS_ROOT + "/" + domainNamespace + "/domain-on-pv-sample-work-dir";

    // env variables to override default values in sample scripts
    envMap = new HashMap<>();
    envMap.put("OPER_NAMESPACE", opNamespace);
    envMap.put("DOMAIN_NAMESPACE", domainNamespace);
    envMap.put("DB_NAMESPACE", dbNamespace);
    envMap.put("DOMAIN_UID1", getUniqueName("fmw-sample-domain-"));
    envMap.put("TRAEFIK_NAMESPACE", traefikNamespace);
    envMap.put("TRAEFIK_HTTP_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_HTTPS_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_NAME", TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3));
    envMap.put("TRAEFIK_IMAGE_REGISTRY", TRAEFIK_INGRESS_IMAGE_REGISTRY);
    envMap.put("TRAEFIK_IMAGE_REPOSITORY", TRAEFIK_INGRESS_IMAGE_NAME);
    envMap.put("TRAEFIK_IMAGE_TAG", TRAEFIK_INGRESS_IMAGE_TAG);
    envMap.put("WORKDIR", domainOnPvSampleWorkDir);
    envMap.put("BASE_IMAGE_NAME", FMWINFRA_IMAGE_TO_USE_IN_SPEC
        .substring(0, FMWINFRA_IMAGE_TO_USE_IN_SPEC.lastIndexOf(":")));
    envMap.put("BASE_IMAGE_TAG", FMWINFRA_IMAGE_TAG);
    envMap.put("DB_IMAGE_NAME", DB_IMAGE_NAME);
    envMap.put("DB_IMAGE_TAG", DB_IMAGE_TAG);
    envMap.put("IMAGE_PULL_SECRET_NAME", BASE_IMAGES_REPO_SECRET_NAME);
    envMap.put("DOMAIN_IMAGE_PULL_SECRET_NAME", TEST_IMAGES_REPO_SECRET_NAME);
    envMap.put("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
    envMap.put("OKD", "" +  OKD);
    envMap.put("KIND_CLUSTER", "" + KIND_CLUSTER);
    envMap.put("OCNE", "" + OCNE);
    envMap.put("OPER_IMAGE_NAME", TEST_IMAGES_PREFIX + IMAGE_NAME_OPERATOR);
    envMap.put("DOMAIN_CREATION_IMAGE_NAME", TEST_IMAGES_PREFIX + DOMAIN_CREATION_IMAGE_NAME);
    envMap.put("DB_IMAGE_PULL_SECRET", BASE_IMAGES_REPO_SECRET_NAME);

    // kind cluster uses openjdk which is not supported by image tool
    if (WIT_JAVA_HOME != null) {
      envMap.put("JAVA_HOME", WIT_JAVA_HOME);
    }

    if (WIT_DOWNLOAD_URL != null) {
      envMap.put("WIT_INSTALLER_URL", WIT_DOWNLOAD_URL);
    }

    if (WDT_DOWNLOAD_URL != null) {
      envMap.put("WDT_INSTALLER_URL", WDT_DOWNLOAD_URL);
    }
    logger.info("Environment variables to the script {0}", envMap);

    logger.info("Setting up image registry secrets");
    // Create the repo secret to pull the domain image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);
    logger.info("Registry secret {0} created for domain image successfully in namespace {1}",
        TEST_IMAGES_REPO_SECRET_NAME, domainNamespace);
    // Create the repo secret to pull the base image
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
    logger.info("Registry secret {0} for base image created successfully in namespace {1}",
        BASE_IMAGES_REPO_SECRET_NAME, domainNamespace);
    // create ocr/ocir image registry secret to pull the db images
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(dbNamespace);
    logger.info("Registry secret {0} created successfully in namespace {1}",
        BASE_IMAGES_REPO_SECRET_NAME, dbNamespace);
  }

  /**
   * Test Domain on PV sample install operator use case.
   */
  @Test
  @Order(1)
  public void testInstallOperator() {
    String backupReports = backupReports(UniqueName.uniqueName(this.getClass().getSimpleName()));
    execTestScriptAndAssertSuccess("-oper", "Failed to run -oper");
    restoreReports(backupReports);
  }

  /**
   * Test Domain on PV sample install Traefik use case.
   */
  @Test
  @Order(2)
  public void testInstallTraefik() {
    execTestScriptAndAssertSuccess("-traefik", "Failed to run -traefik");
  }

  /**
   * Test Domain on PV sample precleandb use case.
   */
  @Test
  @Order(3)
  public void testPrecleandb() {
    execTestScriptAndAssertSuccess("-precleandb", "Failed to run -precleandb");
  }

  /**
   * Test Domain on PV sample create db use case.
   */
  @Test
  @Order(4)
  public void testCreatedb() {
    logger.info("test case for creating a db");
    if (KIND_REPO != null) {
      String dbimage = DB_IMAGE_NAME + ":" + DB_IMAGE_TAG;
      logger.info("loading image {0} to kind", dbimage);
      imagePush(dbimage);
    }
    execTestScriptAndAssertSuccess("-db", "Failed to run -db");
  }

  /**
   * Test Domain on PV sample building image for FMW domain use case.
   */
  @Test
  @Order(5)
  public void testInitialImage() {
    logger.info("test case for building image");
    imagePull(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG);
    imageTag(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG, "busybox");
    execTestScriptAndAssertSuccess("-initial-image", "Failed to run -initial-image");
    ExecResult result = Command.withParams(
        new CommandParams()
            .command(WLSIMG_BUILDER + " images")
            .env(envMap)
            .redirect(true)
    ).executeAndReturnResult();
    logger.info(result.stdout());

    // load the image to kind if using kind cluster
    String imageCreated;
    imageCreated = TEST_IMAGES_PREFIX + DOMAIN_CREATION_IMAGE_NAME + ":" + DOMAIN_CREATION_IMAGE_JRF_TAG;
    logger.info("pushing image {0} to repo", imageCreated);
    imagePush(imageCreated);
  }

  /**
   * Test Domain on PV sample create FMW domain use case.
   */
  @Test
  @Order(6)
  public void testInitialMain() {
    logger.info("test case for creating a FMW domain");

    // load the base image to kind if using kind cluster
    if (KIND_REPO != null) {
      logger.info("loading image {0} to kind", FMWINFRA_IMAGE_TO_USE_IN_SPEC);
      imagePush(FMWINFRA_IMAGE_TO_USE_IN_SPEC);
      createPVHostPathAndChangePermissionInKindCluster("/shared", envMap);
    }

    testUntil(
        withLongRetryPolicy,
        checkTestScriptAndAssertSuccess("-initial-main", "Failed to run -initial-main"),
        logger,
        "create PV HostPath and change Permission in Kind Cluster");
  }

  /**
   * Run script run-test.sh.
   * @param arg arguments to execute script
   * @param errString a string of detailed error
   */
  private boolean execTestScriptAndAssertSuccess(String arg,
                                                 String errString) {

    Assumptions.assumeTrue(previousTestSuccessful);
    previousTestSuccessful = false;

    String command = domainOnPvSampleScript
        + " -jrf "
        + arg;

    ExecResult result = Command.withParams(
        new CommandParams()
            .command(command)
            .env(envMap)
            .redirect(true)
    ).executeAndReturnResult();

    boolean success =
        result != null
            && result.exitValue() == 0
            && result.stdout() != null
            && result.stdout().contains("Finished without errors");

    String outStr = errString;
    outStr += ", command=\n{\n" + command + "\n}\n";
    outStr += ", stderr=\n{\n" + (result != null ? result.stderr() : "") + "\n}\n";
    outStr += ", stdout=\n{\n" + (result != null ? result.stdout() : "") + "\n}\n";

    logger.info("output String is: {0}", outStr);

    previousTestSuccessful = true;

    return success;
  }

  private Callable<Boolean> checkTestScriptAndAssertSuccess(String arg, String errString) {
    return () -> execTestScriptAndAssertSuccess(arg, errString);
  }

  /**
   * Delete DB deployment for FMW test cases and Uninstall Traefik.
   */
  @AfterAll
  public static void tearDownAll() {
    logger = getLogger();

    // uninstall Traefik
    if (traefikNamespace != null) {
      logger.info("Uninstall Traefik");
      String command =
          "helm uninstall " + envMap.get("TRAEFIK_NAME") + " -n " + traefikNamespace;
      Command.withParams(new CommandParams()
          .command(command)
          .redirect(true)).execute();
    }

    // db cleanup or deletion
    logger.info("Running samples DB cleanup");
    Command.withParams(new CommandParams()
        .command(domainOnPvSampleScript + " -precleandb")
        .env(envMap)
        .redirect(true)).execute();
  }

}
