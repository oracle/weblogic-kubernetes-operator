// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

// import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
// import java.io.InputStreamReader;
// import java.util.Map;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The common fuctionality of Installer and WebLogicImageTool
 */
public class InstallWITCommon {

  protected static final String WORK_DIR 
      = System.getProperty("java.io.tmpdir") + "/it-results";

  /* Use ProcessBuilder
  protected boolean executeAndVerify(String command, boolean redirectOutput) {
    ProcessBuilder builder = new ProcessBuilder();
    builder.command(command);
    logger.info("Executing command = " + command + "/ env = " + builder.environment());
    try {
      Process process = builder.start();

      // if (redirectOutput) {
      StringBuilder output = new StringBuilder();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line + "\n");
      }
      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (IOException ioe) {
      ioe.printStackTrace();
      return false;
    } catch (InterruptedException ie) {
      ie.printStackTrace();
      return false;
    }
  }
  */
  
  protected boolean executeAndVerify(String command, boolean redirectOutput) {
    logger.info("Executing command = " + command);
    try {
      checkDirectory(WORK_DIR);
      ExecResult result = ExecCommand.exec(command, redirectOutput);
      return result.exitValue() == 0;
    } catch (IOException ioe) {
      return false;
    } catch (InterruptedException ie) {
      return false;
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
