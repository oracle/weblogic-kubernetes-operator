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

  protected static final String WORK_DIR 
      = System.getProperty("java.io.tmpdir") + "/it-results";
  protected static final String IMAGE_TOOL 
      = WORK_DIR + "/imagetool/bin/imagetool.sh";


  /**
   * Check if the required directoies exist.
   * Currently the directies will be created if missing. We may remove this function
   * once we have all required working directies pre-created.
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
   * Check if the required directoies exist.
   * Currently the directies will be created if missing. We may remove this function
   * once we have all required working directies pre-created.
   *
   * @param dir the directory that needs to be checked
   */
  protected void checkFile(String fileName) throws FileNotFoundException {
    File file = new File(fileName);
    if (!file.exists()) {
      logger.warning("The expected file \" + file + \" not found.");
      throw new FileNotFoundException("The expected file \" + file + \" not found.");
    }
  }
}
