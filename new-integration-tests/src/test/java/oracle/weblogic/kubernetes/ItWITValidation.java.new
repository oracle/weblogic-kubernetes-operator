// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Collections;
import java.util.List;

import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createMIIImage;
import static oracle.weblogic.kubernetes.actions.TestActions.withAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.withWITParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.dockerImageExists;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Simple validation of basic WIT functions")
class ItWITValidation implements LoggedTest {
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String IMAGE_NAME = "test-mii-image-2";
  private static final String IMAGE_TAG = "v1";
  
  // Name of the directory under resources/apps for an application
  // Note: use "_" instead of "-" in app directories names because
  // WDT does not like "-" to be in the name of an archive file
  private static final String APP_NAME = "sample_app";
  
  @Test
  @DisplayName("Create a MII image")
  public void testCreatingMIIImage() {

    logger.info("WDT model directory is " + MODEL_DIR);

    // build the model file list
    List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);
    
    // build an application archive using the src in APP_NAME directory
    boolean archiveBuilt = buildAppArchive(
        withAppParams()
            .srcDir(APP_NAME));
    
    assertThat(archiveBuilt)
        .as("Create an app archive")
        .withFailMessage("Failed to create the app archive")
        .isTrue();
    
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME);
    
    List<String> archiveList = Collections.singletonList(zipFile);
    
    // build an image using WebLogic Image Tool
    boolean success = createMIIImage(
        withWITParams()
            .modelImageName(IMAGE_NAME)
            .modelImageTag(IMAGE_TAG)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion("latest")
            .redirect(false));
 
    assertThat(success)
        .as("Test the Docker image is created")
        .withFailMessage("Failed to create the image using WebLogic Deploy Tool")
        .isTrue();
    
    dockerImageExists(IMAGE_NAME, IMAGE_TAG);
  } 
}

