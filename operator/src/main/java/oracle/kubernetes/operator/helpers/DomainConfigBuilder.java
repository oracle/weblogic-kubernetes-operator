// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * This base class contains common utilities that the version-specific DomainConfigBuilders use to
 * calculate the effective configuration of the servers and clusters in the domain from the domain
 * spec that the customer configured.
 */
public abstract class DomainConfigBuilder {

  protected static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  protected static Integer DEFAULT_NODE_PORT = new Integer(0);
  protected static String DEFAULT_STARTED_SERVER_STATE = ServerConfig.STARTED_SERVER_STATE_RUNNING;
  protected static String DEFAULT_SHUTDOWN_POLICY = ServerConfig.SHUTDOWN_POLICY_FORCED_SHUTDOWN;
  protected static Integer DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT = new Integer(0);
  protected static Boolean DEFAULT_GRACEFUL_SHUTDOWN_IGNORE_SESSIONS = Boolean.FALSE;
  protected static Boolean DEFAULT_GRACEFUL_SHUTDOWN_WAIT_FOR_SESSIONS = Boolean.FALSE;
  protected static Integer DEFAULT_REPLICAS = new Integer(0);

  /**
   * Update a domain spec to reflect the cluster's new replicas count.
   *
   * @param clusterConfig the cluster config that holds the cluster's new replicas count
   */
  public abstract void updateDomainSpec(ClusterConfig clusterConfig);

  /**
   * Gets the effective configuration for a non-clustered server.
   *
   * @param serverName the name of the server
   * @return the effective configuration for the server
   */
  public abstract NonClusteredServerConfig getEffectiveNonClusteredServerConfig(String serverName);

  /**
   * Gets the effective configuration for a clustered server.
   *
   * @param clusterName the name of the cluster
   * @param serverName the name of the server
   * @return the effective configuration for the server
   */
  public abstract ClusteredServerConfig getEffectiveClusteredServerConfig(
      String clusterName, String serverName);

  /**
   * Gets the effective configuration for a cluster.
   *
   * @param clusterName the name of the cluster
   * @return the effective configuration for the cluster
   */
  public abstract ClusterConfig getEffectiveClusterConfig(String clusterName);

  protected String getDefaultImagePullPolicy(String image) {
    if (image != null && image.endsWith(LATEST_IMAGE_SUFFIX)) {
      return ALWAYS_IMAGEPULLPOLICY;
    } else {
      return IFNOTPRESENT_IMAGEPULLPOLICY;
    }
  }
}
