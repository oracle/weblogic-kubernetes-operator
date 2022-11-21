// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.processing;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import oracle.kubernetes.weblogic.domain.model.Shutdown;

public interface EffectiveClusterSpec {
  /**
   * Returns the labels applied to the cluster service.
   *
   * @return a map of labels
   */
  Map<String, String> getClusterLabels();

  /**
   * Returns the annotations applied to the cluster service.
   *
   * @return a map of annotations
   */
  Map<String, String> getClusterAnnotations();

  String getClusterSessionAffinity();

  /**
   * Returns the list of initContainers.
   *
   * @return a list of containers
   */
  List<V1Container> getInitContainers();

  /**
   * Returns the list of additional containers.
   *
   * @return a list of containers
   */
  List<V1Container> getContainers();

  /**
   * Returns the shutdown configuration.
   *
   * @return shutdown configuration
   */
  Shutdown getShutdown();

  String getRestartPolicy();

  String getRuntimeClassName();

  String getSchedulerName();
}
