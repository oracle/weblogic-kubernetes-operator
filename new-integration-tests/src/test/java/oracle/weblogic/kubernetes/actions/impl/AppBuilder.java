// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;

import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;

/**
 *  Implementation of actions that build an application archive file.
 */

public class AppBuilder {
  private static final LoggingFacade logger = LoggingFactory.getLogger(AppBuilder.class);

  private static final String ARCHIVE_SRC_DIR = ARCHIVE_DIR + "/wlsdeploy/applications";
  
  private AppParams params;

  /**
   * Create an AppParams instance with the default values.
   * @return an AppParams instance 
   */
  public static AppParams defaultAppParams() {
    return new AppParams().defaults();
  }

  /**
   * Set up the AppBuilder with given parameters.
   * @return the AppBuilder instance 
   */
  public static AppBuilder withParams(AppParams params) {
    return new AppBuilder().params(params);
  }

  private AppBuilder params(AppParams params) {
    this.params = params;
    return this;
  }

  /**
   * Build an application archive using a pre-populated AppParams instance.
   * @return true if the command succeeds 
   */
  public boolean build() {
    // prepare the archive directory and copy over the app src
    try {
      cleanupDirectory(ARCHIVE_SRC_DIR);
      checkDirectory(ARCHIVE_SRC_DIR);
      for (String item : params.srcDirList()) {
        copyFolder(
            APP_DIR + "/" + item, 
            ARCHIVE_SRC_DIR);
      }
    } catch (IOException ioe) {    
      logger.severe("Failed to get the directory " + ARCHIVE_DIR + " ready", ioe);
      return false;
    }
    
    // make sure that we always have an app name
    if (params.appName() == null) {
      params.appName(params.srcDirList().get(0));
    }

    // build the app archive 
    String jarPath = String.format("%s.ear", params.appName());
    boolean jarBuilt = buildJarArchive(jarPath, ARCHIVE_SRC_DIR);
    
    // build a zip file that can be passed to WIT
    String zipPath = String.format("%s/%s.zip", ARCHIVE_DIR, params.appName());
    boolean zipBuilt = buildZipArchive(zipPath, ARCHIVE_DIR);

    return jarBuilt && zipBuilt;
  }

  /**
   * Build an archive that includes the contents in srcDir.
   *
   * @param jarPath Jar file path for the resulting archive
   * @param srcDir source directory
   */
  private boolean buildJarArchive(
      String jarPath, 
      String srcDir
  ) {

    String cmd = String.format("cd %s; jar -cfM %s . ", srcDir, jarPath);

    return Command.withParams(
            defaultCommandParams()
            .command(cmd)
            .redirect(false))
        .execute();
  }

  /**
   * Build a zip archive that includes an ear file in the srcDir.
   *
   * @param zipPath zip file path for the resulting archive
   * @param srcDir source directory
   */
  private boolean buildZipArchive(
      String zipPath, 
      String srcDir
  ) {

    String cmd = String.format(
        "cd %s ; zip %s wlsdeploy/applications/%s.ear ", 
        srcDir, 
        zipPath,  
        params.appName());

    return Command.withParams(
        defaultCommandParams()
            .command(cmd)
            .redirect(false))
        .execute();
  }
}
