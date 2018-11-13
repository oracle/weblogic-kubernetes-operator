// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import java.util.List;
import java.util.Map;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;

/**
 * The interface for the class used by the domain model to return effective configurations to the
 * operator runtime.
 */
public interface EffectiveConfigurationFactory {

  ServerSpec getAdminServerSpec();

  ServerSpec getServerSpec(String serverName, String clusterName);

  int getReplicaCount(String clusterName);

  void setReplicaCount(String clusterName, int replicaCount);

  boolean isShuttingDown();

  List<String> getExportedNetworkAccessPointNames();

  Map<String, String> getChannelServiceLabels(String channel);

  Map<String, String> getChannelServiceAnnotations(String channel);

  Integer getDefaultReplicaLimit();
}
