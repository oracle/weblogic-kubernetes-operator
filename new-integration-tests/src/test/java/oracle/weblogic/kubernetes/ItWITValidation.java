// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;

import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.assertions.impl.WITAssertion;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.actions.TestActions.withWITParams;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Simple validation of basic WIT functions")
class ItWITValidation implements LoggedTest {
  private static final String TEST_MODEL_DIR =
      System.getProperty("user.dir") + "/src/test/resources/wdt-models/";
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String IMAGE_NAME = "test-mii-image-2";
  private static final String IMAGE_TAG = "v1";
  
  @Test
  @DisplayName("Create a MII image")
  public void testCreatingMIIImage() {

    // create the MII image
    // TODO add model files and other contents to the image once we have those resources

    logger.info("WDT model directory is " + TEST_MODEL_DIR);

    ArrayList<String> modelList = new ArrayList();
    modelList.add(TEST_MODEL_DIR + WDT_MODEL_FILE);

    boolean success = TestActions.createMIIImage(
        withWITParams()
            .modelImageName(IMAGE_NAME)
            .modelImageTag(IMAGE_TAG)
            .modelFiles(modelList)
            .wdtVersion("latest")
            .redirect(false));
 
    assertEquals(true, success, "Failed to create the image using WebLogic Deploy Tool");
    WITAssertion.imageExists(IMAGE_NAME, IMAGE_TAG);
  } 
}

