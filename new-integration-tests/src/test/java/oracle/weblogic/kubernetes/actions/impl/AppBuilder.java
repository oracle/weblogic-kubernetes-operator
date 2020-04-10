// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;

import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 *  Implementation of actions that build an application archive file.
 */

public class AppBuilder {
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
    Throwable throwable = catchThrowable(
        () -> { 
        cleanupDirectory(ARCHIVE_DIR);
        checkDirectory(ARCHIVE_SRC_DIR);
        copyFolder(
            APP_DIR + "/" + params.srcDir(), 
            ARCHIVE_SRC_DIR);
        });
        
    assertThat(throwable)
        .as(String.format(
            "Prepare archive directory %s, and copy app sources over",
            ARCHIVE_DIR))
        .withFailMessage("Failed to get the directory " + ARCHIVE_DIR + " ready")
        .isNull();

    // build the app archive 
    Exception exception = null;
    String jarPath = String.format("%s.ear", params.srcDir());
    try {
      boolean jarBuilt = buildJarArchive(jarPath, ARCHIVE_SRC_DIR);
      assertThat(jarBuilt)
          .as("Create app ear file " + jarPath)
          .withFailMessage("Failed to create the app ear file " + jarPath) 
          .isTrue();
    } catch (Exception e) {
      fail("Failed to create an ear archive file", e);
    }
    
    // build a zip file that can be passed to WIT
    String zipPath = String.format("%s/%s.zip", ARCHIVE_DIR, params.srcDir());
    exception = null;
    try {
      boolean zipBuilt = buildZipArchive(zipPath, ARCHIVE_DIR);
      assertThat(zipBuilt)
          .as("Create app zip file " + zipPath)
          .withFailMessage("Failed to create the zip file " + zipPath) 
          .isTrue();
    
    } catch (Exception e) {
      fail("Failed to create the application archive file " + zipPath, e);
    }
    
    return true;
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
  ) throws IOException {

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
  ) throws IOException, InterruptedException {

    String cmd = String.format(
        "cd %s ; zip %s wlsdeploy/applications/%s.ear ", 
        srcDir, 
        zipPath,  
        params.srcDir());

    return Command.withParams(
        defaultCommandParams()
            .command(cmd)
            .redirect(false))
        .execute();
  }
}
