// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import java.util.List;
import oracle.kubernetes.weblogic.domain.model.AdminServerSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;

/**
 * The interface for the class used by the domain model to return effective configurations to the
 * operator runtime.
 */
public interface EffectiveConfigurationFactory {

  AdminServerSpec getAdminServerSpec();

  ServerSpec getServerSpec(String serverName, String clusterName);

  ClusterSpec getClusterSpec(String clusterName);

  int getReplicaCount(String clusterName);

  void setReplicaCount(String clusterName, int replicaCount);

  int getMaxUnavailable(String clusterName);

  boolean isShuttingDown();

  List<String> getAdminServerChannelNames();
}
