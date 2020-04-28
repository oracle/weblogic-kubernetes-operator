// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import io.kubernetes.client.openapi.models.V1Pod;

/** A factory for objects which can execute commands in Kubertenes containers. */
public interface KubernetesExecFactory {
  /**
   * Creates an object to execute a command in a Kubernetes container.
   *
   * @param pod the pod which has the container in which the command should be run
   * @param containerName the container in which the command is to be run
   * @return the object to execute the command
   */
  KubernetesExec create(V1Pod pod, String containerName);
}
