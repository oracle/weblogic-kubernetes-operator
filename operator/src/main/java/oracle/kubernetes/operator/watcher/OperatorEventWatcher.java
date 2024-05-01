// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.calls.RequestBuilder;

import static oracle.kubernetes.operator.ProcessingConstants.OPERATOR_EVENT_LABEL_FILTER;

/**
 * This class handles Domain Event watching. It receives event notifications and sends them into the operator
 * for processing.
 */
public class OperatorEventWatcher extends Watcher<CoreV1Event> {
  private final String ns;

  private OperatorEventWatcher(
        String ns,
        String initialResourceVersion,
        WatchTuning tuning,
        WatchListener<CoreV1Event> listener,
        AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
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
  public static OperatorEventWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<CoreV1Event> listener,
      AtomicBoolean isStopping) {
    OperatorEventWatcher watcher =
        new OperatorEventWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<CoreV1Event> initiateWatch(ListOptions options) throws ApiException {
    return RequestBuilder.EVENT.watch(ns, options.labelSelector(OPERATOR_EVENT_LABEL_FILTER));
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Watch.Response<CoreV1Event> item) {
    return Optional.ofNullable(item.object)
        .map(CoreV1Event::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(l -> l.get(LabelConstants.DOMAINUID_LABEL))
        .orElse(null);
  }
}
