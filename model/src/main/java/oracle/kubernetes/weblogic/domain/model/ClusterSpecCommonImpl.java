// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import io.kubernetes.client.models.V1Container;
import java.util.List;
import java.util.Map;

public class ClusterSpecCommonImpl extends ClusterSpec {
  private final Cluster cluster;

  public ClusterSpecCommonImpl(DomainSpec spec, Cluster cluster) {
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

  @Override
  public List<V1Container> getInitContainers() {
    return cluster.getInitContainers();
  }

  @Override
  public List<V1Container> getContainers() {
    return cluster.getContainers();
  }
}
