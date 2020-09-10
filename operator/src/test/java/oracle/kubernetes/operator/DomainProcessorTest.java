// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.PodHelper;
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
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainSourceType.FromModel;
import static oracle.kubernetes.operator.DomainSourceType.Image;
import static oracle.kubernetes.operator.DomainSourceType.PersistentVolume;
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
import static oracle.kubernetes.operator.logging.MessageKeys.NOT_STARTING_DOMAINUID_THREAD;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
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
  private static final int MAX_SERVERS = 5;
  private static final String MS_PREFIX = "managed-server";
  private static final int MIN_REPLICAS = 2;
  private static final int NUM_ADMIN_SERVERS = 1;
  private static final int NUM_JOB_PODS = 1;
  private static final String[] MANAGED_SERVER_NAMES =
      IntStream.rangeClosed(1, MAX_SERVERS).mapToObj(DomainProcessorTest::getManagedServerName).toArray(String[]::new);

  @Nonnull
  private static String getManagedServerName(int n) {
    return MS_PREFIX + n;
  }

  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final Domain newDomain = DomainProcessorTestSetup.createTestDomain();
  private final DomainConfigurator domainConfigurator = configureDomain(newDomain);
  private final MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newDomain);
  private final WlsDomainConfig domainConfig = createDomainConfig();

  private static WlsDomainConfig createDomainConfig() {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(CLUSTER);
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig("base_domain")
        .withAdminServer(ADMIN_NAME, "domain1-admin-server", 7001)
        .withCluster(clusterConfig);
  }

  /**
   * Setup test environment.
   * @throws Exception if StaticStubSupport fails to install
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, NOT_STARTING_DOMAINUID_THREAD).withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());
    mementos.add(ScanCacheStub.install());

    testSupport.defineResources(newDomain);
    new DomainProcessorTestSetup(testSupport).defineKubernetesResources(createDomainConfig());
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
  }

  /**
   * Cleanup test environment.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void whenDomainSpecNotChanged_dontRunUpdateThread() {
    DomainProcessorImpl.registerDomainPresenceInfo(new DomainPresenceInfo(domain));

    makeRightOperation.execute();

    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, domain.getDomainUid());
    assertThat(getResourceVersion(updatedDomain), equalTo(getResourceVersion(domain)));
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
  }

  @Test
  public void whenMakeRightRun_updateSDomainStatus() {
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
  public void whenDomainShutDown_removeAllPodsAndServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
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

  @Test
  public void onUpgradeFromV20_updateExternalService() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    testSupport.defineResources(createV20ExternalService());
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(info.getExternalService(ADMIN_NAME), notNullValue());
  }

  private static final String OLD_INTROSPECTION_STATE = "123";
  private static final String NEW_INTROSPECTION_STATE = "124";
  private static final String INTROSPECTOR_MAP_NAME = UID + KubernetesConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;

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

  private void establishPreviousIntrospection(Consumer<Domain> domainSetup) throws JsonProcessingException {
    if (domainSetup != null) {
      domainSetup.accept(domain);
      domainSetup.accept(newDomain);
    }
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    defineServerResources(ADMIN_NAME);
    defineServerResources(getManagedServerName(1));
    defineServerResources(getManagedServerName(2));
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
          .data(new HashMap<>(Map.of(IntrospectorConfigMapKeys.TOPOLOGY_YAML, defineTopology(),
                                     IntrospectorConfigMapKeys.DOMAIN_INPUTS_HASH, getCurrentImageSpecHash())));
  }

  private V1ObjectMeta createIntrospectorConfigMapMeta(@Nullable String introspectionDoneValue) {
    final V1ObjectMeta meta = new V1ObjectMeta()
          .namespace(NS)
          .name(ConfigMapHelper.getIntrospectorConfigMapName(UID));
    Optional.ofNullable(introspectionDoneValue).ifPresent(v -> meta.putLabelsItem(INTROSPECTION_STATE_LABEL, v));
    return meta;
  }

  private String defineTopology() throws JsonProcessingException {
    return DomainProcessorTestSetup.createTopologyYaml(createDomainConfig());
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
          .ifPresent(data -> data.put(IntrospectorConfigMapKeys.DOMAIN_INPUTS_HASH, "changedHash"));
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
    assertThat(serverName + " pod", info.getServerPod(serverName), nullValue());
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
