// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
  
  // Whether the results of the command need to be saved
  private boolean saveResults = false;

  // The stdout of the command execution 
  private String stdout;
  
  // The stderr of the command execution
  private String stderr;

  // Whether to turn on verbose logging  
  private boolean verbose = true;
  
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
  
  public CommandParams saveResults(boolean saveResults) {
    this.saveResults = saveResults;
    return this;
  }

  public boolean saveResults() {
    return saveResults;
  }
 
  public CommandParams stderr(String stderr) {
    this.stderr = stderr;
    return this;
  }

  public String stderr() {
    return stderr;
  }
 
  public CommandParams stdout(String stdout) {
    this.stdout = stdout;
    return this;
  }

  public String stdout() {
    return stdout;
  }
  
  public CommandParams verbose(boolean verbose) {
    this.verbose = verbose;
    return this;
  }

  public boolean verbose() {
    return verbose;
  }
 
}
