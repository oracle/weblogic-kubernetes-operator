// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.IOException;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

/**
 * Implementation of actions that perform command execution.
 */
public class Command {
  private static final LoggingFacade logger = LoggingFactory.getLogger(Command.class);

  private CommandParams params;

  /**
   * Create a CommandParams instance with the default values.
   * @return a CommandParams instance 
   */
  public static CommandParams defaultCommandParams() {
    return new CommandParams().defaults();
  }

  /**
   * Set up a command with given parameters.
   * @return a command instance 
   */
  public static Command withParams(CommandParams params) {
    return new Command().params(params);
  }
  
  private Command params(CommandParams params) {
    this.params = params;
    return this;
  }

  public boolean execute() {
    logger.info("Executing command {0}", params.command());
    try {
      ExecResult result = ExecCommand.exec(
          params.command(), 
          params.redirect(),
          params.env());
      if (result.exitValue() != 0) {
        logger.warning("The command execution failed with the result: {0}", result);
      }
      return result.exitValue() == 0;
    } catch (IOException | InterruptedException ie) {
      logger.warning("The command execution failed due to {0}", ie.getMessage());
      return false;
    }
  }
}
