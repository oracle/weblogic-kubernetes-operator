// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1ServiceSpec;

public class EffectiveClusterSpecCommonImpl extends EffectiveClusterSpec {
  private final ClusterSpec clusterSpec;

  public EffectiveClusterSpecCommonImpl(DomainSpec spec, ClusterSpec clusterSpec) {
    this.clusterSpec = getBaseConfiguration(clusterSpec);
    this.clusterSpec.fillInFrom(spec);
  }

  private ClusterSpec getBaseConfiguration(ClusterSpec clusterSpec) {
    return clusterSpec != null ? clusterSpec.getConfiguration() : new ClusterSpec();
  }

  @Override
  public Map<String, String> getClusterLabels() {
    return clusterSpec.getClusterLabels();
  }

  @Override
  public Map<String, String> getClusterAnnotations() {
    return clusterSpec.getClusterAnnotations();
  }

  @Override
  public V1ServiceSpec.SessionAffinityEnum getClusterSessionAffinity() {
    return clusterSpec.getClusterSessionAffinity();
  }

  @Override
  public List<V1Container> getInitContainers() {
    return clusterSpec.getInitContainers();
  }

  @Override
  public List<V1Container> getContainers() {
    return clusterSpec.getContainers();
  }

  @Override
  public Shutdown getShutdown() {
    return clusterSpec.getShutdown();
  }

  @Override
  public V1PodSpec.RestartPolicyEnum getRestartPolicy() {
    return clusterSpec.getRestartPolicy();
  }

  @Override
  public String getRuntimeClassName() {
    return clusterSpec.getRuntimeClassName();
  }

  @Override
  public String getSchedulerName() {
    return clusterSpec.getSchedulerName();
  }

}
