// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.PodWatcher;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.ThreadFactoryTestBase;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.steps.ManagedServerUpIteratorStep.SCHEDULING_DETECTION_DELAY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ManagedServerUpIteratorStepTest extends ThreadFactoryTestBase implements WatchListener<V1Pod>,
        StubWatchFactory.AllWatchesClosedListener {

  protected static final String DOMAIN_NAME = "domain1";
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  protected static final String KUBERNETES_UID = "12345";
  private static final String ADMIN = "asName";
  private static final String CLUSTER1 = "cluster1";
  private static final String CLUSTER2 = "cluster2";
  private static final int SCHEDULING_DELAY_MSEC = SCHEDULING_DETECTION_DELAY / 2;
  private static final int POD_READY_DELAY_SEC = 9;
  private static final int READY_DETECTION_DELAY = 10;
  private static final int NUM_CLUSTERS = 2;
  private static final boolean INCLUDE_SERVER_OUT_IN_POD_LOG = true;
  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final String LATEST_IMAGE = "image:latest";
  private static final String MS_PREFIX = "ms";
  private static final String MS1 = MS_PREFIX + "1";
  private static final String MS2 = MS_PREFIX + "2";
  private static final String MS3 = MS_PREFIX + "3";
  private static final String MS4 = MS_PREFIX + "4";
  private static final int MAX_SERVERS = 5;
  private static final int PORT = 8001;
  private static final String[] MANAGED_SERVER_NAMES =
          IntStream.rangeClosed(1, MAX_SERVERS)
                  .mapToObj(ManagedServerUpIteratorStepTest::getManagedServerName).toArray(String[]::new);
  private final AtomicBoolean stopping = new AtomicBoolean(false);
  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("234");
  private final PodWatcher watcher = createWatcher(NS, stopping, INITIAL_RESOURCE_VERSION);
  final TuningParameters.WatchTuning tuning = new TuningParameters.WatchTuning(30, 0, 5, 24);

  @Nonnull
  private static String getManagedServerName(int n) {
    return MS_PREFIX + n;
  }

  private final Domain domain = createDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);

  private final Step nextStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfoWithAdminServer();
  private final WlsDomainConfig domainConfig = createDomainConfig();
  private final Collection<ServerStartupInfo> startupInfos = new ArrayList<>();

  private static WlsDomainConfig createDomainConfig() {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(CLUSTER1);
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig("base_domain")
            .withAdminServer(ADMIN, "domain1-admin-server", 7001)
            .withCluster(clusterConfig);
  }

  private DomainPresenceInfo createDomainPresenceInfoWithAdminServer() {
    DomainPresenceInfo dpi = new DomainPresenceInfo(domain);
    addServer(dpi, ADMIN);
    return dpi;
  }

  private Domain createDomain() {
    return new Domain()
            .withApiVersion(KubernetesConstants.DOMAIN_VERSION)
            .withKind(KubernetesConstants.DOMAIN)
            .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME).uid(KUBERNETES_UID))
            .withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec()
            .withDomainUid(UID)
            .withWebLogicCredentialsSecret(new V1SecretReference().name(CREDENTIALS_SECRET_NAME))
            .withIncludeServerOutInPodLog(INCLUDE_SERVER_OUT_IN_POD_LOG)
            .withImage(LATEST_IMAGE);
  }

  @SuppressWarnings("SameParameterValue")
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
            .putLabelsItem(SERVERNAME_LABEL, serverName);
  }

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger()
            .ignoringLoggedExceptions(ApiException.class, InterruptedException.class));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(StubWatchFactory.install());
    StubWatchFactory.setListener(this);

    testSupport.defineResources(domain);
    testSupport
            .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig)
            .addDomainPresenceInfo(domainPresenceInfo);
    testSupport.doOnCreate(POD, p -> schedulePodUpdates((V1Pod) p));
    testSupport.addComponent(
            ProcessingConstants.PODWATCHER_COMPONENT_NAME,
            PodAwaiterStepFactory.class,
            watcher);
  }

  // Invoked when a pod is created to simulate the Kubernetes behavior in which a pod is scheduled on a node
  // very quickly, and then takes much longer actually to become ready.
  void schedulePodUpdates(V1Pod pod) {
    testSupport.schedule(() -> setPodScheduled(pod), SCHEDULING_DELAY_MSEC, TimeUnit.MILLISECONDS);
    testSupport.schedule(() -> setPodReady(pod), POD_READY_DELAY_SEC, TimeUnit.SECONDS);
  }

  // Marks the specified pod as having been scheduled on a Kubernetes node.
  private void setPodScheduled(V1Pod pod) {
    Objects.requireNonNull(pod.getSpec()).setNodeName("aNode");
  }

  // Marks the specified pod as having become ready.
  private void setPodReady(V1Pod pod) {
    pod.status(createPodReadyStatus());
  }

  private V1PodStatus createPodReadyStatus() {
    return new V1PodStatus()
          .phase("Running")
          .addConditionsItem(new V1PodCondition().status("True").type("Ready"));
  }

  @AfterEach
  public void tearDown() throws Exception {
    shutDownThreads();
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  protected PodWatcher createWatcher(String ns, AtomicBoolean stopping, BigInteger rv) {
    return PodWatcher.create(this, ns, rv.toString(), tuning, this, stopping);
  }

  @Test
  public void withMultipleServersAvailableToStart_onlyOneForEachClusterInitiallyStarts() {
    configureCluster(CLUSTER1).withMaxConcurrentStartup(0);
    configureCluster(CLUSTER2).withMaxConcurrentStartup(1);
    addWlsCluster(CLUSTER1, MS1, MS2);
    addWlsCluster(CLUSTER2, MS3, MS4);

    invokeStepWithServerStartupInfos();

    assertThat(getStartedManagedServers().size(), equalTo(NUM_CLUSTERS));
  }

  @Nonnull
  private List<String> getStartedManagedServers() {
    return domainPresenceInfo.getServerPods()
          .map(this::getServerName)
          .filter(name -> !ADMIN.equals(name))
          .collect(Collectors.toList());
  }

  private String getServerName(V1Pod pod) {
    return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getLabels).map(this::getServerName).orElse(null);
  }

  private String getServerName(@Nonnull Map<String,String> labels) {
    return labels.get(SERVERNAME_LABEL);
  }

  @Test
  public void whenConcurrencyLimitDisabled_additionalClusteredServersStartsAfterPreviousIsScheduled() {
    configureCluster(CLUSTER1).withMaxConcurrentStartup(0);
    addWlsCluster(CLUSTER1, MS1, MS2, MS3);

    invokeStepWithServerStartupInfos();
    testSupport.setTime(2 * SCHEDULING_DETECTION_DELAY, TimeUnit.MILLISECONDS);

    assertThat(getStartedManagedServers(), containsInAnyOrder(MS1, MS2, MS3));
  }

  @Test
  public void whenConcurrencyLimitIs1_secondClusteredServerDoesNotStartIfFirstIsNotReady() {
    configureCluster(CLUSTER1).withMaxConcurrentStartup(1);
    addWlsCluster(CLUSTER1, MS1, MS2);

    invokeStepWithServerStartupInfos();
    testSupport.setTime(SCHEDULING_DETECTION_DELAY, TimeUnit.MILLISECONDS);

    assertThat(getStartedManagedServers(), hasSize(1));
  }

  @Test
  public void whileAdminServerStopped_canStartManagedServer() {
    createDomainPresenceInfoWithNoAdminServer();
    addWlsCluster(CLUSTER1, MS1);

    invokeStepWithServerStartupInfos();

    assertThat(getStartedManagedServers(), hasSize(1));
  }

  private void createDomainPresenceInfoWithNoAdminServer() {
    domainPresenceInfo = new DomainPresenceInfo(domain);
    testSupport
            .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig)
            .addDomainPresenceInfo(domainPresenceInfo);
  }

  @Test
  public void whenConcurrencyLimitIs1_secondClusteredServerStartsAfterFirstIsReady() {
    configureCluster(CLUSTER1).withMaxConcurrentStartup(1);
    addWlsCluster(CLUSTER1, MS1, MS2);

    invokeStepWithServerStartupInfos();
    testSupport.setTime(READY_DETECTION_DELAY, TimeUnit.SECONDS);

    assertThat(getStartedManagedServers(), hasSize(2));
  }

  @Test
  public void whenConcurrencyLimitIs2_secondClusteredServerStartsAfterFirstIsScheduledButNotThird() {
    configureCluster(CLUSTER1).withMaxConcurrentStartup(2);
    addWlsCluster(CLUSTER1, MS1, MS2, MS3, MS4);

    invokeStepWithServerStartupInfos();
    testSupport.setTime(2 * SCHEDULING_DETECTION_DELAY, TimeUnit.MILLISECONDS);

    assertThat(getStartedManagedServers(), containsInAnyOrder(MS1, MS2));
  }

  @Test
  public void whenConcurrencyLimitIs2_nextTwoStartAfterFirstTwoAreReady() {
    configureCluster(CLUSTER1).withMaxConcurrentStartup(2);
    addWlsCluster(CLUSTER1, MS1, MS2, MS3, MS4);

    invokeStepWithServerStartupInfos();
    testSupport.setTime(READY_DETECTION_DELAY, TimeUnit.SECONDS);

    assertThat(getStartedManagedServers(), containsInAnyOrder(MS1, MS2, MS3, MS4));
  }

  @Test
  public void nonClusteredServers_ignoreConcurrencyLimit() {
    domain.getSpec().setMaxClusterConcurrentStartup(1);
    addWlsServers(MS1, MS2, MS3);

    invokeStepWithServerStartupInfos();
    testSupport.setTime(2 * SCHEDULING_DETECTION_DELAY, TimeUnit.MILLISECONDS);

    assertThat(getStartedManagedServers(), containsInAnyOrder(MS1, MS2, MS3));
  }

  @Test
  public void withMultipleClusters_differentClusterScheduleAndStartDifferently() {
    configureCluster(CLUSTER1).withMaxConcurrentStartup(0);
    configureCluster(CLUSTER2).withMaxConcurrentStartup(1);
    addWlsCluster(CLUSTER1, MS1, MS2);
    addWlsCluster(CLUSTER2, MS3, MS4);

    invokeStepWithServerStartupInfos();
    testSupport.setTime(SCHEDULING_DETECTION_DELAY, TimeUnit.MILLISECONDS);

    assertThat(getStartedManagedServers(), containsInAnyOrder(MS1, MS2, MS3));
  }

  @Test
  public void whenClusteredServersAlreadyScheduled_canStartNonclusteredServer() {
    domain.getSpec().setMaxClusterConcurrentStartup(1);
    Arrays.asList(MS1, MS2).forEach(this::addScheduledClusteredServer);
    addWlsServer(MS3);

    invokeStepWithServerStartupInfos();

    assertThat(MS3 + " pod", domainPresenceInfo.getServerPod(MS3), notNullValue());
  }

  private void addScheduledClusteredServer(String serverName) {
    domainPresenceInfo.setServerPod(serverName,
          new V1Pod().metadata(
                withNames(new V1ObjectMeta().namespace(NS).putLabelsItem(CLUSTERNAME_LABEL, CLUSTER1), serverName))
                      .spec(new V1PodSpec().nodeName("scheduled")));
  }


  private void invokeStepWithServerStartupInfos() {
    ManagedServerUpIteratorStep step = new ManagedServerUpIteratorStep(startupInfos, nextStep);
    testSupport.runSteps(step);
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(clusterName);
  }

  private void addWlsServers(String... serverNames) {
    Arrays.asList(serverNames).forEach(this::addWlsServer);
  }

  private void addWlsServer(String serverName) {
    configSupport.addWlsServer(serverName, PORT);
    startupInfos.add(
          new ServerStartupInfo(configSupport.getWlsServer(serverName),
              null,
              domain.getServer(serverName, null))
    );
  }

  private void addWlsCluster(String clusterName, String... serverNames) {
    configSupport.addWlsCluster(clusterName, PORT, serverNames);
    Arrays.stream(serverNames).forEach(server ->
            startupInfos.add(
                new ServerStartupInfo(configSupport.getWlsServer(clusterName, server),
                    clusterName,
                    domain.getServer(server, clusterName))
            )
    );

  }

  @Override
  public void receivedResponse(Watch.Response<V1Pod> response) {
  }

  @Override
  public void allWatchesClosed() {
    stopping.set(true);
  }
}