// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.Logger;
import oracle.kubernetes.operator.BaseTest;

public class PersistentVolume {

  private String dirPath;

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public PersistentVolume(String dirPath) throws Exception {
    this.dirPath = dirPath;
    String cmd =
        BaseTest.getProjectRoot()
            + "/src/integration-tests/bash/job.sh \"mkdir -p "
            + dirPath
            + "\"";
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create domain PV directory "
              + cmd
              + " failed, returned "
              + result.stderr());
    }
    logger.info("command result " + result.stdout().trim());
  }

  public String getDirPath() {
    return dirPath;
  }
}
