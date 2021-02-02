// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.CoreV1Event;
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
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_VALIDATION_ERROR_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_VALIDATION_ERROR_PATTERN;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithMessage;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.ServiceHelperTestBase.NS;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
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
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;
  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("mydomain");
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  private Step domainValidationSteps;
  private Step topologyValidationStep;

  @BeforeEach
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, DOMAIN_VALIDATION_FAILED,
        NO_CLUSTER_IN_DOMAIN, NO_MANAGED_SERVER_IN_DOMAIN);
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
  public void whenClusterDoesNotExistInDomain_logWarning() {
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(topologyValidationStep);

    assertThat(logRecords, containsWarning(MessageKeys.NO_CLUSTER_IN_DOMAIN));
    assertThat(info.getValidationWarningsAsString(),
        stringContainsInOrder("Cluster", "no-such-cluster", "does not exist"));
  }

  @Test
  public void whenServerDoesNotExistInDomain_logWarning() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(topologyValidationStep);

    assertThat(logRecords, containsWarning(MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN));
    assertThat(info.getValidationWarningsAsString(),
        stringContainsInOrder("Managed Server", "no-such-server", "does not exist"));
  }

  @Test
  public void whenServerDoesNotExistInDomain_createEvent() {
    consoleControl.ignoreMessage(NO_MANAGED_SERVER_IN_DOMAIN);
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(topologyValidationStep);

    assertContainsEventWithMessage(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server");
  }

  @Test
  public void whenClusterDoesNotExistInDomain_createEvent() {
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(topologyValidationStep);

    assertContainsEventWithMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster");
  }

  @Test
  public void whenBothServerAndClusterDoNotExistInDomain_createEventWithBothWarnings() {
    consoleControl.ignoreMessage(NO_MANAGED_SERVER_IN_DOMAIN);
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

    testSupport.runSteps(topologyValidationStep);

    assertContainsEventWithFormattedMessage(getFormattedMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster")
        + "\n" + getFormattedMessage(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server"));
  }

  @Test
  public void whenIsExplictRecheck_doNotCreateEvent() {
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    setExplicitRecheck();
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());

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
