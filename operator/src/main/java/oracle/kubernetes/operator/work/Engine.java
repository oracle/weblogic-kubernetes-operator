// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Collection of {@link Fiber}s. Owns an {@link Executor} to run them. */
public class Engine {
  private static final int DEFAULT_THREAD_COUNT = 10;

  public static ScheduledExecutorService wrappedExecutorService(String id, Container container) {
    ScheduledThreadPoolExecutor threadPool =
        new ScheduledThreadPoolExecutor(DEFAULT_THREAD_COUNT, new DaemonThreadFactory(id));
    threadPool.setRemoveOnCancelPolicy(true);
    return wrap(container, threadPool);
  }

  private final AtomicReference<ScheduledExecutorService> threadPool = new AtomicReference();

  /**
   * Returns the executor.
   *
   * @return executor
   */
  public ScheduledExecutorService getExecutor() {
    return threadPool.get();
  }

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
   *
   * @param id Engine id
   */
  public Engine(String id) {
    this(wrappedExecutorService(id, ContainerResolver.getDefault().getContainer()));
  }

  void addRunnable(Fiber fiber) {
    getExecutor().execute(fiber);
  }

  private static ScheduledExecutorService wrap(Container container, ScheduledExecutorService ex) {
    return container != null ? ContainerResolver.getDefault().wrapExecutor(container, ex) : ex;
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

  private static class DaemonThreadFactory implements ThreadFactory {
    final AtomicInteger threadNumber = new AtomicInteger(1);
    final String namePrefix;

    DaemonThreadFactory(String id) {
      namePrefix = "engine-" + id + "-thread-";
    }

    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setName(namePrefix + threadNumber.getAndIncrement());
      if (!t.isDaemon()) {
        t.setDaemon(true);
      }
      if (t.getPriority() != Thread.NORM_PRIORITY) {
        t.setPriority(Thread.NORM_PRIORITY);
      }
      return t;
    }
  }
}
