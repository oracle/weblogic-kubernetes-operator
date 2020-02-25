// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.IOException;

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;

/** The live implementation of this factory, which uses the 'kubectl exec' command. */
public class KubernetesExecFactoryImpl implements KubernetesExecFactory {
  @Override
  public KubernetesExec create(ApiClient client, V1Pod pod, String containerName) {
    return new KubernetesExecImpl(client, pod, containerName);
  }

  public static class KubernetesExecImpl extends KubernetesExec {
    private final ApiClient client;
    private final V1Pod pod;
    private final String containerName;

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
