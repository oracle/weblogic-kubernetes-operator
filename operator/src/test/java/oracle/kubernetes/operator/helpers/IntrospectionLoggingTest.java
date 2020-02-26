// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.helpers.JobHelper.INTROSPECTOR_LOG_PREFIX;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class IntrospectionLoggingTest {
  private Domain domain = DomainProcessorTestSetup.createTestDomain();
  private DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();
  private String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private TerminalStep terminalStep = new TerminalStep();

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger().collectAllLogMessages(logRecords));
    mementos.add(testSupport.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.defineResources(domain);
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  private static final String SEVERE_PROBLEM_1 = "really bad";
  private static final String SEVERE_PROBLEM_2 = "better fix it";
  private static final String SEVERE_MESSAGE_1 = "@[SEVERE] " + SEVERE_PROBLEM_1;
  private static final String SEVERE_MESSAGE_2 = "@[SEVERE] " + SEVERE_PROBLEM_2;
  private static final String WARNING_MESSAGE = "@[WARNING] could be bad";
  private static final String INFO_MESSAGE = "@[INFO] just letting you know";
  private static final String INFO_EXTRA1 = "more stuff";
  private static final String INFO_EXTRA_2 = "still more";

  @Test
  public void logIntrospectorMessages() {
    new DomainProcessorTestSetup(testSupport)
        .defineKubernetesResources(
            onSeparateLines(SEVERE_MESSAGE_1, WARNING_MESSAGE, INFO_MESSAGE));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(logRecords, containsSevere(INTROSPECTOR_LOG_PREFIX + SEVERE_MESSAGE_1));
    assertThat(logRecords, containsWarning(INTROSPECTOR_LOG_PREFIX + WARNING_MESSAGE));
    assertThat(logRecords, containsInfo(INTROSPECTOR_LOG_PREFIX + INFO_MESSAGE));
    logRecords.clear();
  }

  private String onSeparateLines(String... s) {
    return String.join(System.lineSeparator(), s);
  }

  @Test
  public void whenIntrospectorMessageContainsAdditionalLines_logThem() {
    String extendedInfoMessage = onSeparateLines(INFO_MESSAGE, INFO_EXTRA1, INFO_EXTRA_2);
    new DomainProcessorTestSetup(testSupport).defineKubernetesResources(extendedInfoMessage);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(logRecords, containsInfo(INTROSPECTOR_LOG_PREFIX + extendedInfoMessage));
    logRecords.clear();
  }

  @Test
  public void whenJobLogContainsSevereError_copyToDomainStatus() {
    new DomainProcessorTestSetup(testSupport).defineKubernetesResources(SEVERE_MESSAGE_1);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));
    logRecords.clear();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo("ErrIntrospector"));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(SEVERE_PROBLEM_1));
  }

  @Test
  public void whenJobLogContainsMultipleSevereErrors_copyToDomainStatus() {
    new DomainProcessorTestSetup(testSupport)
        .defineKubernetesResources(
            onSeparateLines(SEVERE_MESSAGE_1, INFO_MESSAGE, INFO_EXTRA1, SEVERE_MESSAGE_2));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));
    logRecords.clear();

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo("ErrIntrospector"));
    assertThat(
        updatedDomain.getStatus().getMessage(),
        equalTo(onSeparateLines(SEVERE_PROBLEM_1, SEVERE_PROBLEM_2)));
  }
}
