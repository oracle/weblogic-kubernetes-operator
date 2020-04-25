// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Exec {

  /**
   * Execute a command in a container.
   *
   * @param pod The pod where the command is run
   * @param containerName The container in the Pod where the command is run. If no container name
   *     is provided than the first container in the Pod is used.
   * @param command The command to run
   * @return output from the command
   * @throws IOException if an I/O error occurs.
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String exec(V1Pod pod, String containerName, String... command)
      throws IOException, ApiException {
    return Kubernetes.exec(pod, containerName, command);
  }
}
