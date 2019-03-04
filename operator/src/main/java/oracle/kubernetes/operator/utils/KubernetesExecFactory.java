// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1Pod;

/** A factory for objects which can execute commands in Kubertenes containers. */
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
