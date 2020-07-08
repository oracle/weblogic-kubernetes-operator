// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.net.HttpURLConnection.HTTP_GONE;

/**
 * This class handles the Watching interface and drives the watch support for a specific type of
 * object. It runs in a separate thread to drive watching asynchronously to the main thread.
 *
 * @param <T> The type of the object to be watched.
 */
abstract class Watcher<T> {
  static final String HAS_NEXT_EXCEPTION_MESSAGE = "IO Exception during hasNext method.";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String IGNORED_RESOURCE_VERSION = "0";

  private final AtomicBoolean isDraining = new AtomicBoolean(false);
  private final WatchTuning tuning;
  private String resourceVersion;
  private final AtomicBoolean stopping;
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
    this.resourceVersion = resourceVersion;
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
                .withResourceVersion(resourceVersion)
                .withTimeoutSeconds(tuning.watchLifetime))) {
      while (hasNext(watch)) {
        Watch.Response<T> item = watch.next();

        if (isStopping()) {
          setIsDraining(true);
        }
        if (isDraining()) {
          continue;
        }

        try (LoggingContext stack = LoggingContext.setThreadContext().namespace(getNamespace())) {
          if (isError(item)) {
            handleErrorResponse(item);
          } else {
            handleRegularUpdate(item);
          }
        }
      }
    } catch (Throwable ex) {
      LOGGER.warning(MessageKeys.EXCEPTION, ex);
    }
  }

  private boolean hasNext(WatchI<T> watch) {
    try {
      return watch.hasNext();
    } catch (Throwable ex) {
      // no-op on exception during hasNext
    }
    return false;
  }

  /**
   * Initiates a watch by using the watch builder to request any updates for the specified watcher.
   *
   * @param watchBuilder the watch builder, initialized with the current resource version.
   * @return Watch object or null if the operation should end
   * @throws ApiException if there is an API error.
   */
  public abstract WatchI<T> initiateWatch(WatchBuilder watchBuilder) throws ApiException;

  /**
   * Gets the Kubernetes namespace associated with the watcher.
   *
   * @return String object or null if the watcher is not namespaced
   */
  public abstract String getNamespace();

  private boolean isError(Watch.Response<T> item) {
    return item.type.equalsIgnoreCase("ERROR");
  }

  private void handleRegularUpdate(Watch.Response<T> item) {
    LOGGER.finer(MessageKeys.WATCH_EVENT, item.type, item.object);
    trackResourceVersion(item.type, item.object);
    if (listener != null) {
      listener.receivedResponse(item);
    }
  }

  private void handleErrorResponse(Watch.Response<T> item) {
    V1Status status = item.status;
    if (status == null) {
      // The kubernetes client parsing logic can mistakenly parse a status as a type
      // with similar fields, such as V1ConfigMap. In this case, the actual status is
      // not available to our layer, so respond defensively by resetting resource version.
      resourceVersion = IGNORED_RESOURCE_VERSION;
    } else if (status.getCode() == HTTP_GONE) {
      resourceVersion = computeNextResourceVersionFromMessage(status);
    }
  }

  private String computeNextResourceVersionFromMessage(V1Status status) {
    String message = status.getMessage();
    if (message != null) {
      int index1 = message.indexOf('(');
      if (index1 > 0) {
        int index2 = message.indexOf(')', index1 + 1);
        if (index2 > 0) {
          return message.substring(index1 + 1, index2);
        }
      }
    }
    return IGNORED_RESOURCE_VERSION;
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

  private String getNewResourceVersion(String type, Object object) {
    String newResourceVersion = getResourceVersionFromMetadata(object);
    if (type.equalsIgnoreCase("DELETED")) {
      BigInteger biResourceVersion = KubernetesUtils.getResourceVersion(newResourceVersion);
      if (biResourceVersion.compareTo(BigInteger.ZERO) > 0) {
        return biResourceVersion.add(BigInteger.ONE).toString();
      }
    }
    return newResourceVersion;
  }

  private String getResourceVersionFromMetadata(Object object) {
    try {
      Method getMetadata = object.getClass().getDeclaredMethod("getMetadata");
      return Optional.ofNullable((V1ObjectMeta) getMetadata.invoke(object))
              .map(V1ObjectMeta::getResourceVersion).orElse(IGNORED_RESOURCE_VERSION);
    } catch (Exception e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return IGNORED_RESOURCE_VERSION;
    }
  }

  private void updateResourceVersion(String newResourceVersion) {
    if (isNullOrEmpty(resourceVersion) || resourceVersion.equals(IGNORED_RESOURCE_VERSION)) {
      resourceVersion = newResourceVersion;
    } else {
      BigInteger biNewResourceVersion = KubernetesUtils.getResourceVersion(newResourceVersion);
      BigInteger biResourceVersion = KubernetesUtils.getResourceVersion(resourceVersion);
      if (biNewResourceVersion.compareTo(biResourceVersion) > 0) {
        resourceVersion = newResourceVersion;
      }
    }
  }
}
