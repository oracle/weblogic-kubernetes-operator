// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.processing;

import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;

public interface EffectiveIntroServerPodSpec {
  /**
   * Returns the environment variables to be defined for this server.
   *
   * @return a list of environment variables
   */
  List<V1EnvVar> getEnvironmentVariables();

  V1ResourceRequirements getResources();
}
