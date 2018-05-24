// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.Logger;
import oracle.kubernetes.operator.BaseTest;

public class PersistentVolume {

  private String dirPath;

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public PersistentVolume(String dirPath) {
    this.dirPath = dirPath;

    String cmdResult =
        TestUtils.executeCommandStrArray(
            BaseTest.getProjectRoot()
                + "/src/integration-tests/bash/job.sh \"mkdir -p "
                + dirPath
                + "\"");
    // logger.info("job.sh result "+cmdResult);
    // check if cmd executed successfully
    if (!cmdResult.contains("Exiting with status 0")) {
      throw new RuntimeException("FAILURE: Couldn't create domain PV directory " + cmdResult);
    }
  }

  public String getDirPath() {
    return dirPath;
  }
}
