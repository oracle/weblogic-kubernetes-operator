// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify MII sample.
 */
@DisplayName("Test model in image sample")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiWlsAuxiliaryImageSample {

  private static final String MII_SAMPLES_WORK_DIR = RESULTS_ROOT
      + "/model-in-image-sample-work-dir";
  private static final String MII_SAMPLES_SCRIPT =
      "../operator/integration-tests/model-in-image/run-test.sh";

  private static final String CURRENT_DATE_TIME = getDateAndTimeStamp();
  private static final String MII_SAMPLE_WLS_IMAGE_NAME_V1 = DOMAIN_IMAGES_REPO + "mii-" + CURRENT_DATE_TIME + "-wlsv1";
  private static final String MII_SAMPLE_WLS_IMAGE_NAME_V2 = DOMAIN_IMAGES_REPO + "mii-" + CURRENT_DATE_TIME + "-wlsv2";
  private static final String SUCCESS_SEARCH_STRING = "Finished without errors";

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static String dbNamespace = null;
  private static Map<String, String> envMap = null;
  private static boolean previousTestSuccessful = true;
  private static LoggingFacade logger = null;
  private static String useAuxiliaryImage = "true";

  private enum DomainType { 
    JRF, 
    WLS 
  }

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    logger.info("Creating unique namespace for Traefik");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    traefikNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // env variables to override default values in sample scripts
    envMap = new HashMap<String, String>();
    envMap.put("DOMAIN_NAMESPACE", domainNamespace);
    envMap.put("TRAEFIK_NAMESPACE", traefikNamespace);
    envMap.put("TRAEFIK_HTTP_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_HTTPS_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("WORKDIR", MII_SAMPLES_WORK_DIR);
    envMap.put("BASE_IMAGE_NAME", WEBLOGIC_IMAGE_NAME);
    envMap.put("BASE_IMAGE_TAG", WEBLOGIC_IMAGE_TAG);
    envMap.put("IMAGE_PULL_SECRET_NAME", OCIR_SECRET_NAME); //ocir secret
    envMap.put("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
    envMap.put("OKD", "" +  OKD);

    // kind cluster uses openjdk which is not supported by image tool
    String witJavaHome = System.getenv("WIT_JAVA_HOME");
    if (witJavaHome != null) {
      envMap.put("JAVA_HOME", witJavaHome);
    }

    String witInstallerUrl = System.getProperty("wit.download.url");
    if (witInstallerUrl != null) {
      envMap.put("WIT_INSTALLER_URL", witInstallerUrl);
    }

    String wdtInstallerUrl = System.getProperty("wdt.download.url");
    if (wdtInstallerUrl != null) {
      envMap.put("WDT_INSTALLER_URL", wdtInstallerUrl);
    }

    logger.info("Env. variables to the script {0}", envMap);

    // install traefik using the mii sample script
    execTestScriptAndAssertSuccess("-traefik", "Traefik deployment failure");

    logger.info("Setting up docker secrets");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);
    logger.info("Docker registry secret {0} created successfully in namespace {1}",
        OCIR_SECRET_NAME, domainNamespace);
  }

  /**
   * Test to verify MII sample WLS initial use case using auxiliary image.
   * Builds image required for the initial use case, creates secrets, and
   * creates domain resource.
   * Verifies all WebLogic Server pods are ready, are at the expected
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * (response includes "Hello World!").
   */
  @Test
  @Order(1)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS initial use case using auxiliary image")
  public void testAIWlsInitialUseCase() {
    callWlsInitialUseCase();
  }

  /**
   * Test to verify WLS update1 use case using auxiliary image.
   * Adds a data source to initial domain via a configmap and updates the
   * domain resource restartVersion.
   * Verifies all WebLogic Server pods roll to ready, roll to the expected
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * and detects the new datasource (response includes
   * "mynewdatasource").
   */
  @Test
  @Order(2)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update1 use case using auxiliary image")
  public void testAIWlsUpdate1UseCase() {
    callWlsUpdate1UseCase();
  }

  /**
   * Test to verify WLS update2 use case using auxiliary image.
   * Deploys a second domain 'domain2' with a different domain UID,
   * different secrets, and different datasource config map,
   * but that is otherwise the same as the update1 domain.
   * Verifies all WebLogic Server pods are ready, have the expected
   * restartVersion, and have the expected image.
   * For each domain, verifies the sample application is running
   * (response includes "domain1" or "domain2" depending on domain).
   */
  @Test
  @Order(3)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update2 use case using auxiliary image")
  public void tesAIWlsUpdate2UseCase() {
    callWlsUpdate2UseCase();
  }

  /**
   * Test to verify update3 use case using auxiliary image.
   * Deploys an updated WebLogic application to the running
   * domain from update1 using an updated Docker image,
   * and updates the domain resource restartVersion.
   * Verifies all WebLogic Server pods roll to ready, roll to the expected
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * and is at the new version (response includes "v2").
   */
  @Test
  @Order(4)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update3 use case using auxiliary image")
  public void testAIWlsUpdate3UseCase() {
    callWlsUpdate3UseCase();
  }

  /**
   * Test to verify WLS update4 use case using auxiliary image.
   * Update Work Manager Min and Max Threads Constraints via a configmap and updates the
   * domain resource introspectVersion.
   * Verifies the sample application is running
   * and detects the updated configured count for the Min and Max Threads Constraints.
   */
  @Test
  @Order(5)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update4 use case using auxiliary image")
  public void testAIWlsUpdate4UseCase() {
    callWlsUpdate4UseCase();
  }

  /**
   * Delete DB deployment and Uninstall traefik.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall traefik
    if (traefikNamespace != null) {
      logger.info("Uninstall traefik");
      Command.withParams(new CommandParams()
          .command("helm uninstall traefik-operator -n " + traefikNamespace)
          .redirect(true)).execute();
    }
  }

  private static void assertImageExistsAndPushIfNeeded() {
    String imageName = envMap.get("MODEL_IMAGE_NAME");
    String imageVer = "notset";
    String decoration = (envMap.get("DO_AI") != null && envMap.get("DO_AI").equalsIgnoreCase("true"))  ? "AI-" : "";

    if (imageName.equals(MII_SAMPLE_WLS_IMAGE_NAME_V1)) {
      imageVer = "WLS-" + decoration + "v1";
    }
    if (imageName.equals(MII_SAMPLE_WLS_IMAGE_NAME_V2)) {
      imageVer = "WLS-" + decoration + "v2";
    }

    String image = imageName + ":" + imageVer;

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(imageName), 
               String.format("Image %s does not exist", image));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(image);
  }

  private static void execTestScriptAndAssertSuccess(String args, String errString) {
    for (String arg : args.split(",")) {
      Assumptions.assumeTrue(previousTestSuccessful);
      previousTestSuccessful = false;

      if (arg.equals("-check-image-and-push")) {
        assertImageExistsAndPushIfNeeded();
      } else {
        String command = MII_SAMPLES_SCRIPT + " " + arg;

        ExecResult result =
            Command.withParams(new CommandParams()
                .command(command)
                .env(envMap)
                .redirect(true)
            ).executeAndReturnResult();

        boolean success = result != null
            && result.exitValue() == 0
            && result.stdout() != null
            && result.stdout().contains(SUCCESS_SEARCH_STRING);

        String outStr = errString;
        outStr += ", domainType=WLS\n";
        outStr += ", command=\n{\n" + command + "\n}\n";
        outStr += ", stderr=\n{\n" + (result != null ? result.stderr() : "") + "\n}\n";
        outStr += ", stdout=\n{\n" + (result != null ? result.stdout() : "") + "\n}\n";

        assertTrue(success, outStr);
      }

      previousTestSuccessful = true;
    }
  }

  private void callWlsInitialUseCase() {
    previousTestSuccessful = true;
    envMap.put("MODEL_IMAGE_NAME", MII_SAMPLE_WLS_IMAGE_NAME_V1);
    envMap.put("DO_AI", useAuxiliaryImage);
    execTestScriptAndAssertSuccess("-initial-image,-check-image-and-push,-initial-main", "Initial use case failed");
  }

  private void callWlsUpdate1UseCase() {
    envMap.put("DO_AI", useAuxiliaryImage);
    execTestScriptAndAssertSuccess("-update1", "Update1 use case failed");
  }

  private void callWlsUpdate2UseCase() {
    envMap.put("DO_AI", useAuxiliaryImage);
    execTestScriptAndAssertSuccess("-update2", "Update2 use case failed");
  }

  private void callWlsUpdate3UseCase() {
    envMap.put("MODEL_IMAGE_NAME", MII_SAMPLE_WLS_IMAGE_NAME_V2);
    envMap.put("DO_AI", useAuxiliaryImage);
    execTestScriptAndAssertSuccess("-update3-image,-check-image-and-push,-update3-main", "Update3 use case failed");
  }

  private void callWlsUpdate4UseCase() {
    envMap.put("DO_AI", useAuxiliaryImage);
    execTestScriptAndAssertSuccess("-update4", "Update4 use case failed");
  }
}
