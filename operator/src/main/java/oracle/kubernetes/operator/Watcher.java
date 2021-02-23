// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;

import static java.net.HttpURLConnection.HTTP_GONE;
import static oracle.kubernetes.utils.OperatorUtils.isNullOrEmpty;

/**
 * This class handles the Watching interface and drives the watch support for a specific type of
 * object. It runs in a separate thread to drive watching asynchronously to the main thread.
 *
 * @param <T> The type of the object to be watched.
 */
abstract class Watcher<T> {
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"}) // not final so unit tests can set it
  private static WatcherStarter STARTER = Watcher::startAsynchronousWatch;

  static final String HAS_NEXT_EXCEPTION_MESSAGE = "IO Exception during hasNext method.";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String IGNORED = "0";
  private static final Pattern RESOURCE_VERSION_PATTERN = Pattern.compile("\\((\\d+)\\)");

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

  // for test
  String getResourceVersion() {
    return resourceVersion;
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
    thread = STARTER.startWatcher(factory, this::doWatch);
  }

  public static Thread startAsynchronousWatch(ThreadFactory factory, Runnable doWatch) {
    final Thread thread = factory.newThread(doWatch);
    thread.start();
    return thread;
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
    long delay = (getWatchMinimumDelay() * 1000L) - (now - lastInitialize);
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
    try (Watchable<T> watch =
        initiateWatch(
            new WatchBuilder()
                .withResourceVersion(resourceVersion)
                .withTimeoutSeconds(getWatchLifetime()))) {
      while (hasNext(watch)) {
        Watch.Response<T> item = watch.next();

        if (isStopping()) {
          setIsDraining(true);
        }
        if (isDraining()) {
          continue;
        }

        try (LoggingContext ignored =
                 LoggingContext.setThreadContext().namespace(getNamespace()).domainUid(getDomainUid(item))) {
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

  private int getWatchLifetime() {
    return Optional.ofNullable(tuning).map(t -> t.watchLifetime).orElse(5);
  }

  private int getWatchMinimumDelay() {
    return Optional.ofNullable(tuning).map(t -> t.watchMinimumDelay).orElse(1);
  }

  private boolean hasNext(Watchable<T> watch) {
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
  public abstract Watchable<T> initiateWatch(WatchBuilder watchBuilder) throws ApiException;

  /**
   * Gets the Kubernetes namespace associated with the watcher.
   *
   * @return String object or null if the watcher is not namespaced
   */
  public abstract String getNamespace();

  /**
   * Gets the domainUID associated with a watch response.
   *
   * @param item Response item
   * @return String object or null if the watch response is not associated with a domain
   */
  public abstract String getDomainUid(Watch.Response<T> item);

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
    if (Optional.ofNullable(item.status).map(V1Status::getCode).orElse(0) != HTTP_GONE) {
      resourceVersion = IGNORED;
    } else {
      resourceVersion = Optional.of(item.status).map(V1Status::getMessage).map(this::resourceVersion).orElse(IGNORED);
    }
  }

  private String resourceVersion(String message) {
    final Matcher matcher = RESOURCE_VERSION_PATTERN.matcher(message);
    return matcher.find() ? matcher.group(1) : null;
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
              .map(V1ObjectMeta::getResourceVersion).orElse(IGNORED);
    } catch (Exception e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return IGNORED;
    }
  }

  private void updateResourceVersion(String newResourceVersion) {
    if (isNullOrEmpty(resourceVersion) || resourceVersion.equals(IGNORED)) {
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
