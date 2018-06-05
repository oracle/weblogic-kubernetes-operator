// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

/**
 * Class that holds the results of using java to exec a command (i.e. exit value, stdout and stderr)
 */
public class ExecResult {
  private int exitValue;
  private String stdout;
  private String stderr;

  public ExecResult(int exitValue, String stdout, String stderr) throws Exception {
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
}
