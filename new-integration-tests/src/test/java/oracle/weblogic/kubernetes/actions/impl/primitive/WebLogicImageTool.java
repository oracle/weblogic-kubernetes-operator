// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

/**
 * Implementation of actions that use WebLogic Image Tool to create or update a WebLogic Docker image.
 */

public class WebLogicImageTool extends InstallWITCommon {
  public static final String WLS = "WLS";
  public static final String JRF = "JRF";
  public static final String RJRF = "RestrictedJRF";
  public static final String WLS_BASE_IMAGE_NAME = "container-registry.oracle.com/middleware/weblogic";
  public static final String JRF_BASE_IMAGE_NAME = "container-registry.oracle.com/middleware/fmw-infrastructure";
  public static final String BASE_IMAGE_TAG = "12.2.1.4";

  public static final String MODEL_IMAGE_NAME = "test-mii-image";
  public static final String MODEL_IMAGE_TAG  = "v1";

  private WITParams params;

  /**
   * Set up the WIT with default values
   * @return the instance of WIT 
   */
  public WebLogicImageTool with() {
    return this;
  }

  /**
   * Set up the WIT with customized parameters
   * @return the instance of WIT 
   */
  public WebLogicImageTool with(WITParams params) {
    this.params = params;
    return this;
  }

  /**
   * Create an image using the params using WIT update command
   * @return true if the command succeeds 
   */
  public boolean updateImage() {
    return executeAndVerify(buildCommand(), params.isRedirect());
  }
  
  private String buildCommand() {
    String command = 
        WORK_DIR + "/imagetool/bin/imagetool.sh update "
        + " --tag " + params.getModelImageName() + ":" + params.getModelImageTag()
        + " --fromImage " + params.getBaseImageName() + ":" + params.getBaseImageTag()
        + " --wdtDomainType " + params.getDomainType()
        + " --wdtModelOnly ";
  
    if (params.getModelFiles() != null && params.getModelFiles().size() != 0) {
      command += " --wdtModel " + params.getModelFiles();
    }
    if (params.getModelVariableFiles() != null && params.getModelVariableFiles().size() != 0) {
      command += " --wdtVariables " + params.getModelVariableFiles();
    }
    if (params.getModelArchiveFiles() != null && params.getModelArchiveFiles().size() != 0) {
      command += " --wdtArchive " + params.getModelArchiveFiles();
    }
  
    return command;
  }
}
