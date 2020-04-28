// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.KubernetesExecImpl;

/** The live implementation of this factory, which uses the 'kubectl exec' command. */
public class KubernetesExecFactoryImpl implements KubernetesExecFactory {
  @Override
  public KubernetesExec create(V1Pod pod, String containerName) {
    return new KubernetesExecImpl(pod, containerName);
  }


}
