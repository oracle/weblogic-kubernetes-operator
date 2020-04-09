// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.Map;

/**
 * Contains the parameters for an execute command.
 */

public class CommandParams {

  // The command to execute
  private String command;
 
  // The env variables that are needed for running a command
  private Map<String, String> env;

  // Whether the output of the command is redirected to system out
  private boolean redirect = true;
  
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
}
