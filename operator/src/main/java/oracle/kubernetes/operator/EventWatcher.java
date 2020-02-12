// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Event;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles Domain watching. It receives domain events and sends them into the operator
 * for processing.
 */
public class EventWatcher extends Watcher<V1Event> {
  private final String ns;
  private final String fieldSelector;

  private EventWatcher(
      String ns,
      String fieldSelector,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Event> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
    this.fieldSelector = fieldSelector;
  }

  /**
   * Create and start a new EventWatcher.
   * @param factory thread factory to use for this watcher's threads
   * @param ns namespace
   * @param fieldSelector value for the fieldSelector parameter
   * @param initialResourceVersion the oldest version to return for this watch
   * @param tuning Watch tuning parameters
   * @param listener a listener to which to dispatch watch events
   * @param isStopping an atomic boolean to watch to determine when to stop the watcher
   * @return the domain watcher
   */
  public static EventWatcher create(
      ThreadFactory factory,
      String ns,
      String fieldSelector,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Event> listener,
      AtomicBoolean isStopping) {
    EventWatcher watcher =
        new EventWatcher(ns, fieldSelector, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public WatchI<V1Event> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder.withFieldSelector(fieldSelector).createEventWatch(ns);
  }
}
