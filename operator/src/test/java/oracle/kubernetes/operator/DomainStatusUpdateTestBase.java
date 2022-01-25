// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.utils.RandomStringGenerator;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.operator.DomainFailureReason.ReplicasTooHigh;
import static oracle.kubernetes.operator.DomainFailureReason.ServerPod;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdateTestBase.ClusterStatusMatcher.hasStatusForCluster;
import static oracle.kubernetes.operator.DomainStatusUpdateTestBase.EventMatcher.eventWithReason;
import static oracle.kubernetes.operator.DomainStatusUpdateTestBase.ServerStatusMatcher.hasStatusForServer;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.REPLICAS_TOO_HIGH_ERROR;
import static oracle.kubernetes.operator.EventConstants.SERVER_POD_ERROR;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
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
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.FALSE;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.TRUE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Completed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ConfigChangesPendingRestart;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

abstract class DomainStatusUpdateTestBase {
  private static final String NAME = UID;
  private static final String ADMIN = "admin";
  public static final String CLUSTER = "cluster1";
  private static final String IMAGE = "initialImage:0";
  private final TerminalStep endStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final RandomStringGenerator generator = new RandomStringGenerator();
  private final String validationWarning = generator.getUniqueString();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));
  private List<String> liveServers;

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
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
    return new V1ObjectMeta().namespace(NS).name(serverName);
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
          = new DomainStatusUpdater.DomainStatusFactory(getRecordedDomain(), domainConfig, this::isConfiguredToRun);

    domainStatusFactory.setStatusDetails(getRecordedDomain().getOrCreateStatus());
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

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withDesiredState(RUNNING_STATE)
                .withNodeName("node1")
                .withServerName("server1")
                .withHealth(overallHealth("health1"))));
    assertThat(
        getServerStatus(getRecordedDomain(), "server2"),
        equalTo(
            new ServerStatus()
                .withState(SHUTDOWN_STATE)
                .withDesiredState(RUNNING_STATE)
                .withClusterName("clusterB")
                .withNodeName("node2")
                .withServerName("server2")
                .withHealth(overallHealth("health2"))));
  }

  private void updateDomainStatus() {
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));
  }

  // Examines the domain status and returns the server status for the specified server, if it is defined
  private ServerStatus getServerStatus(Domain domain, String serverName) {
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
                .withDesiredState(RUNNING_STATE)
                .withClusterName("clusterC"));
    assertThat(getRecordedDomain(),
          hasStatusForServer("server4")
                .withState(SHUTDOWN_STATE)
                .withDesiredState(SHUTDOWN_STATE)
                .withClusterName("clusterC"));
  }

  @Test
  void statusStep_definesClusterFromWlsConfig() {
    configureDomain().withAllowReplicasBelowMinDynClusterSize(false);
    defineScenario()
          .withCluster("clusterA", "server3", "server4")
          .withDynamicCluster("clusterB", 2, 8)
          .notStarting("server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(),
          hasStatusForCluster("clusterA").withMinimumReplicas(0).withMaximumReplicas(2));
    assertThat(getRecordedDomain(),
          hasStatusForCluster("clusterB").withMinimumReplicas(2).withMaximumReplicas(8));
  }

  @Test
  void statusStep_definesClusterReplicaGoalFromDomain() {
    configureDomain().configureCluster("clusterA").withReplicas(3);
    configureDomain().configureCluster("clusterB").withReplicas(5);
    domain.getSpec().setAllowReplicasBelowMinDynClusterSize(false);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withDynamicCluster("clusterB", 2, 8)
          .build();

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
  void whenServerIntentionallyNotStarted_reportItsStateAsShutdown() {    
    defineScenario().withServers("server1").notStarting("server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(),
          hasStatusForServer("server1").withState(SHUTDOWN_STATE).withDesiredState(SHUTDOWN_STATE));
  }

  @Test
  void statusStep_containsValidationWarnings() {
    info.addValidationWarning(validationWarning);
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain().getStatus().getMessage(), containsString(validationWarning));
  }

  @Test
  void whenStatusUnchanged_statusStepDoesNotUpdateDomain() {   
    defineScenario().withServers("server1").notStarting("server1").build();
    domain.setStatus(
        new DomainStatus()
            .withServers(
                Collections.singletonList(
                    new ServerStatus()
                        .withState(SHUTDOWN_STATE)
                        .withDesiredState(SHUTDOWN_STATE)
                        .withServerName("server1")
                        .withHealth(overallHealth("health1"))))
              .addCondition(new DomainCondition(Available).withStatus(false))
              .addCondition(new DomainCondition(Completed).withStatus(true)));

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
    info.setServerStartupInfo(null);
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

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenNoServersRunning_establishAvailableConditionFalse() {   
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(SHUTDOWN_STATE, "server1", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
  }

  @Test
  void withoutAClusterWhenAllDesiredServersRunning_establishCompletedConditionTrue() {  

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void withAClusterWhenAllDesiredServersRunningAndNoClusters_establishCompletedConditionTrue() {  
    defineScenario().withCluster("cluster1", "ms1", "ms2", "ms3").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void whenAnyServerLacksReadyState_establishCompletedConditionFalse() {
    defineScenario().withCluster("cluster1", "ms1", "ms2", "ms3").build();
    deactivateServer("ms1");
    deactivateServer("ms2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
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

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
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

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void whenAllDesiredServersRunningAndMatchingCompletedConditionFound_leaveIt() {  
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus(true));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
  }

  @Test
  void whenAllDesiredServersRunningAndMismatchedCompletedConditionStatusFound_changeIt() {  
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus(false));

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
  }

  @Test
  void whenAllDesiredServersRunningButSomeMarkedToBeRolled_establishCompletedConditionFalse() {  
    info.setServersToRoll(Map.of("server1", new Step.StepAndPacket(null, null)));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenAllDesiredServersRunningAndMatchingCompletedConditionFound_dontGenerateCompletedEvent() {  
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus(TRUE));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainCompletedEvent), is(false));
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(EVENT);
  }

  private boolean isDomainCompletedEvent(CoreV1Event e) {
    return DOMAIN_COMPLETED_EVENT.equals(e.getReason());
  }

  @Test
  void whenAllDesiredServersRunningAndNoMatchingCompletedConditionFound_generateCompletedEvent() {
    domain.getStatus()
          .addCondition(new DomainCondition(Completed).withStatus(FALSE));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainCompletedEvent), is(true));
  }

  @Test
  void whenUnexpectedServersRunningAndNoMatchingCompletedConditionFound_dontGenerateCompletedEvent() {
    domain.getStatus()
          .addCondition(new DomainCondition(Completed).withStatus(FALSE));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2", "server3")
          .notStarting("server3")
          .withServersReachingState("Unknown","server3")
          .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainCompletedEvent), is(false));
  }

  @Test
  void whenNotAllDesiredServersRunning_establishCompletedConditionFalse() {
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenNotAllDesiredServersRunningAndCompletedFalseConditionFound_ignoreIt() {
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus(FALSE));
    defineScenario()
          .withCluster("clusterA","server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenNotAllDesiredServersRunningAndCompletedFalseConditionNotFound_addOne() {
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenNoPodsFailed_dontEstablishFailedCondition() {
    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenNoPodsFailedAndFailedConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withReason(ServerPod));

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed).withReason(ServerPod)));
  }

  @Test
  void whenAtLeastOnePodFailed_establishFailedCondition() {
    failPod("server1");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Failed));
  }

  @Test
  void whenAtLeastOnePodFailed_generateFailedEvent() {
    failPod("server1");
    failPod("server2");

    updateDomainStatus();

    assertThat(getEvents().size(), greaterThan(0));
    assertThat(getEvents().stream().anyMatch(this::isServerPodFailedEvent), is(true));
  }

  private void failPod(String serverName) {
    getPod(serverName).setStatus(new V1PodStatus().phase("Failed"));
    getServerStateMap().put(serverName, UNKNOWN_STATE);
  }

  @Nonnull
  private Map<String, String> getServerStateMap() {
    return Optional.ofNullable(testSupport.getPacket())
          .map(p -> p.<Map<String, String>>getValue(SERVER_STATE_MAP))
          .orElse(Collections.emptyMap());
  }

  @Test
  void whenAtLeastOnePodAndFailedConditionTrueFound_leaveIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus(TRUE));
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Failed).withStatus(TRUE));
  }

  @Test
  void whenAtLeastOnePodFailed_dontCreateCompletedTrueCondition() {
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Completed).withStatus(TRUE)));
  }

  @Test
  void whenAtLeastOnePodFailedAndCompletedTrueConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus(TRUE));
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Completed).withStatus(TRUE)));
  }

  @Test
  void whenNoDynamicClusters_doNotAddReplicasTooHighFailure() {
    defineScenario().withCluster("cluster1", "ms1", "ms2").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenReplicaCountDoesNotExceedMaxReplicasForDynamicCluster_removeOldReplicasTooHighFailure() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withReason(ReplicasTooHigh));
    domain.setReplicaCount("cluster1", 4);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenReplicaCountDoesNotExceedMaxReplicasForDynamicCluster_doNotAddReplicasTooHighFailure() {
    domain.setReplicaCount("cluster1", 4);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenReplicaCountExceedsMaxReplicasForDynamicCluster_addFailedAndCompletedFalseCondition() {
    domain.setReplicaCount("cluster1", 5);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Failed).withReason(ReplicasTooHigh).withMessageContaining("cluster1"));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));;
  }

  @Test
  void whenReplicaCountExceedsMaxReplicasForDynamicCluster_generateFailedEvent() {
    domain.setReplicaCount("cluster1", 5);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isReplicasTooHighEvent), is(true));
  }

  @Test
  void whenReplicaCountExceedsMaxReplicasForDynamicCluster_domainIsNotCompleted() {
    configureDomain().configureCluster("cluster1").withReplicas(5);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenNumServersStartedBelowMinReplicasForDynamicClusterAndAllowed_domainIsAvailable() {
    defineScenario()
          .withDynamicCluster("cluster1", 3, 4)
          .notStarting("ms3", "ms4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(TRUE));
  }

  @Test
  void whenReplicaCountBelowMinReplicasForDynamicClusterAndNotAllowed_domainIsNotAvailable() {
    configureDomain().withAllowReplicasBelowMinDynClusterSize(false);
    defineScenario()
          .withDynamicCluster("cluster1", 3, 4)
          .notStarting("ms3", "ms4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
  }

  @Test
  void whenReplicaCountWithinMaxUnavailableOfReplicas_domainIsAvailable() {
    configureDomain().configureCluster("cluster1").withReplicas(5).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(TRUE));
  }

  @Test
  void whenReplicaCountNotWithinMaxUnavailableOfReplicas_domainIsNotAvailable() {
    configureDomain().configureCluster("cluster1").withReplicas(20).withMaxUnavailable(1);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
  }

  @Test
  void withNonClusteredServerNotStarting_domainIsNotAvailable() {
    defineScenario().withServers("server1").notStarting("server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
  }

  @Test
  void whenNoServersInAClusterAreRunning_domainIsNotAvailable() {
    defineScenario()
          .withCluster("cluster1", "ms1")
          .withServersReachingState(STARTING_STATE, "ms1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
  }

  @Test
  void withServersShuttingDown_domainIsNotCompleted() {
    defineScenario().withServers("server1").withServersReachingState(SHUTTING_DOWN_STATE, "server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void withAllServersShutdown_domainIsCompleted() {   // !!!! can the admin server be NOT started?
    defineScenario()
          .withServers("server1")
          .notStarting(ADMIN, "server1")
          .withServersReachingState(SHUTDOWN_STATE, ADMIN, "server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
  }

  @Test
  void withClusterIntentionallyShutdown_domainIsCompleted() {
    defineScenario()
          .withCluster("cluster1", "ms1", "ms2")
          .notStarting("ms1", "ms2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
  }

  @Test
  void whenNonClusteredServerNotReady_domainIsNotAvailable() {
    defineScenario()
          .withServers("server0")
          .withCluster("clusterA", "server1", "server2")
          .build();
    deactivateServer("server0");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenNoClustersAndAllNonClusteredServersRunning_domainIsAvailableAndComplete() {
    defineScenario().withServers("server1", "server2").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(TRUE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
  }

  @Test
  void whenClusterIntentionallyShutDownAndAllNonClusteredServersRunning_domainIsAvailableAndComplete() {
    defineScenario()
          .withServers("server1", "server2")
          .withCluster("clusterA", "server3", "server4")
          .notStarting("server3", "server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(TRUE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
  }

  @Test
  void whenNoMoreThanMaxUnavailableServersNotRunningInACluster_domainIsAvailable() {
    configureDomain().configureCluster("clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withServersReachingState(SHUTDOWN_STATE, "server3", "server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(TRUE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenTooManyServersNotRunningInACluster_domainIsNotAvailable() {
    configureDomain().configureCluster("clusterA").withReplicas(4).withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withServersReachingState(SHUTDOWN_STATE, "server2", "server3", "server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenNoServersReadyInCluster_domainIsNotAvailable() {
    configureDomain().configureCluster("clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .build();
    deactivateServer("server1");
    deactivateServer("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Available).withStatus(TRUE)));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenDomainWasAvailableAndNoLongerIs_domainAvailableConditionIsChangedToFalse() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus(TRUE));
    configureDomain().configureCluster("clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .build();
    deactivateServer("server1");
    deactivateServer("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
    assertThat(getRecordedDomain(), not(hasCondition(Available).withStatus(TRUE)));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenAllServersRunningAndAvailableConditionFound_dontGenerateAvailableEvent() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus(TRUE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainAvailableEvent), is(false));
  }

  private boolean isDomainAvailableEvent(CoreV1Event e) {
    return DOMAIN_AVAILABLE_EVENT.equals(e.getReason());
  }

  private boolean isReplicasTooHighEvent(CoreV1Event e) {
    return DOMAIN_FAILED_EVENT.equals(e.getReason()) && e.getMessage().contains(REPLICAS_TOO_HIGH_ERROR);
  }

  private boolean isServerPodFailedEvent(CoreV1Event e) {
    return DOMAIN_FAILED_EVENT.equals(e.getReason()) && e.getMessage().contains(SERVER_POD_ERROR);
  }

  @Test
  void whenAllServersRunningAndAvailableConditionNotFoundCompletedConditionNotFound_generateCompletedEvent() {
    domain.getStatus()
        .addCondition(new DomainCondition(Available).withStatus(FALSE))
        .addCondition(new DomainCondition(Completed).withStatus(FALSE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainCompletedEvent), is(true));
  }

  @Test
  void whenAllServersRunningAndAvailableConditionNotFoundCompletedConditionNotFound_generateTwoEventsInOrder() {
    domain.getStatus()
        .addCondition(new DomainCondition(Available).withStatus(FALSE))
        .addCondition(new DomainCondition(Completed).withStatus(FALSE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();
    testSupport.doOnCreate(EVENT, this::setUniqueCreationTimestamp);

    updateDomainStatus();

    assertThat(getEvents().stream().sorted(this::compareEvents).collect(Collectors.toList()),
        contains(eventWithReason(DOMAIN_AVAILABLE_EVENT), eventWithReason(DOMAIN_COMPLETED_EVENT)));
  }

  private void setUniqueCreationTimestamp(Object event) {
    ((CoreV1Event) event).getMetadata().creationTimestamp(SystemClock.now());
    SystemClockTestSupport.increment();
  }

  private int compareEvents(CoreV1Event event1, CoreV1Event event2) {
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
        .addCondition(new DomainCondition(Available).withStatus(FALSE))
        .addCondition(new DomainCondition(Completed).withStatus(FALSE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainAvailableEvent), is(true));
  }

  @Test
  void whenUnexpectedServersRunningAndAvailableConditionNotFound_generateAvailableEvent() {
    domain.getStatus()
        .addCondition(new DomainCondition(Available).withStatus(FALSE));
    defineScenario()
        .withCluster("clusterA", "server1")
        .withCluster("clusterB", "server2")
        .withServersReachingState("Unknown","server3")
        .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainAvailableEvent), is(true));
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
        Step.chain(processor.createPopulatePacketServerMapsStep(),
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

  private Domain getRecordedDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, NAME);
  }

  @Test
  void whenNonDynamicMiiChangeAndCommitOnlySelected_addRestartRequiredCondition() {
    configureDomain().withMIIOnlineUpdate();
    defineScenario().build();
    testSupport.addToPacket(MII_DYNAMIC_UPDATE, MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(ConfigChangesPendingRestart));
  }

  @Test
  void whenNonDynamicMiiChangeAndUpdateAndRollSelected_dontAddRestartRequiredCondition() {
    testSupport.addToPacket(MII_DYNAMIC_UPDATE, MII_DYNAMIC_UPDATE_RESTART_REQUIRED);
    configureDomain().withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(ConfigChangesPendingRestart)));
  }

  @Test
  void whenAdminOnly_availableIsFalse() {
    configureDomain().withDefaultServerStartPolicy(ConfigurationConstants.START_ADMIN_ONLY);
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus(FALSE));
  }

  @Test
  void whenAdminOnly_completedIsTrue() {
    configureDomain().withDefaultServerStartPolicy(ConfigurationConstants.START_ADMIN_ONLY);
    defineScenario().build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(TRUE));
  }

  @Test
  void whenAdminOnlyAndAdminIsNotYetRunning_completedIsFalse() {
    configureDomain().withDefaultServerStartPolicy(ConfigurationConstants.START_ADMIN_ONLY);
    defineScenario().build();

    deactivateServer(ADMIN);
    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
  }

  @Test
  void whenAdminOnlyAndAManagedServersShuttingDown_completedIsFalse() {
    configureDomain().withDefaultServerStartPolicy(ConfigurationConstants.START_ADMIN_ONLY);
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(SHUTTING_DOWN_STATE, "server1", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus(FALSE));
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

    private ScenarioBuilder() {
      configSupport = new WlsDomainConfigSupport("testDomain");
      configSupport.setAdminServerName(ADMIN);
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
            .filter(this::isLive)
            .map(config -> new DomainPresenceInfo.ServerStartupInfo(config, "", null))
            .collect(Collectors.toList());
    }

    private boolean isLive(WlsServerConfig serverConfig) {
      return !nonStartedServers.contains(serverConfig.getName());
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
      pod.setStatus(new V1PodStatus()
            .phase("Running")
            .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
    }

    private void markServerTerminating(String serverName) {
      Objects.requireNonNull(getPod(serverName).getMetadata()).setDeletionTimestamp(SystemClock.now());
    }

    private String toNodeName(String serverName) {
      return serverName.startsWith("server") ? "node" + serverName.substring("server".length()) : "node";
    }
  }

  @SuppressWarnings("unused")
  static class ServerStatusMatcher extends TypeSafeDiagnosingMatcher<Domain> {
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

    ServerStatusMatcher withDesiredState(String expectedValue) {
      matcher.addField("desired state", ServerStatus::getDesiredState, expectedValue);
      return this;
    }

    ServerStatusMatcher withClusterName(String expectedValue) {
      matcher.addField("cluster name", ServerStatus::getClusterName, expectedValue);
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    ServerStatusMatcher withNodeName(String expectedValue) {
      matcher.addField("node name", ServerStatus::getNodeName, expectedValue);
      return this;
    }

    @Override
    protected boolean matchesSafely(Domain domain, Description description) {
      return matcher.matches(getServerStatus(domain), description);
    }

    private ServerStatus getServerStatus(Domain domain) {
      return getServerStatuses(domain).stream()
            .filter(serverStatus -> serverStatus.getServerName().equals(serverName))
            .findFirst()
            .orElse(null);
    }

    @Nonnull
    private List<ServerStatus> getServerStatuses(Domain domain) {
      return Optional.ofNullable(domain)
            .map(Domain::getStatus)
            .map(DomainStatus::getServers)
            .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      matcher.describe(description);
    }
  }

  @SuppressWarnings("unused")
  static class ClusterStatusMatcher extends TypeSafeDiagnosingMatcher<Domain> {
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
    protected boolean matchesSafely(Domain domain, Description description) {
      return matcher.matches(getClusterStatus(domain), description);
    }

    private ClusterStatus getClusterStatus(Domain domain) {
      return getClusterStatuses(domain).stream()
            .filter(clusterStatus -> clusterStatus.getClusterName().equals(clusterName))
            .findFirst()
            .orElse(null);
    }

    @Nonnull
    private List<ClusterStatus> getClusterStatuses(Domain domain) {
      return Optional.ofNullable(domain)
            .map(Domain::getStatus)
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
