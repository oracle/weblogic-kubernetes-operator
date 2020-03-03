// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import oracle.kubernetes.operator.KubernetesConstants;

public abstract class BasePodStepContext extends StepContextBase {
  final <T> T updateForDeepSubstitution(V1PodSpec podSpec, T target) {
    return getContainer(podSpec)
        .map(
            c -> {
              return doDeepSubstitution(augmentSubVars(deepSubVars(c.getEnv())), target);
            })
        .orElse(target);
  }

  final Map<String, String> deepSubVars(List<V1EnvVar> envVars) {
    return varsToSubVariables(envVars);
  }

  protected Map<String, String> augmentSubVars(Map<String, String> vars) {
    return vars;
  }

  protected Optional<V1Container> getContainer(V1Pod v1Pod) {
    return getContainer(v1Pod.getSpec());
  }

  protected Optional<V1Container> getContainer(V1PodSpec v1PodSpec) {
    return v1PodSpec.getContainers().stream().filter(this::isK8sContainer).findFirst();
  }

  protected boolean isK8sContainer(V1Container c) {
    return getMainContainerName().equals(c.getName());
  }

  protected String getMainContainerName() {
    return KubernetesConstants.CONTAINER_NAME;
  }
}
