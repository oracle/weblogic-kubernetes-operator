// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.List;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;

import static oracle.weblogic.kubernetes.actions.ActionConstants.IMAGE_TOOL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_ZIP_PATH;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWDTParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWITParams;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkFile;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Implementation of actions that use WebLogic Image Tool to create/update a WebLogic Docker image.
 */

public class WebLogicImageTool {
  private static final LoggingFacade logger = LoggingFactory.getLogger(WebLogicImageTool.class);

  private WITParams params;

  /**
   * Set up the WITParams with the default values
   * @return the instance of WITParams
   */
  public static WITParams defaultWITParams() {
    return new WITParams().defaults();
  }

  /**
   * Set up the WebLogicImageTool with given parameters
   * @return the instance of WebLogicImageTool 
   */
  public static WebLogicImageTool withParams(WITParams params) {
    return new WebLogicImageTool().params(params);
  }
  
  private WebLogicImageTool params(WITParams params) {
    this.params = params;
    return this;
  }

  /**
   * Create an image using the params using WIT update command
   * @return true if the command succeeds 
   */
  public boolean updateImage() {
    // download WIT if it is not in the expected location 
    if (!downloadWIT()) {
      logger.warning("Failed to download or unzip WebLogic Image Tool");
      return false;
    } 
   
    // download WDT if it is not in the expected location 
    if (!downloadWDT()) {
      logger.warning("Failed to download WebLogic Deploy Tool");
      return false;
    } 

    // delete the old cache entry for the WDT installer
    if (!deleteEntry()) {
      logger.warning("Failed to delete cache entry in WebLogic Image Tool");
      return false;
    }

    // add the cache entry for the WDT installer
    if (!addInstaller()) {
      logger.warning("Failed to add installer to WebLogic Image Tool");
      return false;
    }
  
    return Command.withParams(
        defaultCommandParams()
            .command(buildCommand())
            .env(params.env())
            .redirect(params.redirect()))
        .executeAndVerify();
  }
  
  private boolean downloadWIT() {
    // install WIT if needed
    return Installer.withParams(
        defaultInstallWITParams())
        .download();
  }
  
  private boolean downloadWDT() {
    // install WDT if needed
    return Installer.withParams(
        defaultInstallWDTParams())
        .download();
  } 
  
  private String buildCommand() {
    String command = 
        IMAGE_TOOL 
        + " update "
        + " --tag " + params.modelImageName() + ":" + params.modelImageTag()
        + " --fromImage " + params.baseImageName() + ":" + params.baseImageTag()
        + " --wdtDomainType " + params.domainType()
        + " --wdtModelOnly ";
  
    if (params.modelFiles() != null && params.modelFiles().size() != 0) {
      command += " --wdtModel " + buildList(params.modelFiles());
    }
    if (params.modelVariableFiles() != null && params.modelVariableFiles().size() != 0) {
      command += " --wdtVariables " + buildList(params.modelVariableFiles());
    }
    if (params.modelArchiveFiles() != null && params.modelArchiveFiles().size() != 0) {
      command += " --wdtArchive " + buildList(params.modelArchiveFiles());
    }
  
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
   * Add WDT installer to the WebLogic Image Tool cache
   * @return true if the command succeeds 
   */
  public boolean addInstaller() {
    assertThatCode(
        () -> checkFile(WDT_ZIP_PATH))
        .as("Test if the WebLogic Deploy Tool installer exists")
        .withFailMessage("Fialed to find WebLogic Deplyo Tool installer " + WDT_ZIP_PATH)
        .doesNotThrowAnyException();
    
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
        .executeAndVerify();

  }
  
  /**
   * Delete the WDT installer cache entry from the WebLogic Image Tool
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
        .executeAndVerify();
  }
}
