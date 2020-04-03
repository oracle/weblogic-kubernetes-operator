// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;
import java.io.FileNotFoundException;

import oracle.weblogic.kubernetes.actions.impl.ActionImplCommon;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The common functionality of Installer and WebLogicImageTool
 */
public class InstallWITCommon extends ActionImplCommon {

  /**
   * Check if the required directories exist.
   * Currently the directories will be created if missing. We may remove this function
   * once we have all required working directives pre-created.
   *
   * @param dir the directory that needs to be checked
   */
  protected void checkDirectory(String dir) {
    File file = new File(dir);
    if (!file.isDirectory()) {
      file.mkdir();
      logger.info("Made a new dir " + dir);
    }
    file = new File(dir + "/download");
    if (!file.isDirectory()) {
      file.mkdir();
      logger.info("Made a new dir " + file);
    }
  }

  /**
   * Check if the required directories exist.
   * Currently the directories will be created if missing. We may remove this function
   * once we have all required working directories pre-created.
   *
   * @param dir the directory that needs to be checked
   */
  protected void checkFile(String fileName) throws FileNotFoundException {
    File file = new File(fileName);
    if (!file.exists()) {
      logger.warning("The expected file " + fileName + " not found.");
      throw new FileNotFoundException("The expected file " + fileName + " not found.");
    }
  }
}
