// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.utils.RandomStringGenerator;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport.DynamicClusterConfigBuilder;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
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
import static oracle.kubernetes.operator.DomainFailureReason.Internal;
import static oracle.kubernetes.operator.DomainFailureReason.Introspection;
import static oracle.kubernetes.operator.DomainFailureReason.ReplicasTooHigh;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdaterTest.EventMatcher.eventWithReason;
import static oracle.kubernetes.operator.DomainStatusUpdaterTest.ServerStatusMatcher.hasStatusForServer;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.STANDBY_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.UNKNOWN_STATE;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Completed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ConfigChangesPendingRestart;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainStatusUpdaterTest {
  private static final String NAME = UID;
  private static final String ADMIN = "admin";
  private final TerminalStep endStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final RandomStringGenerator generator = new RandomStringGenerator();
  private final String message = generator.getUniqueString();
  private final RuntimeException failure = new RuntimeException(message);
  private final String validationWarning = generator.getUniqueString();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(SystemClockTestSupport.installClock());

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
  void whenPacketLacksConfig_dontAbort() throws Exception {
    testSupport.getPacket().remove(DOMAIN_TOPOLOGY);

    updateDomainStatus();

    testSupport.throwOnCompletionFailure();
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
  void failedStepWithFailureMessage_doesNotContainValidationWarnings() {
    info.addValidationWarning(validationWarning);
    defineScenario().build();

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure));

    assertThat(getRecordedDomain().getStatus().getMessage(), not(containsString(validationWarning)));
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
              .addCondition(new DomainCondition(Completed).withStatus("False")));

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

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenAllDesiredServersRunning_establishCompletedConditionTrue() {

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("True"));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void whenTopologyNotPresent_updateStatusConditions() {
    testSupport.getPacket().remove(DOMAIN_TOPOLOGY);

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("True"));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  @Test
  void whenAllDesiredServersRunningAndMatchingCompletedConditionFound_leaveIt() {
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus("True"));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("True"));
  }

  @Test
  void whenAllDesiredServersRunningAndMismatchedCompletedConditionStatusFound_changeIt() {
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus("False"));

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("True"));
  }

  @Test
  void whenAllDesiredServersRunningButSomeMarkedToBeRolled_establishCompletedConditionFalse() {
    info.setServersToRoll(Map.of("server1", new Step.StepAndPacket(null, null)));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenAllDesiredServersRunningAndMatchingCompletedConditionFound_dontGenerateCompletedEvent() {
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus("True"));
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
          .addCondition(new DomainCondition(Completed).withStatus("False"));
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
          .addCondition(new DomainCondition(Completed).withStatus("False"));
    defineScenario()
          .withCluster("clusterA", "server1")
          .withCluster("clusterB", "server2")
          .withServersReachingState("Unknown","server3")
          .build();

    updateDomainStatus();

    assertThat(getEvents().stream().anyMatch(this::isDomainCompletedEvent), is(false));
  }

  @Test
  void whenAllDesiredServersRunningAndFailedConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenNotAllDesiredServersRunning_establishCompletedConditionFalse() {
    defineScenario()
          .withServers("server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenNotAllDesiredServersRunningAndCompletedFalseConditionFound_ignoreIt() {
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus("False"));
    defineScenario()
          .withCluster("clusterA","server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenNotAllDesiredServersRunningAndCompletedFalseConditionNotFound_addOne() {
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server1")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenNoPodsFailed_dontEstablishFailedCondition() {
    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenNoPodsFailedAndFailedConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenAtLeastOnePodFailed_establishFailedCondition() {
    failPod("server1");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Failed));
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
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("True"));
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Failed).withStatus("True"));
  }

  @Test
  void whenAtLeastOnePodFailed_dontCreateCompletedTrueCondition() {
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Completed).withStatus("True")));
  }

  @Test
  void whenAtLeastOnePodFailedAndCompletedTrueConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Completed).withStatus("True"));
    failPod("server2");

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Completed).withStatus("True")));
  }

  @Test
  void whenNoDynamicClusters_doNotAddReplicasTooHighFailure() {
    defineScenario().withCluster("cluster1", "ms1", "ms2").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenReplicaCountDoesNotExceedMaxReplicasForDynamicCluster_doNotAddReplicasTooHighFailure() {
    domain.setReplicaCount("cluster1", 4);
    defineScenario().addDynamicCluster("cluster1", 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenReplicaCountExceedsMaxReplicasForDynamicCluster_addFailedCondition() {
    domain.setReplicaCount("cluster1", 5);
    defineScenario().addDynamicCluster("cluster1", 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Failed).withReason(ReplicasTooHigh).withMessageContaining("cluster1"));
  }

  @Test
  void withServerStartPolicyNEVER_domainIsNotAvailable() {
    domain.getSpec().setServerStartPolicy("NEVER");
    defineScenario().withServers("server1").notStarting("server1").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenNonClusteredServerNotReady_domainIsNotAvailable() {
    domain.getSpec().setServerStartPolicy("NEVER");
    defineScenario()
          .withServers("server0")
          .withCluster("clusterA", "server1", "server2")
          .withServersReachingState(STANDBY_STATE, "server0")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenNoClustersAndAllNonClusteredServersRunning_domainIsAvailableAndComplete() {
    defineScenario().withServers("server1", "server2").build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True"));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("True"));
  }

  @Test
  void whenClusterIntentionallyShutDownAndAllNonClusteredServersRunning_domainIsAvailableAndComplete() {
    defineScenario()
          .withServers("server1", "server2")
          .withCluster("clusterA", "server3", "server4")
          .notStarting("server3", "server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True"));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("True"));
  }

  @Test
  void whenNoMoreThanMaxUnavailableServersNotRunningInACluster_domainIsAvailable() {
    configureDomain().configureCluster("clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withServersReachingState(SHUTDOWN_STATE, "server3", "server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True"));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenMoreThanMaxUnavailableServersNotRunningInACluster_domainIsNotAvailable() {
    configureDomain().configureCluster("clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2", "server3", "server4")
          .withServersReachingState(SHUTDOWN_STATE, "server2", "server3", "server4")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Available).withStatus("True")));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenNoServersRunningInCluster_domainIsNotAvailable() {
    configureDomain().configureCluster("clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .withServersReachingState(SHUTDOWN_STATE, "server1", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), not(hasCondition(Available).withStatus("True")));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenDomainWasAvailableAndNoLongerIs_domainAvailableConditionIsChangedToFalse() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus("True"));
    configureDomain().configureCluster("clusterA").withMaxUnavailable(2);
    defineScenario()
          .withCluster("clusterA", "server1", "server2")
          .withServersReachingState(SHUTDOWN_STATE, "server1", "server2")
          .build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("False"));
    assertThat(getRecordedDomain(), not(hasCondition(Available).withStatus("True")));
    assertThat(getRecordedDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenAllServersRunningAndAvailableConditionFound_dontGenerateAvailableEvent() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus("True"));
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

  @Test
  void whenAllServersRunningAndAvailableConditionNotFoundCompletedConditionNotFound_generateCompletedEvent() {
    domain.getStatus()
        .addCondition(new DomainCondition(Available).withStatus("False"))
        .addCondition(new DomainCondition(Completed).withStatus("False"));
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
        .addCondition(new DomainCondition(Available).withStatus("False"))
        .addCondition(new DomainCondition(Completed).withStatus("False"));
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
    private String expectedReason;

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
        .addCondition(new DomainCondition(Available).withStatus("False"))
        .addCondition(new DomainCondition(Completed).withStatus("False"));
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
        .addCondition(new DomainCondition(Available).withStatus("False"));
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
    return new V1Pod().metadata(createPodMetadata(serverName)).spec(new V1PodSpec());
  }

  @Test
  void whenDomainLacksStatus_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason(Internal).withMessageContaining(message));
  }

  @Test
  void whenDomainStatusIsNull_removeFailuresStepDoesNothing() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createRemoveFailuresStep());

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenDomainHasFailedCondition_removeFailureStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createRemoveFailuresStep());

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenDomainLacksFailedCondition_failedStepUpdatesDomainWithFailedTrueAndException() {
    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason(Internal).withMessageContaining(message));
  }

  @Test
  void afterIntrospectionFailure_generateDomainProcessingAbortedEvent() {
    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(Introspection, FATAL_INTROSPECTOR_ERROR));

    assertThat(getEvents().stream().anyMatch(this::isDomainProcessingAbortedEvent), is(true));
  }

  private boolean isDomainProcessingAbortedEvent(CoreV1Event e) {
    return DOMAIN_PROCESSING_ABORTED_EVENT.equals(e.getReason());
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
    private final Map<String,String[]> serverStates = new HashMap<>();
    private final List<String> servers = new ArrayList<>();
    private final List<String> nonStartedServers = new ArrayList<>();

    private ScenarioBuilder() {
      configSupport = new WlsDomainConfigSupport("testDomain");
      configSupport.setAdminServerName(ADMIN);
    }

    // Adds a cluster to the topology, along with its servers
    ScenarioBuilder withCluster(String clusterName, String... serverNames) {
      Arrays.stream(serverNames).forEach(serverName -> addClusteredServer(clusterName, serverName));
      configSupport.addWlsCluster(clusterName, serverNames);
      return this;
    }

    void addClusteredServer(String clusterName, String serverName) {
      addServer(serverName);
      Objects.requireNonNull(getPod(serverName).getMetadata()).putLabelsItem(CLUSTERNAME_LABEL, clusterName);
    }

    // adds the server to the topology.
    // if the server is not defined in the domain presence, adds it there as well
    private void addServer(String serverName) {
      defineServerPod(serverName);
      configSupport.addWlsServer(serverName);
      servers.add(serverName);
    }

    ScenarioBuilder addDynamicCluster(String clusterName, int maxServers) {
      configSupport.addWlsCluster(new DynamicClusterConfigBuilder(clusterName)
            .withClusterLimits(1, maxServers)
            .withServerNames(generateServerNames(maxServers)));
      return this;
    }

    private String[] generateServerNames(int maxServers) {
      return IntStream.range(1, maxServers).boxed().map(i -> "ms" + i).toArray(String[]::new);
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

    void build() {
      final WlsDomainConfig domainConfig = configSupport.createDomainConfig();
      testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
      testSupport.addToPacket(SERVER_STATE_MAP, createStateMap());
      testSupport.addToPacket(SERVER_HEALTH_MAP, createHealthMap());
      info.setServerStartupInfo(createServerStartupInfo(domainConfig));
      getLiveServers().forEach(this::addNodeName);
    }

    @Nonnull
    private List<DomainPresenceInfo.ServerStartupInfo> createServerStartupInfo(WlsDomainConfig domainConfig) {
      return getLiveServers().stream()
            .map(domainConfig::getServerConfig)
            .map(config -> new DomainPresenceInfo.ServerStartupInfo(config, "", null))
            .collect(Collectors.toList());
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

    private void addNodeName(String serverName) {
      Objects.requireNonNull(getPod(serverName).getSpec()).setNodeName(toNodeName(serverName));
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
  
  static class MultiFieldMatcher<T> {
    private final String objectDescription;
    private final String notFoundDescription;
    private final List<FieldMatcher<T>> fields = new ArrayList<>();

    MultiFieldMatcher(String objectDescription, String notFoundDescription) {
      this.objectDescription = objectDescription;
      this.notFoundDescription = notFoundDescription;
    }

    void addField(String name, Function<T, String> getter, String expectedValue) {
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
  
  
  static class FieldMatcher<T> {
    private final String name;
    private final Function<T, String> getter;
    private final String expectedValue;

    FieldMatcher(String name, Function<T, String> getter, @Nonnull String expectedValue) {
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
