// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.utils.ExecResult;

public class Exec {

  /**
   * Execute a command in a container.
   *
   * @param pod The pod where the command is to be run
   * @param containerName The container in the Pod where the command is to be run. If no
   *     container name is provided than the first container in the Pod is used.
   * @param redirectToStdout copy process output to stdout
   * @param command The command to run
   * @return result of command execution
   * @throws IOException if an I/O error occurs.
   * @throws ApiException if Kubernetes client API call fails
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static ExecResult exec(V1Pod pod, String containerName, boolean redirectToStdout,
      String... command)
      throws IOException, ApiException, InterruptedException {
    return Kubernetes.exec(pod, containerName, redirectToStdout, command);
  }
}
