// Copyright (c) 2021, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.watcher.Watcher;

/**
 * This class handles pod disruption budget watching. It receives pod disruption budget change events and sends them
 * into the operator for processing.
 */
public class PodDisruptionBudgetWatcher extends Watcher<V1PodDisruptionBudget> {
  private final String ns;

  private PodDisruptionBudgetWatcher(
      CoreDelegate delegate,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1PodDisruptionBudget> listener,
      AtomicBoolean isStopping) {
    super(delegate, initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create pod disruption budget watcher.
   * @param delegate Delegate
   * @param factory thread factory
   * @param ns namespace
   * @param initialResourceVersion initial resource version
   * @param tuning tuning parameters
   * @param listener listener
   * @param isStopping stopping flag
   * @return watcher
   */
  public static PodDisruptionBudgetWatcher create(
      CoreDelegate delegate,
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1PodDisruptionBudget> listener,
      AtomicBoolean isStopping) {
    PodDisruptionBudgetWatcher watcher =
        new PodDisruptionBudgetWatcher(delegate, ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<V1PodDisruptionBudget> initiateWatch(ListOptions options) throws ApiException {
    return delegate.getPodDisruptionBudgetBuilder().watch(ns,
            options.labelSelector(LabelConstants.DOMAINUID_LABEL + "," + LabelConstants.CREATEDBYOPERATOR_LABEL));
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Response<V1PodDisruptionBudget> item) {
    return KubernetesUtils.getDomainUidLabel(
        Optional.ofNullable(item.object).map(V1PodDisruptionBudget::getMetadata).orElse(null));
  }
}
