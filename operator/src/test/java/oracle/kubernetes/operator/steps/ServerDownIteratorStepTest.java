// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerShutdownInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ServerDownIteratorStepTest {

  protected static final String DOMAIN_NAME = "domain1";
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  private static final String ADMIN = "asName";
  private static final String CLUSTER = "cluster1";
  private static final String CLUSTER2 = "cluster2";
  private static final String MS_PREFIX = "ms";
  private static final String MS1 = MS_PREFIX + "1";
  private static final String MS2 = MS_PREFIX + "2";
  private static final String MS3 = MS_PREFIX + "3";
  private static final String MS4 = MS_PREFIX + "4";
  private static final String MS5 = MS_PREFIX + "5";
  private static final String MS6 = MS_PREFIX + "6";
  private static final String UID_MS1 = UID + "-" + MS1;
  private static final String UID_MS2 = UID + "-" + MS2;
  private static final int MAX_SERVERS = 5;
  private static final int PORT = 8001;
  private static final String[] MANAGED_SERVER_NAMES =
          IntStream.rangeClosed(1, MAX_SERVERS)
                  .mapToObj(ServerDownIteratorStepTest::getManagedServerName).toArray(String[]::new);

  @Nonnull
  private static String getManagedServerName(int n) {
    return MS_PREFIX + n;
  }

  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);

  private final Step nextStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfoWithServers();
  private final WlsDomainConfig domainConfig = createDomainConfig();
  private List<ServerShutdownInfo> serverShutdownInfos;
  private final V1Pod managedPod1 = defineManagedPod(MS1);
  private final V1Pod managedPod2 = defineManagedPod(MS2);
  private final V1Pod managedPod3 = defineManagedPod(MS3);
  private final V1Pod managedPod4 = defineManagedPod(MS4);

  private final Collection<String> deletedPodNames = new HashSet<>();

  private static WlsDomainConfig createDomainConfig() {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(CLUSTER);
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig("base_domain")
            .withAdminServer(ADMIN, "domain1-admin-server", 7001)
            .withCluster(clusterConfig);
  }

  private DomainPresenceInfo createDomainPresenceInfoWithServers(String... serverNames) {
    DomainPresenceInfo dpi = new DomainPresenceInfo(domain);
    addServer(dpi, ADMIN);
    Arrays.asList(serverNames).forEach(serverName -> addServer(dpi, serverName));
    return dpi;
  }

  private static void addServer(DomainPresenceInfo domainPresenceInfo, String serverName) {
    if (serverName.equals(ADMIN)) {
      domainPresenceInfo.setServerPod(serverName, createReadyPod(serverName));
    } else {
      domainPresenceInfo.setServerPod(serverName, createPod(serverName));
    }
  }

  private static V1Pod createReadyPod(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS), serverName))
            .spec(new V1PodSpec().nodeName("Node1"))
            .status(new V1PodStatus().phase("Running")
            .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
  }

  private static V1Pod createPod(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS), serverName));
  }

  private static V1ObjectMeta withNames(V1ObjectMeta objectMeta, String serverName) {
    return objectMeta
            .name(LegalNames.toPodName(UID, serverName))
            .putLabelsItem(LabelConstants.SERVERNAME_LABEL, serverName);
  }

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());

    testSupport.defineResources(domain, managedPod1, managedPod2, managedPod3, managedPod4);
    testSupport
            .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig)
            .addDomainPresenceInfo(domainPresenceInfo);
    testSupport.doOnCreate(KubernetesTestSupport.POD, p -> setPodReadyWithDelay((V1Pod) p));
    testSupport.doOnDelete(POD, this::recordPodDeletion);
  }

  private V1Pod defineManagedPod(String name) {
    return new V1Pod().metadata(createManagedPodMetadata(name));
  }

  private V1ObjectMeta createManagedPodMetadata(String name) {
    return createPodMetadata(name)
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL,"true")
            .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, UID)
            .putLabelsItem(LabelConstants.SERVERNAME_LABEL, name)
            .putAnnotationsItem("Placeholder", "At-Least-One-Annotation");
  }

  private V1ObjectMeta createPodMetadata(String name) {
    return new V1ObjectMeta()
            .name(UID + "-" + name)
            .namespace(NS);
  }

  private void setPodReadyWithDelay(V1Pod pod) {
    testSupport.schedule(() -> pod.status(createPodReadyStatus()), 1, TimeUnit.SECONDS);
  }

  private V1PodStatus createPodReadyStatus() {
    return new V1PodStatus()
            .phase("Running")
            .addConditionsItem(new V1PodCondition().status("True").type("Ready"));
  }

  private void recordPodDeletion(KubernetesTestSupport.DeletionContext context) {
    deletedPodNames.add(context.name());
  }

  private Collection<String> serverPodsDeleted() {
    return deletedPodNames;
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void withConcurrencyOf1_bothClusteredServersShutdownSequentially() {
    configureCluster(CLUSTER).withMaxConcurrentShutdown(1).withReplicas(1);
    addWlsCluster(CLUSTER, 8001, MS1, MS2);
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS1, MS2);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forClusteredServers(CLUSTER, MS1, MS2)
            .shutdown();

    assertThat(serverPodsDeleted(), containsInAnyOrder(UID_MS2));
    testSupport.setTime(10, TimeUnit.SECONDS);
    assertThat(serverPodsNotDeleted(), not(containsInAnyOrder(MS1, MS2)));
  }

  @Test
  void withConcurrencyOf2_bothClusteredServersShutdownConcurrently() {
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS1, MS2);
    configureCluster(CLUSTER).withMaxConcurrentShutdown(2).withReplicas(1);
    addWlsCluster(CLUSTER, PORT, MS1, MS2);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forClusteredServers(CLUSTER,MS1, MS2)
            .shutdown();

    assertThat(serverPodsDeleted(), containsInAnyOrder(UID_MS1, UID_MS2));
  }

  @Test
  void withConcurrencyOf0_clusteredServersShutdownConcurrently() {
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS1, MS2);
    configureCluster(CLUSTER).withMaxConcurrentShutdown(0);
    addWlsCluster(CLUSTER, PORT, MS1, MS2);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forClusteredServers(CLUSTER,MS1, MS2)
            .shutdown();

    assertThat(serverPodsDeleted(), containsInAnyOrder(UID_MS1, UID_MS2));
  }

  @Test
  void whenClusterShutdown_concurrencySettingIsIgnored() {
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS1, MS2);
    configureCluster(CLUSTER).withMaxConcurrentShutdown(1).withReplicas(0);
    addWlsCluster(CLUSTER, PORT, MS1, MS2);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forClusteredServers(CLUSTER,MS1, MS2)
            .shutdown();

    assertThat(serverPodsDeleted(), containsInAnyOrder(UID_MS1, UID_MS2));
  }

  @Test
  void whenMaxConcurrentShutdownSet_limitNumberOfServersShuttingDownAtOnce() {
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS1, MS2, MS3, MS4);
    configureCluster(CLUSTER).withMaxConcurrentShutdown(2).withReplicas(1);
    addWlsCluster(CLUSTER, PORT, MS1, MS2, MS3, MS4);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forClusteredServers(CLUSTER, MS1, MS2, MS3, MS4)
            .shutdown();

    assertThat(serverPodsDeleted(), hasSize(2));
    testSupport.setTime(5, TimeUnit.SECONDS);
  }

  @Test
  @Disabled("Contents of data repository doesn't match expectations of test")
  void withMultipleClusters_concurrencySettingIsIgnoredForShuttingDownClusterAndHonoredForShrinkingCluster() {
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS1, MS2, MS3, MS4, MS5, MS6);
    configureCluster(CLUSTER).withMaxConcurrentShutdown(1).withReplicas(0);
    configureCluster(CLUSTER2).withMaxConcurrentShutdown(1).withReplicas(1);
    addWlsCluster(CLUSTER, PORT, MS1, MS2, MS3);
    addWlsCluster(CLUSTER2, PORT, MS4, MS5, MS6);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forClusteredServers(CLUSTER, MS1, MS2, MS3)
            .forClusteredServers(CLUSTER2, MS4, MS5, MS6)
            .shutdown();

    assertThat(serverPodsBeingDeleted(), containsInAnyOrder(MS1, MS2, MS3, MS6));
  }

  @Test
  @Disabled("Contents of data repository doesn't match expectations of test")
  void withMultipleClusters_differentClusterScheduleAndShutdownDifferently() {
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS1, MS2, MS3, MS4);
    configureCluster(CLUSTER).withMaxConcurrentShutdown(0).withReplicas(1);
    configureCluster(CLUSTER2).withMaxConcurrentShutdown(1).withReplicas(1);
    addWlsCluster(CLUSTER, PORT, MS1, MS2);
    addWlsCluster(CLUSTER2, PORT, MS3, MS4);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forClusteredServers(CLUSTER,MS1, MS2)
            .forClusteredServers(CLUSTER2, MS3, MS4)
            .shutdown();

    assertThat(serverPodsBeingDeleted(), containsInAnyOrder(MS1, MS2, MS4));
  }

  @Test
  @Disabled("Contents of data repository doesn't match expectations of test")
  void maxClusterConcurrentShutdown_doesNotApplyToNonClusteredServers() {
    domain.getSpec().setMaxClusterConcurrentShutdown(1);
    addWlsServers(MS3, MS4);
    domainPresenceInfo = createDomainPresenceInfoWithServers(MS3,MS4);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    createShutdownInfos()
            .forServers(MS3, MS4)
            .shutdown();

    assertThat(serverPodsBeingDeleted(), containsInAnyOrder(MS3, MS4));
  }

  private List<String> serverPodsBeingDeleted() {
    return domainPresenceInfo.getServerNames().stream()
            .filter(s -> domainPresenceInfo.isServerPodBeingDeleted(s)).toList();
  }

  private List<String> serverPodsNotDeleted() {
    return domainPresenceInfo.getServerNames().stream()
            .filter(s -> domainPresenceInfo.getServerPod(s) != null).toList();
  }

  private ServerDownIteratorStepTest createShutdownInfos() {
    this.serverShutdownInfos = new ArrayList<>();
    return this;
  }

  private ServerDownIteratorStepTest forServers(String... servers) {
    this.serverShutdownInfos.addAll(Arrays.stream(servers).map(this::createShutdownInfo).toList());
    return this;
  }

  private ServerDownIteratorStepTest forClusteredServers(String clusterName, String... servers) {
    this.serverShutdownInfos.addAll(Arrays.stream(servers).map(s -> createShutdownInfo(clusterName, s)).toList());
    return this;
  }

  private void shutdown() {
    testSupport.runSteps(new ServerDownIteratorStep(this.serverShutdownInfos, nextStep));
  }

  private ServerShutdownInfo createShutdownInfo(String clusterName, String serverName) {
    return new ServerShutdownInfo(configSupport.getWlsServer(clusterName, serverName).getName(), clusterName);
  }

  @Nonnull
  private ServerShutdownInfo createShutdownInfo(String server) {
    return new ServerShutdownInfo(configSupport.getWlsServer(server).getName(), null);
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(domainPresenceInfo, clusterName);
  }

  private void addWlsServers(String... serverNames) {
    Arrays.asList(serverNames).forEach(this::addWlsServer);
  }

  private void addWlsServer(String serverName) {
    configSupport.addWlsServer(serverName, 8001);
  }

  private void addWlsCluster(String clusterName, int port, String... serverNames) {

    configSupport.addWlsCluster(clusterName, port, serverNames);
  }
}