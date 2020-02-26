// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1Toleration;

public abstract class ClusterSpec {
  /**
   * Returns true if per-server instance services should be created for cluster member services even
   * if pods are not running for these instances.
   *
   * @return true, if per-server instances services should be pre-created.
   */
  public abstract Boolean isPrecreateServerService();

  /**
   * Returns the labels applied to server instance services.
   *
   * @return a map of labels
   */
  @Nonnull
  public abstract Map<String, String> getServiceLabels();

  /**
   * Returns the annotations applied to service instance services.
   *
   * @return a map of annotations
   */
  @Nonnull
  public abstract Map<String, String> getServiceAnnotations();

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

  public abstract V1Affinity getAffinity();

  public abstract String getPriorityClassName();

  public abstract List<V1PodReadinessGate> getReadinessGates();

  public abstract String getRestartPolicy();

  public abstract String getRuntimeClassName();

  public abstract String getNodeName();

  public abstract String getServiceAccountName();

  public abstract String getSchedulerName();

  public abstract List<V1Toleration> getTolerations();

}
