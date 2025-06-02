// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.NamespacedResourceCache;
import oracle.kubernetes.operator.ResourceCache;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_GONE;

/**
 * This class handles the Watching interface and drives the watch support for a specific type of
 * object. It runs in a separate thread to drive watching asynchronously to the main thread.
 *
 * @param <T> The type of the object to be watched.
 */
public abstract class Watcher<T extends KubernetesObject> {
  static final String HAS_NEXT_EXCEPTION_MESSAGE = "IO Exception during hasNext method.";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String IGNORED = "0";
  private static final Pattern RESOURCE_VERSION_PATTERN = Pattern.compile("\\((\\d+)\\)");

  private final AtomicBoolean isDraining = new AtomicBoolean(false);
  private final WatchTuning tuning;
  protected final CoreDelegate delegate;

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"}) // not final so unit tests can set it
  private static WatcherStarter starter = Watcher::startAsynchronousWatch;

  private String resourceVersion;
  private final AtomicBoolean stopping;
  private WatchListener<T> listener;
  private Thread thread = null;
  private long lastInitialize = 0;

  /**
   * Constructs a watcher without specifying a listener. Needed when the listener is the watch
   * subclass itself.
   *
   * @param delegate Delegate
   * @param resourceVersion the oldest version to return for this watch
   * @param tuning Watch tuning parameters
   * @param stopping an atomic boolean to watch to determine when to stop the watcher
   */
  Watcher(CoreDelegate delegate, String resourceVersion, WatchTuning tuning, AtomicBoolean stopping) {
    this.delegate = delegate;
    this.resourceVersion = resourceVersion;
    this.tuning = tuning;
    this.stopping = stopping;
  }

  /**
   * Constructs a watcher with a separate listener.
   *
   * @param delegate Delegate
   * @param resourceVersion the oldest version to return for this watch
   * @param tuning Watch tuning parameters
   * @param stopping an atomic boolean to watch to determine when to stop the watcher
   * @param listener a listener to which to dispatch watch events
   */
  protected Watcher(
          CoreDelegate delegate,
          String resourceVersion,
          WatchTuning tuning,
          AtomicBoolean stopping,
          WatchListener<T> listener) {
    this(delegate, resourceVersion, tuning, stopping);
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

  public Watcher<T> withResourceVersion(String resourceVersion) {
    this.resourceVersion = resourceVersion;
    return this;
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
  protected void start(ThreadFactory factory) {
    thread = starter.startWatcher(factory, this::doWatch);
  }

  /**
   * Start asynchronous watch.
   * @param factory Thread factory
   * @param doWatch Watch runnable
   * @return the thread
   */
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
        setIsDraining(false);
        watchForEvents();
      }
    }
  }

  // Are we draining?
  private boolean isDraining() {
    return this.isDraining.get();
  }

  // Set the draining state.
  private void setIsDraining(boolean isDraining) {
    this.isDraining.set(isDraining);
  }

  protected boolean isStopping() {
    return this.stopping.get();
  }

  // Set the stopping state to true to pause watches.
  public void pause() {
    this.stopping.set(true);
  }

  // Set the stopping state to false to resume watches.
  public void resume() {
    this.stopping.set(false);
  }

  @SuppressWarnings("try")
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
            new ListOptions()
                .resourceVersion(resourceVersion)
                .timeoutSeconds(getWatchLifetime()))) {
      while (hasNext(watch)) {
        Watch.Response<T> item = watch.next();
        setIsDraining(isStopping());
        if (isDraining()) {
          continue;
        }

        try (ThreadLoggingContext ignored =
                 ThreadLoggingContext.setThreadContext().namespace(getNamespace()).domainUid(getDomainUid(item))) {
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
    return tuning.getWatchLifetime();
  }

  private int getWatchMinimumDelay() {
    return tuning.getWatchMinimumDelay();
  }

  private boolean hasNext(Watchable<T> watch) {
    try {
      return watch.hasNext();
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Initiates a watch by using the watch builder to request any updates for the specified watcher.
   *
   * @param options options, initialized with the current resource version.
   * @return Watch object or null if the operation should end
   * @throws ApiException if there is an API error.
   */
  public abstract Watchable<T> initiateWatch(ListOptions options) throws ApiException;

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

  @SuppressWarnings("unchecked")
  private void handleRegularUpdate(Watch.Response<T> item) {
    LOGGER.finer(MessageKeys.WATCH_EVENT, item.type, item.object);
    trackResourceVersion(item.object);

    // HERE: Is this the correct place to update ResourceCache?
    ResourceCache resourceCache = delegate.getResourceCache();
    KubernetesObject res = item.object;
    switch (item.type) {
      case "ADDED", "MODIFIED":
        if (resourceCache != null && res != null) {
          Class<? extends KubernetesObject> cz = item.object.getClass();
          NamespacedResourceCache cache = Optional.ofNullable(getNamespace())
              .map(resourceCache::findNamespace).orElse(resourceCache);
          ConcurrentMap<String, KubernetesObject> resources
              = (ConcurrentMap<String, KubernetesObject>) cache.lookupByType(cz);
          if (resources != null) {
            resources.compute(res.getMetadata().getName(), (k, v) -> isFirstNewer(res, v) ? res : v);
          }
        }
        break;
      case "DELETED":
        if (resourceCache != null && res != null) {
          Class<? extends KubernetesObject> cz = item.object.getClass();
          NamespacedResourceCache cache = Optional.ofNullable(getNamespace())
                  .map(resourceCache::findNamespace).orElse(resourceCache);
          ConcurrentMap<String, KubernetesObject> resources
                  = (ConcurrentMap<String, KubernetesObject>) cache.lookupByType(cz);
          if (resources != null) {
            resources.remove(res.getMetadata().getName());
          }
        }
        break;
      case "ERROR":
      default:
    }

    if (listener != null) {
      listener.receivedResponse(item);
    }
  }

  protected static boolean isFirstNewer(@Nonnull KubernetesObject k1, KubernetesObject k2) {
    OffsetDateTime time1 = Optional.ofNullable(k1.getMetadata())
            .map(V1ObjectMeta::getCreationTimestamp).orElse(OffsetDateTime.MIN);
    OffsetDateTime time2 = Optional.ofNullable(k2).map(KubernetesObject::getMetadata)
            .map(V1ObjectMeta::getCreationTimestamp).orElse(OffsetDateTime.MIN);

    return time1.isAfter(time2);
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
   * Track resourceVersion and keep the latest one for next watch iteration. The resourceVersion is
   * extracted from the metadata in the class by a getter written to return that information. If the
   * getter is not defined then the user will get all watches repeatedly.
   *
   * @param object the object that is returned
   */
  private void trackResourceVersion(Object object) {
    resourceVersion = getResourceVersionFromMetadata(object);
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
}
