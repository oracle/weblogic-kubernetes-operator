// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.processing;

import java.util.Collections;
import java.util.List;

import io.kubernetes.client.openapi.models.V1Container;

public abstract class EffectiveClusterSpecBase implements EffectiveClusterSpec {
  /**
   * Returns the list of initContainers.
   *
   * @return a list of containers
   */
  public List<V1Container> getInitContainers() {
    return Collections.emptyList();
  }

  /**
   * Returns the list of additional containers.
   *
   * @return a list of containers
   */
  public List<V1Container> getContainers() {
    return Collections.emptyList();
  }
}
