// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Container;

public abstract class ClusterSpec {

  /**
   * Returns the labels applied to the cluster service.
   *
   * @return a map of labels
   */
  @Nonnull
  public abstract Map<String, String> getClusterLabels();

  /**
   * Returns the annotations applied to the cluster service.
   *
   * @return a map of annotations
   */
  @Nonnull
  public abstract Map<String, String> getClusterAnnotations();

  /**
   * Returns the list of initContainers.
   *
   * @return a list of containers
   */
  @Nonnull
  public List<V1Container> getInitContainers() {
    return Collections.emptyList();
  }

  /**
   * Returns the list of additional containers.
   *
   * @return a list of containers
   */
  @Nonnull
  public List<V1Container> getContainers() {
    return Collections.emptyList();
  }

  /**
   * Returns the shutdown configuration.
   *
   * @return shutdown configuration
   */
  @Nonnull
  public abstract Shutdown getShutdown();

  public abstract String getRestartPolicy();

  public abstract String getRuntimeClassName();

  public abstract String getSchedulerName();

}
