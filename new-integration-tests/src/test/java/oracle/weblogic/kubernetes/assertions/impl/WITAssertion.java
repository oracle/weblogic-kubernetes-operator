// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions for the results of WebLogic Image Tool operations
 */

public class WITAssertion {

  /**
   * Check if a Docker image exists.
   * @param imageName - the name of the image to be checked
   * @param imageTag  - the tag of the image to be checked
   * @return true if the image does exist, false otherwise
   */
  public static boolean doesImageExist(String imageName, String imageTag) {
    logger.info("Checking if image " + imageName + ":" + imageTag + " exists.");
    // verify the docker image is created
    Exception exception = null;;
    try {
      ExecResult result = ExecCommand.exec(
          "docker images -q " 
          + imageName
          + ":"
          + imageTag
          + "| wc -l");
          
      if (Integer.parseInt(result.stdout().trim()) != 1) {
        return false;
      }
    } catch (Exception e) {
      exception = e;
    }
    assertThat(exception)
          .as("Check if th expected Docker image exists")
          .withFailMessage("Failed to check if Docker image " 
              + imageName 
              + ":" 
              + imageTag
              + "exists.")
          .isNull();
    return true;
  }
}
