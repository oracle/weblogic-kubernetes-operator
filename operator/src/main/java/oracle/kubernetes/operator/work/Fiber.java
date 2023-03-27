// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction.Kind;

import static oracle.kubernetes.common.logging.MessageKeys.CURRENT_STEPS;
import static oracle.kubernetes.common.logging.MessageKeys.DUMP_BREADCRUMBS;

/**
 * User-level thread&#x2E; Represents the execution of one processing flow. The {@link Engine} is
 * capable of running a large number of flows concurrently by using a relatively small number of
 * threads. This is made possible by utilizing a {@link Fiber} &mdash; a user-level thread that gets
 * created for each processing flow. A fiber remembers where in the pipeline the processing is at
 * and other additional information specific to the execution of a particular flow.
 *
 * <h2>Suspend/Resume</h2>
 *
 * <p>Fiber can be {@link NextAction#suspend(Step,Consumer) suspended} by a {@link Step}. When a fiber is
 * suspended, it will be kept on the side until it is {@link #resume(Packet) resumed}. This allows
 * threads to go execute other runnable fibers, allowing efficient utilization of smaller number of
 * threads.
 *
 * <h2>Context ClassLoader</h2>
 *
 * <p>Just like thread, a fiber has a context class loader (CCL.) A fiber's CCL becomes the thread's
 * CCL when it's executing the fiber. The original CCL of the thread will be restored when the
 * thread leaves the fiber execution.
 *
 * <h2>Debugging Aid</h2>
 *
 * <p>Setting the {@link #LOGGER} for FINE would give you basic start/stop/resume/suspend level
 * logging. Using FINER would cause more detailed logging, which includes what steps are executed in
 * what order and how they behaved.
 */
public final class Fiber implements Runnable, ComponentRegistry, AsyncFiber, BreadCrumbFactory {

  /**
   * Add this to a packet with a string value to log the current fiber breadcrumbs at the INFO level when it exits,
   * along with any defined debug strings. The string value will be displayed as a prefix in the log message.
   */
  public static final String DEBUG_FIBER = "debugFiber";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final int NOT_COMPLETE = 0;
  private static final int DONE = 1;
  private static final int CANCELLED = 2;
  private static final ThreadLocal<Fiber> CURRENT_FIBER = new ThreadLocal<>();
  /** Used to allocate unique number for each fiber. */
  private static final AtomicInteger iotaGen = new AtomicInteger();
  @SuppressWarnings("FieldMayBeFinal")
  private static BiConsumer<NextAction, String> preApplyReport = Fiber::reportPreApplyState;

  public final Engine owner;
  private final Fiber parent;
  private final int id;
  /**
   * Replace uses of synchronized(this) with this lock so that we can control unlocking for resume
   * use cases.
   */
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();
  private final AtomicInteger status = new AtomicInteger(NOT_COMPLETE);
  private final Map<String, Component> components = new ConcurrentHashMap<>();
  /** The next action for this Fiber. */
  private NextAction na;
  private NextAction last;
  private final ClassLoader contextClassLoader;
  private CompletionCallback completionCallback;
  /** The thread on which this Fiber is currently executing, if applicable. */
  private final AtomicReference<Thread> currentThread = new AtomicReference<>();
  private ExitCallback exitCallback;
  private Collection<Fiber> children = null;
  // Will only be populated if log level is at least FINE
  private List<BreadCrumbFactory> breadCrumbs = null;

  // for unit test only
  public Fiber() {
    this(null);
  }

  Fiber(Engine engine) {
    this(engine, null);
  }

  Fiber(Engine engine, Fiber parent) {
    this.owner = engine;
    this.parent = parent;
    id = (parent == null) ? iotaGen.incrementAndGet() : (parent.children.size() + 1);

    // if this is run from another fiber, then we naturally inherit its context
    // classloader,
    // so this code works for fiber->fiber inheritance just fine.
    contextClassLoader = Thread.currentThread().getContextClassLoader();
  }

  /**
   * Gets the current fiber that's running, if set.
   *
   * @return Current fiber
   */
  public static Fiber getCurrentIfSet() {
    return CURRENT_FIBER.get();
  }

  /**
   * Use this fiber's executor to schedule an operation for some time in the future.
   * @param timeout the interval before the check should run, in units
   * @param unit the unit of time that defines the interval
   * @param runnable the operation to run
   */
  @Override
  public void scheduleOnce(long timeout, TimeUnit unit, Runnable runnable) {
    this.owner.getExecutor().schedule(runnable, timeout, unit);
  }

  /**
   * Starts the execution of this fiber asynchronously. This method works like {@link
   * Thread#start()}.
   *
   * @param stepline The first step of the stepline that will act on the packet.
   * @param packet The packet to be passed to {@code Step#apply(Packet)}.
   * @param completionCallback The callback to be invoked when the processing is finished and the
   *     final packet is available.
   */
  public void start(Step stepline, Packet packet, CompletionCallback completionCallback) {
    this.na = new NextAction();
    this.na.invoke(stepline, packet);
    this.completionCallback = completionCallback;

    if (status.get() == NOT_COMPLETE) {
      LOGGER.finer("{0} started", getName());
      breadCrumbs = new ArrayList<>();

      owner.addRunnable(this);
    }
  }

  /**
   * Wakes up a suspended fiber. If a fiber was suspended without specifying the next {@link Step},
   * then the execution will be resumed, by calling the {@link Step#apply(Packet)} method on the
   * next/first {@link Step} in the {@link Fiber}'s processing stack with the specified resume
   * packet as the parameter. If a fiber was suspended with specifying the next {@link Step}, then
   * the execution will be resumed, by calling the next step's {@link Step#apply(Packet)} method
   * with the specified resume packet as the parameter. This method is implemented in a race-free
   * way. Another thread can invoke this method even before this fiber goes into the suspension
   * mode. So the caller need not worry about synchronizing {@link NextAction#suspend(Step,Consumer)} and
   * this method.
   *
   * @param resumePacket packet used in the resumed processing
   */
  @Override
  public void resume(Packet resumePacket) {
    if (status.get() == NOT_COMPLETE) {

      if (LOGGER.isFinerEnabled()) {
        LOGGER.finer("{0} resumed", getName());
      }

      boolean doAddRunnable = false;
      lock.lock();
      try {
        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("{0} resuming.", getName());
        }
        na.setPacket(resumePacket);
        if (na.kind == Kind.SUSPEND) {
          doAddRunnable = true;
          NextAction resume = new NextAction();
          resume.invoke(na.getNext(), na.getPacket());
          na = resume;
        } else {
          if (LOGGER.isFinerEnabled()) {
            LOGGER.finer(
                "{0} taking no action on resume because not suspended", getName());
          }
        }
      } finally {
        lock.unlock();

        if (doAddRunnable) {
          owner.addRunnable(this);
        }
      }
    }
  }

  private String getStatus() {
    switch (status.get()) {
      case NOT_COMPLETE: return "NOT_COMPLETE";
      case DONE: return "DONE";
      case CANCELLED: return "CANCELLED";
      default: return "UNKNOWN: " + status.get();
    }
  }

  /**
   * Terminates fiber with throwable. Must be called while the fiber is suspended.
   *
   * @param t Throwable
   * @param packet Packet
   */
  @Override
  public void terminate(Throwable t, Packet packet) {
    if (t == null) {
      throw new IllegalArgumentException();
    }

    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer("{0} terminated", getName());
    }

    lock.lock();
    try {
      if (na.kind != Kind.SUSPEND) {
        throw new IllegalStateException();
      }
      na.terminate(t, packet);

      addBreadCrumb(na);
      completionCheck();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Creates a child Fiber. If this Fiber is cancelled, so will all of the children.
   *
   * @return Child fiber
   */
  @Override
  public Fiber createChildFiber() {
    synchronized (this) {
      if (children == null) {
        children = new ArrayList<>();
      }
      Fiber child = owner.createChildFiber(this);

      children.add(child);
      if (status.get() == NOT_COMPLETE) {
        addBreadCrumb(child);
      } else {
        // Race condition where child is created after parent is cancelled or done
        child.status.set(CANCELLED);
      }

      return child;
    }
  }

  /**
   * The most recently invoked step if the fiber is currently suspended.
   * @return Last invoked step for suspended fiber.
   */
  public Step getSuspendedStep() {
    lock.lock();
    try {
      if (na != null && na.kind == Kind.SUSPEND) {
        return last.getNext();
      }
      return null;
    } finally {
      lock.unlock();
    }
  }

  private boolean suspend(Holder<Boolean> isRequireUnlock, Consumer<AsyncFiber> onExit) {
    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer("{0} suspending", getName());
    }

    if (onExit != null) {
      /* INTENTIONALLY UNLOCKING EARLY */
      synchronized (this) {
        // currentThread is protected by the monitor for this fiber so
        // that it is accessible to cancel() even when the lock is held
        currentThread.set(null);
      }
      lock.unlock();
      assert (!lock.isHeldByCurrentThread());
      isRequireUnlock.value = Boolean.FALSE;

      try {
        onExit.accept(this);
      } catch (Throwable t) {
        throw new OnExitRunnableException(t);
      } finally {
        synchronized (this) {
          if (currentThread.get() == null) {
            triggerExitCallback();
          }
        }
      }

      return true;
    }

    return false;
  }

  /**
   * DO NOT CALL THIS METHOD. This is an implementation detail of {@link Fiber}.
   */
  @Override
  public void run() {
    if (status.get() == NOT_COMPLETE) {
      clearThreadInterruptedStatus();

      final Fiber oldFiber = CURRENT_FIBER.get();
      CURRENT_FIBER.set(this);
      try {
        // doRun returns true to indicate an early exit from fiber processing
        if (!doRun()) {
          completionCheck();
        }
      } finally {
        if (oldFiber == null) {
          CURRENT_FIBER.remove();
        } else {
          CURRENT_FIBER.set(oldFiber);
        }
      }
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  void clearThreadInterruptedStatus() {
    Thread.interrupted();
  }

  private void logFiberComplete(Packet packet) {
    Optional.ofNullable(packet)
          .map(p -> p.<String>getValue(DEBUG_FIBER))
          .ifPresent(prefix -> LOGGER.info(DUMP_BREADCRUMBS, prefix, getBreadCrumbString()));
  }


  private void completionCheck() {
    lock.lock();
    try {
      // Don't trigger completion and callbacks if fiber is suspended, unless
      // throwable
      int s = status.get();
      if (s == CANCELLED
          || (s == NOT_COMPLETE
              && (na.throwable != null || (na.getNext() == null && na.kind != Kind.SUSPEND)))) {
        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("{0} completed", getName());
        }

        if (LOGGER.isFinestEnabled()) {
          LOGGER.finest("{0} bread crumb: {1}", getName(), getBreadCrumbString());
        }


        try {
          if (s == NOT_COMPLETE && completionCallback != null) {
            if (na.throwable != null) {
              completionCallback.onThrowable(na.getPacket(), na.throwable);
            } else {
              completionCallback.onCompletion(na.getPacket());
            }
          }
        } catch (Throwable t) {
          LOGGER.fine(MessageKeys.EXCEPTION, t);
        } finally {
          status.compareAndSet(NOT_COMPLETE, DONE);
          condition.signalAll();
        }
      }
    } finally {
      logFiberComplete(getPacket());
      lock.unlock();
    }
  }

  /** Executes the fiber as much as possible. */
  private boolean doRun() {
    // isRequireUnlock will contain Boolean.FALSE when lock has already been
    // released in suspend
    Holder<Boolean> isRequireUnlock = new Holder<>(Boolean.TRUE);
    lock.lock();
    try {
      ClassLoader old;
      synchronized (this) {
        // currentThread is protected by the monitor for this fiber so
        // that it is accessible to cancel() even when the lock is held
        Thread thread = Thread.currentThread();
        currentThread.set(thread);
        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("Thread entering doRunInternal(): {0}", thread);
        }

        old = thread.getContextClassLoader();
        thread.setContextClassLoader(contextClassLoader);
      }

      try {
        return doRunInternal(isRequireUnlock);
      } catch (OnExitRunnableException o) {
        // catching this exception indicates onExitRunnable in suspend() threw.
        // we must still avoid double unlock
        Throwable t = o.target;
        if (t instanceof Error) {
          throw (Error) t;
        }
        if (t instanceof RuntimeException) {
          throw (RuntimeException) t;
        }
        throw new RuntimeException(t);
      } finally {
        // reacquire current thread here because fiber processing
        // may already be running on a different thread (Note: isAlreadyExited
        // tracks this state
        Thread thread = Thread.currentThread();
        thread.setContextClassLoader(old);
        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("Thread leaving doRunInternal(): {0}", thread);
        }
      }
    } finally {
      if (Boolean.TRUE.equals(isRequireUnlock.value)) {
        synchronized (this) {
          currentThread.set(null);
          triggerExitCallback();
        }
        lock.unlock();
      }
    }
  }

  private void triggerExitCallback() {
    synchronized (this) {
      if (exitCallback != null) {

        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("{0} triggering exit callback", getName());
        }

        exitCallback.onExit();
      }
      exitCallback = null;
    }
  }

  private boolean doRunInternal(Holder<Boolean> isRequireUnlock) {
    assert (lock.isHeldByCurrentThread());

    while (isReady()) {
      if (status.get() != NOT_COMPLETE) {
        na = new NextAction();
        na.invoke(null, na.getPacket());
        break;
      }

      if (na.getNext() == null) {
        // nothing else to execute. we are done.
        return false;
      }

      preApplyReport.accept(na, getName());
      addBreadCrumb(na);

      NextAction result;
      try {
        result = na.getNext().apply(na.getPacket());
      } catch (Exception t) {
        Packet p = na.getPacket();
        na = new NextAction();
        na.terminate(t, p);

        addBreadCrumb(na);
        return false;
      }

      if (LOGGER.isFinerEnabled()) {
        LOGGER.finer("{0} {1} returned with {2}", getName(), na.getNext(), result);
      }

      // If resume is called before suspend, then make sure
      // resume(Packet) is not lost
      if (result.kind != NextAction.Kind.SUSPEND) {
        result.setPacket(na.getPacket());
      }

      last = na;
      na = result;
      switch (result.kind) {
        case INVOKE:
          break;
        case SUSPEND:
          addBreadCrumb(result);
          if (suspend(isRequireUnlock, result.onExit)) {
            return true; // explicitly exiting control loop
          }
          break;
        case THROW:
          addBreadCrumb(result);
          return false;
        default:
          throw new AssertionError();
      }
    }

    // there's nothing we can execute right away.
    // we'll be back when this fiber is resumed.

    return false;
  }

  private static void reportPreApplyState(NextAction na, String fiberName) {
    LOGGER.finer(CURRENT_STEPS, na.getNext());

    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer(
          "{0} {1}.apply({2})",
          fiberName,
          na.getNext(),
          na.getPacket() != null ? "Packet@" + Integer.toHexString(na.getPacket().hashCode()) : "null");
    }
  }

  private boolean isReady() {
    return na.kind != Kind.SUSPEND;
  }

  private String getName() {
    StringBuilder sb = new StringBuilder();
    if (parent != null) {
      sb.append(parent.getName());
      sb.append("-child-");
    } else {
      Thread thread = currentThread.get();
      if (thread != null) {
        sb.append(thread.getName());
        sb.append("-");
      }
      sb.append("fiber-");
    }
    sb.append(id);
    return sb.toString();
  }

  @Override
  public String toString() {
    return getName() + " " + getStatus();
  }

  /**
   * Gets the current {@link Packet} associated with this fiber. This method returns null if no
   * packet has been associated with the fiber yet.
   *
   * @return the packet
   */
  public Packet getPacket() {
    return na.getPacket();
  }

  /**
   * Cancels this fiber and accepts a callback for when the current thread, if any, exits
   * processing this fiber. Since the fiber will now be cancelled or done, no thread will re-enter
   * this fiber. If the return value is true, then there is a current thread processing in this
   * fiber and the caller can expect a callback; however, if the return value is false, then there
   * is no current thread and there will be no callback.
   *
   * @param mayInterrupt if cancel should use {@link Thread#interrupt()}
   * @param exitCallback Callback for when current thread, if any, finally exits the fiber
   * @return true, if there is a current thread executing in the fiber and that callback will
   *     eventually occur
   */
  boolean cancelAndExitCallback(boolean mayInterrupt, ExitCallback exitCallback) {
    // Mark fiber as cancelled, if not already done
    status.compareAndSet(NOT_COMPLETE, CANCELLED);

    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer("{0} cancelled", getName());
    }

    AtomicInteger count = new AtomicInteger(1); // ensure we don't hit zero before iterating
    // children
    synchronized (this) {
      Thread thread = currentThread.get();
      if (thread != null) {
        if (mayInterrupt) {
          thread.interrupt();
        }
        count.incrementAndGet();
      }

      ExitCallback preexistingExitCallback = this.exitCallback;
      ExitCallback myCallback =
          () -> {
            if (count.decrementAndGet() == 0) {
              if (preexistingExitCallback != null) {
                preexistingExitCallback.onExit();
              }
              exitCallback.onExit();
            }
          };

      if (children != null) {
        for (Fiber child : children) {
          if (child.cancelAndExitCallback(mayInterrupt, myCallback)) {
            count.incrementAndGet();
          }
        }
      }

      boolean isWillCall = count.get() > 1; // more calls outstanding then our initial buffer count
      if (isWillCall) {
        this.exitCallback = myCallback;
        myCallback.onExit(); // remove the buffer count
      }

      return isWillCall;
    }
  }

  private synchronized void addBreadCrumb(NextAction na) {
    breadCrumbs.add(na);
  }

  private synchronized void addBreadCrumb(Fiber child) {
    breadCrumbs.add(child);
  }

  public List<BreadCrumb> getBreadCrumbs() {
    return breadCrumbs.stream().map(BreadCrumbFactory::createBreadCrumb).collect(Collectors.toList());
  }

  public String getBreadCrumbString() {
    return getBreadCrumbString(new NullPacketDumper());
  }

  /**
   * Returns a description of the actions taken while running this fiber.
   * @param dumper an object to inject contents from the packer into the description
   */
  public String getBreadCrumbString(PacketDumper dumper) {
    StringBuilder sb = new StringBuilder();
    writeBreadCrumbs(sb, dumper);
    return sb.toString();
  }

  static class NullPacketDumper implements PacketDumper {
    @Override
    public void dump(StringBuilder sb, Packet packet) {
      // no-op
    }
  }

  private synchronized void writeBreadCrumbs(StringBuilder sb, PacketDumper dumper) {
    sb.append('[');
    BreadCrumb previous = null;
    for (BreadCrumb bc : getBreadCrumbs()) {
      bc.writeTo(sb, previous, dumper);
      previous = bc;
    }
    sb.append(']');
  }

  @Override
  public <S> S getSpi(Class<S> spiType) {
    for (Component c : components.values()) {
      S spi = c.getSpi(spiType);
      if (spi != null) {
        return spi;
      }
    }
    return null;
  }

  public Map<String, Component> getComponents() {
    return components;
  }

  @Override
  public BreadCrumb createBreadCrumb() {
    return new ChildFiberBreadCrumb(this);
  }

  /**
   * Callback to be invoked when a {@link Fiber} finishes execution.
   */
  public interface CompletionCallback {
    /**
     * Indicates that the fiber has finished its execution. Since the processing flow runs
     * asynchronously, this method maybe invoked by a different thread than any of the threads that
     * started it or run a part of stepline.
     *
     * @param packet The packet
     */
    void onCompletion(Packet packet);

    /**
     * Indicates that the fiber has finished its execution with a throwable. Since the processing
     * flow runs asynchronously, this method maybe invoked by a different thread than any of the
     * threads that started it or run a part of stepline.
     *
     * @param packet The packet
     * @param throwable The throwable
     */
    void onThrowable(Packet packet, Throwable throwable);
  }

  /** Callback invoked when a Thread exits processing this fiber. */
  public interface ExitCallback {
    /**
     * Indicates that a thread has finished processing the fiber, for now. If the fiber is done or
     * cancelled then no thread will enter the fiber again.
     */
    void onExit();
  }

  private static final class OnExitRunnableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    final Throwable target;

    OnExitRunnableException(Throwable target) {
      super((Throwable) null); // see pattern for InvocationTargetException
      this.target = target;
    }
  }

  private static class ChildFiberBreadCrumb implements BreadCrumb {
    private final Fiber child;

    ChildFiberBreadCrumb(Fiber child) {
      this.child = child;
    }

    @Override
    public void writeTo(StringBuilder sb, BreadCrumb previous, PacketDumper dumper) {
      sb.append("{child-");
      sb.append(child.id);
      sb.append(": ");
      if (child.status.get() == NOT_COMPLETE) {
        sb.append("not-complete");
      } else {
        child.writeBreadCrumbs(sb, dumper);
      }
      sb.append("}");
    }
  }

  private static final class Holder<T> {
    T value;

    Holder(T value) {
      this.value = value;
    }
  }
}
