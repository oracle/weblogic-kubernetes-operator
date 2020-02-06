// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1Toleration;

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
  public Boolean isPrecreateServerService() {
    return cluster.isPrecreateServerService();
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

  @Override
  public Shutdown getShutdown() {
    return cluster.getShutdown();
  }

  @Override
  public V1Affinity getAffinity() {
    return cluster.getAffinity();
  }

  @Override
  public String getPriorityClassName() {
    return cluster.getPriorityClassName();
  }

  @Override
  public List<V1PodReadinessGate> getReadinessGates() {
    return cluster.getReadinessGates();
  }

  @Override
  public String getRestartPolicy() {
    return cluster.getRestartPolicy();
  }

  @Override
  public String getRuntimeClassName() {
    return cluster.getRuntimeClassName();
  }

  @Override
  public String getNodeName() {
    return cluster.getNodeName();
  }

  @Override
  public String getServiceAccountName() {
    return cluster.getServiceAccountName();
  }

  @Override
  public String getSchedulerName() {
    return cluster.getSchedulerName();
  }

  @Override
  public List<V1Toleration> getTolerations() {
    return cluster.getTolerations();
  }

}
