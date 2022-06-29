// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static oracle.kubernetes.operator.work.Fiber.DEBUG_FIBER;

/**
 * Indicates what shall happen after {@link Step#apply(Packet)} returns.
 *
 * <p>To allow reuse of this object, this class is mutable.
 */
public final class NextAction implements BreadCrumbFactory {

  /** To enable explanatory comments in breadcrumbs, set this to a non-null value.
   * It will be printed with each breadcrumb comment. */
  @SuppressWarnings("unused")
  private static String commentPrefix;

  Kind kind;
  private Step next;
  private Packet packet;
  Consumer<AsyncFiber> onExit;
  Throwable throwable;
  private String comment;

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
    suspend(next, fiber -> fiber.scheduleOnce(delay, unit, () -> fiber.resume(p)));
  }

  /**
   * Returns the next step.
   *
   * @return Next step
   */
  public Step getNext() {
    return next;
  }

  void setPacket(Packet packet) {
    this.packet = packet;
  }

  public Packet getPacket() {
    return packet;
  }

  @Override
  public BreadCrumb createBreadCrumb() {
    return new NextActionBreadCrumb(this);
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

  /**
   * Defines a comment for an action that gives more information. The comment supplier will only be invoked
   * if the comment prefix has been defined.
   *
   * @param commentSupplier a no-argument function that generates a debug comment
   */
  public NextAction withDebugComment(Supplier<String> commentSupplier) {
    Optional.ofNullable(packet).map(p -> p.get(DEBUG_FIBER)).ifPresent(prefix -> annotate(commentSupplier.get()));
    return this;
  }

  /**
   * Defines a comment for an action that gives more information. The comment supplier will only be invoked
   * if the comment prefix has been defined.
   *
   * @param data a value uses to create the comment
   * @param commentFunction a function that creates a comment from the data value
   */
  public <T> NextAction withDebugComment(T data, Function<T, String> commentFunction) {
    Optional.ofNullable(packet).map(p -> p.get(DEBUG_FIBER)).ifPresent(prefix -> annotate(commentFunction.apply(data)));
    return this;
  }

  private void annotate(String comment) {
    this.comment = " ((" + comment + "))";
  }

  public enum Kind {
    INVOKE,
    SUSPEND,
    THROW;
    Kind getPreviousKind(BreadCrumb previous) {
      return (previous instanceof NextActionBreadCrumb) ? ((NextActionBreadCrumb) previous).na.kind : null;
    }
  }

  static class NextActionBreadCrumb implements BreadCrumb {
    private final NextAction na;

    NextActionBreadCrumb(NextAction na) {
      this.na = na;
    }

    @Override
    public void writeTo(StringBuilder sb, BreadCrumb previous, PacketDumper dumper) {
      Kind previousKind = na.kind.getPreviousKind(previous);
      switch (na.kind) {
        case INVOKE:
          if (na.next != null) {
            Optional.ofNullable(na.comment).ifPresent(sb::append);
            if (previousKind != null) {
              sb.append(", ");
            }
            sb.append(na.next.getResourceName());
            dumper.dump(sb, na.getPacket());
          }
          break;
        case SUSPEND:
          if (previousKind != Kind.SUSPEND) {
            sb.append("...");
          }
          break;
        case THROW:
          if (na.throwable != null) {
            if (previousKind == Kind.INVOKE) {
              sb.append(",");
            }
            sb.append('(');
            sb.append(na.throwable.getClass().getSimpleName());
            sb.append(')');
          }
          break;
        default:
          throw new AssertionError();
      }
    }
  }
}
