// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

/**
 * Collection of {@link Fiber}s. Owns an {@link Executor} to run them.
 */
public class Engine {
  private static final int DEFAULT_THREAD_COUNT = 2;
  private final AtomicReference<ScheduledExecutorService> threadPool = new AtomicReference<>();

  /**
   * Creates engine with the specified executor.
   *
   * @param threadPool Executor
   */
  public Engine(ScheduledExecutorService threadPool) {
    this.threadPool.set(threadPool);
  }

  /**
   * Creates engine with the specified id and default container and executor.
   */
  public Engine() {
    this(wrappedExecutorService(ContainerResolver.getDefault().getContainer()));
  }

  /**
   * wrapped executor service.
   * @param container container
   * @return executor service
   */
  public static ScheduledExecutorService wrappedExecutorService(Container container) {
    ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(
        DEFAULT_THREAD_COUNT, r -> {
          Thread t = Executors.defaultThreadFactory().newThread(r);
          t.setDaemon(true);
          return t;
        });
    threadPool.setRemoveOnCancelPolicy(true);
    return wrap(container, new VirtualScheduledExectuorService(threadPool));
  }

  private static ScheduledExecutorService wrap(Container container, ScheduledExecutorService ex) {
    return container != null ? ContainerResolver.getDefault().wrapExecutor(container, ex) : ex;
  }

  private static class VirtualScheduledExectuorService implements ScheduledExecutorService {
    private final ScheduledExecutorService service;
    private final ExecutorService virtualService = Executors.newVirtualThreadPerTaskExecutor();

    public VirtualScheduledExectuorService(ScheduledExecutorService service) {
      this.service = service;
    }

    private Runnable wrap(Runnable command) {
      return () -> virtualService.execute(command);
    }

    @Nonnull
    @Override
    public ScheduledFuture<?> schedule(@Nonnull Runnable command, long delay, @Nonnull TimeUnit unit) {
      return service.schedule(wrap(command), delay, unit);
    }

    @Nonnull
    @Override
    public <V> ScheduledFuture<V> schedule(@Nonnull Callable<V> callable, long delay, @Nonnull TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        @Nonnull Runnable command, long initialDelay, long period, @Nonnull TimeUnit unit) {
      return service.scheduleAtFixedRate(wrap(command), initialDelay, period, unit);
    }

    @Nonnull
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        @Nonnull Runnable command, long initialDelay, long delay, @Nonnull TimeUnit unit) {
      return service.scheduleWithFixedDelay(wrap(command), initialDelay, delay, unit);
    }

    @Override
    public void shutdown() {
      service.shutdown();
    }

    @Nonnull
    @Override
    public List<Runnable> shutdownNow() {
      return service.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
      return service.isShutdown();
    }

    @Override
    public boolean isTerminated() {
      return service.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
      return service.awaitTermination(timeout, unit);
    }

    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Callable<T> task) {
      throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Runnable task, T result) {
      return service.submit(wrap(task), result);
    }

    @Nonnull
    @Override
    public Future<?> submit(@Nonnull Runnable task) {
      return service.submit(wrap(task));
    }

    @Nonnull
    @Override
    public <T> List<Future<T>> invokeAll(@Nonnull Collection<? extends Callable<T>> tasks) throws InterruptedException {
      throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public <T> List<Future<T>> invokeAll(
        @Nonnull Collection<? extends Callable<T>> tasks, long timeout, @Nonnull TimeUnit unit)
        throws InterruptedException {
      throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks, long timeout, @Nonnull TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
      service.close();
    }

    @Override
    public void execute(@Nonnull Runnable command) {
      virtualService.execute(command);
    }
  }

  /**
   * Returns the executor.
   *
   * @return executor
   */
  public ScheduledExecutorService getExecutor() {
    return threadPool.get();
  }

  void addRunnable(Fiber fiber) {
    getExecutor().execute(fiber);
  }

  /**
   * Creates a new fiber in a suspended state.
   *
   * <p>To start the returned fiber, call {@link Fiber#start(Step,Packet,Fiber.CompletionCallback)}.
   * It will start executing the given {@link Step} with the given {@link Packet}.
   *
   * @return new Fiber
   */
  public Fiber createFiber() {
    return new Fiber(this);
  }

  Fiber createChildFiber(Fiber parent) {
    return new Fiber(this, parent);
  }
}
