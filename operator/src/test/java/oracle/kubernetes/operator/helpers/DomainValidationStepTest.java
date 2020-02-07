// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.ServiceHelperTestBase.NS;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
import static oracle.kubernetes.utils.LogMatcher.containsSevere;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainValidationStepTest {
  private Domain domain = DomainProcessorTestSetup.createTestDomain();
  private DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private TerminalStep terminalStep = new TerminalStep();
  private Step domainValidationSteps;
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, DOMAIN_VALIDATION_FAILED);
    mementos.add(consoleControl);
    mementos.add(testSupport.install());

    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(info);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    domainValidationSteps = DomainValidationSteps.createDomainValidationSteps(NS, terminalStep);
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void stepImplementsStepClass() {
    assertThat(domainValidationSteps, instanceOf(Step.class));
  }

  @Test
  public void whenDomainIsValid_runNextStep() {
    testSupport.runStepsToCompletion(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainIsNotValid_dontRunNextStep() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    defineDuplicateServerNames();

    testSupport.runStepsToCompletion(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenDomainIsNotValid_updateStatus() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    defineDuplicateServerNames();

    testSupport.runStepsToCompletion(domainValidationSteps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("ErrBadDomain"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("managedServers", "ms1"));
  }

  @Test
  public void whenDomainIsNotValid_logSevereMessage() {
    defineDuplicateServerNames();

    testSupport.runStepsToCompletion(domainValidationSteps);

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

    testSupport.runStepsToCompletion(domainValidationSteps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("ErrBadDomain"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("name", "not found", NS));
  }

  @Test
  public void whenDomainRefersToUnknownSecret_dontRunNextStep() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    domain.getSpec().withWebLogicCredentialsSecret(new V1SecretReference().name("name").namespace("ns"));

    testSupport.runStepsToCompletion(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenDomainRefersToDefinedSecret_runNextStep() {
    domain.getSpec().withWebLogicCredentialsSecret(new V1SecretReference().name("name"));
    testSupport.defineResources(new V1Secret().metadata(new V1ObjectMeta().name("name").namespace(NS)));

    testSupport.runStepsToCompletion(domainValidationSteps);

    assertThat(terminalStep.wasRun(), is(true));
  }
}
