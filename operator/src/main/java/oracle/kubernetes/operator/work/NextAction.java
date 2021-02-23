// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Indicates what shall happen after {@link Step#apply(Packet)} returns.
 *
 * <p>To allow reuse of this object, this class is mutable.
 */
public final class NextAction {
  Kind kind;
  Step next;
  Packet packet;
  Consumer<AsyncFiber> onExit;
  Throwable throwable;

  private void set(Kind k, Step v, Packet p) {
    this.kind = k;
    this.next = v;
    this.packet = p;
  }

  /**
   * Indicates that the next action should be to invoke the next step's {@link Step#apply(Packet)}.
   *
   * @param next Next step
   * @param p Packet
   */
  public void invoke(Step next, Packet p) {
    set(Kind.INVOKE, next, p);
  }

  /**
   * Indicates that the next action should be to terminate the fiber.
   *
   * @param t Throwable
   * @param p Packet
   */
  public void terminate(Throwable t, Packet p) {
    set(Kind.THROW, null, p);
    this.throwable = t;
  }

  /**
   * Indicates that the fiber should be suspended. Once the current {@link Thread} exits the fiber's
   * control loop, the onExit will be invoked. This {@link Consumer} may call {@link
   * Fiber#resume(Packet)}; however it is still guaranteed that the current fiber will return
   * control, therefore, further processing will be handled on a {@link Thread} from the {@link
   * Executor}. Once {@link Fiber#resume(Packet) resumed}, resume with the {@link
   * Step#apply(Packet)} on the given next step.
   *
   * @param next Next step
   * @param onExit Will be invoked after the fiber suspends
   */
  public void suspend(Step next, Consumer<AsyncFiber> onExit) {
    set(Kind.SUSPEND, next, null);
    this.onExit = onExit;
  }

  /**
   * Indicates that the fiber should be suspended for the indicated delay duration and then
   * automatically resumed.
   *
   * <p>Once {@link Fiber#resume(Packet) resumed}, resume with the {@link Step#apply(Packet)} on the
   * given next step.
   *
   * @param next Next step
   * @param p Packet to use when invoking {@link Step#apply(Packet)} on next step
   * @param delay Delay time
   * @param unit Delay time unit
   */
  public void delay(Step next, Packet p, long delay, TimeUnit unit) {
    suspend(next, (fiber) -> fiber.scheduleOnce(delay, unit, () -> fiber.resume(p)));
  }

  /**
   * Returns the next step.
   *
   * @return Next step
   */
  public Step getNext() {
    return next;
  }

  /** Dumps the contents to assist debugging. */
  @Override
  public String toString() {
    return '['
        + "kind=" + kind
        + ",next=" + next
        + ",packet=" + packet
        + ']';
  }

  public enum Kind {
    INVOKE,
    SUSPEND,
    THROW
  }
}
