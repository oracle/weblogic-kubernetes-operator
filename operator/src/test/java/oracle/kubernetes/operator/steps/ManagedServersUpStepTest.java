// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.operator.WebLogicConstants.ADMIN_STATE;
import static oracle.kubernetes.operator.steps.ManagedServersUpStep.SERVERS_UP_MSG;
import static oracle.kubernetes.operator.steps.ManagedServersUpStepTest.TestStepFactory.getServerStartupInfo;
import static oracle.kubernetes.operator.steps.ManagedServersUpStepTest.TestStepFactory.getServers;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the code to bring up managed servers. "Wls Servers" and "WLS Clusters" are those defined in
 * the admin server for the domain. There is also a kubernetes "Domain Spec," which specifies which
 * servers should be running.
 */
@SuppressWarnings({"ConstantConditions", "SameParameterValue"})
public class ManagedServersUpStepTest {

  private static final String DOMAIN = "domain";
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  private static final String ADMIN = "asName";
  private final Domain domain = createDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);

  private WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN);

  private Step nextStep = new TerminalStep();
  private FiberTestSupport testSupport = new FiberTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
  private ManagedServersUpStep step = new ManagedServersUpStep(nextStep);
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;
  private Memento factoryMemento;

  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(domain);
  }

  private Domain createDomain() {
    return new Domain().withMetadata(createMetaData()).withSpec(createDomainSpec());
  }

  private V1ObjectMeta createMetaData() {
    return new V1ObjectMeta().namespace(NS);
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUID(UID).withReplicas(1);
  }

  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(consoleHandlerMemento = TestUtils.silenceOperatorLogger());
    mementos.add(factoryMemento = TestStepFactory.install());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenEnabled_logCurrentServers() {
    List<LogRecord> messages = new ArrayList<>();
    consoleHandlerMemento.withLogLevel(Level.FINE).collectLogMessages(messages, SERVERS_UP_MSG);
    addRunningServer("admin");
    addRunningServer("ms1");
    addRunningServer("ms2");

    invokeStep();

    assertThat(messages, containsFine(SERVERS_UP_MSG));
  }

  private void addRunningServer(String serverName) {
    ServerKubernetesObjects sko = addServer(domainPresenceInfo, serverName);
    sko.getPod().set(new V1Pod());
  }

  private void addWlsCluster(String clusterName, String... serverNames) {
    configSupport.addWlsCluster(clusterName, serverNames);
  }

  @Test
  public void whenStartPolicyUndefined_startServers() {
    invokeStepWithConfiguredServer();

    assertServersToBeStarted();
  }

  private void invokeStepWithConfiguredServer() {
    configureServer("configured");
    addWlsServer("configured");
    invokeStep();
  }

  @Test
  public void whenStartPolicyIfNeeded_startServers() {
    setDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    invokeStepWithConfiguredServer();

    assertServersToBeStarted();
  }

  @Test
  public void whenStartPolicyAlways_startServers() {
    startAllServers();

    invokeStepWithConfiguredServer();

    assertServersToBeStarted();
  }

  private void startAllServers() {
    configurator.withDefaultServerStartPolicy(START_ALWAYS);
  }

  private void startConfiguredServers() {
    setDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);
  }

  private void assertServersToBeStarted() {
    assertThat(TestStepFactory.next, instanceOf(ManagedServerUpIteratorStep.class));
  }

  @Test
  public void whenStartPolicyAdminOnly_dontStartServers() {
    startAdminServerOnly();

    invokeStepWithConfiguredServer();

    assertServersWillNotBeStarted();
  }

  private void startAdminServerOnly() {
    configurator
        .withDefaultServerStartPolicy(START_NEVER)
        .configureAdminServer()
        .withServerStartPolicy(START_ALWAYS);
  }

  @Test
  public void whenNoServerStartRequested_dontStartServers() {
    startNoServers();

    invokeStepWithConfiguredServer();

    assertServersWillNotBeStarted();
  }

  private void startNoServers() {
    configurator.withDefaultServerStartPolicy(START_NEVER);
  }

  @Test
  public void whenWlsServerInDomainSpec_addToServerList() {
    configureServerToStart("wls1");
    addWlsServer("wls1");

    invokeStep();

    assertThat(getServers(), contains("wls1"));
  }

  @Test
  public void whenServerInDomainSpecButNotDefinedInWls_dontAddToServerList() {
    configureServerToStart("wls1");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  public void whenMultipleWlsServersInDomainSpec_addToServerList() {
    configureServers("wls1", "wls2", "wls3");
    addWlsServers("wls1", "wls2", "wls3");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("wls1", "wls2", "wls3"));
  }

  @Test
  public void whenMultipleWlsServersInDomainSpec_skipAdminServer() {
    defineAdminServer();
    configureServers("wls1", ADMIN, "wls3");
    addWlsServers("wls1", ADMIN, "wls3");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("wls1", "wls3"));
  }

  @Test
  public void whenWlsServersDuplicatedInDomainSpec_skipDuplicates() {
    defineAdminServer();
    configureServers("wls1", "wls1", "wls2");
    addWlsServers("wls1", "wls2");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("wls1", "wls2"));
  }

  @Test
  public void whenWlsServersInDomainSpec_addStartupInfo() {
    configureServerToStart("wls1");
    configureServerToStart("wls2");
    addWlsServers("wls1", "wls2");

    invokeStep();

    assertThat(getServerStartupInfo("wls1"), notNullValue());
    assertThat(getServerStartupInfo("wls2"), notNullValue());
  }

  @Test
  public void serverStartupInfo_containsEnvironmentVariable() {
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
  public void whenDesiredStateIsAdmin_serverStartupCreatesJavaOptionsEnvironment() {
    configureServerToStart("wls1").withDesiredState(ADMIN_STATE);
    addWlsServer("wls1");

    invokeStep();

    assertThat(
        getServerStartupInfo("wls1").getEnvironment(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN")));
  }

  @Test
  public void whenDesiredStateIsAdmin_serverStartupAddsToJavaOptionsEnvironment() {
    configureServerToStart("wls1")
        .withDesiredState(ADMIN_STATE)
        .withEnvironmentVariable("JAVA_OPTIONS", "value1");
    addWlsServer("wls1");

    invokeStep();

    assertThat(
        getServerStartupInfo("wls1").getEnvironment(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN value1")));
  }

  @Test
  public void whenWlsServerNotInCluster_serverStartupInfoHasNoClusterConfig() {
    configureServerToStart("wls1");
    addWlsServer("wls1");

    invokeStep();

    assertThat(getServerStartupInfo("wls1").getClusterName(), nullValue());
  }

  @Test
  public void whenWlsServerInCluster_serverStartupInfoHasMatchingClusterConfig() {
    configureServerToStart("ms1");

    addWlsCluster("cluster1", "ms1");
    addWlsCluster("cluster2");

    invokeStep();

    assertThat(getServerStartupInfo("ms1").getClusterName(), equalTo("cluster1"));
  }

  @Test
  public void whenClusterStartupDefinedForServerNotRunning_addToServers() {
    configureServerToStart("ms1");
    configureCluster("cluster1");
    addWlsCluster("cluster1", "ms1");

    invokeStep();

    assertThat(getServers(), hasItem("ms1"));
  }

  @Test
  public void whenServerStartupNotDefined_useEnvForCluster() {
    configureCluster("cluster1").withEnvironmentVariable("item1", "value1");
    addWlsCluster("cluster1", "ms1");

    configureCluster("cluster1").withServerStartPolicy(START_IF_NEEDED);

    invokeStep();

    assertThat(getServerStartupInfo("ms1").getEnvironment(), contains(envVar("item1", "value1")));
  }

  @Test
  public void whenClusterStartupDefinedWithAdminState_addAdminEnv() {
    configureCluster("cluster1")
        .withDesiredState(ADMIN_STATE)
        .withEnvironmentVariable("item1", "value1");
    addWlsCluster("cluster1", "ms1");

    configureCluster("cluster1").withServerStartPolicy(START_IF_NEEDED);

    invokeStep();

    assertThat(
        getServerStartupInfo("ms1").getEnvironment(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN")));
  }

  @Test
  public void withStartSpecifiedWhenWlsClusterNotInDomainSpec_dontAddServersToList() {
    startConfiguredServers();
    setDefaultReplicas(0);
    setCluster1Replicas(3);
    addWlsCluster("cluster2", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  public void withStartNoneWhenWlsClusterNotInDomainSpec_dontAddServersToList() {
    startNoServers();
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  public void withStartAdminWhenWlsClusterNotInDomainSpec_dontAddServersToList() {
    startAdminServerOnly();
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), empty());
  }

  @Test
  public void withStartAutoWhenWlsClusterNotInDomainSpec_addServersToListUpToReplicaLimit() {
    setDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("ms1", "ms2", "ms3"));
  }

  @Test
  public void withStartAllWhenWlsClusterNotInDomainSpec_addClusteredServersToListUpWithoutLimit() {
    startAllServers();
    setCluster1Replicas(3);
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("ms1", "ms2", "ms3", "ms4", "ms5"));
    assertThat(getServerStartupInfo("ms4").getClusterName(), equalTo("cluster1"));
    assertThat(getServerStartupInfo("ms4").serverConfig, equalTo(getWlsServer("cluster1", "ms4")));
  }

  @Test
  public void whenWlsClusterNotInDomainSpec_recordServerAndClusterConfigs() {
    setCluster1Replicas(3);
    addWlsServers("ms1", "ms2", "ms3", "ms4", "ms5");
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServerStartupInfo("ms1").serverConfig, equalTo(getWlsServer("cluster1", "ms1")));
    assertThat(getServerStartupInfo("ms1").getClusterName(), equalTo("cluster1"));
    assertThat(getServerStartupInfo("ms1").getEnvironment(), empty());
  }

  @Test
  public void whenWlsClusterNotInDomainSpec_startUpToLimit() {
    setCluster1Replicas(3);
    addWlsServers("ms1", "ms2", "ms3", "ms4", "ms5");
    addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5");

    invokeStep();

    assertThat(getServers(), containsInAnyOrder("ms1", "ms2", "ms3"));
  }

  @Test
  public void withStartPolicyAlways_addNonManagedServers() {
    startAllServers();
    addWlsServer("ms1");

    invokeStep();

    assertThat(getServers(), hasItem("ms1"));
    assertThat(getServerStartupInfo("ms1").serverConfig, equalTo(getWlsServer("ms1")));
  }

  @Test
  public void whenShuttingDown_insertCreateAvailableHookStep() {
    configurator.setShuttingDown(true);

    assertThat(createNextStep().getClass().getSimpleName(), equalTo("AvailableHookStep"));
  }

  @Test
  public void whenNotShuttingDown_dontInsertCreateAvailableHookStep() {
    configurator.setShuttingDown(false);

    assertThat(createNextStep(), instanceOf(ClusterServicesStep.class));
  }

  private static ServerKubernetesObjects addServer(
      DomainPresenceInfo domainPresenceInfo, String serverName) {
    return domainPresenceInfo
        .getServers()
        .computeIfAbsent(serverName, k -> new ServerKubernetesObjects());
  }

  @Test
  public void whenShuttingDownAtLeastOneServer_prependServerDownIteratorStep() {
    addServer(domainPresenceInfo, "server1");

    assertThat(createNextStep(), instanceOf(ServerDownIteratorStep.class));
  }

  @Test
  public void whenExclusionsSpecified_doNotAddToListOfServers() {
    addServer(domainPresenceInfo, "server1");
    addServer(domainPresenceInfo, "server2");
    addServer(domainPresenceInfo, "server3");
    addServer(domainPresenceInfo, ADMIN);

    assertStoppingServers(createNextStepWithout("server2"), "server1", "server3");
  }

  @Test
  public void whenShuttingDown_allowAdminServerNameInListOfServers() {
    configurator.setShuttingDown(true);

    addServer(domainPresenceInfo, "server1");
    addServer(domainPresenceInfo, "server2");
    addServer(domainPresenceInfo, "server3");
    addServer(domainPresenceInfo, ADMIN);

    assertStoppingServers(createNextStepWithout("server2"), "server1", "server3", ADMIN);
  }

  private void assertStoppingServers(Step step, String... servers) {
    assertThat(((ServerDownIteratorStep) step).getServersToStop(), containsInAnyOrder(servers));
  }

  private Step createNextStep() {
    return createNextStep(Collections.emptyList());
  }

  private Step createNextStepWithout(String... serverNames) {
    return createNextStep(Arrays.asList(serverNames));
  }

  private Step createNextStep(List<String> servers) {
    configSupport.setAdminServerName(ADMIN);
    WlsDomainConfig config = configSupport.createDomainConfig();
    ManagedServersUpStep.NextStepFactory factory = factoryMemento.getOriginalValue();
    return factory.createServerStep(domainPresenceInfo, config, servers, nextStep);
  }

  private void addWlsServer(String serverName) {
    configSupport.addWlsServer(serverName);
  }

  private void setDefaultReplicas(int replicas) {
    configurator.withDefaultReplicaCount(replicas);
  }

  private void setCluster1Replicas(int replicas) {
    configureCluster("cluster1").withReplicas(replicas);
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

  private ServerConfigurator configureServer(String serverName) {
    return configurator.configureServer(serverName);
  }

  private ServerConfigurator configureServerToStart(String serverName) {
    ServerConfigurator serverConfigurator = configurator.configureServer(serverName);
    serverConfigurator.withServerStartPolicy(START_ALWAYS);
    return serverConfigurator;
  }

  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(clusterName).withReplicas(1);
  }

  private void assertServersWillNotBeStarted() {
    assertThat(TestStepFactory.next, sameInstance(nextStep));
  }

  private void setDefaultServerStartPolicy(String startPolicy) {
    configurator.withDefaultServerStartPolicy(startPolicy);
  }

  private void invokeStep() {
    configSupport.setAdminServerName(ADMIN);

    testSupport.addToPacket(
        ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(step);
  }

  static class TestStepFactory implements ManagedServersUpStep.NextStepFactory {
    private static DomainPresenceInfo info;

    @SuppressWarnings("unused")
    private static WlsDomainConfig config;

    private static Collection<String> servers;
    private static Step next;
    private static TestStepFactory factory = new TestStepFactory();

    private static Memento install() throws NoSuchFieldException {
      factory = new TestStepFactory();
      return StaticStubSupport.install(ManagedServersUpStep.class, "NEXT_STEP_FACTORY", factory);
    }

    static Collection<String> getServers() {
      return servers;
    }

    static ServerStartupInfo getServerStartupInfo(String serverName) {
      for (ServerStartupInfo startupInfo : info.getServerStartupInfo()) {
        if (startupInfo.serverConfig.getName().equals(serverName)) return startupInfo;
      }

      return null;
    }

    @Override
    public Step createServerStep(
        DomainPresenceInfo info, WlsDomainConfig config, Collection<String> servers, Step next) {
      TestStepFactory.info = info;
      TestStepFactory.config = config;
      TestStepFactory.servers = servers;
      TestStepFactory.next = next;
      return new TerminalStep();
    }
  }
}
