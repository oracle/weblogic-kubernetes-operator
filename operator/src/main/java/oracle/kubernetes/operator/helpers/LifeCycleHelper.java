// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Map;
import java.util.Set;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

/**
 * This helper class uses the domain resource that the customer configured to calculate the
 * effective configuration for the servers and clusters in the domain.
 */
public class LifeCycleHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final LifeCycleHelper INSTANCE = new LifeCycleHelper();

  protected LifeCycleHelper() {}

  /**
   * Gets the LifeCycleHelper singleton.
   *
   * @return the lifecycle helper singleton
   */
  public static LifeCycleHelper instance() {
    return INSTANCE;
  }

  /**
   * Update a domain spec to reflect the cluster's new replicas count.
   *
   * @param domain the domain that the customer configured
   * @param clusterConfig the cluster config that holds the cluster's new replicas count
   * @return a new domain spec with an updated replica count
   */
  public void updateDomainSpec(Domain domain, ClusterConfig clusterConfig) {
    LOGGER.entering(domain, clusterConfig);
    DomainSpec domainSpec = domain.getSpec();
    getDomainConfigBuilder(domain).updateDomainSpec(domainSpec, clusterConfig);
    LOGGER.finer("Updated domainSpec: " + domainSpec);
    LOGGER.exiting();
  }

  /**
   * Get the effective configurations of the clusters and servers in this domain.
   *
   * @param domain the domain that the customer configured
   * @param servers the names of the non-clustered servers that are actually configured for this
   *     domain
   * @param clusters the clusters that are actually configured for this domain (the map keys are
   *     cluster name,s the map values are sets of the names of the servers in each cluster)
   * @return a the effective configurations of the clusters and servers
   */
  public DomainConfig getEffectiveDomainConfig(
      Domain domain, Set<String> servers, Map<String, Set<String>> clusters) {
    LOGGER.entering(domain, servers, clusters);
    DomainConfig result = new DomainConfig();
    DomainConfigBuilder bldr = getDomainConfigBuilder(domain);
    DomainSpec domainSpec = domain.getSpec();
    getEffectiveNonClusteredServerConfigs(bldr, result, domainSpec, servers);
    getEffectiveClusterConfigs(bldr, result, domainSpec, clusters);
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Gets the effective configuration for a non-clustered server.
   *
   * @param domain the domain that the customer configured
   * @param serverName the name of the server
   * @return the effective configuration for the server
   */
  public NonClusteredServerConfig getEffectiveNonClusteredServerConfig(
      Domain domain, String serverName) {
    LOGGER.entering(domain, serverName);
    NonClusteredServerConfig result =
        getDomainConfigBuilder(domain)
            .getEffectiveNonClusteredServerConfig(domain.getSpec(), serverName);
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Gets the effective configuration for a clustered server.
   *
   * @param domain the domain that the customer configured
   * @param clusterName the name of the cluster
   * @param serverName the name of the server
   * @return the effective configuration for the server
   */
  public ClusteredServerConfig getEffectiveClusteredServerConfig(
      Domain domain, String clusterName, String serverName) {
    LOGGER.entering(domain, clusterName, serverName);
    ClusteredServerConfig result =
        getDomainConfigBuilder(domain)
            .getEffectiveClusteredServerConfig(domain.getSpec(), clusterName, serverName);
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Gets the effective configuration for a cluster.
   *
   * @param domain the domain that the customer configured
   * @param clusterName the name of the cluster
   * @return the effective configuration for the cluster
   */
  public ClusterConfig getEffectiveClusterConfig(Domain domain, String clusterName) {
    LOGGER.entering(domain, clusterName);
    ClusterConfig result =
        getDomainConfigBuilder(domain).getEffectiveClusterConfig(domain.getSpec(), clusterName);
    LOGGER.exiting(result);
    return result;
  }

  protected void getEffectiveNonClusteredServerConfigs(
      DomainConfigBuilder bldr,
      DomainConfig domainConfig,
      DomainSpec domainSpec,
      Set<String> servers) {
    for (String server : servers) {
      getEffectiveNonClusteredServerConfig(bldr, domainConfig, domainSpec, server);
    }
  }

  protected void getEffectiveNonClusteredServerConfig(
      DomainConfigBuilder bldr, DomainConfig domainConfig, DomainSpec domainSpec, String server) {
    NonClusteredServerConfig ncsc = bldr.getEffectiveNonClusteredServerConfig(domainSpec, server);
    domainConfig.setServer(ncsc.getServerName(), ncsc);
  }

  protected void getEffectiveClusterConfigs(
      DomainConfigBuilder bldr,
      DomainConfig domainConfig,
      DomainSpec domainSpec,
      Map<String, Set<String>> clusters) {
    for (Map.Entry<String, Set<String>> cluster : clusters.entrySet()) {
      getEffectiveClusterConfig(
          bldr, domainConfig, domainSpec, cluster.getKey(), cluster.getValue());
    }
  }

  protected void getEffectiveClusterConfig(
      DomainConfigBuilder bldr,
      DomainConfig domainConfig,
      DomainSpec domainSpec,
      String cluster,
      Set<String> servers) {
    ClusterConfig cc = getEffectiveClusterConfig(bldr, domainSpec, cluster, servers);
    domainConfig.setCluster(cluster, cc);
  }

  protected ClusterConfig getEffectiveClusterConfig(
      DomainConfigBuilder bldr, DomainSpec domainSpec, String cluster, Set<String> servers) {
    ClusterConfig clusterConfig = bldr.getEffectiveClusterConfig(domainSpec, cluster);
    getEffectiveClusteredServerConfigs(bldr, clusterConfig, domainSpec, servers);
    return clusterConfig;
  }

  protected void getEffectiveClusteredServerConfigs(
      DomainConfigBuilder bldr,
      ClusterConfig clusterConfig,
      DomainSpec domainSpec,
      Set<String> servers) {
    for (String server : servers) {
      getEffectiveClusteredServerConfig(bldr, clusterConfig, domainSpec, server);
    }
  }

  protected void getEffectiveClusteredServerConfig(
      DomainConfigBuilder bldr, ClusterConfig clusterConfig, DomainSpec domainSpec, String server) {
    ClusteredServerConfig csc =
        bldr.getEffectiveClusteredServerConfig(domainSpec, clusterConfig.getClusterName(), server);
    clusterConfig.setServer(csc.getServerName(), csc);
  }

  protected DomainConfigBuilder getDomainConfigBuilder(Domain domain) {
    if (VersionHelper.matchesResourceVersion(domain.getMetadata(), VersionConstants.DOMAIN_V1)) {
      return DomainConfigBuilderV1.instance();
    }
    /*
    if (VersionHelper.matchesResourceVersion(domain.getMetadata(), VersionConstants.DOMAIN_V2)) {
      return DomainConfigBuilderV1.instance();
    }
    */
    // TBD - how should we report this error?
    throw new AssertionError(
        "Invalid or missing "
            + LabelConstants.RESOURCE_VERSION_LABEL
            + " label.  It should be "
            + VersionConstants.DOMAIN_V1
            + ". "
            + domain);
  }
}
