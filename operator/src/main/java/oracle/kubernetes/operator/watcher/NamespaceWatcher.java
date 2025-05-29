// Copyright (c) 2019, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.WatchTuning;

/**
 * This class handles Namespace watching. It receives Namespace change events and sends them into
 * the operator for processing.
 */
public class NamespaceWatcher extends Watcher<V1Namespace> {

  private NamespaceWatcher(
      CoreDelegate delegate,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {
    super(delegate, initialResourceVersion, tuning, isStopping, listener);
  }

  /**
   * Create a namespace watcher.
   * @param delegate Delegate
   * @param factory the ThreadFactory to run the watcher
   * @param initialResourceVersion at which to start returning watch events
   * @param tuning any WatchTuning parameters
   * @param listener the WatchListener
   * @param isStopping whether the watcher is stopping
   * @return the watcher
   */
  public static NamespaceWatcher create(
      CoreDelegate delegate,
      ThreadFactory factory,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {

    NamespaceWatcher watcher = new NamespaceWatcher(delegate, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<V1Namespace> initiateWatch(ListOptions options) throws ApiException {
    return delegate.getNamespaceBuilder().watch(options);
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
