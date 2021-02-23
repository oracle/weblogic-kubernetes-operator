// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiException;

/** A base class for an object which can execute a command in an Kubernetes containers. */
public abstract class KubernetesExec {
  private boolean stdin = true;
  private boolean tty = true;

  boolean isStdin() {
    return stdin;
  }

  public void setStdin(boolean stdin) {
    this.stdin = stdin;
  }

  boolean isTty() {
    return tty;
  }

  public void setTty(boolean tty) {
    this.tty = tty;
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
