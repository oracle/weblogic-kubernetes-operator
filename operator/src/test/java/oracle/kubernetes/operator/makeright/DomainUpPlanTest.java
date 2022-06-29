// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.Description;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.makeright.DomainUpPlanTest.ContainerPortMatcher.hasContainerPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DomainUpPlanTest {

  private final TerminalStep adminStep = new TerminalStep();
  private final TerminalStep managedServersStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain)
                    .withWebLogicCredentialsSecret("secret");
  private final DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(domain);

  private DomainPresenceStep getDomainPresenceStep() {
    return DomainPresenceStep.createDomainPresenceStep(adminStep, managedServersStep);
  }

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(ClientFactoryStub.install());
    mementos.add(testSupport.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(TuningParametersStub.install());

    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void whenStartPolicyNull_runAdminStepOnly() {
    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(true));
    assertThat(managedServersStep.wasRun(), is(false));
  }

  @Test
  void whenNotShuttingDown_runAdminStepOnly() {
    configurator.setShuttingDown(false);

    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(true));
    assertThat(managedServersStep.wasRun(), is(false));
  }

  @Test
  void whenShuttingDown_runManagedServersStepOnly() {
    configurator.setShuttingDown(true);

    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(false));
    assertThat(managedServersStep.wasRun(), is(true));
  }

  @Test
  void whenAdminPodCreated_hasListenPort() throws NoSuchFieldException {
    mementos.add(UnitTestHash.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("domain");
    configSupport.addWlsServer("admin", 8045);
    configSupport.setAdminServerName("admin");
    Step plan =
        DomainProcessorImpl.bringAdminServerUp(
            new DomainPresenceInfo(domain), new NullPodWaiter());
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

    @Override
    public Step waitForReady(String podName, Step next) {
      return null;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return null;
    }
  }

  @SuppressWarnings("unused")
  static class ContainerPortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Pod> {
    private final int expectedPort;

    private ContainerPortMatcher(int expectedPort) {
      this.expectedPort = expectedPort;
    }

    static ContainerPortMatcher hasContainerPort(int expectedPort) {
      return new ContainerPortMatcher(expectedPort);
    }

    @Override
    protected boolean matchesSafely(V1Pod item, Description mismatchDescription) {
      if (getContainerPorts(item).stream().anyMatch(p -> p.getContainerPort() == expectedPort)) {
        return true;
      }

      mismatchDescription.appendText("No matching port found in pod ").appendText(item.toString());
      return false;
    }

    private List<V1ContainerPort> getContainerPorts(V1Pod item) {
      return Optional.ofNullable(item.getSpec())
            .map(V1PodSpec::getContainers)
            .map(c -> c.get(0))
            .map(V1Container::getPorts)
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
    private final String[] expectedSteps;

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
      if (expectedSteps.length == 1) {
        description.appendText("expected step ").appendValue(expectedSteps[0]);
      } else {
        description.appendValueList(
            "expected steps in order to include: ", ",", ".", expectedSteps);
      }
    }
  }

}
