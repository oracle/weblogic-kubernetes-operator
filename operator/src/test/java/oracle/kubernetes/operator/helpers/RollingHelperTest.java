// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.PodHelper.ManagedPodStepContext;
import oracle.kubernetes.operator.helpers.PodHelperTestBase.PassthroughPodAwaiterStepFactory;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_ROLL_START;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_POD_REPLACED;
import static oracle.kubernetes.common.logging.MessageKeys.ROLLING_SERVERS;
import static oracle.kubernetes.common.utils.LogMatcher.containsInOrder;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.POD_CYCLE_STARTING_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_ROLL_START_EVENT_GENERATED;
import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsEmptyCollection.empty;

class RollingHelperTest {

  private static final String ADMIN_SERVER = "ADMIN_SERVER";
  private static final Integer ADMIN_PORT = 7001;
  private static final String CLUSTER_NAME = "test-cluster";
  private static final int LISTEN_PORT = 8001;
  private static final String SERVER1_NAME = "ess_server1";
  private static final String SERVER2_NAME = "ess_server2";
  private static final String SERVER10_NAME = "ess_server10";
  private static final List<String> CLUSTERED_SERVER_NAMES = Arrays.asList(SERVER10_NAME, SERVER1_NAME, SERVER2_NAME);
  private static final String NONCLUSTERED_SERVER = "non_clustered";

  private final DomainResource domain = createTestDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  private final TerminalStep terminalStep = new TerminalStep();
  private final Map<String, StepAndPacket> rolling = new HashMap<>();

  protected final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  protected final List<Memento> mementos = new ArrayList<>();
  protected final List<LogRecord> logRecords = new ArrayList<>();
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  private WlsDomainConfig domainTopology;
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(consoleHandlerMemento = TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(UID);
    configSupport.addWlsServer(ADMIN_SERVER, ADMIN_PORT);
    configSupport.setAdminServerName(ADMIN_SERVER);
    CLUSTERED_SERVER_NAMES.forEach(s -> configSupport.addWlsServer(s, LISTEN_PORT));
    configSupport.addWlsCluster(CLUSTER_NAME, SERVER10_NAME, SERVER1_NAME, SERVER2_NAME);

    testSupport.defineResources(domain);
    domainTopology = configSupport.createDomainConfig();
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new PassthroughPodAwaiterStepFactory());

    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  private V1Pod createPod(Packet packet) {
    return new PodHelper.ManagedPodStepContext(null, packet).getPodModel();
  }

  private V1Pod createPodModel(String serverName) {
    testSupport
        .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainTopology)
        .addToPacket(SERVER_SCAN, getServerConfig(serverName))
        .addDomainPresenceInfo(domainPresenceInfo);
    return createPod(testSupport.getPacket());
  }

  private DomainPresenceInfo createDomainPresenceInfo(DomainResource domain) {
    return new DomainPresenceInfo(domain);
  }

  private Step.StepAndPacket createRollingStepAndPacket(String serverName) {
    return createRollingStepAndPacket(getServerPod(serverName), serverName);
  }

  private Step.StepAndPacket createRollingStepAndPacket(V1Pod serverPod, String serverName) {
    Packet packet = testSupport.getPacket().copy();
    Optional.ofNullable(serverName)
          .filter(this::isClustered)
          .ifPresent(s -> packet.put(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    packet.put(ProcessingConstants.SERVER_NAME, serverName);

    packet.put(SERVER_SCAN, getServerConfig(serverName));
    return new Step.StepAndPacket(createCyclePodStep(serverPod, packet), packet);
  }

  boolean isClustered(String serverName) {
    return CLUSTERED_SERVER_NAMES.contains(serverName);
  }

  private Step createCyclePodStep(V1Pod serverPod, Packet packet) {
    return new ManagedPodStepContext(null, packet).createCyclePodStep(serverPod, null);
  }

  private V1Pod getServerPod(String serverName) {
    return testSupport.getResourceWithName(KubernetesTestSupport.POD, LegalNames.toPodName(UID, serverName));
  }

  private void initializeExistingPods() {
    CLUSTERED_SERVER_NAMES.forEach(this::initializeExistingPod);
  }

  private void initializeExistingPod(String serverName) {
    V1Pod pod = createPodModel(serverName);

    testSupport.defineResources(pod);
    setPodStatusReady(pod);
    domainPresenceInfo.setServerPod(serverName, pod);
  }

  private void setPodStatusReady(V1Pod pod) {
    pod.setStatus(new V1PodStatus().phase("Running")
          .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
  }

  @Test
  void standaloneServers_areReplacedImmediately() {
    List<String> allServerNames = getAllServerNames();
    consoleHandlerMemento.trackMessage(MANAGED_POD_REPLACED);
    domainTopology.addWlsServer(NONCLUSTERED_SERVER, "host", LISTEN_PORT);
    allServerNames.forEach(this::initializeExistingPod);
    allServerNames.forEach(s -> rolling.put(s, createRollingStepAndPacket(s)));

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, containsInfo(MANAGED_POD_REPLACED).withParams(NONCLUSTERED_SERVER));
    logRecords.clear();
  }

  @Nonnull
  private List<String> getAllServerNames() {
    List<String> allServerNames = new ArrayList<>(Collections.singletonList(NONCLUSTERED_SERVER));
    allServerNames.addAll(CLUSTERED_SERVER_NAMES);
    return allServerNames;
  }

  @Test
  void whenNoClusterSizeSet_serverPodsAreReplacedInOrder() {
    consoleHandlerMemento.trackMessage(MANAGED_POD_REPLACED);
    initializeExistingPods();
    CLUSTERED_SERVER_NAMES.forEach(s -> rolling.put(s, createRollingStepAndPacket(s)));

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, containsInOrder(
        containsInfo(MANAGED_POD_REPLACED).withParams(SERVER1_NAME),
        containsInfo(MANAGED_POD_REPLACED).withParams(SERVER2_NAME),
        containsInfo(MANAGED_POD_REPLACED).withParams(SERVER10_NAME)
    ));
  }

  @Test
  void whenClusterSizeSet_onlyOnePodImmediatelyReplaced() {
    consoleHandlerMemento.trackMessage(MANAGED_POD_REPLACED);
    initializeExistingPods();
    CLUSTERED_SERVER_NAMES.forEach(s -> rolling.put(s, createRollingStepAndPacket(s)));
    configureDomain().configureCluster(domainPresenceInfo, CLUSTER_NAME).withReplicas(3);

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, containsInfo(MANAGED_POD_REPLACED).withParams(SERVER1_NAME));
  }

  @Test
  void whenRollSpecificClusterStep_apply_calledAgainWithSameServers_onlyOneRollMessageLogged() {
    consoleHandlerMemento.trackMessage(ROLLING_SERVERS);
    initializeExistingPods();
    CLUSTERED_SERVER_NAMES.forEach(s -> rolling.put(s, createRollingStepAndPacket(s)));
    configureDomain().configureCluster(domainPresenceInfo, CLUSTER_NAME).withReplicas(3);

    ConcurrentLinkedQueue<StepAndPacket> stepAndPackets = new ConcurrentLinkedQueue<>(rolling.values());
    Step rollSpecificClusterStep = new RollingHelper.RollSpecificClusterStep(CLUSTER_NAME, stepAndPackets);

    rollSpecificClusterStep.apply(testSupport.getPacket());

    stepAndPackets.clear();
    stepAndPackets.addAll(rolling.values());

    rollSpecificClusterStep.apply(testSupport.getPacket());

    assertThat(logRecords.size(), is(1));
    assertThat(logRecords, containsInfo(ROLLING_SERVERS));
  }

  @Test
  void whenClusterSizeSetAndServersNotReady_replaceAllPodsImmediately() {
    consoleHandlerMemento.trackMessage(MANAGED_POD_REPLACED);
    initializeExistingPods();
    CLUSTERED_SERVER_NAMES.forEach(s -> rolling.put(s, createRollingStepAndPacket(s)));
    getPods().forEach(this::setPodNotReady);

    configureDomain().configureCluster(domainPresenceInfo, CLUSTER_NAME).withReplicas(3);

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, containsInOrder(
        containsInfo(MANAGED_POD_REPLACED).withParams(SERVER1_NAME),
        containsInfo(MANAGED_POD_REPLACED).withParams(SERVER2_NAME),
        containsInfo(MANAGED_POD_REPLACED).withParams(SERVER10_NAME)
    ));
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  private List<V1Pod> getPods() {
    return testSupport.getResources(KubernetesTestSupport.POD);
  }

  private void setPodNotReady(V1Pod pod) {
    pod.setStatus(new V1PodStatus().phase("Running")
          .addConditionsItem(new V1PodCondition().type("Ready").status("False")));
  }

  @Test
  void whenRollingIsEmpty_NoManagedServerPodsAreReplaced() {
    initializeExistingPods();

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, empty());
  }

  @Test
  void whenNoPodsToRoll_dontLogRollStarted() {
    initializeExistingPods();
    testSupport.addToPacket(SERVERS_TO_ROLL, rolling);

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, not(containsInfo(DOMAIN_ROLL_START)));
  }

  @Test
  void whenPacketHasPodsToRoll_podsToRollCleared() {
    initializeExistingPods();
    CLUSTERED_SERVER_NAMES.forEach(s -> rolling.put(s, createRollingStepAndPacket(s)));
    getPods().forEach(this::setPodNotReady);
    testSupport.addToPacket(SERVERS_TO_ROLL, rolling);
    DomainPresenceInfo.fromPacket(testSupport.getPacket()).ifPresent(dpi -> dpi.setServersToRoll(rolling));
    configureDomain().configureCluster(domainPresenceInfo, CLUSTER_NAME).withReplicas(3);

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(serversMarkedForRoll(testSupport.getPacket()), anEmptyMap());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Step.StepAndPacket> serversMarkedForRoll(Packet packet) {
    return DomainPresenceInfo.fromPacket(packet)
        .map(DomainPresenceInfo::getServersToRoll)
        .orElse(Collections.EMPTY_MAP);
  }

  private WlsServerConfig getServerConfig(String serverName) {
    return domainTopology.getServerConfig(serverName);
  }

  @Test
  void whenPodsNeededToRoll_createDomainCycleStartEvent() {
    initializeExistingPods();
    configureDomain().withRestartVersion("123");
    CLUSTERED_SERVER_NAMES.forEach(s -> rolling.put(s, createRollingStepAndPacket(createPodModel(s), s)));

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(testSupport, hasEvent(DOMAIN_ROLL_STARTING_EVENT).inNamespace(NS).withMessageContaining(UID));
  }

  @Test
  void whenDomainHomeAndRestartVersionChanged_podCycleEventCreatedWithCorrectMessage() {
    initializeExistingPods();
    testSupport.addToPacket(DOMAIN_ROLL_START_EVENT_GENERATED, "true");
    CLUSTERED_SERVER_NAMES.forEach(s ->
        rolling.put(s, createRollingStepAndPacket(
            modifyDomainHome(modifyRestartVersion(createPodModel(s), "V5"), "xxxx"), s)));

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));
    logRecords.clear();

    CLUSTERED_SERVER_NAMES.forEach(s ->
          assertThat(testSupport,
                hasEvent(POD_CYCLE_STARTING_EVENT).inNamespace(NS).withMessageContaining(getPodName(s))));
  }

  private String getPodName(String s) {
    return getPodNameFromMetadata(domainPresenceInfo.getServerPod(s));
  }

  private String getPodNameFromMetadata(V1Pod serverPod) {
    return Optional.ofNullable(serverPod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
  }

  @SuppressWarnings({"SameParameterValue", "ConstantConditions"})
  private V1Pod modifyRestartVersion(V1Pod pod, String restartVersion) {
    setPodStatusReady(pod);
    pod.getMetadata().getLabels().remove(LabelConstants.DOMAINRESTARTVERSION_LABEL);
    pod.getMetadata().getLabels().put(LabelConstants.DOMAINRESTARTVERSION_LABEL, restartVersion);
    return pod;
  }

  @SuppressWarnings("SameParameterValue")
  private V1Pod modifyDomainHome(V1Pod pod, String domainHome) {
    setPodStatusReady(pod);
    for (V1EnvVar env : getEnvList(pod)) {
      if (env.getName().equals("DOMAIN_HOME")) {
        env.setValue(domainHome);
        return pod;
      }
    }
    return pod;
  }

  @Nonnull
  private List<V1EnvVar> getEnvList(V1Pod pod) {
    return Optional.ofNullable(pod.getSpec())
          .map(V1PodSpec::getContainers)
          .map(this::getFirst)
          .map(V1Container::getEnv)
          .orElse(Collections.emptyList());
  }

  private <T> T getFirst(List<T> list) {
    return list.get(0);
  }

}
