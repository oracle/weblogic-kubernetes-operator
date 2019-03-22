// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

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
  Consumer<Fiber> onExit;
  Throwable throwable;

  public enum Kind {
    INVOKE,
    SUSPEND,
    THROW
  }

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
   * Fiber#resume(Packet)}; however it is still guaranteed that the current Thread will return
   * control, therefore, further processing will be handled on a {@link Thread} from the {@link
   * Executor}. For synchronous cases, the Thread invoking this fiber cannot return until fiber
   * processing is complete; therefore, the guarantee is only that the onExit will be invoked prior
   * to completing the suspension.
   *
   * @param onExit Called once the fiber is suspended
   */
  public void suspend(Consumer<Fiber> onExit) {
    suspend(null, onExit);
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
  public void suspend(Step next, Consumer<Fiber> onExit) {
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
    suspend(
        next,
        (fiber) -> {
          fiber
              .owner
              .getExecutor()
              .schedule(
                  () -> {
                    fiber.resume(p);
                  },
                  delay,
                  unit);
        });
  }

  /**
   * Returns the next step.
   *
   * @return Next step
   */
  public Step getNext() {
    return next;
  }

  /**
   * Sets the next step.
   *
   * @param next Next step
   */
  public void setNext(Step next) {
    this.next = next;
  }

  /**
   * Returns the last Packet.
   *
   * @return Packet
   */
  public Packet getPacket() {
    return packet;
  }

  /** Dumps the contents to assist debugging. */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append('[');
    buf.append("kind=").append(kind).append(',');
    buf.append("next=").append(next).append(',');
    buf.append("packet=").append(packet != null ? packet.toString() : null).append(']');
    return buf.toString();
  }
}
