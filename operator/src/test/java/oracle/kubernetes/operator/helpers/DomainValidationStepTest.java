// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainFailureSeverity;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestCluster;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.setupCluster;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.ServiceHelperTestBase.NS;
import static oracle.kubernetes.operator.tuning.TuningParameters.DEFAULT_CALL_LIMIT;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.DomainValidationTest.CLUSTER_1;
import static oracle.kubernetes.weblogic.domain.model.DomainValidationTest.CLUSTER_2;
import static oracle.kubernetes.weblogic.domain.model.DomainValidationTest.CLUSTER_3;
import static oracle.kubernetes.weblogic.domain.model.DomainValidationTest.UID2;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainValidationStepTest {
  /** More than one chunk's worth of secrets or configmaps. */
  private static final int MULTI_CHUNKS_FIRST_NUM_IN_SECOND_CHUNK = DEFAULT_CALL_LIMIT + 1;
  private static final int MULTI_CHUNKS_MIDDLE_NUM_IN_FIRST_CHUNK = DEFAULT_CALL_LIMIT / 2;
  private static final int MULTI_CHUNKS_LAST_NUM = DEFAULT_CALL_LIMIT * 2 + 1;

  private static final String SECRETS = "secrets";
  private static final String CONFIGMAPS = "configmaps";

  private static final String TEST_SECRET_PREFIX = "TEST_SECRET";
  private static final String TEST_CONFIGMAP_PREFIX = "TEST_CM";

  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;

  private final Map<String, Map<String, DomainPresenceInfo>> domains = new ConcurrentHashMap<>();
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  private Step domainValidationSteps;

  @BeforeEach
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords);
    mementos.add(consoleControl);
    mementos.add(testSupport.install());

    domains.put(NS, new HashMap<>());
    domains.get(NS).put(UID, info);
    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(info);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    domainValidationSteps = Step.chain(DomainValidationSteps.createDomainValidationSteps(NS), terminalStep);
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domains", domains));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void stepImplementsStepClass() {
    assertThat(domainValidationSteps, instanceOf(Step.class));
  }

  @Test
  void whenDomainIsValid_runNextStep() {
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenDomainIsValid_removeOldDomainInvalidCondition() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(DOMAIN_INVALID).withMessage("Placeholder"));
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(INTROSPECTION).withMessage("Placeholder"));

    testSupport.runSteps(domainValidationSteps);

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain, hasCondition(FAILED).withReason(INTROSPECTION));
    assertThat(updatedDomain, not(hasCondition(FAILED).withReason(DOMAIN_INVALID)));
  }

  @Test
  void whenDomainIsNotValid_dontRunNextStep() {
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenDomainIsNotValid_updateStatus() {
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("DomainInvalid"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("managedServers", "ms1"));
  }

  @Test
  void whenDomainIsNotValid_removePreviousDomainInvalidCondition() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(DOMAIN_INVALID).withMessage("obsolete"));
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain, not(hasCondition(FAILED).withReason(DOMAIN_INVALID).withMessageContaining("obsolete")));
  }

  @Test
  void whenDomainIsNotValid_logSevereMessage() {
    consoleControl.trackMessage(DOMAIN_VALIDATION_FAILED);
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    assertThat(logRecords, containsSevere(DOMAIN_VALIDATION_FAILED));
  }

  @Test
  void whenDomainIsNotValid_generateEvent() {
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    assertThat(testSupport,
        hasEvent(DOMAIN_FAILED_EVENT).withMessageContaining(getLocalizedString(DOMAIN_INVALID_EVENT_ERROR)));
  }

  private String getStatusReason(DomainResource updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(DomainResource::getStatus).map(DomainStatus::getReason).orElse(null);
  }

  private String getStatusMessage(DomainResource updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(DomainResource::getStatus).map(DomainStatus::getMessage).orElse(null);
  }

  private void defineDuplicateServerNames() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
  }

  @Test
  void whenDomainHasFatalValidationErrors_reportFatalFailedCondition() {
    defineDuplicateServerNames();

    testSupport.runSteps(domainValidationSteps);

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("DomainInvalid"));
    assertThat(getFailedConditionSeverity(updatedDomain), equalTo(DomainFailureSeverity.FATAL));
  }

  private DomainFailureSeverity getFailedConditionSeverity(DomainResource updatedDomain) {
    return getConditions(updatedDomain).filter(this::isFailedCondition).findFirst().map(DomainCondition::getSeverity)
        .orElse(null);
  }

  @Nonnull
  private Stream<DomainCondition> getConditions(DomainResource updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(DomainResource::getStatus).map(DomainStatus::getConditions)
        .orElse(Collections.emptyList()).stream();
  }

  private boolean isFailedCondition(DomainCondition condition) {
    return condition.getType().equals(FAILED);
  }

  @Test
  void whenDomainRefersToUnknownSecret_updateStatus() {
    domain.getSpec().withWebLogicCredentialsSecret(new V1LocalObjectReference().name("name"));

    testSupport.runSteps(domainValidationSteps);

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("DomainInvalid"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("name", "not found", NS));
  }

  @Test
  void whenDomainRefersToUnknownSecret_dontRunNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(new V1LocalObjectReference().name("name"));

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenDomainRefersToDefinedSecret_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(new V1LocalObjectReference().name("name"));
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenDomainValidationStepsCalled_withSecretInMultiChunks_packetContainsAllSecrets() {
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(getNumMatchingSecrets(), is((long) MULTI_CHUNKS_LAST_NUM));
  }

  private long getNumMatchingSecrets() {
    return Optional.ofNullable(testSupport.getPacket().<List<V1Secret>>getValue(SECRETS))
          .orElse(Collections.emptyList())
          .stream()
          .filter(this::matchesExpectedSecretNamePattern)
          .count();
  }

  private boolean matchesExpectedSecretNamePattern(V1Secret secret) {
    return Optional.of(secret).map(V1Secret::getMetadata).map(V1ObjectMeta::getName).orElse("")
        .startsWith(TEST_SECRET_PREFIX);
  }

  @Test
  void whenDomainRefersToDefinedSecretInMiddleChunk_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(
            new V1LocalObjectReference().name(TEST_SECRET_PREFIX + MULTI_CHUNKS_FIRST_NUM_IN_SECOND_CHUNK));
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenDomainRefersToDefinedSecretInFirstChunk_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(
        new V1LocalObjectReference().name(TEST_SECRET_PREFIX + MULTI_CHUNKS_MIDDLE_NUM_IN_FIRST_CHUNK));
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenDomainRefersToDefinedSecretInLastChunk_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(
        new V1LocalObjectReference().name(TEST_SECRET_PREFIX + MULTI_CHUNKS_LAST_NUM));
    createSecrets(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @SuppressWarnings("SameParameterValue")
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
  void whenDomainValidationStepsCalled_withConfigMapInMultiChunks_packetContainsAllConfigMaps() {
    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(getNumMatchingConfigMaps(), equalTo((long) MULTI_CHUNKS_LAST_NUM));
  }

  private long getNumMatchingConfigMaps() {
    return Optional.ofNullable(testSupport.getPacket().<List<V1ConfigMap>>getValue(CONFIGMAPS))
          .orElse(Collections.emptyList())
          .stream()
          .filter(this::matchesExpectedConfigMapNamePattern)
          .count();
  }

  private boolean matchesExpectedConfigMapNamePattern(V1ConfigMap cm) {
    return Optional.of(cm).map(V1ConfigMap::getMetadata).map(V1ObjectMeta::getName).orElse("")
        .startsWith(TEST_CONFIGMAP_PREFIX);
  }

  @Test
  void whenDomainRefersToDefinedConfigMapInMiddleChunk_runNextStep() {
    domain.getSpec()
        .withWebLogicCredentialsSecret(new V1LocalObjectReference().name("name"))
        .setConfiguration(new Configuration().withModel(
            new Model().withConfigMap(TEST_CONFIGMAP_PREFIX + MULTI_CHUNKS_FIRST_NUM_IN_SECOND_CHUNK)
                .withRuntimeEncryptionSecret("name")));

    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenDomainRefersToDefinedConfigMapInFirstChunk_runNextStep() {
    domain.getSpec()
        .withWebLogicCredentialsSecret(new V1LocalObjectReference().name("name"))
        .setConfiguration(new Configuration().withModel(
            new Model().withConfigMap(TEST_CONFIGMAP_PREFIX + MULTI_CHUNKS_MIDDLE_NUM_IN_FIRST_CHUNK)
                .withRuntimeEncryptionSecret("name")));

    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenDomainRefersToDefinedConfigMapInLastChunk_runNextStep() {
    domain.getSpec()
        .withWebLogicCredentialsSecret(new V1LocalObjectReference().name("name"))
        .setConfiguration(new Configuration().withModel(
            new Model().withConfigMap(TEST_CONFIGMAP_PREFIX + MULTI_CHUNKS_LAST_NUM)
                .withRuntimeEncryptionSecret("name")));

    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    createConfigMaps(MULTI_CHUNKS_LAST_NUM);
    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenTwoDomainsHaveOverlapClusterResourceReferences_dontRunNextStep() {
    setUpTwoDomainsWithOverlapClusterReferences(false);

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(false));
  }


  @Test
  void whenTwoDomainsHaveOverlapClusterResourceReferencesButOneInfoHasNullDomainObject_runNextStep() {
    setUpTwoDomainsWithOverlapClusterReferences(true);

    testSupport.runSteps(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  private void setUpTwoDomainsWithOverlapClusterReferences(boolean withNoDomain) {
    String wlsClusterName1 = "c1";
    String wlsClusterName2 = "c2";
    String wlsClusterName3 = "c3";
    DomainResource domain2 = createTestDomain(UID2);
    DomainPresenceInfo info = new DomainPresenceInfo(domain2);
    if (withNoDomain) {
      info.setDomain(null);
    }
    domains.get(NS).put(UID2, info);
    ClusterResource cluster1 = createTestCluster(CLUSTER_1);
    cluster1.getSpec().setClusterName(wlsClusterName1);
    ClusterResource cluster2 = createTestCluster(CLUSTER_2);
    cluster2.getSpec().setClusterName(wlsClusterName2);
    ClusterResource cluster3 = createTestCluster(CLUSTER_3);
    cluster3.getSpec().setClusterName(wlsClusterName3);
    testSupport.defineResources(domain2, cluster1, cluster2, cluster3);

    setupCluster(domain, new ClusterResource[] {cluster1, cluster2});
    setupCluster(domain2, new ClusterResource[] {cluster2, cluster3});
  }


  @SuppressWarnings("SameParameterValue")
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
}
