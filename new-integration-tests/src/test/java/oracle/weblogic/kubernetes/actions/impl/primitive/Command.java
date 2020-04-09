// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.IOException;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The common functionality of Installer and WebLogicImageTool
 */
public class Command {

  private CommandParams params;

  /**
   * Set up the command with given parameters
   * @return the command instance 
   */
  public static CommandParams defaultCommandParams() {
    return new CommandParams().defaults();
  }

  /**
   * Set up the command with given parameters
   * @return the command instance 
   */
  public static Command withParams(CommandParams params) {
    return new Command().params(params);
  }
  
  private Command params(CommandParams params) {
    this.params = params;
    return this;
  }

  public boolean executeAndVerify() {
    logger.info("Executing command " + params.command());
    try {
      ExecResult result = ExecCommand.exec(
          params.command(), 
          params.redirect(),
          params.env());
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
