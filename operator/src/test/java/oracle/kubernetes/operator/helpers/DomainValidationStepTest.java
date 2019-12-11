// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
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
  private DomainValidationStep step = new DomainValidationStep(terminalStep);
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;

  @Before
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, DOMAIN_VALIDATION_FAILED);
    mementos.add(consoleControl);
    mementos.add(testSupport.install());

    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(info);
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void stepImplementsStepClass() {
    assertThat(step, instanceOf(Step.class));
  }

  @Test
  public void whenDomainIsValid_runNextStep() {
    testSupport.runStepsToCompletion(step);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenDomainIsNotValid_dontRunNextStep() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    defineDuplicateServerNames();

    testSupport.runStepsToCompletion(step);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenDomainIsNotValid_updateStatus() {
    consoleControl.ignoreMessage(DOMAIN_VALIDATION_FAILED);
    defineDuplicateServerNames();

    testSupport.runStepsToCompletion(step);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("ErrBadDomain"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("managedServers", "ms1"));
  }

  @Test
  public void whenDomainIsNotValid_logSevereMessage() {
    defineDuplicateServerNames();

    testSupport.runStepsToCompletion(step);

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
}
