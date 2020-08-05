// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.steps.ManagedServerUpIteratorStep.StartManagedServersStep;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.steps.ManagedServerUpIteratorStepTest.TestStepFactory.getServers;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ManagedServerUpIteratorStepTest {

  private static final String DOMAIN = "domain";
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  private static final String ADMIN = "asName";
  private static final String CLUSTER = "cluster1";
  private static final String NON_CLUSTERED = "NonClustered";
  private final Domain domain = createDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN);

  private Step nextStep = new TerminalStep();
  private FiberTestSupport testSupport = new FiberTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfoServers = createDomainPresenceInfoWithServers();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  private DomainPresenceInfo createDomainPresenceInfoWithServers() {
    DomainPresenceInfo dpi = new DomainPresenceInfo(domain);
    addServer(dpi, "admin");
    addServer(dpi, "ms1");
    addServer(dpi, "ms2");
    return dpi;
  }

  private Domain createDomain() {
    return new Domain().withMetadata(createMetaData()).withSpec(createDomainSpec());
  }

  private V1ObjectMeta createMetaData() {
    return new V1ObjectMeta().namespace(NS);
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUid(UID).withReplicas(1);
  }

  private static void addServer(DomainPresenceInfo domainPresenceInfo, String serverName) {
    if (serverName.equals("admin")) {
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

  /**
   * Setup env for tests.
   * @throws NoSuchFieldException if TestStepFactory fails to install
   */
  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(consoleHandlerMemento = TestUtils.silenceOperatorLogger());
    mementos.add(TestStepFactory.install());
    testSupport.addDomainPresenceInfo(createDomainPresenceInfoWithServers());
  }

  /**
   * Cleanup env after tests.
   * @throws Exception if test support failed
   */
  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void withConcurrencyOf1_bothClusteredServersStartSequentially() {
    configureCluster(CLUSTER).withMaxConcurrentStartup(1);
    addWlsCluster(CLUSTER, "ms1", "ms2");

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER,"ms1", "ms2"));

    assertThat(getServers(), hasItem("ms2"));
    assertThat(getServers().size(), equalTo(1));
  }

  @Test
  public void withConcurrencyOf0_bothClusteredServersStartConcurrently() {
    TestStepFactory.initializeStepMap();
    testSupport.addDomainPresenceInfo(createDomainPresenceInfoWithServers());
    configureCluster(CLUSTER).withMaxConcurrentStartup(0);
    addWlsCluster(CLUSTER, "ms1", "ms2");

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER,"ms1", "ms2"));

    assertThat(getServers().size(), equalTo(0));
  }

  @Test
  public void withConcurrencyOf2_bothClusteredServersStartConcurrently() {
    TestStepFactory.initializeStepMap();
    configureCluster(CLUSTER).withMaxConcurrentStartup(2);
    addWlsCluster(CLUSTER, "ms1", "ms2");

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER, "ms1", "ms2"));

    assertThat(getServers().size(), equalTo(0));
  }

  @Test
  public void withConcurrencyOf2_4clusteredServersStartIn2Threads() {
    configureCluster(CLUSTER).withMaxConcurrentStartup(2);
    addWlsCluster(CLUSTER, "ms1", "ms2", "ms3", "ms4");

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER, "ms1", "ms2", "ms3", "ms4"));

    //assertThat(getServers(), hasItem(Arrays.asList("ms1", "ms2", "ms3", "ms4")));
    //testSupport.setTime(RECHECK_SECONDS, TimeUnit.SECONDS);
    assertThat(getServers(), allOf(hasItem("ms3"), hasItem("ms4")));
    assertThat(getServers().size(), equalTo(2));
  }

  @Test
  public void withMultipleClusters_differentClusterStartDifferently() {
    final String CLUSTER2 = "cluster2";
    TestStepFactory.initializeStepMap();
    testSupport.addDomainPresenceInfo(createDomainPresenceInfoWithServers());

    configureCluster(CLUSTER).withMaxConcurrentStartup(1);
    configureCluster(CLUSTER2).withMaxConcurrentStartup(0);

    addWlsCluster(CLUSTER, "ms1", "ms2");
    addWlsCluster(CLUSTER2, "ms3", "ms4");

    Collection<ServerStartupInfo> serverStartupInfos = createServerStartupInfosForCluster(CLUSTER, "ms1", "ms2");
    serverStartupInfos.addAll(createServerStartupInfosForCluster(CLUSTER2, "ms3", "ms4"));
    invokeStepWithServerStartupInfos(serverStartupInfos);

    assertThat(getServers(), hasItem("ms2"));
    assertThat(getServers(CLUSTER2).size(), equalTo(0));
  }

  @Test
  public void maxClusterConcurrentStartup_doesNotApplyToNonClusteredServers() {
    TestStepFactory.initializeStepMap();
    domain.getSpec().setMaxClusterConcurrentStartup(1);

    addWlsServers("ms3", "ms4");

    invokeStepWithServerStartupInfos(createServerStartupInfos("ms3", "ms4"));

    assertThat(getServers(NON_CLUSTERED).size(), equalTo(0));
  }

  @NotNull
  private Collection<ServerStartupInfo> createServerStartupInfosForCluster(String clusterName, String... servers) {
    Collection<ServerStartupInfo> serverStartupInfos = new ArrayList<>();
    Arrays.asList(servers).stream().forEach(server ->
            serverStartupInfos.add(
                new ServerStartupInfo(configSupport.getWlsServer(clusterName, server),
                    clusterName,
                    domain.getServer(server, clusterName))
            )
    );
    return serverStartupInfos;
  }

  @NotNull
  private Collection<ServerStartupInfo> createServerStartupInfos(String... servers) {
    Collection<ServerStartupInfo> serverStartupInfos = new ArrayList<>();
    Arrays.asList(servers).stream().forEach(server ->
        serverStartupInfos.add(
            new ServerStartupInfo(configSupport.getWlsServer(server),
                null,
                domain.getServer(server, null))
        )
    );
    return serverStartupInfos;
  }

  private void invokeStepWithServerStartupInfos(Collection<ServerStartupInfo> startupInfos) {
    ManagedServerUpIteratorStep step = new ManagedServerUpIteratorStep(startupInfos, nextStep);
    // configSupport.setAdminServerName(ADMIN);

    testSupport.addToPacket(
        ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(step);
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(clusterName);
  }

  private void addWlsServers(String... serverNames) {
    Arrays.asList(serverNames).forEach(serverName -> addWlsServer(serverName));
  }

  private void addWlsServer(String serverName) {
    configSupport.addWlsServer(serverName);
  }

  private void addWlsCluster(String clusterName, String... serverNames) {
    configSupport.addWlsCluster(clusterName, serverNames);
  }

  static class TestStepFactory implements ManagedServerUpIteratorStep.NextStepFactory {

    private static TestStepFactory factory = new TestStepFactory();
    private static int staticServerCount = 0;
    private static Map<String,Step> nextMap = new ConcurrentHashMap<>();

    static void initializeStepMap() {
      initializeStepMap(0);
    }

    static void initializeStepMap(int serverCount) {
      staticServerCount = serverCount;
      nextMap.clear();
    }

    private static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(ManagedServerUpIteratorStep.class, "NEXT_STEP_FACTORY",
              TestStepFactory.factory);
    }

    static Collection<Object> getServers() {
      return getServers(CLUSTER);
    }

    static Collection<Object> getServers(String clusterName) {
      Step next = nextMap.get(clusterName);
      if (next instanceof StartManagedServersStep) {
        return ((StartManagedServersStep)next).getStartDetails()
                .stream()
                .map(serverToStart -> getServerFromStepAndPacket(serverToStart)).collect(Collectors.toList());
      }
      return Collections.emptyList();
    }

    static Object getServerFromStepAndPacket(StepAndPacket startDetail) {
      //if (startDetail.step instanceof StartClusteredServersStep) {
      if (startDetail.step instanceof StartManagedServersStep) {
        Collection<StepAndPacket> serversToStart = ((StartManagedServersStep)startDetail.step).getServersToStart();
        return serversToStart.stream().map(serverToStart -> getServerFromStepAndPacket(serverToStart))
                .collect(Collectors.toList());
      }
      return startDetail.packet.get(ProcessingConstants.SERVER_NAME);
    }

    @Override
    public Step startClusteredServersStep(Step step, Packet packet, Collection<StepAndPacket> serverDetails) {
      if (step instanceof StartManagedServersStep) {
        String clusterName = Optional.ofNullable(
                ((StartManagedServersStep)step).getClusterName()).orElse(NON_CLUSTERED);
        TestStepFactory.nextMap.put(clusterName, step);
      }
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      staticServerCount++;
      PodHelper.schedulePods(info, "ms" + staticServerCount);
      //PodHelper.makePodsReady(info, "ms" + serverCount);
      return step;
    }
  }

}
