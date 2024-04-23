// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.NotNull;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class VirtualScheduledExecutorService implements ScheduledExecutorService {
  private static final int NEW = 0;
  private static final int DONE = 1;
  private static final int CANCELED = 2;

  private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

  private record Result<V>(V result, Throwable throwable) {

    V get() throws ExecutionException {
      if (throwable != null) {
        throw new ExecutionException(throwable);
      }
      return result;
    }
  }

  private static class MyScheduledFuture<V> implements ScheduledFuture<V> {
    private final AtomicLong time;
    private final AtomicInteger status = new AtomicInteger(NEW);
    private final CountDownLatch latch = new CountDownLatch(1);

    private final AtomicReference<Result<V>> result = new AtomicReference<>(new Result<>(null, null));

    MyScheduledFuture(long triggerTime) {
      time = new AtomicLong(triggerTime);
    }

    void setTime(long triggerTime) {
      time.set(triggerTime);
    }

    /**
     * Returns the remaining delay associated with this object, in the
     * given time unit.
     *
     * @param unit the time unit
     * @return the remaining delay; zero or negative values indicate that the delay has already elapsed
     */
    @Override
    public long getDelay(@NotNull TimeUnit unit) {
      return unit.convert(time.get() - System.nanoTime(), NANOSECONDS);
    }

    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * <p>The implementor must ensure {@link Integer#signum
     * signum}{@code (x.compareTo(y)) == -signum(y.compareTo(x))} for
     * all {@code x} and {@code y}.  (This implies that {@code
     * x.compareTo(y)} must throw an exception if and only if {@code
     * y.compareTo(x)} throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * {@code (x.compareTo(y) > 0 && y.compareTo(z) > 0)} implies
     * {@code x.compareTo(z) > 0}.
     *
     * <p>Finally, the implementor must ensure that {@code
     * x.compareTo(y)==0} implies that {@code signum(x.compareTo(z))
     * == signum(y.compareTo(z))}, for all {@code z}.
     *
     * @param other the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     *         is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     * @apiNote It is strongly recommended, but <i>not</i> strictly required that
     *          {@code (x.compareTo(y)==0) == (x.equals(y))}.  Generally speaking, any
     *          class that implements the {@code Comparable} interface and violates
     *          this condition should clearly indicate this fact.  The recommended
     *          language is "Note: this class has a natural ordering that is
     *          inconsistent with equals."
     */
    @Override
    @SuppressWarnings("rawtypes")
    public int compareTo(@NotNull Delayed other) {
      if (other == this) {
        return 0;
      }
      if (other instanceof MyScheduledFuture x) {
        long diff = time.get() - x.time.get();
        if (diff < 0) {
          return -1;
        } else if (diff > 0) {
          return 1;
        } else {
          return 1;
        }
      }
      long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
      return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
    }

    /**
     * Attempts to cancel execution of this task.  This method has no
     * effect if the task is already completed or cancelled, or could
     * not be cancelled for some other reason.  Otherwise, if this
     * task has not started when {@code cancel} is called, this task
     * should never run.  If the task has already started, then the
     * {@code mayInterruptIfRunning} parameter determines whether the
     * thread executing this task (when known by the implementation)
     * is interrupted in an attempt to stop the task.
     *
     * <p>The return value from this method does not necessarily
     * indicate whether the task is now cancelled; use {@link
     * #isCancelled}.
     *
     * @param mayInterruptIfRunning {@code true} if the thread
     *                              executing this task should be interrupted (if the thread is
     *                              known to the implementation); otherwise, in-progress tasks are
     *                              allowed to complete
     * @return {@code false} if the task could not be cancelled,
     *         typically because it has already completed; {@code true}
     *         otherwise. If two or more threads cause a task to be cancelled,
     *         then at least one of them returns {@code true}. Implementations
     *         may provide stronger guarantees.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (status.compareAndSet(NEW, CANCELED)) {
        latch.countDown();
        return true;
      }
      return false;
    }

    void setCanceled() {
      status.set(CANCELED);
    }

    void setResult(V result) {
      this.result.set(new Result<>(result, null));
      signalDone();
    }

    void setThrowable(Throwable throwable) {
      this.result.set(new Result<>(null, throwable));
      signalDone();
    }

    private void signalDone() {
      if (status.compareAndSet(NEW, DONE)) {
        latch.countDown();
      }
    }

    /**
     * Returns {@code true} if this task was cancelled before it completed
     * normally.
     *
     * @return {@code true} if this task was cancelled before it completed
     */
    @Override
    public boolean isCancelled() {
      return status.get() == CANCELED;
    }

    /**
     * Returns {@code true} if this task completed.
     * <p>
     * Completion may be due to normal termination, an exception, or
     * cancellation -- in all of these cases, this method will return
     * {@code true}.
     *
     * @return {@code true} if this task completed
     */
    @Override
    public boolean isDone() {
      return status.get() != NEW;
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException    if the computation threw an
     *                               exception
     * @throws InterruptedException  if the current thread was interrupted
     *                               while waiting
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {
      latch.await();
      if (isCancelled()) {
        throw new CancellationException();
      }
      return result.get().get();
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException    if the computation threw an
     *                               exception
     * @throws InterruptedException  if the current thread was interrupted
     *                               while waiting
     * @throws TimeoutException      if the wait timed out
     */
    @Override
    public V get(long timeout, @NotNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
      if (latch.await(timeout, unit)) {
        if (isCancelled()) {
          throw new CancellationException();
        }
        return result.get().get();
      } else {
        throw new TimeoutException();
      }
    }
  }

  private long triggerTime(long delay, TimeUnit unit) {
    return triggerTime(System.nanoTime(), delay, unit);
  }

  private long triggerTime(long startTime, long delay, TimeUnit unit) {
    return startTime + unit.toNanos((delay < 0) ? 0 : delay);
  }

  /**
   * Submits a one-shot task that becomes enabled after the given delay.
   *
   * @param command the task to execute
   * @param delay   the time from now to delay execution
   * @param unit    the time unit of the delay parameter
   * @return a ScheduledFuture representing pending completion of the task and
   *         whose {@code get()} method will return {@code null} upon completion
   * @throws RejectedExecutionException if the task cannot be scheduled for execution
   * @throws NullPointerException       if command or unit is null
   */
  @NotNull
  @Override
  public ScheduledFuture<?> schedule(@NotNull Runnable command, long delay, @NotNull TimeUnit unit) {
    MyScheduledFuture<?> future = new MyScheduledFuture<>(triggerTime(delay, unit));
    executorService.execute(() -> {
      try {
        unit.sleep(delay);
        if (!future.isCancelled()) {
          try {
            command.run();
            future.setResult(null);
          } catch (Throwable t) {
            future.setThrowable(t);
          }
        }
      } catch (InterruptedException e) {
        future.setCanceled();
      }
    });
    return future;
  }

  /**
   * Submits a value-returning one-shot task that becomes enabled
   * after the given delay.
   *
   * @param callable the function to execute
   * @param delay    the time from now to delay execution
   * @param unit     the time unit of the delay parameter
   * @return a ScheduledFuture that can be used to extract result or cancel
   * @throws RejectedExecutionException if the task cannot be
   *                                    scheduled for execution
   * @throws NullPointerException       if callable or unit is null
   */
  @NotNull
  @Override
  public <V> ScheduledFuture<V> schedule(@NotNull Callable<V> callable, long delay, @NotNull TimeUnit unit) {
    MyScheduledFuture<V> future = new MyScheduledFuture<>(triggerTime(delay, unit));
    executorService.execute(() -> {
      try {
        unit.sleep(delay);
        if (!future.isCancelled()) {
          try {
            future.setResult(callable.call());
          } catch (Throwable t) {
            future.setThrowable(t);
          }
        }
      } catch (InterruptedException e) {
        future.setCanceled();
      }
    });
    return future;
  }

  /**
   * Submits a periodic action that becomes enabled first after the
   * given initial delay, and subsequently with the given period;
   * that is, executions will commence after
   * {@code initialDelay}, then {@code initialDelay + period}, then
   * {@code initialDelay + 2 * period}, and so on.
   *
   * <p>The sequence of task executions continues indefinitely until
   * one of the following exceptional completions occur:
   * <ul>
   * <li>The task is {@linkplain Future#cancel explicitly cancelled}
   * via the returned future.
   * <li>The executor terminates, also resulting in task cancellation.
   * <li>An execution of the task throws an exception.  In this case
   * calling {@link Future#get() get} on the returned future will throw
   * {@link ExecutionException}, holding the exception as its cause.
   * </ul>
   * Subsequent executions are suppressed.  Subsequent calls to
   * {@link Future#isDone isDone()} on the returned future will
   * return {@code true}.
   *
   * <p>If any execution of this task takes longer than its period, then
   * subsequent executions may start late, but will not concurrently
   * execute.
   *
   * @param command      the task to execute
   * @param initialDelay the time to delay first execution
   * @param period       the period between successive executions
   * @param unit         the time unit of the initialDelay and period parameters
   * @return a ScheduledFuture representing pending completion of the series of repeated tasks.  The future's {@link
   *         Future#get() get()} method will never return normally, and will throw an exception upon task cancellation
   *         or abnormal termination of a task execution.
   * @throws RejectedExecutionException if the task cannot be
   *                                    scheduled for execution
   * @throws NullPointerException       if command or unit is null
   * @throws IllegalArgumentException   if period less than or equal to zero
   */
  @NotNull
  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(@NotNull Runnable command,
                                                long initialDelay, long period, @NotNull TimeUnit unit) {
    MyScheduledFuture<?> future = new MyScheduledFuture<>(triggerTime(initialDelay, unit));
    executorService.execute(() -> {
      try {
        unit.sleep(initialDelay);
        while (!future.isCancelled()) {
          long begin = System.nanoTime();
          try {
            command.run();
          } catch (Throwable t) {
            future.setThrowable(t);
          }
          long next = triggerTime(begin, period, unit);
          future.setTime(next);
          long delay = System.nanoTime() - next;
          if (delay > 0) {
            NANOSECONDS.sleep(delay);
          }
        }
      } catch (InterruptedException e) {
        future.setCanceled();
      }
    });
    return future;
  }

  /**
   * Submits a periodic action that becomes enabled first after the
   * given initial delay, and subsequently with the given delay
   * between the termination of one execution and the commencement of
   * the next.
   *
   * <p>The sequence of task executions continues indefinitely until
   * one of the following exceptional completions occur:
   * <ul>
   * <li>The task is {@linkplain Future#cancel explicitly cancelled}
   * via the returned future.
   * <li>The executor terminates, also resulting in task cancellation.
   * <li>An execution of the task throws an exception.  In this case
   * calling {@link Future#get() get} on the returned future will throw
   * {@link ExecutionException}, holding the exception as its cause.
   * </ul>
   * Subsequent executions are suppressed.  Subsequent calls to
   * {@link Future#isDone isDone()} on the returned future will
   * return {@code true}.
   *
   * @param command      the task to execute
   * @param initialDelay the time to delay first execution
   * @param delay        the delay between the termination of one
   *                     execution and the commencement of the next
   * @param unit         the time unit of the initialDelay and delay parameters
   * @return a ScheduledFuture representing pending completion of the series of repeated tasks.  The future's {@link
   *         Future#get() get()} method will never return normally, and will throw an exception upon task cancellation
   *         or abnormal termination of a task execution.
   * @throws RejectedExecutionException if the task cannot be
   *                                    scheduled for execution
   * @throws NullPointerException       if command or unit is null
   * @throws IllegalArgumentException   if delay less than or equal to zero
   */
  @NotNull
  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable command,
                                                   long initialDelay, long delay, @NotNull TimeUnit unit) {
    MyScheduledFuture<?> future = new MyScheduledFuture<>(triggerTime(initialDelay, unit));
    executorService.execute(() -> {
      try {
        unit.sleep(initialDelay);
        while (!future.isCancelled()) {
          try {
            command.run();
          } catch (Throwable t) {
            future.setThrowable(t);
          }
          future.setTime(triggerTime(delay, unit));
          unit.sleep(delay);
        }
      } catch (InterruptedException e) {
        future.setCanceled();
      }
    });
    return future;
  }

  /**
   * Initiates an orderly shutdown in which previously submitted
   * tasks are executed, but no new tasks will be accepted.
   * Invocation has no additional effect if already shut down.
   *
   * <p>This method does not wait for previously submitted tasks to
   * complete execution.  Use {@link #awaitTermination awaitTermination}
   * to do that.
   *
   * @throws SecurityException if a security manager exists and
   *                           shutting down this ExecutorService may manipulate
   *                           threads that the caller is not permitted to modify
   *                           because it does not hold {@link
   *                           RuntimePermission}{@code ("modifyThread")},
   *                           or the security manager's {@code checkAccess} method
   *                           denies access.
   */
  @Override
  public void shutdown() {
    executorService.shutdown();
  }

  /**
   * Attempts to stop all actively executing tasks, halts the
   * processing of waiting tasks, and returns a list of the tasks
   * that were awaiting execution.
   *
   * <p>This method does not wait for actively executing tasks to
   * terminate.  Use {@link #awaitTermination awaitTermination} to
   * do that.
   *
   * <p>There are no guarantees beyond best-effort attempts to stop
   * processing actively executing tasks.  For example, typical
   * implementations will cancel via {@link Thread#interrupt}, so any
   * task that fails to respond to interrupts may never terminate.
   *
   * @return list of tasks that never commenced execution
   * @throws SecurityException if a security manager exists and
   *                           shutting down this ExecutorService may manipulate
   *                           threads that the caller is not permitted to modify
   *                           because it does not hold {@link
   *                           RuntimePermission}{@code ("modifyThread")},
   *                           or the security manager's {@code checkAccess} method
   *                           denies access.
   */
  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return executorService.shutdownNow();
  }

  /**
   * Returns {@code true} if this executor has been shut down.
   *
   * @return {@code true} if this executor has been shut down
   */
  @Override
  public boolean isShutdown() {
    return executorService.isShutdown();
  }

  /**
   * Returns {@code true} if all tasks have completed following shut down.
   * Note that {@code isTerminated} is never {@code true} unless
   * either {@code shutdown} or {@code shutdownNow} was called first.
   *
   * @return {@code true} if all tasks have completed following shut down
   */
  @Override
  public boolean isTerminated() {
    return executorService.isTerminated();
  }

  /**
   * Blocks until all tasks have completed execution after a shutdown
   * request, or the timeout occurs, or the current thread is
   * interrupted, whichever happens first.
   *
   * @param timeout the maximum time to wait
   * @param unit    the time unit of the timeout argument
   * @return {@code true} if this executor terminated and {@code false} if the timeout elapsed before termination
   * @throws InterruptedException if interrupted while waiting
   */
  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    return executorService.awaitTermination(timeout, unit);
  }

  /**
   * Submits a value-returning task for execution and returns a
   * Future representing the pending results of the task. The
   * Future's {@code get} method will return the task's result upon
   * successful completion.
   *
   * <p>
   * If you would like to immediately block waiting
   * for a task, you can use constructions of the form
   * {@code result = exec.submit(aCallable).get();}
   *
   * <p>Note: The {@link Executors} class includes a set of methods
   * that can convert some other common closure-like objects,
   * for example, {@link PrivilegedAction} to
   * {@link Callable} form so they can be submitted.
   *
   * @param task the task to submit
   * @return a Future representing pending completion of the task
   * @throws RejectedExecutionException if the task cannot be
   *                                    scheduled for execution
   * @throws NullPointerException       if the task is null
   */
  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task) {
    return executorService.submit(task);
  }

  /**
   * Submits a Runnable task for execution and returns a Future
   * representing that task. The Future's {@code get} method will
   * return the given result upon successful completion.
   *
   * @param task   the task to submit
   * @param result the result to return
   * @return a Future representing pending completion of the task
   * @throws RejectedExecutionException if the task cannot be
   *                                    scheduled for execution
   * @throws NullPointerException       if the task is null
   */
  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Runnable task, T result) {
    return executorService.submit(task, result);
  }

  /**
   * Submits a Runnable task for execution and returns a Future
   * representing that task. The Future's {@code get} method will
   * return {@code null} upon <em>successful</em> completion.
   *
   * @param task the task to submit
   * @return a Future representing pending completion of the task
   * @throws RejectedExecutionException if the task cannot be
   *                                    scheduled for execution
   * @throws NullPointerException       if the task is null
   */
  @NotNull
  @Override
  public Future<?> submit(@NotNull Runnable task) {
    return executorService.submit(task);
  }

  /**
   * Executes the given tasks, returning a list of Futures holding
   * their status and results when all complete.
   * {@link Future#isDone} is {@code true} for each
   * element of the returned list.
   * Note that a <em>completed</em> task could have
   * terminated either normally or by throwing an exception.
   * The results of this method are undefined if the given
   * collection is modified while this operation is in progress.
   *
   * @param tasks the collection of tasks
   * @return a list of Futures representing the tasks, in the same
   *         sequential order as produced by the iterator for the given task list, each of which has completed
   * @throws InterruptedException       if interrupted while waiting, in
   *                                    which case unfinished tasks are cancelled
   * @throws NullPointerException       if tasks or any of its elements are {@code null}
   * @throws RejectedExecutionException if any task cannot be
   *                                    scheduled for execution
   */
  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return executorService.invokeAll(tasks);
  }

  /**
   * Executes the given tasks, returning a list of Futures holding
   * their status and results
   * when all complete or the timeout expires, whichever happens first.
   * {@link Future#isDone} is {@code true} for each
   * element of the returned list.
   * Upon return, tasks that have not completed are cancelled.
   * Note that a <em>completed</em> task could have
   * terminated either normally or by throwing an exception.
   * The results of this method are undefined if the given
   * collection is modified while this operation is in progress.
   *
   * @param tasks   the collection of tasks
   * @param timeout the maximum time to wait
   * @param unit    the time unit of the timeout argument
   * @return a list of Futures representing the tasks, in the same
   *         sequential order as produced by the iterator for the given task list. If the operation did not time out,
   *         each task will have completed. If it did time out, some of these tasks will not have completed.
   * @throws InterruptedException       if interrupted while waiting, in
   *                                    which case unfinished tasks are cancelled
   * @throws NullPointerException       if tasks, any of its elements, or
   *                                    unit are {@code null}
   * @throws RejectedExecutionException if any task cannot be scheduled
   *                                    for execution
   */
  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks,
                                       long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    return executorService.invokeAll(tasks, timeout, unit);
  }

  /**
   * Executes the given tasks, returning the result
   * of one that has completed successfully (i.e., without throwing
   * an exception), if any do. Upon normal or exceptional return,
   * tasks that have not completed are cancelled.
   * The results of this method are undefined if the given
   * collection is modified while this operation is in progress.
   *
   * @param tasks the collection of tasks
   * @return the result returned by one of the tasks
   * @throws InterruptedException       if interrupted while waiting
   * @throws NullPointerException       if tasks or any element task
   *                                    subject to execution is {@code null}
   * @throws IllegalArgumentException   if tasks is empty
   * @throws ExecutionException         if no task successfully completes
   * @throws RejectedExecutionException if tasks cannot be scheduled
   *                                    for execution
   */
  @NotNull
  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks)
          throws InterruptedException, ExecutionException {
    return executorService.invokeAny(tasks);
  }

  /**
   * Executes the given tasks, returning the result
   * of one that has completed successfully (i.e., without throwing
   * an exception), if any do before the given timeout elapses.
   * Upon normal or exceptional return, tasks that have not
   * completed are cancelled.
   * The results of this method are undefined if the given
   * collection is modified while this operation is in progress.
   *
   * @param tasks   the collection of tasks
   * @param timeout the maximum time to wait
   * @param unit    the time unit of the timeout argument
   * @return the result returned by one of the tasks
   * @throws InterruptedException       if interrupted while waiting
   * @throws NullPointerException       if tasks, or unit, or any element
   *                                    task subject to execution is {@code null}
   * @throws TimeoutException           if the given timeout elapses before
   *                                    any task successfully completes
   * @throws ExecutionException         if no task successfully completes
   * @throws RejectedExecutionException if tasks cannot be scheduled
   *                                    for execution
   */
  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
    return executorService.invokeAny(tasks, timeout, unit);
  }

  /**
   * Initiates an orderly shutdown in which previously submitted tasks are
   * executed, but no new tasks will be accepted. This method waits until all
   * tasks have completed execution and the executor has terminated.
   *
   * <p> If interrupted while waiting, this method stops all executing tasks as
   * if by invoking {@link #shutdownNow()}. It then continues to wait until all
   * actively executing tasks have completed. Tasks that were awaiting
   * execution are not executed. The interrupt status will be re-asserted
   * before this method returns.
   *
   * <p> If already terminated, invoking this method has no effect.
   *
   * @throws SecurityException if a security manager exists and
   *                           shutting down this ExecutorService may manipulate
   *                           threads that the caller is not permitted to modify
   *                           because it does not hold {@link
   *                           RuntimePermission}{@code ("modifyThread")},
   *                           or the security manager's {@code checkAccess} method
   *                           denies access.
   * @implSpec The default implementation invokes {@code shutdown()} and waits for tasks
   *           to complete execution with {@code awaitTermination}.
   * @since 19
   */
  @Override
  public void close() {
    executorService.close();
  }

  /**
   * Executes the given command at some time in the future.  The command
   * may execute in a new thread, in a pooled thread, or in the calling
   * thread, at the discretion of the {@code Executor} implementation.
   *
   * @param command the runnable task
   * @throws RejectedExecutionException if this task cannot be
   *                                    accepted for execution
   * @throws NullPointerException       if command is null
   */
  @Override
  public void execute(@NotNull Runnable command) {
    executorService.execute(command);
  }
}
