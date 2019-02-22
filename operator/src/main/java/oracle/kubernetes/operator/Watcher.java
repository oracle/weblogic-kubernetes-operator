// Copyright 2017, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static java.net.HttpURLConnection.HTTP_GONE;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.util.Watch;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles the Watching interface and drives the watch support for a specific type of
 * object. It runs in a separate thread to drive watching asynchronously to the main thread.
 *
 * @param <T> The type of the object to be watched.
 */
abstract class Watcher<T> {
  static final String HAS_NEXT_EXCEPTION_MESSAGE = "IO Exception during hasNext method.";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final long IGNORED_RESOURCE_VERSION = 0;

  private final AtomicBoolean isDraining = new AtomicBoolean(false);
  private final WatchTuning tuning;
  private Long resourceVersion;
  private AtomicBoolean stopping;
  private WatchListener<T> listener;
  private Thread thread = null;
  private long lastInitialize = 0;

  /**
   * Constructs a watcher without specifying a listener. Needed when the listener is the watch
   * subclass itself.
   *
   * @param resourceVersion the oldest version to return for this watch
   * @param tuning Watch tuning parameters
   * @param stopping an atomic boolean to watch to determine when to stop the watcher
   */
  Watcher(String resourceVersion, WatchTuning tuning, AtomicBoolean stopping) {
    this.resourceVersion =
        !isNullOrEmptyString(resourceVersion) ? Long.parseLong(resourceVersion) : 0;
    this.tuning = tuning;
    this.stopping = stopping;
  }

  /**
   * Constructs a watcher with a separate listener.
   *
   * @param resourceVersion the oldest version to return for this watch
   * @param tuning Watch tuning parameters
   * @param stopping an atomic boolean to watch to determine when to stop the watcher
   * @param listener a listener to which to dispatch watch events
   */
  Watcher(
      String resourceVersion,
      WatchTuning tuning,
      AtomicBoolean stopping,
      WatchListener<T> listener) {
    this(resourceVersion, tuning, stopping);
    this.listener = listener;
  }

  /** Waits for this watcher's thread to exit. For unit testing only. */
  void waitForExit() {
    try {
      if (thread != null) {
        thread.join();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Sets the listener for watch events.
   *
   * @param listener the instance which should receive watch events
   */
  void setListener(WatchListener<T> listener) {
    this.listener = listener;
  }

  /** Kick off the watcher processing that runs in a separate thread. */
  void start(ThreadFactory factory) {
    thread = factory.newThread(this::doWatch);
    thread.start();
  }

  private void doWatch() {
    setIsDraining(false);

    while (!isDraining()) {
      if (isStopping()) {
        setIsDraining(true);
      } else {
        watchForEvents();
      }
    }
  }

  // Are we draining?
  private boolean isDraining() {
    return isDraining.get();
  }

  // Set the draining state.
  private void setIsDraining(boolean isDraining) {
    this.isDraining.set(isDraining);
  }

  protected boolean isStopping() {
    return stopping.get();
  }

  private void watchForEvents() {
    long now = System.currentTimeMillis();
    long delay = (tuning.watchMinimumDelay * 1000) - (now - lastInitialize);
    if (lastInitialize != 0 && delay > 0) {
      try {
        Thread.sleep(delay);
      } catch (InterruptedException ex) {
        LOGGER.warning(MessageKeys.EXCEPTION, ex);
        Thread.currentThread().interrupt();
      }
      lastInitialize = System.currentTimeMillis();
    } else {
      lastInitialize = now;
    }
    try (WatchI<T> watch =
        initiateWatch(
            new WatchBuilder()
                .withResourceVersion(resourceVersion.toString())
                .withTimeoutSeconds(tuning.watchLifetime))) {
      while (watch.hasNext()) {
        Watch.Response<T> item = watch.next();

        if (isStopping()) {
          setIsDraining(true);
        }
        if (isDraining()) {
          continue;
        }

        if (isError(item)) {
          handleErrorResponse(item);
        } else {
          handleRegularUpdate(item);
        }
      }
    } catch (Throwable ex) {
      LOGGER.warning(MessageKeys.EXCEPTION, ex);
    }
  }

  /**
   * Initiates a watch by using the watch builder to request any updates for the specified watcher.
   *
   * @param watchBuilder the watch builder, initialized with the current resource version.
   * @return Watch object or null if the operation should end
   * @throws ApiException if there is an API error.
   */
  public abstract WatchI<T> initiateWatch(WatchBuilder watchBuilder) throws ApiException;

  private boolean isError(Watch.Response<T> item) {
    return item.type.equalsIgnoreCase("ERROR");
  }

  private void handleRegularUpdate(Watch.Response<T> item) {
    LOGGER.fine(MessageKeys.WATCH_EVENT, item.type, item.object);
    trackResourceVersion(item.type, item.object);
    if (listener != null) {
      listener.receivedResponse(item);
    }
  }

  private void handleErrorResponse(Watch.Response<T> item) {
    V1Status status = item.status;
    if (status != null && status.getCode() == HTTP_GONE) {
      String message = status.getMessage();
      int index1 = message.indexOf('(');
      if (index1 > 0) {
        int index2 = message.indexOf(')', index1 + 1);
        if (index2 > 0) {
          String val = message.substring(index1 + 1, index2);
          resourceVersion = !isNullOrEmptyString(val) ? Long.parseLong(val) : 0;
        }
      }
    }
  }

  /**
   * Track resourceVersion and keep highest one for next watch iteration. The resourceVersion is
   * extracted from the metadata in the class by a getter written to return that information. If the
   * getter is not defined then the user will get all watches repeatedly.
   *
   * @param type the type of operation
   * @param object the object that is returned
   */
  private void trackResourceVersion(String type, Object object) {
    updateResourceVersion(getNewResourceVersion(type, object));
  }

  private long getNewResourceVersion(String type, Object object) {
    long newResourceVersion = getResourceVersionFromMetadata(object);
    if (type.equalsIgnoreCase("DELETED")) {
      return 1 + newResourceVersion;
    } else {
      return newResourceVersion;
    }
  }

  private long getResourceVersionFromMetadata(Object object) {
    try {
      Method getMetadata = object.getClass().getDeclaredMethod("getMetadata");
      V1ObjectMeta metadata = (V1ObjectMeta) getMetadata.invoke(object);
      String val = metadata.getResourceVersion();
      return !isNullOrEmptyString(val) ? Long.parseLong(val) : 0;
    } catch (Exception e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return IGNORED_RESOURCE_VERSION;
    }
  }

  private void updateResourceVersion(long newResourceVersion) {
    if (resourceVersion == 0) {
      resourceVersion = newResourceVersion;
    } else if (newResourceVersion > resourceVersion) {
      resourceVersion = newResourceVersion;
    }
  }

  private static boolean isNullOrEmptyString(String s) {
    return s == null || s.equals("");
  }
}
