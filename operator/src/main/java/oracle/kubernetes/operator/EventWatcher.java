// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles Event watching. It receives event notifications and sends them into the operator
 * for processing.
 */
public class EventWatcher extends Watcher<CoreV1Event> {
  private static final String FIELD_SELECTOR = ProcessingConstants.READINESS_PROBE_FAILURE_EVENT_FILTER;
  
  protected final String ns;

  EventWatcher(
        String ns,
        String initialResourceVersion,
        WatchTuning tuning,
        WatchListener<CoreV1Event> listener,
        AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create and start a new EventWatcher.
   * @param factory thread factory to use for this watcher's threads
   * @param ns namespace
   * @param initialResourceVersion the oldest version to return for this watch
   * @param tuning Watch tuning parameters
   * @param listener a listener to which to dispatch watch events
   * @param isStopping an atomic boolean to watch to determine when to stop the watcher
   * @return the domain watcher
   */
  public static EventWatcher create(
        ThreadFactory factory,
        String ns,
        String initialResourceVersion,
        WatchTuning tuning,
        WatchListener<CoreV1Event> listener,
        AtomicBoolean isStopping) {
    EventWatcher watcher =
        new EventWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<CoreV1Event> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder.withFieldSelector(FIELD_SELECTOR).createEventWatch(ns);
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Response<CoreV1Event> item) {
    return null;
  }
}
