// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles ConfigMap watching. It receives config map change events and sends them into
 * the operator for processing.
 */
public class ConfigMapWatcher extends Watcher<V1ConfigMap> {
  private final String ns;

  private ConfigMapWatcher(
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1ConfigMap> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create watcher.
   * @param factory thread factory
   * @param ns namespace
   * @param initialResourceVersion initial resource version
   * @param tuning tuning parameters
   * @param listener listener
   * @param isStopping stopping flag
   * @return watcher
   */
  public static ConfigMapWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1ConfigMap> listener,
      AtomicBoolean isStopping) {
    ConfigMapWatcher watcher =
        new ConfigMapWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public WatchI<V1ConfigMap> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelector(LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createConfigMapWatch(ns);
  }
}
