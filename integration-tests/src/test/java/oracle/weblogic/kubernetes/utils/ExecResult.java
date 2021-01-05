// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;


/**
 * Class that holds the results of using java to exec a command (i.e. exit value, stdout and stderr)
 */
public class ExecResult {
  private final int exitValue;
  private final String stdout;
  private final String stderr;

  /**
   * Populate execution result.
   * @param exitValue Exit value
   * @param stdout Contents of standard out
   * @param stderr Contents of standard error
   */
  public ExecResult(
      int exitValue, 
      String stdout, 
      String stderr) {
    this.exitValue = exitValue;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  public int exitValue() {
    return this.exitValue;
  }

  public String stdout() {
    return this.stdout;
  }

  public String stderr() {
    return this.stderr;
  }

  @Override
  public String toString() {
    return String.format(
        "ExecResult: exitValue = %s, stdout = %s, stderr = %s", 
        exitValue, 
        stdout, 
        stderr);
  }
}
