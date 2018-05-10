// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

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
  protected Integer DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT = new Integer(0);
  protected Boolean DEFAULT_GRACEFUL_SHUTDOWN_IGNORE_SESSIONS = Boolean.FALSE;
  protected Boolean DEFAULT_GRACEFUL_SHUTDOWN_WAIT_FOR_SESSIONS = Boolean.FALSE;
  protected Integer DEFAULT_REPLICAS = new Integer(0); // TBD - is this correct?

  protected DomainConfigBuilder() {}

  /**
   * Update a domain spec to reflect the cluster's new replicas count.
   *
   * @param domainSpec the domain spec that the customer configured
   * @param clusterConfig the cluster config that holds the cluster's new replicas count
   */
  public abstract void updateDomainSpec(DomainSpec domainSpec, ClusterConfig clusterConfig);

  /**
   * Gets the effective configuration for a non-clustered server.
   *
   * @param domainSpec the domain spec that the customer configured
   * @param serverName the name of the server
   * @return the effective configuration for the server
   */
  public abstract NonClusteredServerConfig getEffectiveNonClusteredServerConfig(
      DomainSpec domainSpec, String serverName);

  /**
   * Gets the effective configuration for a clustered server.
   *
   * @param domainSpec the domain spec that the customer configured
   * @param clusterName the name of the cluster
   * @param serverName the name of the server
   * @return the effective configuration for the server
   */
  public abstract ClusteredServerConfig getEffectiveClusteredServerConfig(
      DomainSpec domainSpec, String clusterName, String serverName);

  /**
   * Gets the effective configuration for a cluster.
   *
   * @param domainSpec the domain spec that the customer configured
   * @param clusterName the name of the cluster
   * @return the effective configuration for the cluster
   */
  public abstract ClusterConfig getEffectiveClusterConfig(
      DomainSpec domainSpec, String clusterName);

  protected String getDefaultImagePullPolicy(String image) {
    if (image != null && image.endsWith(LATEST_IMAGE_SUFFIX)) {
      return ALWAYS_IMAGEPULLPOLICY;
    } else {
      return IFNOTPRESENT_IMAGEPULLPOLICY;
    }
  }
}
