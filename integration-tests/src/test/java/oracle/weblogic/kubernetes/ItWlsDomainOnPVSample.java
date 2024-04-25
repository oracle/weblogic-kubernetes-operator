// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_NAME_OPERATOR;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_NAMESPACE;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePull;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.backupReports;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.restoreReports;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.SampleUtils.createPVHostPathAndChangePermissionInKindCluster;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test and verify Domain on PV sample.
 */
@DisplayName("test domain on pv sample")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
@Tag("kind-sequential")
@Tag("olcne-sequential")
@DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
class ItWlsDomainOnPVSample {

  private static final String domainOnPvSampleScript = "../operator/integration-tests/domain-on-pv/run-test.sh";
  private static final String DOMAIN_CREATION_IMAGE_WLS_TAG = "WLS-v1";
  private static String traefikNamespace = null;
  private static Map<String, String> envMap = null;
  private static LoggingFacade logger = null;

  private boolean previousTestSuccessful = true;
  private static String DOMAIN_CREATION_IMAGE_NAME = "wdt-domain-image";

  /**
   * Create namespaces and set environment variables for the test.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
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

    String domainOnPvSampleWorkDir =
        RESULTS_ROOT + "/" + domainNamespace + "/domain-on-pv-sample-work-dir";

    // env variables to override default values in sample scripts
    envMap = new HashMap<>();
    envMap.put("OPER_NAMESPACE", opNamespace);
    envMap.put("DOMAIN_NAMESPACE", domainNamespace);
    envMap.put("DOMAIN_UID1", getUniqueName("sample-domain-"));
    envMap.put("TRAEFIK_NAMESPACE", traefikNamespace);
    envMap.put("TRAEFIK_HTTP_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_HTTPS_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_NAME", TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3));
    envMap.put("TRAEFIK_IMAGE_REGISTRY", TRAEFIK_INGRESS_IMAGE_REGISTRY);
    envMap.put("TRAEFIK_IMAGE_REPOSITORY", TRAEFIK_INGRESS_IMAGE_NAME);
    envMap.put("TRAEFIK_IMAGE_TAG", TRAEFIK_INGRESS_IMAGE_TAG);
    envMap.put("WORKDIR", domainOnPvSampleWorkDir);
    envMap.put("BASE_IMAGE_NAME", WEBLOGIC_IMAGE_TO_USE_IN_SPEC
        .substring(0, WEBLOGIC_IMAGE_TO_USE_IN_SPEC.lastIndexOf(":")));
    envMap.put("BASE_IMAGE_TAG", WEBLOGIC_IMAGE_TAG);
    envMap.put("IMAGE_PULL_SECRET_NAME", BASE_IMAGES_REPO_SECRET_NAME);
    envMap.put("DOMAIN_IMAGE_PULL_SECRET_NAME", TEST_IMAGES_REPO_SECRET_NAME);
    envMap.put("WLSIMG_BUILDER_DEFAULT", WLSIMG_BUILDER_DEFAULT);
    envMap.put("WLSIMG_BUILDER", WLSIMG_BUILDER);
    envMap.put("OKD", "" +  OKD);
    envMap.put("KIND_CLUSTER", "" + KIND_CLUSTER);
    envMap.put("OCNE", "" + OCNE);

    if (OCNE) {
      envMap.put("OPER_IMAGE_NAME", TEST_IMAGES_PREFIX + IMAGE_NAME_OPERATOR);
      envMap.put("DOMAIN_CREATION_IMAGE_NAME", TEST_IMAGES_PREFIX + DOMAIN_CREATION_IMAGE_NAME);
    }

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

    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      DOMAIN_CREATION_IMAGE_NAME = "localhost/wdt-domain-image";
      envMap.put("OPER_IMAGE_NAME", "localhost/weblogic-kubernetes-operator");
      envMap.put("DOMAIN_CREATION_IMAGE_NAME", DOMAIN_CREATION_IMAGE_NAME);
      envMap.put("K8S_NODEPORT_HOST", assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostAddress()));
      envMap.put("TRAEFIK_INGRESS_HTTP_HOSTPORT", "" + TRAEFIK_INGRESS_HTTP_HOSTPORT);
      envMap.put("TRAEFIK_NAMESPACE", TRAEFIK_NAMESPACE);
    } else {
      envMap.put("TRAEFIK_NAMESPACE", traefikNamespace);
      envMap.put("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
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
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      logger.info("skip installing  Traefik in KIND and podman environment");
      logger.info("Traefik is already installed in InitialTask in namespace %s", TRAEFIK_NAMESPACE);
    } else {
      execTestScriptAndAssertSuccess("-traefik", "Failed to run -traefik");
    }
  }

  /**
   * Test Domain on PV sample building image use case.
   */
  @Test
  @Order(3)
  public void testInitialImage() {
    imagePull(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG);
    imageTag(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG, "busybox");
    execTestScriptAndAssertSuccess("-initial-image", "Failed to run -initial-image");

    // load the image to kind if using kind cluster
    String imageCreated;
    if (KIND_REPO != null) {
      imageCreated = DOMAIN_CREATION_IMAGE_NAME + ":" + DOMAIN_CREATION_IMAGE_WLS_TAG;
      logger.info("loading image {0} to kind", imageCreated);
      imagePush(imageCreated);
    } else if (OCNE) {
      imageCreated = TEST_IMAGES_PREFIX + DOMAIN_CREATION_IMAGE_NAME + ":" + DOMAIN_CREATION_IMAGE_WLS_TAG;
      logger.info("pushing image {0} to repo", imageCreated);
      imagePush(imageCreated);
    }
  }

  /**
   * Test Domain on PV sample create domain use case.
   */
  @Test
  @Order(4)
  public void testInitialMain() {
    // load the base image to kind if using kind cluster
    if (KIND_REPO != null) {
      logger.info("loading image {0} to kind", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
      imagePush(WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
      createPVHostPathAndChangePermissionInKindCluster("/shared", envMap);
    }

    execTestScriptAndAssertSuccess("-initial-main", "Failed to run -initial-main");
  }

  /**
   * Run script run-test.sh.
   * @param arg arguments to execute script
   * @param errString a string of detailed error
   */
  private void execTestScriptAndAssertSuccess(String arg,
                                              String errString) {

    Assumptions.assumeTrue(previousTestSuccessful);
    previousTestSuccessful = false;

    String command = domainOnPvSampleScript
        + " "
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

    assertTrue(success, outStr);

    previousTestSuccessful = true;
  }

  /**
   * Uninstall Traefik.
   */
  @AfterAll
  public static void tearDownAll() {
    logger = getLogger();
    // uninstall traefik
    if (traefikNamespace != null) {
      logger.info("Uninstall Traefik");
      String command = "helm uninstall " + envMap.get("TRAEFIK_NAME") + " -n " + traefikNamespace;
      Command.withParams(new CommandParams()
          .command(command)
          .redirect(true)).execute();
    }

  }
}
