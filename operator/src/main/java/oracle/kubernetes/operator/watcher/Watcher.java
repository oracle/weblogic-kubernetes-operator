// Copyright (c) 2017, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.calls.Client;
import oracle.kubernetes.operator.calls.KubernetesApiAuthenticationHealth;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_GONE;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAUTHORIZED;

/**
 * This class handles the Watching interface and drives the watch support for a specific type of
 * object. It runs in a separate thread to drive watching asynchronously to the main thread.
 *
 * @param <T> The type of the object to be watched.
 */
public abstract class Watcher<T> {
  static final String HAS_NEXT_EXCEPTION_MESSAGE = "IO Exception during hasNext method.";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String IGNORED = "0";
  private static final Pattern RESOURCE_VERSION_PATTERN = Pattern.compile("\\((\\d+)\\)");

  private final AtomicBoolean isDraining = new AtomicBoolean(false);
  private final WatchTuning tuning;

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"}) // not final so unit tests can set it
  private static WatcherStarter starter = Watcher::startAsynchronousWatch;

  private String resourceVersion;
  private final AtomicBoolean stopping;
  private WatchListener<T> listener;
  private Thread thread = null;
  private long lastInitialize = 0;
  private long watchAttempt;

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
  protected Watcher(
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
   * Updates the resource version used to initiate the next watch.
   *
   * @param resourceVersion the oldest version to return for the watch
   * @return this watcher
   */
  public Watcher<T> withResourceVersion(String resourceVersion) {
    String previousResourceVersion = this.resourceVersion;
    this.resourceVersion = resourceVersion;
    if (isPodWatcherTraceEnabled()) {
      trace("resource-version-update", "previousResourceVersion=" + previousResourceVersion);
    }
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
    if (isPodWatcherTraceEnabled()) {
      trace("start-request", "");
    }
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
    boolean tracePodWatcher = isPodWatcherTraceEnabled();
    if (tracePodWatcher) {
      trace("thread-enter", "");
    }
    try {
      setIsDraining(false);

      while (!isDraining()) {
        if (isStopping()) {
          if (tracePodWatcher) {
            trace("drain-observed", "reason=stopping");
          }
          setIsDraining(true);
        } else {
          setIsDraining(false);
          watchForEvents();
        }
      }
    } finally {
      if (tracePodWatcher) {
        trace("thread-exit", "watchAttempts=" + watchAttempt);
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

  /** Sets the stopping state to true to pause watches. */
  public void pause() {
    if (isPodWatcherTraceEnabled()) {
      boolean previouslyStopping = this.stopping.getAndSet(true);
      trace("pause", "previouslyStopping=" + previouslyStopping);
    } else {
      this.stopping.set(true);
    }
  }

  /** Sets the stopping state to false to resume watches. */
  public void resume() {
    if (isPodWatcherTraceEnabled()) {
      boolean previouslyStopping = this.stopping.getAndSet(false);
      trace("resume", "previouslyStopping=" + previouslyStopping);
    } else {
      this.stopping.set(false);
    }
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

    boolean tracePodWatcher = isPodWatcherTraceEnabled();
    long attempt = tracePodWatcher ? ++watchAttempt : 0;
    long startNanos = tracePodWatcher ? System.nanoTime() : 0;
    int eventCount = 0;
    String outcome = "not-opened";
    if (tracePodWatcher) {
      trace("watch-open-start", "attempt=" + attempt + " watchLifetimeSeconds=" + getWatchLifetime());
    }
    try (Watchable<T> watch =
        initiateWatch(
            new ListOptions()
                .resourceVersion(resourceVersion)
                .timeoutSeconds(getWatchLifetime()))) {
      outcome = "opened";
      if (tracePodWatcher) {
        trace("watch-open-complete", "attempt=" + attempt + " watchNull=" + (watch == null));
      }
      KubernetesApiAuthenticationHealth.reportSuccessfulResponse();
      while (hasNext(watch, attempt)) {
        Watch.Response<T> item = watch.next();
        setIsDraining(isStopping());
        if (isDraining()) {
          if (tracePodWatcher) {
            trace(
                "event-skipped",
                "reason=draining attempt=" + attempt + " event=" + item.type + getPodEventDetails(item));
          }
          continue;
        }

        if (tracePodWatcher) {
          eventCount++;
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
      outcome = "stream-ended";
    } catch (Throwable ex) {
      outcome = "failure";
      resetApiClientIfUnauthorized(ex);
      if (tracePodWatcher) {
        traceFailure("watch-failure", attempt, ex);
      }
      LOGGER.warning(MessageKeys.EXCEPTION, ex);
    } finally {
      if (tracePodWatcher) {
        trace(
            "watch-close",
            "outcome=" + outcome
                + " attempt=" + attempt
                + " eventCount=" + eventCount
                + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
      }
    }
  }

  private void resetApiClientIfUnauthorized(Throwable ex) {
    if (ex instanceof ApiException apiException && apiException.getCode() == HTTP_UNAUTHORIZED) {
      KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(
          getClass().getSimpleName() + " in namespace " + getNamespace() + ": "
              + Optional.ofNullable(apiException.getResponseBody())
                  .filter(responseBody -> !responseBody.isBlank())
                  .orElseGet(apiException::getMessage));
      Client.reset();
    }
  }

  private int getWatchLifetime() {
    return tuning.getWatchLifetime();
  }

  private int getWatchMinimumDelay() {
    return tuning.getWatchMinimumDelay();
  }

  private boolean hasNext(Watchable<T> watch, long attempt) {
    try {
      return watch.hasNext();
    } catch (Exception ex) {
      resetApiClientIfUnauthorized(ex);
      if (isPodWatcherTraceEnabled()) {
        traceFailure("has-next-failure", attempt, ex);
      }
      return false;
    }
  }

  private boolean isPodWatcherTraceEnabled() {
    return this instanceof PodWatcher && LOGGER.isFineEnabled();
  }

  private String getPodEventDetails(Watch.Response<T> item) {
    if (!(item.object instanceof V1Pod pod)) {
      return "";
    }
    V1ObjectMeta metadata = pod.getMetadata();
    return " domainUid=" + getLabel(metadata, LabelConstants.DOMAINUID_LABEL)
        + " server=" + getLabel(metadata, LabelConstants.SERVERNAME_LABEL)
        + " pod=" + Optional.ofNullable(metadata).map(V1ObjectMeta::getName).orElse(null)
        + " podResourceVersion=" + Optional.ofNullable(metadata).map(V1ObjectMeta::getResourceVersion).orElse(null)
        + " node=" + Optional.ofNullable(pod.getSpec()).map(s -> s.getNodeName()).orElse(null);
  }

  private String getLabel(V1ObjectMeta metadata, String labelName) {
    return Optional.ofNullable(metadata)
        .map(V1ObjectMeta::getLabels)
        .map(labels -> labels.get(labelName))
        .orElse(null);
  }

  private void traceFailure(String phase, long attempt, Throwable throwable) {
    ApiException apiException = findApiException(throwable);
    trace(
        phase,
        "attempt=" + attempt
            + " throwable=" + throwable.getClass().getName()
            + " cause=" + getRootCause(throwable).getClass().getName()
            + " apiCode=" + (apiException == null ? null : apiException.getCode())
            + " message=" + getExceptionMessage(throwable));
  }

  private ApiException findApiException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null && current.getCause() != current) {
      if (current instanceof ApiException apiException) {
        return apiException;
      }
      current = current.getCause();
    }
    return current instanceof ApiException apiException ? apiException : null;
  }

  private Throwable getRootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private String getExceptionMessage(Throwable throwable) {
    String message = Optional.ofNullable(throwable.getMessage()).orElse("");
    String singleLineMessage = message.replace('\n', ' ').replace('\r', ' ');
    return singleLineMessage.length() <= 512 ? singleLineMessage : singleLineMessage.substring(0, 512);
  }

  private void trace(String phase, String details) {
    if (!LOGGER.isFineEnabled()) {
      return;
    }
    Thread watchThread = thread;
    LOGGER.fine(
        "WKO-POD-STARTUP-TRACE component=watcher-lifecycle phase=" + phase
            + " watcherType=" + getClass().getSimpleName()
            + " watcher=" + Integer.toHexString(System.identityHashCode(this))
            + " namespace=" + getNamespace()
            + " resourceVersion=" + resourceVersion
            + " stopping=" + isStopping()
            + " draining=" + isDraining()
            + " stopSignal=" + Integer.toHexString(System.identityHashCode(stopping))
            + " watchThreadId=" + (watchThread == null ? null : watchThread.threadId())
            + " watchThreadAlive=" + (watchThread != null && watchThread.isAlive())
            + " watchThreadState=" + (watchThread == null ? null : watchThread.getState())
            + " currentThreadId=" + Thread.currentThread().threadId()
            + (details.isEmpty() ? "" : " " + details));
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

  private void handleRegularUpdate(Watch.Response<T> item) {
    LOGGER.finer(MessageKeys.WATCH_EVENT, item.type, item.object);
    String previousResourceVersion = resourceVersion;
    trackResourceVersion(item.object);
    if (this instanceof PodWatcher) {
      trace(
          "event-received",
          "event=" + item.type
              + " previousResourceVersion=" + previousResourceVersion
              + " listenerPresent=" + (listener != null));
    }
    if (listener != null) {
      listener.receivedResponse(item);
    }
  }

  private void handleErrorResponse(Watch.Response<T> item) {
    String previousResourceVersion = resourceVersion;
    Integer statusCode = Optional.ofNullable(item.status).map(V1Status::getCode).orElse(null);
    if (Optional.ofNullable(item.status).map(V1Status::getCode).orElse(0) != HTTP_GONE) {
      resourceVersion = IGNORED;
    } else {
      resourceVersion = Optional.of(item.status).map(V1Status::getMessage).map(this::resourceVersion).orElse(IGNORED);
    }
    if (isPodWatcherTraceEnabled()) {
      trace(
          "error-event",
          "statusCode=" + statusCode + " previousResourceVersion=" + previousResourceVersion);
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
