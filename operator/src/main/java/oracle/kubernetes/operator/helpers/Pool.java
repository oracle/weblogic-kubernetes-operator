// Copyright 2017, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.joda.time.DateTime;

/** General-purpose object pool. */
public abstract class Pool<T> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public static class Entry<T> {
    private final T value;
    private final DateTime creationTime;

    public Entry(T value) {
      this.value = value;
      this.creationTime = DateTime.now();
    }

    public T value() {
      return value;
    }
  }

  // volatile since multiple threads may access queue reference
  private volatile Queue<Entry<T>> queue = new ConcurrentLinkedQueue<>();

  protected int connectionLifetimeSeconds() {
    return 0;
  }

  private final boolean validateEntry(Entry<T> entry) {
    int lifetime = connectionLifetimeSeconds();
    if (lifetime > 0) {
      return entry.creationTime.plusSeconds(lifetime).compareTo(DateTime.now()) > 0;
    }

    return true;
  }

  /**
   * Gets a new object from the pool. If no object is available in the pool, this method creates a
   * new one.
   *
   * @return always non-null.
   */
  public final Entry<T> take() {
    Entry<T> instance = null;
    do {
      instance = getQueue().poll();
    } while (instance != null && !validateEntry(instance));
    if (instance == null) {
      LOGGER.finer("Creating instance");
      return new Entry(create());
    }

    if (LOGGER.isFinerEnabled()) {
      LOGGER.finer(
          "Returning existing instance from pool, instances remaining: " + getQueue().size());
    }
    return instance;
  }

  protected Queue<Entry<T>> getQueue() {
    return queue;
  }

  /**
   * Returns an object back to the pool.
   *
   * @param instance Pool object to recycle
   */
  public final void recycle(Entry<T> instance) {
    if (validateEntry(instance)) {
      getQueue().offer(instance);
      if (LOGGER.isFinerEnabled()) {
        LOGGER.finer("Recycling instance to pool, instances now in pool: " + getQueue().size());
      }
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
}
