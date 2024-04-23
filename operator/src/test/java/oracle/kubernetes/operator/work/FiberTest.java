// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.extended.controller.reconciler.Result;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.DUMP_BREADCRUMBS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class FiberTest {

  private static final String STEPS = "steps";

  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final Packet packet = new Packet();
  private final CompletionCallbackImpl completionCallback = new CompletionCallbackImpl();

  private final List<Step> stepList = new ArrayList<>();
  private final List<Throwable> throwableList = new ArrayList<>();

  private final Step step1 = new BasicStep(1);
  private final Step step2 = new BasicStep(2);
  private final Step step3 = new BasicStep(3);
  private final ChildFiberStep childFiberStep = new ChildFiberStep(step3, step1, step2);
  private final Step error = new ThrowableStep();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();

  @BeforeEach
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, DUMP_BREADCRUMBS)
          .withLogLevel(Level.INFO));

    packet.put(STEPS, stepList);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void runToEnd() {
    runSteps(step1, step2, step3);

    assertThat(stepList, contains(step1, step2, step3));
  }

  private void runSteps(Step... steps) {
    Fiber fiber = new Fiber(testSupport.getScheduledExecutorService(), Step.chain(steps), packet, completionCallback);
    fiber.start();
  }

  @Test
  void afterSuccessfulRun_executeCompletionCallback() {
    runSteps(step1, step2, step3);

    assertThat(completionCallback.completed, is(true));
  }

  @Test
  void whenStepThrowsException_abortProcessing() {
    runSteps(step1, error, step3);

    assertThat(stepList, contains(step1, error));
  }

  @Test
  void whenStepThrowsException_captureThrowable() {
    runSteps(step1, error, step3);

    assertThat(throwableList, contains(instanceOf(RuntimeException.class)));
  }

  @Test
  void whenChildFibersCreated_runAllSteps() {
    runSteps(childFiberStep);

    assertThat(stepList, contains(step1, step2, step3));
  }

  @Test
  void whenChildFibersCreated_runSynchronizationStepLast() {
    runSteps(childFiberStep);

    assertThat(stepList, containsInRelativeOrder(step2, step3));
  }

  static class BasicStep extends Step {

    private final Integer stepNum;

    BasicStep() {
      stepNum = null;
    }

    BasicStep(int stepNum) {
      this.stepNum = stepNum;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      recordStep(packet);
      return doNext(packet);
    }

    @SuppressWarnings("unchecked")
    final void recordStep(Packet packet) {
      ((List<Step>) packet.get(STEPS)).add(this);
    }

    @Override
    protected String getDetail() {
      return Optional.ofNullable(stepNum).map(Integer::toHexString).orElse(null);
    }
  }

  static class RetryStep extends BasicStep {
    int count = 2;

    @Override
    public @Nonnull Result apply(Packet packet) {
      recordStep(packet);
      return count-- > 0 ? doRetry(packet, 50, TimeUnit.MILLISECONDS) : doNext(packet);
    }
  }

  static class ThrowableStep extends BasicStep {
    @Override
    public @Nonnull Result apply(Packet packet) {
      recordStep(packet);

      throw new RuntimeException("in test");
    }
  }

  static class ChildFiberStep extends BasicStep {

    private final Step nextStep;
    private final Step[] childSteps;

    ChildFiberStep(Step nextStep, Step... steps) {
      this.nextStep = nextStep;
      childSteps = steps;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      return doForkJoin(nextStep, packet, createStepAndPacketList(packet));
    }

    @Nonnull
    private List<Fiber.StepAndPacket> createStepAndPacketList(Packet packet) {
      return Arrays.stream(childSteps)
            .map(s -> new Fiber.StepAndPacket(s, packet.copy()))
            .collect(Collectors.toList());
    }
  }

  class CompletionCallbackImpl implements Fiber.CompletionCallback {
    boolean completed;

    @Override
    public void onCompletion(Packet packet) {
      completed = true;
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      throwableList.add(throwable);
    }
  }
}
