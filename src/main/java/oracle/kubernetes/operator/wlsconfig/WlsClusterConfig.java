// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains configuration of a WLS cluster
 * <p>
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsClusterConfig {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String clusterName;
  private List<WlsServerConfig> serverConfigs = new ArrayList<>();

  public static WlsClusterConfig create(String clusterName) {
    return new WlsClusterConfig(clusterName);
  }

  public WlsClusterConfig(String clusterName) {
    this.clusterName = clusterName;
  }


  synchronized void addServerConfig(WlsServerConfig wlsServerConfig) {
    serverConfigs.add(wlsServerConfig);
  }

  /**
   * Returns the number of servers configured in this cluster
   *
   * @return The number of servers configured in this cluster
   */
  public synchronized int getClusterSize() {
    return serverConfigs.size();
  }

  /**
   * Returns the name of the cluster that this WlsClusterConfig is created for
   *
   * @return the name of the cluster that this WlsClusterConfig is created for
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * Returns a list of server configurations for servers that belong to this cluster.
   *
   * @return A list of WlsServerConfig containing configurations of servers that belong to this cluster
   */
  public synchronized List<WlsServerConfig> getServerConfigs() {
    return serverConfigs;
  }

  /**
   * Validate the clusterStartup configured should be consistent with this configured WLS cluster. The method
   * also logs warning if inconsistent WLS configurations are found.
   * <p>
   * In the future this method may also attempt to fix the configuration inconsistencies by updating the ClusterStartup.
   * It is the responsibility of the caller to persist the changes to ClusterStartup to kubernetes.
   *
   * @param clusterStartup The ClusterStartup to be validated against the WLS configuration
   * @return true if the DomainSpec has been updated, false otherwise
   */
  public boolean validateClusterStartup(ClusterStartup clusterStartup) {
    LOGGER.entering();

    boolean modified = false;

    // log warning if no servers are configured in the cluster
    if (getClusterSize() == 0) {
      LOGGER.warning(MessageKeys.NO_WLS_SERVER_IN_CLUSTER, clusterName);
    }

    // Warns if replicas is larger than the number of servers configured in the cluster
    validateReplicas(clusterStartup.getReplicas(), "clusterStartup");

    LOGGER.exiting(modified);

    return modified;
  }

  public void validateReplicas(Integer replicas, String source) {
    if (replicas != null && replicas > getClusterSize()) {
      LOGGER.warning(MessageKeys.REPLICA_MORE_THAN_WLS_SERVERS, source, clusterName, replicas, getClusterSize());
    }
  }

  @Override
  public String toString() {
    return "WlsClusterConfig{" +
        "clusterName='" + clusterName + '\'' +
        ", serverConfigs=" + serverConfigs +
        '}';
  }
}
