// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainStatusUpdater.ClearCompletedConditionSteps;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.CreateEventStep;
import oracle.kubernetes.operator.helpers.KubernetesEventObjects;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.steps.ManagedServersUpStep.ServersUpStepFactory;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport.DynamicClusterConfigBuilder;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.REPLICAS_EXCEEDS_TOTAL_CLUSTER_SERVER_COUNT;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.steps.ManagedServersUpStep.SERVERS_UP_MSG;
import static oracle.kubernetes.operator.steps.ManagedServersUpStepTest.TestStepFactory.getPreCreateServers;
import static oracle.kubernetes.operator.steps.ManagedServersUpStepTest.TestStepFactory.getServerStartupInfo;
import static oracle.kubernetes.operator.steps.ManagedServersUpStepTest.TestStepFactory.getServers;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/**
 * Tests the code to bring up managed servers. "Wls Servers" and "WLS Clusters" are those defined in
 * the admin server for the domain. There is also a kubernetes "Domain Spec," which specifies which
 * servers should be running.
 */
@SuppressWarnings({"ConstantConditions", "SameParameterValue"})
class ManagedServersUpStepTest {

  private static final String DOMAIN = "domain";
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  private static final String ADMIN = "asName";
  private final DomainResource domain = createDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);

  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN);

  private final Step nextStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainPresenceInfo info = createDomainPresenceInfo();
  private final ManagedServersUpStep step = new ManagedServersUpStep(nextStep);
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;
  private Memento factoryMemento;
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  private static void addServer(DomainPresenceInfo domainPresenceInfo, String serverName) {
    domainPresenceInfo.setServerPod(serverName, createPod(serverName));
  }

  private static V1Pod createPod(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS), serverName));
  }

  private static V1ObjectMeta withNames(V1ObjectMeta objectMeta, String serverName) {
    return objectMeta
        .name(LegalNames.toPodName(UID, serverName))
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, serverName);
  }

  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(domain);
  }

  private DomainResource createDomain() {
    return new DomainResource().withMetadata(createMetaData()).withSpec(createDomainSpec());
  }

  private V1ObjectMeta createMetaData() {
    return new V1ObjectMeta().namespace(NS);
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUid(UID).withReplicas(1);
  }

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(consoleHandlerMemento = TestUtils.silenceOperatorLogger());
    mementos.add(factoryMemento = TestStepFactory.install());
    mementos.add(testSupport.install());
    testSupport.addDomainPresenceInfo(info);
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void whenEnabled_logCurrentServers() {
    List<LogRecord> messages = new ArrayList<>();
    consoleHandlerMemento.withLogLevel(Level.FINE).collectLogMessages(messages, SERVERS_UP_MSG);
    addRunningServer("admin");
    addRunningServer("ms1");
    addRunningServer("ms2");

    invokeStep();

    assertThat(messages, containsFine(SERVERS_UP_MSG));
  }

  private void addRunningServer(String serverName) {
    addServer(info, serverName);
  }

  private void addWlsCluster(String clusterName, String... serverNames) {
    configSupport.addWlsCluster(clusterName, serverNames);
  }

  private void addDynamicWlsCluster(String clusterName,
      int minDynamicClusterSize,
      int maxDynamicClusterSize,
      String... serverNames) {
    configSupport.addWlsCluster(
          new DynamicClusterConfigBuilder(clusterName)
                .withClusterLimits(minDynamicClusterSize, maxDynamicClusterSize)
                .withServerNames(serverNames));
  }

  @Test
  void whenStartPolicyUndefined_startServers() {
    invokeStepWithConfiguredServer();

    assertManagedServersUpStepCreated();
  }

  private void invokeStepWithConfiguredServer() {
    configureServer("configured");
    addWlsServer("configured");
    invokeStep();
  }

  @Test
  void whenStartPolicyIfNeeded_startServers() {
    setDefaultServerStartPolicy(ServerStartPolicy.IF_NEEDED);

    invokeStepWithConfiguredServer();

    assertManagedServersUpStepCreated();
  }

  @Test
  void whenStartPolicyAlways_startServers() {
    startAllServers();

    invokeStepWithConfiguredServer();

    assertManagedServersUpStepCreated();
  }

  private void startAllServers() {
    configurator.withDefaultServerStartPolicy(ServerStartPolicy.ALWAYS);
  }

  private void startConfiguredServers() {
    setDefaultServerStartPolicy(ServerStartPolicy.IF_NEEDED);
  }

  private void assertManagedServersUpStepCreated() {
    assertThat(TestStepFactory.next, instanceOf(ManagedServerUpIteratorStep.class));
  }

  @Test
  void whenStartPolicyAdminOnly_dontStartServers() {
    startAdminServerOnly();

    invokeStepWithConfiguredServer();

    assertManagedServersUpStepNotCreated();
  }

  private void startAdminServerOnly() {
    configurator
        .withDefaultServerStartPolicy(ServerStartPolicy.NEVER)
        .configureAdminServer()
        .withServerStartPolicy(ServerStartPolicy.ALWAYS);
  }

  @Test
  void whenNoServerStartRequested_dontStartServers() {
    startNoServers();

    invokeStepWithConfiguredServer();

    assertManagedServersUpStepNotCreated();
  }

  @Test
  void whenNoServerStartRequested_dontPrependUpdateCompleteConditionStep() {
    startNoServers();

    invokeStepWithConfiguredServer();

    assertThat(firstNonEventStep(createNextStep()), not(instanceOf(ClearCompletedConditionSteps.class)));
  }

  private void startNoServers() {
    configurator.withDefaultServerStartPolicy(ServerStartPolicy.NEVER);
  }

  @Test
  void whenWlsServerInDomainSpec_addToServerList() {
    configureServerToStart("wls1");
    addWlsServer("wls1");

    invokeStep();

    assertThat(getServers(), contains("wls1"));
  }

  @Test
  void whenServerInDomainSpecButNotDefinedInWls_dontAddToServerList() {
    configureServerToStart("wls1");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  void whenMultipleWlsServersInDomainSpec_addToServerList() {
    configureServers("wls1", "wls2", "wls3");
    addWlsServers("wls1", "wls2", "wls3");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("wls1", "wls2", "wls3"));
  }

  @Test
  void whenMultipleWlsServersInDomainSpec_skipAdminServer() {
    defineAdminServer();
    configureServers("wls1", ADMIN, "wls3");
    addWlsServers("wls1", ADMIN, "wls3");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("wls1", "wls3"));
  }

  @Test
  void whenWlsServersDuplicatedInDomainSpec_skipDuplicates() {
    defineAdminServer();
    configureServers("wls1", "wls1", "wls2");
    addWlsServers("wls1", "wls2");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("wls1", "wls2"));
  }

  @Test
  void whenWlsServersInDomainSpec_addStartupInfo() {
    configureServerToStart("wls1");
    configureServerToStart("wls2");
    addWlsServers("wls1", "wls2");

    invokeStep();

    assertThat(getServerStartupInfo("wls1"), notNullValue());
    assertThat(getServerStartupInfo("wls2"), notNullValue());
  }

  @Test
  void serverStartupInfo_containsEnvironmentVariable() {
    configureServerToStart("wls1")
        .withEnvironmentVariable("item1", "value1")
        .withEnvironmentVariable("item2", "value2");
    addWlsServer("wls1");

    invokeStep();

    assertThat(
        getServerStartupInfo("wls1").getEnvironment(),
        containsInAnyOrder(envVar("item1", "value1"), envVar("item2", "value2")));
  }

  @Test
  void whenWlsServerNotInCluster_serverStartupInfoHasNoClusterConfig() {
    configureServerToStart("wls1");
    addWlsServer("wls1");

    invokeStep();

    assertThat(getServerStartupInfo("wls1").getClusterName(), nullValue());
  }

  @Test
  void whenWlsServerInCluster_serverStartupInfoHasMatchingClusterConfig() {
    configureServerToStart("ms1");

    addWlsCluster("cluster1", "ms1");
    addWlsCluster("cluster2");

    invokeStep();

    assertThat(getServerStartupInfo("ms1").getClusterName(), equalTo("cluster1"));
  }

  @Test
  void whenClusterStartupDefinedForServerNotRunning_addToServers() {
    configureServerToStart("ms1");
    configureCluster("cluster1");
    addWlsCluster("cluster1", "ms1");

    invokeStep();

    assertThat(getServers(), hasItem("ms1"));
  }

  @Test
  void whenClusterStartupDefinedWithZeroReplicas_addNothingToServers() {
    configureCluster("cluster1").withReplicas(0);
    addWlsCluster("cluster1", "ms1", "ms2");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  void whenServerStartupNotDefined_useEnvForCluster() {
    configureCluster("cluster1").withEnvironmentVariable("item1", "value1");
    addWlsCluster("cluster1", "ms1");

    configureCluster("cluster1").withServerStartPolicy(ServerStartPolicy.IF_NEEDED);

    invokeStep();

    assertThat(getServerStartupInfo("ms1").getEnvironment(), contains(envVar("item1", "value1")));
  }

  @Test
  void withStartSpecifiedWhenWlsClusterNotInDomainSpec_dontAddServersToList() {
    startConfiguredServers();
    setDefaultReplicas(0);
    setCluster1Replicas(3);
    addWlsCluster("cluster2", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  void withStartNoneWhenWlsClusterNotInDomainSpec_dontAddServersToList() {
    startNoServers();
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  void withStartAdminWhenWlsClusterNotInDomainSpec_dontAddServersToList() {
    startAdminServerOnly();
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  void withStartAutoWhenWlsClusterNotInDomainSpec_addServersToListUpToReplicaLimit() {
    setDefaultServerStartPolicy(ServerStartPolicy.IF_NEEDED);
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("ms1", "ms2", "ms3"));
  }

  @Test
  void withStartAllWhenWlsClusterNotInDomainSpec_addClusteredServersToListUpWithoutLimit() {
    startAllServers();
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("ms1", "ms2", "ms3", "ms4", "ms5"));
    assertThat(getServerStartupInfo("ms4").getClusterName(), equalTo("cluster1"));
    assertThat(getServerStartupInfo("ms4").serverConfig, equalTo(getWlsServer("cluster1", "ms4")));
  }

  @Test
  void whenWlsClusterNotInDomainSpec_recordServerAndClusterConfigs() {
    setCluster1Replicas(3);
    addWlsServers("ms1", "ms2", "ms3", "ms4", "ms5");
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServerStartupInfo("ms1").serverConfig, equalTo(getWlsServer("cluster1", "ms1")));
    assertThat(getServerStartupInfo("ms1").getClusterName(), equalTo("cluster1"));
    assertThat(getServerStartupInfo("ms1").getEnvironment(), empty());
  }

  @Test
  void whenWlsClusterNotInDomainSpec_startUpToLimit() {
    setCluster1Replicas(3);
    addWlsServers("ms1", "ms2", "ms3", "ms4", "ms5");
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("ms1", "ms2", "ms3"));
    assertThat(info.getExpectedRunningServers(), containsInAnyOrder("ms1", "ms2", "ms3"));
  }

  @Test
  void withStartPolicyAlways_addNonManagedServers() {
    startAllServers();
    addWlsServer("ms1");

    invokeStep();

    assertThat(getServers(), hasItem("ms1"));
    assertThat(getServerStartupInfo("ms1").serverConfig, equalTo(getWlsServer("ms1")));
  }

  @Test
  void whenShuttingDownAtLeastOneServer_prependServerDownIteratorStep() {
    addServer(info, "server1");

    assertThat(firstCoreStep(createNextStep()),
        instanceOf(ServerDownIteratorStep.class));
  }

  @Test
  void whenShuttingDownAtLeastOneServer_prependClearCompleteConditionStep() {
    addServer(info, "server1");

    assertThat(firstNonEventStep(createNextStep()), instanceOf(ClearCompletedConditionSteps.class));
  }

  @Test
  void whenExclusionsSpecified_doNotAddToListOfServers() {
    addServer(info, "server1");
    addServer(info, "server2");
    addServer(info, "server3");
    addServer(info, ADMIN);

    assertStoppingServers(firstCoreStep(createNextStepWithout("server2")),
        "server1", "server3");
  }

  @Test
  void whenShuttingDown_allowAdminServerNameInListOfServers() {
    configurator.setShuttingDown(true);

    addServer(info, "server1");
    addServer(info, "server2");
    addServer(info, "server3");
    addServer(info, ADMIN);

    assertStoppingServers(firstCoreStep(createNextStepWithout("server2")),
        "server1", "server3", ADMIN);
  }

  @Test
  void whenShuttingDown_withNullWlsDomainConfig_ensureNoException() {
    configurator.setShuttingDown(true);

    assertThat(firstCoreStep(createNextStepWithNullWlsDomainConfig()),
        instanceOf(ClusterServicesStep.class));
  }

  @Test
  void whenClusterStartupDefinedWithPreCreateServerService_addAllToServers() {
    configureCluster("cluster1").withPrecreateServerService(true);
    addWlsCluster("cluster1", "ms1", "ms2");

    invokeStep();

    assertThat(TestStepFactory.getPreCreateServers(), allOf(hasItem("ms1"), hasItem("ms2")));
  }

  @Test
  void whenClusterStartupDefinedWithPreCreateServerService_adminServerDown_addAllToServers() {
    configureCluster("cluster1").withPrecreateServerService(true);
    addWlsCluster("cluster1", "ms1", "ms2");
    configureAdminServer().withServerStartPolicy(ServerStartPolicy.NEVER);

    invokeStep();

    assertThat(getPreCreateServers(), allOf(hasItem("ms1"), hasItem("ms2")));
  }

  @Test
  void whenClusterStartupDefinedWithPreCreateServerService_managedServerDown_addAllToServers() {
    configureCluster("cluster1").withPrecreateServerService(true)
        .withServerStartPolicy(ServerStartPolicy.NEVER);
    addWlsCluster("cluster1", "ms1", "ms2");

    invokeStep();

    assertThat(getPreCreateServers(), allOf(hasItem("ms1"), hasItem("ms2")));
  }

  @Test
  void whenClusterStartupDefinedWithPreCreateServerService_allServersDown_addNothingToServers() {
    configureCluster("cluster1").withPrecreateServerService(true)
        .withServerStartPolicy(ServerStartPolicy.NEVER);
    addWlsCluster("cluster1", "ms1", "ms2");
    configureAdminServer().withServerStartPolicy(ServerStartPolicy.NEVER);

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  void whenReplicasLessThanMinDynClusterSize_allowBelowMin_doNotChangeReplicaCount() {
    startNoServers();
    setCluster1Replicas(0);

    addDynamicWlsCluster("cluster1", 2, 5,"ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(0, equalTo(info.getReplicaCount("cluster1")));
  }

  @Test
  void whenReplicasMoreThanMinDynClusterSize_doNotChangeReplicaCount() {
    startNoServers();
    setCluster1Replicas(3);

    addDynamicWlsCluster("cluster1", 2, 5,"ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(3, equalTo(info.getReplicaCount("cluster1")));
  }

  @Test
  void whenReplicasExceedsMaxDynClusterSize_logMessage() {
    List<LogRecord> messages = new ArrayList<>();
    consoleHandlerMemento.withLogLevel(Level.WARNING)
        .collectLogMessages(messages, REPLICAS_EXCEEDS_TOTAL_CLUSTER_SERVER_COUNT);

    startNoServers();
    setCluster1Replicas(10);

    addDynamicWlsCluster("cluster1", 2, 5,"ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(messages, containsWarning(REPLICAS_EXCEEDS_TOTAL_CLUSTER_SERVER_COUNT));
  }

  @Test
  void withValidReplicas_noEventsCreated() {
    setCluster1Replicas(2);
    addDynamicWlsCluster("cluster1", 2, 5,"ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getEvents(), empty());
  }

  @Test
  void whenDomainTopologyIsMissing_noExceptionAndDontStartServers() {
    invokeStepWithoutDomainTopology();

    assertManagedServersUpStepNotCreated();
  }

  private static Step firstNonEventStep(Step step) {
    Step stepLocal = step;
    while (stepLocal instanceof EventHelper.CreateEventStep) {
      stepLocal = stepLocal.getNext();
    }

    return stepLocal;
  }

  private static Step firstCoreStep(Step step) {
    Step stepLocal = step;
    while (stepLocal instanceof EventHelper.CreateEventStep
        || stepLocal instanceof ClearCompletedConditionSteps) {
      stepLocal = stepLocal.getNext();
    }

    return stepLocal;
  }

  private void assertStoppingServers(Step step, String... servers) {
    assertThat(((ServerDownIteratorStep) step).getServersToStop(), containsInAnyOrder(servers));
  }

  private Step createNextStepWithout(String... serverNames) {
    return createNextStep(Arrays.asList(serverNames));
  }

  private Step createNextStep() {
    return createNextStep(Collections.emptyList());
  }

  private Step createNextStep(List<String> servers) {
    configSupport.setAdminServerName(ADMIN);
    WlsDomainConfig config = configSupport.createDomainConfig();
    ManagedServersUpStep.NextStepFactory factory = factoryMemento.getOriginalValue();
    ServersUpStepFactory serversUpStepFactory = new ServersUpStepFactory(config, info);
    List<DomainPresenceInfo.ServerShutdownInfo> ssi = new ArrayList<>();
    info.getServerPods().map(PodHelper::getPodServerName).collect(Collectors.toList())
            .forEach(s -> addShutdownServerInfo(s, servers, ssi));
    serversUpStepFactory.shutdownInfos.addAll(ssi);
    return factory.createServerStep(info, config, serversUpStepFactory, nextStep);
  }

  private Step createNextStepWithNullWlsDomainConfig() {
    configSupport.setAdminServerName(ADMIN);
    ManagedServersUpStep.NextStepFactory factory = factoryMemento.getOriginalValue();
    ServersUpStepFactory serversUpStepFactory = new ServersUpStepFactory(null, info);
    return factory.createServerStep(info, null, serversUpStepFactory, nextStep);
  }

  private void addShutdownServerInfo(String serverName, List<String> servers,
                                     List<DomainPresenceInfo.ServerShutdownInfo> ssi) {
    if (!serverName.equals(configSupport.createDomainConfig().getAdminServerName()) && !servers.contains(serverName)) {
      ssi.add(new DomainPresenceInfo.ServerShutdownInfo(serverName, null));
    }
  }

  private void addWlsServer(String serverName) {
    configSupport.addWlsServer(serverName);
  }

  private void setDefaultReplicas(int replicas) {
    configurator.withDefaultReplicaCount(replicas);
  }

  private void setCluster1Replicas(int replicas) {
    configurator.configureCluster(info, "cluster1").withReplicas(replicas);
  }

  private void configureServers(String... serverNames) {
    for (String serverName : serverNames) {
      configureServerToStart(serverName);
    }
  }

  private void addWlsServers(String... serverNames) {
    for (String serverName : serverNames) {
      addWlsServer(serverName);
    }
  }

  private void defineAdminServer() {
    configurator.configureAdminServer();
  }

  private WlsServerConfig getWlsServer(String serverName) {
    return configSupport.getWlsServer(serverName);
  }

  private WlsServerConfig getWlsServer(String clusterName, String serverName) {
    return configSupport.getWlsServer(clusterName, serverName);
  }

  private void configureServer(String serverName) {
    configurator.configureServer(serverName);
  }

  private AdminServerConfigurator configureAdminServer() {
    return configurator.configureAdminServer();
  }

  private ServerConfigurator configureServerToStart(String serverName) {
    ServerConfigurator serverConfigurator = configurator.configureServer(serverName);
    serverConfigurator.withServerStartPolicy(ServerStartPolicy.ALWAYS);
    return serverConfigurator;
  }

  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(info, clusterName).withReplicas(1);
  }

  private void assertManagedServersUpStepNotCreated() {
    assertThat(TestStepFactory.next, sameInstance(nextStep));
  }

  private void setDefaultServerStartPolicy(ServerStartPolicy startPolicy) {
    configurator.withDefaultServerStartPolicy(startPolicy);
  }

  private void invokeStep() {
    configSupport.setAdminServerName(ADMIN);

    testSupport.addToPacket(
        ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(step);
  }

  private void invokeStepWithoutDomainTopology() {
    configSupport.setAdminServerName(ADMIN);

    testSupport.runSteps(step);
  }

  static class TestStepFactory implements ManagedServersUpStep.NextStepFactory {
    private static DomainPresenceInfo info;

    @SuppressWarnings("unused")
    private static WlsDomainConfig config;

    private static Collection<String> servers;
    private static Collection<String> preCreateServers;
    private static Step next;
    @SuppressWarnings("FieldCanBeLocal")
    private static TestStepFactory factory = new TestStepFactory();

    private static Memento install() throws NoSuchFieldException {
      factory = new TestStepFactory();
      return StaticStubSupport.install(ManagedServersUpStep.class, "nextStepFactory", factory);
    }

    static Collection<String> getServers() {
      return servers;
    }

    static Collection<String> getPreCreateServers() {
      return preCreateServers;
    }

    static ServerStartupInfo getServerStartupInfo(String serverName) {
      for (ServerStartupInfo startupInfo : getServerStartupInfos()) {
        if (startupInfo.serverConfig.getName().equals(serverName)) {
          return startupInfo;
        }
      }
      return null;
    }

    private static Collection<ServerStartupInfo> getServerStartupInfos() {
      return Optional.ofNullable(info.getServerStartupInfo()).orElse(Collections.emptyList());
    }

    @Override
    public Step createServerStep(
            DomainPresenceInfo info, WlsDomainConfig config, ServersUpStepFactory factory, Step next) {
      TestStepFactory.info = info;
      TestStepFactory.config = config;
      TestStepFactory.servers = factory.servers;
      TestStepFactory.preCreateServers = factory.preCreateServers;
      TestStepFactory.next = next;
      return (next != null && next.getNext() instanceof CreateEventStep) ? next.getNext() : new TerminalStep();
    }
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

}
