// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.processing.EffectiveClusterSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;

/**
 * An interface between DomainPresenceInfo and the DomainResource.
 */
public interface PrivateDomainApi {

  /**
   * Returns the specification applicable to a particular server/cluster combination.
   *
   * @param serverName  the name of the server
   * @param clusterName the name of the cluster; may be null for a non-clustered server
   * @param clusterSpec the configuration for the cluster in a Kubernetes resource
   * @return the effective configuration for the server
   */
  EffectiveServerSpec getServer(String serverName, String clusterName, ClusterSpec clusterSpec);

  /**
   * Returns the specification applicable to a particular cluster.
   *
   * @param clusterSpec the spec of the cluster; may be null or empty if no applicable cluster.
   * @return the effective configuration for the cluster
   */
  EffectiveClusterSpec getCluster(ClusterSpec clusterSpec);

  /**
   * Returns the number of replicas to start for the specified cluster.
   *
   * @param clusterSpec the spec of the cluster; may be null or empty if no applicable cluster.
   * @return the result of applying any configurations for this value
   */
  int getReplicaCount(ClusterSpec clusterSpec);

  /**
   * Returns the number of replicas to start for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @param clusterSpec the spec of the cluster; may be null or empty if no applicable cluster.
   */
  void setReplicaCount(String clusterName, ClusterSpec clusterSpec, int replicaLimit);

  /**
   * Returns the maximum number of unavailable replicas for the specified cluster.
   *
   * @param clusterSpec the spec of the cluster; may be null or empty if no applicable cluster.
   * @return the result of applying any configurations for this value
   */
  int getMaxUnavailable(ClusterSpec clusterSpec);

  /**
   * Returns the minimum number of replicas for the specified cluster.
   *
   * @param clusterSpec the spec of the cluster; may be null or empty if no applicable cluster.
   * @return the result of applying any configurations for this value
   */
  int getMinAvailable(ClusterSpec clusterSpec);

  /**
   * Get the maximum number of servers to start concurrently.
   * @param clusterSpec the spec of the cluster; may be null or empty if no applicable cluster.
   */
  int getMaxConcurrentStartup(ClusterSpec clusterSpec);

  /**
   * Get the maximum number of servers to shutdown concurrently.
   * @param clusterSpec the spec of the cluster; may be null or empty if no applicable cluster.
   */
  int getMaxConcurrentShutdown(ClusterSpec clusterSpec);
}
