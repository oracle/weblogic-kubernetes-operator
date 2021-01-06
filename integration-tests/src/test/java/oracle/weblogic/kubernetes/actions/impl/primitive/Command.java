// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.IOException;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Implementation of actions that perform command execution.
 */
public class Command {

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
   * 
   * @param params instance of {@link CommandParams} that contains parameters to run a command
   * @return a command instance 
   */
  public static Command withParams(CommandParams params) {
    return new Command().params(params);
  }
  
  private Command params(CommandParams params) {
    this.params = params;
    return this;
  }

  /**
   * Execute a command.
   *
   * @return true if the execution succeeded with an exitValue of zero
   */
  public boolean execute() {
    if (params.verbose()) {
      getLogger().info("Executing command {0}", params.command());
    }
    try {
      ExecResult result = ExecCommand.exec(
          params.command(), 
          params.redirect(),
          params.env());
      if (params.saveResults()) {
        params.stdout(result.stdout());
        params.stderr(result.stderr());
      }

      // check exitValue to determine if the command execution has failed.
      if (params.verbose()) {
        if (result.exitValue() != 0) {
          getLogger().severe("The command execution failed because it returned non-zero exit value: {0}.", result);
        } else {
          getLogger().info("The command execution succeeded with result: {0}.", result);
        }
      } 

      return result.exitValue() == 0;
    } catch (IOException | InterruptedException ie) {
      getLogger().severe("The command execution failed", ie);
      return false;
    }
  }
  
  /**
   * Execute a command and verify the response.
   *
   * @param expectedResponse the expected response to verify
   * @return true if the execution succeeded and response contains the expected value
   */
  public boolean executeAndVerify(String expectedResponse) {
    if (params.verbose()) {
      getLogger().info("Executing command {0}", params.command());
    }
    try {
      ExecResult result = ExecCommand.exec(
          params.command(), 
          params.redirect(),
          params.env());
      if (params.saveResults()) {
        params.stdout(result.stdout());
        params.stderr(result.stderr());
      }

      // check exitValue to determine if the command execution has failed.
      if (params.verbose()) {
        if (result.exitValue() != 0) {
          getLogger().severe("The command execution failed because it returned non-zero exit value: {0}.", result);
        } else {
          getLogger().info("The command execution succeeded with result: {0}.", result);
        }
      } 

      return result.exitValue() == 0
             && result.stdout() != null
             && result.stdout().contains(expectedResponse);
    } catch (IOException | InterruptedException ie) {
      getLogger().severe("The command execution failed", ie);
      return false;
    }
  }

  /**
   * Execute a command and return ExecResult.
   *
   * @return ExecResult
   */
  public ExecResult executeAndReturnResult() {
    if (params.verbose()) {
      getLogger().info("Executing command {0}", params.command());
    }
    ExecResult result = null;
    try {
      result = ExecCommand.exec(
          params.command(),
          params.redirect(),
          params.env());
      if (params.saveResults()) {
        params.stdout(result.stdout());
        params.stderr(result.stderr());
      }

    } catch (IOException | InterruptedException ie) {
      getLogger().severe("The command execution failed", ie);
    }
    return result;
  }

}
