// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.processing;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;

public interface EffectiveIntrospectorJobPodSpec {
  /**
   * Returns the environment variables to be defined for the introspector job pod.
   *
   * @return a list of environment variables
   */
  List<V1EnvVar> getEnv();

  /**
   * Returns the source of the environment variables to be defined for the introspector job pod.
   *
   * @return a list of sources for the environment variables
   */
  List<V1EnvFromSource> getEnvFrom();

  V1ResourceRequirements getResources();

  V1PodSecurityContext getPodSecurityContext();

  List<V1Container> getInitContainers();

  Map<String, String> getLabels();

  Map<String, String> getAnnotations();

}
