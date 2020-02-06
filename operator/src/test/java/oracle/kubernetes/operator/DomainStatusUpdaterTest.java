// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
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
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.SERVERS_READY_REASON;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.STANDBY_STATE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainStatusUpdaterTest {
  private static final String NAME = UID;
  private final TerminalStep endStep = new TerminalStep();
  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private Domain domain = DomainProcessorTestSetup.createTestDomain();
  private DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private RandomStringGenerator generator = new RandomStringGenerator();
  private final String message = generator.getUniqueString();
  private String reason = generator.getUniqueString();
  private RuntimeException failure = new RuntimeException(message);

  /**
   * Setup test environment.
   * @throws NoSuchFieldException if test support fails to install.
   */
  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());

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

  /**
   * Cleanup test environment.
   * @throws Exception if test support fails.
   */
  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void statusStep_copiesServerStatesFromMaps() {
    testSupport.addToPacket(
        SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE, "server2", SHUTDOWN_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP,
        ImmutableMap.of("server1", overallHealth("health1"), "server2", overallHealth("health2")));
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");
    setClusterAndNodeName(getPod("server2"), "clusterB", "node2");

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsCluster("clusterA", "server1");
    configSupport.addWlsServer("server2");
    configSupport.addWlsCluster("clusterB", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withClusterName("clusterA")
                .withNodeName("node1")
                .withServerName("server1")
                .withHealth(overallHealth("health1"))));
    assertThat(
        getServerStatus(getRecordedDomain(), "server2"),
        equalTo(
            new ServerStatus()
                .withState(SHUTDOWN_STATE)
                .withClusterName("clusterB")
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

  private V1Pod getPod(String serverName) {
    return info.getServerPod(serverName);
  }

  @Test
  public void statusStep_usesServerFromWlsConfig() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server3", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP,
        ImmutableMap.of("server3", overallHealth("health3"), "server4", overallHealth("health4")));
    configSupport.addWlsServer("server3");
    configSupport.addWlsServer("server4");
    configSupport.addWlsCluster("clusterC", "server3", "server4");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server3"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withClusterName("clusterC")
                .withServerName("server3")
                .withHealth(overallHealth("health3"))));
    assertThat(
        getServerStatus(getRecordedDomain(), "server4"),
        equalTo(
            new ServerStatus()
                .withState(SHUTDOWN_STATE)
                .withClusterName("clusterC")
                .withServerName("server4")
                .withHealth(overallHealth("health4"))));
  }

  @Test
  public void statusStep_copiesClusterFromWlsConfigAndNodeNameFromPod() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server2", STANDBY_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server2", overallHealth("health2")));
    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server2");
    configSupport.addWlsCluster("wlsCluster", "server2");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    setClusterAndNodeName(getPod("server2"), "clusterB", "node2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server2"),
        equalTo(
            new ServerStatus()
                .withState(STANDBY_STATE)
                .withClusterName("wlsCluster")
                .withNodeName("node2")
                .withServerName("server2")
                .withHealth(overallHealth("health2"))));
  }

  @Test
  public void statusStep_updatesDomainWhenHadNoStatus() {
    testSupport.addToPacket(SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE));
    testSupport.addToPacket(
        SERVER_HEALTH_MAP, ImmutableMap.of("server1", overallHealth("health1")));
    setClusterAndNodeName(getPod("server1"), "clusterA", "node1");

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsCluster("clusterA", "server1");
    domain.setStatus(null);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getServerStatus(getRecordedDomain(), "server1"),
        equalTo(
            new ServerStatus()
                .withState(RUNNING_STATE)
                .withClusterName("clusterA")
                .withNodeName("node1")
                .withServerName("server1")
                .withHealth(overallHealth("health1"))));
  }

  @Test
  public void whenStatusUnchanged_statusStepDoesNotUpdateDomain() {
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
  public void whenDomainHasNoClusters_statusLacksReplicaCount() {
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain().getStatus().getReplicas(), nullValue());
  }

  @Test
  public void whenDomainHasOneCluster_statusReplicaCountShowsServersInThatCluster() {
    defineCluster("cluster1", "server1", "server2", "server3");

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
    configSupport.addWlsServer("server1");
    configSupport.addWlsServer("server2");
    configSupport.addWlsServer("server3");
    configSupport.addWlsCluster("cluster1", "server1", "server2", "server3");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain().getStatus().getReplicas(), equalTo(3));
  }

  @Test
  public void whenDomainHasMultipleClusters_statusLacksReplicaCount() {
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
  public void whenAllDesiredServersRunning_establishAvailableCondition() {
    setAllDesiredServersRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getRecordedDomain(),
        hasCondition(Available).withStatus("True").withReason(SERVERS_READY_REASON));
  }

  private void setAllDesiredServersRunning() {
    configureServer("server1").withDesiredState("ADMIN");
    configureServer("server2").withDesiredState("ADMIN");
    generateStartupInfos("server1", "server2");
    testSupport.addToPacket(
        SERVER_STATE_MAP, ImmutableMap.of("server1", RUNNING_STATE, "server2", STANDBY_STATE));
  }

  @Test
  public void whenAllDesiredServersRunningAndMatchingAvailableConditionFound_ignoreIt() {
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
  public void whenAllDesiredServersRunningAndMismatchedAvailableConditionReasonFound_changeIt() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus("True"));
    setAllDesiredServersRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(
        getRecordedDomain(),
        hasCondition(Available).withStatus("True").withReason(SERVERS_READY_REASON));
  }

  @Test
  public void whenAllDesiredServersRunningAndMismatchedAvailableConditionStatusFound_changeIt() {
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
  public void whenAllDesiredServersRunningAndProgressingConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing));
    setAllDesiredServersRunning();

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  public void whenNotAllDesiredServersRunning_dontEstablishAvailableCondition() {
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
  public void whenNotAllDesiredServersRunningAndProgressingConditionFound_ignoreIt() {
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
  public void whenNotAllDesiredServersRunningAndProgressingConditionNotFound_addOne() {
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
  public void whenPodFailedAndProgressingConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing));
    setDesiredServerNotRunning();
    failPod("server1");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  public void whenNoPodsFailed_dontEstablishFailedCondition() {
    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  public void whenNoPodsFailedAndFailedConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  public void whenAtLeastOnePodFailed_establishFailedCondition() {
    failPod("server1");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Failed));
  }

  @Test
  public void whenAtLeastOnePodAndFailedConditionTrueFound_leaveIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("True"));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Failed).withStatus("True"));
  }

  @Test
  public void whenAtLeastOnePodFailedAndFailedConditionFalseFound_changeIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("False "));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), hasCondition(Failed).withStatus("True"));
  }

  @Test
  public void whenAtLeastOnePodFailed_doneCreateAvailableCondition() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("False "));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  @Test
  public void whenAtLeastOnePodFailedAndAvailableConditionFound_removeIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));
    failPod("server2");

    testSupport.runSteps(DomainStatusUpdater.createStatusUpdateStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  @Test
  public void whenTwoConditionUpdatesScheduled_useResultOfFirstToComputeSecond() {
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

  private void generateStartupInfos(String... serverNames) {
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
              domain.getServer(serverName, clusterName)));
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
  public void whenDomainHasNoStatus_progressingStepUpdatesItWithProgressingTrueAndReason() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing).withStatus("True").withReason(reason));
  }

  @Test
  public void
      whenDomainHasNoProgressingCondition_progressingStepUpdatesItWithProgressingTrueAndReason() {
    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing).withStatus("True").withReason(reason));
  }

  @Test
  public void
      whenDomainHasProgressingNonTrueCondition_progressingStepUpdatesItWithProgressingTrueAndReason() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("?"));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), hasCondition(Progressing).withStatus("True").withReason(reason));
    assertThat(getRecordedDomain().getStatus().getConditions(), hasSize(1));
  }

  @Test
  public void whenDomainHasProgressingTrueConditionWithDifferentReason_progressingStepUpdatesReason() {
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
  public void whenDomainHasProgressingTrueConditionWithSameReason_progressingStepIgnoresIt() {
    domain
        .getStatus()
        .addCondition(new DomainCondition(Progressing).withStatus("True").withReason(reason));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  public void whenDomainHasFailedCondition_progressingStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  public void whenDomainHasAvailableCondition_progressingStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, false, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  @Test
  public void whenDomainHasAvailableCondition_progressingStepWithPreserveAvailableIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));

    testSupport.runSteps(DomainStatusUpdater.createProgressingStep(reason, true, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available));
  }

  @Test
  public void whenDomainHasNoConditions_endProgressingStepDoesNothing() {
    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  public void whenDomainHasProgressingTrueCondition_endProgressingStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("True"));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  public void whenDomainHasProgressingNotTrueCondition_endProgressingStepIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("?"));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  public void whenDomainHasAvailableCondition_endProgressingStepIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  public void whenDomainHasFailedCondition_endProgressingStepIgnoresIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createEndProgressingStep(endStep));

    assertThat(testSupport.getNumCalls(), equalTo(0));
  }

  @Test
  public void whenDomainLacksStatus_availableStepUpdatesDomainWithAvailableTrueAndReason() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
  }

  @Test
  public void
      whenDomainLacksAvailableCondition_availableStepUpdatesDomainWithAvailableTrueAndReason() {
    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
  }

  @Test
  public void whenDomainHasAvailableFalseCondition_availableStepUpdatesItWithTrueAndReason() {
    domain.getStatus().addCondition(new DomainCondition(Available).withStatus("False"));

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
    assertThat(getRecordedDomain().getStatus().getConditions(), hasSize(1));
  }

  @Test
  public void whenDomainHasProgressingCondition_availableStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing));

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), hasCondition(Available).withStatus("True").withReason(reason));
    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  public void whenDomainHasFailedCondition_availableStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createAvailableStep(reason, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  public void whenDomainLacksStatus_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createFailedStep(failure, endStep));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason("Exception").withMessage(message));
  }

  // ---

  @Test
  public void whenDomainLacksFailedCondition_failedStepUpdatesDomainWithFailedTrueAndException() {
    testSupport.runSteps(DomainStatusUpdater.createFailedStep(failure, endStep));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason("Exception").withMessage(message));
  }

  @Test
  public void whenDomainHasFailedFalseCondition_failedStepUpdatesItWithTrueAndException() {
    domain.getStatus().addCondition(new DomainCondition(Failed).withStatus("False"));

    testSupport.runSteps(DomainStatusUpdater.createFailedStep(failure, endStep));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason("Exception").withMessage(message));
    assertThat(getRecordedDomain().getStatus().getConditions(), hasSize(1));
  }

  @Test
  public void whenDomainHasProgressingTrueCondition_failedStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Progressing).withStatus("True"));

    testSupport.runSteps(DomainStatusUpdater.createFailedStep(failure, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Progressing)));
  }

  @Test
  public void whenDomainHasAvailableCondition_failedStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Available));

    testSupport.runSteps(DomainStatusUpdater.createFailedStep(failure, endStep));

    assertThat(getRecordedDomain(), not(hasCondition(Available)));
  }

  private Domain getRecordedDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, NAME);
  }
}
