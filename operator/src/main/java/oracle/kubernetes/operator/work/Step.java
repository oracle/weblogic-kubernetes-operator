// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;

/** Individual step in a processing flow */
public abstract class Step {
  protected final Step next;

  /**
   * Create a step with the indicated next step.
   *
   * @param next The next step, use null to indicate a terminal step
   */
  public Step(Step next) {
    this.next = next;
  }

  /**
   * Invokes step using the packet as input/output context. The next action directs further
   * processing of the {@link Fiber}.
   *
   * @param packet Packet
   * @return Next action
   */
  public abstract NextAction apply(Packet packet);

  /**
   * Create {@link NextAction} that indicates that the next step be invoked with the given {@link
   * Packet}
   *
   * @param packet Packet to provide when invoking the next step
   * @return The next action
   */
  protected NextAction doNext(Packet packet) {
    NextAction na = new NextAction();
    na.invoke(next, packet);
    return na;
  }

  /**
   * Create {@link NextAction} that indicates that the indicated step be invoked with the given
   * {@link Packet}
   *
   * @param step The step
   * @param packet Packet to provide when invoking the next step
   * @return The next action
   */
  protected NextAction doNext(Step step, Packet packet) {
    NextAction na = new NextAction();
    na.invoke(step, packet);
    return na;
  }

  /**
   * Returns next action that will end the fiber processing
   *
   * @param packet Packet
   * @return Next action that will end processing
   */
  protected final NextAction doEnd(Packet packet) {
    return doNext(null, packet);
  }

  /**
   * Returns next action that will terminate fiber processing with a throwable
   *
   * @param throwable Throwable
   * @param packet Packet
   * @return Next action that will end processing with a throwable
   */
  protected final NextAction doTerminate(Throwable throwable, Packet packet) {
    NextAction na = new NextAction();
    na.terminate(throwable, packet);
    return na;
  }

  /**
   * Create {@link NextAction} that indicates the the current step be retried after a delay
   *
   * @param packet Packet to provide when retrying this step
   * @param delay Delay time
   * @param unit Delay time unit
   * @return The next action
   */
  protected NextAction doRetry(Packet packet, long delay, TimeUnit unit) {
    NextAction na = new NextAction();
    na.delay(this, packet, delay, unit);
    return na;
  }

  /**
   * Create {@link NextAction} that indicates the the current fiber resume with the next step after
   * a delay.
   *
   * @param packet Packet to provide when retrying this step
   * @param delay Delay time
   * @param unit Delay time unit
   * @return The next action
   */
  protected NextAction doDelay(Packet packet, long delay, TimeUnit unit) {
    NextAction na = new NextAction();
    na.delay(next, packet, delay, unit);
    return na;
  }

  /**
   * Create {@link NextAction} that indicates the the current fiber resume with the indicated step
   * after a delay.
   *
   * @param step Step from which to resume
   * @param packet Packet to provide when retrying this step
   * @param delay Delay time
   * @param unit Delay time unit
   * @return The next action
   */
  protected NextAction doDelay(Step step, Packet packet, long delay, TimeUnit unit) {
    NextAction na = new NextAction();
    na.delay(step, packet, delay, unit);
    return na;
  }

  /**
   * Create {@link NextAction} that suspends the current {@link Fiber}. While suspended the Fiber
   * does not consume a thread. Resume the Fiber using {@link Fiber#resume(Packet)}
   *
   * @param onExit Called after fiber is suspended
   * @return Next action
   */
  protected NextAction doSuspend(Consumer<Fiber> onExit) {
    NextAction na = new NextAction();
    na.suspend(next, onExit);
    return na;
  }

  /**
   * Create {@link NextAction} that suspends the current {@link Fiber}. While suspended the Fiber
   * does not consume a thread. When the Fiber resumes it will start with the indicated step. Resume
   * the Fiber using {@link Fiber#resume(Packet)}
   *
   * @param step Step to invoke next when resumed
   * @param onExit Called after fiber is suspended
   * @return Next action
   */
  protected NextAction doSuspend(Step step, Consumer<Fiber> onExit) {
    NextAction na = new NextAction();
    na.suspend(step, onExit);
    return na;
  }

  /** Multi-exception */
  @SuppressWarnings("serial")
  public static class MultiThrowable extends RuntimeException {
    private final List<Throwable> throwables;

    private MultiThrowable(List<Throwable> throwables) {
      super(throwables.get(0));
      this.throwables = throwables;
    }

    /**
     * The multiple exceptions wrapped by this exception
     *
     * @return Multiple exceptions
     */
    public List<Throwable> getThrowables() {
      return throwables;
    }
  }

  /**
   * Create a {@link NextAction} that suspends the current {@link Fiber} and that starts child
   * fibers for each step and packet pair. When all of the created child fibers complete, then this
   * fiber is resumed with the indicated step and packet.
   *
   * @param step Step to invoke next when resumed after child fibers complete
   * @param packet Resume packet
   * @param startDetails Pairs of step and packet to use when starting child fibers
   * @return Next action
   */
  protected NextAction doForkJoin(
      Step step, Packet packet, Collection<StepAndPacket> startDetails) {
    return doSuspend(
        step,
        (fiber) -> {
          CompletionCallback callback =
              new CompletionCallback() {
                final AtomicInteger count = new AtomicInteger(startDetails.size());
                final List<Throwable> throwables = new ArrayList<Throwable>();

                @Override
                public void onCompletion(Packet packet) {
                  if (count.decrementAndGet() == 0) {
                    // no need to synchronize throwables as all fibers are done
                    if (throwables.isEmpty()) {
                      fiber.resume(packet);
                    } else if (throwables.size() == 1) {
                      fiber.terminate(throwables.get(0), packet);
                    } else {
                      fiber.terminate(new MultiThrowable(throwables), packet);
                    }
                  }
                }

                @Override
                public void onThrowable(Packet packet, Throwable throwable) {
                  synchronized (throwables) {
                    throwables.add(throwable);
                  }
                  if (count.decrementAndGet() == 0) {
                    // no need to synchronize throwables as all fibers are done
                    if (throwables.size() == 1) {
                      fiber.terminate(throwable, packet);
                    } else {
                      fiber.terminate(new MultiThrowable(throwables), packet);
                    }
                  }
                }
              };
          // start forked fibers
          for (StepAndPacket sp : startDetails) {
            fiber.createChildFiber().start(sp.step, sp.packet, callback);
          }
        });
  }

  /**
   * Simplifies creation of stepline. Steps will be connected following the list ordering of their
   * classes
   *
   * @param steps List of step classes
   * @return Head step
   */
  public static Step createStepline(List<Class<? extends Step>> steps) {
    try {
      Step s = null;
      ListIterator<Class<? extends Step>> it = steps.listIterator(steps.size());
      while (it.hasPrevious()) {
        Class<? extends Step> c = it.previous();
        Constructor<? extends Step> construct = c.getConstructor(Step.class);
        s = construct.newInstance(s);
      }
      return s;
    } catch (NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static class StepAndPacket {
    public final Step step;
    public final Packet packet;

    public StepAndPacket(Step step, Packet packet) {
      this.step = step;
      this.packet = packet;
    }
  }
}
