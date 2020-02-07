// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles Namespace watching. It receives Namespace change events and sends them into
 * the operator for processing.
 */
public class NamespaceWatcher extends Watcher<V1Namespace> {

  private NamespaceWatcher(
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
  }

  /**
   * Create a namespace watcher.
   * @param factory the ThreadFactory to run the watcher
   * @param initialResourceVersion at which to start returning watch events
   * @param tuning any WatchTuning parameters
   * @param listener the WatchListener
   * @param isStopping whether the watcher is stopping
   * @return the watcher
   */
  public static NamespaceWatcher create(
      ThreadFactory factory,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {

    NamespaceWatcher watcher =
        new NamespaceWatcher(initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public WatchI<V1Namespace> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .createNamespacesWatch();
  }
}
