// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudget;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainPresenceInfoTest {
  private final DomainPresenceInfo info = new DomainPresenceInfo("ns", "domain");

  @Test
  public void whenNoneDefined_getClusterServiceReturnsNull() {
    assertThat(info.getClusterService("cluster"), nullValue());
  }

  @Test
  public void afterClusterServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setClusterService("cluster", service);

    assertThat(info.getClusterService("cluster"), sameInstance(service));
  }

  @Test
  public void whenNoneDefined_getServerServiceReturnsNull() {
    assertThat(info.getServerService("admin"), nullValue());
  }

  @Test
  public void afterServerServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setServerService("admin", service);

    assertThat(info.getServerService("admin"), sameInstance(service));
  }

  @Test
  public void whenNoneDefined_getExternalServiceReturnsNull() {
    assertThat(info.getExternalService("admin"), nullValue());
  }

  @Test
  public void afterExternalServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setExternalService("admin", service);

    assertThat(info.getExternalService("admin"), sameInstance(service));
  }

  @Test
  public void whenNoneDefined_getServerPodReturnsNull() {
    assertThat(info.getServerPod("myserver"), nullValue());
  }

  @Test
  public void afterServerPodDefined_nextCallReturnsIt() {
    V1Pod pod = new V1Pod();
    info.setServerPod("myserver", pod);

    assertThat(info.getServerPod("myserver"), sameInstance(pod));
  }

  @Test
  public void whenNoneDefined_getPodDisruptionBudgetReturnsNull() {
    assertThat(info.getPodDisruptionBudget("cluster"), nullValue());
  }

  @Test
  public void afterPodDisruptionBudgetDefined_nextCallReturnsIt() {
    V1beta1PodDisruptionBudget pdb = new V1beta1PodDisruptionBudget();
    info.setPodDisruptionBudget("cluster", pdb);

    assertThat(info.getPodDisruptionBudget("cluster"), sameInstance(pdb));
  }

  @Test
  public void afterValidationWarningsAdded_nextCallReturnsThem() {
    final String warning1 = "warning1";
    final String warning2 = "warning2";

    info.addValidationWarning(warning1);
    info.addValidationWarning(warning2);

    assertThat(info.getValidationWarningsAsString(), containsString(warning1));
    assertThat(info.getValidationWarningsAsString(), containsString(warning2));
  }

  @Test
  public void countReadyServers() {
    addServer("MS1", "cluster1");
    addReadyServer("MS2", "cluster1");
    addServer("MS3", "cluster2");
    addReadyServer("MS4", "cluster2");
    addServer("MS5", null);
    addReadyServer("MS6", null);

    assertThat(info.getNumReadyServers("cluster1"), equalTo(2L));
    assertThat(info.getNumReadyServers("cluster2"), equalTo(2L));
    assertThat(info.getNumReadyServers(null), equalTo(1L));
  }

  private void addServer(String serverName, String clusterName) {
    info.setServerPod(serverName, createServerInCluster(serverName, clusterName));
  }

  private V1Pod createServerInCluster(String podName, String clusterName) {
    return new V1Pod().metadata(new V1ObjectMeta().name(podName).putLabelsItem(CLUSTERNAME_LABEL, clusterName));
  }

  private void addReadyServer(String serverName, String clusterName) {
    addServer(serverName, clusterName);
    setReady(info.getServerPod(serverName));
  }

  private void setReady(V1Pod pod) {
    pod.status(new V1PodStatus()
          .phase("Running")
          .addConditionsItem(new V1PodCondition().type("Ready").status("True")));

  }

  @Test
  public void countScheduledServers() {
    addServer("MS1", "cluster1");
    addScheduledServer("MS2", "cluster1");
    addServer("MS3", "cluster2");
    addScheduledServer("MS4", "cluster2");
    addServer("MS5", null);
    addScheduledServer("MS6", null);

    assertThat(info.getNumScheduledServers("cluster1"), equalTo(2L));
    assertThat(info.getNumScheduledServers("cluster2"), equalTo(2L));
    assertThat(info.getNumScheduledServers(null), equalTo(1L));
  }

  private void addScheduledServer(String serverName, String clusterName) {
    addServer(serverName, clusterName);
    setScheduled(info.getServerPod(serverName));
  }

  private void setScheduled(V1Pod pod) {
    pod.spec(new V1PodSpec().nodeName("aNode"));
  }

}
