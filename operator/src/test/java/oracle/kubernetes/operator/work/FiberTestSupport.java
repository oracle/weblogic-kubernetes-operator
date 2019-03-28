// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_COMPONENT_NAME;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

/**
 * Support for writing unit tests that use a fiber to run steps. Such tests can call #runStep to
 * initiate fiber execution, which will happen in simulated time. That time starts at zero when an
 * instance is created, and can be increased by a call to setTime. This is useful to run steps which
 * are scheduled for the future, without using delays. As all steps are run in a single thread,
 * there is no need to add semaphores to coordinate them.
 *
 * <p>The components in the packet used by the embedded fiber may be access via
 * #getPacketComponents.
 */
@SuppressWarnings("UnusedReturnValue")
public class FiberTestSupport {
  private CompletionCallbackStub completionCallback = new CompletionCallbackStub();
  private ScheduledExecutorStub schedule = ScheduledExecutorStub.create();

  private static Container container = new Container();
  private Engine engine = new Engine(schedule);
  private Fiber fiber = engine.createFiber();
  private Packet packet = new Packet();

  /** Creates a single-threaded FiberGate instance. */
  public FiberGate createFiberGate() {
    return new FiberGate(engine);
  }

  /**
   * Schedules a runnable to run immediately. In practice, it will run as soon as all previously
   * queued runnables have complete.
   *
   * @param runnable a runnable to be executed by the scheduler.
   */
  public void schedule(Runnable runnable) {
    schedule.execute(runnable);
  }

  /**
   * Schedules a runnable to run immediately and at fixed intervals afterwards. See {@link
   * #schedule(Runnable)}.
   *
   * @param command a runnable to be executed by the scheduler.
   * @param initialDelay the number of time units in the future to run for the first time.
   * @param delay the number of time units between scheduled executions
   * @param unit the time unit used for the above parameters
   */
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return schedule.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }

  /**
   * Returns true if an item is scheduled to run at the specified time.
   *
   * @param time the time, in units
   * @param unit the unit associated with the time
   */
  public boolean hasItemScheduledAt(int time, TimeUnit unit) {
    return schedule.containsItemAt(time, unit);
  }

  /**
   * Returns the engine used by this support object.
   *
   * @return the current engine object
   */
  public Engine getEngine() {
    return engine;
  }

  /**
   * Sets the simulated time, thus triggering the execution of any runnable items associated with
   * earlier times.
   *
   * @param time the time, in units
   * @param unit the unit associated with the time
   */
  public void setTime(int time, TimeUnit unit) {
    schedule.setTime(time, unit);
  }

  /** Returns an unmodifiable map of the components in the test packet. */
  public Map<String, Component> getPacketComponents() {
    return Collections.unmodifiableMap(packet.getComponents());
  }

  public Packet getPacket() {
    return packet;
  }

  public FiberTestSupport addToPacket(String key, Object value) {
    packet.put(key, value);
    return this;
  }

  public FiberTestSupport addDomainPresenceInfo(DomainPresenceInfo info) {
    packet.getComponents().put(DOMAIN_COMPONENT_NAME, Component.createFor(info));
    return this;
  }

  public FiberTestSupport addRetryStrategy(RetryStrategy retryStrategy) {
    packet.getComponents().put("retry", Component.createFor(RetryStrategy.class, retryStrategy));
    return this;
  }

  public <T> FiberTestSupport addComponent(String key, Class<T> aClass, T component) {
    packet.getComponents().put(key, Component.createFor(aClass, component));
    return this;
  }

  public <T> FiberTestSupport addContainerComponent(String key, Class<T> aClass, T component) {
    container.getComponents().put(key, Component.createFor(aClass, component));
    return this;
  }

  /**
   * Starts a unit-test fiber with the specified step
   *
   * @param step the first step to run
   */
  public Packet runSteps(Step step) {
    fiber = engine.createFiber();
    fiber.start(step, packet, completionCallback);

    return packet;
  }

  /**
   * Starts a unit-test fiber with the specified step and runs until the fiber is done
   *
   * @param step the first step to run
   */
  public Packet runStepsToCompletion(Step step) {
    fiber = engine.createFiber();
    fiber.start(step, packet, completionCallback);

    // Wait for fiber to finish
    try {
      fiber.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    return packet;
  }

  /**
   * Starts a unit-test fiber with the specified step
   *
   * @param nextStep the first step to run
   */
  public Packet runSteps(StepFactory factory, Step nextStep) {
    fiber = engine.createFiber();
    fiber.start(factory.createStepList(nextStep), packet, completionCallback);
    return packet;
  }

  @FunctionalInterface
  public interface StepFactory {
    Step createStepList(Step next);
  }

  /**
   * Verifies that the completion callback's 'onThrowable' method was invoked with a throwable of
   * the specified class. Clears the throwable so that #throwOnFailure will not throw the expected
   * exception.
   *
   * @param throwableClass the class of the excepted throwable
   */
  public void verifyCompletionThrowable(Class<? extends Throwable> throwableClass) {
    completionCallback.verifyThrowable(throwableClass);
  }

  /**
   * If the completion callback's 'onThrowable' method was invoked, throws the specified throwable.
   * Note that a call to #verifyCompletionThrowable will consume the throwable, so this method will
   * not throw it.
   *
   * @throws Exception the exception reported as a failure
   */
  public void throwOnCompletionFailure() throws Exception {
    completionCallback.throwOnFailure();
  }

  abstract static class ScheduledExecutorStub implements ScheduledExecutorService {
    /** current time in milliseconds. */
    private long currentTime = 0;

    private SortedSet<ScheduledItem> scheduledItems = new TreeSet<>();
    private Queue<Runnable> queue = new ArrayDeque<>();
    private Runnable current;

    public static ScheduledExecutorStub create() {
      return createStrictStub(ScheduledExecutorStub.class);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> schedule(
        @Nonnull Runnable command, long delay, @Nonnull TimeUnit unit) {
      scheduledItems.add(new ScheduledItem(unit.toMillis(delay), command));
      runNextRunnable();
      return createStub(ScheduledFuture.class);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> scheduleWithFixedDelay(
        @Nonnull Runnable command, long initialDelay, long delay, @Nonnull TimeUnit unit) {
      scheduledItems.add(
          new PeriodicScheduledItem(
              currentTime + unit.toMillis(initialDelay), unit.toMillis(delay), command));
      runNextRunnable();
      return createStub(ScheduledFuture.class);
    }

    @Override
    public void execute(@Nullable Runnable command) {
      queue.add(command);
      if (current == null) runNextRunnable();
    }

    private void runNextRunnable() {
      while (null != (current = queue.poll())) {
        ThreadLocalContainerResolver cr = ContainerResolver.getDefault();
        Container old = cr.enterContainer(container);
        try {
          current.run();
        } finally {
          cr.exitContainer(old);
        }
        current = null;
      }
    }

    /**
     * Sets the simulated time, thus triggering the execution of any runnable iterms associated with
     * earlier times.
     *
     * @param time the time, in units
     * @param unit the unit associated with the time
     */
    void setTime(long time, TimeUnit unit) {
      long newTime = unit.toMillis(time);
      if (newTime < currentTime)
        throw new IllegalStateException(
            "Attempt to move clock backwards from " + currentTime + " to " + newTime);

      List<ScheduledItem> rescheduledItems = new ArrayList<>();
      for (Iterator<ScheduledItem> it = scheduledItems.iterator(); it.hasNext(); ) {
        ScheduledItem item = it.next();
        if (item.atTime > newTime) break;
        it.remove();
        Optional.ofNullable(item.rescheduled()).ifPresent(rescheduledItems::add);
        execute(item.runnable);
      }
      scheduledItems.addAll(rescheduledItems);

      currentTime = newTime;
    }

    /**
     * Returns true if a runnable item has been scheduled for the specified time.
     *
     * @param time the time, in units
     * @param unit the unit associated with the time
     * @return true if such an item exists
     */
    boolean containsItemAt(int time, TimeUnit unit) {
      for (ScheduledItem scheduledItem : scheduledItems)
        if (scheduledItem.atTime == unit.toMillis(time)) return true;
      return false;
    }

    private static class ScheduledItem implements Comparable<ScheduledItem> {
      private long atTime;
      private Runnable runnable;

      ScheduledItem(long atTime, Runnable runnable) {
        this.atTime = atTime;
        this.runnable = runnable;
      }

      @Override
      public int compareTo(@Nonnull ScheduledItem o) {
        return Long.compare(atTime, o.atTime);
      }

      ScheduledItem rescheduled() {
        return null;
      }
    }

    private static class PeriodicScheduledItem extends ScheduledItem {
      private final long interval;

      PeriodicScheduledItem(long atTime, long interval, Runnable runnable) {
        super(atTime, runnable);
        this.interval = interval;
      }

      @Override
      ScheduledItem rescheduled() {
        return new PeriodicScheduledItem(super.atTime + interval, interval, super.runnable);
      }
    }
  }

  static class CompletionCallbackStub implements Fiber.CompletionCallback {
    private Throwable throwable;

    @Override
    public void onCompletion(Packet packet) {}

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      this.throwable = throwable;
    }

    /**
     * Verifies that 'onThrowable' was invoked with a throwable of the specified class. Clears the
     * throwable so that #throwOnFailure will not throw the expected exception.
     *
     * @param throwableClass the class of the excepted throwable
     */
    void verifyThrowable(Class<?> throwableClass) {
      Throwable actual = throwable;
      throwable = null;

      if (actual == null)
        throw new AssertionError("Expected exception: " + throwableClass.getName());
      if (!throwableClass.isInstance(actual))
        throw new AssertionError(
            "Expected exception: " + throwableClass.getName() + " but was " + actual);
    }

    /**
     * If 'onThrowable' was invoked, throws the specified throwable. Note that a call to
     * #verifyThrowable will consume the throwable, so this method will not throw it.
     *
     * @throws Exception the exception reported as a failure
     */
    void throwOnFailure() throws Exception {
      if (throwable == null) return;
      if (throwable instanceof Error) throw (Error) throwable;
      throw (Exception) throwable;
    }
  }
}
