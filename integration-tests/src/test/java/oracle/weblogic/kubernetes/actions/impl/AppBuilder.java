// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.utils.FileUtils;
import oracle.weblogic.kubernetes.utils.JakartaRefactorUtil;

import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *  Implementation of actions that build an application archive file.
 */

public class AppBuilder {
  
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
   * 
   * @param params instance of {@link AppParams} that contains parameters to build an application archive
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
    String archiveSrcDir = params.appArchiveDir() + "/wlsdeploy/applications";
    // prepare the archive directory and copy over the app src
    try {
      cleanupDirectory(archiveSrcDir);
      checkDirectory(archiveSrcDir);
      for (String item : params.srcDirList()) {
        if (WEBLOGIC_IMAGE_TO_USE_IN_SPEC.contains("15.1")) {
          JakartaRefactorUtil.copyAndRefactorDirectory(Paths.get(APP_DIR, item),
              Paths.get(archiveSrcDir));
        } else {
          copyFolder(
              APP_DIR + "/" + item,
              archiveSrcDir);
        }
      }
    } catch (IOException ioe) {
      getLogger().severe("Failed to get the directory " + archiveSrcDir + " ready", ioe);
      return false;
    }

    // make sure that we always have an app name
    if (params.appName() == null) {
      params.appName(params.srcDirList().getFirst());
    }

    // build the app archive
    String jarPath = String.format("%s.ear", params.appName());
    boolean jarBuilt = buildJarArchive(jarPath, archiveSrcDir);
    
    // build a zip file that can be passed to WIT
    String zipPath = String.format("%s/%s.zip", params.appArchiveDir(), params.appName());
    boolean zipBuilt = buildZipArchive(zipPath, params.appArchiveDir());

    return jarBuilt && zipBuilt;
  }

  /**
   * Build an application archive using a pre-populated AppParams instance.
   * @return true if the command succeeds
   */
  public boolean buildCoherence() {
    // prepare the archive directory and copy over the app src
    String archiveSrcDir = params.appArchiveDir() + "/wlsdeploy/applications";
    try {
      cleanupDirectory(archiveSrcDir);
      checkDirectory(archiveSrcDir);
      for (String item : params.srcDirList()) {
        copyFolder(APP_DIR + "/" + item, archiveSrcDir);
      }
    } catch (IOException ioe) {
      getLogger().severe("Failed to get the directory " + archiveSrcDir + " ready", ioe);
      return false;
    }

    // make sure that we always have an app name
    if (params.appName() == null) {
      params.appName(params.srcDirList().getFirst());
    }

    // build the app archive
    boolean jarBuilt = false;
    if (params.appName().contains("coherence-proxy")) {
      String jarPath = String.format("%s.gar", params.appName());
      jarBuilt = buildJarArchive(jarPath, archiveSrcDir);
    } else if (params.appName().contains("CoherenceApp")) {
      String [] appTypes = {"ear", "gar"};
      try {
        for (String appType : appTypes) {
          String appSrcDir = String.format("%s/%s/u01/application/builddir/%s.%s",
              WORK_DIR, params.appName(), params.appName(), appType);
          String appArchiveSrcDir = String.format("%s/%s.%s", archiveSrcDir, params.appName(), appType);
          assertTrue(FileUtils.doesFileExist(appSrcDir), "File " + appSrcDir + " doesn't exist");
          assertTrue(FileUtils.doesDirExist(archiveSrcDir), "Dir " + archiveSrcDir + " doesn't exist");

          FileUtils.copy(Paths.get(appSrcDir), Paths.get(appArchiveSrcDir));
        }
        jarBuilt = true;
      } catch (IOException ex) {
        getLogger().severe("Failed to copy Coherence app ", ex.getMessage());
        ex.printStackTrace();
        return false;
      }
    }

    // build a zip file that can be passed to WIT
    String zipPath = String.format("%s/%s.zip", params.appArchiveDir(), params.appName());
    boolean zipBuilt = buildCoherenceZipArchive(zipPath, params.appArchiveDir());

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
  public boolean buildZipArchive(
      String zipPath, 
      String srcDir
  ) {

    // make sure that we always have an app name
    if (params.appName() == null) {
      params.appName(params.srcDirList().getFirst());
    }

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

  /**
   * Build a zip archive that includes coh-proxy-server.gar in the srcDir.
   *
   * @param zipPath zip file path for the resulting archive
   * @param srcDir source directory
   */
  public boolean buildCoherenceZipArchive(String zipPath, String srcDir) {

    // make sure that we always have an app name
    if (params.appName() == null) {
      params.appName(params.srcDirList().getFirst());
    }

    String cmd = String.format(
        "cd %s ; zip %s wlsdeploy/applications/%s.gar ",
        srcDir,
        zipPath,
        params.appName());

    if (params.appName().contains("CoherenceApp")) {
      cmd = String.format(
        "cd %s ; zip -r %s.zip wlsdeploy/applications ",
        params.appArchiveDir(),
        params.appName()
      );
    }

    return Command.withParams(
      defaultCommandParams()
        .command(cmd)
        .redirect(false))
      .execute();
  }

  /**
   * Archive an application from provided ear or war file that can be used by WebLogic Image Tool
   * to create an image with the application for a model-in-image use case.
   *
   * @return true if the operation succeeds
   */
  public boolean archiveApp() {
    List<String> srcFiles  = params.srcDirList();
    String srcFile = srcFiles.getFirst();
    String appName = srcFile.substring(srcFile.lastIndexOf("/") + 1, srcFile.lastIndexOf("."));
    params.appName(appName);
    String archiveSrcDir = params.appArchiveDir() + "/wlsdeploy/applications";

    try {
      cleanupDirectory(archiveSrcDir);
      checkDirectory(archiveSrcDir);
      for (String appSrcFile : srcFiles) {
        if (!appSrcFile.isEmpty()) {
          getLogger().info("copy {0} to {1} ", appSrcFile, archiveSrcDir);
          String fileName = appSrcFile.substring(appSrcFile.lastIndexOf("/") + 1);
          Files.copy(Paths.get(appSrcFile), Paths.get(archiveSrcDir + "/" + fileName),
                  StandardCopyOption.REPLACE_EXISTING);
        }
      }
    } catch (IOException ioe) {
      getLogger().severe("Failed to get the directory " + archiveSrcDir + " ready", ioe);
      return false;
    }

    String cmd = String.format(
            "cd %s ; zip -r %s.zip wlsdeploy/applications ",
            params.appArchiveDir(),
            appName
    );

    return Command.withParams(
            defaultCommandParams()
                    .command(cmd)
                    .redirect(false))
            .execute();
  }
}
