// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StepChainTest {
  private final FiberTestSupport testSupport = new FiberTestSupport();

  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void afterChainingGroupsOfSteps_fiberRunsThemInOrder() {
    Step group1 = new NamedStep("one", new NamedStep("two"));
    Step group2 = new NamedStep("three", new NamedStep("four", new NamedStep("five")));
    Step group3 = new NamedStep("six");

    Step chain = Step.chain(group1, group2, group3);

    Packet packet = testSupport.runSteps(chain);

    assertThat(NamedStep.getNames(packet), contains("one", "two", "three", "four", "five", "six"));
  }

  @Test
  public void ignoreNullFirstSteps() {
    Step group2 = new NamedStep("three", new NamedStep("four", new NamedStep("five")));
    Step group3 = new NamedStep("six");

    Step chain = Step.chain(null, group2, group3);

    Packet packet = testSupport.runSteps(chain);

    assertThat(NamedStep.getNames(packet), contains("three", "four", "five", "six"));
  }

  @Test
  public void ignoreNullMiddleSteps() {
    Step group1 = new NamedStep("one", new NamedStep("two"));
    Step group3 = new NamedStep("six");

    Step chain = Step.chain(group1, null, group3);

    Packet packet = testSupport.runSteps(chain);

    assertThat(NamedStep.getNames(packet), contains("one", "two", "six"));
  }

  @Test
  public void whenNoNonNullSteps_throwException() {
    assertThrows(IllegalArgumentException.class, Step::chain);
  }

  @Test
  public void doNotChainGroupThatContainsDuplicateStep() {
    Step duplicateStep = new NamedStep("duplicate", new NamedStep("two"));
    Step group1 = new NamedStep("one", duplicateStep);
    Step group2 = new NamedStep("three", duplicateStep);

    Step chain = Step.chain(group1, group2);

    assertThat(stepNamesInStepChain(chain), contains("one", "duplicate", "two"));
  }

  @Test
  public void addGroupThatContainsStepsWithSameName() {
    Step group1 = new NamedStep("one", new NamedStep("two"));
    Step group2 = new NamedStep("two", new NamedStep("three"));

    Step chain = Step.chain(group1, group2);

    assertThat(stepNamesInStepChain(chain), contains("one", "two", "two", "three"));
  }

  private static List<String> stepNamesInStepChain(Step steps) {
    return stepNamesInStepChain(steps, 10);
  }

  private static List<String> stepNamesInStepChain(Step steps, int maxNumSteps) {
    List<String> stepNames = new ArrayList<>(maxNumSteps);
    Step s = steps;
    while (s != null && stepNames.size() < maxNumSteps) {
      stepNames.add(s.getName());
      s = s.getNext();
    }
    return stepNames;
  }

  private static class NamedStep extends Step {
    private static final String NAMES = "names";
    private final String name;

    NamedStep(String name) {
      this(name, null);
    }

    NamedStep(String name, Step next) {
      super(next);
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getNames(Packet p) {
      return (List<String>) p.get(NAMES);
    }

    @Override
    public NextAction apply(Packet packet) {
      getStepNames(packet).add(name);
      return doNext(packet);
    }

    @SuppressWarnings("unchecked")
    private List<String> getStepNames(Packet packet) {
      if (!packet.containsKey(NAMES)) {
        packet.put(NAMES, new ArrayList<String>());
      }
      return (List<String>) packet.get(NAMES);
    }
  }
}
