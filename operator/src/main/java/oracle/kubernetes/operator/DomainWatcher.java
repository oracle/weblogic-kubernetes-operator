// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.weblogic.domain.model.Domain;

/**
 * This class handles Domain watching. It receives domain events and sends them into the operator
 * for processing.
 */
public class DomainWatcher extends Watcher<Domain> {
  private final String ns;

  private DomainWatcher(
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<Domain> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create domain watcher.
   * @param factory thread factory
   * @param ns namespace
   * @param initialResourceVersion initial resource version
   * @param tuning tuning parameter
   * @param listener listener
   * @param isStopping stopping flag
   * @return watcher
   */
  public static DomainWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<Domain> listener,
      AtomicBoolean isStopping) {
    DomainWatcher watcher =
        new DomainWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<Domain> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder.createDomainWatch(ns);
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Response<Domain> item) {
    return Optional.ofNullable(item.object).map(Domain::getDomainUid).orElse(null);
  }
}
