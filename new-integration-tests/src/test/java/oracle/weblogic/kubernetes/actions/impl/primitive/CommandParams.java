// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.Map;

/**
 * Contains the parameters for executing a command.
 */

public class CommandParams {

  // The command to execute
  private String command;
 
  // The env variables that are needed for running a command
  private Map<String, String> env;

  // Whether the output of the command is redirected to system out
  private boolean redirect = true;
  
  // Whether the stdout of the command needs to be saved
  private boolean saveStdOut = false;

  // The stdout of the command run 
  private String stdOut;
  
  public CommandParams defaults() {
    return this;
  }

  public CommandParams command(String command) {
    this.command = command;
    return this;
  }

  public String command() {
    return command;
  }
  
  public CommandParams env(Map<String, String> env) {
    this.env = env;
    return this;
  }

  public Map<String, String> env() {
    return env;
  }

  public CommandParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean redirect() {
    return redirect;
  }
  
  public CommandParams saveStdOut(boolean saveStdOut) {
    this.saveStdOut = saveStdOut;
    return this;
  }

  public boolean saveStdOut() {
    return saveStdOut;
  }
 
  public CommandParams stdOut(String stdOut) {
    this.stdOut = stdOut;
    return this;
  }

  public String stdOut() {
    return stdOut;
  }
 
}
