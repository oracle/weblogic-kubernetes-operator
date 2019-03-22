// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Exec;
import io.kubernetes.client.models.V1Pod;
import java.io.IOException;

/** The live implementation of this factory, which uses the 'kubectl exec' command. */
public class KubernetesExecFactoryImpl implements KubernetesExecFactory {
  @Override
  public KubernetesExec create(ApiClient client, V1Pod pod, String containerName) {
    return new KubernetesExecImpl(client, pod, containerName);
  }

  public static class KubernetesExecImpl extends KubernetesExec {
    private ApiClient client;
    private V1Pod pod;
    private String containerName;

    KubernetesExecImpl(ApiClient client, V1Pod pod, String containerName) {
      this.client = client;
      this.pod = pod;
      this.containerName = containerName;
    }

    @Override
    public Process exec(String... command) throws ApiException, IOException {
      return new Exec(client).exec(pod, command, containerName, isStdin(), isTty());
    }
  }
}
