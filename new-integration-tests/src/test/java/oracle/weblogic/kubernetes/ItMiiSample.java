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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.MII_SAMPLES_SCRIPT;
import static oracle.weblogic.kubernetes.TestConstants.MII_SAMPLES_WORK_DIR;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.getDateAndTimeStamp;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify MII sample.
 */
@DisplayName("Test model in image sample")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiSample implements LoggedTest {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static Map<String, String> envMap = null;
  private static String DOMAIN_TYPE = "WLS";
  private static String MII_SAMPLE_IMAGE_NAME = REPO_NAME + "mii-" + getDateAndTimeStamp();
  //private static String MII_SAMPLE_IMAGE_NAME2 = REPO_NAME + "mii-" + getDateAndTimeStamp();
  private static String MII_SAMPLE_IMAGE_TAG_V1 = DOMAIN_TYPE + "-v1";
  private static String MII_SAMPLE_IMAGE_TAG_V2 = DOMAIN_TYPE + "-v2";
  private static String imageNameV1 = MII_SAMPLE_IMAGE_NAME + ":" + MII_SAMPLE_IMAGE_TAG_V1;
  private static String imageNameV2 = MII_SAMPLE_IMAGE_NAME + ":" + MII_SAMPLE_IMAGE_TAG_V2;
  private static String SUCCESS_SEARCH_STRING = "Finished without errors";

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    envMap = new HashMap<String, String>();
    envMap.put("DOMAIN_NAMESPACE", domainNamespace);
    envMap.put("WORKDIR", MII_SAMPLES_WORK_DIR);
    envMap.put("MODEL_IMAGE_NAME", MII_SAMPLE_IMAGE_NAME);
    envMap.put("MODEL_DIR", "model-images/model-in-image__" + MII_SAMPLE_IMAGE_TAG_V1); //workaround

    // install traefik and create ingress using the mii sample script
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -traefik")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Traefik deployment is not successful");
  }

  /**
   * Generate sample and verify that this matches the source
   * checked into the mii sample git location.
   */
  @Test
  @DisplayName("Test to verify MII Sample source")
  public void testCheckSampleSource() {

    String cmd = MII_SAMPLES_SCRIPT + " -check-sample";
    boolean success = Command.withParams(new CommandParams()
                    .command(cmd)
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
  @DisplayName("Test to verify MII sample initial use case")
  public void testInitialUseCase() {

    // create image
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -initial-image")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Initial image creation failed");

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(MII_SAMPLE_IMAGE_NAME),
        String.format("Image %s does not exist", imageNameV1));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(imageNameV1);

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
  public void testUpdate3UseCase() {
    envMap.put("MODEL_DIR", "model-images/model-in-image__" + MII_SAMPLE_IMAGE_TAG_V2); //workaround

    // run update3 use case
    boolean success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update3-image")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Update3 create image failed");

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(MII_SAMPLE_IMAGE_NAME),
        String.format("Image %s does not exist", imageNameV2));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(imageNameV2);

    success = Command.withParams(new CommandParams()
        .command(MII_SAMPLES_SCRIPT + " -update3-main")
        .env(envMap)
        .redirect(true)).executeAndVerify(SUCCESS_SEARCH_STRING);
    assertTrue(success, "Update3 use case failed");
  }

  /**
   * Delete images.
   */
  @AfterAll
  public void tearDownAll() {
    // delete images created for the test
    if (imageNameV1 != null) {
      deleteImage(imageNameV1);
    }
    if (imageNameV2 != null) {
      deleteImage(imageNameV2);
    }
  }


}
