// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.List;

import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.actions.ActionConstants.IMAGE_TOOL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_ZIP_PATH;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWdtParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWitParams;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;


/**
 * Implementation of actions that use WebLogic Image Tool to create/update a WebLogic Docker image.
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
            .command(buildiWitCommand())
            .env(params.env())
            .redirect(params.redirect()))
        .execute();
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
        .download();
  }

  private String buildiWitCommand() {
    LoggingFacade logger = getLogger();
    String command =
        IMAGE_TOOL
        + " update "
        + " --tag " + params.modelImageName() + ":" + params.modelImageTag()
        + " --fromImage " + params.baseImageName() + ":" + params.baseImageTag()
        + " --wdtDomainType " + params.domainType()
        + " --chown oracle:root";

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

    logger.info("Build image with command: {0} and domainType: {1}", command,  params.domainType());
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
    String command = String.format(
        "%s cache addInstaller --type wdt --version %s --path %s",
        IMAGE_TOOL,
        params.wdtVersion(),
        WDT_ZIP_PATH);

    return Command.withParams(
            defaultCommandParams()
            .command(command)
            .env(params.env())
            .redirect(params.redirect()))
        .execute();
  }

  /**
   * Delete the WDT installer cache entry from the WebLogic Image Tool.
   * @return true if the command succeeds
   */
  public boolean deleteEntry() {
    String command = String.format("%s cache deleteEntry --key wdt_%s",
        IMAGE_TOOL,
        params.wdtVersion());

    return Command.withParams(
            defaultCommandParams()
            .command(command)
            .env(params.env())
            .redirect(params.redirect()))
        .execute();
  }
}
