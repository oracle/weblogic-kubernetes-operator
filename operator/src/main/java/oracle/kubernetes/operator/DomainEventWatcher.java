// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.watcher.WatchListener;

import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_EVENT_LABEL_FILTER;

/**
 * This class handles Domain Event watching. It receives event notifications and sends them into the operator
 * for processing.
 */
public class DomainEventWatcher extends EventWatcher {

  private DomainEventWatcher(
        String ns,
        String initialResourceVersion,
        WatchTuning tuning,
        WatchListener<V1Event> listener,
        AtomicBoolean isStopping) {
    super(ns, initialResourceVersion, tuning, listener, isStopping);
  }

  /**
   * Create and start a new DomainEventWatcher.
   * @param factory thread factory to use for this watcher's threads
   * @param ns namespace
   * @param initialResourceVersion the oldest version to return for this watch
   * @param tuning Watch tuning parameters
   * @param listener a listener to which to dispatch watch events
   * @param isStopping an atomic boolean to watch to determine when to stop the watcher
   * @return the domain watcher
   */
  public static DomainEventWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Event> listener,
      AtomicBoolean isStopping) {
    DomainEventWatcher watcher =
        new DomainEventWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<V1Event> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder.withLabelSelector(DOMAIN_EVENT_LABEL_FILTER).createEventWatch(ns);
  }

}
