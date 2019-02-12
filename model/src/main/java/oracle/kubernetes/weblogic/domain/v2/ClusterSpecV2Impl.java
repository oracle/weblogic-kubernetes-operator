// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import java.util.Map;

public class ClusterSpecV2Impl extends ClusterSpec {
  private final Cluster cluster;

  public ClusterSpecV2Impl(DomainSpec spec, Cluster cluster) {
    this.cluster = getBaseConfiguration(cluster);
    this.cluster.fillInFrom(spec);
  }

  private Cluster getBaseConfiguration(Cluster cluster) {
    return cluster != null ? cluster.getConfiguration() : new Cluster();
  }

  @Override
  public Map<String, String> getServiceLabels() {
    return cluster.getServiceLabels();
  }

  @Override
  public Map<String, String> getServiceAnnotations() {
    return cluster.getServiceAnnotations();
  }

  @Override
  public Map<String, String> getClusterLabels() {
    return cluster.getClusterLabels();
  }

  @Override
  public Map<String, String> getClusterAnnotations() {
    return cluster.getClusterAnnotations();
  }
}


// TODO mark: will need a class like this to roll up the initContainers
// also have to think about what the override rules are if they are
// specified at multiple levels