// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collection of {@link Fiber}s. Owns an {@link Executor} to run them.
 */
public class Engine {
  private final int DEFAULT_THREAD_COUNT = 5;

  private volatile ScheduledExecutorService threadPool;
  public final String id;
  private final Container container;

  /**
   * Returns identifier for this Engine
   * @return identifier
   */
  String getId() {
    return id;
  }

  /**
   * Returns the container
   * @return container
   */
  Container getContainer() {
    return container;
  }

  /**
   * Returns the executor
   * @return executor
   */
  public ScheduledExecutorService getExecutor() {
    return threadPool;
  }

  /**
   * Creates engine with the specified id, default container and specified executor
   * @param id Engine id
   * @param threadPool Executor
   */
  public Engine(String id, ScheduledExecutorService threadPool) {
    this(id, ContainerResolver.getDefault().getContainer(), threadPool);
  }

  /**
   * Creates engine with the specified id, container and executor
   * @param id Engine id
   * @param container Container
   * @param threadPool Executor
   */
  public Engine(String id, Container container, ScheduledExecutorService threadPool) {
    this(id, container);
    this.threadPool = threadPool != null ? wrap(threadPool) : null;
  }

  /**
   * Creates engine with the specified id and default container and executor
   * @param id Engine id
   */
  public Engine(String id) {
    this(id, ContainerResolver.getDefault().getContainer());
  }

  /**
   * Creates engine with the specified id, container and default executor
   * @param id Engine id
   * @param container Container
   */
  public Engine(String id, Container container) {
    this.id = id;
    this.container = container;
  }

  /**
   * Sets the executor
   * @param threadPool Executor
   */
  public void setExecutor(ScheduledExecutorService threadPool) {
    this.threadPool = threadPool != null ? wrap(threadPool) : null;
  }

  void addRunnable(Fiber fiber) {
    if (threadPool == null) {
      synchronized (this) {
        threadPool = wrap(Executors.newScheduledThreadPool(DEFAULT_THREAD_COUNT, new DaemonThreadFactory()));
      }
    }
    threadPool.execute(fiber);
  }

  private ScheduledExecutorService wrap(ScheduledExecutorService ex) {
    return ContainerResolver.getDefault().wrapExecutor(container, ex);
  }

  /**
   * Creates a new fiber in a suspended state.
   *
   * <p>
   * To start the returned fiber, call
   * {@link Fiber#start(Step,Packet,Fiber.CompletionCallback)}. It will start
   * executing the given {@link Step} with the given {@link Packet}.
   *
   * @return new Fiber
   */
  public Fiber createFiber() {
    return new Fiber(this);
  }

  Fiber createChildFiber(Fiber parent) {
    return new Fiber(this, parent);
  }

  private class DaemonThreadFactory implements ThreadFactory {
    final AtomicInteger threadNumber = new AtomicInteger(1);
    final String namePrefix;

    DaemonThreadFactory() {
      namePrefix = "engine-" + id  + "-thread-";
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
