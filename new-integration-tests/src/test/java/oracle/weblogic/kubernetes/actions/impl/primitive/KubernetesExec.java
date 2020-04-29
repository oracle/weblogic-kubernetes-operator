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

  /**
   * Get the API client for these exec operations.
   *
   * @return The API client that will be used.
   */
  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public KubernetesExec pod(V1Pod pod) {
    this.pod = pod;
    return this;
  }

  /**
   * Get the Kubernetes pod where the command is to be run.
   *
   * @return Kubernetes pod object
   */
  public V1Pod getPod() {
    return pod;
  }

  public void setPod(V1Pod pod) {
    this.pod = pod;
  }


  public KubernetesExec containerName(String containerName) {
    this.containerName = containerName;
    return this;
  }

  /**
   * Get the name of the container in which the command is to be run.
   *
   * @return name of the container
   */
  public String getContainerName() {
    return containerName;
  }

  public void setContainerName(String containerName) {
    this.containerName = containerName;
  }

  public KubernetesExec passStdinAsStream() {
    this.passStdinAsStream = true;
    return this;
  }

  /**
   * Is a stdin stream passed into the container.
   *
   * @return true if configured to pass a stdin stream into the container, otherwise false
   */
  public boolean getPassStdInAsStream() {
    return this.passStdinAsStream;
  }

  public void setPassStdinAsStream(boolean passStdinAsStream) {
    this.passStdinAsStream = passStdinAsStream;
  }

  public KubernetesExec stdinIsTty() {
    this.stdinIsTty = true;
    return this;
  }

  /**
   * Is stdin a TTY.
   *
   * @return true if stdin is a TTY, otherwise false
   */
  public boolean getStdinIsTty() {
    return this.stdinIsTty;
  }

  public void setStdinIsTty(boolean stdinIsTty) {
    this.stdinIsTty = stdinIsTty;
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
    return new Exec(apiClient).exec(pod, command, containerName, true, true);
  }
}
