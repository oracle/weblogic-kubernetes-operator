// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;

/** A factory for objects which can execute commands in Kubernetes containers. */
public interface KubernetesExecFactory {
  /**
   * Creates an object to execute a command in a Kubernetes container.
   *
   * @param client the api client to dispatch the "exec" command
   * @param pod the pod which has the container in which the command should be run
   * @param containerName the container in which the command is to be run
   * @return the object to execute the command
   */
  KubernetesExec create(ApiClient client, V1Pod pod, String containerName);
}
