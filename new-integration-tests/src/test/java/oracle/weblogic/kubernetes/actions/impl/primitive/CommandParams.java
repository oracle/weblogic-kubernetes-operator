// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

/**
 * Contains the parameters for installing the WebLogic Image Tool or WebLogic Deploy Tool.
 */

public class CommandParams {

  // the command to execute
  private String command;
  
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

  public CommandParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean redirect() {
    return redirect;
  }
}
