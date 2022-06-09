// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainPresenceInfoTest {
  private static final String[] MANAGED_SERVER_NAMES = {"ms1", "ms2", "ms3"};
  private static final List<ServerStartupInfo> STARTUP_INFOS = Arrays.stream(MANAGED_SERVER_NAMES)
        .map(DomainPresenceInfoTest::toServerStartupInfo)
        .collect(Collectors.toList());
  private static final String NAMESPACE = "ns";
  private static final String DOMAIN_UID = "domain";
  private final DomainPresenceInfo info = new DomainPresenceInfo(NAMESPACE, DOMAIN_UID);

  @NotNull
  private static DomainPresenceInfo createDomainPresenceInfo(DomainResource domain) {
    return new DomainPresenceInfo(domain);
  }

  static DomainResource createDomain(String ns, String domainUid) {
    return new DomainResource()
            .withMetadata(new V1ObjectMeta().namespace(ns))
            .withSpec(
                    new DomainSpec().withDomainUid(domainUid));
  }

  private static ServerStartupInfo toServerStartupInfo(String serverName) {
    return new ServerStartupInfo(
          new WlsServerConfig(serverName, "host", 7001),
          null,
          Stub.createStub(ServerSpec.class)
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

  @Test
  void whenNoneDefined_getClusterResourceReturnsNull() {
    assertThat(info.getClusterResource("cluster-1"), nullValue());
  }

  @Test
  void afterAddingClusterResource_verifyClusterResourceIsNotNull() {
    final String clusterName = "cluster-1";
    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    assertThat(info.getClusterResource(clusterName), notNullValue());
    assertThat(info.getClusterResource(clusterName).getDomainUid(), equalTo(DOMAIN_UID));
  }

  @Test
  void afterRemoveClusterResource_verifyClusterResourceIsNull() {
    final String clusterName = "cluster-1";
    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    ClusterResource clusterResource = info.removeClusterResource(clusterName);
    assertThat(clusterResource, notNullValue());
    assertThat(info.getClusterResource(clusterName), nullValue());
  }

  private void createAndAddClusterResourceToDomainInfo(
          DomainPresenceInfo dpi, String clusterName, String domainUid) {
    dpi.addClusterResource(createClusterResource(clusterName, domainUid));
  }

  @Test
  void whenClusterResourceNotDefined_removeReturnsNull() {
    final String clusterName = "cluster-1";
    ClusterResource clusterResource = info.removeClusterResource(clusterName);
    assertThat(clusterResource, nullValue());
  }

  private ClusterResource createClusterResource(String clusterName, String domain1) {
    return new ClusterResource()
            .spec(createClusterSpec(clusterName, domain1));
  }

  private Cluster createClusterSpec(String clusterName, String domain1) {
    return new Cluster().withClusterName(clusterName).withDomainUid(domain1);
  }

  @Test
  void adminServerSpecHasStandardValues() {
    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    ServerSpec spec = info.getAdminServerSpec();

    verifyStandardFields(spec);
  }

  @Test
  void whenClusterResourceDefined_butServerNotDefined_getEffectiveServerSpec() {
    final String clusterName = "cluster-1";
    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    ServerSpec spec = info.getServer("aServer", clusterName);

    verifyStandardFields(spec);
  }

  @Test
  void whenClusterResourceDefined_getClusterRestartVersionFromServerSpec() {
    final String clusterName = "cluster-1";
    final String serverName = "managed-server1";
    final String MY_RESTART_VERSION = "MyRestartVersion";

    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    info.getClusterResource(clusterName).getSpec().setRestartVersion(MY_RESTART_VERSION);

    ServerSpec spec = info.getServer(serverName, clusterName);

    verifyStandardFields(spec);
    assertThat(spec.getClusterRestartVersion(), equalTo(MY_RESTART_VERSION));
  }

  @Test
  void whenClusterResourceNotDefined_getEffectiveDefaultClusterSpec() {
    final String clusterName = "cluster-1";
    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);

    ClusterSpec clusterSpec = info.getCluster(clusterName);
    assertThat(clusterSpec.getClusterLabels(), anEmptyMap());
  }

  @Test
  void whenClusterResourceDefined_getEffectiveClusterSpec() {
    final String clusterName = "cluster-1";
    final String labelKey = "Hello";
    final String labelValue = "World";

    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    info.getClusterResource(clusterName).getSpec().getClusterLabels().put(labelKey, labelValue);

    ClusterSpec clusterSpec = info.getCluster(clusterName);
    Map<String,String> labels = clusterSpec.getClusterLabels();
    assertThat(labels, aMapWithSize(1));
    assertThat(labels.get(labelKey), equalTo(labelValue));
  }

  @Test
  void whenClusterResourceDefined_getEffectiveReplicaCountFromClusterResource() {
    final String clusterName = "cluster-1";

    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    info.setReplicaCount(clusterName, 5);

    assertThat(info.getReplicaCount(clusterName), equalTo(5));
  }

  @Test
  void whenClusterResourceNotDefined_getEffectiveReplicaCountFromDomain() {
    final String clusterName = "cluster-1";

    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    domain.getSpec().setReplicas(3);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);

    assertThat(info.getReplicaCount(clusterName), equalTo(3));
  }

  @Test
  void whenClusterResourceNotDefined_getDefaultMaxUnvailable() {
    final String clusterName = "cluster-1";

    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);

    assertThat(info.getMaxUnavailable(clusterName), equalTo(1));
  }

  @Test
  void whenAdminServerStartPolicy_isNever_verifyIsShuttingDown() {
    final String clusterName = "cluster-1";

    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    domain.getSpec().getOrCreateAdminServer().setServerStartPolicy(ServerStartPolicy.NEVER);

    assertThat(info.isShuttingDown(), is(true));
  }

  @Test
  void getAdminServerChannelNames() {
    final DomainResource domain = createDomain(NAMESPACE, DOMAIN_UID);
    final DomainPresenceInfo info = createDomainPresenceInfo(domain);
    domain.getSpec().getOrCreateAdminServer().createAdminService()
            .withChannel("Channel-A")
            .withChannel("Channel-B");

    assertThat(info.getAdminServerChannelNames(), containsInAnyOrder("Channel-A", "Channel-B"));
  }

  @Test
  void whenClusterResourceNotDefined_verifyDefaultIsAllowReplicasBelowMinDynClusterSize() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    assertThat(info.isAllowReplicasBelowMinDynClusterSize(clusterName), is(true));
  }

  @Test
  void whenClusterResourceNotDefined_verifyIsAllowReplicasBelowMinDynClusterSizeFromDomainSpec() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    info.getDomain().getSpec().setAllowReplicasBelowMinDynClusterSize(false);
    assertThat(info.isAllowReplicasBelowMinDynClusterSize(clusterName), is(false));
  }

  @Test
  void whenClusterResourceDefined_verifyIsAllowReplicasBelowMinDynClusterSize() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    info.getClusterResource(clusterName).getSpec().setAllowReplicasBelowMinDynClusterSize(false);

    assertThat(info.isAllowReplicasBelowMinDynClusterSize(clusterName), is(false));
  }

  @Test
  void whenClusterResourceNotDefined_getDefaultMaxConcurrentStartup() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    assertThat(info.getMaxConcurrentStartup(clusterName), equalTo(0));
  }

  @Test
  void whenClusterResourceNotDefined_verifyGetMaxConcurrentStartupFromDomainSpec() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    info.getDomain().getSpec().setMaxClusterConcurrentStartup(5);
    assertThat(info.getMaxConcurrentStartup(clusterName), equalTo(5));
  }

  @Test
  void whenClusterResourceDefined_verifyGetMaxConcurrentStartup() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    info.getClusterResource(clusterName).getSpec().setMaxConcurrentStartup(3);
    assertThat(info.getMaxConcurrentStartup(clusterName), equalTo(3));
  }

  @Test
  void whenClusterResourceNotDefined_getDefaultMaxConcurrentShutdown() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    assertThat(info.getMaxConcurrentShutdown(clusterName), equalTo(1));
  }

  @Test
  void whenClusterResourceNotDefined_verifyGetMaxConcurrentShutdownFromDomainSpec() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    info.getDomain().getSpec().setMaxClusterConcurrentShutdown(7);
    assertThat(info.getMaxConcurrentShutdown(clusterName), equalTo(7));
  }

  @Test
  void whenClusterResourceDefined_verifyGetMaxConcurrentShutdown() {
    final String clusterName = "cluster-1";
    final DomainPresenceInfo info = createDomainPresenceInfo(createDomain(NAMESPACE,DOMAIN_UID));
    createAndAddClusterResourceToDomainInfo(info, clusterName, DOMAIN_UID);
    info.getClusterResource(clusterName).getSpec().setMaxConcurrentShutdown(2);
    assertThat(info.getMaxConcurrentShutdown(clusterName), equalTo(2));
  }


  // Confirms the value of fields that are constant across the domain
  private void verifyStandardFields(ServerSpec spec) {
    assertThat(spec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(spec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.IFNOTPRESENT));
    assertThat(spec.getImagePullSecrets(), empty());
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
