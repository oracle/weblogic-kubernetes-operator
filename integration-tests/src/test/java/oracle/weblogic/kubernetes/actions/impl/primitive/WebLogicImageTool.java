// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.List;
import java.util.Objects;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.ARM;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.YAML_MAX_FILE_SIZE_PROPERTY;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.IMAGE_TOOL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL_BASE;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWdtParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWitParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.installWdtParams;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;


/**
 * Implementation of actions that use WebLogic Image Tool to create/update a WebLogic image.
 */

public class WebLogicImageTool {

  private WitParams params;

  /**
   * Create a WITParams with the default values.
   * @return a WITParams instance
   */
  public static WitParams defaultWitParams() {
    return new WitParams().defaults();
  }

  /**
   * Set up the WebLogicImageTool with given parameters.
   *
   * @param params instance of {@link WitParams} that contains parameters to run WebLogic Image Tool
   * @return the instance of WebLogicImageTool
   */
  public static WebLogicImageTool withParams(WitParams params) {
    return new WebLogicImageTool().params(params);
  }

  private WebLogicImageTool params(WitParams params) {
    this.params = params;
    return this;
  }

  /**
   * Create an image using the params using WIT update command.
   * @return true if the command succeeds
   */
  public boolean updateImage() {
    // download WIT if it is not in the expected location
    if (!downloadWit()) {
      return false;
    }

    // download WDT if it is not in the expected location
    if (!downloadWdt()) {
      return false;
    }

    // delete the old cache entry for the WDT installer
    if (!deleteEntry()) {
      return false;
    }

    // add the WDT installer that we just downloaded into WIT cache entry
    if (!addInstaller()) {
      return false;
    }

    return Command.withParams(
        defaultCommandParams()
            .command(buildWitCommand())
            .env(params.env())
            .redirect(params.redirect()))
        .execute();
  }

  /**
   * Inspect an image using the params using WIT inspect command.
   * @return inspection output string if the command succeeds otherwise null
   */
  public String inspectImage(String imageName, String imageTag) {
    String output = null;
    // download WIT if it is not in the expected location
    testUntil(
        withStandardRetryPolicy,
        () -> downloadWit(),
        getLogger(),
        "downloading WIT succeeds");

    // download WDT if it is not in the expected location
    testUntil(
        withStandardRetryPolicy,
        () -> downloadWdt(),
        getLogger(),
        "downloading WDT succeeds");

    // delete the old cache entry for the WDT installer
    testUntil(
        withStandardRetryPolicy,
        () -> deleteEntry(),
        getLogger(),
        "deleting cache entry for WDT installer succeeds");

    // add the WDT installer that we just downloaded into WIT cache entry
    testUntil(
        withStandardRetryPolicy,
        () -> addInstaller(),
        getLogger(),
        "adding WDT installer to the cache succeeds");

    ExecResult result = Command.withParams(
            defaultCommandParams()
                    .command(buildInspectWitCommand(imageName,
                            imageTag))
                    .redirect(params.redirect()))
            .executeAndReturnResult();

    // check exitValue to determine if the command execution has failed.
    if (result.exitValue() != 0) {
      getLogger().severe("The command execution failed because it returned non-zero exit value: {0}.", result);
      output = result.stderr();
    } else {
      getLogger().info("The command execution succeeded with result: {0}.", result);
      output = result.stdout();
    }
    return output;
  }

  private boolean downloadWit() {
    // install WIT if needed
    return Installer.withParams(
        defaultInstallWitParams())
        .download();
  }

  private boolean downloadWdt() {
    // install WDT if needed
    return Installer.withParams(
        defaultInstallWdtParams())
        .download(DOWNLOAD_DIR + "/latest");
  }

  private boolean downloadWdt(String version) {
    // install WDT with specific version
    return Installer.withParams(
        installWdtParams(version, WDT_DOWNLOAD_URL_BASE))
        .download(DOWNLOAD_DIR + "/" + version);
  }

  private String buildWitCommand() {
    LoggingFacade logger = getLogger();
    String ownership = " --chown oracle:root";
    if (OKE_CLUSTER) {
      if (params.baseImageName().equals(FMWINFRA_IMAGE_NAME)) {
        String output = inspectImage(params.baseImageName(), params.baseImageTag());
        logger.info("Inspect image result ");
        if (output != null && !output.contains("root")) {
          ownership = " --chown oracle:oracle";
        }
      }
    }
    String command =
        IMAGE_TOOL
        + " update "
        + " --tag " + params.modelImageName() + ":" + params.modelImageTag()
        + " --fromImage " + params.baseImageName() + ":" + params.baseImageTag()
        + " --wdtDomainType " + params.domainType()
        + " --wdtJavaOptions " + YAML_MAX_FILE_SIZE_PROPERTY
        + ownership;

    if (ARM) {
      command += " --platform linux/arm64";
    }

    if (params.wdtModelOnly()) {
      command += " --wdtModelOnly ";
    }

    if (params.wdtModelHome() != null) {
      command += " --wdtModelHome " + params.wdtModelHome();
    }

    if (params.modelFiles() != null && !params.modelFiles().isEmpty()) {
      command += " --wdtModel " + buildList(params.modelFiles());
    }
    if (params.modelVariableFiles() != null && !params.modelVariableFiles().isEmpty()) {
      command += " --wdtVariables " + buildList(params.modelVariableFiles());
    }
    if (params.modelArchiveFiles() != null && !params.modelArchiveFiles().isEmpty()) {
      command += " --wdtArchive " + buildList(params.modelArchiveFiles());
    }

    if (params.domainHome() != null) {
      command += " --wdtDomainHome " + params.domainHome();
    }

    if (params.wdtOperation() != null) {
      command += " --wdtOperation " + params.wdtOperation();
    }

    if (params.additionalBuildCommands() != null) {
      command += " --additionalBuildCommands " + params.additionalBuildCommands();
    }

    if (params.additionalBuildFiles() != null) {
      command += " --additionalBuildFiles " + params.additionalBuildFiles();
    }

    if (params.target() != null) {
      command += " --target " + params.target();
    }

    logger.info("Build image with command: {0} and domainType: {1}", command,  params.domainType());
    return command;
  }

  private String buildInspectWitCommand(String imageName, String imageTag) {
    LoggingFacade logger = getLogger();
    String command =
            IMAGE_TOOL
                    + " inspect "
                    + " -i " + imageName + ":" + imageTag;

    logger.info("Inspect image {0} with command: {1}",
            imageName + ":" + imageTag,
            command);
    return command;
  }

  private String buildList(List<String> list) {
    StringBuilder sbString = new StringBuilder("");

    //iterate through ArrayList
    for (String item : list) {
      //append ArrayList element followed by comma
      sbString.append(item).append(",");
    }

    //convert StringBuffer to String
    String strList = sbString.toString();

    //remove last comma from String if you want
    if (strList.length() > 0) {
      strList = strList.substring(0, strList.length() - 1);
    }
    return strList;
  }

  /**
   * Add WDT installer to the WebLogic Image Tool cache.
   * @return true if the command succeeds
   */
  public boolean addInstaller() {
    String wdtVersion = (params.wdtVersion() != null) ? params.wdtVersion() : "latest";
    if (!wdtVersion.equals("NONE")) {
      String command = String.format(
          "%s cache addInstaller --type wdt --version %s --path %s",
          IMAGE_TOOL,
          wdtVersion,
          DOWNLOAD_DIR + "/" + wdtVersion + "/" + WDT_DOWNLOAD_FILENAME_DEFAULT);

      return Command.withParams(
          defaultCommandParams()
              .command(command)
              .env(params.env())
              .redirect(params.redirect()))
          .execute();
    } else {
      return true;
    }

  }

  /**
   * Delete the WDT installer cache entry from the WebLogic Image Tool.
   * @return true if the command succeeds
   */
  public boolean deleteEntry() {
    String wdtVersion = (params.wdtVersion() != null) ? params.wdtVersion() : "latest";
    if (!wdtVersion.equals("NONE")) {
      String command = String.format("%s cache deleteEntry --key wdt_%s",
          IMAGE_TOOL,
          params.wdtVersion());

      return Command.withParams(
          defaultCommandParams()
              .command(command)
              .env(params.env())
              .redirect(params.redirect()))
          .execute();
    } else {
      return true;
    }
  }

  /**
   * Create an Auxiliary image using WIT command.
   * @return true if the command succeeds
   */
  public boolean createAuxImage() {
    // download WIT if it is not in the expected location
    if (!downloadWit()) {
      return false;
    }

    // download WDT if it is not in the expected location
    if (params.wdtVersion() != null && !Objects.equals(params.wdtVersion(), "NONE")) {
      if (!downloadWdt(params.wdtVersion())) {
        return false;
      }
    } else {
      if (!downloadWdt()) {
        return false;
      }
    }

    // delete the old cache entry for the WDT installer
    if (!deleteEntry()) {
      return false;
    }

    // add the WDT installer that we just downloaded into WIT cache entry
    if (!addInstaller()) {
      return false;
    }

    return Command.withParams(
        defaultCommandParams()
            .command(buildCreateAuxImageCommand())
            .env(params.env())
            .redirect(params.redirect()))
        .execute();
  }

  /**
   * Create an Auxiliary image using WIT command and return result output.
   * @return true if the command succeeds
   */
  public ExecResult createAuxImageAndReturnResult() {
    // download WIT if it is not in the expected location
    testUntil(
        withStandardRetryPolicy,
        () -> downloadWit(),
        getLogger(),
        "downloading WIT succeeds");

    // download WDT if it is not in the expected location
    if (params.wdtVersion() != null && !Objects.equals(params.wdtVersion(), "NONE")) {
      testUntil(
          withStandardRetryPolicy,
          () -> downloadWdt(params.wdtVersion()),
          getLogger(),
          "downloading WDT with version {0} succeeds",
          params.wdtVersion());
    } else {
      testUntil(
          withStandardRetryPolicy,
          () -> downloadWdt(),
          getLogger(),
          "downloading latest WDT succeeds");
    }

    // delete the old cache entry for the WDT installer
    testUntil(
        withStandardRetryPolicy,
        () -> deleteEntry(),
        getLogger(),
        "deleting cache entry for WDT installer succeeds");

    // add the WDT installer that we just downloaded into WIT cache entry
    testUntil(
        withStandardRetryPolicy,
        () -> addInstaller(),
        getLogger(),
        "adding WDT installer to the cache succeeds");

    return Command.withParams(
            defaultCommandParams()
              .command(buildCreateAuxImageCommand())
              .env(params.env())
              .redirect(params.redirect()))
          .executeAndReturnResult();
  }

  private String buildCreateAuxImageCommand() {
    LoggingFacade logger = getLogger();
    String command =
        IMAGE_TOOL
            + " createAuxImage "
            + " --tag " + params.modelImageName() + ":" + params.modelImageTag();

    if (params.builder() != null) {
      command += " --builder " + params.builder();
    }

    if (params.buildNetwork() != null) {
      command += " --buildNetwork " + params.buildNetwork();
    }

    if (params.dryRun()) {
      command += " --dryRun ";
    }

    if (params.baseImageName() != null && params.baseImageTag() != null) {
      command += " --fromImage " + params.baseImageName() + ":" + params.baseImageTag();
    } else {
      command += " --fromImage " + BUSYBOX_IMAGE + ":" + BUSYBOX_TAG;
    }

    if (params.useridGroupid() != null) {
      command += " --chown " + params.useridGroupid();
    }

    if (params.httpProxyUrl() != null) {
      command += " --httpProxyUrl " + params.httpProxyUrl();
    }

    if (params.httpsProxyUrl() != null) {
      command += " --httpsProxyUrl " + params.httpsProxyUrl();
    }

    if (params.packageManager() != null) {
      command += " --packageManager " + params.packageManager();
    }

    if (params.pull()) {
      command += " --pull ";
    }

    if (params.skipCleanup()) {
      command += " --skipcleanup ";
    }

    if (params.wdtHome() != null) {
      command += " --wdtHome " + params.wdtHome();
    }

    if (params.wdtModelHome() != null) {
      command += " --wdtModelHome " + params.wdtModelHome();
    }

    if (params.modelFiles() != null && !params.modelFiles().isEmpty()) {
      command += " --wdtModel " + buildList(params.modelFiles());
    }

    if (params.modelVariableFiles() != null && !params.modelVariableFiles().isEmpty()) {
      command += " --wdtVariables " + buildList(params.modelVariableFiles());
    }

    if (params.modelArchiveFiles() != null && !params.modelArchiveFiles().isEmpty()) {
      command += " --wdtArchive " + buildList(params.modelArchiveFiles());
    }

    if (params.wdtVersion() != null) {
      command += " --wdtVersion " + params.wdtVersion();
    }

    if (params.target() != null) {
      command += " --target " + params.target();
    }

    if (params.additionalBuildCommands() != null) {
      command += " --additionalBuildCommands " + params.additionalBuildCommands();
    }

    if (params.additionalBuildFiles() != null) {
      command += " --additionalBuildFiles " + params.additionalBuildFiles();
    }

    logger.info("Build auxiliary image with command: {0}", command);
    return command;
  }
}
