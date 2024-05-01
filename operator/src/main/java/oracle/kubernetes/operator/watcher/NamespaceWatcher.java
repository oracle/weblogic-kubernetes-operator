// Copyright (c) 2019, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.calls.RequestBuilder;

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

    NamespaceWatcher watcher = new NamespaceWatcher(initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<V1Namespace> initiateWatch(ListOptions options) throws ApiException {
    return RequestBuilder.NAMESPACE.watch(options);
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
