// Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction.Kind;

/**
 * User-level thread&#x2E; Represents the execution of one processing flow. The {@link Engine} is
 * capable of running a large number of flows concurrently by using a relatively small number of
 * threads. This is made possible by utilizing a {@link Fiber} &mdash; a user-level thread that gets
 * created for each processing flow. A fiber remembers where in the pipeline the processing is at
 * and other additional information specific to the execution of a particular flow.
 *
 * <h2>Suspend/Resume</h2>
 *
 * <p>Fiber can be {@link NextAction#suspend(Consumer) suspended} by a {@link Step}. When a fiber is
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
public final class Fiber implements Runnable, Future<Void>, ComponentRegistry {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** The next action for this Fiber. */
  private NextAction na;

  public final Engine owner;
  private final Fiber parent;

  private final int id;
  private ClassLoader contextClassLoader;
  private CompletionCallback completionCallback;

  /** The thread on which this Fiber is currently executing, if applicable. */
  private Thread currentThread;

  private ExitCallback exitCallback;

  private Collection<Fiber> children = null;

  // Will only be populated if log level is at least FINE
  private List<BreadCrumb> breadCrumbs = null;

  /**
   * Replace uses of synchronized(this) with this lock so that we can control unlocking for resume
   * use cases.
   */
  private final ReentrantLock lock = new ReentrantLock();

  private final Condition condition = lock.newCondition();

  private static final int NOT_COMPLETE = 0;
  private static final int DONE = 1;
  private static final int CANCELLED = 2;
  private final AtomicInteger status = new AtomicInteger(NOT_COMPLETE);

  /** Callback to be invoked when a {@link Fiber} finishes execution. */
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

  /** Callback invoked when a Thread exits processing this fiber */
  public interface ExitCallback {
    /**
     * Indicates that a thread has finished processing the fiber, for now. If the fiber is done or
     * cancelled then no thread will enter the fiber again.
     */
    void onExit();
  }

  private static final ExitCallback PLACEHOLDER = () -> {};

  Fiber(Engine engine) {
    this(engine, null);
  }

  Fiber(Engine engine, Fiber parent) {
    this.owner = engine;
    this.parent = parent;
    id = iotaGen.incrementAndGet();

    // if this is run from another fiber, then we naturally inherit its context
    // classloader,
    // so this code works for fiber->fiber inheritance just fine.
    contextClassLoader = Thread.currentThread().getContextClassLoader();
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

    if (LOGGER.isFineEnabled()) {
      breadCrumbs = new ArrayList<>();
      LOGGER.fine("{0} started", new Object[] {getName()});
    }

    if (status.get() == NOT_COMPLETE) {
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
   * mode. So the caller need not worry about synchronizing {@link NextAction#suspend(Consumer)} and
   * this method.
   *
   * @param resumePacket packet used in the resumed processing
   */
  public void resume(Packet resumePacket) {
    resume(resumePacket, null);
  }

  /**
   * Similar to resume(Packet) but allowing the Fiber to be resumed and at the same time atomically
   * assign a new CompletionCallback to it.
   *
   * @param resumePacket packet used in the resumed processing
   * @param callback Replacement completion callback
   */
  public void resume(Packet resumePacket, CompletionCallback callback) {
    if (status.get() == NOT_COMPLETE) {

      if (LOGGER.isFinerEnabled()) {
        LOGGER.finer("{0} resumed", new Object[] {getName()});
      }

      boolean doAddRunnable = false;
      lock.lock();
      try {
        if (callback != null) {
          setCompletionCallback(callback);
        }
        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("{0} resuming.", new Object[] {getName()});
        }
        na.packet = resumePacket;
        if (na.kind == Kind.SUSPEND) {
          doAddRunnable = true;
          NextAction resume = new NextAction();
          resume.invoke(na.next, na.packet);
          na = resume;
        } else {
          if (LOGGER.isFinerEnabled()) {
            LOGGER.finer(
                "{0} taking no action on resume because not suspended", new Object[] {getName()});
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

  /**
   * Terminates fiber with throwable. Must be called while the fiber is suspended.
   *
   * @param t Throwable
   * @param packet Packet
   */
  public void terminate(Throwable t, Packet packet) {
    if (t == null) {
      throw new IllegalArgumentException();
    }

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine("{0} terminated", new Object[] {getName()});
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
  public Fiber createChildFiber() {
    Fiber child = owner.createChildFiber(this);

    synchronized (this) {
      if (children == null) {
        children = new ArrayList<>();
      }
      children.add(child);
      addBreadCrumb(child);
    }

    return child;
  }

  /**
   * Marks this Fiber as cancelled. A cancelled Fiber will never invoke its completion callback
   *
   * @param mayInterrupt if cancel should use {@link Thread#interrupt()}
   * @see java.util.concurrent.Future#cancel(boolean)
   */
  @Override
  public boolean cancel(boolean mayInterrupt) {
    if (!status.compareAndSet(NOT_COMPLETE, CANCELLED)) {
      return false;
    }

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine("{0} cancelled", new Object[] {getName()});
    }

    // synchronized(this) is used as Thread running Fiber will be holding lock
    synchronized (this) {
      if (mayInterrupt) {
        if (currentThread != null) {
          currentThread.interrupt();
        }
      }

      if (children != null) {
        for (Fiber child : children) {
          child.cancel(mayInterrupt);
        }
      }

      recordBreadCrumb();
    }

    return true;
  }

  @Override
  public boolean isCancelled() {
    return status.get() == CANCELLED;
  }

  @Override
  public boolean isDone() {
    return status.get() == DONE;
  }

  public Void get() throws InterruptedException, ExecutionException {
    int s = status.get();
    if (s == CANCELLED) {
      throw new CancellationException();
    }
    if (s == NOT_COMPLETE) {
      while (true) {
        lock.lock();
        try {
          // check again under lock
          s = status.get();
          if (s == CANCELLED) {
            throw new CancellationException();
          }
          if (s == NOT_COMPLETE) {
            condition.await();
            s = status.get();
            if (s == CANCELLED) {
              throw new CancellationException();
            }
          }
        } finally {
          lock.unlock();
        }

        if (s == DONE) {
          break;
        }
      }
    }

    return null;
  }

  public Void get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    int s = status.get();
    if (s == CANCELLED) {
      throw new CancellationException();
    }
    if (s == NOT_COMPLETE) {
      while (true) {
        if (!lock.tryLock() && !lock.tryLock(timeout, unit)) {
          throw new TimeoutException();
        }
        try {
          // check again under lock
          s = status.get();
          if (s == CANCELLED) {
            throw new CancellationException();
          }
          if (s == NOT_COMPLETE) {
            if (!condition.await(timeout, unit)) {
              throw new TimeoutException();
            }
            s = status.get();
            if (s == CANCELLED) {
              throw new CancellationException();
            }
          }
        } finally {
          lock.unlock();
        }

        if (s == DONE) {
          break;
        }
      }
    }

    return null;
  }

  private boolean suspend(Holder<Boolean> isRequireUnlock, Consumer<Fiber> onExit) {
    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer("{0} suspending", new Object[] {getName()});
    }

    if (onExit != null) {
      /* INTENTIONALLY UNLOCKING EARLY */
      synchronized (this) {
        // currentThread is protected by the monitor for this fiber so
        // that it is accessible to cancel() even when the lock is held
        currentThread = null;
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
          if (currentThread == null) {
            triggerExitCallback();
          }
        }
      }

      return true;
    }

    return false;
  }

  private static final class OnExitRunnableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Throwable target;

    public OnExitRunnableException(Throwable target) {
      super((Throwable) null); // see pattern for InvocationTargetException
      this.target = target;
    }
  }

  /**
   * Gets the context {@link ClassLoader} of this fiber.
   *
   * @return Context class loader
   */
  public ClassLoader getContextClassLoader() {
    return contextClassLoader;
  }

  /**
   * Sets the context {@link ClassLoader} of this fiber.
   *
   * @param contextClassLoader Context class loader
   * @return previous context class loader
   */
  public ClassLoader setContextClassLoader(ClassLoader contextClassLoader) {
    ClassLoader r = this.contextClassLoader;
    this.contextClassLoader = contextClassLoader;
    return r;
  }

  /** DO NOT CALL THIS METHOD. This is an implementation detail of {@link Fiber}. */
  @Override
  public void run() {
    if (status.get() == NOT_COMPLETE) {
      // Clear the interrupted status, if present
      Thread.interrupted();

      final Fiber oldFiber = CURRENT_FIBER.get();
      CURRENT_FIBER.set(this);
      try {
        // doRun returns true to indicate an early exit from fiber processing
        if (!doRun()) {
          completionCheck();
        }
      } finally {
        CURRENT_FIBER.set(oldFiber);
      }
    }
  }

  private void completionCheck() {
    lock.lock();
    try {
      // Don't trigger completion and callbacks if fiber is suspended, unless
      // throwable
      int s = status.get();
      if (s == CANCELLED
          || (s == NOT_COMPLETE
              && (na.throwable != null || (na.next == null && na.kind != Kind.SUSPEND)))) {
        if (LOGGER.isFineEnabled()) {
          LOGGER.fine("{0} completed", getName());
        }

        recordBreadCrumb();
        try {
          if (s == NOT_COMPLETE && completionCallback != null) {
            if (na.throwable != null) {
              completionCallback.onThrowable(na.packet, na.throwable);
            } else {
              completionCallback.onCompletion(na.packet);
            }
          }
        } catch (Throwable t) {
          LOGGER.warning(MessageKeys.EXCEPTION, t);
        } finally {
          status.compareAndSet(NOT_COMPLETE, DONE);
          condition.signalAll();
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /** Executes the fiber as much as possible. */
  private boolean doRun() {
    // isRequireUnlock will contain Boolean.FALSE when lock has already been
    // released in suspend
    Holder<Boolean> isRequireUnlock = new Holder<Boolean>(Boolean.TRUE);
    lock.lock();
    try {
      ClassLoader old;
      synchronized (this) {
        // currentThread is protected by the monitor for this fiber so
        // that it is accessible to cancel() even when the lock is held
        currentThread = Thread.currentThread();
        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("Thread entering _doRun(): {0}", currentThread);
        }

        old = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(contextClassLoader);
      }

      try {
        return _doRun(isRequireUnlock);
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
          LOGGER.finer("Thread leaving _doRun(): {0}", thread);
        }
      }
    } finally {
      if (isRequireUnlock.value) {
        synchronized (this) {
          currentThread = null;
          triggerExitCallback();
        }
        lock.unlock();
      }
    }
  }

  private void triggerExitCallback() {
    synchronized (this) {
      if (exitCallback != null && exitCallback != PLACEHOLDER) {

        if (LOGGER.isFinerEnabled()) {
          LOGGER.finer("{0} triggering exit callback", new Object[] {getName()});
        }

        exitCallback.onExit();
      }
      exitCallback = PLACEHOLDER;
    }
  }

  private boolean _doRun(Holder<Boolean> isRequireUnlock) {
    assert (lock.isHeldByCurrentThread());

    while (isReady()) {
      if (status.get() != NOT_COMPLETE) {
        na = new NextAction();
        na.invoke(null, na.packet);
        break;
      }

      if (na.next == null) {
        // nothing else to execute. we are done.
        return false;
      }

      if (LOGGER.isFinerEnabled()) {
        LOGGER.finer(
            "{0} {1}.apply({2})",
            new Object[] {
              getName(),
              na.next,
              na.packet != null ? "Packet@" + Integer.toHexString(na.packet.hashCode()) : "null"
            });
      }

      addBreadCrumb(na);

      NextAction result;
      try {
        result = na.next.apply(na.packet);
      } catch (Throwable t) {
        Packet p = na.packet;
        na = new NextAction();
        na.terminate(t, p);

        addBreadCrumb(na);
        return false;
      }

      if (LOGGER.isFinerEnabled()) {
        LOGGER.finer("{0} {1} returned with {2}", new Object[] {getName(), na.next, result});
      }

      // If resume is called before suspend, then make sure
      // resume(Packet) is not lost
      if (result.kind != NextAction.Kind.SUSPEND) {
        result.packet = na.packet;
      }

      na = result;
      switch (result.kind) {
        case INVOKE:
          break;
        case SUSPEND:
          addBreadCrumb(new SuspendMarkerBreadCrumb());
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

  private boolean isReady() {
    return na.kind != Kind.SUSPEND;
  }

  private String getName() {
    StringBuilder sb = new StringBuilder();
    if (parent != null) {
      sb.append(parent.getName());
      sb.append("-child-");
    } else {
      synchronized (this) {
        if (currentThread != null) {
          sb.append(currentThread.getName());
          sb.append("-");
        }
      }
      sb.append("fiber-");
    }
    sb.append(id);
    return sb.toString();
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * Gets the current {@link Packet} associated with this fiber. This method returns null if no
   * packet has been associated with the fiber yet.
   *
   * @return the packet
   */
  public Packet getPacket() {
    return na.packet;
  }

  /**
   * Returns completion callback associated with this {@link Fiber}.
   *
   * @return Completion callback
   */
  public CompletionCallback getCompletionCallback() {
    return completionCallback;
  }

  /**
   * Updates completion callback associated with this {@link Fiber}.
   *
   * @param completionCallback Completion callback
   */
  public void setCompletionCallback(CompletionCallback completionCallback) {
    this.completionCallback = completionCallback;
  }

  /**
   * Cancels the current thread and accepts a callback for when the current thread, if any, exits
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
  public boolean cancelAndExitCallback(boolean mayInterrupt, ExitCallback exitCallback) {
    // Mark fiber as cancelled, if not already done
    status.compareAndSet(NOT_COMPLETE, CANCELLED);

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine("{0} cancelled", new Object[] {getName()});
    }

    AtomicInteger count = new AtomicInteger(1); // ensure we don't hit zero before iterating
    // children
    synchronized (this) {
      if (currentThread != null) {
        if (mayInterrupt) {
          currentThread.interrupt();
        }
        count.incrementAndGet();
      }

      ExitCallback myCallback =
          () -> {
            if (count.decrementAndGet() == 0) {
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
        if (this.exitCallback != null || this.exitCallback == PLACEHOLDER) {
          throw new IllegalStateException();
        }
        this.exitCallback = myCallback;
        myCallback.onExit(); // remove the buffer count
      }

      return isWillCall;
    }
  }

  /**
   * Gets the current fiber that's running. This works like {@link Thread#currentThread()}. This
   * method only works when invoked from {@link Step}.
   *
   * @return Current fiber
   */
  public static Fiber current() {
    Fiber fiber = CURRENT_FIBER.get();
    if (fiber == null) {
      throw new IllegalStateException("Can be only used from fibers");
    }
    return fiber;
  }

  /**
   * Gets the current fiber that's running, if set.
   *
   * @return Current fiber
   */
  public static Fiber getCurrentIfSet() {
    return CURRENT_FIBER.get();
  }

  private static final ThreadLocal<Fiber> CURRENT_FIBER = new ThreadLocal<Fiber>();

  /** Used to allocate unique number for each fiber. */
  private static final AtomicInteger iotaGen = new AtomicInteger();

  private synchronized void addBreadCrumb(NextAction na) {
    if (breadCrumbs != null) {
      breadCrumbs.add(new NextActionBreadCrumb(na));
    }
  }

  private synchronized void addBreadCrumb(Fiber child) {
    if (breadCrumbs != null) {
      breadCrumbs.add(new ChildFiberBreadCrumb(child));
    }
  }

  private synchronized void addBreadCrumb(BreadCrumb bc) {
    if (breadCrumbs != null) {
      breadCrumbs.add(bc);
    }
  }

  private synchronized void recordBreadCrumb() {
    if (breadCrumbs != null) {
      if (parent == null) {
        StringBuilder sb = new StringBuilder();
        writeBreadCrumb(sb);

        if (sb != null && LOGGER.isFineEnabled()) {
          LOGGER.fine("{0} bread crumb: {1}", new Object[] {getName(), sb.toString()});
        }
        breadCrumbs = null;
      }
    }
  }

  private synchronized void writeBreadCrumb(StringBuilder sb) {
    if (breadCrumbs != null) {
      sb.append('[');
      Iterator<BreadCrumb> it = breadCrumbs.iterator();
      BreadCrumb previous = null;
      while (it.hasNext()) {
        BreadCrumb bc = it.next();
        if (!bc.isMarker()) {
          if (previous != null) {
            sb.append(previous.isMarker() ? "][" : ",");
          }
          bc.writeTo(sb);
        }
        previous = bc;
      }
      sb.append(']');
    }
  }

  private interface BreadCrumb {
    void writeTo(StringBuilder sb);

    default boolean isMarker() {
      return false;
    }
  }

  private static class NextActionBreadCrumb implements BreadCrumb {
    private final NextAction na;

    public NextActionBreadCrumb(NextAction na) {
      this.na = na;
    }

    @Override
    public void writeTo(StringBuilder sb) {
      switch (na.kind) {
        case INVOKE:
        case SUSPEND:
          if (na.next != null) {
            sb.append(na.next.getName());
          }
          break;
        case THROW:
          if (na.throwable != null) {
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

  private static class ChildFiberBreadCrumb implements BreadCrumb {
    private final Fiber child;

    public ChildFiberBreadCrumb(Fiber child) {
      this.child = child;
    }

    @Override
    public void writeTo(StringBuilder sb) {
      sb.append("{child-");
      sb.append(child.id);
      sb.append(": ");
      if (child.status.get() == NOT_COMPLETE) {
        sb.append("not-complete");
      } else {
        child.writeBreadCrumb(sb);
      }
      sb.append("}");
    }
  }

  private static class SuspendMarkerBreadCrumb implements BreadCrumb {

    @Override
    public void writeTo(StringBuilder sb) {
      // no-op
    }

    @Override
    public boolean isMarker() {
      return true;
    }
  }

  private final Map<String, Component> components = new ConcurrentHashMap<String, Component>();

  @Override
  public <S> S getSPI(Class<S> spiType) {
    for (Component c : components.values()) {
      S spi = c.getSPI(spiType);
      if (spi != null) {
        return spi;
      }
    }
    return null;
  }

  @Override
  public Map<String, Component> getComponents() {
    return components;
  }

  private static final class Holder<T> {
    T value;

    public Holder(T value) {
      this.value = value;
    }
  }
}
