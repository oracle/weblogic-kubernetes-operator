// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.utils.FileUtils;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;

import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Implementation of actions that createArchive an application archive file.
 */
public class WDTArchiveHelper {

  private AppParams params;

  /**
   * Create an AppParams instance with the default values.
   *
   * @return an AppParams instance
   */
  public static AppParams defaultAppParams() {
    return new AppParams().defaults();
  }

  /**
   * Create an AppParams instance with the custom values.
   *
   * @return an AppParams instance
   */
  public static AppParams customAppParams(List<String> srcDirList) {
    return new AppParams().srcDirList(srcDirList);
  }

  /**
   * Set up the AppBuilder with given parameters.
   *
   * @param params instance of {@link AppParams} that contains parameters to createArchive an application archive
   * @return the AppBuilder instance
   */
  public static WDTArchiveHelper withParams(AppParams params) {
    return new WDTArchiveHelper().params(params);
  }

  private WDTArchiveHelper params(AppParams params) {
    this.params = params;
    return this;
  }

  /**
   * Build an application archive using a pre-populated AppParams instance.
   *
   * @param structuredApplication true if the application is structured and exploded.
   * @return true if the command succeeds
   * @throws java.io.IOException when WDT download fails
   */
  public boolean createArchive(boolean structuredApplication) throws IOException {
    // check and install WDT
    checkAndInstallWDT();
    String archiveSrcDir = params.appArchiveDir() + "/wlsdeploy/applications";
    // prepare the archive directory and copy over the app src
    try {
      cleanupDirectory(archiveSrcDir);
      checkDirectory(archiveSrcDir);
      for (String item : params.srcDirList()) {
        copyFolder(
            APP_DIR + "/" + item,
            archiveSrcDir);
      }
    } catch (IOException ioe) {
      getLogger().severe("Failed to get the directory " + archiveSrcDir + " ready", ioe);
      return false;
    }

    // make sure that we always have an app name
    if (params.appName() == null) {
      params.appName(params.srcDirList().get(0));
    }
    boolean jarBuilt = true;
    if (!structuredApplication) {
      // createArchive the app archive
      String jarPath = String.format("%s.ear", params.appName());
      jarBuilt = buildJarArchive(jarPath, archiveSrcDir);
    }

    // createArchive a zip file that can be passed to WIT
    String zipPath = String.format("%s/%s.zip", params.appArchiveDir(), params.appName());
    boolean zipBuilt = buildZipArchive(zipPath, params.appArchiveDir());

    return jarBuilt && zipBuilt;
  }

  /**
   * Build an application archive using a pre-populated AppParams instance.
   *
   * @return true if the command succeeds
   * @throws java.io.IOException when WDT download fails
   */
  public boolean createArchive() throws IOException {
    return createArchive(false);
  }

  /**
   * Add an application archive to an existing zip archive.
   *
   * @return true of adding to an archive succeeds
   * @throws IOException when zip archive fails
   */
  public boolean addToArchive() throws IOException {
    String zipPath = String.format("%s/%s.zip", params.appArchiveDir(), params.appName());
    if (Files.exists(Path.of(zipPath))) {
      return buildZipArchive(zipPath, params.appArchiveDir());
    } else {
      return createArchive();
    }
  }

  /**
   * Build an application archive using a pre-populated AppParams instance.
   *
   * @return true if the command succeeds
   * @throws java.io.IOException when WDT download fails
   */
  public boolean createArchiveWithStructuredApplication(String archiveName) throws IOException {
    // check and install WDT
    checkAndInstallWDT();
    // make sure that we always have an app name
    if (params.appName() == null) {
      getLogger().info("Appname is not set, setting it to app src dir name");
      params.appName(params.srcDirList().get(0));
    }
    String archiveSrcDir = params.appArchiveDir()
        + "/wlsdeploy/applications/" + params.appName();
    // prepare the archive directory and copy over the app src
    try {
      cleanupDirectory(archiveSrcDir);
      checkDirectory(archiveSrcDir);
      for (String item : params.srcDirList()) {
        getLogger().info("Copying {0} to {1}", item, archiveSrcDir);
        copyFolder(
            item,
            archiveSrcDir);
      }
    } catch (IOException ioe) {
      getLogger().severe("Failed to get the directory " + archiveSrcDir + " ready", ioe);
      return false;
    }

    // createArchive a zip file that can be passed to WIT
    String zipPath = String.format("%s/%s.zip", params.appArchiveDir(), archiveName);
    String cmd = String.format(
        archiveHelperScript + " add structuredApplication"
        + " -archive_file %s"
        + " -source %s ",
        zipPath,
        archiveSrcDir);
    return Command.withParams(
        defaultCommandParams()
            .command(cmd)
            .verbose(true)
            .redirect(false))
        .execute();
  }

  /**
   * Build an application archive using a pre-populated AppParams instance.
   *
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
      params.appName(params.srcDirList().get(0));
    }

    // createArchive the app archive
    boolean jarBuilt = false;
    if (params.appName().contains("coherence-proxy")) {
      String jarPath = String.format("%s.gar", params.appName());
      jarBuilt = buildJarArchive(jarPath, archiveSrcDir);
    } else if (params.appName().contains("CoherenceApp")) {
      String[] appTypes = {"ear", "gar"};
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

    // createArchive a zip file that can be passed to WIT
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
      params.appName(params.srcDirList().get(0));
    }

    String cmd = String.format(
        "cd %s/wlsdeploy/applications; "
        + archiveHelperScript + " add application"
        + " -archive_file %s"
        + " -source %s.ear ",
        srcDir,
        zipPath,
        params.appName());

    return Command.withParams(
        defaultCommandParams()
            .command(cmd)
            .verbose(true)
            .redirect(false))
        .execute();
  }

  /**
   * Build a zip archive that includes an ear file in the srcDir.
   *
   * @param zipPath zip file path for the resulting archive
   * @param serverName server name
   * @param source source directory
   */
  public boolean addServerKeystore(
      String zipPath,
      String serverName,
      String source
  ) {

    String cmd = String.format(
        archiveHelperScript + " add serverKeystore"
        + " -archive_file %s"
        + " -server_name %s"
        + " -source %s ",
        zipPath,
        serverName,
        source);

    return Command.withParams(
        defaultCommandParams()
            .command(cmd)
            .verbose(true)
            .redirect(false))
        .execute();
  }

  /**
   * Build a zip archive that includes an ear file in the srcDir.
   *
   * @param zipPath zip file path for the resulting archive
   * @param source source directory
   */
  public boolean addCustom(
      String zipPath,
      String source
  ) {

    String cmd = String.format(
        archiveHelperScript + " add custom"
        + " -archive_file %s"
        + "-path patch"
        + "-use_non_replicable_location"
        + " -source %s ",
        zipPath,
        source);

    return Command.withParams(
        defaultCommandParams()
            .command(cmd)
            .verbose(true)
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
      params.appName(params.srcDirList().get(0));
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
   * Archive an application from provided ear or war file that can be used by WebLogic Image Tool to create an image
   * with the application for a model-in-image use case.
   *
   * @return true if the operation succeeds
   */
  public boolean archiveApp() {
    List<String> srcFiles = params.srcDirList();
    String srcFile = srcFiles.get(0);
    String appName = srcFile.substring(srcFile.lastIndexOf("/") + 1, srcFile.lastIndexOf("."));
    params.appName(appName);
    String archiveSrcDir = params.appArchiveDir() + "/wlsdeploy/applications";

    try {
      cleanupDirectory(archiveSrcDir);
      checkDirectory(archiveSrcDir);
      for (String appSrcFile : srcFiles) {
        if (appSrcFile.length() > 0) {
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

  static Path archiveHelperScript = Path.of(DOWNLOAD_DIR, "wdt", "weblogic-deploy", "bin", "archiveHelper.sh");

  private static void downloadAndInstallWDT() throws IOException {
    String wdtUrl = WDT_DOWNLOAD_URL + "/download/weblogic-deploy.zip";
    Path destLocation = Path.of(DOWNLOAD_DIR, "wdt", "weblogic-deploy.zip");
    if (!Files.exists(destLocation) && !Files.exists(archiveHelperScript)) {
      getLogger().info("Downloading WDT to {0}", destLocation);
      Files.createDirectories(destLocation.getParent());
      OracleHttpClient.downloadFile(wdtUrl, destLocation.toString(), null, null, 3);
      String cmd = "cd " + destLocation.getParent() + ";unzip " + destLocation;
      assertTrue(Command.withParams(new CommandParams().command(cmd)).execute(), "unzip command failed");
    }
    assertTrue(Files.exists(archiveHelperScript), "could not find createDomain.sh script");
  }

  /**
   * Check if WDT is installed if not download and install.
   *
   * @throws IOException when download fails
   */
  public static void checkAndInstallWDT() throws IOException {
    if (!Files.exists(archiveHelperScript)) {
      downloadAndInstallWDT();
    }
  }
}
