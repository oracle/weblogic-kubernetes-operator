// Copyright (c) 2021, Oracle and/or its affiliates.
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
import oracle.weblogic.kubernetes.utils.ItMiiSampleHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests to verify MII sample with JRF domain using auxiliary image.
 */
@DisplayName("Test model in image sample with JRF domain using auxiliary image")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiSampleFmwAux {

  private static final String MII_SAMPLES_WORK_DIR = RESULTS_ROOT
      + "/model-in-image-sample-work-dir";
  private static final String MII_SAMPLES_SCRIPT =
      "../operator/integration-tests/model-in-image/run-test.sh";

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static String dbNamespace = null;
  private static Map<String, String> envMap = null;
  private static LoggingFacade logger = null;

  private static ItMiiSampleHelper miiSampleHelper = null;
  private static String domainType = "JRF";
  private static String imageType = "AUX";

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
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

    logger.info("Creating unique namespace for Database");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    dbNamespace = namespaces.get(3);

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
    envMap.put("dbNamespace", dbNamespace);

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

    miiSampleHelper = new ItMiiSampleHelper();
    miiSampleHelper.setEnvMap(envMap);
    miiSampleHelper.setDomainType(domainType);
    miiSampleHelper.setImageType(imageType);

    // install traefik using the mii sample script
    miiSampleHelper.execTestScriptAndAssertSuccess("-traefik", "Traefik deployment failure");

    logger.info("Setting up docker secrets");
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);
    logger.info("Docker registry secret {0} created successfully in namespace {1}",
        OCIR_SECRET_NAME, domainNamespace);

    // create ocr/ocir docker registry secret to pull the db images
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(dbNamespace);
    logger.info("Docker registry secret {0} created successfully in namespace {1}",
        BASE_IMAGES_REPO_SECRET, dbNamespace);
  }

  /**
   * Test to verify MII sample JRF initial use case using auxiliary image.
   * Deploys a database and initializes it for RCU,
   * uses an FMW infra base image instead of WLS
   * base image, and uses a WDT model that's
   * specialized for JRF, but is otherwise similar to
   * the WLS initial use case.
   * @see ItMiiSample#testWlsInitialUseCase for more...
   */
  @Test
  @Order(1)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF initial use case using auxiliary image")
  public void testAIFmwInitialUseCase() {
    miiSampleHelper.callInitialUseCase();
  }

  /**
   * Test to verify JRF update1 use case using auxiliary image.
   * @see ItMiiSample#testWlsInitialUseCase for more...
   */
  @Test
  @Order(2)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update1 use case using auxiliary image")
  public void testAIFmwUpdate1UseCase() {
    //miiSampleHelper.callFmwUpdate1UseCase();
    miiSampleHelper.callUpdate1UseCase();
  }

  /**
   * Test to verify JRF update2 use case using auxiliary image.
   * @see ItMiiSample#testWlsInitialUseCase for more...
   */
  @Test
  @Order(3)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update2 use case using auxiliary image")
  public void testAIFmwUpdate2UseCase() {
    miiSampleHelper.callUpdate2UseCase();
  }

  /**
   * Test to verify JRF update3 use case using auxiliary image.
   * @see ItMiiSample#testWlsInitialUseCase for more...
   */
  @Test
  @Order(4)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update3 use case using auxiliary image")
  public void testAIFmwUpdate3UseCase() {
    miiSampleHelper.callUpdate3UseCase();
  }

  /**
   * Test to verify JRF update4 use case using auxiliary image.
   * Update Work Manager Min and Max Threads Constraints via a configmap and updates the
   * domain resource introspectVersion.
   * Verifies the sample application is running
   * and detects the updated configured count for the Min and Max Threads Constraints.
   */
  @Test
  @Order(5)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update4 use case using auxiliary image")
  public void testAIFmwUpdate4UseCase() {
    miiSampleHelper.callUpdate4UseCase();
  }

  /**
   * Delete DB deployment and Uninstall traefik.
   */
  @AfterAll
  public void tearDownAll() {
    // db cleanup or deletion
    if (envMap != null) {
      logger.info("Running samples DB cleanup");
      Command.withParams(new CommandParams()
          .command(MII_SAMPLES_SCRIPT + " -precleandb")
          .env(envMap)
          .redirect(true)).execute();
    }

    // uninstall traefik
    if (traefikNamespace != null) {
      logger.info("Uninstall traefik");
      Command.withParams(new CommandParams()
          .command("helm uninstall traefik-operator -n " + traefikNamespace)
          .redirect(true)).execute();
    }
  }
}
