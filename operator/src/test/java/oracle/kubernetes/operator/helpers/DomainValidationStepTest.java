// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_VALIDATION_ERROR_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_VALIDATION_ERROR_PATTERN;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithMessage;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.TuningParametersImpl.DEFAULT_CALL_LIMIT;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.ServiceHelperTestBase.NS;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER;
import static oracle.kubernetes.operator.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_SERVER;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_CLUSTER_IN_DOMAIN;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN;
import static oracle.kubernetes.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainValidationStepTest {
  /** More than one chunk's worth of secrets or configmaps. */
  private static final int MULTI_CHUNKS_FIRST_NUM_IN_SECOND_CHUNK = DEFAULT_CALL_LIMIT + 1;
  private static final int MULTI_CHUNKS_MIDDLE_NUM_IN_FIRST_CHUNK = DEFAULT_CALL_LIMIT / 2;
  private static final int MULTI_CHUNKS_LAST_NUM = DEFAULT_CALL_LIMIT * 2 + 1;

  private static final String SECRETS = "secrets";
  private static final String CONFIGMAPS = "configmaps";

  private static final String TEST_SECRET_PREFIX = "TEST_SECRET";
  private static final String TEST_CONFIGMAP_PREFIX = "TEST_CM";

  private static final String ADMIN_SERVER = "admin-server";
  private static final String MANAGED_SERVER1 = "managed-server1";
  private static final String DYNAMIC_CLUSTER_NAME = "dyn-cluster-1";
  private static final String SERVER_TEMPLATE_NAME = "server-template";
  private static final String DOMAIN_NAME = "domain";
  private static final int ADMIN_SERVER_PORT_NUM = 7001;
  private static final int MANAGED_SERVER1_PORT_NUM = 8001;
  private static final int SERVER_TEMPLATE_PORT_NUM = 9001;

  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;
  private final WlsDomainConfig domainConfig =
      new WlsDomainConfigSupport(DOMAIN_NAME)
          .withAdminServerName(ADMIN_SERVER)
          .withWlsServer(ADMIN_SERVER, ADMIN_SERVER_PORT_NUM)
          .withWlsServer(MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM)
          .withDynamicWlsCluster(DYNAMIC_CLUSTER_NAME, SERVER_TEMPLATE_NAME, SERVER_TEMPLATE_PORT_NUM)
          .createDomainConfig();

  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  private Step domainValidationSteps;
  private Step topologyValidationStep;

  @BeforeEach
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, DOMAIN_VALIDATION_FAILED,
        NO_CLUSTER_IN_DOMAIN, NO_MANAGED_SERVER_IN_DOMAIN,
        MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER, MONITORING_EXPORTER_CONFLICT_SERVER);
    mementos.add(consoleControl);
    mementos.add(testSupport.install());

    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(info);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    domainValidationSteps = DomainValidationSteps.createDomainValidationSteps(NS, terminalStep);
    topologyValidationStep = DomainValidationSteps.createValidateDomainTopologyStep(terminalStep);
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void stepImplementsStepClass() {
    assertThat(domainValidationSteps, instanceOf(Step.class));
  }

  @Test
  public void whenDomainIsValid_runNextStep() {
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainIsNotValid_dontRunNextStep() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenDomainIsNotValid_updateStatus() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("ErrBadDomain"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("managedServers", "ms1"));
  }

  @Test
  public void whenDomainIsNotValid_logSevereMessage() {
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    assertThat(logRecords, containsSevere(DOMAIN_VALIDATION_FAILED));
  }

  private String getStatusReason(Domain updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(Domain::getStatus).map(DomainStatus::getReason).orElse(null);
  }

  private String getStatusMessage(Domain updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(Domain::getStatus).map(DomainStatus::getMessage).orElse(null);
  }

  private void defineDuplicateServerNames() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
  }

  @Test
  public void whenDomainRefersToUnknownSecret_updateStatus() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    domain.getSpec().withWebLogicCredentialsSecret(new V1SecretReference().name("name").namespace("ns"));

    testSupport.runSteps(domainValidationSteps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("ErrBadDomain"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("name", "not found", NS));
  }

  @Test
  public void whenDomainRefersToUnknownSecret_dontRunNextStep() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    domain.getSpec().withWebLogicCredentialsSecret(new V1SecretReference().name("name").namespace("ns"));

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenDomainRefersToDefinedSecret_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(new V1SecretReference().name("name"));
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainValidationStepsCalled_withSecretInMultiChunks_packetContainsAllSecrets() {
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(getMatchingSecretsCount(), is(MULTI_CHUNKS_LAST_NUM));
  }

  private int getMatchingSecretsCount() {
    List<V1Secret> list =
        (List<V1Secret>) Optional.ofNullable(testSupport.getPacket().get(SECRETS)).orElse(Collections.EMPTY_LIST);
    return Math.toIntExact(list.stream().filter(secret -> matchesExpectedSecretNamePattern(secret)).count());
  }

  private boolean matchesExpectedSecretNamePattern(V1Secret secret) {
    return Optional.of(secret).map(V1Secret::getMetadata).map(V1ObjectMeta::getName).orElse("")
        .startsWith(TEST_SECRET_PREFIX);
  }

  @Test
  public void whenDomainRefersToDefinedSecretInMiddleChunk_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(
            new V1SecretReference().name(TEST_SECRET_PREFIX + MULTI_CHUNKS_FIRST_NUM_IN_SECOND_CHUNK).namespace(NS));
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainRefersToDefinedSecretInFirstChunk_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(
        new V1SecretReference().name(TEST_SECRET_PREFIX + MULTI_CHUNKS_MIDDLE_NUM_IN_FIRST_CHUNK).namespace(NS));
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainRefersToDefinedSecretInLastChunk_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(
        new V1SecretReference().name(TEST_SECRET_PREFIX + MULTI_CHUNKS_LAST_NUM).namespace(NS));
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  private void createSecrets(int lastSecretNum) {
    IntStream.rangeClosed(1, lastSecretNum)
        .boxed()
        .map(i -> TEST_SECRET_PREFIX + i)
        .map(this::createSecret)
        .forEach(testSupport::defineResources);
  }

  private V1Secret createSecret(String secret) {
    return new V1Secret().metadata(new V1ObjectMeta().name(secret).namespace(NS));
  }

  @Test
  public void whenDomainValidationStepsCalled_withConfigMapInMultiChunks_packetContainsAllConfigMaps() {
    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(getMatchingConfigMapsCount(), is(MULTI_CHUNKS_LAST_NUM));
  }

  private int getMatchingConfigMapsCount() {
    List<V1ConfigMap> list =
        (List<V1ConfigMap>) Optional.ofNullable(testSupport.getPacket().get(CONFIGMAPS)).orElse(Collections.EMPTY_LIST);
    return Math.toIntExact(list.stream().filter(cm -> matchesExpectedConfigMapNamePattern(cm)).count());
  }

  private boolean matchesExpectedConfigMapNamePattern(V1ConfigMap cm) {
    return Optional.of(cm).map(V1ConfigMap::getMetadata).map(V1ObjectMeta::getName).orElse("")
        .startsWith(TEST_CONFIGMAP_PREFIX);
  }

  @Test
  public void whenDomainRefersToDefinedConfigMapInMiddleChunk_runNextStep() {
    domain.getSpec()
        .withWebLogicCredentialsSecret(new V1SecretReference().name("name"))
        .setConfiguration(new Configuration().withModel(
            new Model().withConfigMap(TEST_CONFIGMAP_PREFIX + MULTI_CHUNKS_FIRST_NUM_IN_SECOND_CHUNK)
                .withRuntimeEncryptionSecret("name")));

    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainRefersToDefinedConfigMapInFirstChunk_runNextStep() {
    domain.getSpec()
        .withWebLogicCredentialsSecret(new V1SecretReference().name("name"))
        .setConfiguration(new Configuration().withModel(
            new Model().withConfigMap(TEST_CONFIGMAP_PREFIX + MULTI_CHUNKS_MIDDLE_NUM_IN_FIRST_CHUNK)
                .withRuntimeEncryptionSecret("name")));

    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainRefersToDefinedConfigMapInLastChunk_runNextStep() {
    domain.getSpec()
        .withWebLogicCredentialsSecret(new V1SecretReference().name("name"))
        .setConfiguration(new Configuration().withModel(
            new Model().withConfigMap(TEST_CONFIGMAP_PREFIX + MULTI_CHUNKS_LAST_NUM)
                .withRuntimeEncryptionSecret("name")));

    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  private void createConfigMaps(int lastConfigMapNum) {
    IntStream.rangeClosed(1, lastConfigMapNum)
        .boxed()
        .map(i -> TEST_CONFIGMAP_PREFIX + i)
        .map(this::createConfigMap)
        .forEach(testSupport::defineResources);
  }

  private V1ConfigMap createConfigMap(String cm) {
    return new V1ConfigMap().metadata(new V1ObjectMeta().name(cm).namespace(NS));
  }

  @Test
  public void whenClusterDoesNotExistInDomain_logWarning() {
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertThat(logRecords, containsWarning(MessageKeys.NO_CLUSTER_IN_DOMAIN));
    assertThat(info.getValidationWarningsAsString(),
        stringContainsInOrder("Cluster", "no-such-cluster", "does not exist"));
  }

  @Test
  public void whenServerDoesNotExistInDomain_logWarning() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertThat(logRecords, containsWarning(MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN));
    assertThat(info.getValidationWarningsAsString(),
        stringContainsInOrder("Managed Server", "no-such-server", "does not exist"));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  @Test
  public void whenMonitoringExporterPortConflictsWithAdminServerPort_logWarningAndGenerateEvent() {
    configureDomain(domain).withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(7001);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertThat(logRecords, containsWarning(MessageKeys.MONITORING_EXPORTER_CONFLICT_SERVER));
    assertThat(info.getValidationWarningsAsString(),
        stringContainsInOrder(Integer.toString(7001), ADMIN_SERVER, Integer.toString(ADMIN_SERVER_PORT_NUM)));

    assertContainsEventWithFormattedMessage(
        getFormattedMessage(MONITORING_EXPORTER_CONFLICT_SERVER,
            Integer.toString(7001), ADMIN_SERVER, Integer.toString(ADMIN_SERVER_PORT_NUM)));
  }

  @Test
  public void whenMonitoringExporterPortConflictsWithManagedServerPort_logWarningAndGenerateEvent() {
    configureDomain(domain).withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(8001);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertThat(logRecords, containsWarning(MessageKeys.MONITORING_EXPORTER_CONFLICT_SERVER));
    assertThat(info.getValidationWarningsAsString(),
        stringContainsInOrder(Integer.toString(8001), MANAGED_SERVER1, Integer.toString(MANAGED_SERVER1_PORT_NUM)));

    assertContainsEventWithFormattedMessage(
        getFormattedMessage(MONITORING_EXPORTER_CONFLICT_SERVER,
            Integer.toString(8001), MANAGED_SERVER1, Integer.toString(MANAGED_SERVER1_PORT_NUM)));
  }

  @Test
  public void whenMonitoringExporterPortConflictsWithClusterServerTemplatePort_logWarningAndGenerateEvent() {
    configureDomain(domain).withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(9001);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertThat(logRecords, containsWarning(MessageKeys.MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER));
    assertThat(info.getValidationWarningsAsString(),
        stringContainsInOrder(Integer.toString(9001), DYNAMIC_CLUSTER_NAME,
            Integer.toString(SERVER_TEMPLATE_PORT_NUM)));

    assertContainsEventWithFormattedMessage(
        getFormattedMessage(MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER,
            Integer.toString(9001), DYNAMIC_CLUSTER_NAME, Integer.toString(SERVER_TEMPLATE_PORT_NUM)));
  }

  @Test
  public void whenServerDoesNotExistInDomain_createEvent() {
    consoleControl.ignoreMessage(NO_MANAGED_SERVER_IN_DOMAIN);
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertContainsEventWithMessage(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server");
  }

  @Test
  public void whenClusterDoesNotExistInDomain_createEvent() {
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertContainsEventWithMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster");
  }

  @Test
  public void whenBothServerAndClusterDoNotExistInDomain_createEventWithBothWarnings() {
    consoleControl.ignoreMessage(NO_MANAGED_SERVER_IN_DOMAIN);
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertContainsEventWithFormattedMessage(getFormattedMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster")
        + "\n" + getFormattedMessage(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server"));
  }

  @Test
  public void whenIsExplicitRecheck_doNotCreateEvent() {
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    setExplicitRecheck();
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);

    testSupport.runSteps(topologyValidationStep);

    assertThat(getEvents(), empty());
  }

  @SuppressWarnings("SameParameterValue")
  private Cluster createCluster(String clusterName) {
    Cluster cluster = new Cluster();
    cluster.setClusterName(clusterName);
    cluster.setReplicas(1);
    cluster.setServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);
    return cluster;
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  private void assertContainsEventWithMessage(String msgId, Object... messageParams) {
    assertContainsEventWithFormattedMessage(getFormattedMessage(msgId, messageParams));
  }

  private void assertContainsEventWithFormattedMessage(String message) {
    assertThat(
        "Expected Event with message was not created: " + getExpectedValidationEventMessage(message)
            + ".\nThere are " + getEvents().size() + " event.\nEvents: " + getEvents(),
        containsEventWithMessage(
            getEvents(),
            DOMAIN_VALIDATION_ERROR_EVENT,
            getExpectedValidationEventMessage(message)),
        is(true));
  }

  private String getFormattedMessage(String msgId, Object... params) {
    LoggingFacade logger = LoggingFactory.getLogger("Operator", "Operator");
    return logger.formatMessage(msgId, params);
  }

  private String getExpectedValidationEventMessage(String message) {
    return String.format(DOMAIN_VALIDATION_ERROR_PATTERN, UID, message);
  }

  private void setExplicitRecheck() {
    testSupport.addToPacket(MAKE_RIGHT_DOMAIN_OPERATION,
        Stub.createStub(ExplicitRecheckMakeRightDomainOperationStub.class));
  }

  abstract static class ExplicitRecheckMakeRightDomainOperationStub implements
      MakeRightDomainOperation {

    @Override
    public boolean isExplicitRecheck() {
      return true;
    }
  }
}
