// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

/**
 * This class handles Domain watching. It receives domain events and sends them into the operator
 * for processing.
 */
public class DomainWatcher extends Watcher<DomainResource> {
  private final String ns;

  private DomainWatcher(
      CoreDelegate delegate,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<DomainResource> listener,
      AtomicBoolean isStopping) {
    super(delegate, initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create domain watcher.
   * @param delegate Delegate
   * @param factory thread factory
   * @param ns namespace
   * @param initialResourceVersion initial resource version
   * @param tuning tuning parameter
   * @param listener listener
   * @param isStopping stopping flag
   * @return watcher
   */
  public static DomainWatcher create(
      CoreDelegate delegate,
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<DomainResource> listener,
      AtomicBoolean isStopping) {
    DomainWatcher watcher =
        new DomainWatcher(delegate, ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<DomainResource> initiateWatch(ListOptions options) throws ApiException {
    return delegate.getDomainBuilder().watch(ns, options);
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Response<DomainResource> item) {
    return Optional.ofNullable(item.object).map(DomainResource::getDomainUid).orElse(null);
  }
}
