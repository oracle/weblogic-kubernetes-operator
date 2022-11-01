// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.DUMP_BREADCRUMBS;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiberTest {

  private static final String STEPS = "steps";
  private static final String FIBERS = "fibers";

  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final Packet packet = new Packet();
  private final CompletionCallbackImpl completionCallback = new CompletionCallbackImpl();
  private final Fiber fiber = testSupport.getEngine().createFiber();

  private final List<Step> stepList = new ArrayList<>();
  private final List<Throwable> throwableList = new ArrayList<>();
  private final List<AsyncFiber> fiberList = new ArrayList<>();

  private final Step step1 = new BasicStep(1);
  private final Step step2 = new BasicStep(2);
  private final Step step3 = new BasicStep(3);
  private final Step retry = new RetryStep();
  private final Step error = new ThrowableStep();
  private final Step suspend = new SuspendingStep(this::recordFiber);
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();

  @BeforeEach
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, DUMP_BREADCRUMBS)
          .withLogLevel(Level.INFO));

    packet.put(STEPS, stepList);
    packet.put(FIBERS, fiberList);
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
    fiber.start(Step.chain(steps), packet, completionCallback);
  }

  @Test
  void afterSuccessfulRun_executeCompletionCallback() {
    runSteps(step1, step2, step3);

    assertThat(completionCallback.completed, is(true));
  }

  @Test
  void whenStepRetries_runItAgain() {
    runSteps(step1, retry, step3);
    testSupport.setTime(200, TimeUnit.MILLISECONDS);

    assertThat(stepList, contains(step1, retry, retry, retry, step3));
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
  void whenStepRequestsSuspend_hasAccessToFiber() {
    runSteps(step1, new SuspendingStep(this::recordFiber), step3);

    assertThat(fiberList, contains(sameInstance(fiber)));
  }

  @SuppressWarnings("unchecked")
  void recordFiber(Packet packet, AsyncFiber fiber) {
    ((List<AsyncFiber>) packet.get(FIBERS)).add(fiber);
  }

  @Test
  void whenStepRequestsSuspend_suspendProcessing() {
    runSteps(step1, suspend, step3);

    assertThat(stepList, contains(step1, suspend));
  }

  @Test
  void whenSuspendActionThrowsRuntimeException_rethrowFromFiber() {
    Step step = new SuspendingStep(this::throwException);
    assertThrows(RuntimeException.class,
          () -> runSteps(step1, step, step3));
  }

  void throwException(Packet packet, AsyncFiber fiber) {
    throw new RuntimeException("from test");
  }

  @Test
  void whenSuspendActionThrowsError_rethrowFromFiber() {
    Step step = new SuspendingStep(this::throwError);
    assertThrows(Error.class,
          () -> runSteps(step1, step, step3));
  }

  void throwError(Packet packet, AsyncFiber fiber) {
    throw new Error("from test");
  }

  @Test
  void whenResumeAfterStepRequestsSuspend_completeProcessing() {
    runSteps(step1, suspend, step3);
    fiber.resume(packet);

    assertThat(stepList, contains(step1, suspend, step3));
  }

  @Test
  void whenChildFibersCreated_runAllSteps() {
    runSteps(new ChildFiberStep(step3, step1, step2));

    assertThat(stepList, contains(step1, step2, step3));
  }

  @Test
  void whenChildFibersCreated_runSynchronizationStepLast() {
    runSteps(new ChildFiberStep(step3, step1, step2));

    assertThat(stepList, containsInRelativeOrder(step2, step3));
  }

  @Test
  void whenLastChildFiberThrowsException_captureIt() {
    runSteps(new ChildFiberStep(step1, step2, error));

    assertThat(throwableList, contains(instanceOf(RuntimeException.class)));
  }

  @Test
  void whenFirstChildFiberThrowsException_captureIt() {
    runSteps(new ChildFiberStep(step1, error, step2));

    assertThat(throwableList, contains(instanceOf(RuntimeException.class)));
  }

  @Test
  void whenMultipleChildFibersAtEndThrowException_captureThemInMultiThrowable() {
    runSteps(new ChildFiberStep(step1, step2, error, error));

    assertThat(throwableList, contains(instanceOf(Step.MultiThrowable.class)));
    assertThat(((Step.MultiThrowable) throwableList.get(0)).getThrowables(), hasSize(2));
  }

  @Test
  void whenMultipleChildFibersAtStartThrowException_captureThemInMultiThrowable() {
    runSteps(new ChildFiberStep(step1, error, error, step2));

    assertThat(throwableList, contains(instanceOf(Step.MultiThrowable.class)));
    assertThat(((Step.MultiThrowable) throwableList.get(0)).getThrowables(), hasSize(2));
  }


  // 1. fiber has no children, no preexisting and is not on a thread
  // create myCallback to invoke the callback on exit
  // isWillCall is false
  // returns false (fiber is now canceled)

  // 2. fiber has no children, no preexisting and is on a thread
  // create myCallback to invoke the callback on exit and sets it on the fiber
  // isWillCall is true
  // executes the callback to decrement the count
  // returns true (fiber is not canceled, and will be later)


  @Test
  void whenChildFiberAbortsAForkJoin_cancelRemainingChildren() {
    final Step failingChild2 = new FailingChildStep(step2);
    runSteps(new ChildFiberStep(error, step1, failingChild2, step3));

    assertThat(stepList, not(hasItem(step3)));
  }

  @Test
  void whenChildFiberAbortsAForkJoin_doNotRunNormalNextStep() {
    final Step failingChild2 = new FailingChildStep(step2);
    runSteps(new ChildFiberStep(error, step1, failingChild2, step3));

    assertThat(stepList, not(hasItem(error)));
  }

  @Test
  void whenChildFiberAbortsAForkJoin_runAfterAbortStep() {
    final Step afterAbortStep = step2;
    final Step failingChild2 = new FailingChildStep(afterAbortStep);
    runSteps(new ChildFiberStep(error, step1, failingChild2, step3));

    assertThat(stepList, contains(step1, failingChild2, afterAbortStep));
  }

  static class FailingChildStep extends BasicStep {
    private final Step nextStep;

    FailingChildStep(Step nextStep) {
      this.nextStep = nextStep;
    }

    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);
      return doForkJoinAbort(nextStep, packet);
    }
  }

  @Test
  void whenFiberCompletes_breadcrumbsAreCreated() {
    runSteps(step1, step2, step3);

    assertThat(fiber.getBreadCrumbs(), hasSize(3));
  }

  @Test
  void whenFiberCompletes_canWriteStepNames() {
    runSteps(step1, step2, step3);

    assertThat(fiber.getBreadCrumbString(), allOf(containsString("1"), containsString("2"), containsString("3")));
  }

  @Test
  void whenFiberWithSuspendCompletes_breadCrumbReportsSuspend() {
    runSteps(step1, suspend, step3);
    fiber.resume(packet);

    assertThat(fiber.getBreadCrumbString(), allOf(containsString("Suspending..."), containsString("Basic (3)")));
  }

  @Test
  void whenFiberThrowsException_breadCrumbReportsException() {
    runSteps(step1, error, step3);

    assertThat(fiber.getBreadCrumbString(), containsString("Throwable,(RuntimeException"));
  }

  @Test
  void whenChildFibersCreated_createBreadCrumbsForChildFibers() {
    runSteps(new ChildFiberStep(step3, step1, step2));

    assertThat(fiber.getBreadCrumbString(), containsString("child-1: [FiberTest$Basic (1)"));
  }

  @Test
  void whenDebugNotEnabled_doNotInvokeDebugCommentGenerator() {
    runSteps(
          new SimpleAnnotationStep(this::failOnInvoke),
          new ComputedAnnotationStep(this::failOnInvoke));
  }

  private String failOnInvoke() {
    throw new RuntimeException();
  }

  private String failOnInvoke(Integer i) {
    throw new RuntimeException();
  }

  @Test
  void whenDebugEnable_breadCrumbsIncludeComments() throws NoSuchFieldException {
    mementos.add(StaticStubSupport.install(NextAction.class, "commentPrefix", "PREFIX: "));
    packet.put(Fiber.DEBUG_FIBER, "PREFIX");

    runSteps(
          new SimpleAnnotationStep(this::simpleComment),
          new ComputedAnnotationStep(this::computedComment),
          step1);

    final String breadCrumbString = fiber.getBreadCrumbString();
    assertThat(logRecords, containsInfo(DUMP_BREADCRUMBS).withParams("PREFIX", breadCrumbString));
    assertThat(breadCrumbString, both(containsString("something")).and(containsString("comment(0)")));
  }

  private String simpleComment() {
    return "something";
  }

  private String computedComment(Integer i) {
    return "comment(" + i + ")";
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
    public NextAction apply(Packet packet) {
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

  static class SimpleAnnotationStep extends Step {
    private final Supplier<String> annotationGenerator;

    SimpleAnnotationStep(Supplier<String> annotationGenerator) {
      this.annotationGenerator = annotationGenerator;
    }

    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);
      return doNext(packet).withDebugComment(annotationGenerator);
    }

    @SuppressWarnings("unchecked")
    final void recordStep(Packet packet) {
      ((List<Step>) packet.get(STEPS)).add(this);
    }
  }

  static class ComputedAnnotationStep extends Step {
    private final Function<Integer,String> annotationGenerator;

    ComputedAnnotationStep(Function<Integer,String> annotationGenerator) {
      this.annotationGenerator = annotationGenerator;
    }

    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);
      return doNext(packet).withDebugComment(0, annotationGenerator);
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

    SuspendingStep(BiConsumer<Packet, AsyncFiber> suspendAction) {
      this.suspendAction = suspendAction;
    }

    @Override
    public NextAction apply(Packet packet) {
      recordStep(packet);

      return doSuspend(f -> suspendAction.accept(packet, f));
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
    public NextAction apply(Packet packet) {
      return doForkJoin(nextStep, packet, createStepAndPacketList(packet));
    }

    @Nonnull
    private List<StepAndPacket> createStepAndPacketList(Packet packet) {
      return Arrays.stream(childSteps)
            .map(s -> new StepAndPacket(s, packet.copy()))
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
