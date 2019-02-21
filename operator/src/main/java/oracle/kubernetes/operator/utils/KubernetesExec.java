// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.ApiException;
import java.io.IOException;

/** A base class for an object which can execute a command in an Kubertenes containers. */
public abstract class KubernetesExec {
  private boolean stdin = true;
  private boolean tty = true;

  public void setStdin(boolean stdin) {
    this.stdin = stdin;
  }

  public void setTty(boolean tty) {
    this.tty = tty;
  }

  boolean isStdin() {
    return stdin;
  }

  boolean isTty() {
    return tty;
  }

  /**
   * Executes the command.
   *
   * @param command the shell script command to run
   * @return the process which mediates the execution
   * @throws ApiException if a Kubernetes-specific problem arises
   * @throws IOException if another problem occurs while trying to run the command
   */
  public abstract Process exec(String... command) throws ApiException, IOException;
}
