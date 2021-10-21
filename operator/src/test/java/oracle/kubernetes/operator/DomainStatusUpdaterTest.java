// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.utils.RandomStringGenerator;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainConditionType;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.SERVERS_READY_REASON;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.ADMIN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTTING_DOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.STANDBY_STATE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ConfigChangesPendingRestart;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainStatusUpdaterTest {
  private static final String NAME = UID;
  private final TerminalStep endStep = new TerminalStep();
  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final RandomStringGenerator generator = new RandomStringGenerator();
  private final String message = generator.getUniqueString();
  private final String reason = generator.getUniqueString();
  private final RuntimeException failure = new RuntimeException(message);
  private final String validationWarning = generator.getUniqueString();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport), null);

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger()
        .ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
    mementos.add(ClientFactoryStub.install());

    domain.setStatus(new DomainStatus());

    defineServerPod("server1");
    defineServerPod("server2");
    testSupport.addDomainPresenceInfo(info);
    testSupport.defineResources(domain);
    testSupport.addToPacket(SERVER_STATE_MAP, Collections.emptyMap());
    testSupport.addToPacket(SERVER_HEALTH_MAP, Collections.emptyMap());
  }

  private V1ObjectMeta createPodMetadata(String serverName) {
    return new V1ObjectMeta().namespace(NS).name(serverName).labels(ImmutableMap.of());
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void statusStep_copiesServerStatesFromMaps() {
    testSupport.addToPacket(
        SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE, "server2", SHUTDOWN_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP,
        ImmutableMap.of("server1", overallHealth("health1"), "server2", overallHealth("health2")));
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");
    setClusterAndNodeName(getPod("server2"), "clusterB", "node2");

    configSupport.addWlsCluster("clusterA", "server1");
    configSupport.addWlsCluster("clusterB", "server2");
    generateStartupInfos("server1", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            createServerStatus(RUNNING_STATE, RUNNING_STATE, "clusterA", "node1", "server1")
                .withHealth(overallHealth("health1"))));
    assertThat(
        getServerStatus(getRecordedDomain(), "server2"),
        equalTo(
            createServerStatus(SHUTDOWN_STATE, RUNNING_STATE, "clusterB", "node2", "server2")
                .withHealth(overallHealth("health2"))));
  }

  @Test
  void statusStep_copiesServerStatesFromMaps_standalone() {
    testSupport.addToPacket(
        SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE, "server2", SHUTDOWN_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP,
        ImmutableMap.of("server1", overallHealth("health1"), "server2", overallHealth("health2")));
    setNodeName(getPod("server1"), "node1");
    setNodeName(getPod("server2"), "node2");

    generateStartupInfos("server1", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

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
                .withNodeName("node2")
                .withServerName("server2")
                .withHealth(overallHealth("health2"))));
  }

  private ServerStatus getServerStatus(Domain domain, String serverName) {
    for (ServerStatus status : domain.getStatus().getServers()) {
      if (status.getServerName().equals(serverName)) {
        return status;
      }
    }

    return null;
  }

  private ServerHealth overallHealth(String health) {
    return new ServerHealth().withOverallHealth(health);
  }

  private void setClusterAndNodeName(V1Pod pod, String clusterName, String nodeName) {
    Objects.requireNonNull(pod.getMetadata()).setLabels(ImmutableMap.of(LabelConstants.CLUSTERNAME_LABEL, clusterName));
    pod.setSpec(new V1PodSpec().nodeName(nodeName));
  }

  private void setNodeName(V1Pod pod,String nodeName) {
    pod.setSpec(new V1PodSpec().nodeName(nodeName));
  }

  private V1Pod getPod(String serverName) {
    return info.getServerPod(serverName);
  }

  @Test
  void statusStep_usesServerFromWlsConfig() {
    configureServer("server4").withDesiredState(ADMIN_STATE);
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server3", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP,
        ImmutableMap.of("server3", overallHealth("health3"), "server4", overallHealth("health4")));
    defineServerPod("server3");
    defineServerPod("server4");
    configSupport.addWlsCluster("clusterC", "server3", "server4");
    generateStartupInfos("server3", "server4");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server3"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withDesiredState(RUNNING_STATE)
                .withClusterName("clusterC")
                .withServerName("server3")
                .withHealth(overallHealth("health3"))));
    assertThat(
        getServerStatus(getRecordedDomain(), "server4"),
        equalTo(
            new ServerStatus()
                .withState(SHUTDOWN_STATE)
                .withDesiredState(ADMIN_STATE)
                .withClusterName("clusterC")
                .withServerName("server4")
                .withHealth(overallHealth("health4"))));
  }

  @Test
  void statusStep_copiesClusterFromWlsConfigAndNodeNameFromPod() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server2", STANDBY_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server2", overallHealth("health2")));
    configSupport.addWlsCluster("wlsCluster", "server2");
    generateStartupInfos("server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    setClusterAndNodeName(getPod("server2"), "clusterB", "node2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server2"),
        equalTo(
            createServerStatus(STANDBY_STATE, RUNNING_STATE, "wlsCluster", "node2", "server2")
                .withHealth(overallHealth("health2"))));
  }

  @Test
  void statusStep_updatesDomainWhenHadNoStatus() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server1", overallHealth("health1")));
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");

    configSupport.addWlsCluster("clusterA", "server1");
    generateStartupInfos("server1");
    domain.setStatus(null);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            createServerStatus(RUNNING_STATE, RUNNING_STATE, "clusterA", "node1", "server1")
                .withHealth(overallHealth("health1"))));
  }

  @Test
  void statusStep_shutdownDesiredState_ifServerHasNoStartupInfos() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server1", overallHealth("health1")));
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");

    configSupport.addWlsCluster("clusterA", "server1");
    configSupport.addWlsServer("server1");
    domain.setStatus(null);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            createServerStatus(RUNNING_STATE, SHUTDOWN_STATE, "clusterA", "node1", "server1")
                .withHealth(overallHealth("health1"))));
  }

  @Test
  void statusStep_shutdownDesiredState_ifStandAloneServerHasNoStartupInfos() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server1", overallHealth("health1")));
    setNodeName(getPod("server1"),"node1");

    configSupport.addWlsServer("server1");
    domain.setStatus(null);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withDesiredState(SHUTDOWN_STATE)
                .withNodeName("node1")
                .withServerName("server1")
                .withHealth(overallHealth("health1"))));
  }

  @Test
  void statusStep_shutdownDesiredState_ifServerIsServiceOnly() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server1", overallHealth("health1")));
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");

    configSupport.addWlsCluster("clusterA", "server1");
    generateServiceOnlyStartupInfos("server1");

    domain.setStatus(null);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            createServerStatus(RUNNING_STATE, SHUTDOWN_STATE, "clusterA", "node1", "server1")
                .withHealth(overallHealth("health1"))));
  }

  @Test
  void statusStep_shutdownDesiredState_ifStandAloneServerIsServiceOnly() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server1", overallHealth("health1")));
    setNodeName(getPod("server1"), "node1");

    generateServiceOnlyStartupInfos("server1");

    domain.setStatus(null);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withDesiredState(SHUTDOWN_STATE)
                .withNodeName("node1")
                .withServerName("server1")
                .withHealth(overallHealth("health1"))));
  }

  @Test
  void statusStep_containsValidationWarnings() {
    info.addValidationWarning(validationWarning);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain().getStatus().getMessage(), containsString(validationWarning));
  }

  @Test
  void progressingStep_containsValidationWarnings() {
    info.addValidationWarning(validationWarning);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, true, endStep));

    assertThat(getRecordedDomain().getStatus().getMessage(), containsString(validationWarning));
  }

  @Test
  void failedStepWithFailureMessage_doesNotContainValidationWarnings() {
    info.addValidationWarning(validationWarning);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure, endStep));

    assertThat(getRecordedDomain().getStatus().getMessage(), not(containsString(validationWarning)));
  }

  @Test
  void whenStatusUnchanged_statusStepDoesNotUpdateDomain() {
    info = new DomainPresenceInfo(domain);
    testSupport.addDomainPresenceInfo(info);
    defineServerPod("server1");
    domain.setStatus(
        new DomainStatus()
            .withServers(
                Collections.singletonList(
                    new ServerStatus()
                        .withState(RUNNING_STATE)
                        .withClusterName("clusterA")
                        .withNodeName("node1")
                        .withServerName("server1")
                        .withHealth(overallHealth("health1"))))
            .addCondition(
                new DomainCondition(Available)
                    .withStatus("True")
                    .withReason(SERVERS_READY_REASON)));
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server1", overallHealth("health1")));
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");

    testSupport.clearNumCalls();
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  void whenDomainHasNoClusters_statusLacksReplicaCount() {
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain().getStatus().getReplicas(), nullValue());
  }

  @Test
  void forDomainWithAClusterOf3Servers_statusHasCorrectReplicaCount() {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createWlsDomainConfigSupport().createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain().getStatus().getReplicas(), equalTo(3));
  }

  @Test
  void whenAllSeverPodsInClusterAreBeingTerminated_ClusterStatusDoesNotShowReadyReplicaCount() {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createWlsDomainConfigSupport().createDomainConfig());

    markServerPodsInClusterForDeletion();
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getClusterStatus().getReadyReplicas(), nullValue());
  }

  private void markServerPodsInClusterForDeletion() {
    info.getServerPod("server1").getMetadata().setDeletionTimestamp(OffsetDateTime.now());
    info.getServerPod("server2").getMetadata().setDeletionTimestamp(OffsetDateTime.now());
    info.getServerPod("server3").getMetadata().setDeletionTimestamp(OffsetDateTime.now());
  }

  @Test
  void whenAllSeverPodsInClusterAreTerminated_ClusterStatusDoesNotShowReadyReplicaCount() {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createWlsDomainConfigSupport().createDomainConfig());

    deleteServerPodsInCluster();
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getClusterStatus().getReadyReplicas(), nullValue());
  }

  private void deleteServerPodsInCluster() {
    info.setServerPod("server1", null);
    info.setServerPod("server2", null);
    info.setServerPod("server3", null);
  }

  @Test
  void whenAllSeverPodsInClusterAreBeingTerminated_StatusShowsServersShuttingDown() {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createWlsDomainConfigSupport().createDomainConfig());

    markServerPodsInClusterForDeletion();
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
            getRecordedDomain().getStatus().getServers(),
            hasItems(
                    createServerStatus(SHUTTING_DOWN_STATE, SHUTDOWN_STATE, "cluster1", "server1", "server1"),
                    createServerStatus(SHUTTING_DOWN_STATE, SHUTDOWN_STATE, "cluster1", "server2", "server2"),
                    createServerStatus(SHUTTING_DOWN_STATE, SHUTDOWN_STATE, "cluster1", "server3", "server3")));
  }

  private ServerStatus createServerStatus(String state, String desiredState,
                                          String clusterName, String nodeName, String serverName) {
    return new ServerStatus()
            .withState(state)
            .withDesiredState(desiredState)
            .withClusterName(clusterName)
            .withNodeName(nodeName)
            .withServerName(serverName);
  }

  @Test
  void whenAllSeverPodsInClusterAreTerminated_StatusShowsServersShuttingDown() {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createWlsDomainConfigSupport().createDomainConfig());

    deleteServerPodsInCluster();
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
            getRecordedDomain().getStatus().getServers(),
            hasItems(
                    createServerStatus(SHUTDOWN_STATE, SHUTDOWN_STATE, "cluster1", null, "server1"),
                    createServerStatus(SHUTDOWN_STATE, SHUTDOWN_STATE, "cluster1", null, "server2"),
                    createServerStatus(SHUTDOWN_STATE, SHUTDOWN_STATE, "cluster1", null, "server3")));
  }

  @NotNull
  private WlsDomainConfigSupport createWlsDomainConfigSupport() {
    defineCluster("cluster1", "server1", "server2", "server3");

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsServer("server2");
    configSupport.addWlsServer("server3");
    configSupport.addWlsCluster("cluster1", "server1", "server2", "server3");
    return configSupport;
  }

  private ClusterStatus getClusterStatus() {
    return getRecordedDomain().getStatus().getClusters().stream().findFirst().orElse(null);
  }

  @Test
  void whenDomainHasMultipleClusters_statusLacksReplicaCount() {
    defineCluster("cluster1", "server1", "server2", "server3");
    defineCluster("cluster2", "server4", "server5", "server6", "server7");
    defineCluster("cluster3", "server8", "server9");

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsServer("server2");
    configSupport.addWlsServer("server3");
    configSupport.addWlsCluster("cluster1", "server1", "server2", "server3");
    configSupport.addWlsServer("server4");
    configSupport.addWlsServer("server5");
    configSupport.addWlsServer("server6");
    configSupport.addWlsServer("server7");
    configSupport.addWlsCluster("cluster2", "server4", "server5", "server6", "server7");
    configSupport.addWlsServer("server8");
    configSupport.addWlsServer("server9");
    configSupport.addWlsCluster("cluster3", "server8", "server9");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain().getStatus().getReplicas(), nullValue());
  }

  @Test
  void whenAllDesiredServersRunning_establishAvailableCondition() {
    setAllDesiredServersRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getRecordedDomain(),
        hasCondition(Available).withStatus("True").withReason(SERVERS_READY_REASON));
    assertThat(
        getRecordedDomain().getApiVersion(),
        equalTo(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE));
  }

  private void setAllDesiredServersRunning() {
    configureServer("server1").withDesiredState("ADMIN");
    configureServer("server2").withDesiredState("ADMIN");
    generateStartupInfos("server1", "server2");
    testSupport.addToPacket(
        SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE, "server2", STANDBY_STATE));
  }

  @Test
  void whenAllDesiredServersRunningAndMatchingAvailableConditionFound_ignoreIt() {
    domain
        .getStatus()
        .addCondition(
            new DomainCondition(Available).withStatus("True").withReason(SERVERS_READY_REASON));
    setAllDesiredServersRunning();

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsCluster("clusterA", "server1");
    configSupport.addWlsServer("server2");
    configSupport.addWlsCluster("clusterB", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getRecordedDomain(),
        hasCondition(Available).withStatus("True").withReason(SERVERS_READY_REASON));
  }

  @Test
  void whenAllDesiredServersRunningAndMismatchedAvailableConditionReasonFound_changeIt() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus("True"));
    setAllDesiredServersRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getRecordedDomain(),
        hasCondition(Available).withStatus("True").withReason(SERVERS_READY_REASON));
  }

  @Test
  void whenAllDesiredServersRunningAndMismatchedAvailableConditionStatusFound_changeIt() {
    domain
        .getStatus()
        .addCondition(new DomainCondition(Available).withReason(SERVERS_READY_REASON));
    setAllDesiredServersRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getRecordedDomain(),
        hasCondition(Available).withStatus("True").withReason(SERVERS_READY_REASON));
  }

  @Test
  void whenAllDesiredServersRunningAndProgressingConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing));
    setAllDesiredServersRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  void whenNotAllDesiredServersRunning_dontEstablishAvailableCondition() {
    setDesiredServerNotRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  private void setDesiredServerNotRunning() {
    configureServer("server1").withDesiredState("RUNNING");
    configureServer("server2").withDesiredState("ADMIN");
    generateStartupInfos("server1", "server2");
    testSupport.addToPacket(
        SERVER_STATE_MAP, ImmutableMap.of("server1", STANDBY_STATE, "server2", RUNNING_STATE));
  }

  @Test
  void whenNotAllDesiredServersRunningAndProgressingConditionFound_ignoreIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing));
    setDesiredServerNotRunning();

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsServer("server2");
    configSupport.addWlsCluster("clusterA", "server1", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing));
  }

  @Test
  void whenNotAllDesiredServersRunningAndProgressingConditionNotFound_addOne() {
    setDesiredServerNotRunning();

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsServer("server2");
    configSupport.addWlsCluster("clusterA", "server1", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing));
  }

  @Test
  void whenPodFailedAndProgressingConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing));
    setDesiredServerNotRunning();
    failPod("server1");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  void whenNoPodsFailed_dontEstablishFailedCondition() {
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenNoPodsFailedAndFailedConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenAtLeastOnePodFailed_establishFailedCondition() {
    failPod("server1");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Failed));
  }

  @Test
  void whenAtLeastOnePodAndFailedConditionTrueFound_leaveIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("True"));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Failed).withStatus("True"));
  }

  @Test
  void whenAtLeastOnePodFailedAndFailedConditionFalseFound_changeIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("False "));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Failed).withStatus("True"));
  }

  @Test
  void whenAtLeastOnePodFailed_doneCreateAvailableCondition() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("False "));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  @Test
  void whenAtLeastOnePodFailedAndAvailableConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  @Test
  void whenTwoConditionUpdatesScheduled_useResultOfFirstToComputeSecond() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus("False"));
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("True").withReason("Initial"));

    testSupport.runSteps(
          Step.chain(
                DomainStatusUpdater.createProgressingStep("Modifying", false, null),
                DomainStatusUpdater.createAvailableStep("Test complete", null))
    );

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True"));

  }

  // 1. response step must call onFailure to repeat the initiating step on a 500 error, in order to recompute the patch;
  //    potentially, multiple repeats may be needed, but that should be rare. Maybe 3 tries?
  // 2. will still need to update the packet for this to work. That probably needs to happen on as part of this.

  private void failPod(String serverName) {
    getPod(serverName).setStatus(new V1PodStatus().phase("Failed"));
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  private ServerConfigurator configureServer(String serverName) {
    return configureDomain().configureServer(serverName);
  }

  private void generateServiceOnlyStartupInfos(String... serverNames) {
    generateStartupInfos(true, serverNames);
  }

  private void generateStartupInfos(String... serverNames) {
    generateStartupInfos(false, serverNames);
  }

  private void generateStartupInfos(boolean serviceOnly, String... serverNames) {
    List<DomainPresenceInfo.ServerStartupInfo> startupInfos = new ArrayList<>();
    for (String serverName : serverNames) {
      configSupport.addWlsServer(serverName);
    }
    WlsDomainConfig domainConfig = configSupport.createDomainConfig();
    for (String serverName : serverNames) {
      String clusterName = getClusterName(serverName);
      startupInfos.add(
          new DomainPresenceInfo.ServerStartupInfo(
              domainConfig.getServerConfig(serverName),
              clusterName,
              domain.getServer(serverName, clusterName),
              serviceOnly));
    }
    info.setServerStartupInfo(startupInfos);
  }

  private String getClusterName(String serverName) {
    return Optional.ofNullable(getPod(serverName).getMetadata())
          .map(V1ObjectMeta::getLabels)
          .map(l -> l.get(LabelConstants.CLUSTERNAME_LABEL))
          .orElse(null);
  }

  private void defineCluster(String clusterName, String... serverNames) {
    for (String serverName : serverNames) {
      definePodWithCluster(serverName, clusterName);
    }
  }

  private void definePodWithCluster(String serverName, String clusterName) {
    defineServerPod(serverName);
    setClusterAndNodeName(getPod(serverName), clusterName, serverName);
  }

  private void defineServerPod(String serverName) {
    info.setServerPod(serverName, createPod(serverName));
  }

  private V1Pod createPod(String serverName) {
    return new V1Pod().metadata(createPodMetadata(serverName)).spec(new V1PodSpec());
  }

  @Test
  void whenDomainHasNoStatus_progressingStepUpdatesItWithProgressingTrueAndReason() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing).withStatus("True").withReason(reason));
  }

  @Test
  void
      whenDomainHasNoProgressingCondition_progressingStepUpdatesItWithProgressingTrueAndReason() {
    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing).withStatus("True").withReason(reason));
  }

  @Test
  void
      whenDomainHasProgressingNonTrueCondition_progressingStepUpdatesItWithProgressingTrueAndReason() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("?"));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing).withStatus("True").withReason(reason));
    assertThat(getRecordedDomain().getStatus().getConditions(), hasSize(1));
  }

  @Test
  void whenDomainHasProgressingTrueConditionWithDifferentReason_progressingStepUpdatesReason() {
    domain
        .getStatus()
        .addCondition(
            new DomainCondition(Progressing)
                .withStatus("True")
                .withReason(generator.getUniqueString()));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing).withStatus("True").withReason(reason));
    assertThat(getRecordedDomain().getStatus().getConditions(), hasSize(1));
    assertThat(testSupport.getNumCalls(), equalTo(1));
  }

  @Test
  void whenDomainHasProgressingTrueConditionWithSameReason_progressingStepIgnoresIt() {
    domain
        .getStatus()
        .addCondition(new DomainCondition(Progressing).withStatus("True").withReason(reason));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  void whenDomainHasFailedCondition_progressingStepShouldNotRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Failed));
  }

  @Test
  void whenDomainHasAvailableCondition_progressingStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  @Test
  void whenDomainHasAvailableCondition_progressingStepWithPreserveAvailableIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, true, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available));
  }

  @Test
  void whenDomainHasNoConditions_endProgressingStepDoesNothing() {
    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  void whenDomainHasProgressingTrueCondition_endProgressingStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("True"));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  void whenDomainHasProgressingNotTrueCondition_endProgressingStepIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("?"));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  void whenDomainHasAvailableCondition_endProgressingStepIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  void whenDomainHasFailedCondition_endProgressingStepIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  void whenDomainLacksStatus_availableStepUpdatesDomainWithAvailableTrueAndReason() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
  }

  @Test
  void
      whenDomainLacksAvailableCondition_availableStepUpdatesDomainWithAvailableTrueAndReason() {
    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
  }

  @Test
  void whenDomainHasAvailableFalseCondition_availableStepUpdatesItWithTrueAndReason() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus("False"));

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
    assertThat(getRecordedDomain().getStatus().getConditions(), hasSize(1));
  }

  @Test
  void whenDomainHasProgressingCondition_availableStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing));

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  void whenDomainHasFailedCondition_availableStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenDomainLacksStatus_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure, endStep));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason("Exception").withMessage(message));
  }

  // ---

  @Test
  void whenDomainLacksFailedCondition_failedStepUpdatesDomainWithFailedTrueAndException() {
    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure, endStep));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason("Exception").withMessage(message));
  }

  @Test
  void whenDomainHasFailedFalseCondition_failedStepUpdatesItWithTrueAndException() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("False"));

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure, endStep));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason("Exception").withMessage(message));
    assertThat(getRecordedDomain().getStatus().getConditions(), hasSize(1));
  }

  @Test
  void whenDomainHasProgressingTrueCondition_failedStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("True"));

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  void whenPacketNotPopulatedBeforeUpdateServerStatus_resourceVersionUpdated() {
    setupInitialServerStatus();
    String cachedResourceVersion = getRecordedDomain().getMetadata().getResourceVersion();

    // Clear the server maps in the packet, and run StatusUpdateStep, the domain resource
    // version should be updated because server health information is removed from domain status.
    clearPacketServerStatusMaps();
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

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
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");
    setClusterAndNodeName(getPod("server2"), "clusterB", "node2");

    configSupport.addWlsCluster("clusterA", "server1");
    configSupport.addWlsCluster("clusterB", "server2");
    generateStartupInfos("server1", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    // Run StatusUpdateStep with server maps in the packet to set up the initial domain status
    testSupport.addToPacket(
        SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE, "server2", SHUTDOWN_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP,
        ImmutableMap.of("server1", overallHealth("health1"), "server2", overallHealth("health2")));

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));
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
    testSupport.addToPacket(MII_DYNAMIC_UPDATE, MII_DYNAMIC_UPDATE_RESTART_REQUIRED);
    configureDomain().withMIIOnlineUpdate();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getDomainStatusCondition(ConfigChangesPendingRestart), notNullValue());
  }

  private DomainCondition getDomainStatusCondition(DomainConditionType conditionType) {
    return getDomainStatusConditions().stream().filter(c -> c.getType().equals(conditionType)).findFirst().orElse(null);
  }

  private List<DomainCondition> getDomainStatusConditions() {
    return Optional.of(getRecordedDomain())
        .map(Domain::getStatus)
        .map(DomainStatus::getConditions)
        .orElse(Collections.emptyList());
  }

  @Test
  void whenNonDynamicMiiChangeAndUpdateAndRollSelected_dontAddRestartRequiredCondition() {
    testSupport.addToPacket(MII_DYNAMIC_UPDATE, MII_DYNAMIC_UPDATE_RESTART_REQUIRED);
    configureDomain().withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getDomainStatusCondition(ConfigChangesPendingRestart), nullValue());
  }
}
