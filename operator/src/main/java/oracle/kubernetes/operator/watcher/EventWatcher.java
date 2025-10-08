// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.calls.RequestBuilder;

/**
 * This class handles Event watching. It receives event notifications and sends them into the operator
 * for processing.
 */
public class EventWatcher extends Watcher<EventsV1Event> {
  private static final String FIELD_SELECTOR = ProcessingConstants.READINESS_PROBE_FAILURE_EVENT_FILTER;
  
  protected final String ns;

  EventWatcher(
        String ns,
        String initialResourceVersion,
        WatchTuning tuning,
        WatchListener<EventsV1Event> listener,
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
        WatchListener<EventsV1Event> listener,
        AtomicBoolean isStopping) {
    EventWatcher watcher =
        new EventWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<EventsV1Event> initiateWatch(ListOptions options) throws ApiException {
    return RequestBuilder.EVENT.watch(ns, options.fieldSelector(FIELD_SELECTOR));
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Response<EventsV1Event> item) {
    return null;
  }
}
