// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.RetryStrategyStub;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterCondition;
import oracle.kubernetes.weblogic.domain.model.ClusterConditionType;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_NOT_READY;
import static oracle.kubernetes.common.logging.MessageKeys.NON_CLUSTERED_SERVERS_NOT_READY;
import static oracle.kubernetes.common.logging.MessageKeys.NO_APPLICATION_SERVERS_READY;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_POD_EVENT_ERROR;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdateTestBase.ClusterStatusMatcher.hasStatusForCluster;
import static oracle.kubernetes.operator.DomainStatusUpdateTestBase.EventMatcher.eventWithReason;
import static oracle.kubernetes.operator.DomainStatusUpdateTestBase.ServerStatusMatcher.hasStatusForServer;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTTING_DOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.STANDBY_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.STARTING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.UNKNOWN_STATE;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN_STATUS;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.FALSE;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.TRUE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.COMPLETED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.CONFIG_CHANGES_PENDING_RESTART;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ROLLING;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.SERVER_POD;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

abstract class DomainStatusUpdateTestBase {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String NAME = UID;
  private static final String ADMIN = "admin";
  private static final String CLUSTER = "cluster1";
  private static final String IMAGE = "initialImage:0";
  private final TerminalStep endStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private List<String> liveServers;
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(SystemClockTestSupport.installClock());

    domain.getSpec().setImage(IMAGE);
    domain.setStatus(new DomainStatus());
    info.setAdminServerName(ADMIN);

    testSupport.addDomainPresenceInfo(info);
    testSupport.defineResources(domain);
    defineScenario().withServers("server1", "server2").build();
  }

  private V1ObjectMeta createPodMetadata(String serverName) {
    return new V1ObjectMeta().namespace(NS).name(serverName).creationTimestamp(SystemClock.now());
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  abstract void processTopology(WlsDomainConfig domainConfig);

  void addTopologyToPacket(WlsDomainConfig domainConfig) {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
  }

  void initializeDomainStatus(WlsDomainConfig domainConfig) {
    final DomainStatusUpdater.DomainStatusFactory domainStatusFactory
          = new DomainStatusUpdater.DomainStatusFactory(info, domainConfig,
              this::isConfiguredToRun);

    domainStatusFactory.setStatusDetails(info.getDomain().getOrCreateStatus());
  }

  boolean isConfiguredToRun(String serverName) {
    return liveServers.contains(serverName);
  }

  @Test
  void statusStep_copiesServerStatesFromMaps() {   
    defineScenario()
          .withServers("server1")
          .withCluster("clusterB", "server2")
          .withServersReachingState(SHUTDOWN_STATE, "server2")
          .build();

    updateDomainStatus();

    // Observed generation is only updated during a make-right status update
    assertThat(getRecordedDomain().getStatus().getObservedGeneration(), nullValue());
    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withStateGoal(RUNNING_STATE)
                .withNodeName("node1")
                .withServerName("server1")
                .withPodPhase("Running")
                .withPodReady("True")
                .withHealth(overallHealth("health1"))));
    assertThat(
        getServerStatus(getRecordedDomain(), "server2"),
        equalTo(
            new ServerStatus()
                .withState(SHUTDOWN_STATE)
                .withStateGoal(RUNNING_STATE)
                .withClusterName("clusterB")
                .withNodeName("node2")
                .withServerName("server2")
                .withPodPhase("Running")
                .withPodReady("True")
                .withHealth(overallHealth("health2"))));
  }

  private void updateDomainStatus() {
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));
  }

  private void updateDomainStatusInEndOfProcessing() {
    testSupport.runSteps(DomainStatusUpdater.createLastStatusUpdateStep(endStep));
  }

  // Examines the domain status and returns the server status for the specified server, if it is defined
  private ServerStatus getServerStatus(DomainResource domain, String serverName) {
    return domain.getStatus().getServers().stream()
          .filter(status -> status.getServerName().equals(serverName))
          .findAny()
          .orElse(null);
  }

  private ServerHealth overallHealth(String health) {
    return new ServerHealth().withOverallHealth(health);
  }

  private V1Pod getPod(String serverName) {
    return info.getServerPod(serverName);
  }

  @Test
  void statusStep_usesServerFromWlsConfig() {
    defineScenario()
          .withCluster("clusterC", "server3", "server4")
          .notStarting("server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(),
          hasStatusForServer("server3")
                .withState(RUNNING_STATE)
                .withStateGoal(RUNNING_STATE)
                .withClusterName("clusterC"));
    assertThat(getRecordedDomain(),
          hasStatusForServer("server4")
                .withState(SHUTDOWN_STATE)
                .withStateGoal(SHUTDOWN_STATE)
                .withClusterName("clusterC"));
  }

  @Test
  void statusStep_definesClusterFromWlsConfig() {
    defineScenario()
          .withCluster("clusterA", "server3", "server4")
          .withDynamicCluster("clusterB", 0, 8)
          .notStarting("server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(),
          hasStatusForCluster("clusterA").withMinimumReplicas(0).withMaximumReplicas(2));
    assertThat(getRecordedDomain(),
          hasStatusForCluster("clusterB").withMinimumReplicas(0).withMaximumReplicas(8));
  }

  @Test
  void statusStep_definesClusterReplicaGoalFromDomain() {
    configureDomain().configureCluster(info, "clusterA").withReplicas(3);
    configureDomain().configureCluster(info,"clusterB").withReplicas(5);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withDynamicCluster("clusterB", 2, 8)
          .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasStatusForCluster("clusterA").withReplicasGoal(3));
    assertThat(getRecordedDomain(), hasStatusForCluster("clusterB").withReplicasGoal(5));
  }

  @Test
  void statusStep_copiesClusterFromWlsConfigAndNodeNameFromPod() {
    defineScenario()
          .withCluster("wlsCluster", "server2")
          .withServersReachingState(STANDBY_STATE, "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasStatusForServer("server2").withClusterName("wlsCluster").withNodeName("node2"));
  }

  @Test
  void statusStep_updatesDomainWhenHadNoStatus() {
    domain.setStatus(null);
    defineScenario()
          .withCluster("clusterA", "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasStatusForServer("server1").withClusterName("clusterA"));
  }

  @Test
  void statusStepWhenInfoHasNullDomain_dontUpdatesDomainStatus() {
    defineScenario()
        .withCluster("clusterA", "server1")
        .build();
    domain.setStatus(null);
    info.setDomain(null);

    updateDomainStatus();

    assertThat(getRecordedDomain().getStatus(), equalTo(null));
  }

  @Test
  void whenServerIntentionallyNotStarted_reportItsStateAsShutdown() {    
    defineScenario().withServers("server1").notStarting("server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(),
          hasStatusForServer("server1").withState(SHUTDOWN_STATE).withStateGoal(SHUTDOWN_STATE));
  }

  @Test
  void whenStatusUnchanged_statusStepDoesNotUpdateDomain() {   
    defineScenario().withServers("server1").notStarting("server1").build();
    domain.setStatus(
        new DomainStatus()
            .withServers(
                List.of(
                    new ServerStatus()
                        .withState(RUNNING_STATE)
                        .withStateGoal(RUNNING_STATE)
                        .withServerName("admin")
                        .withNodeName("node")
                        .withIsAdminServer(true)
                        .withPodPhase("Running")
                        .withPodReady("True")
                        .withHealth(overallHealth("health")),
                    new ServerStatus()
                        .withState(SHUTDOWN_STATE)
                        .withStateGoal(SHUTDOWN_STATE)
                        .withServerName("server1")
                        .withHealth(overallHealth("health1"))))
              .addCondition(new DomainCondition(AVAILABLE).withStatus(true))
              .addCondition(new DomainCondition(COMPLETED).withStatus(true)));

    testSupport.clearNumCalls();
    updateDomainStatus();

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  void whenDomainHasNoClusters_statusLacksReplicaCount() {   
    updateDomainStatus();

    assertThat(getRecordedDomain().getStatus().getReplicas(), nullValue());
  }

  @Test
  void whenDomainHasOneCluster_statusReplicaCountShowsServersInThatCluster() {  
    defineScenario()
          .withCluster("cluster1", "server1", "server2", "server3")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain().getStatus().getReplicas(), equalTo(3));
  }

  @Test
  void whenDomainHasMultipleClusters_perClusterReplicaCountsAreDefined() {
    defineScenario()
          .withCluster("cluster1", "server1", "server2", "server3")
          .withCluster("cluster2", "server4", "server5")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasStatusForCluster("cluster1").withReplicas(3));
    assertThat(getRecordedDomain(), hasStatusForCluster("cluster2").withReplicas(2));
  }

  @Test
  void whenDomainHasMultipleClusters_perClusterReadyReplicaCountsAreDefined() {
    defineScenario()
          .withCluster("cluster1", "server1", "server2", "server3")
          .withCluster("cluster2", "server4", "server5")
          .notStarting("server2", "server5")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasStatusForCluster("cluster1").withReadyReplicas(2));
    assertThat(getRecordedDomain(), hasStatusForCluster("cluster2").withReadyReplicas(1));
  }

  @Test
  void whenAllServerPodsInClusterAreBeingTerminated_ClusterStatusDoesNotShowReadyReplicaCount() {  
    defineScenario()
          .withCluster("cluster1", "server1", "server2", "server3")
          .terminating("server1", "server2", "server3")
          .build();

    updateDomainStatus();

    assertThat(getClusterStatus().getReadyReplicas(), nullValue());
  }

  private ClusterStatus getClusterStatus() {
    return getRecordedDomain().getStatus().getClusters().stream().findFirst().orElse(null);
  }

  @Test
  void whenAllServerPodsInClusterAreTerminated_ClusterStatusDoesNotShowReadyReplicaCount() {  
    defineScenario()
            .withCluster("cluster1", "server1", "server2", "server3")
            .build();

    deleteServerPodsInCluster();
    updateDomainStatus();

    assertThat(getClusterStatus().getReadyReplicas(), nullValue());
  }

  private void deleteServerPodsInCluster() {
    info.setServerPod("server1", null);
    info.setServerPod("server2", null);
    info.setServerPod("server3", null);
    info.setServerStartupInfo(Collections.emptyList());
  }

  @Test
  void whenAllServerPodsInClusterAreBeingTerminated_StatusShowsServersShuttingDown() {
    defineScenario()
          .withCluster(CLUSTER, "server1", "server2", "server3")
          .terminating("server1", "server2", "server3")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasStatusForServer("server1").withState(SHUTTING_DOWN_STATE));
    assertThat(getRecordedDomain(), hasStatusForServer("server2").withState(SHUTTING_DOWN_STATE));
    assertThat(getRecordedDomain(), hasStatusForServer("server3").withState(SHUTTING_DOWN_STATE));
  }

  @Test
  void whenAllServerPodsInClusterAreTerminated_StatusShowsServersShutDown() {  
    defineScenario()
            .withCluster("cluster1", "server1", "server2", "server3")
            .build();

    deleteServerPodsInCluster();
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasStatusForServer("server1").withState(SHUTDOWN_STATE));
    assertThat(getRecordedDomain(), hasStatusForServer("server2").withState(SHUTDOWN_STATE));
    assertThat(getRecordedDomain(), hasStatusForServer("server3").withState(SHUTDOWN_STATE));
  }

  @Test
  void whenDomainHasMultipleClusters_statusLacksReplicaCount() {  
    defineScenario()
          .withCluster("cluster1", "server1", "server2", "server3")
          .withCluster("cluster2", "server4", "server5", "server6", "server7")
          .withCluster("cluster3", "server8", "server9")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain().getStatus().getReplicas(), nullValue());
  }

  @Test
  void whenNoServersRunning_establishCompletedConditionFalse() {  
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(SHUTDOWN_STATE, "server1", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenNoServersReady_establishAvailableConditionFalse() {
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(SHUTDOWN_STATE, "server1", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE)
                                         .withMessageContaining(LOGGER.formatMessage(NO_APPLICATION_SERVERS_READY)));
  }

  @Test
  void withoutAClusterWhenAllDesiredServersRunning_establishCompletedConditionTrue() {  

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void withAClusterWhenAllDesiredServersRunningAndNoClusters_establishCompletedConditionTrue() {  
    defineScenario().withCluster("cluster1", "ms1", "ms2", "ms3").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void whenAllDesiredServersRunningAndNoClusters_removeRollingStatus() {
    defineScenario().withCluster("cluster1", "ms1", "ms2", "ms3").build();
    domain.getOrCreateStatus().addCondition(new DomainCondition(ROLLING));

    updateDomainStatus();

    assertThat(getRecordedDomain().getStatus().isRolling(), is(false));
  }

  @Test
  void whenAnyServerLacksReadyState_establishCompletedConditionFalse() {
    defineScenario().withCluster("cluster1", "ms1", "ms2", "ms3").build();
    deactivateServer("ms1");
    deactivateServer("ms2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  private void deactivateServer(String serverName) {
    Optional.of(getPod(serverName))
          .map(V1Pod::getStatus)
          .map(V1PodStatus::getConditions).orElse(Collections.emptyList()).stream()
          .filter(this::isReadyTrue)
          .forEach(this::setNotReady);
  }

  private boolean isReadyTrue(V1PodCondition condition) {
    return "Ready".equals(condition.getType()) && "True".equals(condition.getStatus());
  }

  private void setNotReady(V1PodCondition condition) {
    condition.status("False");
  }

  @Test
  void whenAnyServerHasRollNeededLabel_establishCompletedConditionFalse() {
    defineScenario().withCluster("cluster1", "ms1", "ms2", "ms3").build();
    addRollNeededLabel("ms1");
    addRollNeededLabel("ms2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @SuppressWarnings("ConstantConditions")
  private void addRollNeededLabel(String serverName) {
    info.getServerPod(serverName).getMetadata().getLabels().put(LabelConstants.TO_BE_ROLLED_LABEL, "true");
  }

  @Test
  void whenNoServerHasRollNeededLabel_establishCompletedConditionTrue() {
    defineScenario().withCluster("cluster1", "ms1", "ms2", "ms3").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void whenAllDesiredServersRunningAndMatchingCompletedConditionFound_leaveIt() {  
    domain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(true));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void whenAllDesiredServersRunningAndMismatchedCompletedConditionStatusFound_changeIt() {  
    domain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(false));

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void whenAllDesiredServersRunningButSomeMarkedToBeRolled_establishCompletedConditionFalse() {  
    info.setServersToRoll(Map.of("server1", new Step.StepAndPacket(null, null)));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenAllDesiredServersRunningAndMatchingCompletedConditionFound_dontGenerateCompletedEvent() {  
    domain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(TRUE));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(testSupport, not(hasEvent(DOMAIN_ROLL_COMPLETED_EVENT)));
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(EVENT);
  }

  @Test
  void whenAllDesiredServersRunningAndNoMatchingCompletedConditionFound_generateCompletedEvent() {
    domain.getStatus()
          .addCondition(new DomainCondition(COMPLETED).withStatus(FALSE));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(testSupport, hasEvent(DOMAIN_COMPLETED_EVENT));
  }

  @Test
  void whenRollInProcessAndSomeServersNotRunning_dontGenerateDomainRollCompletedEvent() {
    domain.getStatus().addCondition(new DomainCondition(ROLLING));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();
    deactivateServer("server1");

    updateDomainStatus();

    assertThat(testSupport, not(hasEvent(DOMAIN_ROLL_COMPLETED_EVENT)));
  }

  @Test
  void whenRollInProcessAndAllServersRunning_generateDomainRollCompletedEvent() {
    domain.getStatus().addCondition(new DomainCondition(ROLLING));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(testSupport, hasEvent(DOMAIN_ROLL_COMPLETED_EVENT).inNamespace(NS).withMessageContaining(UID));
  }

  @Test
  void whenRollInProcessAndReplicasTooHighAndAllServersRunning_removeRollingCondition() {
    domain.getStatus().addCondition(new DomainCondition(ROLLING));
    configureDomain().configureCluster(info, "cluster1").withReplicas(5);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(domain, not(hasCondition(ROLLING)));
  }

  @Test
  void whenRollInProcessAndReplicasTooHighAndAllServersRunning_generateDomainRollCompletedEvent() {
    domain.getStatus().addCondition(new DomainCondition(ROLLING));
    configureDomain().configureCluster(info, "cluster1").withReplicas(5);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(testSupport, hasEvent(DOMAIN_ROLL_COMPLETED_EVENT).inNamespace(NS).withMessageContaining(UID));
  }

  @Test
  void whenRollNotInProcessAndAllServersRunning_dontGenerateDomainRollCompletedEvent() {
    domain.getStatus().removeConditionsWithType(ROLLING);
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(testSupport, not(hasEvent(DOMAIN_ROLL_COMPLETED_EVENT)));
  }

  @Test
  void whenUnexpectedServersRunningAndNoMatchingCompletedConditionFound_dontGenerateCompletedEvent() {
    domain.getStatus()
          .addCondition(new DomainCondition(COMPLETED).withStatus(FALSE));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2", "server3")
          .notStarting("server3")
          .withServersReachingState("Unknown","server3")
          .build();

    updateDomainStatus();

    assertThat(testSupport, not(hasEvent(DOMAIN_COMPLETED_EVENT)));
  }

  @Test
  void whenNotAllDesiredServersRunning_establishCompletedConditionFalse() {
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenNotAllDesiredServersRunningAndCompletedFalseConditionFound_ignoreIt() {
    domain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(FALSE));
    defineScenario()
          .withCluster("clusterA","server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenNotAllDesiredServersRunningAndCompletedFalseConditionNotFound_addOne() {
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenNoPodsFailed_dontEstablishFailedCondition() {
    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(FAILED)));
  }

  @Test
  void whenNoPodsFailedAndFailedConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(SERVER_POD));

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(FAILED).withReason(SERVER_POD)));
  }

  @Test
  void whenAtLeastOnePodFailed_establishFailedCondition() {
    failPod("server1");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED));
  }

  @Test
  void whenAtLeastOnePodFailed_generateFailedEvent() {
    failPod("server1");
    failPod("server2");

    updateDomainStatus();

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT)
        .withMessageContaining(getLocalizedString(SERVER_POD_EVENT_ERROR)));
  }

  private void failPod(String serverName) {
    getPod(serverName).setStatus(new V1PodStatus().phase("Failed"));
    getServerStateMap().put(serverName, UNKNOWN_STATE);
  }

  @SuppressWarnings("SameParameterValue")
  private void unreadyPod(String serverName) {
    getPod(serverName).setStatus(
        new V1PodStatus().phase("Running").addConditionsItem(
            new V1PodCondition().type("Ready").status("False")));
  }

  @SuppressWarnings("SameParameterValue")
  private void markPodRunningPhaseFalse(String serverName) {
    getPod(serverName).setStatus(new V1PodStatus().phase("Pending"));
  }

  @Nonnull
  private Map<String, String> getServerStateMap() {
    return Optional.ofNullable(testSupport.getPacket())
          .map(p -> p.<Map<String, String>>getValue(SERVER_STATE_MAP))
          .orElse(Collections.emptyMap());
  }

  @Test
  void whenAtLeastOnePodAndFailedConditionTrueFound_leaveIt() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(SERVER_POD));
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED).withStatus(TRUE));
  }

  @Test
  void whenAtLeastOnePodFailed_dontCreateCompletedTrueCondition() {
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(COMPLETED).withStatus(TRUE)));
  }

  @Test
  void whenAtLeastOnePodFailedAndCompletedTrueConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(TRUE));
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(COMPLETED).withStatus(TRUE)));
  }

  @Test
  void whenAtLeastOnePodNotReadyInTime_createFailedCondition() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);
    unreadyPod("server2");

    SystemClockTestSupport.increment();
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED).withStatus(TRUE));
  }

  @Test
  void whenAtLeastOneReadyPodBecomeUnreadyForSometime_createFailedCondition() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);
    updateDomainStatus();

    unreadyPod("server2");
    SystemClockTestSupport.increment();
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED).withStatus(TRUE));
  }

  @Test
  void whenAtLeastOnePodNotReadyInTime_phaseRunningFalse_createFailedCondition() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);
    markPodRunningPhaseFalse("server2");

    SystemClockTestSupport.increment();
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED).withStatus(TRUE));
  }

  @Test
  void whenAtLeastOneReadyPodBecomeUnreadyForSometime_phaseRunningFalse_createFailedCondition() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);
    updateDomainStatus();

    markPodRunningPhaseFalse("server2");
    SystemClockTestSupport.increment();
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED).withStatus(TRUE));
  }

  @Test
  void whenAllPodsReadyInTime_dontCreateFailedCondition() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(FAILED).withStatus(TRUE)));
  }

  @Test
  void whenAtLeastOnePodWaitingForReady_dontCreateFailedCondition() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(2L);
    unreadyPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(FAILED).withStatus(TRUE)));
  }

  @Test
  void whenAtLeastOnePodNotReadyInTime_serverStatusPodNotReady() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);
    unreadyPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(),
        hasStatusForServer("server2").withPodReady("False").withPodPhase("Running"));
  }

  @Test
  void whenAtLeastOneReadyPodBecomeUnreadyForSometime_serverStatusPodNotReady() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);
    updateDomainStatus();

    unreadyPod("server2");
    SystemClockTestSupport.increment();
    updateDomainStatus();

    assertThat(getRecordedDomain(),
        hasStatusForServer("server2").withPodReady("False").withPodPhase("Running"));
  }

  @Test
  void whenAllPodsReadyInTime_serverStatusPodReady() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(0L);

    updateDomainStatus();

    assertThat(getRecordedDomain(),
        hasStatusForServer("server1").withPodReady("True").withPodPhase("Running"));
    assertThat(getRecordedDomain(),
        hasStatusForServer("server2").withPodReady("True").withPodPhase("Running"));
  }

  @Test
  void whenAtLeastOnePodWaitingForReady_serverStatusPodNotReady() {
    domain.getSpec().setMaxReadyWaitTimeSeconds(2L);
    unreadyPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(),
        hasStatusForServer("server2").withPodReady("False").withPodPhase("Running"));
  }

  @Test
  void whenPodPendingForTooLong_reportServerPodFailure() {
    TuningParametersStub.setParameter(TuningParameters.MAX_PENDING_WAIT_TIME_SECONDS, Long.toString(20));
    defineScenario().withServers("ms1", "ms2")
        .withServerState("ms1", new V1ContainerStateWaiting().reason("ImageBackOff"))
        .build();

    SystemClockTestSupport.increment(21);
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED).withReason(SERVER_POD).withMessageContaining("did not start"));
  }

  @Test
  void whenPodPendingWithinTimeLimit_doNotReportServerPodFailure() {
    TuningParametersStub.setParameter(TuningParameters.MAX_PENDING_WAIT_TIME_SECONDS, Long.toString(20));
    defineScenario().withServers("ms1", "ms2")
        .withServerState("ms1", new V1ContainerStateWaiting().reason("ImageBackOff"))
        .build();

    SystemClockTestSupport.increment(19);
    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(FAILED)));
  }

  @Test
  void whenPodPendingWithinTimeLimit_removePreviousServerPodFailures() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(SERVER_POD).withMessage("unit test"));
    domain.getSpec().setMaxPendingWaitTimeSeconds(20);
    defineScenario().withServers("ms1", "ms2")
        .withServerState("ms1", new V1ContainerStateWaiting().reason("ImageBackOff"))
        .build();

    SystemClockTestSupport.increment(19);
    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(FAILED)));
  }

  // todo remove server pod failures when OK

  @Test
  void whenNoDynamicClusters_doNotAddReplicasTooHighFailure() {
    defineScenario().withCluster("cluster1", "ms1", "ms2").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(FAILED)));
  }

  @Test
  void whenReplicaCountExceedsMaxReplicasForDynamicCluster_domainIsNotCompleted() {
    configureDomain().configureCluster(info,"cluster1").withReplicas(5);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenNumServersStartedBelowMinReplicasForDynamicClusterAndAllowed_domainIsAvailable() {
    defineScenario()
          .withDynamicCluster("cluster1", 3, 4)
          .notStarting("ms3", "ms4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
  }

  @Test
  void whenReplicaCountIsZeroAndAdminServerRunning_domainIsAvailable() {
    defineScenario()
          .withDynamicCluster("cluster1", 3, 4)
          .notStarting("ms1", "ms2", "ms3", "ms4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
  }

  @Test
  void whenReplicaCountWithinMaxUnavailableOfReplicas_domainIsAvailable() {
    configureDomain().configureCluster(info,"cluster1").withReplicas(5).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
  }

  @Test
  void whenReplicaCountNotWithinMaxUnavailableOfReplicas_domainIsNotAvailable() {
    configureDomain().configureCluster(info,"cluster1").withReplicas(20).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE)
        .withMessageContaining(LOGGER.formatMessage(CLUSTER_NOT_READY, "cluster1", 19, 4)));
  }

  // todo add hasCondition matcher for cluster status

  @Test
  void whenReplicaCountWithinMaxUnavailableOfReplicas_establishClusterAvailableConditionTrue() {
    configureDomain().configureCluster(info,"cluster1").withReplicas(5).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(0);
    assertThat(condition.getType(), equalTo(ClusterConditionType.AVAILABLE));
    assertThat(condition.getStatus(), equalTo(TRUE));
  }

  @Test
  void whenReplicaCountNotWithinMaxUnavailableOfReplicas_establishClusterAvailableConditionFalse() {
    configureDomain().configureCluster(info,"cluster1").withReplicas(20).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(0);
    assertThat(condition.getType(), equalTo(ClusterConditionType.AVAILABLE));
    assertThat(condition.getStatus(), equalTo(FALSE));
  }

  @Test
  void whenClusterIsIntentionallyShutdown_establishClusterAvailableConditionFalse() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(0).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 0).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(0);
    assertThat(condition.getType(), equalTo(ClusterConditionType.AVAILABLE));
    assertThat(condition.getStatus(), equalTo(FALSE));
  }

  @Test
  void whenClusterIsIntentionallyShutdown_establishClusterCompletedConditionTrue() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(0).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 0).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition completedCondition = clusterStatus.getConditions().get(1);
    assertThat(completedCondition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(completedCondition.getStatus(), equalTo(TRUE));
  }

  @Test
  void whenClusterIsIntentionallyShutdown_serverShuttingDown_establishClusterCompletedConditionFalse() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(0).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 1)
        .notStarting("ms1")
        .withServersReachingState(SHUTTING_DOWN_STATE, "ms1")
        .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition completedCondition = clusterStatus.getConditions().get(1);
    assertThat(completedCondition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(completedCondition.getStatus(), equalTo(FALSE));
  }

  @Test
  void whenClusterIsIntentionallyShutdown_serverShutDown_establishClusterCompletedConditionTrue() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(0).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 1)
        .notStarting("ms1")
        .withServersReachingState(SHUTDOWN_STATE, "ms1")
        .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition completedCondition = clusterStatus.getConditions().get(1);
    assertThat(completedCondition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(completedCondition.getStatus(), equalTo(TRUE));
  }

  @Test
  void whenClusterHasTooManyReplicas_establishClusterCompletedConditionFalse() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(20).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(1);
    assertThat(condition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(condition.getStatus(), equalTo(FALSE));
  }

  @Test
  void whenAllDesiredServersRunning_establishClusterCompletedConditionTrue() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(4).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(1);
    assertThat(condition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(condition.getStatus(), equalTo(TRUE));
  }

  @Test
  void whenAllDesiredServersRunningButSomeShutdown_establishClusterCompletedConditionTrue() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(2).withMaxUnavailable(1);
    defineScenario()
        .withCluster("cluster1", "server1", "server2", "server3", "server4")
        .notStarting("server2", "server3")
        .withServersReachingState(SHUTDOWN_STATE, "server2", "server3")
        .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(1);
    assertThat(condition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(condition.getStatus(), equalTo(TRUE));
  }

  @Test
  void whenAllDesiredServersRunningButSomeMarkedToBeRolled_establishClusterCompletedConditionFalse() {
    info.setServersToRoll(Map.of("server1", new Step.StepAndPacket(null, null)));
    configureDomain().configureCluster(info, "cluster1").withReplicas(2).withMaxUnavailable(1);
    defineScenario()
        .withCluster("cluster1", "server1", "server2", "server3", "server4")
        .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(1);
    assertThat(condition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(condition.getStatus(), equalTo(FALSE));
  }

  @Test
  void withNonClusteredServerNotReady_domainIsNotAvailable() {
    defineScenario().withServers("server1", "server2").withServersReachingState(STARTING_STATE, "server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE)
        .withMessageContaining(LOGGER.formatMessage(NON_CLUSTERED_SERVERS_NOT_READY, "server1")));
  }

  @Test
  void whenNoServersInAClusterAreRunning_domainIsNotAvailable() {
    defineScenario()
          .withCluster("cluster1", "ms1")
          .withServersReachingState(STARTING_STATE, "ms1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
  }

  @Test
  void whenServersInAClusterAreNotInRunningState_clusterIsNotAvailableAndNotCompleted() {
    defineScenario()
            .withCluster("cluster1", "ms1", "ms2")
            .withServersReachingState(STARTING_STATE, "ms1", "ms2").build();

    updateDomainStatus();

    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getConditions().size(), equalTo(2));
    ClusterCondition condition = clusterStatus.getConditions().get(0);
    assertThat(condition.getType(), equalTo(ClusterConditionType.AVAILABLE));
    assertThat(condition.getStatus(), equalTo(FALSE));
    condition = clusterStatus.getConditions().get(1);
    assertThat(condition.getType(), equalTo(ClusterConditionType.COMPLETED));
    assertThat(condition.getStatus(), equalTo(FALSE));
  }

  @Test
  void withServersShuttingDown_domainIsNotCompleted() {
    defineScenario().withServers("server1").withServersReachingState(SHUTTING_DOWN_STATE, "server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void withAllServersShutdown_domainIsCompleted() {   // !!!! can the admin server be NOT started?
    defineScenario()
          .withServers("server1")
          .notStarting(ADMIN, "server1")
          .withServersReachingState(SHUTDOWN_STATE, ADMIN, "server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void withClusterIntentionallyShutdownAndAdminServerRunning_domainIsAvailableAndCompleted() {
    defineScenario()
          .withCluster("cluster1", "ms1", "ms2")
          .notStarting("ms1", "ms2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void whenNonClusteredServerNotReady_domainIsNotAvailable() {
    defineScenario()
          .withServers("server0")
          .withCluster("clusterA", "server1", "server2")
          .build();
    deactivateServer("server0");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenNoClustersAndAllNonClusteredServersRunning_domainIsAvailableAndComplete() {
    defineScenario().withServers("server1", "server2").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void whenClusterIntentionallyShutDownAndAllNonClusteredServersRunning_domainIsAvailableAndComplete() {
    defineScenario()
          .withServers("server1", "server2")
          .withCluster("clusterA", "server3", "server4")
          .notStarting("server3", "server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void whenNoMoreThanMaxUnavailableServersNotRunningInACluster_domainIsAvailable() {
    configureDomain().configureCluster(info,"clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withServersReachingState(SHUTDOWN_STATE, "server3", "server4")
          .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenTooManyServersNotRunningInACluster_domainIsNotAvailable() {
    configureDomain().configureCluster(info,"clusterA").withReplicas(4).withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withServersReachingState(SHUTDOWN_STATE, "server2", "server3", "server4")
          .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenNoServersReadyInCluster_domainIsNotAvailable() {
    configureDomain().configureCluster(info,"clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .build();
    deactivateServer("server1");
    deactivateServer("server2");
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(AVAILABLE).withStatus(TRUE)));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenDomainWasAvailableAndNoLongerIs_domainAvailableConditionIsChangedToFalse() {
    domain.getStatus().addCondition(new DomainCondition(AVAILABLE).withStatus(TRUE));
    configureDomain().configureCluster(info,"clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .build();
    deactivateServer("server1");
    deactivateServer("server2");
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
    assertThat(getRecordedDomain(), not(hasCondition(AVAILABLE).withStatus(TRUE)));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenAllServersRunningAndAvailableConditionFound_dontGenerateAvailableEvent() {
    domain.getStatus().addCondition(new DomainCondition(AVAILABLE).withStatus(TRUE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();

    updateDomainStatus();

    assertThat(testSupport, not(hasEvent(DOMAIN_AVAILABLE_EVENT)));
  }

  @Test
  void whenAllServersRunningAndAvailableConditionNotFoundCompletedConditionNotFound_generateCompletedEvent() {
    domain.getStatus()
        .addCondition(new DomainCondition(AVAILABLE).withStatus(FALSE))
        .addCondition(new DomainCondition(COMPLETED).withStatus(FALSE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();

    updateDomainStatus();

    assertThat(testSupport, hasEvent(DOMAIN_COMPLETED_EVENT));
  }

  @Test
  void whenMultipleEventsGeneratedInDomainStatus_preserveOrder() {
    domain.getStatus()
        .addCondition(new DomainCondition(AVAILABLE).withStatus(FALSE))
        .addCondition(new DomainCondition(COMPLETED).withStatus(FALSE))
        .addCondition(new DomainCondition(ROLLING));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();
    testSupport.doOnCreate(EVENT, this::setUniqueCreationTimestamp);

    updateDomainStatus();

    assertThat(getEvents().stream().sorted(this::compareEventTimes).collect(Collectors.toList()),
        containsInRelativeOrder(List.of(
              eventWithReason(DOMAIN_AVAILABLE_EVENT),
              eventWithReason(DOMAIN_ROLL_COMPLETED_EVENT),
              eventWithReason(DOMAIN_COMPLETED_EVENT))));
  }

  @Test
  void whenUpdateDomainStatus_verifyClusterStatusObservedGeneration() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(4).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    // Get initial ClusterStatus from DomainResource and verify the initial value.
    ClusterStatus clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getObservedGeneration(), equalTo(1L));

    // increment the observedGeneration on the deployed ClusterResource and update DomainStatus
    ClusterResource cs = testSupport.getResourceWithName(KubernetesTestSupport.CLUSTER, "cluster1");
    cs.getStatus().withObservedGeneration(2L);

    updateDomainStatus();

    // Verify the ClusterStatus was updated with incremented observedGeneration after
    // DomainStatus update.
    clusterStatus = getClusterStatus();
    assertThat(clusterStatus.getObservedGeneration(), equalTo(2L));
  }

  private void setUniqueCreationTimestamp(Object event) {
    ((CoreV1Event) event).getMetadata().creationTimestamp(SystemClock.now());
    SystemClockTestSupport.increment();
  }

  private int compareEventTimes(CoreV1Event event1, CoreV1Event event2) {
    return getCreationStamp(event1).compareTo(getCreationStamp(event2));
  }

  private OffsetDateTime getCreationStamp(CoreV1Event event) {
    return Optional.ofNullable(event)
        .map(CoreV1Event::getMetadata)
        .map(V1ObjectMeta::getCreationTimestamp)
        .orElse(OffsetDateTime.MIN);
  }

  static class EventMatcher extends TypeSafeDiagnosingMatcher<CoreV1Event> {
    private final String expectedReason;

    private EventMatcher(String expectedReason) {
      this.expectedReason = expectedReason;
    }

    static EventMatcher eventWithReason(String expectedReason) {
      return new EventMatcher(expectedReason);
    }

    @Override
    protected boolean matchesSafely(CoreV1Event coreV1Event, Description description) {
      if (expectedReason.equals(coreV1Event.getReason())) {
        return true;
      } else {
        description.appendText(coreV1Event.getReason());
        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText(expectedReason);
    }
  }

  @Test
  void whenAllServersRunningAndAvailableConditionNotFoundCompletedConditionNotFound_generateAvailableEvent() {
    domain.getStatus()
        .addCondition(new DomainCondition(AVAILABLE).withStatus(FALSE))
        .addCondition(new DomainCondition(COMPLETED).withStatus(FALSE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();

    updateDomainStatus();

    assertThat(testSupport, hasEvent(DOMAIN_AVAILABLE_EVENT));
  }

  @Test
  void whenUnexpectedServersRunningAndAvailableConditionNotFound_generateAvailableEvent() {
    domain.getStatus()
        .addCondition(new DomainCondition(AVAILABLE).withStatus(FALSE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .withServersReachingState("Unknown","server3")
        .build();

    updateDomainStatus();

    assertThat(testSupport, hasEvent(DOMAIN_AVAILABLE_EVENT));
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  private void defineServerPod(String serverName) {
    info.setServerPod(serverName, createPod(serverName));
  }

  private V1Pod createPod(String serverName) {
    return new V1Pod().metadata(createPodMetadata(serverName))
          .spec(new V1PodSpec().addContainersItem(new V1Container().image(IMAGE)));
  }

  @Test
  void whenPacketNotPopulatedBeforeUpdateServerStatus_resourceVersionUpdated() {
    setupInitialServerStatus();
    String cachedResourceVersion = getRecordedDomain().getMetadata().getResourceVersion();

    // Clear the server maps in the packet, and run StatusUpdateStep, the domain resource
    // version should be updated because server health information is removed from domain status.
    clearPacketServerStatusMaps();
    updateDomainStatus();

    assertThat(getRecordedDomain().getMetadata().getResourceVersion(), not(cachedResourceVersion));
  }

  @Test
  void whenPacketPopulatedBeforeUpdateServerStatus_resourceVersionNotUpdated() {
    setupInitialServerStatus();
    String cachedResourceVersion = getRecordedDomain().getMetadata().getResourceVersion();

    // Clear the server maps in the packet, run StatusUpdateStep after running
    // PopulatePacketServerMapsStep, the domain resource version should NOT be updated because
    // the server maps are populated in the packet with the existing server status
    clearPacketServerStatusMaps();

    testSupport.runSteps(
        Step.chain(new DomainProcessorImpl.PopulatePacketServerMapsStep(),
            DomainStatusUpdater.createStatusUpdateStep(endStep)));

    assertThat(getRecordedDomain().getMetadata().getResourceVersion(), equalTo(cachedResourceVersion));
  }

  private void setupInitialServerStatus() {
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .withServersReachingState(SHUTDOWN_STATE, "server2")
          .build();

    // Run StatusUpdateStep with server maps in the packet to set up the initial domain status
    updateDomainStatus();
  }

  private void clearPacketServerStatusMaps() {
    testSupport.addToPacket(SERVER_STATE_MAP, null);
    testSupport.addToPacket(SERVER_HEALTH_MAP, null);
  }

  private DomainResource getRecordedDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, NAME);
  }

  @Test
  void whenNonDynamicMiiChangeAndCommitOnlySelected_addRestartRequiredCondition() {
    configureDomain().withMIIOnlineUpdate();
    defineScenario().build();
    testSupport.addToPacket(MII_DYNAMIC_UPDATE, MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(CONFIG_CHANGES_PENDING_RESTART));
  }

  @Test
  void whenNonDynamicMiiChangeAndUpdateAndRollSelected_dontAddRestartRequiredCondition() {
    testSupport.addToPacket(MII_DYNAMIC_UPDATE, MII_DYNAMIC_UPDATE_RESTART_REQUIRED);
    configureDomain().withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(CONFIG_CHANGES_PENDING_RESTART)));
  }

  @Test
  void whenAdminOnlyAndAdminServerIsReady_availableIsTrue() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
  }

  @Test
  void whenDomainHasNeverStartPolicy_completedIsTrue() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.NEVER);
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void whenAdminOnlyAndAdminServerIsNotReady_availableIsFalse() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario().withServersReachingState(STARTING_STATE, "admin").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
  }

  @Test
  void whenAdminOnlyAndAdminServerNameNotSetInDomainPresenceInfo_availableIsFalse() {
    info.setAdminServerName(null);
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario().withServersReachingState(STARTING_STATE, "admin").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
  }

  @Test
  void whenAdminOnly_completedIsTrue() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(TRUE));
  }

  @Test
  void whenAdminOnlyAndAdminIsNotYetRunning_completedIsFalse() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario().build();

    deactivateServer(ADMIN);
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenAdminOnlyAndAManagedServersShuttingDown_completedIsFalse() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(SHUTTING_DOWN_STATE, "server1", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenDomainRecheckOrScheduledStatusUpdateAndSSINotConstructed_verifyDomainStatusNotUpdated() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(2).withMaxUnavailable(1);
    ScenarioBuilder scenarioBuilder = defineScenario();
    scenarioBuilder.withCluster("cluster1", "server1", "server2")
        .withServersReachingState(STARTING_STATE, "server1", "server2")
        .build();
    info.getReferencedClusters().forEach(testSupport::defineResources);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));

    scenarioBuilder.withServersReachingState(RUNNING_STATE, "server1", "server2").build();
    info.setServerStartupInfo(null);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }

  @Test
  void whenDomainRecheckOrScheduleStatusUpdateAndAdminOnly_availableIsTrue() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
  }

  @Test
  void whenDomainRecheckOrScheduleStatusUpdateAndAdminOnlyAndAdminServerIsNotReady_availableIsFalse() {
    configureDomain().withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    defineScenario().withServersReachingState(STARTING_STATE, "admin").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
  }

  @Test
  void whenServerStartupInfoIsNull_andDontSkipStatusUpdate_availableIsFalse() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(2);
    info.getReferencedClusters().forEach(testSupport::defineResources);

    defineScenario()
        .withCluster("cluster1", "server1", "server2")
        .build();
    info.setServerStartupInfo(null);

    updateDomainStatusInEndOfProcessing();

    assertThat(getClusterConditions(),
        hasItems(new ClusterCondition(ClusterConditionType.AVAILABLE).withStatus(FALSE)));
  }

  @Test
  void whenUpdateDomainStatus_observedGenerationUpdated() {
    testSupport.getPacket().put(MAKE_RIGHT_DOMAIN_OPERATION, createDummyMakeRightOperation());
    updateDomainStatus();

    info.getDomain().getMetadata().setGeneration(2L);
    updateDomainStatusInEndOfProcessing();

    assertThat(getRecordedDomain().getStatus().getObservedGeneration(), equalTo(2L));
  }


  @Test
  void whenUpdateDomainStatusWhenObservedGenerationUpToDateStatusUnchanged_observedGenerationUnchanged() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(2);
    info.getReferencedClusters().forEach(testSupport::defineResources);
    info.getDomain().getMetadata().setGeneration(2L);
    info.getDomain().getStatus().setObservedGeneration(2L);

    defineScenario()
        .withCluster("cluster1", "server1", "server2")
        .build();
    info.setServerStartupInfo(null);

    updateDomainStatus();

    testSupport.getPacket().put(MAKE_RIGHT_DOMAIN_OPERATION, createDummyMakeRightOperation());
    updateDomainStatusInEndOfProcessing();

    assertThat(getRecordedDomain().getStatus().getObservedGeneration(), equalTo(2L));
  }

  @Test
  void whenUpdateDomainStatusWithEmptyResult_observedGenerationUpdated() {
    testSupport.getPacket().put(MAKE_RIGHT_DOMAIN_OPERATION, createDummyMakeRightOperation());

    info.getDomain().getMetadata().setGeneration(2L);
    testSupport.returnEmptyResult(DOMAIN, info.getDomainUid(), info.getNamespace());
    updateDomainStatusInEndOfProcessing();

    assertThat(getRecordedDomain().getStatus().getObservedGeneration(), equalTo(2L));
  }

  @Test
  void whenUpdateDomainStatusWithEmptyResultOnRetry_observedGenerationUpdated() {
    testSupport.getPacket().put(MAKE_RIGHT_DOMAIN_OPERATION, createDummyMakeRightOperation());

    info.getDomain().getMetadata().setGeneration(2L);
    testSupport.failOnReplaceStatus(DOMAIN_STATUS, info.getDomainUid(), info.getNamespace(), HTTP_UNAVAILABLE);
    testSupport.returnEmptyResultOnRead(DOMAIN, info.getDomainUid(), info.getNamespace());
    retryStrategy.setNumRetriesLeft(1);
    testSupport.addRetryStrategy(retryStrategy);
    updateDomainStatusInEndOfProcessing();

    assertThat(getRecordedDomain().getStatus().getObservedGeneration(), equalTo(2L));
  }

  @Test
  void whenUpdateDomainStatusFail503ErrorSuccessOnRetry_observedGenerationUpdated() {
    testSupport.getPacket().put(MAKE_RIGHT_DOMAIN_OPERATION, createDummyMakeRightOperation());

    info.getDomain().getMetadata().setGeneration(2L);
    testSupport.failOnReplaceStatus(DOMAIN_STATUS, info.getDomainUid(), info.getNamespace(), HTTP_UNAVAILABLE);
    retryStrategy.setNumRetriesLeft(1);
    testSupport.addRetryStrategy(retryStrategy);
    updateDomainStatusInEndOfProcessing();

    assertThat(getRecordedDomain().getStatus().getObservedGeneration(), equalTo(2L));
  }

  @Test
  void whenUpdateDomainStatusWith404Error_observedGenerationNotUpdated() {
    testSupport.getPacket().put(MAKE_RIGHT_DOMAIN_OPERATION, createDummyMakeRightOperation());

    info.getDomain().getMetadata().setGeneration(2L);
    testSupport.failOnReplaceStatus(DOMAIN_STATUS, info.getDomainUid(), info.getNamespace(), HTTP_NOT_FOUND);
    retryStrategy.setNumRetriesLeft(1);
    testSupport.addRetryStrategy(retryStrategy);
    updateDomainStatusInEndOfProcessing();

    assertThat(getRecordedDomain().getStatus().getObservedGeneration(), not(equalTo(2L)));
  }

  @Nullable
  private MakeRightOperation<DomainPresenceInfo> createDummyMakeRightOperation() {
    return new MakeRightDomainOperation() {
      @Override
      public MakeRightDomainOperation forDeletion() {
        return null;
      }

      @Override
      public MakeRightDomainOperation createRetry(@NotNull DomainPresenceInfo info) {
        return null;
      }

      @Override
      public MakeRightDomainOperation withExplicitRecheck() {
        return null;
      }

      @Override
      public MakeRightDomainOperation withEventData(EventHelper.EventData eventData) {
        return null;
      }

      @Override
      public MakeRightDomainOperation interrupt() {
        return null;
      }

      @Override
      public MakeRightDomainOperation retryOnFailure() {
        return null;
      }

      @Override
      public boolean hasEventData() {
        return false;
      }

      @Override
      public boolean isDeleting() {
        return false;
      }

      @Override
      public boolean isRetryOnFailure() {
        return false;
      }

      @Override
      public boolean isExplicitRecheck() {
        return false;
      }

      @Override
      public void setInspectionRun() {
      }

      @Override
      public EventHelper.EventData getEventData() {
        return null;
      }

      @Override
      public void setLiveInfo(@NotNull DomainPresenceInfo info) {

      }

      @Override
      public void clear() {
      }

      @Override
      public boolean wasInspectionRun() {
        return false;
      }

      @Override
      public void execute() {
      }

      @NotNull
      @Override
      public Packet createPacket() {
        return null;
      }

      @Override
      public Step createSteps() {
        return null;
      }

      @Override
      public boolean isWillInterrupt() {
        return false;
      }

      @Override
      public DomainPresenceInfo getPresenceInfo() {
        return null;
      }
    };
  }

  @Test
  void whenServerStartupInfoIsNull_andClusterStatusInitialized_statusUnchanged() {
    configureDomain().configureCluster(info, "cluster1").withReplicas(2);
    info.getReferencedClusters().forEach(testSupport::defineResources);

    defineScenario()
        .withCluster("cluster1", "server1", "server2")
        .build();
    initializeClusterStatus();
    info.setServerStartupInfo(null);

    updateDomainStatus();

    assertThat(getClusterConditions(),
        hasItems(new ClusterCondition(ClusterConditionType.AVAILABLE).withStatus(FALSE)));
  }

  private void initializeClusterStatus() {
    List<ClusterStatus> statusList = info.getDomain().getStatus().getClusters();
    ClusterStatus status = statusList.isEmpty() ? new ClusterStatus().withClusterName("cluster1") : statusList.get(0);
    status.addCondition(
        new ClusterCondition(ClusterConditionType.AVAILABLE).withStatus(FALSE));
    ClusterResource clusterResource = testSupport.getResourceWithName(KubernetesTestSupport.CLUSTER, "cluster1");
    clusterResource.setStatus(status);
  }

  @Test
  void whenDomainOnlyHasAdminServer_availableIsTrue() {
    configureDomain().configureAdminServer();
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
  }

  private Collection<ClusterCondition> getClusterConditions() {
    return testSupport.<ClusterResource>getResourceWithName(KubernetesTestSupport.CLUSTER, "cluster1")
        .getStatus().getConditions();
  }

  @Test
  void whenClusterIntentionallyShutdown_clusterAvailableIsFalseAndDomainAvailableIsTrue() {
    configureDomain()
        .configureCluster(info, "cluster1").withReplicas(0);
    info.getReferencedClusters().forEach(testSupport::defineResources);
    defineScenario()
        .withCluster("cluster1", "server1", "server2")
        .notStarting("server1", "server2")
        .build();

    updateDomainStatus();

    assertThat(getClusterConditions(),
        hasItems(new ClusterCondition(ClusterConditionType.AVAILABLE).withStatus(FALSE)));
    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(TRUE));
  }

  @Test
  void whenClusterIntentionallyShutdownAndSSINotConstructed_clusterAndDomainAvailableIsFalse() {
    configureDomain()
        .configureCluster(info, "cluster1").withReplicas(0);
    info.getReferencedClusters().forEach(testSupport::defineResources);
    defineScenario()
        .withCluster("cluster1", "server1", "server2")
        .notStarting("server1", "server2")
        .build();
    info.setServerStartupInfo(null);

    updateDomainStatusInEndOfProcessing();

    assertThat(getClusterConditions(),
        hasItems(new ClusterCondition(ClusterConditionType.AVAILABLE).withStatus(FALSE)));
    assertThat(getRecordedDomain(), hasCondition(AVAILABLE).withStatus(FALSE));
  }

  @Test
  void whenClusterIntentionallyShutdownAndSSINotSet_clusterStatusInitialized_clusterAndDomainAvailableIsFalse() {
    configureDomain()
        .configureCluster(info, "cluster1").withReplicas(0);
    info.getReferencedClusters().forEach(testSupport::defineResources);
    defineScenario()
        .withCluster("cluster1", "server1", "server2")
        .notStarting("server1", "server2")
        .build();
    initializeClusterStatus();
    info.setServerStartupInfo(null);

    updateDomainStatus();

    assertThat(getClusterConditions(),
        hasItems(new ClusterCondition(ClusterConditionType.AVAILABLE).withStatus(FALSE)));
  }

  @SuppressWarnings("SameParameterValue")
  private ScenarioBuilder defineScenario() {
    return new ScenarioBuilder();
  }

  // A builder class to define a test scenario.
  //
  // Methods 'withCluster' and 'withServers' define the domain configuration,
  // adding clustered and non-clustered servers, respectively.
  // A test ServerHealth will be associated with each server, created by replacing the string "server" in the server
  // name with "health."
  // Each running server will also be associated with a node whose name is created by replacing "server" with "node".
  // By default, all servers are marked as intended to have been started, and having reached the RUNNING state.
  // Servers may be marked instead as not started by invoking 'notStarting'
  // and the actual state may be changed by invoking 'withServersInState'
  private class ScenarioBuilder {
    private final WlsDomainConfigSupport configSupport;
    private final List<String> servers = new ArrayList<>();
    private final List<String> terminatingServers = new ArrayList<>();
    private final List<String> nonStartedServers = new ArrayList<>();
    private final Map<String,String[]> serverStates = new HashMap<>();
    private final Map<String, V1ContainerStateWaiting> waitingStates = new HashMap<>();

    private ScenarioBuilder() {
      configSupport = new WlsDomainConfigSupport("testDomain");
      configSupport.setAdminServerName(ADMIN);
      addServer(ADMIN);
      defineServerPod(ADMIN);
      activateServer(ADMIN);
    }

    // Adds a cluster to the topology, along with its servers
    ScenarioBuilder withCluster(String clusterName, String... serverNames) {
      Arrays.stream(serverNames).forEach(serverName -> addClusteredServer(clusterName, serverName));
      configSupport.addWlsCluster(clusterName, serverNames);
      return this;
    }

    void addClusteredServer(String clusterName, String serverName) {
      defineServerPod(serverName);
      servers.add(serverName);
      Objects.requireNonNull(getPod(serverName).getMetadata()).putLabelsItem(CLUSTERNAME_LABEL, clusterName);
    }

    // adds the server to the topology.
    // if the server is not defined in the domain presence, adds it there as well
    private void addServer(String serverName) {
      defineServerPod(serverName);
      configSupport.addWlsServer(serverName);
      servers.add(serverName);
    }

    @SuppressWarnings("SameParameterValue")
    ScenarioBuilder withDynamicCluster(String clusterName, int minServers, int maxServers) {
      configSupport.addWlsCluster(new WlsDomainConfigSupport.DynamicClusterConfigBuilder(clusterName)
            .withClusterLimits(minServers, maxServers)
            .withServerNames(generateServerNames(maxServers)));
      Arrays.stream(generateServerNames(maxServers)).forEach(serverName -> addClusteredServer(clusterName, serverName));

      return this;
    }

    private String[] generateServerNames(int maxServers) {
      return IntStream.rangeClosed(1, maxServers).boxed().map(i -> "ms" + i).toArray(String[]::new);
    }

    // Adds non-clustered servers to the topology
    ScenarioBuilder withServers(String... serverNames) {
      Arrays.stream(serverNames).forEach(this::addServer);
      return this;
    }

    ScenarioBuilder withServersReachingState(String state, String... servers) {
      serverStates.put(state, servers);
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    ScenarioBuilder withServerState(String serverName, V1ContainerStateWaiting waitingState) {
      waitingStates.put(serverName, waitingState);
      return this;
    }

    ScenarioBuilder notStarting(String... serverNames) {
      nonStartedServers.addAll(Arrays.asList(serverNames));
      return this;
    }

    ScenarioBuilder terminating(String... serverNames) {
      terminatingServers.addAll(Arrays.asList(serverNames));
      return this;
    }

    void build() {
      nonStartedServers.stream().filter(ADMIN::equals).findAny().ifPresent(s -> info.setAdminServerName(null));
      final WlsDomainConfig domainConfig = configSupport.createDomainConfig();
      liveServers = getLiveServers();
      testSupport.addToPacket(SERVER_STATE_MAP, createStateMap());
      testSupport.addToPacket(SERVER_HEALTH_MAP, createHealthMap());
      processTopology(domainConfig);
      info.setServerStartupInfo(createServerStartupInfo(domainConfig));
      liveServers.forEach(this::activateServer);
      terminatingServers.forEach(this::markServerTerminating);
    }

    @Nonnull
    private List<DomainPresenceInfo.ServerStartupInfo> createServerStartupInfo(WlsDomainConfig domainConfig) {
      return domainConfig.getAllServers().stream()
            .filter(c -> !isAdminServer(c))
            .filter(this::isLive)
            .map(config -> new DomainPresenceInfo.ServerStartupInfo(config, "", null))
            .collect(Collectors.toList());
    }

    private boolean isLive(WlsServerConfig serverConfig) {
      return !nonStartedServers.contains(serverConfig.getName());
    }

    private boolean isAdminServer(WlsServerConfig serverConfig) {
      return ADMIN.equals(serverConfig.getName());
    }

    private Map<String,String> createStateMap() {
      Map<String,String> result = new HashMap<>();
      result.put(ADMIN, RUNNING_STATE);
      getLiveServers().forEach(server -> result.put(server, RUNNING_STATE));
      for (String state : serverStates.keySet()) {
        for (String server: serverStates.get(state)) {
          result.put(server, state);
        }
      }
      return result;
    }

    private List<String> getLiveServers() {
      List<String> result = new ArrayList<>(servers);
      result.removeAll(nonStartedServers);
      result.removeAll(terminatingServers);
      return result;
    }

    private Map<String,ServerHealth> createHealthMap() {
      Map<String,ServerHealth> result = new HashMap<>();
      servers.forEach(server -> result.put(server, overallHealth(toHealthString(server))));
      return result;
    }

    private String toHealthString(String serverName) {
      return serverName.startsWith("server") ? "health" + serverName.substring("server".length()) : "health";
    }

    private void activateServer(String serverName) {
      final V1Pod pod = getPod(serverName);

      Objects.requireNonNull(pod.getSpec()).setNodeName(toNodeName(serverName));
      if (waitingStates.containsKey(serverName)) {
        pod.setStatus(new V1PodStatus()
            .startTime(SystemClock.now())
            .phase("Pending")
            .addConditionsItem(new V1PodCondition().type("Ready").status("False"))
            .addContainerStatusesItem(createContainerStatusItem(serverName))
        );  
      } else {
        pod.setStatus(new V1PodStatus()
              .startTime(SystemClock.now())
              .phase("Running")
              .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
      }
    }

    private V1ContainerStatus createContainerStatusItem(String serverName) {
      return new V1ContainerStatus().state(new V1ContainerState().waiting(waitingStates.get(serverName)));
    }

    private void markServerTerminating(String serverName) {
      Objects.requireNonNull(getPod(serverName).getMetadata()).setDeletionTimestamp(SystemClock.now());
    }

    private String toNodeName(String serverName) {
      return serverName.startsWith("server") ? "node" + serverName.substring("server".length()) : "node";
    }
  }

  @SuppressWarnings("unused")
  static class ServerStatusMatcher extends TypeSafeDiagnosingMatcher<DomainResource> {
    private final String serverName;
    private final MultiFieldMatcher<ServerStatus> matcher;

    private ServerStatusMatcher(String serverName) {
      this.serverName = serverName;
      matcher = new MultiFieldMatcher<>("status for server '" + serverName + "'", "no such status found");
    }

    static ServerStatusMatcher hasStatusForServer(String serverName) {
      return new ServerStatusMatcher(serverName);
    }

    ServerStatusMatcher withState(@Nonnull String expectedValue) {
      matcher.addField("state", ServerStatus::getState, expectedValue);
      return this;
    }

    ServerStatusMatcher withStateGoal(String expectedValue) {
      matcher.addField("desired state", ServerStatus::getStateGoal, expectedValue);
      return this;
    }

    ServerStatusMatcher withClusterName(String expectedValue) {
      matcher.addField("cluster name", ServerStatus::getClusterName, expectedValue);
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    ServerStatusMatcher withPodPhase(String expectedValue) {
      matcher.addField("pod phase", ServerStatus::getPodPhase, expectedValue);
      return this;
    }

    ServerStatusMatcher withPodReady(String expectedValue) {
      matcher.addField("pod ready", ServerStatus::getPodReady, expectedValue);
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    ServerStatusMatcher withNodeName(String expectedValue) {
      matcher.addField("node name", ServerStatus::getNodeName, expectedValue);
      return this;
    }

    @Override
    protected boolean matchesSafely(DomainResource domain, Description description) {
      return matcher.matches(getServerStatus(domain), description);
    }

    private ServerStatus getServerStatus(DomainResource domain) {
      return getServerStatuses(domain).stream()
            .filter(serverStatus -> serverStatus.getServerName().equals(serverName))
            .findFirst()
            .orElse(null);
    }

    @Nonnull
    private List<ServerStatus> getServerStatuses(DomainResource domain) {
      return Optional.ofNullable(domain)
            .map(DomainResource::getStatus)
            .map(DomainStatus::getServers)
            .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      matcher.describe(description);
    }
  }

  @SuppressWarnings("unused")
  static class ClusterStatusMatcher extends TypeSafeDiagnosingMatcher<DomainResource> {
    private final String clusterName;
    private final MultiFieldMatcher<ClusterStatus> matcher;

    private ClusterStatusMatcher(String clusterName) {
      this.clusterName = clusterName;
      matcher = new MultiFieldMatcher<>("status for cluster '" + clusterName + "'", "no such status found");
    }

    static ClusterStatusMatcher hasStatusForCluster(String clusterName) {
      return new ClusterStatusMatcher(clusterName);
    }

    ClusterStatusMatcher withMaximumReplicas(@Nonnull Integer expectedValue) {
      matcher.addField("maximumReplicas", ClusterStatus::getMaximumReplicas, expectedValue);
      return this;
    }

    ClusterStatusMatcher withMinimumReplicas(@Nonnull Integer expectedValue) {
      matcher.addField("minimumReplicas", ClusterStatus::getMinimumReplicas, expectedValue);
      return this;
    }

    ClusterStatusMatcher withReplicas(@Nonnull Integer expectedValue) {
      matcher.addField("replicas", ClusterStatus::getReplicas, expectedValue);
      return this;
    }

    ClusterStatusMatcher withReplicasGoal(@Nonnull Integer expectedValue) {
      matcher.addField("replicasGoal", ClusterStatus::getReplicasGoal, expectedValue);
      return this;
    }

    ClusterStatusMatcher withReadyReplicas(@Nonnull Integer expectedValue) {
      matcher.addField("readyReplicas", ClusterStatus::getReadyReplicas, expectedValue);
      return this;
    }

    @Override
    protected boolean matchesSafely(DomainResource domain, Description description) {
      return matcher.matches(getClusterStatus(domain), description);
    }

    private ClusterStatus getClusterStatus(DomainResource domain) {
      return getClusterStatuses(domain).stream()
          .filter(clusterStatus -> clusterStatus.getClusterName().equals(clusterName))
          .findFirst()
          .orElse(null);
    }

    @Nonnull
    private List<ClusterStatus> getClusterStatuses(DomainResource domain) {
      return Optional.ofNullable(domain)
          .map(DomainResource::getStatus)
          .map(DomainStatus::getClusters)
          .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      matcher.describe(description);
    }
  }

  static class MultiFieldMatcher<T> {
    private final String objectDescription;
    private final String notFoundDescription;
    private final List<FieldMatcher<T,?>> fields = new ArrayList<>();

    MultiFieldMatcher(String objectDescription, String notFoundDescription) {
      this.objectDescription = objectDescription;
      this.notFoundDescription = notFoundDescription;
    }

    <S> void addField(String name, Function<T, S> getter, S expectedValue) {
      fields.add(new FieldMatcher<>(name, getter, expectedValue));
    }

    boolean matches(T object, Description description) {
      if (object == null) {
        description.appendText(notFoundDescription);
        return false;
      } else if (fields.stream().allMatch(m -> m.matches(object))) {
        return true;
      } else {
        description.appendText(getMismatchDescription(object));
        return false;
      }
    }

    @Nonnull
    private String getMismatchDescription(T object) {
      return fields.stream()
            .map(f -> f.getMismatch(object))
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" and "));
    }

    void describe(Description description) {
      description.appendText(objectDescription);
      if (!fields.isEmpty()) {
        description
              .appendText(" with ")
              .appendText(fields.stream().map(FieldMatcher::getDescription).collect(Collectors.joining(" and ")));
      }
    }
  }


  static class FieldMatcher<T,S> {
    private final String name;
    private final Function<T, S> getter;
    private final S expectedValue;

    FieldMatcher(String name, Function<T, S> getter, @Nonnull S expectedValue) {
      this.name = name;
      this.getter = getter;
      this.expectedValue = expectedValue;
    }

    boolean matches(@Nonnull T object) {
      return expectedValue.equals(getter.apply(object));
    }

    String getMismatch(@Nonnull T object) {
      if (matches(object)) {
        return null;
      } else {
        return name + " was '" + getter.apply(object) + "'";
      }
    }

    String getDescription() {
      return String.format("%s '%s'", name, expectedValue);
    }

  }

}
