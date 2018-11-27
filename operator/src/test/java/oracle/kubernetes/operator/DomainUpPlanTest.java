package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.DomainUpPlanTest.StepChainMatcher.hasChainWithStep;
import static oracle.kubernetes.operator.DomainUpPlanTest.StepChainMatcher.hasChainWithStepsInOrder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ObjectMeta;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.steps.DomainPresenceStep;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DomainUpPlanTest {
  private static final String NS = "namespace";
  private FiberTestSupport testSupport = new FiberTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private final TerminalStep adminStep = new TerminalStep();
  private final TerminalStep managedServersStep = new TerminalStep();
  private Domain domain =
      new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(new DomainSpec());
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(domain);

  private DomainPresenceStep getDomainPresenceStep() {
    return DomainPresenceStep.createDomainPresenceStep(domain, adminStep, managedServersStep);
  }

  @Before
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());

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

    Step plan = DomainProcessorImpl.createDomainUpPlan(new DomainPresenceInfo(domain));

    assertThat(plan, hasChainWithStepsInOrder("AdminPodStep", "ManagedServersUpStep"));
  }

  @Test
  public void whenShuttingDown_selectManagedServerStepOnly() {
    configurator.setShuttingDown(true);

    Step plan = DomainProcessorImpl.createDomainUpPlan(new DomainPresenceInfo(domain));

    assertThat(
        plan,
        both(hasChainWithStep("ManagedServersUpStep"))
            .and(not(hasChainWithStep("AdminServerStep"))));
  }

  @Test
  public void useSequenceBeforeAdminServerStep() {
    Step plan = DomainProcessorImpl.createDomainUpPlan(new DomainPresenceInfo(domain));

    assertThat(
        plan,
        hasChainWithStepsInOrder(
            "ProgressingHookStep",
            "DomainPresenceStep",
            "ListPersistentVolumeClaimStep",
            "DeleteIntrospectorJobStep",
            "DomainIntrospectorJobStep",
            "WatchDomainIntrospectorJobReadyStep",
            "ReadDomainIntrospectorPodStep",
            "ReadDomainIntrospectorPodLogStep",
            "SitConfigMapStep",
            "AdminPodStep",
            "BeforeAdminServiceStep",
            "ForServerStep",
            "WatchPodReadyAdminStep",
            "ExternalAdminChannelsStep",
            "ManagedServersUpStep",
            "EndProgressingStep"));
  }

  @Test
  public void whenOperatorCreatedStorageConfigured_createBeforeListingClaims() {
    configurator.withNfsStorage("myhost", "/path");

    Step plan = DomainProcessorImpl.createDomainUpPlan(new DomainPresenceInfo(domain));

    assertThat(
        plan,
        hasChainWithStepsInOrder(
            "ProgressingHookStep",
            "PersistentVolumeStep",
            "PersistentVolumeClaimStep",
            "ListPersistentVolumeClaimStep"));
  }

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
}
