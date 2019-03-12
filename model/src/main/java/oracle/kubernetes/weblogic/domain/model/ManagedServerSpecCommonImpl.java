// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

public class ManagedServerSpecCommonImpl extends ServerSpecCommonImpl {
  /**
   * Constructs an object to return the effective configuration for a managed server.
   *
   * @param spec the domain specification
   * @param server the server whose configuration is to be returned
   * @param cluster the cluster that this managed server belongs to
   * @param clusterLimit the number of servers desired for the cluster, or null if not a clustered
   *     server
   */
  public ManagedServerSpecCommonImpl(
      DomainSpec spec, Server server, Cluster cluster, Integer clusterLimit) {
    super(spec, server, cluster, clusterLimit);
  }

  @Override
  public boolean shouldStart(int currentReplicas) {
    if (isStartAdminServerOnly()) {
      return false;
    }
    return super.shouldStart(currentReplicas);
  }
}
