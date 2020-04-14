// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.weblogic.kubernetes.extensions.IntegrationTestWatcher;

import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createMIIImage;
import static oracle.weblogic.kubernetes.actions.TestActions.withWITParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.dockerImageExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Simple validation of basic WIT functions")
@ExtendWith(IntegrationTestWatcher.class)
class ItWITValidation implements LoggedTest {
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String IMAGE_NAME = "test-mii-image-2";
  private static final String IMAGE_TAG = "v1";

  @Test
  @DisplayName("Create a MII image")
  public void testCreatingMIIImage() {

    logger.info("WDT model directory is {0}", MODEL_DIR);

    // build the model file list
    List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // build an image using WebLogic Image Tool
    boolean success = createMIIImage(
        withWITParams()
            .modelImageName(IMAGE_NAME)
            .modelImageTag(IMAGE_TAG)
            .modelFiles(modelList)
            .wdtVersion("latest")
            .env(env)
            .redirect(true));

    assertEquals(true, success, "Failed to create the image using WebLogic Deploy Tool");
    dockerImageExists(IMAGE_NAME, IMAGE_TAG);
  }
}

