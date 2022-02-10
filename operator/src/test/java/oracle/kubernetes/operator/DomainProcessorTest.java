// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1HTTPGetAction;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Probe;
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
import oracle.kubernetes.operator.http.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.HttpResponseStub;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.operator.DomainFailureReason.Internal;
import static oracle.kubernetes.operator.DomainFailureReason.Kubernetes;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.SECRET_NAME;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainSourceType.FromModel;
import static oracle.kubernetes.operator.DomainSourceType.Image;
import static oracle.kubernetes.operator.DomainSourceType.PersistentVolume;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_OK;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CONFIG_MAP;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.JOB;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SECRET;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static oracle.kubernetes.operator.helpers.SecretHelper.PASSWORD_KEY;
import static oracle.kubernetes.operator.helpers.SecretHelper.USERNAME_KEY;
import static oracle.kubernetes.operator.http.HttpAsyncTestSupport.OK_RESPONSE;
import static oracle.kubernetes.operator.http.HttpAsyncTestSupport.createExpectedRequest;
import static oracle.kubernetes.operator.logging.MessageKeys.NOT_STARTING_DOMAINUID_THREAD;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_NEVER;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Completed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
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

class DomainProcessorTest {
  private static final String ADMIN_NAME = "admin";
  private static final String CLUSTER = "cluster";
  private static final String CLUSTER2 = "cluster-2";
  private static final String INDEPENDENT_SERVER = "server-1";
  private static final int MAX_SERVERS = 5;
  private static final String MS_PREFIX = "managed-server";
  private static final int MIN_REPLICAS = 2;
  private static final int NUM_ADMIN_SERVERS = 1;
  private static final int NUM_JOB_PODS = 1;
  private static final String[] MANAGED_SERVER_NAMES = getManagedServerNames(CLUSTER);

  static final String DOMAIN_NAME = "base_domain";
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;
  private final HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private final KubernetesExecFactoryFake execFactoryFake = new KubernetesExecFactoryFake();

  private static String[] getManagedServerNames(String clusterName) {
    return IntStream.rangeClosed(1, MAX_SERVERS)
            .mapToObj(n -> getManagedServerName(n, clusterName)).toArray(String[]::new);
  }

  @Nonnull
  private static String getManagedServerName(int n) {
    return getManagedServerName(n, CLUSTER);
  }

  private static String getManagedServerName(int n, String clusterName) {
    return clusterName + "-" + MS_PREFIX + n;
  }

  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> makeRightFiberGates = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> statusFiberGates = new ConcurrentHashMap<>();
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
    return createDomainConfig(Collections.singletonList(CLUSTER), new ArrayList<>());
  }

  private static WlsDomainConfig createDomainConfig(List<String> clusterNames, List<String> independentServerNames) {
    WlsDomainConfig wlsDomainConfig = new WlsDomainConfig(DOMAIN_NAME)
            .withAdminServer(ADMIN_NAME, "domain1-admin-server", 7001);
    for (String serverName : independentServerNames) {
      wlsDomainConfig.addWlsServer(serverName, "domain-" + serverName, 8001);
    }
    for (String clusterName : clusterNames) {
      WlsClusterConfig clusterConfig = new WlsClusterConfig(clusterName);
      for (String serverName : getManagedServerNames(clusterName)) {
        clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
      }
      wlsDomainConfig.withCluster(clusterConfig);
    }
    return wlsDomainConfig;
  }

  @BeforeEach
  void setUp() throws Exception {
    consoleHandlerMemento = TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, NOT_STARTING_DOMAINUID_THREAD).withLogLevel(Level.FINE);
    mementos.add(consoleHandlerMemento);
    mementos.add(testSupport.install());
    mementos.add(httpSupport.install());
    mementos.add(execFactoryFake.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "makeRightFiberGates", makeRightFiberGates));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "statusFiberGates", statusFiberGates));
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
    ScanCache.INSTANCE.registerScan(NS,UID, new Scan(domainConfig, SystemClock.now()));
  }

  @AfterEach
  void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  void whenDomainSpecNotChanged_dontRunUpdateThread() {
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
  void whenDomainExplicitSet_runUpdateThread() {
    DomainProcessorImpl.registerDomainPresenceInfo(new DomainPresenceInfo(domain));

    processor.createMakeRightOperation(new DomainPresenceInfo(domain)).withExplicitRecheck().execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainConfiguredForMaxServers_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (String serverName : MANAGED_SERVER_NAMES) {
      assertServerPodAndServicePresent(info, serverName);
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
    assertThat(info.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenMakeRightRun_updateDomainStatus() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).execute();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);

    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[0]), equalTo(RUNNING_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[1]), equalTo(RUNNING_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[2]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[3]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[4]), equalTo(SHUTDOWN_STATE));
    assertThat(getResourceVersion(updatedDomain), not(getResourceVersion(domain)));
  }

  @Test
  void whenMakeRightRunFailsEarly_populateAvailableAndCompletedConditions() {
    consoleHandlerMemento.ignoringLoggedExceptions(ApiException.class);
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    testSupport.failOnResource(SECRET, null, NS, KubernetesConstants.HTTP_BAD_REQUEST);

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).execute();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain, hasCondition(Available).withStatus("False"));
    assertThat(updatedDomain, hasCondition(Completed).withStatus("False"));
  }

  @Test
  void afterMakeRightAndChangeServerToNever_desiredStateIsShutdown() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).execute();

    domainConfigurator.withDefaultServerStartPolicy("NEVER");
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).withExplicitRecheck().execute();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);

    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[0]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[1]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[2]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[3]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[4]), equalTo(SHUTDOWN_STATE));
    assertThat(getResourceVersion(updatedDomain), not(getResourceVersion(domain)));
  }

  @Test
  void afterServersUpdated_updateDomainStatus() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    final DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();
    info.setWebLogicCredentialsSecret(createCredentialsSecret());
    makePodsReady();
    makePodsHealthy();

    triggerStatusUpdate();

    assertThat(testSupport.getResourceWithName(DOMAIN, UID), hasCondition(Completed).withStatus("True"));
  }

  @Test
  void afterChangeToNever_statusUpdateRetainsDesiredState() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    final DomainPresenceInfo info1 = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info1).execute();
    domainConfigurator.withDefaultServerStartPolicy("NEVER");
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).withExplicitRecheck().execute();

    info1.setWebLogicCredentialsSecret(createCredentialsSecret());
    makePodsReady();
    makePodsHealthy();
    triggerStatusUpdate();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getDesiredState(updatedDomain, ADMIN_NAME), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[0]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[1]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[2]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[3]), equalTo(SHUTDOWN_STATE));
    assertThat(getDesiredState(updatedDomain, MANAGED_SERVER_NAMES[4]), equalTo(SHUTDOWN_STATE));
  }

  private void triggerStatusUpdate() {
    testSupport.setTime((int) TuningParameters.getInstance().getMainTuning().initialShortDelay, TimeUnit.SECONDS);
  }

  private void makePodsHealthy() {
    defineOKResponse(ADMIN_NAME, 7001);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(ms -> defineOKResponse(ms, 8001));
  }

  private void makePodsReady() {
    testSupport.<V1Pod>getResources(POD).stream()
          .filter(this::isWlsServer)
          .forEach(pod -> pod.setStatus(createReadyStatus()));
  }

  private V1PodStatus createReadyStatus() {
    return new V1PodStatus().phase("Running")
          .addConditionsItem(new V1PodCondition().type("Ready").status("True"));
  }

  private V1Secret createCredentialsSecret() {
    return new V1Secret()
          .metadata(new V1ObjectMeta().namespace(NS).name(SECRET_NAME))
          .data(Map.of(USERNAME_KEY, "user".getBytes(),
                PASSWORD_KEY, "password".getBytes()));
  }

  private void defineOKResponse(@Nonnull String serverName, int port) {
    final String url = "http://" + UID + "-" + serverName + "." + NS + ":" + port;
    httpSupport.defineResponse(createExpectedRequest(url), createStub(HttpResponseStub.class, HTTP_OK, OK_RESPONSE));
  }

  private boolean isWlsServer(V1Pod pod) {
    return Optional.of(pod)
          .map(V1Pod::getMetadata)
          .map(V1ObjectMeta::getLabels)
          .stream()
          .anyMatch(this::hasServerNameLabel);
  }

  private boolean hasServerNameLabel(Map<String,String> labels) {
    return labels.containsKey(SERVERNAME_LABEL);
  }

  @Test
  void whenDomainScaledDown_removeExcessPodsAndServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).withExplicitRecheck().execute();

    assertThat((int) getServerServices().count(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  void whenDomainScaledDown_withPreCreateServerService_doesNotRemoveServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS).withPrecreateServerService(true);

    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).withExplicitRecheck().execute();

    assertThat((int) getServerServices().count(), equalTo(MAX_SERVERS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  void whenStrandedResourcesExist_removeThem() {
    V1Service service1 = createServerService("admin");
    V1Service service2 = createServerService("ms1");
    testSupport.defineResources(service1, service2);

    processor.createMakeRightOperation(new DomainPresenceInfo(NS, UID)).withExplicitRecheck().forDeletion().execute();

    assertThat(testSupport.getResources(SERVICE), empty());
  }

  @Test
  void whenDomainShutDown_removeAllPodsServicesAndPodDisruptionBudgets() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), empty());
  }

  @Test
  void whenDomainShutDown_ignoreNonOperatorServices() {
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
  void whenDomainScaledUp_podDisruptionBudgetMinAvailableUpdated()
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
  void whenDomainScaledDown_podDisruptionBudgetMinAvailableUpdated() throws JsonProcessingException {
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
    return runningPDBs.stream().findFirst()
          .map(V1beta1PodDisruptionBudget::getSpec)
          .map(V1beta1PodDisruptionBudgetSpec::getMinAvailable)
          .map(IntOrString::getIntValue).orElse(0) == count;
  }

  @Test
  void whenDomainShutDown_ignoreNonOperatorPodDisruptionBudgets() {
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
  void whenMakeRightExecuted_ignoreNonOperatorPodDisruptionBudgets() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorPodDisruptionBudget());


    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();

    assertThat(info.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas2_server3WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);
    domainConfigurator.configureServer(getManagedServerName(3)).withServerStartPolicy(START_ALWAYS);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3)) {
      assertServerPodAndServicePresent(info, getManagedServerName(i));
    }
    assertServerPodNotPresent(info, getManagedServerName(2));

    assertThat(info.getClusterService(CLUSTER), notNullValue());
    assertThat(info.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterScaleUpToReplicas3_fromReplicas2_server3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 3));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    domainConfigurator.configureServer(getManagedServerName(3)).withServerStartPolicy(START_ALWAYS);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(info, getManagedServerName(i));
    }
    assertThat(info.getClusterService(CLUSTER), notNullValue());

  }

  @Test
  void whenClusterScaleDownToReplicas1_fromReplicas2_server3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1,3));

    domainConfigurator.configureCluster(CLUSTER).withReplicas(1);
    domainConfigurator.configureServer(getManagedServerName(3)).withServerStartPolicy(START_ALWAYS);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    logRecords.clear();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and one managed server pods
    assertThat(runningPods.size(), equalTo(3));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    assertServerPodAndServicePresent(info, getManagedServerName(3));
    for (Integer i : Arrays.asList(1,2)) {
      assertServerPodNotPresent(info, getManagedServerName(i));
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
    assertThat(info.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas3_server3And4WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);

    for (Integer i : Arrays.asList(3,4)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(START_ALWAYS);
    }

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3,4)) {
      assertServerPodAndServicePresent(info, getManagedServerName(i));
    }
    assertServerPodNotPresent(info, getManagedServerName(2));

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterScaleUpToReplicas4_fromReplicas2_server3And4WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 3, 4));

    for (Integer i : Arrays.asList(3,4)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(START_ALWAYS);
    }

    domainConfigurator.configureCluster(CLUSTER).withReplicas(4);

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);

    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and four managed server pods
    assertThat(runningPods.size(), equalTo(6));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3,4)) {
      assertServerPodAndServicePresent(info, getManagedServerName(i));
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas2_server1And2And3WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);

    for (Integer i : Arrays.asList(1,2,3)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(START_ALWAYS);
    }

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(info, getManagedServerName(i));
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterScaleDownToReplicas1_fromReplicas2_server1And2And3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3));

    // now scale down the cluster
    domainConfigurator.configureCluster(CLUSTER).withReplicas(1);

    for (Integer i : Arrays.asList(1,2,3)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(START_ALWAYS);
    }

    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();
    logRecords.clear();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(info, getManagedServerName(i));
    }
    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas2_server2NeverPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(2);
    domainConfigurator.configureServer(getManagedServerName(2)).withServerStartPolicy(START_NEVER);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3)) {
      assertServerPodAndServicePresent(info, getManagedServerName(i));
    }
    assertServerPodNotPresent(info, getManagedServerName(2));
    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas2_allServersExcept5NeverPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(3);
    int[] servers = IntStream.rangeClosed(1, MAX_SERVERS).toArray();
    for (int i : servers) {
      if (i != 5) {
        domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(START_NEVER);
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
        assertServerPodAndServiceNotPresent(info, getManagedServerName(i));
      } else {
        assertServerPodAndServicePresent(info, getManagedServerName(i));
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
                            .putLabelsItem(CREATEDBYOPERATOR_LABEL, "false"))
            .spec(new V1beta1PodDisruptionBudgetSpec()
                    .selector(new V1LabelSelector()
                            .putMatchLabelsItem(CREATEDBYOPERATOR_LABEL, "false")
                            .putMatchLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                            .putMatchLabelsItem(CLUSTERNAME_LABEL, CLUSTER)));
  }

  @Test
  void onUpgradeFromV20_updateExternalService() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    testSupport.defineResources(createV20ExternalService());
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(info.getExternalService(ADMIN_NAME), notNullValue());
  }

  @Test
  void whenNoExternalServiceNameSuffixConfigured_externalServiceNameContainsDefaultSuffix() {
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
  void whenExternalServiceNameSuffixConfigured_externalServiceNameContainsSuffix() {
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
  void beforeIntrospectionForNewDomain_addDefaultCondition() {
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    testSupport.addDomainPresenceInfo(info);
    makeRightOperation.execute();

    assertThat(info.getDomain(), hasCondition(Completed).withStatus("False"));
  }

  @Test
  void whenDomainHasRunningServersAndExistingTopology_dontRunIntrospectionJob() throws JsonProcessingException {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(OLD_INTROSPECTION_STATE);
    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  @Test
  void whenDomainHasIntrospectVersionDifferentFromOldDomain_runIntrospectionJob() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    assertThat(job, notNullValue());
  }

  @Test
  void whenIntrospectionJobRun_recordIt() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    MakeRightDomainOperation makeRight = this.processor.createMakeRightOperation(
          new DomainPresenceInfo(newDomain)).interrupt();
    makeRight.execute();

    assertThat(makeRight.wasInspectionRun(), is(true));
  }

  @Test
  void whenIntrospectionJobNotComplete_waitForIt() throws Exception {
    establishPreviousIntrospection(null);
    jobStatus = createNotCompletedStatus();

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    MakeRightDomainOperation makeRight = this.processor.createMakeRightOperation(
          new DomainPresenceInfo(newDomain)).interrupt();
    makeRight.execute();

    assertThat(processorDelegate.waitedForIntrospection(), is(true));
  }


  @Test
  void whenIntrospectionJobTimedOut_failureCountIncremented() throws Exception {
    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    establishPreviousIntrospection(null);
    jobStatus = createTimedOutStatus();
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    assertThat(newDomain.getStatus().getIntrospectJobFailureCount(), is(1));
  }

  @Test
  void whenIntrospectionJobTimedOut_createAbortedEvent() throws Exception {
    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    establishPreviousIntrospection(null);
    jobStatus = createTimedOutStatus();
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    executeScheduledRetry();

    assertThat(getEvents().stream().anyMatch(EventTestUtils::isDomainFailedAbortedEvent), is(true));
  }

  @Test
  void whenIntrospectionJobTimedOut_activeDeadlineIncremented() throws Exception {
    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    establishPreviousIntrospection(null);
    jobStatus = createTimedOutStatus();
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    executeScheduledRetry();

    assertThat(getJob().getSpec().getActiveDeadlineSeconds(), is(240L));
  }

  private void executeScheduledRetry() {
    testSupport.setTime(10, TimeUnit.SECONDS);
  }


  @NotNull
  private V1Job getJob() {
    return (V1Job) testSupport.getResources(JOB).stream().findFirst().get();
  }

  V1JobStatus createTimedOutStatus() {
    return new V1JobStatus().addConditionsItem(new V1JobCondition().status("True").type("Failed")
            .reason("DeadlineExceeded"));
  }

  @Test
  void whenIntrospectionJobPodTimedOut_jobRecreatedAndFailedConditionCleared() throws Exception {
    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    jobStatus = createBackoffStatus();
    establishPreviousIntrospection(null);
    defineTimedoutIntrospection();
    testSupport.doOnDelete(JOB, j -> deletePod());
    testSupport.doOnCreate(JOB, j -> createJobPodAndSetCompletedStatus(job));
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    assertThat(isDomainConditionFailed(), is(false));
  }

  private boolean isDomainConditionFailed() {
    return newDomain.getStatus().getConditions().stream().anyMatch(c -> c.getType() == Failed);
  }

  V1JobStatus createBackoffStatus() {
    return new V1JobStatus().addConditionsItem(new V1JobCondition().status("True").type("Failed")
            .reason("BackoffLimitExceeded"));
  }

  private void deletePod() {
    testSupport.deleteResources(new V1Pod().metadata(new V1ObjectMeta().name(getJobName()).namespace(NS)));
  }

  private static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  private void createJobPodAndSetCompletedStatus(V1Job job) {
    Map<String, String> labels = new HashMap<>();
    labels.put(LabelConstants.JOBNAME_LABEL, getJobName());
    testSupport.defineResources(POD,
            new V1Pod().metadata(new V1ObjectMeta().name(getJobName()).labels(labels).namespace(NS)));
    job.setStatus(createCompletedStatus());
  }

  private void defineTimedoutIntrospection() {
    V1Job job = asFailedJob(createIntrospectorJob("TIMEDOUT_JOB"));
    testSupport.defineResources(job);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, job);
    setJobPodStatusReasonDeadlineExceeded();
  }

  private void setJobPodStatusReasonDeadlineExceeded() {
    testSupport.<V1Pod>getResourceWithName(POD, getJobName()).status(new V1PodStatus().reason("DeadlineExceeded"));
  }

  private V1Job asFailedJob(V1Job job) {
    job.setStatus(new V1JobStatus().addConditionsItem(new V1JobCondition().status("True").type("Failed")
            .reason("BackoffLimitExceeded")));
    return job;
  }

  @Test
  void whenIntrospectionJobInitContainerHasImagePullFailure_jobRecreatedAndFailedConditionCleared() throws Exception {
    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    jobStatus = createBackoffStatus();
    establishPreviousIntrospection(null);
    defineIntrospectionWithInitContainerImagePullError();
    testSupport.doOnDelete(JOB, j -> deletePod());
    testSupport.doOnCreate(JOB, j -> createJobPodAndSetCompletedStatus(job));
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(new DomainPresenceInfo(newDomain)).interrupt().execute();

    assertThat(isDomainConditionFailed(), is(false));
  }

  private void defineIntrospectionWithInitContainerImagePullError() {
    V1Job job = asFailedJob(createIntrospectorJob("IMAGE_PULL_FAILURE_JOB"));
    testSupport.defineResources(job);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, job);
    setJobPodInitContainerStatusImagePullError();
  }

  private void setJobPodInitContainerStatusImagePullError() {
    testSupport.<V1Pod>getResourceWithName(POD, getJobName()).status(new V1PodStatus().initContainerStatuses(
            Arrays.asList(new V1ContainerStatus().state(new V1ContainerState().waiting(
                    new V1ContainerStateWaiting().reason("ImagePullBackOff").message("Back-off pulling image"))))));
  }

  private V1Job createIntrospectorJob(String uid) {
    return new V1Job().metadata(createJobMetadata(uid)).status(new V1JobStatus());
  }

  private V1ObjectMeta createJobMetadata(String uid) {
    return new V1ObjectMeta().name(getJobName()).namespace(NS).creationTimestamp(SystemClock.now()).uid(uid);
  }

  private void establishPreviousIntrospection(Consumer<Domain> domainSetup) throws JsonProcessingException {
    establishPreviousIntrospection(domainSetup, Arrays.asList(1,2));
  }

  private void establishPreviousIntrospection(Consumer<Domain> domainSetup, List<Integer> msNumbers)
          throws JsonProcessingException {
    establishPreviousIntrospection(domainSetup, msNumbers, Collections.singletonList(CLUSTER), new ArrayList<>());
  }

  private void establishPreviousIntrospection(Consumer<Domain> domainSetup, List<Integer> msNumbers,
                                              List<String> clusterNames, List<String> independentServers)
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
    testSupport.defineResources(createIntrospectorConfigMap(OLD_INTROSPECTION_STATE, clusterNames, independentServers));
    testSupport.doOnCreate(KubernetesTestSupport.JOB, j -> recordJob((V1Job) j));
    domainConfigurator.withIntrospectVersion(OLD_INTROSPECTION_STATE);
  }

  private String getCurrentImageSpecHash() {
    return String.valueOf(ConfigMapHelper.getModelInImageSpecHash(newDomain.getSpec().getImage()));
  }

  // define a config map with a topology to avoid the no-topology condition that always runs the introspector
  @SuppressWarnings("SameParameterValue")
  private V1ConfigMap createIntrospectorConfigMap(String introspectionDoneValue, List<String> clusterNames,
                                                  List<String> serverNames) throws JsonProcessingException {
    return new V1ConfigMap()
          .metadata(createIntrospectorConfigMapMeta(introspectionDoneValue))
          .data(new HashMap<>(Map.of(IntrospectorConfigMapConstants.TOPOLOGY_YAML,
                  defineTopology(clusterNames, serverNames),
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
    return defineTopology(Collections.singletonList(CLUSTER), new ArrayList<>());
  }

  private String defineTopology(List<String> clusterNames, List<String> serverNames) throws JsonProcessingException {
    return IntrospectionTestUtils.createTopologyYaml(createDomainConfig(clusterNames, serverNames));
  }

  // case 1: job was able to pull, time out during introspection: pod will have DEADLINE_EXCEEDED

  @Test
  void afterIntrospection_introspectorConfigMapHasUpToDateLabel() throws Exception {
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
  void afterInitialIntrospection_serverPodsHaveInitialIntrospectVersionLabel() {
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
  void afterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
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
  void afterScaleupClusterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
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
  void afterScaledownClusterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
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
  void whenDomainTypeIsDomainInPV_dontRerunIntrospectionJob() throws Exception {
    establishPreviousIntrospection(this::configureForDomainInPV);

    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  void configureForDomainInPV(Domain d) {
    configureDomain(d).withDomainHomeSourceType(PersistentVolume);
  }

  private V1Job job;

  private void recordJob(V1Job job) {
    this.job = job;
  }

  @Test
  void whenDomainTypeIsDomainInImage_dontRerunIntrospectionJob() throws Exception {
    establishPreviousIntrospection(d -> configureDomain(d).withDomainHomeSourceType(Image));

    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  @Test
  void whenDomainTypeIsFromModelDomainAndNoChanges_dontRerunIntrospectionJob() throws Exception {
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
  void whenDomainTypeIsFromModelDomainAndImageHashChanged_runIntrospectionJob() throws Exception {
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
  void whenDomainTypeIsFromModelDomainAndManagedServerModified_runIntrospectionJobThenRoll() throws Exception {
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
  void whenDomainTypeIsFromModelOnlineUpdateSuccessUpdateRestartRequired() throws Exception {
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
  String getManagedPodName(int i) {
    return LegalNames.toPodName(UID, getManagedServerName(i));
  }

  @Nonnull
  String getAdminPodName() {
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
  void whenDomainTypeIsFromModelDomainAndNewServerCreated_dontRunIntrospectionJobFirst() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    configureDomain(newDomain).configureCluster(CLUSTER).withReplicas(3);

    makeRightOperation.execute();
    assertThat(introspectionRunBeforeUpdates, hasEntry(getManagedPodName(3), false));
  }

  @Test
  void whenDomainTypeIsFromModelDomainAndAdminServerModified_runIntrospectionJobFirst() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    configureDomain(newDomain).configureAdminServer().withAdditionalVolume("newVol", "/path");

    makeRightOperation.execute();

    assertThat(introspectionRunBeforeUpdates, hasEntry(getAdminPodName(), true));
  }

  @Test
  void afterChangeTriggersIntrospection_doesNotRunIntrospectionOnNextExplicitMakeRight() throws Exception {
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
  void whenDomainTypeIsFromModelDomainAndAdminServerModified_runIntrospectionJobFirst2() throws Exception {
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

  @Test
  void whenRunningClusterAndIndependentManagedServerRemovedFromDomainTopology_establishMatchingPresence()
          throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3, 4), Arrays.asList(CLUSTER, CLUSTER2),
            Arrays.asList(INDEPENDENT_SERVER));
    domainConfigurator.configureCluster(CLUSTER).withReplicas(4);
    domainConfigurator.configureCluster(CLUSTER2).withReplicas(4);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    processor.createMakeRightOperation(info).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod, one independent server and four managed server pods for each cluster
    assertThat(runningPods.size(), equalTo(11));

    removeSecondClusterAndIndependentServerFromDomainTopology();
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    runningPods = getRunningPods();
    //one introspector pod, one admin server pod and four managed server pods for the one remaining cluster
    assertThat(runningPods.size(), equalTo(6));
  }

  @Test
  void whenIstioDomainWideAdminPortEnabled_checkReadinessPortAndScheme() throws Exception {

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
        + ">>> EOF\n";

    String topologyxml = "domainValid: true\n"
            + "domain:\n"
            + "  name: \"base_domain\"\n"
            + "  adminServerName: \"admin-server\"\n"
            + "  configuredClusters:\n"
            + "  - name: \"cluster-1\"\n"
            + "    servers:\n"
            + "      - name: \"managed-server1\"\n"
            + "        listenPort: 7003\n"
            + "        listenAddress: \"domain1-managed-server1\"\n"
            + "        adminPort: 7099\n"
            + "        sslListenPort: 7104\n"
            + "      - name: \"managed-server2\"\n"
            + "        listenPort: 7003\n"
            + "        listenAddress: \"domain1-managed-server2\"\n"
            + "        adminPort: 7099\n"
            + "        sslListenPort: 7104\n"
            + "  servers:\n"
            + "    - name: \"admin-server\"\n"
            + "      listenPort: 7001\n"
            + "      listenAddress: \"domain1-admin-server\"\n"
            + "      adminPort: 7099\n"
            + "    - name: \"server1\"\n"
            + "      listenPort: 9003\n"
            + "      adminPort: 7099\n"
            + "      listenAddress: \"domain1-server1\"\n"
            + "      sslListenPort: 8003\n"
            + "    - name: \"server2\"\n"
            + "      listenPort: 9004\n"
            + "      listenAddress: \"domain1-server2\"\n"
            + "      adminPort: 7099\n"
            + "      sslListenPort: 8004\n";

    //establishPreviousIntrospection(null);
    domainConfigurator.withIstio();
    domainConfigurator.configureCluster("cluster-1").withReplicas(2);

    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS,
        String.format(introspectorResult, topologyxml));
    makeRightOperation.execute();
    List<V1Pod> runningPods = getRunningPods();
    assertThat(runningPods.size(), equalTo(6));

    assertThat(getContainerReadinessPort(runningPods,"test-domain-server1"), equalTo(7099));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-server2"), equalTo(7099));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-managed-server1"), equalTo(7099));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-managed-server2"), equalTo(7099));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-admin-server"), equalTo(7099));

    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server1"), equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server2"), equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server1"), equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server2"), equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-admin-server"), equalTo("HTTPS"));

  }

  @Test
  void whenIstioDomainNoWideAdminPortEnabled_checkReadinessPortAndScheme() throws Exception {

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
        + ">>> EOF\n";

    String topologyxml = "domainValid: true\n"
        + "domain:\n"
        + "  name: \"base_domain\"\n"
        + "  adminServerName: \"admin-server\"\n"
        + "  configuredClusters:\n"
        + "  - name: \"cluster-1\"\n"
        + "    servers:\n"
        + "      - name: \"managed-server1\"\n"
        + "        listenPort: 7003\n"
        + "        listenAddress: \"domain1-managed-server1\"\n"
        + "        sslListenPort: 7104\n"
        + "      - name: \"managed-server2\"\n"
        + "        listenPort: 7003\n"
        + "        listenAddress: \"domain1-managed-server2\"\n"
        + "        sslListenPort: 7104\n"
        + "  servers:\n"
        + "    - name: \"admin-server\"\n"
        + "      listenPort: 7001\n"
        + "      listenAddress: \"domain1-admin-server\"\n"
        + "    - name: \"server1\"\n"
        + "      listenPort: 9003\n"
        + "      listenAddress: \"domain1-server1\"\n"
        + "      sslListenPort: 8003\n"
        + "    - name: \"server2\"\n"
        + "      listenPort: 9004\n"
        + "      listenAddress: \"domain1-server2\"\n"
        + "      sslListenPort: 8004\n";

    //establishPreviousIntrospection(null);
    domainConfigurator.withIstio();
    domainConfigurator.configureCluster("cluster-1").withReplicas(2);

    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS,
        String.format(introspectorResult, topologyxml));
    makeRightOperation.execute();
    List<V1Pod> runningPods = getRunningPods();
    assertThat(runningPods.size(), equalTo(6));

    assertThat(getContainerReadinessPort(runningPods,"test-domain-server1"), equalTo(8888));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-server2"), equalTo(8888));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-managed-server1"), equalTo(8888));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-managed-server2"), equalTo(8888));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-admin-server"), equalTo(8888));

    // default  is not set
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server1"), equalTo(null));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server2"), equalTo(null));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server1"), equalTo(null));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server2"), equalTo(null));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-admin-server"), equalTo(null));

  }


  private void removeSecondClusterAndIndependentServerFromDomainTopology() throws JsonProcessingException {
    testSupport.deleteResources(new V1ConfigMap()
            .metadata(createIntrospectorConfigMapMeta(OLD_INTROSPECTION_STATE)));
    testSupport.defineResources(new V1ConfigMap()
            .metadata(createIntrospectorConfigMapMeta(OLD_INTROSPECTION_STATE))
            .data(new HashMap<>(Map.of(IntrospectorConfigMapConstants.TOPOLOGY_YAML,
                    IntrospectionTestUtils.createTopologyYaml(createDomainConfig()),
                    IntrospectorConfigMapConstants.DOMAIN_INPUTS_HASH, getCurrentImageSpecHash()))));
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
    defineServerResources(serverName, CLUSTER);
  }

  private void defineServerResources(String serverName, String clusterName) {
    testSupport.defineResources(createServerPod(serverName, clusterName), createServerService(serverName, clusterName));
  }

  /**/
  private V1Pod createServerPod(String serverName, String clusterName) {
    Packet packet = new Packet();
    packet
          .getComponents()
          .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(new DomainPresenceInfo(domain)));
    packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig);

    if (ADMIN_NAME.equals(serverName)) {
      packet.put(ProcessingConstants.SERVER_SCAN, domainConfig.getServerConfig(serverName));
      return PodHelper.createAdminServerPodModel(packet);
    } else {
      packet.put(ProcessingConstants.CLUSTER_NAME, clusterName);
      packet.put(ProcessingConstants.SERVER_SCAN, getClusteredServerConfig(serverName, clusterName));
      return PodHelper.createManagedServerPodModel(packet);
    }
  }

  private WlsServerConfig getClusteredServerConfig(String serverName, String clusterName) {
    return domainConfig.getClusterConfig(clusterName).getServerConfigs()
          .stream()
          .filter(c -> serverName.equals(c.getName())).findFirst()
          .orElseThrow();
  }


  private V1ObjectMeta withServerLabels(V1ObjectMeta meta, String serverName) {
    return KubernetesUtils.withOperatorLabels(DomainProcessorTestSetup.UID, meta)
        .putLabelsItem(SERVERNAME_LABEL, serverName);
  }
  
  private V1Service createServerService(String serverName) {
    return createServerService(serverName, null);
  }

  private V1Service createServerService(String serverName, String clusterName) {
    V1Service service = new V1Service()
        .metadata(
            withServerLabels(
                new V1ObjectMeta()
                    .name(
                        LegalNames.toServerServiceName(
                            DomainProcessorTestSetup.UID, serverName))
                    .namespace(NS),
                serverName));

    if (clusterName != null && !clusterName.isEmpty()) {
      service.getMetadata().putLabelsItem(CLUSTERNAME_LABEL, clusterName);
    }

    return AnnotationHelper.withSha256Hash(service);
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
  void whenDomainIsNotValid_dontBringUpServers() {
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
  void whenDomainIsNotValid_updateStatus() {
    defineDuplicateServerNames();

    processor.createMakeRightOperation(new DomainPresenceInfo(domain)).withExplicitRecheck().execute();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("DomainInvalid"));
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

  private Integer getContainerReadinessPort(List<V1Pod> pods, String podName) {
    for (V1Pod pod : pods) {
      if (pod.getMetadata().getName().equals(podName)) {
        return Optional.ofNullable(pod)
            .map(V1Pod::getSpec)
            .map(V1PodSpec::getContainers)
            .flatMap(c -> c.stream().findFirst())
            .map(V1Container::getReadinessProbe)
            .map(V1Probe::getHttpGet)
            .map(V1HTTPGetAction::getPort)
            .map(IntOrString::getIntValue)
            .orElse(null);
      }
    }
    return null;
  }

  private String getContainerReadinessScheme(List<V1Pod> pods, String podName) {
    for (V1Pod pod : pods) {
      if (pod.getMetadata().getName().equals(podName)) {
        return Optional.ofNullable(pod)
            .map(V1Pod::getSpec)
            .map(V1PodSpec::getContainers)
            .flatMap(c -> c.stream().findFirst())
            .map(V1Container::getReadinessProbe)
            .map(V1Probe::getHttpGet)
            .map(V1HTTPGetAction::getScheme)
            .orElse(null);
      }
    }
    return null;
  }

  private void defineDuplicateServerNames() {
    newDomain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    newDomain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
  }

  @Test
  void whenExceptionDuringProcessing_reportInDomainStatus() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    DomainProcessorImpl.registerDomainPresenceInfo(info);
    forceExceptionDuringProcessing();

    testSupport.setTime(DomainPresence.getDomainPresenceFailureRetrySeconds(), TimeUnit.SECONDS);
    processor.createMakeRightOperation(info).withExplicitRecheck().throwNPE().execute();

    assertThat(
        getRecordedDomain(),
        hasCondition(Failed).withStatus("True").withReason(Internal));
  }

  private Domain getRecordedDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
  }

  @Test
  void whenExceptionDuringProcessing_createFailedEvent() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    DomainProcessorImpl.registerDomainPresenceInfo(info);
    forceExceptionDuringProcessing();

    testSupport.setTime(DomainPresence.getDomainPresenceFailureRetrySeconds(), TimeUnit.SECONDS);
    processor.createMakeRightOperation(info).withExplicitRecheck().throwNPE().execute();

    assertThat(getEvents().stream().anyMatch(EventTestUtils::isDomainInternalFailedEvent), is(true));
  }

  private void forceExceptionDuringProcessing() {
    consoleHandlerMemento.ignoringLoggedExceptions(NullPointerException.class);
    domain.getSpec().withWebLogicCredentialsSecret(null);
  }

  @Test
  void whenWebLogicCredentialsSecretRemoved_NullPointerExceptionAndAbortedEventNotGenerated() {
    consoleHandlerMemento.ignoreMessage(NOT_STARTING_DOMAINUID_THREAD);
    DomainProcessorImpl.registerDomainPresenceInfo(new DomainPresenceInfo(domain));
    domain.getSpec().withWebLogicCredentialsSecret(null);
    int time = 0;

    for (int numRetries = 0; numRetries < DomainPresence.getDomainPresenceFailureRetryMaxCount(); numRetries++) {
      processor.createMakeRightOperation(new DomainPresenceInfo(domain)).withExplicitRecheck().execute();
      time += DomainPresence.getDomainPresenceFailureRetrySeconds();
      testSupport.setTime(time, TimeUnit.SECONDS);
    }

    assertThat(getEvents().stream().anyMatch(EventTestUtils::isDomainFailedAbortedEvent), is(false));
  }

  private void addFailedCondition(DomainStatus status) {
    status.addCondition(new DomainCondition(Failed).withStatus("True").withReason(Kubernetes));
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

}
