// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/** General-purpose object pool. */
public abstract class Pool<T> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // volatile since multiple threads may access queue reference
  private volatile WeakReference<ConcurrentLinkedQueue<T>> queue;

  /**
   * Gets a new object from the pool. If no object is available in the pool, this method creates a
   * new one.
   *
   * @return always non-null.
   */
  public final T take() {
    T instance = getQueue().poll();
    if (instance == null) {
      LOGGER.finer("Creating instance");
      return create();
    }

    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer(
          "Returning existing instance from pool, instances remaining: " + getQueue().size());
    }
    return instance;
  }

  private ConcurrentLinkedQueue<T> getQueue() {
    WeakReference<ConcurrentLinkedQueue<T>> referenceQueue = queue;
    if (referenceQueue != null) {
      ConcurrentLinkedQueue<T> returnQueue = referenceQueue.get();
      if (returnQueue != null) {
        return returnQueue;
      }
    }

    // overwrite the queue
    ConcurrentLinkedQueue<T> d = new ConcurrentLinkedQueue<>();
    queue = new WeakReference<>(d);

    return d;
  }

  /**
   * Returns an object back to the pool.
   *
   * @param instance Pool object to recycle
   */
  public final void recycle(T instance) {
    getQueue().offer(instance);
    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer("Recycling instance to pool, instances now in pool: " + getQueue().size());
    }
  }

  /**
   * Creates a new instance of object. This method is used when someone wants to {@link #take()
   * take} an object from an empty pool. Also note that multiple threads may call this method
   * concurrently.
   *
   * @return Created instance
   */
  protected abstract T create();

  /** Drains pool of all entries; useful for unit-testing */
  public void drain() {
    getQueue().clear();
  }
}
