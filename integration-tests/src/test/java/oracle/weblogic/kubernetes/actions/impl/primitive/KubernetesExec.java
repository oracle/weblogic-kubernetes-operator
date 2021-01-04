// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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

  /**
   * Set the API client for subsequent exec commands.
   *
   * @param apiClient the API client to use to exec commands
   * @return a KubernetesExec instance for executing commands using a Kubernetes api client
   */
  public KubernetesExec apiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
    return this;
  }

  /**
   * Set the Kubernetes pod where the command is to be run.
   *
   * @param pod the pod where the command is to be run
   * @return a KubernetesExec instance for executing commands in a pod
   */
  public KubernetesExec pod(V1Pod pod) {
    this.pod = pod;
    return this;
  }

  /**
   * Set the name of the container of the pod where the command is to be run.
   *
   * @param containerName the name of the container in the pod where the command is to be run.
   * @return a KubernetesExec instance for executing commands in a container of a pod
   */
  public KubernetesExec containerName(String containerName) {
    this.containerName = containerName;
    return this;
  }

  /**
   * Enable passing a stdin stream into the container of pod where the command is to be run.
   *
   * @return a KubernetesExec instance where a stdin stream will be passed to the container
   */
  public KubernetesExec passStdinAsStream() {
    this.passStdinAsStream = true;
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
    return new Exec(apiClient).exec(pod, command, containerName, passStdinAsStream, false);
  }
}
