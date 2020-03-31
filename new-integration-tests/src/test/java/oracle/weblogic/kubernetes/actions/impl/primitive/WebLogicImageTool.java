// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

// Implementation of all of the WIT primitives that the IT test needs.

public class WebLogicImageTool extends BaseInstallWIT {
    public static final String WLS = "WLS";
    public static final String JRF = "JRF";
    public static final String RJRF = "RestrictedJRF";
    public static final String WLS_BASE_IMAGE_NAME = "container-registry.oracle.com/middleware/weblogic";
    public static final String JRF_BASE_IMAGE_NAME = "container-registry.oracle.com/middleware/fmw-infrastructure";
    public static final String BASE_IMAGE_TAG = "12.2.1.4";

    public static final String MODEL_IMAGE_NAME = "model-in-image";
    public static final String MODEL_IMAGE_TAG  = "v1";

    private WITParams params;

    // use the default values
    public WebLogicImageTool with() {
      // fill in the default values!!
      return this;
    }

    public WebLogicImageTool with(WITParams params) {
      this.params = params;
      return this;
    }

    public boolean updateImage() {
      return executeAndVerify(buildCommand(), true);
    }

    private String buildCommand() {
      String command = WORK_DIR + "/imagetool/bin/imagetool.sh update "
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
