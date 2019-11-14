// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Namespace;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles Namespace watching. It receives Namespace change events and sends them into
 * the operator for processing.
 */
public class NamespaceWatcher extends Watcher<V1Namespace> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private NamespaceWatcher(
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
  }

  public static NamespaceWatcher create(
      ThreadFactory factory,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Namespace> listener,
      AtomicBoolean isStopping) {
    LOGGER.info(MessageKeys.ENTER_METHOD, "NamespaceWatch.create", "version = " + initialResourceVersion);

    NamespaceWatcher watcher =
        new NamespaceWatcher(initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public WatchI<V1Namespace> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        //.withLabelSelector(LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createNamespacesWatch();
  }
}
