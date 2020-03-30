// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.IOException;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

// Implementation of all of the installation primitives that the IT test needs.
// This class is a temporary solution, and may go away once we eventually
// install everything before the Java test is invoked.

public class Installer extends BaseInstallWIT {
    // temporary dir, will fix this once we decide on the
    private static final String downloadDir = WORK_DIR + "/download";
    private InstallParams params;

    public Installer with(InstallParams params) {
        this.params = params;
        return this;
    }

    public boolean download() {
        return executeAndVerify(buildDownloadCommand());
    }

    public boolean unzip(String path, String fileName, String targetDir) {
        String command = "unzip -o -d " + targetDir + path + "/" + fileName;
        return executeAndVerify(command);
    }

    private String buildDownloadCommand() {
      String url = params.getLocation() 
          + "/releases/download/" 
          + params.getVersion() 
          + "/" 
          + params.getFileName();
      return "curl -fL " + url + " -o " + downloadDir + "/" + params.getFileName();
    }
}
