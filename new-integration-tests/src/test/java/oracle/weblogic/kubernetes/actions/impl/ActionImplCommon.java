// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The common functionality of Installer and WebLogicImageTool
 */
public class ActionImplCommon {

  protected boolean executeAndVerify(String command) {
    return executeAndVerify(command, false);
  }

  protected boolean executeAndVerify(String command, boolean redirectOutput) {
    logger.info("Executing command " + command);
    try {
      ExecResult result = ExecCommand.exec(command, redirectOutput);
      return result.exitValue() == 0;
    } catch (IOException ioe) {
      logger.warning("Failed too run the command due to " + ioe.getMessage());
      return false;
    } catch (InterruptedException ie) {
      logger.warning("Failed too run the command due to " + ie.getMessage());
      return false;
    }
  }
}
