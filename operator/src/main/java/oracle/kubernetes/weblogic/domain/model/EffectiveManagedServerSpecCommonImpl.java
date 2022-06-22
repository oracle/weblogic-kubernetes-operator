// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

public class EffectiveManagedServerSpecCommonImpl extends EffectiveServerSpecCommonImpl {
  /**
   * Constructs an object to return the effective configuration for a managed server.
   *
   * @param spec the domain specification
   * @param server the server whose configuration is to be returned
   * @param clusterSpec the cluster that this managed server belongs to
   * @param clusterLimit the number of servers desired for the cluster, or null if not a clustered
   *     server
   */
  public EffectiveManagedServerSpecCommonImpl(
          DomainSpec spec, Server server, ClusterSpec clusterSpec, Integer clusterLimit) {
    super(spec, server, clusterSpec, clusterLimit);
  }

  @Override
  public boolean shouldStart(int currentReplicas) {
    if (isStartAdminServerOnly()) {
      return false;
    }
    return super.shouldStart(currentReplicas);
  }

  @Override
  public boolean alwaysStart() {
    if (isStartAdminServerOnly()) {
      return false;
    }
    return super.alwaysStart();
  }
}
