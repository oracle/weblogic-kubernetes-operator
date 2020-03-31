// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;
import java.io.IOException;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The common fuctionality of Installer and WebLogicImageTool
 */
public class BaseInstallWIT {

  protected static final String WORK_DIR 
      = System.getProperty("java.io.tmpdir") + "/it-results";

  protected boolean executeAndVerify(String command, boolean redirectOutput) {
    logger.info("Executing command = " + command);
    try {
      checkDirectory(WORK_DIR);
      ExecResult result = ExecCommand.exec(command, redirectOutput);
      verifyExitValue(result, command);
      return result.exitValue() == 0;
    } catch (IOException ioe) {
      return false;
    } catch (InterruptedException ie) {
      return false;
    }
  }

  protected void verifyExitValue(ExecResult result, String command) throws IOException {
    if (result.exitValue() != 0) {
      throw new IOException("executing the following command failed: " + command);
    }
  }
  
  // this most likely is thrown-away code. Eventually the directory will be created
  // upfront by a script before the Java test is invoked. 
  protected void checkDirectory(String dir) {
    File file = new File(dir);
    if (!file.isDirectory()) {
      file.mkdir();
      logger.info("Made a new dir " + dir);
    }
    file = new File(dir + "/download");
    if (!file.isDirectory()) {
      file.mkdir();
      logger.info("Made a new dir " + file);
    }
  }
}
