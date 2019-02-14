// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1ConfigMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.ConfigMapWatcher;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ConfigMapAfterStep extends Step {
  private final String ns;
  private final Map<String, ConfigMapWatcher> configMapWatchers;
  private final WatchTuning tuning;
  private final AtomicBoolean stopping;
  private final WatchListener<V1ConfigMap> listener;

  public ConfigMapAfterStep(
      String ns,
      Map<String, ConfigMapWatcher> configMapWatchers,
      WatchTuning tuning,
      AtomicBoolean stopping,
      WatchListener<V1ConfigMap> listener) {
    this.ns = ns;
    this.configMapWatchers = configMapWatchers;
    this.tuning = tuning;
    this.stopping = stopping;
    this.listener = listener;
  }

  @Override
  public NextAction apply(Packet packet) {
    V1ConfigMap result = (V1ConfigMap) packet.get(ProcessingConstants.SCRIPT_CONFIG_MAP);
    if (!configMapWatchers.containsKey(ns)) {
      configMapWatchers.put(
          ns,
          createConfigMapWatcher(
              ns, result != null ? result.getMetadata().getResourceVersion() : ""));
    }
    return doNext(packet);
  }

  private ConfigMapWatcher createConfigMapWatcher(String namespace, String initialResourceVersion) {
    ThreadFactory factory =
        ContainerResolver.getInstance().getContainer().getSPI(ThreadFactory.class);

    return ConfigMapWatcher.create(
        factory, namespace, initialResourceVersion, tuning, listener, stopping);
  }
}
