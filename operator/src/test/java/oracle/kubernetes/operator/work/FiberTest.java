// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FiberTest {

  private static final String STEPS = "steps";
  private static final String FIBERS = "fibers";

  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final Packet packet = new Packet();
  private final CompletionCallbackImpl completionCallback = new CompletionCallbackImpl();
  private final Fiber fiber = testSupport.getEngine().createFiber();

  private final List<Step> stepList = new ArrayList<>();
  private final List<Throwable> throwablesList = new ArrayList<>();
  private final List<AsyncFiber> fiberList = new ArrayList<>();

  private final Step step1 = new BasicStep();
  private final Step step2 = new BasicStep();
  private final Step step3 = new BasicStep();
  private final Step retry = new RetryStep();
  private final Step error = new ThrowableStep();
  private final Step suspend = new SuspendingStep(this::recordFiber);

  @BeforeEach
  public void setUp() {
    packet.put(STEPS, stepList);
    packet.put(FIBERS, fiberList);
  }

  @Test
  public void runToEnd() {
    runSteps(step1, step2, step3);

    assertThat(stepList, contains(step1, step2, step3));
  }

  private void runSteps(Step... steps) {
    fiber.start(Step.chain(steps), packet, completionCallback);
  }

  @Test
  public void afterSuccessfulRun_executeCompletionCallback() {
    runSteps(step1, step2, step3);

    assertThat(completionCallback.completed, is(true));
  }

  @Test
  public void whenStepRetries_runItAgain() {
    runSteps(step1, retry, step3);
    testSupport.setTime(200, TimeUnit.MILLISECONDS);

    assertThat(stepList, contains(step1, retry, retry, retry, step3));
  }

  @Test
  public void whenStepThrowsException_abortProcessing() {
    runSteps(step1, error, step3);

    assertThat(stepList, contains(step1, error));
  }

  @Test
  public void whenStepThrowsException_captureThrowable() {
    runSteps(step1, error, step3);

    assertThat(throwablesList, contains(instanceOf(RuntimeException.class)));
  }

  @Test
  public void whenStepRequestsSuspend_hasAccessToFiber() {
    runSteps(step1, new SuspendingStep(this::recordFiber), step3);

    assertThat(fiberList, contains(sameInstance(fiber)));
  }

  @SuppressWarnings("unchecked")
  void recordFiber(Packet packet, AsyncFiber fiber) {
    ((List<AsyncFiber>) packet.get(FIBERS)).add(fiber);
  }

  @Test
  public void whenStepRequestsSuspend_suspendProcessing() {
    runSteps(step1, suspend, step3);

    assertThat(stepList, contains(step1, suspend));
  }

  @Test
  public void whenSuspendActionThrowsRuntimeException_rethrowFromFiber() {
    assertThrows(RuntimeException.class,
          () -> runSteps(step1, new SuspendingStep(this::throwException), step3));
  }

  void throwException(Packet packet, AsyncFiber fiber) {
    throw new RuntimeException("from test");
  }

  @Test
  public void whenSuspendActionThrowsError_rethrowFromFiber() {
    assertThrows(Error.class,
          () -> runSteps(step1, new SuspendingStep(this::throwError), step3));
  }

  void throwError(Packet packet, AsyncFiber fiber) {
    throw new Error("from test");
  }

  @Test
  public void whenResumeAfterStepRequestsSuspend_completeProcessing() {
    runSteps(step1, suspend, step3);
    fiber.resume(packet);

    assertThat(stepList, contains(step1, suspend, step3));
  }

  static class BasicStep extends Step {
    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);
      return doNext(packet);
    }

    @SuppressWarnings("unchecked")
    final void recordStep(Packet packet) {
      ((List<Step>) packet.get(STEPS)).add(this);
    }
  }

  static class RetryStep extends BasicStep {
    int count = 2;

    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);
      return count-- > 0 ? doRetry(packet, 50, TimeUnit.MILLISECONDS) : doNext(packet);
    }
  }

  static class ThrowableStep extends BasicStep {
    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);

      throw new RuntimeException("in test");
    }
  }

  static class SuspendingStep extends BasicStep {
    private final BiConsumer<Packet, AsyncFiber> suspendAction;

    public SuspendingStep(BiConsumer<Packet, AsyncFiber> suspendAction) {
      this.suspendAction = suspendAction;
    }

    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);

      return doSuspend(f -> suspendAction.accept(packet, f));
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
      throwablesList.add(throwable);
    }
  }
}