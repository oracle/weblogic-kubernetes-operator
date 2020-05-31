// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.getDateAndTimeStamp;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify MII sample.
 */
@DisplayName("Test model in image sample")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiSample implements LoggedTest {

  private static final String MII_SAMPLES_WORK_DIR = WORK_DIR
      + "/model-in-image-sample-work-dir";
  private static final String MII_SAMPLES_SCRIPT =
      "../src/integration-tests/model-in-image/run-test.sh";

  private static String DOMAIN_TYPE = "WLS";
  private static String OCR_SECRET_NAME = "docker-store";
  private static String MII_SAMPLE_WLS_IMAGE_NAME1 = REPO_NAME + "mii-" + getDateAndTimeStamp();
  private static String MII_SAMPLE_WLS_IMAGE_NAME2 = REPO_NAME + "mii-" + getDateAndTimeStamp();
  private static String MII_SAMPLE_WLS_IMAGE_TAG_V1 = "WLS-v1";
  private static String MII_SAMPLE_WLS_IMAGE_TAG_V2 = "WLS-v2";
  private static String wlsImageNameV1 = MII_SAMPLE_WLS_IMAGE_NAME1 + ":" + MII_SAMPLE_WLS_IMAGE_TAG_V1;
  private static String wlsImageNameV2 = MII_SAMPLE_WLS_IMAGE_NAME2 + ":" + MII_SAMPLE_WLS_IMAGE_TAG_V2;

  private static String MII_SAMPLE_JRF_IMAGE_NAME1 = REPO_NAME + "mii-" + getDateAndTimeStamp();
  private static String MII_SAMPLE_JRF_IMAGE_NAME2 = REPO_NAME + "mii-" + getDateAndTimeStamp();
  private static String MII_SAMPLE_JRF_IMAGE_TAG_V1 = "JRF-v1";
  private static String MII_SAMPLE_JRF_IMAGE_TAG_V2 = "JRF-v2";
  private static String jrfImageNameV1 = MII_SAMPLE_JRF_IMAGE_NAME1 + ":" + MII_SAMPLE_JRF_IMAGE_TAG_V1;
  private static String jrfImageNameV2 = MII_SAMPLE_JRF_IMAGE_NAME2 + ":" + MII_SAMPLE_JRF_IMAGE_TAG_V2;
  private static String SUCCESS_SEARCH_STRING = "Finished without errors";

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static String dbNamespace = null;
  private static Map<String, String> envMap = null;
  private boolean previousTestSuccessfull = false;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    logger.info("Creating unique namespace for Treafik");
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
    envMap.put("WORKDIR", MII_SAMPLES_WORK_DIR);
    envMap.put("MODEL_IMAGE_NAME", MII_SAMPLE_WLS_IMAGE_NAME1);
    envMap.put("IMAGE_PULL_SECRET_NAME", REPO_SECRET_NAME); //ocir secret
    envMap.put("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
    envMap.put("POD_WAIT_TIMEOUT_SECS", "1000"); // JRF pod waits on slow machines, can take at least 650 seconds
    envMap.put("DB_NAMESPACE", dbNamespace);
    envMap.put("DB_IMAGE_PULL_SECRET", OCR_SECRET_NAME); //ocr secret


    logger.info("Env. variables to the script {0}", envMap);

    // kind cluster uses openjdk which is not supported by image tool
    String witJavaHome = System.getenv("WIT_JAVA_HOME");
    if (witJavaHome != null) {
      envMap.put("JAVA_HOME", witJavaHome);
    }

    // install traefik and create ingress using the mii sample script
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -traefik")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Traefik deployment is not successful");

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createDockerRegistrySecret(REPO_USERNAME, REPO_PASSWORD, REPO_EMAIL,
        REPO_REGISTRY, REPO_SECRET_NAME, domainNamespace),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));
    logger.info("Docker registry secret {0} created successfully in namespace {1}",
        REPO_SECRET_NAME, domainNamespace);
  }

  /**
   * Generate sample and verify that this matches the source
   * checked into the mii sample git location.
   */
  @Test
  @DisplayName("Test to verify MII Sample source")
  public void testCheckSampleSource() {

    boolean success = Command.withParams(new CommandParams()
                    .command(MII_SAMPLES_SCRIPT + " -check-sample")
                    .env(envMap)
                    .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Sample source doesn't match with the generated source");
  }

  /**
   * Test to verify MII sample initial use case. Build image required for the initial use case
   * and create secrets, domain resource and verifies the domain is up and running.
   */
  @Test
  @Order(1)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS initial use case")
  public void testInitialUseCase() {

    // create image
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -initial-image")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Initial image creation failed");

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(MII_SAMPLE_WLS_IMAGE_NAME1),
        String.format("Image %s does not exist", wlsImageNameV1));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(wlsImageNameV1);

    // run initial use case
    success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -initial-main")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Initial use case failed");

  }

  /**
   * Test to verify update1 use case works. Add data source to initial domain via configmap.
   */
  @Test
  @Order(2)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update1 use case")
  public void testUpdate1UseCase() {

    // run update1 use case
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update1")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Update1 use case failed");
  }

  /**
   * Test to verify update2 use case. Deploys a second domain with the same image as initial
   * WebLogic domain but with different domain UID and verifies the domain is up and running.
   */
  @Test
  @Order(3)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update2 use case")
  public void testUpdate2UseCase() {

    // run update2 use case
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update2")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Update2 use case failed");
  }

  /**
   * Test to verify update3 use case. Deploys an updated WebLogic application to the running
   * domain using an updated Docker image.
   */
  @Test
  @Order(4)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update3 use case")
  public void testUpdate3UseCase() {
    envMap.put("MODEL_IMAGE_NAME", MII_SAMPLE_WLS_IMAGE_NAME2);

    // run update3 use case
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update3-image")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Update3 create image failed");

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(MII_SAMPLE_WLS_IMAGE_NAME2),
        String.format("Image %s does not exist", wlsImageNameV2));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(wlsImageNameV2);

    success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update3-main")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Update3 use case failed");
  }

  /**
   * Test to verify MII sample JRF initial use case. Build image required for the initial use case
   * and create secrets, domain resource and verifies the domain is up and running.
   */
  @Test
  @Order(5)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF initial use case")
  public void testJrfInitialUseCase() {
    previousTestSuccessfull = false;
    envMap.put("MODEL_IMAGE_NAME", MII_SAMPLE_JRF_IMAGE_NAME1);

    // create ocr docker registry secret to pull the db images
    createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD,
        OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, dbNamespace);
    logger.info("Docker registry secret {0} created in namespace {1}",
        OCR_SECRET_NAME, dbNamespace);

    // create db and rcu
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -db -rcu")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "DB/RCU creation is not successful");

    // create image
    success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -initial-image -jrf")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "JRF Initial image creation failed");

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(MII_SAMPLE_JRF_IMAGE_NAME1),
        String.format("Image %s does not exist", jrfImageNameV1));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(jrfImageNameV1);

    // run initial use case
    success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -initial-main -jrf")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "JRF Initial use case failed");
    previousTestSuccessfull = true;
  }


  /**
   * Test to verify update1 use case works. Add data source to initial domain via configmap.
   */
  @Test
  @Order(6)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update1 use case")
  public void testJrfUpdate1UseCase() {
    Assumptions.assumeTrue(previousTestSuccessfull);
    previousTestSuccessfull = false;
    // run update1 use case
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update1 -jrf")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "JRF Update1 use case failed");
    previousTestSuccessfull = true;
  }

  /**
   * Test to verify update2 use case. Deploys a second domain with the same image as initial
   * WebLogic domain but with different domain UID and verifies the domain is up and running.
   */
  @Test
  @Order(7)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update2 use case")
  public void testJrfUpdate2UseCase() {
    Assumptions.assumeTrue(previousTestSuccessfull);
    previousTestSuccessfull = false;
    // run update2 use case
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update2 -jrf")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "JRF Update2 use case failed");
    previousTestSuccessfull = true;
  }

  /**
   * Test to verify update3 use case. Deploys an updated WebLogic application to the running
   * domain using an updated Docker image.
   */
  //@Test
  @Order(8)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update3 use case")
  public void testJrfUpdate3UseCase() {
    envMap.put("MODEL_IMAGE_NAME", MII_SAMPLE_JRF_IMAGE_NAME2);

    // run update3 use case
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update3-image -jrf")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "JRF Update3 create image failed");

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(MII_SAMPLE_JRF_IMAGE_NAME2),
        String.format("Image %s does not exist", jrfImageNameV2));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(jrfImageNameV2);

    success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update3-main -jrf")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "JRF Update3 use case failed");
  }



  /**
   * Delete images.
   */
  @AfterAll
  public void tearDownAll() {
    // delete images created for the test
    if (wlsImageNameV1 != null) {
      deleteImage(wlsImageNameV1);
    }
    if (wlsImageNameV2 != null) {
      deleteImage(wlsImageNameV2);
    }
    if (jrfImageNameV1 != null) {
      deleteImage(jrfImageNameV1);
    }
    if (jrfImageNameV2 != null) {
      deleteImage(jrfImageNameV2);
    }

    // db cleanup or deletion
    /* if (envMap != null) {
      Command.withParams(new CommandParams()
          .command(MII_SAMPLES_SCRIPT + " -precleandb")
          .env(envMap)
          .redirect(true)).execute();
    } */

    //uninstall traefik
    if (traefikNamespace != null) {
      Command.withParams(new CommandParams()
          .command("helm uninstall traefik-operator -n " + traefikNamespace)
          .redirect(true)).execute();
    }
  }

}
