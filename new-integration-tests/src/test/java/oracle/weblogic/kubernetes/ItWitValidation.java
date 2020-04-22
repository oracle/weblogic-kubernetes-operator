// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.dockerImageExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Simple validation of basic WIT functions")
@IntegrationTest
class ItWitValidation implements LoggedTest {
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String IMAGE_NAME = "test-mii-image-2";
  private static final String IMAGE_TAG = "v1";

  private static final String APP_NAME = "sample-app";

  @Test
  @DisplayName("Create a MII image")
  public void testCreatingMiiImage() {

    logger.info("WDT model directory is {0}", MODEL_DIR);

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // build an application archive using what is in resources/apps/APP_NAME
    boolean archiveBuilt = buildAppArchive(
        defaultAppParams()
            .srcDir(APP_NAME));

    assertThat(archiveBuilt)
        .as("Create an app archive")
        .withFailMessage("Failed to create app archive for " + APP_NAME)
        .isTrue();

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME);
    List<String> archiveList = Collections.singletonList(zipFile);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // build an image using WebLogic Image Tool
    boolean success = createMiiImage(
        defaultWitParams()
            .modelImageName(IMAGE_NAME)
            .modelImageTag(IMAGE_TAG)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

    assertThat(success)
        .as("Test the Docker image creation")
        .withFailMessage("Failed to create the image using WebLogic Image Tool")
        .isTrue();

    dockerImageExists(IMAGE_NAME, IMAGE_TAG);
  }
}
