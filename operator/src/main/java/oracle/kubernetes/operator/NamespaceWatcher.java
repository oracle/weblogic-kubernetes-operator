// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles Namespace watching. It receives Namespace change events and sends them into
 * the operator for processing.
 */
public class NamespaceWatcher extends Watcher<V1Namespace> {
  private final String[] labelSelectors;

  private NamespaceWatcher(
      String initialResourceVersion,
      String[] labelSelectors,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.labelSelectors = labelSelectors;
  }

  /**
   * Create a namespace watcher.
   * @param factory the ThreadFactory to run the watcher
   * @param initialResourceVersion at which to start returning watch events
   * @param labelSelector label selector
   * @param tuning any WatchTuning parameters
   * @param listener the WatchListener
   * @param isStopping whether the watcher is stopping
   * @return the watcher
   */
  public static NamespaceWatcher create(
      ThreadFactory factory,
      String initialResourceVersion,
      String[] labelSelector,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {

    NamespaceWatcher watcher =
        new NamespaceWatcher(initialResourceVersion, labelSelector, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<V1Namespace> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(labelSelectors)
        .createNamespacesWatch();
  }

  @Override
  public String getNamespace() {
    return null;
  }

  @Override
  public String getDomainUid(Watch.Response<V1Namespace> item) {
    return null;
  }
}
