// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

public class ManagedServerSpecV2Impl extends ServerSpecV2Impl {
  /**
   * Constructs an object to return the effective configuration for a managed server.
   *
   * @param spec the domain specification
   * @param server the server whose configuration is to be returned
   * @param clusterLimit the number of servers desired for the cluster, or null if not a clustered
   *     server
   * @param configurations the additional configurations to search for values if the server lacks
   */
  public ManagedServerSpecV2Impl(
      DomainSpec spec, Server server, Integer clusterLimit, BaseConfiguration... configurations) {
    super(spec, server, clusterLimit, configurations);
  }

  @Override
  public boolean shouldStart(int currentReplicas) {
    if (isStartAdminServerOnly()) return false;
    return super.shouldStart(currentReplicas);
  }
}
