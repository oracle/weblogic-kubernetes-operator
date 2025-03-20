// Copyright (c) 2019, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1HTTPGetAction;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetSpec;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.Watch.Response;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.ClusterPresenceInfo;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.http.client.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.client.HttpResponseStub;
import oracle.kubernetes.operator.http.rest.Scan;
import oracle.kubernetes.operator.http.rest.ScanCache;
import oracle.kubernetes.operator.http.rest.ScanCacheStub;
import oracle.kubernetes.operator.introspection.IntrospectionTestUtils;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.OperatorUtils;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterCondition;
import oracle.kubernetes.weblogic.domain.model.ClusterConditionType;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainConditionFailureInfo;
import oracle.kubernetes.weblogic.domain.model.DomainConditionType;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.InitializeDomainOnPV;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.hamcrest.MatcherAssert;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static java.util.logging.Level.INFO;
import static oracle.kubernetes.common.logging.MessageKeys.ASYNC_NO_RETRY;
import static oracle.kubernetes.common.logging.MessageKeys.JOB_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.NOT_STARTING_DOMAINUID_THREAD;
import static oracle.kubernetes.common.logging.MessageKeys.WATCH_CLUSTER;
import static oracle.kubernetes.common.logging.MessageKeys.WATCH_CLUSTER_DELETED;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.SECRET_NAME;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainSourceType.FROM_MODEL;
import static oracle.kubernetes.operator.DomainSourceType.IMAGE;
import static oracle.kubernetes.operator.DomainSourceType.PERSISTENT_VOLUME;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_CHANGED_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_DELETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_INCOMPLETE_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithLabels;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_OK;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CLUSTER_OBSERVED_GENERATION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAIN_OBSERVED_GENERATION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTION_COMPLETE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SUSPENDING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.UNKNOWN_STATE;
import static oracle.kubernetes.operator.helpers.AffinityHelper.getDefaultAntiAffinity;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_DELETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_DELETED;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CONFIG_MAP;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.JOB;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SECRET;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static oracle.kubernetes.operator.helpers.SecretHelper.PASSWORD_KEY;
import static oracle.kubernetes.operator.helpers.SecretHelper.USERNAME_KEY;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTBIT_CONFIGMAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTBIT_CONFIG_DATA_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIGMAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIG_DATA_NAME;
import static oracle.kubernetes.operator.http.client.HttpAsyncTestSupport.OK_RESPONSE;
import static oracle.kubernetes.operator.http.client.HttpAsyncTestSupport.createExpectedRequest;
import static oracle.kubernetes.operator.tuning.TuningParameters.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.COMPLETED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.weblogic.domain.model.DomainStatusNoConditionMatcher.hasNoCondition;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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
  private static final String CLUSTER3 = "Cluster-3";
  private static final String CLUSTER4 = "Cluster-4";
  private static final String INDEPENDENT_SERVER = "server-1";
  private static final int MAX_SERVERS = 5;
  private static final String MS_PREFIX = "managed-server";
  private static final int MIN_REPLICAS = 2;
  private static final int NUM_ADMIN_SERVERS = 1;
  private static final int NUM_JOB_PODS = 1;
  private static final String[] MANAGED_SERVER_NAMES = getManagedServerNames(CLUSTER);

  static final String DOMAIN_NAME = "base_domain";
  public static final String NEW_DOMAIN_UID = "56789";
  public static final String EXEC_FORMAT_ERROR = "Exec format error";
  static long uidNum = 0;
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;
  private final HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private final KubernetesExecFactoryFake execFactoryFake = new KubernetesExecFactoryFake();
  private final DomainNamespaces domainNamespaces = new DomainNamespaces(null);

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
  private final DomainProcessorDelegateStub processorDelegate = DomainProcessorDelegateStub.createDelegate(testSupport);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo originalInfo = new DomainPresenceInfo(domain);
  private final DomainResource newDomain = DomainProcessorTestSetup.createTestDomain(2L);
  private final DomainPresenceInfo newInfo = new DomainPresenceInfo(newDomain);
  private final DomainConfigurator domainConfigurator = configureDomain(newDomain);
  private final WlsDomainConfig domainConfig = createDomainConfig();
  private final DomainProcessorTestSupport domainProcessorTestSupport = new DomainProcessorTestSupport();

  private final JobStatusSupplier jobStatusSupplier = new JobStatusSupplier(createCompletedStatus());

  private static class JobStatusSupplier implements Supplier<V1JobStatus> {
    private V1JobStatus jobStatus;

    JobStatusSupplier(V1JobStatus jobStatus) {
      this.jobStatus = jobStatus;
    }

    void setJobStatus(V1JobStatus jobStatus) {
      this.jobStatus = jobStatus;
    }

    @Override
    public V1JobStatus get() {
      return jobStatus;
    }
  }

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
    mementos.add(domainProcessorTestSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());
    mementos.add(ScanCacheStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(NoopWatcherStarter.install());
    testSupport.defineResources(newDomain);
    IntrospectionTestUtils.defineIntrospectionTopology(testSupport, createDomainConfig(), jobStatusSupplier);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    ScanCache.INSTANCE.registerScan(NS,UID, new Scan(domainConfig, SystemClock.now()));
  }

  @AfterEach
  void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  void whenDomainAddedWithChangedEventData_runMakeRightButDontGenerateDomainCreatedEvent() {
    processor.createMakeRightOperation(newInfo).withEventData(new EventData(DOMAIN_CHANGED)).execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
    assertThat(testSupport, not(hasEvent(DOMAIN_CREATED.getReason())));
  }

  @Test
  void whenDomainSpecNotChanged_dontRunMakeRight() {
    processor.registerDomainPresenceInfo(newInfo);

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, newDomain.getDomainUid());
    assertThat(getResourceVersion(updatedDomain), equalTo(getResourceVersion(newDomain)));
  }

  @Test
  void whenDomainSpecNotChanged_newInfoMissingCluster_dontRunMakeRight() {
    domain.getMetadata().generation(getGeneration(newDomain));
    ClusterResource clusterResource1 = createClusterResource(NS, CLUSTER);
    configureDomain(domain).configureCluster(originalInfo, clusterResource1.getClusterName());
    testSupport.defineResources(clusterResource1);
    originalInfo.addClusterResource(clusterResource1);
    processor.registerDomainPresenceInfo(originalInfo);

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, newDomain.getDomainUid());
    assertThat(getResourceVersion(updatedDomain), equalTo(getResourceVersion(newDomain)));
  }

  private String getResourceVersion(DomainResource domain) {
    return Optional.of(domain).map(DomainResource::getMetadata).map(V1ObjectMeta::getResourceVersion).orElse("");
  }

  private Long getGeneration(KubernetesObject resource) {
    return Optional.ofNullable(resource).map(KubernetesObject::getMetadata).map(V1ObjectMeta::getGeneration).orElse(0L);
  }

  @Test
  void whenNamespaceNotRunning_dontRunMakeRight() {
    processorDelegate.setNamespaceRunning(false);
    processor.registerDomainPresenceInfo(originalInfo);

    processor.createMakeRightOperation(originalInfo).execute();

    assertThat(testSupport.getNumItemsRun(), equalTo(0));
  }

  @Test
  void whenCachedDomainIsNewerThanSpecifiedDomain_runMakeRightWhenNotStartedFromEvent() {
    consoleHandlerMemento.ignoreMessage(NOT_STARTING_DOMAINUID_THREAD);
    final DomainResource cachedDomain = this.domain;
    processor.registerDomainPresenceInfo(new DomainPresenceInfo(cachedDomain));
    cachedDomain.getMetadata().setCreationTimestamp(laterThan(newDomain));

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(testSupport.getNumItemsRun(), greaterThan(0));
  }

  @Test
  void whenCachedDomainIsNewerThanSpecifiedDomain_dontRunMakeRightWhenHasEventData() {
    consoleHandlerMemento.ignoreMessage(NOT_STARTING_DOMAINUID_THREAD);
    final DomainResource cachedDomain = this.domain;
    processor.registerDomainPresenceInfo(new DomainPresenceInfo(cachedDomain));
    cachedDomain.getMetadata().setCreationTimestamp(laterThan(newDomain));

    processor.createMakeRightOperation(newInfo)
        .withEventData(new EventData(DOMAIN_CHANGED))
        .execute();

    assertThat(testSupport.getNumItemsRun(), equalTo(0));
  }

  @SuppressWarnings("ConstantConditions")
  private OffsetDateTime laterThan(DomainResource newDomain) {
    return newDomain.getMetadata().getCreationTimestamp().plusSeconds(1);
  }

  @Test
  void whenExplicitRecheckRequested_runMakeRight() {
    processor.registerDomainPresenceInfo(originalInfo);

    processor.createMakeRightOperation(originalInfo).withExplicitRecheck().execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainChangedSpec_runMakeRight() {
    processor.registerDomainPresenceInfo(originalInfo);

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainChangedSpecWithForDeletion_dontGenerateDomainChangedEvent() {
    processor.registerDomainPresenceInfo(originalInfo);

    processor.createMakeRightOperation(newInfo).forDeletion().execute();

    assertThat(testSupport, not(hasEvent(DOMAIN_CHANGED.getReason())));
    assertThat(testSupport, hasEvent(DOMAIN_DELETED.getReason()));
  }

  @Test
  void whenDomainSpecNotChangedWithRetryableFailureButNotRetrying_dontContinueProcessing() {
    originalInfo.setPopulated(true);
    processor.registerDomainPresenceInfo(originalInfo);
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(DOMAIN_INVALID));

    processor.createMakeRightOperation(originalInfo).withExplicitRecheck().execute();

    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
  }

  @Test
  void whenDomainSpecChangedWithRetryableFailureButNotRetrying_continueProcessing() {
    originalInfo.setPopulated(true);
    processor.registerDomainPresenceInfo(originalInfo);
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(DOMAIN_INVALID));

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainWithRetryableFailureButForDeletion_continueProcessing() {
    originalInfo.setPopulated(true);
    processor.registerDomainPresenceInfo(originalInfo);
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(DOMAIN_INVALID));

    processor.createMakeRightOperation(originalInfo).withExplicitRecheck().forDeletion().execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainWithNonRetryableFailureButForDeletion_continueProcessing() {
    originalInfo.setPopulated(true);
    processor.registerDomainPresenceInfo(originalInfo);
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED));

    processor.createMakeRightOperation(originalInfo).withExplicitRecheck().forDeletion().execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainWithRetryableFailureAndRetryOnFailure_continueProcess() {
    originalInfo.setPopulated(false);
    processor.registerDomainPresenceInfo(originalInfo);
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(DOMAIN_INVALID));

    processor.createMakeRightOperation(originalInfo).withExplicitRecheck().retryOnFailure().execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainChangedSpecNewer_setWillInterrupt() {
    processor.registerDomainPresenceInfo(originalInfo);

    final MakeRightDomainOperation operation = processor.createMakeRightOperation(newInfo);

    assertThat(operation.isWillInterrupt(), is(true));
  }

  @Test
  void whenDomainChangedSpecNotNewer_dontSetWillInterrupt() {
    processor.registerDomainPresenceInfo(newInfo);

    final MakeRightDomainOperation operation = processor.createMakeRightOperation(originalInfo);

    assertThat(operation.isWillInterrupt(), is(false));
  }

  @Test
  void whenDomainChangedSpecButProcessingAborted_dontRunUpdateThread() {
    processor.registerDomainPresenceInfo(originalInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("ugh"));

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
  }

  @Test
  void whenDomainChangedSpecAndProcessingAbortedButRestartVersionChanged_runUpdateThread() {
    processor.registerDomainPresenceInfo(originalInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("ugh"));
    domainConfigurator.withRestartVersion("17");

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainSpecMatchesFailureInfoAndProcessingAborted_dontRunUpdateThread() {
    DomainResource localDomain = DomainProcessorTestSetup.createTestDomain();
    DomainConfigurator localConfigurator = configureDomain(localDomain);
    localConfigurator.withRestartVersion("17");
    DomainPresenceInfo localInfo = new DomainPresenceInfo(localDomain);

    processor.registerDomainPresenceInfo(localInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED)
        .withFailureInfo(new DomainConditionFailureInfo().withRestartVersion("17")).withMessage("ugh"));
    domainConfigurator.withRestartVersion("17");

    processor.createMakeRightOperation(localInfo).execute();

    assertThat(logRecords, containsFine(NOT_STARTING_DOMAINUID_THREAD));
  }

  @Test
  void whenDomainSpecLeadsFailureInfoAndProcessingAborted_runUpdateThread() {
    // This test assumes a missed watch event or related situation where the
    // spec has been updated to a new restart version ("18") but where the
    // original failure and the related abort occurred at a different version ("17")

    DomainResource localDomain = DomainProcessorTestSetup.createTestDomain();
    localDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED)
        .withFailureInfo(new DomainConditionFailureInfo().withRestartVersion("17")).withMessage("ugh"));
    DomainConfigurator localConfigurator = configureDomain(localDomain);
    localConfigurator.withRestartVersion("18");
    DomainPresenceInfo localInfo = new DomainPresenceInfo(localDomain);

    processor.registerDomainPresenceInfo(localInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED)
        .withFailureInfo(new DomainConditionFailureInfo().withRestartVersion("17")).withMessage("ugh"));
    domainConfigurator.withRestartVersion("18");

    processor.createMakeRightOperation(localInfo).execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainResourceDoesNotExistsAndMakeRightNotForDeletion_introspectorJobNotCreated() {
    consoleHandlerMemento.collectLogMessages(logRecords, JOB_CREATED).withLogLevel(INFO);
    processor.registerDomainPresenceInfo(originalInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(AVAILABLE).withMessage("Test"));
    domainConfigurator.withRestartVersion("17");

    testSupport.failOnResource(DOMAIN, UID, NS, HTTP_NOT_FOUND);

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, not(containsInfo(JOB_CREATED)));
  }

  @Test
  void whenDomainResourceReadFailsWithUnrecoverableAndMakeRightNotForDeletion_noRetryWarningLogged() {
    consoleHandlerMemento.collectLogMessages(logRecords, ASYNC_NO_RETRY).withLogLevel(Level.WARNING);
    processor.registerDomainPresenceInfo(originalInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(AVAILABLE).withMessage("Test"));
    domainConfigurator.withRestartVersion("17");

    testSupport.failOnResource(DOMAIN, UID, NS, HTTP_BAD_REQUEST);

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, containsWarning(ASYNC_NO_RETRY));
  }

  @Test
  void whenDomainChangedSpecAndProcessingAbortedButInspectionVersionChanged_runUpdateThread() {
    processor.registerDomainPresenceInfo(originalInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("ugh"));
    domainConfigurator.withIntrospectVersion("17");

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainChangedSpecAndProcessingAbortedButImageChanged_runUpdateThread() {
    processor.registerDomainPresenceInfo(originalInfo);
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("ugh"));
    domainConfigurator.withDefaultImage("abcd:123");

    processor.createMakeRightOperation(newInfo).execute();

    assertThat(logRecords, not(containsFine(NOT_STARTING_DOMAINUID_THREAD)));
  }

  @Test
  void whenDomainConfiguredForMaxServers_establishMatchingPresence() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MAX_SERVERS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (String serverName : MANAGED_SERVER_NAMES) {
      assertServerPodAndServicePresent(newInfo, serverName);
    }

    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
    assertThat(newInfo.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenMakeRightRun_updateDomainStatusAndDomainObservedGeneration() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);

    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[0]), equalTo(RUNNING_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[1]), equalTo(RUNNING_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[2]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[3]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[4]), equalTo(SHUTDOWN_STATE));
    assertThat(getResourceVersion(updatedDomain), not(getResourceVersion(domain)));
    assertThat(updatedDomain.getStatus().getObservedGeneration(), equalTo(2L));
    assertThat(getDomainObservedGeneration(ADMIN_NAME), is("2"));
    assertThat(getDomainObservedGeneration(getManagedServerName(1)), is("2"));
    assertThat(getDomainObservedGeneration(getManagedServerName(2)), is("2"));
  }

  private String getDomainObservedGeneration(String name) {
    return getObservedGeneration(name, "DOMAIN");
  }

  private String getObservedGeneration(String name, String generationType) {
    return generationType.equals("DOMAIN") ? getPodLabels(name).get(DOMAIN_OBSERVED_GENERATION_LABEL)
        : getPodLabels(name).get(CLUSTER_OBSERVED_GENERATION_LABEL);
  }

  private Map<String, String> getPodLabels(String name) {
    return testSupport.<V1Pod>getResourceWithName(POD, UID + "-" + name).getMetadata().getLabels();
  }

  @Test
  void whenMakeRightRun_updateClusterResourceStatusAndClusterObservedGeneration() {
    ClusterResource clusterResource = createClusterResource(NS, CLUSTER);
    clusterResource.getMetadata().generation(2L);
    testSupport.defineResources(clusterResource);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    info.getDomain().getSpec().getClusters().add(new V1LocalObjectReference().name(CLUSTER));

    processor.createMakeRightOperation(info).withExplicitRecheck().execute();

    ClusterResource updatedClusterResource = testSupport
        .getResourceWithName(KubernetesTestSupport.CLUSTER, CLUSTER);

    assertThat(updatedClusterResource.getStatus(), notNullValue());
    assertThat(updatedClusterResource.getStatus().getMinimumReplicas(), equalTo(0));
    assertThat(updatedClusterResource.getStatus().getMaximumReplicas(), equalTo(5));
    assertThat(updatedClusterResource.getStatus().getObservedGeneration(), equalTo(2L));
    assertThat(getObservedGeneration(getManagedServerName(1), "CLUSTER"), is("2"));
  }

  @Test
  void whenMakeRightRunFailsEarly_populateAvailableAndCompletedConditions() {
    consoleHandlerMemento.ignoringLoggedExceptions(ApiException.class);
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    testSupport.failOnResource(SECRET, null, NS, KubernetesConstants.HTTP_BAD_REQUEST);

    processor.createMakeRightOperation(newInfo)
        .withEventData(new EventData(DOMAIN_CHANGED)).execute();

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain, hasCondition(AVAILABLE).withStatus("False"));
    assertThat(updatedDomain, hasCondition(COMPLETED).withStatus("False"));
    assertThat(updatedDomain.getStatus().getObservedGeneration(), equalTo(2L));
  }

  @Test
  void afterMakeRightAndChangeServerToNever_stateGoalIsShutdown() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    domainConfigurator.withDefaultServerStartPolicy(ServerStartPolicy.NEVER);
    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);

    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[0]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[1]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[2]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[3]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[4]), equalTo(SHUTDOWN_STATE));
    assertThat(getResourceVersion(updatedDomain), not(getResourceVersion(domain)));
  }

  @Test
  void afterMakeRightAndChangeServerToNever_serverPodsWaitForShutdownWithHttpToCompleteBeforeTerminating() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    domainConfigurator.withDefaultServerStartPolicy(ServerStartPolicy.NEVER);
    DomainStatus status = newInfo.getDomain().getStatus();
    defineServerShutdownWithHttpOkResponse();
    makePodsReady();
    setAdminServerStatus(status, SUSPENDING_STATE);
    setManagedServerState(status, SUSPENDING_STATE);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();
    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);

    assertThat(getRunningPods().size(), equalTo(4));
    setAdminServerStatus(status, SHUTDOWN_STATE);
    setManagedServerState(status, SHUTDOWN_STATE);
    testSupport.setTime(100, TimeUnit.SECONDS);
    assertThat(getRunningPods().size(), equalTo(1));
    assertThat(getResourceVersion(updatedDomain), not(getResourceVersion(domain)));
  }

  private void defineServerShutdownWithHttpOkResponse() {
    httpSupport.defineResponse(createShutdownRequest(ADMIN_NAME, 7001),
        createStub(HttpResponseStub.class, HTTP_OK, OK_RESPONSE));
    IntStream.range(1, 3).forEach(idx -> httpSupport.defineResponse(
        createShutdownRequest("cluster-managed-server" + idx, 8001),
        createStub(HttpResponseStub.class, HTTP_OK, OK_RESPONSE)));
  }

  private HttpRequest createShutdownRequest(String serverName, int portNumber) {
    String url = "http://test-domain-" + serverName + ".namespace.svc:" + portNumber;
    return HttpRequest.newBuilder()
        .uri(URI.create(url + "/management/weblogic/latest/serverRuntime/shutdown"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
  }

  private void setManagedServerState(DomainStatus status, String suspendingState) {
    IntStream.range(1, 3).forEach(idx -> getManagedServerStatus(status, idx).setState(suspendingState));
  }

  private void setAdminServerStatus(DomainStatus status, String state) {
    status.getServers().stream().filter(s -> matchingServerName(s, ADMIN_NAME)).forEach(s -> s.setState(state));
  }

  private ServerStatus getManagedServerStatus(DomainStatus status, int idx) {
    return status.getServers().stream()
        .filter(s -> matchingServerName(s, getManagedServerName(idx))).findAny().orElse(null);
  }

  private boolean matchingServerName(ServerStatus serverStatus, String serverName) {
    return serverStatus.getServerName().equals(serverName);
  }

  @Test
  void afterServersUpdated_updateDomainStatus() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();
    newInfo.setWebLogicCredentialsSecret(createCredentialsSecret());
    makePodsReady();
    makePodsHealthy();

    triggerStatusUpdate();

    assertThat(testSupport.getResourceWithName(DOMAIN, UID), hasCondition(COMPLETED).withStatus("True"));
  }

  @Test
  void afterServersUpdatedWhenFailedConditionExists_dontUpdateDomainStatus() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);
    processor.createMakeRightOperation(newInfo).execute();
    newInfo.setWebLogicCredentialsSecret(createCredentialsSecret());
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(KUBERNETES).withStatus(true));
    makePodsReady();
    makePodsHealthy();

    triggerStatusUpdate();

    assertThat(((DomainResource)testSupport.getResourceWithName(DOMAIN, UID)).getStatus(),
        hasNoCondition(COMPLETED).withStatus("True"));
  }

  @Test
  void afterChangeToNever_statusUpdateRetainsStateGoal() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();
    domainConfigurator.withDefaultServerStartPolicy(ServerStartPolicy.NEVER);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    newInfo.setWebLogicCredentialsSecret(createCredentialsSecret());
    makePodsReady();
    makePodsHealthy();

    triggerStatusUpdate();

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStateGoal(updatedDomain, ADMIN_NAME), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[0]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[1]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[2]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[3]), equalTo(SHUTDOWN_STATE));
    assertThat(getStateGoal(updatedDomain, MANAGED_SERVER_NAMES[4]), equalTo(SHUTDOWN_STATE));
  }

  private void triggerStatusUpdate() {
    testSupport.setTime(TuningParameters.getInstance().getInitialShortDelay(), TimeUnit.SECONDS);
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

  @SuppressWarnings("HttpUrlsUsage")
  private void defineOKResponse(@Nonnull String serverName, int port) {
    final String url = "http://" + UID + "-" + serverName + "." + NS + ".svc:" + port;
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

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    assertThat((int) getServerServices().count(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  void whenDomainScaledDownAndServerStateUnknown_removeExcessPods() {
    newInfo.updateLastKnownServerStatus("cluster-managed-server3", UNKNOWN_STATE);
    newInfo.updateLastKnownServerStatus("cluster-managed-server4", UNKNOWN_STATE);
    newInfo.updateLastKnownServerStatus("cluster-managed-server5", UNKNOWN_STATE);

    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  void whenDomainScaledDown_withPreCreateServerService_doesNotRemoveServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS).withPrecreateServerService(true);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    createMakeRight(newInfo).execute();

    assertThat((int) getServerServices().count(), equalTo(MAX_SERVERS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  void whenDomainScaledDown_withoutPreCreateServerService_removeService() {
    final String SERVER3 = MANAGED_SERVER_NAMES[2];
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3).withPrecreateServerService(false);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    createMakeRight(newInfo).execute();
    assertThat(isHeadlessService(SERVER3), is(true));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    assertThat(getServerService(SERVER3).isPresent(), is(false));
  }

  @Test
  void whenDomainWithoutPreCreateServerService_removeService() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS).withPrecreateServerService(true);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    createMakeRight(newInfo).execute();
    assertThat((int) getServerServices().count(), equalTo(MAX_SERVERS + NUM_ADMIN_SERVERS));
    newInfo.getReferencedClusters().getFirst().getSpec().setPrecreateServerService(false);
    newDomain.getSpec().setPrecreateServerService(false);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();
    assertThat((int) getServerServices().count(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS));
  }

  @Test
  void whenDomainScaledDown_withPreCreateServerService_createClusterIPService() {
    final String SERVER3 = MANAGED_SERVER_NAMES[2];
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3).withPrecreateServerService(true);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    createMakeRight(newInfo).execute();
    assertThat(isHeadlessService(SERVER3), is(true));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    assertThat(isClusterIPService(SERVER3), is(true));
  }

  @Test
  void whenDomainScaledUp_withPreCreateServerService_createHeadlessService() {
    final String SERVER3 = MANAGED_SERVER_NAMES[2];
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2).withPrecreateServerService(true);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();
    assertThat(isClusterIPService(SERVER3), is(true));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(newInfo)
        .withExplicitRecheck().execute();

    assertThat(isHeadlessService(SERVER3), is(true));
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

    processor.createMakeRightOperation(originalInfo).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), empty());
  }

  @Test
  void whenDomainMarkedForDeletion_removeAllPodsServicesAndPodDisruptionBudgets() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domain.getMetadata().setDeletionTimestamp(OffsetDateTime.now());
    // MakeRightOperation is created without forDeletion() similar to list or MODIFIED watch
    processor.createMakeRightOperation(originalInfo).interrupt().withExplicitRecheck().execute();

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), empty());
  }

  @Test
  void whenDomainShutDown_ignoreNonOperatorServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorService());

    processor.createMakeRightOperation(originalInfo).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), contains(createNonOperatorService()));
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), empty());
  }

  @Test
  void whenDomainScaledUp_podDisruptionBudgetMinAvailableUpdated()
          throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 1), is(true));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(newInfo)
            .withExplicitRecheck().execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 2), is(true));
  }

  @Test
  void whenDomainScaledDown_podDisruptionBudgetMinAvailableUpdated() throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 2), is(true));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2);
    newDomain.getMetadata().setCreationTimestamp(SystemClock.now());
    processor.createMakeRightOperation(newInfo)
            .withExplicitRecheck().execute();
    assertThat(minAvailableMatches(getRunningPDBs(), 1), is(true));
  }

  private Boolean minAvailableMatches(List<V1PodDisruptionBudget> runningPDBs, int count) {
    return runningPDBs.stream().findFirst()
          .map(V1PodDisruptionBudget::getSpec)
          .map(V1PodDisruptionBudgetSpec::getMinAvailable)
          .map(IntOrString::getIntValue).orElse(0) == count;
  }

  @Test
  void whenDomainShutDown_ignoreNonOperatorPodDisruptionBudgets() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorPodDisruptionBudget());

    processor.createMakeRightOperation(originalInfo).interrupt().forDeletion().withExplicitRecheck().execute();

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
    assertThat(getRunningPDBs(), contains(createNonOperatorPodDisruptionBudget()));
  }

  @Test
  void whenMakeRightExecuted_ignoreNonOperatorPodDisruptionBudgets() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorPodDisruptionBudget());


    processor.createMakeRightOperation(originalInfo).interrupt().withExplicitRecheck().execute();

    assertThat(originalInfo.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenNewClusterAddedWithoutStatus_generateClusterCreatedEvent() {
    processor.getClusterPresenceInfoMap().values().clear();
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    cluster1.setStatus(null);
    testSupport.defineResources(cluster1);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(testSupport, hasEvent(CLUSTER_CREATED.getReason()));
  }

  @Test
  void whenNewClusterAddedWithStatus_dontGenerateClusterCreatedEvent() {
    processor.getClusterPresenceInfoMap().values().clear();
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    cluster1.setStatus(new ClusterStatus());
    testSupport.defineResources(cluster1);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(testSupport, not(hasEvent(CLUSTER_CREATED.getReason())));
  }

  @Test
  void whenClusterChanged_generateClusterChangedEvent() {
    ClusterStatus status = new ClusterStatus().withClusterName(CLUSTER4);
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    processor.registerClusterPresenceInfo(info);
    ClusterResource cluster2 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    cluster2.getMetadata().setGeneration(1234L);
    testSupport.defineResources(cluster2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(testSupport, hasEvent(CLUSTER_CHANGED.getReason()));
    assertThat(getEventsForSeason(CLUSTER_CHANGED.getReason()), not(empty()));
  }

  @Test
  void whenClusterResourceWithDifferentMetadataNameAndSpecNameChanged_generateClusterChangedEvent() {
    ClusterStatus status = new ClusterStatus().withClusterName(CLUSTER4);
    ClusterResource cluster1 = createClusterWithDifferentMetadataAndSpecName(CLUSTER4, NS).withStatus(status);
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    processor.registerClusterPresenceInfo(info);
    ClusterResource cluster2 = createClusterWithDifferentMetadataAndSpecName(CLUSTER4, NS).withStatus(status);
    cluster2.getMetadata().setGeneration(1234L);
    testSupport.defineResources(cluster2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(testSupport, hasEvent(CLUSTER_CHANGED.getReason()));
    assertThat(getEventsForSeason(CLUSTER_CHANGED.getReason()), not(empty()));
  }

  private List<Object> getEventsForSeason(String reason) {
    return testSupport.getResources(EVENT).stream()
        .filter(e -> ((CoreV1Event)e).getReason().equals(reason)).collect(Collectors.toList());
  }

  @Test
  void whenClusterChangedButOlder_dontGenerateClusterChangedEvent() {
    ClusterStatus status = new ClusterStatus().withClusterName(CLUSTER4);
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    cluster1.getMetadata().setGeneration(1234L);
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    processor.registerClusterPresenceInfo(info);
    ClusterResource cluster2 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    cluster2.getMetadata().setGeneration(1000L);
    testSupport.defineResources(cluster2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(getEventsForSeason(CLUSTER_CHANGED.getReason()), empty());
  }

  @Test
  void whenClusterUnchanged_dontGenerateClusterChangedEvent() {
    ClusterStatus status = new ClusterStatus().withClusterName(CLUSTER4);
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    processor.registerClusterPresenceInfo(info);
    ClusterResource cluster2 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    testSupport.defineResources(cluster2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(getEventsForSeason(CLUSTER_CHANGED.getReason()), empty());
  }

  @Test
  void whenNewClusterAddedAndReferenced_generateClusterCreatedEventWithDomainUidLabel() {
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    testSupport.defineResources(cluster1);
    newInfo.getDomain().getClusters().add(new V1LocalObjectReference().name(CLUSTER4));

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(testSupport, hasEvent(CLUSTER_CREATED.getReason()));
    MatcherAssert.assertThat("Found CLUSTER_CREATED event with expected labels",
        containsEventWithLabels(testSupport.getResources(EVENT),
            CLUSTER_CREATED.getReason(), getExpectedLabels()), is(true));
  }

  @NotNull
  private Map<String, String> getExpectedLabels() {
    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.DOMAINUID_LABEL, newInfo.getDomainUid());
    expectedLabels.put(CREATEDBYOPERATOR_LABEL, "true");
    return expectedLabels;
  }

  @NotNull
  private Map<String, String> getExpectedLabelCreatedByOp() {
    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(CREATEDBYOPERATOR_LABEL, "true");
    return expectedLabels;
  }

  @Test
  void whenClusterStatusChangedToNull_generateClusterCreatedEvent() {
    ClusterStatus status = new ClusterStatus().withClusterName(CLUSTER4);
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    newInfo.getDomain().getClusters().add(new V1LocalObjectReference().name(CLUSTER4));
    processor.registerClusterPresenceInfo(info);
    ClusterResource cluster2 = createClusterAlone(CLUSTER4, NS).withStatus(null);
    testSupport.defineResources(cluster2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(testSupport, hasEvent(CLUSTER_CREATED.getReason()));
  }

  @Test
  void whenClusterChangedAndReferenced_generateClusterChangedEventWithDomainUIDLabel() {
    ClusterStatus status = new ClusterStatus().withClusterName(CLUSTER4);
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    newInfo.getDomain().getClusters().add(new V1LocalObjectReference().name(CLUSTER4));
    processor.registerClusterPresenceInfo(info);
    ClusterResource cluster2 = createClusterAlone(CLUSTER4, NS).withStatus(status);
    cluster2.getMetadata().setGeneration(1234L);
    testSupport.defineResources(cluster2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, processor));

    assertThat(testSupport, hasEvent(CLUSTER_CHANGED.getReason()));
    MatcherAssert.assertThat("Found CLUSTER_CHANGED event with expected labels",
        containsEventWithLabels(testSupport.getResources(EVENT),
            CLUSTER_CHANGED.getReason(), getExpectedLabels()), is(true));
  }

  @Test
  void whenClusterReplicas2_server3WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2);
    domainConfigurator.configureServer(getManagedServerName(3)).withServerStartPolicy(ServerStartPolicy.ALWAYS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3)) {
      assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
    }
    assertServerPodNotPresent(newInfo, getManagedServerName(2));

    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
    assertThat(newInfo.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterScaleUpToReplicas3_fromReplicas2_server3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 3));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3);
    domainConfigurator.configureServer(getManagedServerName(3)).withServerStartPolicy(ServerStartPolicy.ALWAYS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
    }
    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());

  }

  @Test
  void whenClusterScaleDownToReplicas1_fromReplicas2_server3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1,3));

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(1);
    domainConfigurator.configureServer(getManagedServerName(3)).withServerStartPolicy(ServerStartPolicy.ALWAYS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    logRecords.clear();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and one managed server pods
    assertThat(runningPods.size(), equalTo(3));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    assertServerPodAndServicePresent(newInfo, getManagedServerName(3));
    for (Integer i : Arrays.asList(1,2)) {
      assertServerPodNotPresent(newInfo, getManagedServerName(i));
    }

    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
    assertThat(newInfo.getPodDisruptionBudget(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas3_server3And4WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3);

    for (Integer i : Arrays.asList(3,4)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(ServerStartPolicy.ALWAYS);
    }
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3,4)) {
      assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
    }
    assertServerPodNotPresent(newInfo, getManagedServerName(2));

    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterScaleUpToReplicas4_fromReplicas2_server3And4WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 3, 4));

    for (Integer i : Arrays.asList(3,4)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(ServerStartPolicy.ALWAYS);
    }

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(4);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and four managed server pods
    assertThat(runningPods.size(), equalTo(6));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3,4)) {
      assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
    }

    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas2_server1And2And3WithAlwaysPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2);

    for (Integer i : Arrays.asList(1,2,3)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(ServerStartPolicy.ALWAYS);
    }
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
    }

    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterScaleDownToReplicas1_fromReplicas2_server1And2And3WithAlwaysPolicy_establishMatchingPresence()
      throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3));

    // now scale down the cluster
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(1);

    for (Integer i : Arrays.asList(1,2,3)) {
      domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(ServerStartPolicy.ALWAYS);
    }
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();
    logRecords.clear();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,2,3)) {
      assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
    }
    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterScaleDown_reportIncompleteEvent()
      throws JsonProcessingException {
    newDomain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(true));
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3));

    // now scale down the cluster
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(1);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();
    logRecords.clear();

    assertThat(testSupport, hasEvent(DOMAIN_INCOMPLETE_EVENT));
  }

  @Test
  void whenClusterReplicas2_server2NeverPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(2);
    domainConfigurator.configureServer(getManagedServerName(2)).withServerStartPolicy(ServerStartPolicy.NEVER);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (Integer i : Arrays.asList(1,3)) {
      assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
    }
    assertServerPodNotPresent(newInfo, getManagedServerName(2));
    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenClusterReplicas2_allServersExcept5NeverPolicy_establishMatchingPresence() {
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3);
    int[] servers = IntStream.rangeClosed(1, MAX_SERVERS).toArray();
    for (int i : servers) {
      if (i != 5) {
        domainConfigurator.configureServer(getManagedServerName(i)).withServerStartPolicy(ServerStartPolicy.NEVER);
      }
    }
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and one managed server pods
    assertThat(runningPods.size(), equalTo(3));

    assertServerPodAndServicePresent(newInfo, ADMIN_NAME);
    for (int i : servers) {
      if (i != 5) {
        assertServerPodAndServiceNotPresent(newInfo, getManagedServerName(i));
      } else {
        assertServerPodAndServicePresent(newInfo, getManagedServerName(i));
      }
    }

    assertThat(newInfo.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  void whenMakeRightClusterAdded_reportClusterCreatedEvent() {
    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);

    processor.createMakeRightOperationForClusterEvent(CLUSTER_CREATED, cluster).execute();
    logRecords.clear();

    assertThat(testSupport, hasEvent(CLUSTER_CREATED_EVENT));
  }

  @Test
  void whenMakeRightClusterAddedOnExistingCluster_dontReportClusterCreatedEvent() {
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    ClusterPresenceInfo info = getInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);

    processor.createMakeRightOperationForClusterEvent(CLUSTER_CREATED, cluster).execute();
    logRecords.clear();
    processor.unregisterClusterPresenceInfo(info);

    assertThat(testSupport, not(hasEvent(CLUSTER_CREATED_EVENT)));
  }

  @Test
  void whenMakeRightClusterDeleted_reportClusterDeletedEvent() {
    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);
    ClusterPresenceInfo info = getInfo(cluster);
    processor.registerClusterPresenceInfo(info);

    processor.createMakeRightOperationForClusterEvent(CLUSTER_DELETED, cluster).execute();
    logRecords.clear();
    processor.unregisterClusterPresenceInfo(info);

    assertThat(testSupport, hasEvent(CLUSTER_DELETED_EVENT));
  }

  @NotNull
  private ClusterPresenceInfo getInfo(ClusterResource cluster) {
    return new ClusterPresenceInfo(cluster);
  }

  @Test
  void whenMakeRightClusterDeletedOnNonExistingCluster_dontReportClusterDeletedEvent() {
    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);
    ClusterPresenceInfo info = getInfo(cluster);
    processor.unregisterClusterPresenceInfo(info);

    processor.createMakeRightOperationForClusterEvent(CLUSTER_DELETED, cluster).execute();
    logRecords.clear();

    assertThat(testSupport, not(hasEvent(CLUSTER_DELETED_EVENT)));
  }

  @Test
  void whenMakeRightClusterModified_reportClusterChangedEvent() {
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    ClusterPresenceInfo info = getInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);
    cluster.getMetadata().setGeneration(1234L);
    processor.createMakeRightOperationForClusterEvent(CLUSTER_CHANGED, cluster).execute();
    logRecords.clear();
    processor.unregisterClusterPresenceInfo(info);

    assertThat(testSupport, hasEvent(CLUSTER_CHANGED_EVENT));
  }

  @Test
  void whenMakeRightClusterModifiedWithNoRealChange_dontReportClusterChangedEvent() {
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    ClusterPresenceInfo info = getInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);
    processor.createMakeRightOperationForClusterEvent(CLUSTER_CHANGED, cluster).execute();
    logRecords.clear();
    processor.unregisterClusterPresenceInfo(info);

    assertThat(testSupport, not(hasEvent(CLUSTER_CHANGED_EVENT)));
  }

  @Test
  void whenMakeRightClusterModifiedWithOnlyStatusChanges_dontReportClusterChangedEvent() {
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    ClusterPresenceInfo info = getInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);
    cluster.setStatus(new ClusterStatus().addCondition(
        new ClusterCondition(ClusterConditionType.COMPLETED).withStatus(ClusterCondition.TRUE)));
    processor.createMakeRightOperationForClusterEvent(CLUSTER_CHANGED, cluster).execute();
    logRecords.clear();
    processor.unregisterClusterPresenceInfo(info);

    assertThat(testSupport, not(hasEvent(CLUSTER_CHANGED_EVENT)));
  }

  @Test
  void whenMakeRightClusterModifiedWithOlderClusterObject_dontReportClusterChangedEvent() {
    ClusterResource cluster1 = createClusterAlone(CLUSTER4, NS);
    OffsetDateTime timeNow = OffsetDateTime.now();
    cluster1.getMetadata().setCreationTimestamp(timeNow);
    ClusterPresenceInfo info = getInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    ClusterResource cluster = createClusterAlone(CLUSTER4, NS);
    cluster.getMetadata().creationTimestamp(timeNow.minusSeconds(10L));
    processor.createMakeRightOperationForClusterEvent(CLUSTER_CHANGED, cluster).execute();
    logRecords.clear();
    processor.unregisterClusterPresenceInfo(info);

    assertThat(testSupport, not(hasEvent(CLUSTER_CHANGED_EVENT)));
  }

  private ClusterResource createClusterAlone(String clusterName, String ns) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().name(clusterName).namespace(ns))
        .spec(new ClusterSpec().withClusterName(clusterName));
  }

  private ClusterResource createClusterWithDifferentMetadataAndSpecName(String clusterMetadataName, String ns) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().name(clusterMetadataName).namespace(ns))
        .spec(new ClusterSpec().withClusterName("specClusterName-" + clusterMetadataName));
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
        .spec(new V1ServiceSpec().type("ClusterIP"));
  }

  private V1PodDisruptionBudget createNonOperatorPodDisruptionBudget() {
    return new V1PodDisruptionBudget()
            .metadata(
                    new V1ObjectMeta()
                            .name("do-not-delete-pdb")
                            .namespace(NS)
                            .putLabelsItem("serviceType", "SERVER")
                            .putLabelsItem(CREATEDBYOPERATOR_LABEL, "false"))
            .spec(new V1PodDisruptionBudgetSpec()
                    .selector(new V1LabelSelector()
                            .putMatchLabelsItem(CREATEDBYOPERATOR_LABEL, "false")
                            .putMatchLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                            .putMatchLabelsItem(CLUSTERNAME_LABEL, CLUSTER)));
  }

  @Test
  void onUpgradeFromV20_updateExternalService() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    testSupport.defineResources(createV20ExternalService());
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MAX_SERVERS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    assertThat(newInfo.getExternalService(ADMIN_NAME), notNullValue());
  }

  @Test
  void whenNoExternalServiceNameSuffixConfigured_externalServiceNameContainsDefaultSuffix() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    configureDomain(domain)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    processor.createMakeRightOperation(originalInfo).withExplicitRecheck().execute();

    assertThat(originalInfo.getExternalService(ADMIN_NAME).getMetadata().getName(),
        equalTo(originalInfo.getDomainUid() + "-" + ADMIN_NAME + "-ext"));
  }

  @Test
  void whenExternalServiceNameSuffixConfigured_externalServiceNameContainsSuffix() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    TuningParametersStub.setParameter(LegalNames.EXTERNAL_SERVICE_NAME_SUFFIX_PARAM, "-my-external-service");
    configureDomain(domain)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    processor.createMakeRightOperation(originalInfo).withExplicitRecheck().execute();

    assertThat(originalInfo.getExternalService(ADMIN_NAME).getMetadata().getName(),
        equalTo(originalInfo.getDomainUid() + "-" + ADMIN_NAME + "-my-external-service"));
  }

  private static final String OLD_INTROSPECTION_STATE = "123";
  private static final String NEW_INTROSPECTION_STATE = "124";
  private static final String INTROSPECTOR_MAP_NAME = UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;

  @Test
  void beforeIntrospectionForNewDomain_addDefaultCondition() {
    testSupport.addDomainPresenceInfo(newInfo);
    processor.createMakeRightOperation(newInfo).execute();

    assertThat(newInfo.getDomain(), hasCondition(COMPLETED).withStatus("False"));
  }

  @Test
  void runStatusInitializationStepWithKubernetesFailure_removeFailedCondition() {
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(KUBERNETES).withStatus(true));
    testSupport.addDomainPresenceInfo(newInfo);
    testSupport.runSteps(DomainStatusUpdater.createStatusInitializationStep(false));

    assertThat(newInfo.getDomain().getStatus(), hasNoCondition(FAILED).withReason(KUBERNETES));
  }

  @Test
  void runStatusInitializationStepWithNonKubernetesFailure_dontRemoveFailedCondition() throws JsonProcessingException {
    newDomain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(DOMAIN_INVALID).withStatus(true));
    testSupport.addDomainPresenceInfo(newInfo);
    testSupport.runSteps(DomainStatusUpdater.createStatusInitializationStep(false));

    assertThat(newInfo.getDomain(), hasCondition(FAILED).withStatus("True"));
  }

  @Test
  void whenDomainHasRunningServersAndExistingTopology_dontRunIntrospectionJob() throws JsonProcessingException {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(OLD_INTROSPECTION_STATE);
    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  @Test
  void whenDomainHasIntrospectVersionDifferentFromOldDomain_runIntrospectionJob() throws Exception {
    establishPreviousIntrospection(null);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    createMakeRight(newInfo).execute();

    assertThat(job, notNullValue());
  }

  private MakeRightDomainOperation createMakeRight(DomainPresenceInfo info) {
    return processor.createMakeRightOperation(info).interrupt();
  }

  @Test
  void whenIntrospectionJobRun_recordIt() throws Exception {
    establishPreviousIntrospection(null);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    MakeRightDomainOperation makeRight = createMakeRight(newInfo);
    makeRight.execute();

    assertThat(makeRight.wasInspectionRun(), is(true));
  }

  @Test
  void whenIntrospectionJobNotComplete_waitForIt() throws Exception {
    establishPreviousIntrospection(null);
    jobStatusSupplier.setJobStatus(createNotCompletedStatus());
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    MakeRightDomainOperation makeRight = this.processor.createMakeRightOperation(
          newInfo).interrupt();
    makeRight.execute();

    assertThat(processorDelegate.waitedForIntrospection(), is(true));
  }

  private void runMakeRight_withIntrospectionTimeout() throws JsonProcessingException {
    consoleHandlerMemento.ignoringLoggedExceptions(JobWatcher.DeadlineExceededException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);

    establishPreviousIntrospection(null);
    jobStatusSupplier.setJobStatus(createTimedOutStatus());
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    testSupport.doOnCreate(JOB, (j -> assignUid((V1Job) j)));
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).interrupt().execute();
  }

  private void assignUid(V1Job job) {
    Optional.ofNullable(job).map(V1Job::getMetadata).ifPresent(m -> m.setUid(Long.toString(++uidNum)));
  }

  @Test
  void whenIntrospectionJobTimedOut_activeDeadlineIncreased() throws Exception {
    TuningParametersStub.setParameter(INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS, "180");

    runMakeRight_withIntrospectionTimeout();

    executeScheduledRetry();

    assertThat(getRecordedJob().getSpec().getActiveDeadlineSeconds(), is(240L));
  }

  private V1Job getRecordedJob() {
    return testSupport.<V1Job>getResources(JOB).get(0);
  }

  private void executeScheduledRetry() {
    testSupport.setTime(domain.getFailureRetryIntervalSeconds(), TimeUnit.SECONDS);
  }

  @Test
  void whenIntrospectionJobTimedOutForInitDomainOnPV_activeDeadlineNotIncreased() throws Exception {
    TuningParametersStub.setParameter(INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS, "180");
    initializeDomainOnPV();
    runMakeRight_withIntrospectionTimeout();

    executeScheduledRetry();

    assertThat(getRecordedJob().getSpec().getActiveDeadlineSeconds(), is(180L));
  }

  private void initializeDomainOnPV() {
    domainConfigurator.withConfigurationForInitializeDomainOnPV(
        new InitializeDomainOnPV(), "test-volume", "test-pvc", "/shared");
  }

  @Test
  void whenFluentdSpecified_verifyConfigMap() {
    domainConfigurator
            .withFluentdConfiguration(true, "fluentd-cred",
                    null, null, null)
            .configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    V1ConfigMap fluentdConfigMap = testSupport.getResourceWithName(CONFIG_MAP, UID + FLUENTD_CONFIGMAP_NAME_SUFFIX);

    assertThat(Optional.ofNullable(fluentdConfigMap)
            .map(V1ConfigMap::getData)
            .stream().anyMatch(map -> map.containsKey(FLUENTD_CONFIG_DATA_NAME)), equalTo(true));

  }

  @Test
  void whenFluentdSpecifiedWithConfig_verifyConfigMap() {
    domainConfigurator
            .withFluentdConfiguration(true, "fluentd-cred",
                    "<match>me</match>", null, null)
            .configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    V1ConfigMap fluentdConfigMap = testSupport.getResourceWithName(CONFIG_MAP, UID + FLUENTD_CONFIGMAP_NAME_SUFFIX);

    assertThat(Optional.ofNullable(fluentdConfigMap)
            .map(V1ConfigMap::getData)
            .map(d -> d.get(FLUENTD_CONFIG_DATA_NAME))
            .orElse(null), equalTo("<match>me</match>"));
  }

  @Test
  void whenFluentbitSpecified_verifyConfigMap() {
    domainConfigurator
            .withFluentbitConfiguration(true, "fluentbit-cred",
                    null, null, null, null)
            .configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    V1ConfigMap fluentbitConfigMap = testSupport.getResourceWithName(CONFIG_MAP, UID + FLUENTBIT_CONFIGMAP_NAME_SUFFIX);

    assertThat(Optional.ofNullable(fluentbitConfigMap)
            .map(V1ConfigMap::getData)
            .stream().anyMatch(map -> map.containsKey(FLUENTBIT_CONFIG_DATA_NAME)), equalTo(true));

  }

  @Test
  void whenFluentbitSpecifiedWithConfig_verifyConfigMap() {
    domainConfigurator
            .withFluentbitConfiguration(true, "fluentbit-cred",
                    "[OUTPUT]", "[PARSER]", null, null)
            .configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    V1ConfigMap fluentbitConfigMap = testSupport.getResourceWithName(CONFIG_MAP, UID + FLUENTBIT_CONFIGMAP_NAME_SUFFIX);

    assertThat(Optional.ofNullable(fluentbitConfigMap)
            .map(V1ConfigMap::getData)
            .map(d -> d.get(FLUENTBIT_CONFIG_DATA_NAME))
            .orElse(null), equalTo("[OUTPUT]"));
  }

  V1JobStatus createTimedOutStatus() {
    return new V1JobStatus().addConditionsItem(new V1JobCondition().status("True").type("Failed")
            .reason("DeadlineExceeded"));
  }

  @Test
  void whenIntrospectionJobPodTimedOut_jobRecreatedAndFailedConditionCleared() throws Exception {
    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    jobStatusSupplier.setJobStatus(createBackoffStatus());
    establishPreviousIntrospection(null);
    defineTimedoutIntrospection();
    testSupport.doOnDelete(JOB, j -> deletePod());
    testSupport.doOnCreate(JOB, j -> createJobPodAndSetCompletedStatus(job));
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).interrupt().execute();

    assertThat(isDomainConditionFailed(), is(false));
  }

  private boolean isDomainConditionFailed() {
    return newDomain.getStatus().getConditions().stream().anyMatch(c -> c.getType() == FAILED);
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

  private void createJobPodAndSetExecFormatErrorStatus(V1Job job) {
    Map<String, String> labels = new HashMap<>();
    labels.put(LabelConstants.JOBNAME_LABEL, getJobName());
    testSupport.defineResources(POD,
        new V1Pod().metadata(new V1ObjectMeta().name(getJobName()).labels(labels).namespace(NS))
            .status(getInitContainerStatusWithExecFormatError()));
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
    job.setStatus(new V1JobStatus().addConditionsItem(
        new V1JobCondition().status("True").type("Failed")
            .reason("BackoffLimitExceeded")));
    return job;
  }

  @Test
  void whenIntrospectionJobInitContainerHasImagePullFailure_jobRecreatedAndFailedConditionCleared() throws Exception {
    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    jobStatusSupplier.setJobStatus(createBackoffStatus());

    establishPreviousIntrospection(null);
    defineIntrospectionWithInitContainerImagePullError();
    testSupport.doOnDelete(JOB, j -> deletePod());
    testSupport.doOnCreate(JOB, j -> createJobPodAndSetCompletedStatus(job));
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).interrupt().execute();

    assertThat(isDomainConditionFailed(), is(false));
  }

  private void defineIntrospectionWithInitContainerImagePullError() {
    V1Job job = asFailedJob(createIntrospectorJob("IMAGE_PULL_FAILURE_JOB"));
    testSupport.defineResources(job);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, job);
    testSupport.<V1Pod>getResourceWithName(POD, getJobName()).status(getInitContainerStatusWithImagePullError());
  }

  public static V1PodStatus getInitContainerStatusWithImagePullError() {
    return new V1PodStatus().initContainerStatuses(
          List.of(new V1ContainerStatus().state(new V1ContainerState().waiting(
                new V1ContainerStateWaiting().reason("ImagePullBackOff").message("Back-off pulling image")))));
  }

  private void defineIntrospectionWithInitContainerWithExecFormatError() {
    V1Job job = asFailedJob(createIntrospectorJob("IMAGE_PULL_FAILURE_JOB"));
    testSupport.defineResources(job);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, job);
    testSupport.<V1Pod>getResourceWithName(POD, getJobName()).status(getInitContainerStatusWithExecFormatError());
  }

  public static V1PodStatus getInitContainerStatusWithExecFormatError() {
    return new V1PodStatus().containerStatuses(List.of(new V1ContainerStatus().ready(false))).initContainerStatuses(
        List.of(new V1ContainerStatus().name("operator-aux-container1")
            .state(new V1ContainerState().terminated(new V1ContainerStateTerminated()
            .reason("Error")))));
  }

  private V1Job createIntrospectorJob(String uid) {
    return new V1Job().metadata(createJobMetadata(uid)).status(new V1JobStatus());
  }

  private V1ObjectMeta createJobMetadata(String uid) {
    return new V1ObjectMeta().name(getJobName()).namespace(NS).creationTimestamp(SystemClock.now()).uid(uid);
  }

  private void establishPreviousIntrospection(Consumer<DomainResource> domainSetup) throws JsonProcessingException {
    establishPreviousIntrospection(domainSetup, Arrays.asList(1,2));
  }

  private void establishPreviousIntrospection(Consumer<DomainResource> domainSetup, List<Integer> msNumbers)
          throws JsonProcessingException {
    establishPreviousIntrospection(domainSetup, msNumbers, Collections.singletonList(CLUSTER), new ArrayList<>());
  }

  private void establishPreviousIntrospection(Consumer<DomainResource> domainSetup, List<Integer> msNumbers,
                                              List<String> clusterNames, List<String> independentServers)
          throws JsonProcessingException {
    if (domainSetup != null) {
      domainSetup.accept(domain);
      domainSetup.accept(newDomain);
    }
    configureDomain(domain).configureCluster(originalInfo, CLUSTER)
        .withReplicas(MIN_REPLICAS).withAffinity(getDefaultAntiAffinity());
    domainConfigurator.configureCluster(newInfo, CLUSTER)
        .withReplicas(MIN_REPLICAS).withAffinity(getDefaultAntiAffinity());
    defineServerResources(ADMIN_NAME);
    for (Integer i : msNumbers) {
      defineServerResources(getManagedServerName(i));
    }
    processor.registerDomainPresenceInfo(originalInfo);
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

  @Test
  void whenIntrospectionJobInitContainerScriptExecError_domainStatusUpdated() throws Exception {


    consoleHandlerMemento.ignoringLoggedExceptions(RuntimeException.class);
    consoleHandlerMemento.ignoreMessage(MessageKeys.NOT_STARTING_DOMAINUID_THREAD);
    jobStatusSupplier.setJobStatus(createBackoffStatus());

    establishPreviousIntrospection(null);
    defineIntrospectionWithInitContainerWithExecFormatError();
    testSupport.doOnDelete(JOB, j -> deletePod());
    testSupport.doOnCreate(JOB, j -> createJobPodAndSetExecFormatErrorStatus(job));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS,
        String.format(EXEC_FORMAT_ERROR, defineTopology()));
    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).interrupt().execute();

    assertThat(newDomain.getStatus().getMessage().contains(EXEC_FORMAT_ERROR), is(true));
  }

  // case 1: job was able to pull, time out during introspection: pod will have DEADLINE_EXCEEDED

  @Test
  void afterIntrospection_introspectorConfigMapHasUpToDateLabel() throws Exception {
    establishPreviousIntrospection(null);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    processor.createMakeRightOperation(newInfo).interrupt().execute();

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
    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo).withExplicitRecheck();
    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(MIN_REPLICAS);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    makeRightOperation.execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));
    for (V1Pod pod: runningPods) {
      if (isNotIntrospectorPod(pod)) {
        assertThat(getServerPodIntrospectionVersion(pod), equalTo(OLD_INTROSPECTION_STATE));
      }
    }
  }

  private boolean isNotIntrospectorPod(V1Pod pod) {
    return !pod.getMetadata().getName().contains(LegalNames.getIntrospectorJobNameSuffix());
  }

  @Test
  void afterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.withIntrospectVersion(NEW_INTROSPECTION_STATE);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and two managed server pods
    assertThat(runningPods.size(), equalTo(4));
    for (V1Pod pod: runningPods) {
      if (isNotIntrospectorPod(pod)) {
        assertThat(getServerPodIntrospectionVersion(pod), equalTo(NEW_INTROSPECTION_STATE));
      }
    }
  }

  @Test
  void afterScaleupClusterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(3);
    domainConfigurator.withIntrospectVersion("after-scaleup");
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and three managed server pods
    assertThat(runningPods.size(), equalTo(5));
    for (V1Pod pod: runningPods) {
      if (isNotIntrospectorPod(pod)) {
        assertThat(getServerPodIntrospectionVersion(pod), equalTo("after-scaleup"));
      }
    }
  }

  @Test
  void afterScaledownClusterIntrospection_serverPodsHaveUpToDateIntrospectVersionLabel() throws Exception {
    establishPreviousIntrospection(null);

    domainConfigurator.configureCluster(newInfo, CLUSTER).withReplicas(1);
    domainConfigurator.withIntrospectVersion("after-scaledown");
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    List<V1Pod> runningPods = getRunningPods();
    //one introspector pod, one admin server pod and one managed server pod
    assertThat(runningPods.size(), equalTo(3));
    for (V1Pod pod: runningPods) {
      if (isNotIntrospectorPod(pod)) {
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

    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  void configureForDomainInPV(DomainResource d) {
    configureDomain(d).withDomainHomeSourceType(PERSISTENT_VOLUME);
  }

  private V1Job job;

  private void recordJob(V1Job job) {
    this.job = job;
  }

  @Test
  void whenDomainTypeIsDomainInImage_dontRerunIntrospectionJob() throws Exception {
    establishPreviousIntrospection(d -> configureDomain(d).withDomainHomeSourceType(IMAGE));
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  @Test
  void whenDomainTypeIsFromModelDomainAndNoChanges_dontRerunIntrospectionJob() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    makeRightOperation.execute();

    assertThat(makeRightOperation.wasInspectionRun(), is(false));
  }

  void configureForModelInImage(DomainResource domain) {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL).withRuntimeEncryptionSecret("wdt-cm-secret");
  }

  void configureForModelInImageOnlineUpdate(DomainResource domain) {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL).withRuntimeEncryptionSecret("wdt-cm-secret")
      .withMIIOnlineUpdate();
  }

  private DomainConfigurator configureDomain(DomainResource domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  @Test
  void whenDomainTypeIsFromModelDomainAndImageHashChanged_runIntrospectionJob() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    cacheChangedDomainInputsHash();
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
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

    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
    domainConfigurator.configureServer(getManagedServerName(1)).withAdditionalVolume("vol1", "/path");
    domainConfigurator.configureServer(getManagedServerName(2)).withAdditionalVolume("vol2", "/path");

    makeRightOperation.execute();

    assertThat(introspectionRunBeforeUpdates,
          allOf(hasEntry(getManagedPodName(1), true), hasEntry(getManagedPodName(2), true)));
  }

  @Test
  void whenDomainTypeIsFromModelOnlineUpdateSuccessUpdateRestartRequired() throws Exception {
    getMIIOnlineUpdateIntrospectResult(DomainConditionType.CONFIG_CHANGES_PENDING_RESTART,
        ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED);
  }

  @SuppressWarnings("SameParameterValue")
  private void getMIIOnlineUpdateIntrospectResult(DomainConditionType domainConditionType, String updateResult)
      throws Exception {

    processor.registerDomainPresenceInfo(originalInfo);

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
        + ">>>  updatedomainResult=%s\n"
        + DOMAIN_INTROSPECTION_COMPLETE;

    establishPreviousIntrospection(this::configureForModelInImageOnlineUpdate);
    domainConfigurator.withIntrospectVersion("after-onlineUpdate");
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS,
        String.format(introspectorResult, defineTopology(), updateResult));
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    makeRightOperation.execute();
    boolean found = false;
    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    if (updatedDomain.getStatus() != null) {
      for (DomainCondition domainCondition : updatedDomain.getStatus().getConditions()) {
        if (domainCondition.getType() == domainConditionType) {
          found = true;
          break;
        }
      }
    }
    assertThat(found, is(true));

    domainProcessorTestSupport.getCachedPresenceInfo("namespace", "test-domain").getServerPods()
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

  private void recordPodCreation(MakeRightDomainOperation makeRightOperation, V1Pod pod) {
    Optional.of(pod)
          .map(V1Pod::getMetadata)
          .map(V1ObjectMeta::getName)
          .ifPresent(name -> introspectionRunBeforeUpdates.put(name, makeRightOperation.wasInspectionRun()));
  }

  // Map of server names to a boolean, indicating that the introspection had already been run when the pod was created
  private final Map<String,Boolean> introspectionRunBeforeUpdates = new HashMap<>();

  @Test
  void whenDomainTypeIsFromModelDomainAndNewServerCreated_dontRunIntrospectionJobFirst() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
    configureDomain(newDomain).configureCluster(newInfo, CLUSTER).withReplicas(3);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    makeRightOperation.execute();
    assertThat(introspectionRunBeforeUpdates, hasEntry(getManagedPodName(3), false));
  }

  @Test
  void whenDomainTypeIsFromModelDomainAndAdminServerModified_runIntrospectionJobFirst() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
    configureDomain(newDomain).configureAdminServer().withAdditionalVolume("newVol", "/path");
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    makeRightOperation.execute();

    assertThat(introspectionRunBeforeUpdates, hasEntry(getAdminPodName(), true));
  }

  @Test
  void afterChangeTriggersIntrospection_doesNotRunIntrospectionOnNextExplicitMakeRight() throws Exception {
    establishPreviousIntrospection(this::configureForModelInImage);
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("wdt-cm-secret").namespace(NS)));
    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
    configureDomain(newDomain).configureAdminServer().withAdditionalVolume("newVol", "/path");
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    makeRightOperation.execute();
    job = null;

    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    assertThat(job, nullValue());
  }

  @Test
  void whenRunningClusterAndIndependentManagedServerRemovedFromDomainTopology_establishMatchingPresence()
          throws JsonProcessingException {
    establishPreviousIntrospection(null, Arrays.asList(1, 2, 3, 4), Arrays.asList(CLUSTER, CLUSTER2),
          List.of(INDEPENDENT_SERVER));
    domainConfigurator.withDefaultReplicaCount(2);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    processor.createMakeRightOperation(newInfo).execute();

    //one introspector pod, one admin server pod, one independent server and two managed server pods for each cluster
    assertThat(getRunningPods().size(), equalTo(7));

    removeSecondClusterAndIndependentServerFromDomainTopology();
    processor.createMakeRightOperation(newInfo).withExplicitRecheck().execute();

    //one introspector pod, one admin server pod and two managed server pods for the one remaining cluster
    assertThat(getRunningPods().size(), equalTo(4));
  }

  @Test
  void whenDomainWideAdminPortEnabled_checkReadinessPortAndScheme() {

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
        + DOMAIN_INTROSPECTION_COMPLETE;

    String topologyxml = """
        domainValid: true
        domain:
          name: "base_domain"
          adminServerName: "admin-server"
          configuredClusters:
          - name: "cluster-1"
            servers:
              - name: "managed-server1"
                listenPort: 7003
                listenAddress: "domain1-managed-server1"
                adminPort: 7099
                sslListenPort: 7104
              - name: "managed-server2"
                listenPort: 7003
                listenAddress: "domain1-managed-server2"
                adminPort: 7099
                sslListenPort: 7104
          servers:
            - name: "admin-server"
              listenPort: 7001
              listenAddress: "domain1-admin-server"
              adminPort: 7099
            - name: "server1"
              listenPort: 9003
              adminPort: 7099
              listenAddress: "domain1-server1"
              sslListenPort: 8003
            - name: "server2"
              listenPort: 9004
              listenAddress: "domain1-server2"
              adminPort: 7099
              sslListenPort: 8004
        """;

    //establishPreviousIntrospection(null);
    domainConfigurator.configureCluster(newInfo, "cluster-1").withReplicas(2);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
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

    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server1"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server2"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server1"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server2"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-admin-server"),
        equalTo("HTTPS"));

  }

  @Test
  void whenDomainNoWideAdminPortEnabled_checkReadinessPortAndScheme() {

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
        + DOMAIN_INTROSPECTION_COMPLETE;

    String topologyxml = """
        domainValid: true
        domain:
          name: "base_domain"
          adminServerName: "admin-server"
          configuredClusters:
          - name: "cluster-1"
            servers:
              - name: "managed-server1"
                listenPort: 7003
                listenAddress: "domain1-managed-server1"
                sslListenPort: 7104
              - name: "managed-server2"
                listenPort: 7003
                listenAddress: "domain1-managed-server2"
                sslListenPort: 7104
          servers:
            - name: "admin-server"
              listenPort: 7001
              listenAddress: "domain1-admin-server"
            - name: "server1"
              listenPort: 9003
              listenAddress: "domain1-server1"
              sslListenPort: 8003
            - name: "server2"
              listenPort: 9004
              listenAddress: "domain1-server2"
              sslListenPort: 8004
        """;

    //establishPreviousIntrospection(null);
    domainConfigurator.configureCluster(newInfo,"cluster-1").withReplicas(2);
    newInfo.getReferencedClusters().forEach(testSupport::defineResources);

    MakeRightDomainOperation makeRightOperation = processor.createMakeRightOperation(newInfo);
    testSupport.doOnCreate(POD, p -> recordPodCreation(makeRightOperation, (V1Pod) p));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS,
        String.format(introspectorResult, topologyxml));
    makeRightOperation.execute();
    List<V1Pod> runningPods = getRunningPods();
    assertThat(runningPods.size(), equalTo(6));

    assertThat(getContainerReadinessPort(runningPods,"test-domain-server1"), equalTo(8003));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-server2"), equalTo(8004));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-managed-server1"), equalTo(7104));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-managed-server2"), equalTo(7104));
    assertThat(getContainerReadinessPort(runningPods,"test-domain-admin-server"), equalTo(7001));

    // default  is not set
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server1"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-server2"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server1"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-managed-server2"),
        equalTo("HTTPS"));
    assertThat(getContainerReadinessScheme(runningPods,"test-domain-admin-server"),
        equalTo(null));

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
                .type("NodePort")
                .addPortsItem(new V1ServicePort().nodePort(30701)));
  }

  private Stream<V1Service> getServerServices() {
    return getRunningServices().stream().filter(ServiceHelper::isServerService);
  }

  private boolean isHeadlessService(String serverName) {
    return getServerService(serverName)
        .map(V1Service::getSpec)
        .map(this::isHeadless)
        .orElse(false);
  }

  private boolean isClusterIPService(String serverName) {
    return getServerService(serverName)
        .map(V1Service::getSpec)
        .map(this::isClusterIP)
        .orElse(false);
  }

  private Optional<V1Service> getServerService(String serverName) {
    return getRunningServices().stream()
        .filter(ServiceHelper::isServerService)
        .filter(s -> ServiceHelper.getServerName(s).equals(serverName))
        .findFirst();
  }

  private boolean isHeadless(V1ServiceSpec serviceSpec) {
    return "ClusterIP".equals(serviceSpec.getType())
        && "None".equals(serviceSpec.getClusterIP());
  }

  private boolean isClusterIP(V1ServiceSpec serviceSpec) {
    return "ClusterIP".equals(serviceSpec.getType())
        && serviceSpec.getClusterIP() == null;
  }

  private List<V1Service> getRunningServices() {
    return testSupport.getResources(KubernetesTestSupport.SERVICE);
  }

  private List<V1Pod> getRunningPods() {
    return testSupport.getResources(KubernetesTestSupport.POD);
  }

  private List<V1PodDisruptionBudget> getRunningPDBs() {
    return testSupport.getResources(KubernetesTestSupport.PODDISRUPTIONBUDGET);
  }

  private void defineServerResources(String serverName) {
    defineServerResources(serverName, CLUSTER);
  }

  @SuppressWarnings("SameParameterValue")
  private void defineServerResources(String serverName, String clusterName) {
    testSupport.defineResources(createServerPod(serverName, clusterName), createServerService(serverName, clusterName));
  }

  /**/
  private V1Pod createServerPod(String serverName, String clusterName) {
    Packet packet = new Packet().with(processorDelegate).with(originalInfo);
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

  private V1Service createServerService(String serverName) {
    return createServerService(serverName, null);
  }

  private V1Service createServerService(String serverName, String clusterName) {
    V1Service service = new V1Service()
        .metadata(
            withServerAndClusterLabels(
                serverName, clusterName, new V1ObjectMeta()
                    .name(LegalNames.toServerServiceName(DomainProcessorTestSetup.UID, serverName))
                    .namespace(NS)
            ));

    return AnnotationHelper.withSha256Hash(service);
  }

  private V1ObjectMeta withServerAndClusterLabels(String serverName, String clusterName, V1ObjectMeta meta) {
    final V1ObjectMeta objectMeta = KubernetesUtils.withOperatorLabels(UID, meta);
    if (!OperatorUtils.isNullOrEmpty(clusterName)) {
      objectMeta.putLabelsItem(CLUSTERNAME_LABEL, clusterName);
    }
    return objectMeta.putLabelsItem(SERVERNAME_LABEL, serverName);
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

    processor.createMakeRightOperation(originalInfo)
        .withEventData(new EventData(DOMAIN_CHANGED)).withExplicitRecheck().execute();

    assertServerPodAndServiceNotPresent(originalInfo, ADMIN_NAME);
    for (String serverName : MANAGED_SERVER_NAMES) {
      assertServerPodAndServiceNotPresent(originalInfo, serverName);
    }
  }

  private void assertServerPodAndServiceNotPresent(DomainPresenceInfo info, String serverName) {
    assertThat(serverName + " server service", info.getServerService(serverName), nullValue());
    assertThat(serverName + " pod", isServerInactive(info, serverName), is(Boolean.TRUE));
  }

  @Test
  void whenDomainIsNotValid_updateStatus() {
    defineDuplicateServerNames();

    processor.createMakeRightOperation(originalInfo)
        .withEventData(new EventData(DOMAIN_CHANGED)).withExplicitRecheck().execute();

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("DomainInvalid"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("managedServers", "ms1"));
  }
  
  private String getStatusReason(DomainResource updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(DomainResource::getStatus).map(DomainStatus::getReason).orElse(null);
  }

  private String getStatusMessage(DomainResource updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(DomainResource::getStatus).map(DomainStatus::getMessage).orElse(null);
  }

  private String getStateGoal(DomainResource domain, String serverName) {
    return Optional.ofNullable(getServerStatus(domain, serverName)).map(ServerStatus::getStateGoal).orElse("");
  }

  private ServerStatus getServerStatus(DomainResource domain, String serverName) {
    for (ServerStatus status : domain.getStatus().getServers()) {
      if (status.getServerName().equals(serverName)) {
        return status;
      }
    }

    return null;
  }

  private Integer getContainerReadinessPort(List<V1Pod> pods, String podName) {
    return pods.stream()
          .filter(pod -> isNamedPod(pod, podName))
          .findFirst()
          .map(V1Pod::getSpec)
          .map(V1PodSpec::getContainers)
          .flatMap(c -> c.stream().findFirst())
          .map(V1Container::getReadinessProbe)
          .map(V1Probe::getHttpGet)
          .map(V1HTTPGetAction::getPort)
          .map(IntOrString::getIntValue)
          .orElse(null);
  }

  private boolean isNamedPod(V1Pod pod, String name) {
    return Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).stream().anyMatch(name::equals);
  }

  private String getContainerReadinessScheme(List<V1Pod> pods, String podName) {
    return pods.stream()
          .filter(pod -> isNamedPod(pod, podName))
          .findFirst()
          .map(V1Pod::getSpec)
          .map(V1PodSpec::getContainers)
          .flatMap(c -> c.stream().findFirst())
          .map(V1Container::getReadinessProbe)
          .map(V1Probe::getHttpGet)
          .map(V1HTTPGetAction::getScheme)
          .orElse(null);
  }

  private void defineDuplicateServerNames() {
    newDomain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    newDomain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
  }

  @Test
  void whenWebLogicCredentialsSecretRemoved_NullPointerExceptionAndAbortedEventNotGenerated() {
    consoleHandlerMemento.ignoreMessage(NOT_STARTING_DOMAINUID_THREAD);
    processor.registerDomainPresenceInfo(originalInfo);
    domain.getSpec().withWebLogicCredentialsSecret(null);
    long time = 0;

    for (int numRetries = 0; numRetries < 5; numRetries++) {
      processor.createMakeRightOperation(originalInfo).withExplicitRecheck().execute();
      time += domain.getFailureRetryIntervalSeconds();
      testSupport.setTime(time, TimeUnit.SECONDS);
    }

    assertThat(getEvents().stream().anyMatch(EventTestUtils::isDomainFailedAbortedEvent), is(false));
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  @Test
  void whenClusterResourceAdded_verifyDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER).withLogLevel(INFO);
    configureDomain(domain).configureCluster(originalInfo, CLUSTER);
    processor.registerDomainPresenceInfo(originalInfo);
    final Response<ClusterResource> item = new Response<>("ADDED", createClusterResource(NS, CLUSTER));

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, containsInfo(WATCH_CLUSTER));
  }

  @Test
  void whenClusterResourceAdded_noDomainPresenceInfoExists_dontDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER).withLogLevel(INFO);
    final Response<ClusterResource> item = new Response<>("ADDED", createClusterResource(NS, CLUSTER));

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, not(containsInfo(WATCH_CLUSTER)));
  }

  @Test
  void whenClusterResourceAdded_listClusterResources() {
    processor.registerDomainPresenceInfo(originalInfo);
    addClustersAndDispatchClusterWatch();

    assertThat(originalInfo.getClusterResource(CLUSTER), notNullValue());
    assertThat(originalInfo.getClusterResource(CLUSTER2), notNullValue());
    assertThat(originalInfo.getClusterResource(CLUSTER3), notNullValue());
  }

  private void addClustersAndDispatchClusterWatch() {
    for (String clusterName : List.of(CLUSTER, CLUSTER2, CLUSTER3)) {
      configureDomain(domain).configureCluster(originalInfo, clusterName);
      testSupport.defineResources(createClusterResource(NS, clusterName));
    }
    final Response<ClusterResource> item = new Response<>("ADDED", testSupport
        .<ClusterResource>getResourceWithName(KubernetesTestSupport.CLUSTER, CLUSTER3));

    processor.dispatchClusterWatch(item);
  }

  @Test
  void whenDomainAndClusterResourcesAddedAtSameTime_introspectorJobHasCorrectOwnerReference() {
    consoleHandlerMemento.ignoringLoggedExceptions(ApiException.class);
    domain.getOrCreateStatus().addCondition(new DomainCondition(AVAILABLE).withStatus(false));
    setupNewDomainResource(NEW_DOMAIN_UID);
    processor.registerDomainPresenceInfo(originalInfo);
    addClustersAndDispatchClusterWatch();

    assertThat(getIntrospectorJobOwnerReferenceUid(), equalTo(NEW_DOMAIN_UID));
  }

  private void setupNewDomainResource(String newUid) {
    testSupport.deleteResources(domain);
    testSupport.defineResources(newDomain.withMetadata(newDomain.getMetadata().uid(newUid)));
    testSupport.failOnDelete(JOB, getJobName(), NS, HTTP_BAD_REQUEST);
  }

  private String getIntrospectorJobOwnerReferenceUid() {
    return Optional.ofNullable((V1Job) testSupport.getResourceWithName(JOB, getJobName())).map(V1Job::getMetadata)
        .map(m -> m.getOwnerReferences().stream().findFirst().orElse(null).getUid()).orElse(null);
  }

  @Test
  void whenClusterResourceModified_verifyDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER).withLogLevel(Level.FINE);
    processor.registerDomainPresenceInfo(originalInfo);
    ClusterResource clusterResource1 = createClusterResource(NS, CLUSTER);
    configureDomain(domain).configureCluster(originalInfo, clusterResource1.getClusterName());
    testSupport.defineResources(clusterResource1);
    originalInfo.addClusterResource(clusterResource1);
    ClusterResource clusterResource2 = createClusterResource(NS, CLUSTER);
    clusterResource2.getMetadata().generation(2L);
    final Response<ClusterResource> item = new Response<>("MODIFIED", clusterResource2);

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, containsFine(WATCH_CLUSTER));
  }

  @Test
  void whenClusterResourceModified_noDomainPresenceInfoExists_dontDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER).withLogLevel(Level.FINE);
    processor.registerDomainPresenceInfo(originalInfo);
    final Response<ClusterResource> item = new Response<>("MODIFIED", createClusterResource(NS, CLUSTER));

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, not(containsInfo(WATCH_CLUSTER)));
  }

  @Test
  void whenClusterResourceModified_noGenerationChange_dontDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER).withLogLevel(Level.FINE);
    processor.registerDomainPresenceInfo(originalInfo);
    ClusterResource clusterResource1 = createClusterResource(NS, CLUSTER);
    originalInfo.addClusterResource(clusterResource1);
    final Response<ClusterResource> item = new Response<>("MODIFIED", clusterResource1);

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, not(containsFine(WATCH_CLUSTER)));
  }

  @Test
  void whenClusterResourceModified_generationChanged_verifyDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER).withLogLevel(Level.FINE);
    processor.registerDomainPresenceInfo(originalInfo);
    ClusterResource clusterResource1 = createClusterResource(NS, CLUSTER);
    configureDomain(domain).configureCluster(originalInfo, clusterResource1.getClusterName());
    testSupport.defineResources(clusterResource1);
    originalInfo.addClusterResource(clusterResource1);
    ClusterResource clusterResource2 = createClusterResource(NS, CLUSTER);
    clusterResource2.getMetadata().generation(2L);
    final Response<ClusterResource> item = new Response<>("MODIFIED", clusterResource2);

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, containsFine(WATCH_CLUSTER));
  }

  @Test
  void whenClusterResourceDeleted_verifyDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER_DELETED).withLogLevel(INFO);
    configureDomain(domain).configureCluster(originalInfo, CLUSTER);
    processor.registerDomainPresenceInfo(originalInfo);
    final Response<ClusterResource> item = new Response<>("DELETED", createClusterResource(NS, CLUSTER));

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, containsInfo(WATCH_CLUSTER_DELETED));
  }

  @Test
  void whenClusterResourceDeleted_noDomainPresenceInfoExists_dontDispatch() {
    consoleHandlerMemento.collectLogMessages(logRecords, WATCH_CLUSTER_DELETED).withLogLevel(INFO);
    final Response<ClusterResource> item = new Response<>("DELETED", createClusterResource(NS, CLUSTER));

    processor.dispatchClusterWatch(item);

    assertThat(logRecords, not(containsInfo(WATCH_CLUSTER_DELETED)));
  }

  @Test
  void verifyClusterResourceDeleted() {
    DomainConfigurator configurator = configureDomain(domain);
    configurator.configureCluster(originalInfo, CLUSTER);
    configurator.configureCluster(originalInfo, CLUSTER2);
    processor.registerDomainPresenceInfo(originalInfo);
    testSupport.defineResources(createClusterResource(NS, CLUSTER2));
    final Response<ClusterResource> item = new Response<>("DELETED", createClusterResource(NS, CLUSTER));

    processor.dispatchClusterWatch(item);

    assertThat(originalInfo.getClusterResource(CLUSTER), nullValue());
    assertThat(originalInfo.getClusterResource(CLUSTER2), notNullValue());
  }

  private ClusterResource createClusterResource(String namespace, String clusterName) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(clusterName).generation(1L))
        .spec(new ClusterSpec().withClusterName(clusterName));
  }
}
