// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Event;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
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

  @Override
  public WatchI<V1Event> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder.withFieldSelector(fieldSelector).createEventWatch(ns);
  }
}
