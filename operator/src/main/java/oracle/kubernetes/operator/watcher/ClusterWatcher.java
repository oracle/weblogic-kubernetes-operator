// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;

/**
 * This class handles Cluster watching. It receives cluster events and sends them into the operator
 * for processing.
 */
public class ClusterWatcher extends Watcher<ClusterResource> {
  private final String ns;

  private ClusterWatcher(
      CoreDelegate delegate,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<ClusterResource> listener,
      AtomicBoolean isStopping) {
    super(delegate, initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create cluster watcher.
   * @param delegate Delegate
   * @param factory thread factory
   * @param ns namespace
   * @param initialResourceVersion initial resource version
   * @param tuning tuning parameter
   * @param listener listener
   * @param isStopping stopping flag
   * @return watcher
   */
  public static ClusterWatcher create(
      CoreDelegate delegate,
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<ClusterResource> listener,
      AtomicBoolean isStopping) {
    ClusterWatcher watcher =
        new ClusterWatcher(delegate, ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<ClusterResource> initiateWatch(ListOptions options) throws ApiException {
    return delegate.getClusterBuilder().watch(ns, options);
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Response<ClusterResource> item) {
    return null;
  }
}
