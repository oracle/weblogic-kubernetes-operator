// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainPresenceInfoTest {
  private static final String[] MANAGED_SERVER_NAMES = {"ms1", "ms2", "ms3"};
  private static final List<ServerStartupInfo> STARTUP_INFOS = Arrays.stream(MANAGED_SERVER_NAMES)
        .map(DomainPresenceInfoTest::toServerStartupInfo)
        .collect(Collectors.toList());
  private final DomainPresenceInfo info = new DomainPresenceInfo("ns", "domain");

  private static ServerStartupInfo toServerStartupInfo(String serverName) {
    return new ServerStartupInfo(
          new WlsServerConfig(serverName, "host", 7001),
          null,
          Stub.createStub(EffectiveServerSpec.class)
          );
  }

  @Test
  void whenNoneDefined_getClusterServiceReturnsNull() {
    assertThat(info.getClusterService("cluster"), nullValue());
  }

  @Test
  void afterClusterServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setClusterService("cluster", service);

    assertThat(info.getClusterService("cluster"), sameInstance(service));
  }

  @Test
  void whenNoneDefined_getServerServiceReturnsNull() {
    assertThat(info.getServerService("admin"), nullValue());
  }

  @Test
  void whenNoServersDefined_getServerStartupInfoReturnsEmptyCollection() {
    assertThat(info.getServerStartupInfo(), empty());
  }

  @Test
  void whenNoServersDefined_expectedRunningServersReturnsEmptyCollection() {
    assertThat(info.getExpectedRunningServers(), empty());
  }

  @Test
  void whenAdminServerNameDefined_expectedRunningServersIncludesAdminServerName() {
    info.setAdminServerName("admin");

    assertThat(info.getExpectedRunningServers(), hasItem("admin"));
  }

  @Test
  void whenServerStartupInfoDefined_expectedRunningServersIncludesDefinedServers() {
    info.setServerStartupInfo(STARTUP_INFOS);

    assertThat(info.getExpectedRunningServers(), hasItems(MANAGED_SERVER_NAMES));
  }

  @Test
  void afterServerServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setServerService("admin", service);

    assertThat(info.getServerService("admin"), sameInstance(service));
  }

  @Test
  void whenNoneDefined_getExternalServiceReturnsNull() {
    assertThat(info.getExternalService("admin"), nullValue());
  }

  @Test
  void afterExternalServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setExternalService("admin", service);

    assertThat(info.getExternalService("admin"), sameInstance(service));
  }

  @Test
  void whenNoneDefined_getServerPodReturnsNull() {
    assertThat(info.getServerPod("myserver"), nullValue());
  }

  @Test
  void afterServerPodDefined_nextCallReturnsIt() {
    V1Pod pod = new V1Pod();
    info.setServerPod("myserver", pod);

    assertThat(info.getServerPod("myserver"), sameInstance(pod));
  }

  @Test
  void whenNoneDefined_getPodDisruptionBudgetReturnsNull() {
    assertThat(info.getPodDisruptionBudget("cluster"), nullValue());
  }

  @Test
  void afterPodDisruptionBudgetDefined_nextCallReturnsIt() {
    V1PodDisruptionBudget pdb = new V1PodDisruptionBudget();
    info.setPodDisruptionBudget("cluster", pdb);

    assertThat(info.getPodDisruptionBudget("cluster"), sameInstance(pdb));
  }

  @Test
  void countReadyServers() {
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
          .phase(V1PodStatus.PhaseEnum.RUNNING)
          .addConditionsItem(new V1PodCondition().type(V1PodCondition.TypeEnum.READY).status("True")));

  }

  @Test
  void countScheduledServers() {
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


  // todo compute availability per cluster: how many servers need to be running, list of servers in cluster
}
