// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1ServiceSpec;

public class ClusterSpecCommonImpl extends ClusterSpecCommon {
  private final ClusterSpec cluster;

  public ClusterSpecCommonImpl(DomainSpec spec, ClusterSpec cluster) {
    this.cluster = getBaseConfiguration(cluster);
    this.cluster.fillInFrom(spec);
  }

  private ClusterSpec getBaseConfiguration(ClusterSpec cluster) {
    return cluster != null ? cluster.getConfiguration() : new ClusterSpec();
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
  public V1ServiceSpec.SessionAffinityEnum getClusterSessionAffinity() {
    return cluster.getClusterSessionAffinity();
  }

  @Override
  public List<V1Container> getInitContainers() {
    return cluster.getInitContainers();
  }

  @Override
  public List<V1Container> getContainers() {
    return cluster.getContainers();
  }

  @Override
  public Shutdown getShutdown() {
    return cluster.getShutdown();
  }

  @Override
  public V1PodSpec.RestartPolicyEnum getRestartPolicy() {
    return cluster.getRestartPolicy();
  }

  @Override
  public String getRuntimeClassName() {
    return cluster.getRuntimeClassName();
  }

  @Override
  public String getSchedulerName() {
    return cluster.getSchedulerName();
  }

}
