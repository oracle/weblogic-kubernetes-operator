// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.weblogic.domain.model.Domain;

/**
 * This class handles Domain watching. It receives domain events and sends them into the operator
 * for processing.
 */
public class DomainWatcher extends Watcher<Domain> {
  private final String ns;

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

  private DomainWatcher(
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<Domain> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  @Override
  public WatchI<Domain> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder.createDomainWatch(ns);
  }
}
