// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;

/**
 * This class handles Cluster watching. It receives cluster events and sends them into the operator
 * for processing.
 */
public class ClusterWatcher extends Watcher<ClusterResource> {
  private final String ns;

  private ClusterWatcher(
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<ClusterResource> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create cluster watcher.
   * @param factory thread factory
   * @param ns namespace
   * @param initialResourceVersion initial resource version
   * @param tuning tuning parameter
   * @param listener listener
   * @param isStopping stopping flag
   * @return watcher
   */
  public static ClusterWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<ClusterResource> listener,
      AtomicBoolean isStopping) {
    ClusterWatcher watcher =
        new ClusterWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<ClusterResource> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder.createClusterWatch(ns);
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
