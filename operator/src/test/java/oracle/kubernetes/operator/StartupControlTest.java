// Copyright (c) 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ResourceRule;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.helpers.CrdHelper;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.StartupControlTest.StepPresenceMatcher.containsStep;
import static oracle.kubernetes.operator.logging.MessageKeys.CRD_NO_READ_ACCESS;
import static oracle.kubernetes.operator.logging.MessageKeys.NS_NO_READ_ACCESS;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class StartupControlTest {
  private static final KubernetesVersion VERSION = new KubernetesVersion(1, 8);

  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private Map<String,String> environmentVariables = new HashMap<>();
  private Function<String, String> getEnv = (name -> environmentVariables.get(name));
  private List<Memento> mementos = new ArrayList<>();
  private List<V1ResourceRule> rules = new ArrayList<>();
  private Step startStep = new TerminalStep();
  private Step namespaceStep = new TerminalStep();
  private StartupControl startupControl = new StartupControl(VERSION);
  private Collection<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento loggerMemento;

  /**
   * Setup test environment.
   * @throws Exception if TuningParametersStub install fails.
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(StaticStubSupport.install(StartupControl.class, "getHelmVariable", getEnv));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(loggerMemento = TestUtils.silenceOperatorLogger());

    loggerMemento.collectLogMessages(logRecords, CRD_NO_READ_ACCESS, NS_NO_READ_ACCESS);
    testSupport.doOnCreate(KubernetesTestSupport.SELF_SUBJECT_RULES_REVIEW,
          (Consumer<V1SelfSubjectRulesReview>) this::populateSubjectRulesReview);
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenOperatorDedicatedUndefined_isDedicatedReturnsFalse() {
    assertThat(startupControl.isDedicated(), is(false));
  }

  @Test
  public void whenOperatorDedicatedEmptyString_isDedicatedReturnsFalse() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "");
    
    assertThat(startupControl.isDedicated(), is(false));
  }

  @Test
  public void whenOperatorDedicatedNotTrue_isDedicatedReturnsFalse() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "zork");

    assertThat(startupControl.isDedicated(), is(false));
  }

  @Test
  public void whenOperatorDedicatedTrue_isDedicatedReturnsTrue() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");

    assertThat(startupControl.isDedicated(), is(true));
  }

  @Test
  public void whenTuningParameterDedicatedSet_isDedicatedReturnsTrue() {
    TuningParameters.getInstance().put("dedicated", "true");

    assertThat(startupControl.isDedicated(), is(true));
  }

  @Test
  public void whenOperatorNamespaceEnvUndefinedDefined_returnDefaultNamespace() {
    assertThat(StartupControl.getOperatorNamespace(), equalTo("default"));
  }

  @Test
  public void whenOperatorNamespaceEnvDefined_returnSelectedNamespace() {
    environmentVariables.put(StartupControl.OPERATOR_NAMESPACE_ENV, "mynamespace");

    assertThat(StartupControl.getOperatorNamespace(), equalTo("mynamespace"));
  }

  @Test
  public void whenNotDedicated_allowAccessToAllResource() {
    assertThat(startupControl.isClusterAccessAllowed(Resource.CRDS, Operation.get), is(true));
    assertThat(startupControl.isClusterAccessAllowed(Resource.NAMESPACES, Operation.list), is(true));
  }

  @SuppressWarnings("SameParameterValue")
  private void allowClusterAccess(Resource resource, Operation operation) {
    rules.add(new V1ResourceRule()
          .apiGroups(Collections.singletonList(resource.getApiGroup()))
          .resources(Collections.singletonList(resource.getResource()))
          .verbs(Collections.singletonList(operation.name()))
    );
  }

  private void populateSubjectRulesReview(V1SelfSubjectRulesReview review) {
    review.status(new V1SubjectRulesReviewStatus().resourceRules(rules));
  }

  @Test
  public void whenDedicated_allowAccessToSpecifiedResources() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");
    allowClusterAccess(Resource.NAMESPACES, Operation.list);

    assertThat(startupControl.isClusterAccessAllowed(Resource.CRDS, Operation.get), is(false));
    assertThat(startupControl.isClusterAccessAllowed(Resource.NAMESPACES, Operation.list), is(true));
  }

  @Test
  public void whenNotDedicated_firstStepIsCrdStep() {
    Step firstStep = startupControl.getSteps(startStep, namespaceStep);

    assertThat(firstStep, instanceOf(CrdHelper.CrdStep.class));
  }

  @Test
  public void whenDedicatedAndHaveCrdReadAccess_firstStepIsCrdStep() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");
    allowClusterAccess(Resource.CRDS, Operation.get);
    loggerMemento.ignoreMessage(NS_NO_READ_ACCESS);

    Step firstStep = startupControl.getSteps(startStep, namespaceStep);

    assertThat(firstStep, instanceOf(CrdHelper.CrdStep.class));
  }

  @Test
  public void whenDedicatedAndNoCrdReadAccess_firstStepIsProposedStep() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");
    loggerMemento.ignoreMessage(NS_NO_READ_ACCESS);
    loggerMemento.ignoreMessage(CRD_NO_READ_ACCESS);

    Step firstStep = startupControl.getSteps(startStep, namespaceStep);

    assertThat(firstStep, sameInstance(startStep));
  }

  @Test
  public void whenDedicatedAndNoCrdReadAccess_logWarning() {
    loggerMemento.ignoreMessage(NS_NO_READ_ACCESS);
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");

    startupControl.getSteps(startStep, namespaceStep);

    assertThat(logRecords, containsWarning(CRD_NO_READ_ACCESS));
  }

  @Test
  public void whenNotDedicated_chainIncludesReadNamespaceStep() {
    Step firstStep = startupControl.getSteps(startStep, namespaceStep);

    assertThat(firstStep, containsStep(namespaceStep));
  }

  @Test
  public void whenDedicatedAndHaveNamespaceListAccess_chainIncludesReadNamespaceStep() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");
    allowClusterAccess(Resource.NAMESPACES, Operation.list);
    loggerMemento.ignoreMessage(CRD_NO_READ_ACCESS);

    Step firstStep = startupControl.getSteps(startStep, namespaceStep);

    assertThat(firstStep, containsStep(namespaceStep));
  }

  @Test
  public void whenDedicatedAndNoNamespaceListAccess_chainExcludesReadNamespaceStep() {
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");
    loggerMemento.ignoreMessage(CRD_NO_READ_ACCESS);
    loggerMemento.ignoreMessage(NS_NO_READ_ACCESS);

    Step firstStep = startupControl.getSteps(startStep, namespaceStep);

    assertThat(firstStep, not(containsStep(namespaceStep)));
  }

  @Test
  public void whenDedicatedAndNoNamespaceListAccess_logWarning() {
    loggerMemento.ignoreMessage(CRD_NO_READ_ACCESS);
    environmentVariables.put(StartupControl.OPERATOR_DEDICATED_ENV, "true");

    startupControl.getSteps(startStep, namespaceStep);

    assertThat(logRecords, containsWarning(NS_NO_READ_ACCESS));
  }

  @SuppressWarnings("unused")
  static class StepPresenceMatcher extends org.hamcrest.TypeSafeDiagnosingMatcher<Step> {
    private Step expectedStep;

    private StepPresenceMatcher(Step expectedStep) {
      this.expectedStep = expectedStep;
    }

    static StepPresenceMatcher containsStep(Step expectedStep) {
      return new StepPresenceMatcher(expectedStep);
    }

    @Override
    protected boolean matchesSafely(Step step, Description description) {
      while (step != null && step != expectedStep) {
        step = step.getNext();
      }

      if (step != null) {
        return true;
      }

      description.appendText("Chain does not include ").appendValue(expectedStep);
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("Chain including ").appendValue(expectedStep);
    }
  }

}
