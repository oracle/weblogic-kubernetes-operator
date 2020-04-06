// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;

import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.IMAGE_TOOL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_FILE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_FILE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.doesFileExist;


/**
 *  Implementation of actions that download/install tools for the uses to use.
 *  NOTE: This class is a temporary solution, and may go away once we eventually
 *  install everything before the Java test starts to run.
 */

public class Installer {

  private InstallParams params;
  
  /**
   * Set up the installer with default values for WDT
   * @return the InstallParams instance 
   */
  public static InstallParams defaultInstallWDTParams() {
    return new InstallParams()
        .defaults()
        .type(WDT)
        .fileName(WDT_FILE_NAME)
        .version(WDT_VERSION)
        .location(WDT_DOWNLOAD_URL)
        .verify(true)
        .unzip(false);
  } 
  
  /**
   * Set up the installer with default values for WIT
   * @return the InstallParams instance 
   */
  public static InstallParams defaultInstallWITParams() {
    return new InstallParams()
        .defaults()
        .type(WIT)
        .fileName(WIT_FILE_NAME)
        .version(WIT_VERSION)
        .location(WIT_DOWNLOAD_URL)
        .verify(true)
        .unzip(true);
  }

  /**
   * Set up the installer with given parameters
   * @return the installer instance 
   */
  public static Installer withParams(InstallParams params) {
    return new Installer().params(params);
  }
  
  private Installer params(InstallParams params) {
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
      checkDirectory(DOWNLOAD_DIR);
      downloadResult = Command.withParams(
          defaultCommandParams() 
              .command(buildDownloadCommand())
              .redirect(params.redirect()))
          .executeAndVerify();
    }
    if (params.unzip()) {
      // only unzip WIT once
      if (!(doesFileExist(IMAGE_TOOL))) { 
        unzipResult = unzip();
      }
    }
    return downloadResult && unzipResult;
  }

  private boolean unzip() {
    String command = 
        String.format("unzip -o -d %s %s/%s", WORK_DIR, DOWNLOAD_DIR, params.fileName());
    return Command.withParams(
        defaultCommandParams()  
            .command(command)
            .redirect(false))
        .executeAndVerify();
  }

  private String buildDownloadCommand() {
    String command = String.format(
        "curl -fL %s/releases/download/%s/%s -o %s/%s", 
        params.location(), 
        params.version(),
        params.fileName(),
        DOWNLOAD_DIR,
        params.fileName());
    return command;
  }
}
