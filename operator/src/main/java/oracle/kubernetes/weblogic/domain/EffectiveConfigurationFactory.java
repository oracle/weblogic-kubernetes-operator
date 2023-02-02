// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import java.util.List;

import oracle.kubernetes.operator.processing.EffectiveAdminServerSpec;
import oracle.kubernetes.operator.processing.EffectiveClusterSpec;
import oracle.kubernetes.operator.processing.EffectiveIntrospectorJobPodSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;

/**
 * The interface for the class used by the domain model to return effective configurations to the
 * operator runtime.
 */
public interface EffectiveConfigurationFactory {

  EffectiveIntrospectorJobPodSpec getIntrospectorJobPodSpec();

  EffectiveAdminServerSpec getAdminServerSpec();

  EffectiveServerSpec getServerSpec(String serverName, String clusterName, ClusterSpec clusterSpec);

  EffectiveClusterSpec getClusterSpec(ClusterSpec clusterSpec);

  int getReplicaCount(ClusterSpec clusterSpec);

  void setReplicaCount(String clusterName, ClusterSpec clusterSpec, int replicaCount);

  int getMaxUnavailable(ClusterSpec clusterSpec);

  boolean isShuttingDown();

  List<String> getAdminServerChannelNames();

  int getMaxConcurrentStartup(ClusterSpec clusterSpec);

  int getMaxConcurrentShutdown(ClusterSpec clusterSpec);

}
