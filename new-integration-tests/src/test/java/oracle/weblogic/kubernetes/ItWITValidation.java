// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Arrays;
import java.util.List;

import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createMIIImage;
import static oracle.weblogic.kubernetes.actions.TestActions.withWITParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.assertDockerImageExists;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Simple validation of basic WIT functions")
class ItWITValidation implements LoggedTest {
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String IMAGE_NAME = "test-mii-image-2";
  private static final String IMAGE_TAG = "v1";
  
  @Test
  @DisplayName("Create a MII image")
  public void testCreatingMIIImage() {

    logger.info("WDT model directory is " + MODEL_DIR);

    // build the model file list
    List<String> modelList = Arrays.asList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // build an image using WebLogic Image Tool
    boolean success = createMIIImage(
        withWITParams()
            .modelImageName(IMAGE_NAME)
            .modelImageTag(IMAGE_TAG)
            .modelFiles(modelList)
            .wdtVersion("latest")
            .redirect(false));
 
    assertEquals(true, success, "Failed to create the image using WebLogic Deploy Tool");
    assertDockerImageExists(IMAGE_NAME, IMAGE_TAG);
  } 
}

