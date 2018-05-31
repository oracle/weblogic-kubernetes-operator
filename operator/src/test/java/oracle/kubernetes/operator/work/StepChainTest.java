// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import com.meterware.simplestub.Memento;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StepChainTest {
  private FiberTestSupport testSupport = new FiberTestSupport();

  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }
    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void afterChainingGroupsOfSteps_fiberRunsThemInOrder() throws Exception {
    Step group1 = new NamedStep("one", new NamedStep("two"));
    Step group2 = new NamedStep("three", new NamedStep("four", new NamedStep("five")));
    Step group3 = new NamedStep("six");

    Step chain = Step.chain(group1, group2, group3);

    Packet packet = testSupport.runSteps(chain);

    assertThat(NamedStep.getNames(packet), contains("one", "two", "three", "four", "five", "six"));
  }

  @Test
  public void ignoreNullFirstSteps() throws Exception {
    Step group2 = new NamedStep("three", new NamedStep("four", new NamedStep("five")));
    Step group3 = new NamedStep("six");

    Step chain = Step.chain(null, group2, group3);

    Packet packet = testSupport.runSteps(chain);

    assertThat(NamedStep.getNames(packet), contains("three", "four", "five", "six"));
  }

  @Test
  public void ignoreNullMiddleSteps() throws Exception {
    Step group1 = new NamedStep("one", new NamedStep("two"));
    Step group3 = new NamedStep("six");

    Step chain = Step.chain(group1, null, group3);

    Packet packet = testSupport.runSteps(chain);

    assertThat(NamedStep.getNames(packet), contains("one", "two", "six"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenNoNonNullSteps_throwException() throws Exception {
    Step.chain();
  }

  private static class NamedStep extends Step {
    private static final String NAMES = "names";
    private String name;

    NamedStep(String name) {
      this(name, null);
    }

    NamedStep(String name, Step next) {
      super(next);
      this.name = name;
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

    @SuppressWarnings("unchecked")
    private static List<String> getNames(Packet p) {
      return (List<String>) p.get(NAMES);
    }
  }
}
