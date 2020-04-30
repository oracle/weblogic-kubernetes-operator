// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.IOException;

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;

/**
 * KubernetesExec object for executing a command in the container of a pod.
 */
public class KubernetesExec {
  // the Kubernetes api client to dispatch the "exec" command
  private ApiClient apiClient;

  // The Kubernetes pod where the command is to be run
  private V1Pod pod;

  // the container in which the command is to be run
  private String containerName;

  // If true, pass a stdin stream into the container
  private boolean passStdinAsStream;

  // If true, stdin is a TTY (only applies if stdin is true)
  private boolean stdinIsTty;

  public KubernetesExec apiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
    return this;
  }

  public KubernetesExec pod(V1Pod pod) {
    this.pod = pod;
    return this;
  }


  public KubernetesExec containerName(String containerName) {
    this.containerName = containerName;
    return this;
  }

  public KubernetesExec passStdinAsStream() {
    this.passStdinAsStream = true;
    return this;
  }

  public KubernetesExec stdinIsTty() {
    this.stdinIsTty = true;
    return this;
  }

  /**
   * Execute a command in a container.
   *
   * @param command the command to run
   * @return the process which mediates the execution
   * @throws ApiException if a Kubernetes-specific problem arises
   * @throws IOException if another problem occurs while trying to run the command
   */
  public Process exec(String... command) throws ApiException, IOException {
    return new Exec(apiClient).exec(pod, command, containerName, passStdinAsStream, stdinIsTty);
  }
}
