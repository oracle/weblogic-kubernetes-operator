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
import io.kubernetes.client.openapi.models.V1ServiceSpec.SessionAffinityEnum;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.ServerStartState;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterService;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterSpecCommon;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainValidationTestBase.KubernetesResourceLookupStub;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainPresenceInfoTest {
  private static final String[] MANAGED_SERVER_NAMES = {"ms1", "ms2", "ms3"};
  private static final List<ServerStartupInfo> STARTUP_INFOS = Arrays.stream(MANAGED_SERVER_NAMES)
        .map(DomainPresenceInfoTest::toServerStartupInfo)
        .collect(Collectors.toList());
  private static final String NS = "ns";
  private static final String DOMAIN_UID = "domain";
  private static final String ENV_NAME1 = "MY_ENV";
  private static final String RAW_VALUE_1 = "123";
  private static final String RAW_MOUNT_PATH_1 = "$(DOMAIN_HOME)/servers/$(SERVER_NAME)";
  private static final String RAW_MOUNT_PATH_2 = "$(MY_ENV)/bin";
  private static final String BAD_MOUNT_PATH_1 = "$DOMAIN_HOME/servers/$SERVER_NAME";
  private final Domain domain = createDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  protected final KubernetesResourceLookupStub resourceLookup = new KubernetesResourceLookupStub();

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

  private void addScheduledServer(String serverName, String clusterName) {
    addServer(serverName, clusterName);
    setScheduled(info.getServerPod(serverName));
  }

  private void setScheduled(V1Pod pod) {
    pod.spec(new V1PodSpec().nodeName("aNode"));
  }


  // todo compute availability per cluster: how many servers need to be running, list of servers in cluster

  @Test
  void whenClusterResourceDeployed_verifyClusterSpec_fromClusterResource() {
    ClusterSpec clusterSpec = createClusterSpec();

    Cluster cluster = createClusterResource(clusterSpec);

    info.addClusterResource(cluster);

    ClusterSpecCommon clusterSpecCommon = info.getClusterSpecCommon(cluster.getClusterName());
    assertThat(clusterSpecCommon.getClusterSessionAffinity(), equalTo(SessionAffinityEnum.CLIENTIP));
  }

  @Test
  void whenClusterResourceDeployed_verifyClusterSpecAttribute_overrideServerSpec() {
    ClusterSpec clusterSpec = createClusterSpec();

    Cluster cluster = createClusterResource(clusterSpec);

    info.addClusterResource(cluster);

    ServerSpec serverSpec = info.getServer("ms1", cluster.getClusterName());
    assertThat(serverSpec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenClusterResourceNotDeployed_verifyDefaultServerSpec() {
    ServerSpec serverSpec = info.getServer("ms1", "domain-cluster-1");
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenClusterResourceAlreadyDeployed_addUpdatedClusterResource() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    Cluster c = info.getClusterResource(cluster.getClusterName());
    assertThat(c, sameInstance(cluster));
  }

  @Test
  void whenClusterResourceDeployed_verifyReplicaCount() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    int replicaCount = info.getReplicaCount(cluster.getClusterName());
    assertThat(replicaCount, equalTo(5));
  }

  @Test
  void whenClusterResourceDeployed_setReplicaCount() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    info.setReplicaCount(cluster.getClusterName(), 3);

    assertThat(info.getReplicaCount(cluster.getClusterName()), equalTo(3));
  }

  @Test
  void whenClusterResourceDeployed_getMaxUnavailabe() {
    ClusterSpec clusterSpec = createClusterSpec();
    clusterSpec.setMaxUnavailable(5);
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    assertThat(info.getMaxUnavailable(cluster.getClusterName()), equalTo(5));
  }

  @Test
  void whenAdminServerChannelsNotDefined_exportedNamesIsEmpty() {
    assertThat(info.getAdminServerChannelNames(), empty());
  }

  @Test
  void whenClusterResourceDeployed_isAllowReplicasBelowMinDynClusterSize() {
    ClusterSpec clusterSpec = createClusterSpec();
    clusterSpec.setAllowReplicasBelowMinDynClusterSize(false);
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    assertThat(info.isAllowReplicasBelowMinDynClusterSize(cluster.getClusterName()), equalTo(false));
  }

  @Test
  void whenClusterResourceDeployed_getMaxConcurrentStartup() {
    ClusterSpec clusterSpec = createClusterSpec();
    clusterSpec.setMaxConcurrentStartup(3);
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    assertThat(info.getMaxConcurrentStartup(cluster.getClusterName()), equalTo(3));
  }

  @Test
  void whenClusterResourceDeployed_getMaxConcurrentShutdown() {
    ClusterSpec clusterSpec = createClusterSpec();
    clusterSpec.setMaxConcurrentShutdown(3);
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    assertThat(info.getMaxConcurrentShutdown(cluster.getClusterName()), equalTo(3));
  }

  @Test
  void whenClusterSpecsHaveUniqueNames_dontReportError() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    domain.getSpec().getClusters().add(new ClusterSpec().withClusterName("cluster1"));
    domain.getSpec().getClusters().add(new ClusterSpec().withClusterName("cluster2"));

    assertThat(info.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenClusterSpecsHaveDuplicateNames_reportError() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    domain.getSpec().getClusters().add(new ClusterSpec().withClusterName("cluster-1"));

    assertThat(info.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("clusters", "cluster-1")));
  }

  @Test
  void whenClusterSpecsHaveDns1123DuplicateNames_reportError() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    clusterSpec = createClusterSpec("cluster_1");
    cluster = createClusterResource(clusterSpec);
    info.addClusterResource(cluster);

    assertThat(info.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("clusters", "cluster-1")));
  }

  @Test
  void whenClusterServerPodHasAdditionalVolumeMountsWithInvalidChar_reportError() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    cluster.addAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);
    info.addClusterResource(cluster);
    //configureDomain(domain)
    //    .configureCluster("Cluster-1").withAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);

    assertThat(info.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", "of domain resource", "is not valid")));
  }

  @Test
  void whenClusterServerPodHasAdditionalVolumeMountsWithReservedVariables_dontReportError() {
    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    cluster.addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);
    info.addClusterResource(cluster);

    assertThat(info.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenClusterServerPodHasAdditionalVolumeMountsWithCustomVariables_dontReportError() {
    configureDomain(domain)
        .withEnvironmentVariable(ENV_NAME1, RAW_VALUE_1)
        .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    ClusterSpec clusterSpec = createClusterSpec();
    Cluster cluster = createClusterResource(clusterSpec);
    cluster.addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);
    info.addClusterResource(cluster);

    assertThat(info.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenClusterServerPodHasAdditionalVolumeMountsWithNonExistingVariables_reportError() {
    configureDomain(domain)
        .configureCluster("Cluster-1").withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(info.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", "volume1", "of domain resource", "is not valid")));
  }

  private Cluster createClusterResource(ClusterSpec clusterSpec) {
    return new Cluster()
          .withApiVersion(KubernetesConstants.API_VERSION_CLUSTER_WEBLOGIC_ORACLE)
          .withKind(KubernetesConstants.CLUSTER)
          .withMetadata(new V1ObjectMeta()
              .name(qualifyClusterResourceName("domain", clusterSpec.getClusterName()))
              .namespace("ns")
              .putLabelsItem("weblogic.domainUID", "domain")
              .creationTimestamp(SystemClock.now()))
          .spec(clusterSpec);
  }

  private ClusterSpec createClusterSpec() {
    return createClusterSpec("cluster-1");
  }

  private ClusterSpec createClusterSpec(String clusterName) {
    return new ClusterSpec().withClusterName(clusterName).withReplicas(5)
        .withClusterService(new ClusterService().withSessionAffinity(SessionAffinityEnum.CLIENTIP))
        .withServerStartState(ServerStartState.ADMIN);
  }

  private String qualifyClusterResourceName(String domainUid, String clusterName) {
    return domainUid + "-" + clusterName;
  }

  protected static Domain createDomain() {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(NS))
        .withSpec(
            new DomainSpec()
                //.withWebLogicCredentialsSecret(new V1SecretReference().name(SECRET_NAME))
                .withDomainUid(DOMAIN_UID));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }
}
