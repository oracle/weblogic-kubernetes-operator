// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;

import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.IMAGE_TOOL;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;


/**
 *  Implementation of actions that download/install tools for the uses to use.
 *  NOTE: This class is a temporary solution, and may go away once we eventually
 *  install everything before the Java test starts to run.
 */

public class Installer extends InstallWITCommon {

  private InstallParams params;

  /**
   * Set up the installer with given parameters
   * @return the installer instance 
   */
  public Installer with(InstallParams params) {
    this.params = params;
    return this;
  }

  /**
   * Download and install the tool using the params
   * @return true if the command succeeds 
   */
  public boolean download() {
    boolean downloadResult = true;
    boolean unzipResult = true;
    if (params.verify()
        && new File(DOWNLOAD_DIR, params.fileName()).exists()) {
      logger.info("File " + params.fileName() + " already exists.");
    } else {
      checkDirectory(WORK_DIR);
      downloadResult = executeAndVerify(buildDownloadCommand(), params.direct());
    }
    if (!(new File(IMAGE_TOOL).exists()) 
        && params.unzip()) {
      unzipResult = unzip();
    }
    return downloadResult && unzipResult;
  }

  private boolean unzip() {
    String command = 
        "unzip -o "
        + "-d " + WORK_DIR + " " 
        + DOWNLOAD_DIR + "/" + params.fileName();
    checkDirectory(WORK_DIR);
    return executeAndVerify(command, false);
  }

  private String buildDownloadCommand() {
    String url = 
        params.location() 
        + "/releases/download/" 
        + params.version() 
        + "/" 
        + params.fileName();
    
    return "curl -fL " 
        + url 
        + " -o " + DOWNLOAD_DIR + "/" + params.fileName();
  }
}
