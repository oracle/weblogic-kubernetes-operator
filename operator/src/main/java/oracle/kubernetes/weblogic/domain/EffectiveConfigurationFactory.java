// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import java.util.List;

import oracle.kubernetes.operator.processing.EffectiveAdminServerSpec;
import oracle.kubernetes.operator.processing.EffectiveClusterSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;

/**
 * The interface for the class used by the domain model to return effective configurations to the
 * operator runtime.
 */
public interface EffectiveConfigurationFactory {

  EffectiveAdminServerSpec getAdminServerSpec();

  EffectiveServerSpec getServerSpec(String serverName, String clusterName);

  EffectiveClusterSpec getClusterSpec(String clusterName);

  int getReplicaCount(String clusterName);

  void setReplicaCount(String clusterName, int replicaCount);

  int getMaxUnavailable(String clusterName);

  boolean isShuttingDown();

  List<String> getAdminServerChannelNames();

  boolean isAllowReplicasBelowMinDynClusterSize(String clusterName);

  int getMaxConcurrentStartup(String clusterName);

  int getMaxConcurrentShutdown(String clusterName);
}
