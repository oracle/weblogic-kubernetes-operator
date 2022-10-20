// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;

import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.IMAGE_TOOL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.REMOTECONSOLE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.REMOTECONSOLE_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.REMOTECONSOLE_FILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.SNAKE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.SNAKE_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLE_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getActualLocationIfNeeded;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getInstallerFileName;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.doesFileExist;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 *  Implementation of actions that download/install tools for the uses to use.
 *  NOTE: This class is a temporary solution, and may go away once we eventually
 *  install everything before the Java test starts to run.
 */

public class Installer {
  private static final String TMP_FILE_NAME = "temp-download-file.out";


  private InstallParams params;
  
  /**
   * Create an InstallParams with the default values for WDT.
   * @return an InstallParams instance 
   */
  public static InstallParams defaultInstallWdtParams() {
    return new InstallParams()
        .defaults()
        .type(WDT)
        .location(WDT_DOWNLOAD_URL)
        .verify(true)
        .unzip(false);
  }

  /**
   * Create an InstallParams with the default values for WDT.
   * @param locationURL wdt download url
   * @return an InstallParams instance
   */
  public static InstallParams installWdtParams(String locationURL) {
    return new InstallParams()
        .defaults()
        .type(WDT)
        .location(locationURL)
        .verify(true)
        .unzip(false);
  }

  /**
   * Create an InstallParams with the default values for WDT.
   * @param version wdt version
   * @param wdtDownloadURLBase wdt download url base
   * @return an InstallParams instance
   */
  public static InstallParams installWdtParams(String version, String wdtDownloadURLBase) {
    return new InstallParams()
        .defaults()
        .type(WDT)
        .location(wdtDownloadURLBase + version + "/" + WDT_DOWNLOAD_FILENAME_DEFAULT)
        .verify(true)
        .unzip(false);
  }

  /**
   * Create an InstallParams with the default values for WIT.
   * @return an InstallParams instance 
   */
  public static InstallParams defaultInstallWitParams() {
    return new InstallParams()
        .defaults()
        .type(WIT)
        .location(WIT_DOWNLOAD_URL)
        .verify(true)
        .unzip(true);
  }

  /**
   * Create an InstallParams with the default values for WLE.
   * @return an InstallParams instance
   */
  public static InstallParams defaultInstallWleParams() {
    return new InstallParams()
        .defaults()
        .type(WLE)
        .location(WLE_DOWNLOAD_URL)
        .verify(true)
        .unzip(false);
  }

  /**
   * Create an InstallParams with the default values for SnakeYAML.
   * @return an InstallParams instance
   */
  public static InstallParams defaultInstallSnakeParams() {
    return new InstallParams()
        .defaults()
        .type(SNAKE)
        .location(SNAKE_DOWNLOAD_URL)
        .verify(true)
        .unzip(false);
  }

  /**
   * Create an InstallParams with the default values for Remoteconsole.
   * @return an InstallParams instance
   */
  public static InstallParams defaultInstallRemoteconsoleParams() {
    return new InstallParams()
        .defaults()
        .type(REMOTECONSOLE)
        .location(REMOTECONSOLE_DOWNLOAD_URL)
        .verify(true)
        .unzip(true);
  }


  /**
   * Set up the installer with given parameters.
   * 
   * @param params instance of {@link InstallParams} that contains parameters to download and install a tool
   * @return an installer instance 
   */
  public static Installer withParams(InstallParams params) {
    return new Installer().params(params);
  }
  
  private Installer params(InstallParams params) {
    this.params = params;
    return this;
  }

  /**
   * Download and install the tool using the params.
   * @return true if the command succeeds 
   */
  public boolean download() {
    return download(DOWNLOAD_DIR);
  }

  /**
   * Download and install the tool using the params.
   * @param downloadDir download directory
   * @return true if the command succeeds
   */
  public boolean download(String downloadDir) {

    boolean downloadSucceeded = true;
    boolean unzipSucceeded = true;
    if (params.verify()
        && new File(downloadDir, getInstallerFileName(params.type())).exists()) {
      getLogger().fine("File {0} already exists.", getInstallerFileName(params.type()));
    } else {
      // check and make sure downloadDir exists; will create it if it is missing
      checkDirectory(downloadDir);

      // we are about to download the installer. We need to get the real version that is requested
      try {
        params.location(getActualLocationIfNeeded(params.location(), params.type()));
      } catch (RuntimeException re) {
        // already logged
        return false;
      }

      downloadSucceeded = Command.withParams(
          defaultCommandParams()
              .command(buildDownloadCommand(downloadDir))
              .redirect(params.redirect()))
          .execute();
    }
    if (params.unzip()) {
      // only unzip WIT once
      if (!(doesFileExist(IMAGE_TOOL)) || !(doesFileExist(REMOTECONSOLE_FILE))) {
        unzipSucceeded = unzip(downloadDir);
      }
    }
    return downloadSucceeded && unzipSucceeded;
  }

  private boolean unzip(String downloadDir) {
    String command = String.format(
        "unzip -o -d %s %s/%s", 
        WORK_DIR,
        downloadDir,
        getInstallerFileName(params.type()));

    return Command.withParams(
        defaultCommandParams()  
            .command(command)
            .redirect(false))
        .execute();
  }

  private String buildDownloadCommand(String downloadDir) {
    String command = String.format(
        "curl -fL %s -o %s/%s",
        params.location(),
        downloadDir,
        getInstallerFileName(params.type()));
    return command;
  }

}
