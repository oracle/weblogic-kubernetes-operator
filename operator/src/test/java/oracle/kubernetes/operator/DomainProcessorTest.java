// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudgetSpec;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.IntrospectionTestUtils;
import oracle.kubernetes.operator.helpers.KubernetesEventObjects;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.PodStepContext;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.rest.ScanCacheStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainConditionType;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainSourceType.FromModel;
import static oracle.kubernetes.operator.DomainSourceType.Image;
import static oracle.kubernetes.operator.DomainSourceType.PersistentVolume;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTING_EVENT;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CONFIG_MAP;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static oracle.kubernetes.operator.logging.MessageKeys.NOT_STARTING_DOMAINUID_THREAD;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_NEVER;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainProcessorTest {
  private static final String ADMIN_NAME = "admin";
  private static final String CLUSTER = "cluster";
  private static final int MAX_SERVERS = 60;
  private static final String MS_PREFIX = "managed-server";
  private static final int MIN_REPLICAS = 2;
  private static final int NUM_ADMIN_SERVERS = 1;
  private static final int NUM_JOB_PODS = 1;
  private static final String[] MANAGED_SERVER_NAMES =
      IntStream.rangeClosed(1, MAX_SERVERS).mapToObj(DomainProcessorTest::getManagedServerName).toArray(String[]::new);
  public static final String DOMAIN_NAME = "base_domain";

  @Nonnull
  private static String getManagedServerName(int n) {
    return MS_PREFIX + n;
  }

  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();
  private final DomainProcessorDelegateStub processorDelegate = DomainProcessorDelegateStub.createDelegate(testSupport);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final Domain newDomain = DomainProcessorTestSetup.createTestDomain(2L);
  private final DomainConfigurator domainConfigurator = configureDomain(newDomain);
  private final MakeRightDomainOperation makeRightOperation
        = processor.createMakeRightOperation(new DomainPresenceInfo(newDomain));
  private final WlsDomainConfig domainConfig = createDomainConfig();

  private V1JobStatus jobStatus = createCompletedStatus();
  private final Supplier<V1JobStatus> jobStatusSupplier = () -> jobStatus;

  V1JobStatus createCompletedStatus() {
    return new V1JobStatus()
          .addConditionsItem(new V1JobCondition().type("Complete").status("True"));
  }

  V1JobStatus createNotCompletedStatus() {
    return new V1JobStatus();
  }

  private static WlsDomainConfig createDomainConfig() {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(CLUSTER);
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig(DOMAIN_NAME)
        .withAdminServer(ADMIN_NAME, "domain1-admin-server", 7001)
        .withCluster(clusterConfig);
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, NOT_STARTING_DOMAINUID_THREAD).withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
    mementos.add(StaticStubSupport.install(PodStepContext.class, "productVersion", "unit-test"));
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());
    mementos.add(ScanCacheStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(NoopWatcherStarter.install());

    testSupport.defineResources(newDomain);
    IntrospectionTestUtils.defineResources(testSupport, createDomainConfig(), jobStatusSupplier);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenDomainSpecNotChanged_dontRunUpdateThread() {
    DomainProcessorImpl.registerDomainPresenceInfo(new DomainPresenceInfo(newDomain));

    makeRightOperation.execute();

    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, newDomain.getDomainUid());
    assertThat(getResourceVersion(updatedDomain), equalTo(getResourceVersion(newDomain)));
  }

  private String getResourceVersion(Domain domain) {
    return Optional.of(domain).map(Domain::getMetadata).map(V1ObjectMeta::getResourceVersion).orElse("");
  }

  @Test
  public void whenDomainExplicitSet_runUpdateThread() {
    DomainProcessorImpl.registerDomainPresenceInfo(new DomainPresenceInfo(domain));

    processor.createMakeRightOperation(new DomainPresenceInfo(domain)).withExplicitRecheck().execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  public void whenDomainConfiguredForMaxServers_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).execute();

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (String serverName : MANAGED_SERVER_NAMES) {
      assertServerPodAndServicePresent(info, serverName);
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
    assertThat(info.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  public void whenMakeRightRun_updateDomainStatus() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);

    processor.createMakeRightOperation(new DomainPresenceInfo(domain)).execute();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);

    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[0]), equalTo(RUNNING_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[1]), equalTo(RUNNING_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[2]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[3]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[4]), equalTo(SHUTDOWN_STATE));
    assertThat(getResourceVersion(updatedDomain), not(getResourceVersion(domain)));
  }

  @Test
  public void whenDomainScaledDown_removeExcessPodsAndServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    processor.createMakeRightOperation(new DomainPresenceInfo(domain)).withExplicitRecheck().execute();

    assertThat((int) getServerServices().count(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  private List<CoreV1Event> getEventsAfterTimestamp(OffsetDateTime timestamp) {
    return testSupport.<CoreV1Event>getResources(KubernetesTestSupport.EVENT).stream()
            .filter(e -> e.getLastTimestamp().isAfter(timestamp)).collect(Collectors.toList());
  }

  @Test
  public void whenDomainScaledDown_withPreCreateServerService_doesNotRemoveServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS).withPrecreateServerService(true);

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).withExplicitRecheck().execute();

    assertThat((int) getServerServices().count(), equalTo(MAX_SERVERS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  public void whenStrandedResourcesExist_removeThem() {
    V1Service service1 = createServerService("admin");
    V1Service service2 = createServerService("ms1");
    testSupport.defineResources(service1, service2);

    processor.createMakeRightOperation(new DomainPresenceInfo(NS, UID)).withExplicitRecheck().forDeletion().execute();

    assertThat(testSupport.getResources(SERVICE), empty());
  }

  @Test
  public void whenDomainShutDown_removeAllPodsServicesAndPodDisruptionBudgets() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), empty());
  }

  @Test
  public void whenDomainShutDown_ignoreNonOperatorServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorService());

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), contains(createNonOperatorService()));
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), empty());
  }

  @Test
  public void whenDomainScaledUp_podDisruptionBudgetMinAvailableUpdated()
          throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 1), is(true));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain))
            .withExplicitRecheck().execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 2), is(true));
  }

  @Test
  public void whenDomainScaledDown_podDisruptionBudgetMinAvailableUpdated() throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 2), is(true));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain))
            .withExplicitRecheck().execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 1), is(true));
  }

  private Boolean minAvailableMatches(List<V1beta1PodDisruptionBudget> runningPDBs, int count) {
    return runningPDBs.stream().findFirst().map(V1beta1PodDisruptionBudget::getSpec)
            .map(s -> s.getMinAvailable().getIntValue()).orElse(0) == count;
  }

  @Test
  public void whenDomainShutDown_ignoreNonOperatorPodDisruptionBudgets() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorPodDisruptionBudget());

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), contains(createNonOperatorPodDisruptionBudget()));
  }

  @Test
  public void whenClusterReplicas2_server3WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);
    domainConfigurator.configureServer(MS_PREFIX + 3).withServerStartPolicy(START_ALWAYS);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3)) {
      assertServerPodAndServicePresent(info, MS_PREFIX + i);
    }
    assertServerPodNotPresent(info, MS_PREFIX + 2);

    assertThat(info.getClusterService(CLUSTER), notNullValue());
    assertThat(info.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  public void whenClusterScaleUpToReplicas3_fromReplicas2_server3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 3));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    domainConfigurator.configureServer(MS_PREFIX + 3).withServerStartPolicy(START_ALWAYS);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(info, MS_PREFIX + i);
    }
    assertThat(info.getClusterService(CLUSTER), notNullValue());

  }

  @Test
  public void whenClusterScaleDownToReplicas1_fromReplicas2_server3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1,3));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(1);
    domainConfigurator.configureServer(MS_PREFIX + 3).withServerStartPolicy(START_ALWAYS);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    logRecords.clear();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and one managed server pods
    assertThat(runningPods.size(), equalTo(3));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    assertServerPodAndServicePresent(info, MS_PREFIX + 3);
    for (Integer i : Arrays.asList(1,2)) {
      assertServerPodNotPresent(info, MS_PREFIX + i);
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
    assertThat(info.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  public void whenClusterReplicas3_server3And4WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);

    for (Integer i : Arrays.asList(3,4)) {
      domainConfigurator.configureServer(MS_PREFIX + i).withServerStartPolicy(START_ALWAYS);
    }

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3,4)) {
      assertServerPodAndServicePresent(info, MS_PREFIX + i);
    }
    assertServerPodNotPresent(info, MS_PREFIX + 2);

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  public void whenMakeRightOperationHasNoDomainUpdates_domainProcessingEventsNotGenerated()
          throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3, 4));
    for (Integer i : Arrays.asList(1,2,3,4)) {
      domainConfigurator.configureServer(MS_PREFIX + i);
    }
    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);

    processor.createMakeRightOperation(info).execute();

    // Run the make right flow again with explicit recheck and no domain updates
    OffsetDateTime timestamp = SystemClock.now();
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();
    assertThat("Event DOMAIN_PROCESSING_STARTED",
            doesNotContainEvent(getEventsAfterTimestamp(timestamp), DOMAIN_PROCESSING_STARTING_EVENT), is(true));
    assertThat("Event DOMAIN_PROCESSING_COMPLETED_EVENT",
            doesNotContainEvent(getEventsAfterTimestamp(timestamp), DOMAIN_PROCESSING_COMPLETED_EVENT), is(true));
  }

  @Test
  public void whenMakeRightOperationHasNoDomainUpdatesAndServiceOnlyServers_domainProcessingEventsNotGenerated()
          throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3, 4));
    for (Integer i : Arrays.asList(1,2,3,4)) {
      domainConfigurator.configureServer(MS_PREFIX + i);
    }
    domainConfigurator.configureCluster(CLUSTER).withReplicas(3).withPrecreateServerService(true);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);

    processor.createMakeRightOperation(info).execute();

    // Run the make right flow again with explicit recheck and no domain updates
    OffsetDateTime timestamp = SystemClock.now();
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();
    assertThat("Event DOMAIN_PROCESSING_STARTED",
            doesNotContainEvent(getEventsAfterTimestamp(timestamp), DOMAIN_PROCESSING_STARTING_EVENT), is(true));
    assertThat("Event DOMAIN_PROCESSING_COMPLETED_EVENT",
            doesNotContainEvent(getEventsAfterTimestamp(timestamp), DOMAIN_PROCESSING_COMPLETED_EVENT), is(true));
  }

  private static boolean doesNotContainEvent(List<CoreV1Event> events, String reason) {
    return Optional.ofNullable(events).get()
            .stream()
            .filter(e -> reason.equals(e.getReason())).findFirst().orElse(null) == null;
  }

  @Test
  public void whenScalingUpDomain_domainProcessingCompletedEventsGenerated()
          throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).execute();

    // Scale up the cluster and execute the make right flow again with explicit recheck
    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    OffsetDateTime timestamp = SystemClock.now();
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain))
            .withExplicitRecheck().execute();
    assertThat("Event DOMAIN_PROCESSING_COMPLETED_EVENT",
            containsEvent(getEventsAfterTimestamp(timestamp), DOMAIN_PROCESSING_COMPLETED_EVENT), is(true));
  }

  private static boolean containsEvent(List<CoreV1Event> events, String reason) {
    return Optional.ofNullable(events).get()
            .stream()
            .filter(e -> reason.equals(e.getReason())).findFirst().orElse(null) != null;
  }

  @Test
  public void whenClusterScaleUpToReplicas4_fromReplicas2_server3And4WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 3, 4));

    for (Integer i : Arrays.asList(3,4)) {
      domainConfigurator.configureServer(MS_PREFIX + i).withServerStartPolicy(START_ALWAYS);
    }

    domainConfigurator.configureCluster(CLUSTER).withReplicas(4);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);

    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and four managed server pods
    assertThat(runningPods.size(), equalTo(6));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3,4)) {
      assertServerPodAndServicePresent(info, MS_PREFIX + i);
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  public void whenClusterReplicas2_server1And2And3WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);

    for (Integer i : Arrays.asList(1,2,3)) {
      domainConfigurator.configureServer(MS_PREFIX + i).withServerStartPolicy(START_ALWAYS);
    }

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(info, MS_PREFIX + i);
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  public void whenClusterScaleDownToReplicas1_fromReplicas2_server1And2And3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3));

    // now scale down the cluster
    domainConfigurator.configureCluster(CLUSTER).withReplicas(1);

    for (Integer i : Arrays.asList(1,2,3)) {
      domainConfigurator.configureServer(MS_PREFIX + i).withServerStartPolicy(START_ALWAYS);
    }

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();
    logRecords.clear();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(info, MS_PREFIX + i);
    }
    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  public void whenClusterReplicas2_server2NeverPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);
    domainConfigurator.configureServer(MS_PREFIX + 2).withServerStartPolicy(START_NEVER);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3)) {
      assertServerPodAndServicePresent(info, MS_PREFIX + i);
    }
    assertServerPodNotPresent(info, MS_PREFIX + 2);
    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  public void whenClusterReplicas2_allServersExcept5NeverPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    int[] servers = IntStream.rangeClosed(1, MAX_SERVERS).toArray();
    for (int i : servers) {
      if (i != 5) {
        domainConfigurator.configureServer(MS_PREFIX + i).withServerStartPolicy(START_NEVER);
      }
    }
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and one managed server pods
    assertThat(runningPods.size(), equalTo(3));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (int i : servers) {
      if (i != 5) {
        assertServerPodAndServiceNotPresent(info, MS_PREFIX + i);
      } else {
        assertServerPodAndServicePresent(info, MS_PREFIX + i);
      }
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  private V1Service createNonOperatorService() {
    return new V1Service()
        .metadata(
            new V1ObjectMeta()
                .name("do-not-delete-service")
                .namespace(NS)
                .putLabelsItem("serviceType", "SERVER")
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "false")
                .putLabelsItem(DOMAINNAME_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(SERVERNAME_LABEL, ADMIN_NAME))
        .spec(new V1ServiceSpec().type(ServiceHelper.CLUSTER_IP_TYPE));
  }

  private V1beta1PodDisruptionBudget createNonOperatorPodDisruptionBudget() {
    return new V1beta1PodDisruptionBudget()
            .metadata(
                    new V1ObjectMeta()
                            .name("do-not-delete-pdb")
                            .namespace(NS)
                            .putLabelsItem("serviceType", "SERVER")
                            .putLabelsItem(CREATEDBYOPERATOR_LABEL, "false")
                            .putLabelsItem(DOMAINNAME_LABEL, DomainProcessorTestSetup.UID)
                            .putLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                            .putLabelsItem(CLUSTERNAME_LABEL, CLUSTER))
            .spec(new V1beta1PodDisruptionBudgetSpec()
                    .selector(new V1LabelSelector()
                            .putMatchLabelsItem(CREATEDBYOPERATOR_LABEL, "false")
                            .putMatchLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                            .putMatchLabelsItem(CLUSTERNAME_LABEL, CLUSTER)));
  }

  @Test
  public void onUpgradeFromV20_updateExternalService() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    testSupport.defineResources(createV20ExternalService());
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(info.getExternalService(ADMIN_NAME), notNullValue());
  }

  @Test
  public void whenNoExternalServiceNameSuffixConfigured_externalServiceNameContainsDefaultSuffix() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(info.getExternalService(ADMIN_NAME).getMetadata().getName(),
        equalTo(info.getDomainUid() + "-" + ADMIN_NAME + "-ext"));
  }

  @Test
  public void whenExternalServiceNameSuffixConfigured_externalServiceNameContainsSuffix() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    TuningParameters.getInstance().put(LegalNames.EXTERNAL_SERVICE_NAME_SUFFIX_PARAM, "-my-external-service");
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(info.getExternalService(ADMIN_NAME).getMetadata().getName(),
        equalTo(info.getDomainUid() + "-" + ADMIN_NAME + "-my-external-service"));
  }

  private static final String OLD_INTROSPECTION_STATE = "123";
  private static final String NEW_INTROSPECTION_STATE = "124";
  private static final String INTROSPECTOR_MAP_NAME = UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;

  @Test
  public void whenDomainHasRunningServersAndExistingTopology_dontRunIntrospectionJob() throws JsonProcessingException {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(OLD_INTROSPECTION_STATE);
    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  @Test
  public void whenDomainHasIntrospectVersionDifferentFromOldDomain_runIntrospectionJob() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    assertThat(job, notNullValue());
  }

  @Test
  public void whenIntrospectionJobRun_recordIt() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    MakeRightDomainOperation makeRight = this.processor.createMakeRightOperation(
          new DomainPresenceInfo(newDomain)).interrupt();
    makeRight.execute();

    assertThat(makeRight.wasInspectionRun(), is(true));
  }

  @Test
  public void whenIntrospectionJobNotComplete_waitForIt() throws Exception {
    establishPreviousIntrospection(null);
    jobStatus = createNotCompletedStatus();

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    MakeRightDomainOperation makeRight = this.processor.createMakeRightOperation(
          new DomainPresenceInfo(newDomain)).interrupt();
    makeRight.execute();

    assertThat(processorDelegate.waitedForIntrospection(), is(true));
  }

  private void establishPreviousIntrospection(Consumer<Domain> domainSetup) throws JsonProcessingException {
    establishPreviousIntrospection(domainSetup, Arrays.asList(1,2));
  }

  private void establishPreviousIntrospection(Consumer<Domain> domainSetup, List<Integer> msNumbers)
      throws JsonProcessingException {
    if (domainSetup != null) {
      domainSetup.accept(domain);
      domainSetup.accept(newDomain);
    }
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    defineServerResources(ADMIN_NAME);
    for (Integer i : msNumbers) {
      defineServerResources(getManagedServerName(i));
    }
    DomainProcessorImpl.registerDomainPresenceInfo(new DomainPresenceInfo(domain));
    testSupport.defineResources(createIntrospectorConfigMap(OLD_INTROSPECTION_STATE));
    testSupport.doOnCreate(KubernetesTestSupport.JOB, j -> recordJob((V1Job) j));
    domainConfigurator.withIntrospectVersion(OLD_INTROSPECTION_STATE);
  }

  private String getCurrentImageSpecHash() {
    return String.valueOf(ConfigMapHelper.getModelInImageSpecHash(newDomain.getSpec().getImage()));
  }

  // define a config map with a topology to avoid the no-topology condition that always runs the introspector
  @SuppressWarnings("SameParameterValue")
  private V1ConfigMap createIntrospectorConfigMap(String introspectionDoneValue) throws JsonProcessingException {
    return new V1ConfigMap()
          .metadata(createIntrospectorConfigMapMeta(introspectionDoneValue))
          .data(new HashMap<>(Map.of(IntrospectorConfigMapConstants.TOPOLOGY_YAML, defineTopology(),
                                     IntrospectorConfigMapConstants.DOMAIN_INPUTS_HASH, getCurrentImageSpecHash())));
  }

  private V1ObjectMeta createIntrospectorConfigMapMeta(@Nullable String introspectionDoneValue) {
    final V1ObjectMeta meta = new V1ObjectMeta()
          .namespace(NS)
          .name(ConfigMapHelper.getIntrospectorConfigMapName(UID));
    Optional.ofNullable(introspectionDoneValue).ifPresent(v -> meta.putLabelsItem(INTROSPECTION_STATE_LABEL, v));
    return meta;
  }

  private String defineTopology() throws JsonProcessingException {
    return IntrospectionTestUtils.createTopologyYaml(createDomainConfig());
  }

  @Test
  public void afterIntrospection_introspectorConfigMapHasUpToDateLabel() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    assertThat(getIntrospectorConfigMapIntrospectionVersion(), equalTo(NEW_INTROSPECTION_STATE));
  }

  private String getIntrospectorConfigMapIntrospectionVersion() {
    return getConfigMaps()
          .map(V1ConfigMap::getMetadata)
          .filter(this::isIntrospectorMeta)
          .findFirst()
          .map(V1ObjectMeta::getLabels)
          .map(m -> m.get(INTROSPECTION_STATE_LABEL))
          .orElse(null);
  }

  private Stream<V1ConfigMap> getConfigMaps() {
    return testSupport.<V1ConfigMap>getResources(CONFIG_MAP).stream();
  }

  @Test
  public void afterInitialIntrospection_serverPodsHaveInitialIntrospectVersionLabel() {
    domainConfigurator.withIntrospectVersion(OLD_INTROSPECTION_STATE);
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));
    for (V1Pod pod: runningPods) {
      if (!pod.getMetadata().getName().contains(LegalNames.getIntrospectorJobNameSuffix())) {
        assertThat(getServerPodIntrospectionVersion(pod), equalTo(OLD_INTROSPECTION_STATE));
      }
    }
  }

  @Test
  public void afterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));
    for (V1Pod pod: runningPods) {
      if (!pod.getMetadata().getName().contains(LegalNames.getIntrospectorJobNameSuffix())) {
        assertThat(getServerPodIntrospectionVersion(pod), equalTo(NEW_INTROSPECTION_STATE));
      }
    }
  }

  @Test
  public void afterScaleupClusterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    domainConfigurator.withIntrospectVersion("after-scaleup");
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));
    for (V1Pod pod: runningPods) {
      if (!pod.getMetadata().getName().contains(LegalNames.getIntrospectorJobNameSuffix())) {
        assertThat(getServerPodIntrospectionVersion(pod), equalTo("after-scaleup"));
      }
    }
  }

  @Test
  public void afterScaledownClusterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(1);
    domainConfigurator.withIntrospectVersion("after-scaledown");
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and one managed server pod
    assertThat(runningPods.size(), equalTo(3));
    for (V1Pod pod: runningPods) {
      if (!pod.getMetadata().getName().contains(LegalNames.getIntrospectorJobNameSuffix())) {
        assertThat(getServerPodIntrospectionVersion(pod), equalTo("after-scaledown"));
      }
    }
  }

  private String getServerPodIntrospectionVersion(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(m -> m.get(INTROSPECTION_STATE_LABEL))
        .orElse(null);
  }

  private boolean isIntrospectorMeta(@Nullable V1ObjectMeta meta) {
    return meta != null && NS.equals(meta.getNamespace()) && INTROSPECTOR_MAP_NAME.equals(meta.getName());
  }

  @Test
  public void whenDomainTypeIsDomainInPV_dontRerunIntrospectionJob() throws Exception {
    establishPreviousIntrospection(this::configureForDomainInPV);

    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  public void configureForDomainInPV(Domain d) {
    configureDomain(d).withDomainHomeSourceType(PersistentVolume);
  }

  private V1Job job;

  private void recordJob(V1Job job) {
    this.job = job;
  }

  @Test
  public void whenDomainTypeIsDomainInImage_dontRerunIntrospectionJob() throws Exception {
    establishPreviousIntrospection(d -> configureDomain(d).withDomainHomeSourceType(Image));

    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  @Test
  public void whenDomainTypeIsFromModelDomainAndNoChanges_dontRerunIntrospectionJob() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));

    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  void configureForModelInImage(Domain domain) {
    configureDomain(domain).withDomainHomeSourceType(FromModel).withRuntimeEncryptionSecret("wdt-cm-secret");
  }

  void configureForModelInImageOnlineUpdate(Domain domain) {
    configureDomain(domain).withDomainHomeSourceType(FromModel).withRuntimeEncryptionSecret("wdt-cm-secret")
      .withMIIOnlineUpdate();
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  @Test
  public void whenDomainTypeIsFromModelDomainAndImageHashChanged_runIntrospectionJob() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    cacheChangedDomainInputsHash();

    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(true));
  }

  private void cacheChangedDomainInputsHash() {
    getConfigMaps()
          .filter(this::isIntrospectorConfigMap)
          .findFirst()
          .map(V1ConfigMap::getData)
          .ifPresent(data -> data.put(IntrospectorConfigMapConstants.DOMAIN_INPUTS_HASH, "changedHash"));
  }

  private boolean isIntrospectorConfigMap(V1ConfigMap map) {
    return isIntrospectorMeta(map.getMetadata());
  }

  @Test
  public void whenDomainTypeIsFromModelDomainAndManagedServerModified_runIntrospectionJobThenRoll() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    domainConfigurator.configureServer(getManagedServerName(1)).withAdditionalVolume("vol1", "/path");
    domainConfigurator.configureServer(getManagedServerName(2)).withAdditionalVolume("vol2", "/path");

    makeRightOperation.execute();

    assertThat(introspectionRunBeforeUpdates,
          allOf(hasEntry(getManagedPodName(1), true), hasEntry(getManagedPodName(2), true)));
  }

  @Test
  public void whenDomainTypeIsFromModelOnlineUpdateSuccessUpdateRestartRequired() throws Exception {
    getMIIOnlineUpdateIntrospectResult(DomainConditionType.ConfigChangesPendingRestart,
        ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED);
  }

  private void getMIIOnlineUpdateIntrospectResult(DomainConditionType domainConditionType, String updateResult)
      throws Exception {

    DomainProcessorImpl.registerDomainPresenceInfo(new DomainPresenceInfo(domain));

    String introspectorResult = ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
        + "#WebLogic User Configuration File; 2\n"
        + "#Thu Oct 04 21:07:06 GMT 2018\n"
        + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
        + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
        + "\n"
        + ">>> EOF\n"
        + "\n"
        + "@[2018-10-04T21:07:06.864000Z][introspectDomain.py:105] Printing file "
        + "/u01/introspect/domain1/userKeyNodeManager.secure\n"
        + "\n"
        + ">>>  /u01/introspect/domain1/userKeyNodeManager.secure\n"
        + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==\n"
        + "\n"
        + ">>> EOF\n"
        + "\n"
        + ">>>  /u01/introspect/domain1/domainzip_hash\n"
        + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xbo\n"
        + "\n"
        + ">>> EOF\n"
        + "\n"
        + "@[2018-10-04T21:07:06.867000Z][introspectDomain.py:105] Printing file "
        + "/u01/introspect/domain1/topology.yaml\n"
        + "\n"
        + ">>>  /u01/introspect/domain1/topology.yaml\n"
        + "%s\n"
        + ">>> EOF\n"
        + ">>>  updatedomainResult=%s\n";

    establishPreviousIntrospection(this::configureForModelInImageOnlineUpdate);
    domainConfigurator.withIntrospectVersion("after-onlineUpdate");
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS,
        String.format(introspectorResult, defineTopology(), updateResult));
    makeRightOperation.execute();
    boolean found = false;
    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    if (updatedDomain.getStatus() != null) {
      for (DomainCondition domainCondition : updatedDomain.getStatus().getConditions()) {
        if (domainCondition.getType() == domainConditionType) {
          found = true;
          break;
        }
      }
    }
    assertThat(found, is(true));

    presenceInfoMap.get("namespace").get("test-domain").getServerPods()
        .forEach(p -> {
          Map<String, String> labels = Optional.ofNullable(p)
                .map(V1Pod::getMetadata)
                .map(V1ObjectMeta::getLabels)
                .orElse(new HashMap<>());
          String message = String.format("Server pod (%s) should have label weblogic.configChangesPendingRestart"
                + " set to true", p.getMetadata().getName());
          assertThat(message, labels.get("weblogic.configChangesPendingRestart"), is("true"));
          }
    );
  }

  @Nonnull
  public String getManagedPodName(int i) {
    return LegalNames.toPodName(UID, getManagedServerName(i));
  }

  @Nonnull
  public String getAdminPodName() {
    return LegalNames.toPodName(UID, ADMIN_NAME);
  }

  private void recordPodCreation(V1Pod pod) {
    Optional.of(pod)
          .map(V1Pod::getMetadata)
          .map(V1ObjectMeta::getName)
          .ifPresent(name -> introspectionRunBeforeUpdates.put(name, makeRightOperation.wasInspectionRun()));
  }

  private final Map<String,Boolean> introspectionRunBeforeUpdates = new HashMap<>();

  @Test
  public void whenDomainTypeIsFromModelDomainAndNewServerCreated_dontRunIntrospectionJobFirst() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    configureDomain(newDomain).configureCluster(CLUSTER).withReplicas(3);

    makeRightOperation.execute();
    assertThat(introspectionRunBeforeUpdates, hasEntry(getManagedPodName(3), false));
  }

  @Test
  public void whenDomainTypeIsFromModelDomainAndAdminServerModified_runIntrospectionJobFirst() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    configureDomain(newDomain).configureAdminServer().withAdditionalVolume("newVol", "/path");

    makeRightOperation.execute();

    assertThat(introspectionRunBeforeUpdates, hasEntry(getAdminPodName(), true));
  }

  @Test
  public void afterChangeTriggersIntrospection_doesNotRunIntrospectionOnNextExplicitMakeRight() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    configureDomain(newDomain).configureAdminServer().withAdditionalVolume("newVol", "/path");
    makeRightOperation.execute();
    job = null;

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).withExplicitRecheck().execute();

    assertThat(job, nullValue());
  }

  @Test
  public void whenDomainTypeIsFromModelDomainAndAdminServerModified_runIntrospectionJobFirst2() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    configureDomain(newDomain).configureAdminServer().withAdditionalVolume("newVol", "/path");
    makeRightOperation.execute();
    assertThat(job, notNullValue());
    job = null;
    DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(newDomain);
    domainPresenceInfo.getDomain().getStatus().setMessage("FatalIntrospectorError: ECCC");
    domainPresenceInfo.getDomain().getSpec().setIntrospectVersion("NEWVERSION");
    MakeRightDomainOperation mk = processor.createMakeRightOperation(domainPresenceInfo).withExplicitRecheck();
    mk.execute();
    assertThat(job, nullValue());
    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
  }


  // todo after external service created, if adminService deleted, delete service

  // problem - ServiceType doesn't know what this is, so does not
  // add it to DomainPresenceInfo, so does not delete it!
  private V1Service createV20ExternalService() {
    return new V1Service()
        .metadata(
            new V1ObjectMeta()
                .name(LegalNames.toExternalServiceName(DomainProcessorTestSetup.UID, ADMIN_NAME))
                .namespace(NS)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true")
                .putLabelsItem(DOMAINNAME_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(SERVERNAME_LABEL, ADMIN_NAME))
        .spec(
            new V1ServiceSpec()
                .type(ServiceHelper.NODE_PORT_TYPE)
                .addPortsItem(new V1ServicePort().nodePort(30701)));
  }

  private Stream<V1Service> getServerServices() {
    return getRunningServices().stream().filter(ServiceHelper::isServerService);
  }

  private List<V1Service> getRunningServices() {
    return testSupport.getResources(KubernetesTestSupport.SERVICE);
  }

  private List<V1Pod> getRunningPods() {
    return testSupport.getResources(KubernetesTestSupport.POD);
  }

  private List<V1beta1PodDisruptionBudget> getRunningPDBs() {
    return testSupport.getResources(KubernetesTestSupport.PODDISRUPTIONBUDGET);
  }

  private void defineServerResources(String serverName) {
    testSupport.defineResources(createServerPod(serverName), createServerService(serverName));
  }

  /**/
  private V1Pod createServerPod(String serverName) {
    Packet packet = new Packet();
    packet
          .getComponents()
          .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(new DomainPresenceInfo(domain)));
    packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig);

    if (ADMIN_NAME.equals(serverName)) {
      packet.put(ProcessingConstants.SERVER_SCAN, domainConfig.getServerConfig(serverName));
      return PodHelper.createAdminServerPodModel(packet);
    } else {
      packet.put(ProcessingConstants.CLUSTER_NAME, CLUSTER);
      packet.put(ProcessingConstants.SERVER_SCAN, getClusteredServerConfig(serverName));
      return PodHelper.createManagedServerPodModel(packet);
    }
  }

  private WlsServerConfig getClusteredServerConfig(String serverName) {
    return domainConfig.getClusterConfig(CLUSTER).getServerConfigs()
          .stream()
          .filter(c -> serverName.equals(c.getName())).findFirst()
          .orElseThrow();
  }


  private V1ObjectMeta withServerLabels(V1ObjectMeta meta, String serverName) {
    return KubernetesUtils.withOperatorLabels(DomainProcessorTestSetup.UID, meta)
        .putLabelsItem(SERVERNAME_LABEL, serverName);
  }
  
  private V1Service createServerService(String serverName) {
    return AnnotationHelper.withSha256Hash(
        new V1Service()
            .metadata(
                withServerLabels(
                    new V1ObjectMeta()
                        .name(
                            LegalNames.toServerServiceName(
                                DomainProcessorTestSetup.UID, serverName))
                        .namespace(NS),
                    serverName)));
  }

  private void assertServerPodAndServicePresent(DomainPresenceInfo info, String serverName) {
    assertThat(serverName + " server service", info.getServerService(serverName), notNullValue());
    assertThat(serverName + " pod", info.getServerPod(serverName), notNullValue());
  }

  private void assertServerPodNotPresent(DomainPresenceInfo info, String serverName) {
    assertThat(serverName + " pod", isServerInactive(info, serverName), is(Boolean.TRUE));
  }

  private boolean isServerInactive(DomainPresenceInfo info, String serverName) {
    return info.isServerPodBeingDeleted(serverName) || info.getServerPod(serverName) == null;
  }

  @Test
  public void whenDomainIsNotValid_dontBringUpServers() {
    defineDuplicateServerNames();

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertServerPodAndServiceNotPresent(info, ADMIN_NAME);
    for (String serverName : MANAGED_SERVER_NAMES) {
      assertServerPodAndServiceNotPresent(info, serverName);
    }
  }

  private void assertServerPodAndServiceNotPresent(DomainPresenceInfo info, String serverName) {
    assertThat(serverName + " server service", info.getServerService(serverName), nullValue());
    assertThat(serverName + " pod", isServerInactive(info, serverName), is(Boolean.TRUE));
  }

  @Test
  public void whenDomainIsNotValid_updateStatus() {
    defineDuplicateServerNames();

    processor.createMakeRightOperation(new DomainPresenceInfo(domain)).withExplicitRecheck().execute();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("ErrBadDomain"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("managedServers", "ms1"));
  }
  
  private String getStatusReason(Domain updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(Domain::getStatus).map(DomainStatus::getReason).orElse(null);
  }

  private String getStatusMessage(Domain updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(Domain::getStatus).map(DomainStatus::getMessage).orElse(null);
  }

  private String getDesiredState(Domain domain, String serverName) {
    return Optional.ofNullable(getServerStatus(domain, serverName)).map(ServerStatus::getDesiredState).orElse("");
  }

  private ServerStatus getServerStatus(Domain domain, String serverName) {
    for (ServerStatus status : domain.getStatus().getServers()) {
      if (status.getServerName().equals(serverName)) {
        return status;
      }
    }

    return null;
  }

  private void defineDuplicateServerNames() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
  }
}
