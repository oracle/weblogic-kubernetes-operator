// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.DomainUpPlanTest.ContainerPortMatcher.hasContainerPort;
import static oracle.kubernetes.operator.DomainUpPlanTest.StepChainMatcher.hasChainWithStep;
import static oracle.kubernetes.operator.DomainUpPlanTest.StepChainMatcher.hasChainWithStepsInOrder;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1SecretReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.steps.DomainPresenceStep;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DomainUpPlanTest {
  private static final String NS = "namespace";
  private static final String UID = "test-uid";
  private static final V1SecretReference SECRET = new V1SecretReference().name("secret");
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private final TerminalStep adminStep = new TerminalStep();
  private final TerminalStep managedServersStep = new TerminalStep();
  private Domain domain =
      new Domain()
          .withMetadata(new V1ObjectMeta().namespace(NS))
          .withSpec(new DomainSpec().withDomainUID(UID).withWebLogicCredentialsSecret(SECRET));
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(domain);
  private DomainProcessorImpl processor =
      new DomainProcessorImpl(createStrictStub(DomainProcessorDelegateStub.class));

  private DomainPresenceStep getDomainPresenceStep() {
    return DomainPresenceStep.createDomainPresenceStep(domain, adminStep, managedServersStep);
  }

  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(InMemoryCertificates.install());

    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenStartPolicyNull_runAdminStepOnly() {
    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(true));
    assertThat(managedServersStep.wasRun(), is(false));
  }

  @Test
  public void whenNotShuttingDown_runAdminStepOnly() {
    configurator.setShuttingDown(false);

    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(true));
    assertThat(managedServersStep.wasRun(), is(false));
  }

  @Test
  public void whenShuttingDown_runManagedServersStepOnly() {
    configurator.setShuttingDown(true);

    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(false));
    assertThat(managedServersStep.wasRun(), is(true));
  }

  @Test
  public void whenNotShuttingDown_selectAdminServerStep() {
    configurator.setShuttingDown(false);

    Step plan = processor.createDomainUpPlan(new DomainPresenceInfo(domain));

    assertThat(plan, hasChainWithStepsInOrder("AdminPodStep", "ManagedServersUpStep"));
  }

  @Test
  public void whenShuttingDown_selectManagedServerStepOnly() {
    configurator.setShuttingDown(true);

    Step plan = processor.createDomainUpPlan(new DomainPresenceInfo(domain));

    assertThat(
        plan,
        both(hasChainWithStep("ManagedServersUpStep"))
            .and(not(hasChainWithStep("AdminServerStep"))));
  }

  @Test
  public void useSequenceBeforeAdminServerStep() {
    Step plan = processor.createDomainUpPlan(new DomainPresenceInfo(domain));

    assertThat(
        plan,
        hasChainWithStepsInOrder(
            "ProgressingHookStep",
            "DomainPresenceStep",
            // "DeleteIntrospectorJobStep",
            "DomainIntrospectorJobStep",
            // "WatchDomainIntrospectorJobReadyStep",
            // "ReadDomainIntrospectorPodStep",
            // "ReadDomainIntrospectorPodLogStep",
            // "SitConfigMapStep",
            "BeforeAdminServiceStep",
            "AdminPodStep",
            "ForServerStep",
            "WatchPodReadyAdminStep",
            "ManagedServersUpStep",
            "EndProgressingStep"));
  }

  @Test
  public void whenAdminPodCreated_hasListenPort() throws NoSuchFieldException {
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("domain");
    configSupport.addWlsServer("admin", 8045);
    configSupport.setAdminServerName("admin");
    Step plan =
        DomainProcessorImpl.bringAdminServerUp(
            new DomainPresenceInfo(domain), new NullPodWaiter(), null);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(plan);

    List<V1Pod> resources = testSupport.getResources(POD);
    assertThat(resources.get(0), hasContainerPort(8045));
  }

  static class NullPodWaiter implements PodAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return null;
    }
  }

  @SuppressWarnings("unused")
  static class ContainerPortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.models.V1Pod> {
    private int expectedPort;

    private ContainerPortMatcher(int expectedPort) {
      this.expectedPort = expectedPort;
    }

    static ContainerPortMatcher hasContainerPort(int expectedPort) {
      return new ContainerPortMatcher(expectedPort);
    }

    @Override
    protected boolean matchesSafely(V1Pod item, Description mismatchDescription) {
      if (getContainerPorts(item).stream().anyMatch(p -> p.getContainerPort() == expectedPort))
        return true;

      mismatchDescription.appendText("No matching port found in pod ").appendText(item.toString());
      return false;
    }

    private List<V1ContainerPort> getContainerPorts(V1Pod item) {
      return Optional.ofNullable(item.getSpec().getContainers().get(0).getPorts())
          .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("Pod with container port ").appendValue(expectedPort);
    }
  }

  @SuppressWarnings("unused")
  static class StepChainMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<oracle.kubernetes.operator.work.Step> {
    private String[] expectedSteps;

    private StepChainMatcher(String[] expectedSteps) {
      this.expectedSteps = expectedSteps;
    }

    static StepChainMatcher hasChainWithStep(String expectedStep) {
      return hasChainWithStepsInOrder(expectedStep);
    }

    static StepChainMatcher hasChainWithStepsInOrder(String... expectedSteps) {
      return new StepChainMatcher(expectedSteps);
    }

    @Override
    protected boolean matchesSafely(Step item, Description mismatchDescription) {
      List<String> steps = chainStepNames(item);

      int index = 0;
      for (String step : expectedSteps) {
        int nextIndex = steps.indexOf(step);
        if (nextIndex < index) {
          mismatchDescription.appendValueList("found steps: ", ", ", ".", steps);
          return false;
        }
        index = nextIndex;
      }

      return true;
    }

    private List<String> chainStepNames(Step step) {
      List<String> stepNames = new ArrayList<>();
      while (step != null) {
        stepNames.add(step.getClass().getSimpleName());
        step = step.getNext();
      }
      return stepNames;
    }

    @Override
    public void describeTo(Description description) {
      if (expectedSteps.length == 1)
        description.appendText("expected step ").appendValue(expectedSteps[0]);
      else
        description.appendValueList(
            "expected steps in order to include: ", ",", ".", expectedSteps);
    }
  }

  abstract static class DomainProcessorDelegateStub implements DomainProcessorDelegate {
    @Override
    public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
      return new NullPodWaiter();
    }
  }
}
