// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;

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
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.doesFileExist;


/**
 *  Implementation of actions that download/install tools for the uses to use.
 *  NOTE: This class is a temporary solution, and may go away once we eventually
 *  install everything before the Java test starts to run.
 */

public class Installer {
  private static final LoggingFacade logger = LoggingFactory.getLogger(Installer.class);
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
        .fileName(WDT_FILE_NAME)
        .version(WDT_VERSION)
        .location(WDT_DOWNLOAD_URL)
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
        .fileName(WIT_FILE_NAME)
        .version(WIT_VERSION)
        .location(WIT_DOWNLOAD_URL)
        .verify(true)
        .unzip(true);
  }

  /**
   * Set up the installer with given parameters.
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
 
    boolean downloadSucceeded = true;
    boolean unzipSucceeded = true;
    if (params.verify()
        && new File(DOWNLOAD_DIR, params.fileName()).exists()) {
      logger.info("File {0} already exists.", params.fileName());
    } else {
      // check and make sure DOWNLOAD_DIR exists; will create it if it is missing
      checkDirectory(DOWNLOAD_DIR);
      
      // we are about to download the installer. We need to get the real version that is requested
      try {
        params.version(getActualVersionIfNeeded(params.location(), params.type(), params.version()));      
      } catch (RuntimeException re) {
        // already logged
        return false;
      }
      
      downloadSucceeded = Command.withParams(
          defaultCommandParams() 
              .command(buildDownloadCommand())
              .redirect(params.redirect()))
          .execute();
    }
    if (params.unzip()) {
      // only unzip WIT once
      if (!(doesFileExist(IMAGE_TOOL))) {
        unzipSucceeded = unzip();
      }
    }
    return downloadSucceeded && unzipSucceeded;
  }

  private boolean unzip() {
    String command = 
        String.format("unzip -o -d %s %s/%s", WORK_DIR, DOWNLOAD_DIR, params.fileName());
    return Command.withParams(
        defaultCommandParams()  
            .command(command)
            .redirect(false))
        .execute();
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

  /**
   * Figure out the actual version number of the latest release of WDT or WIT if the version
   * parameter is not specified or is specified as "latest". Otherwise return the passed in
   * version parameter itself.
   * 
   * @return the version number that is determined
   * @throws RuntimeException if the operation failed for any reason
   */
  private String getActualVersionIfNeeded(
      String location,
      String type,
      String version
  ) throws RuntimeException {
    if (version == null || version.equalsIgnoreCase("latest")) {
      String command = String.format(
          "curl -fL %s/releases/latest -o %s/%s-%s", 
          location,
          DOWNLOAD_DIR,
          type,
          TMP_FILE_NAME);
 
      CommandParams params = 
          defaultCommandParams()
              .command(command)
              .saveResults(true);
      if (!Command.withParams(params).execute()) {
        RuntimeException exception =
            new RuntimeException(String.format("Failed to get the latest %s release information.", type));
        logger.severe(
            String.format(
                "Failed to get the latest %s release information. The stderr is %s",
                type,
                params.stderr()),
            exception);
        throw exception;
      }

      command = String.format(
          "cat %s/%s-%s | grep 'releases/download' | awk '{ split($0,a,/href=\"/);%s | %s", 
          DOWNLOAD_DIR, 
          type, 
          TMP_FILE_NAME, 
          " print a[2] }'", 
          " cut -d/ -f 6"); 
    
      params = 
          defaultCommandParams()
          .command(command)
          .saveResults(true)
          .redirect(true);
 
      // the command is considered successful only if we have got back a real version number in params.stdout()
      if (Command.withParams(params).execute()
          && params.stdout() != null
          && params.stdout().length() != 0) {
        return params.stdout();
      } else {
        RuntimeException exception =
            new RuntimeException(String.format("Failed to get the version number of the requested %s release.", type));
        logger.severe(
            String.format(
                "Failed to get the version number of the requested %s release. The stderr is %s",
                type,
                params.stderr()),
            exception);
        throw exception;
      }
    }
    return version;
  }
}
