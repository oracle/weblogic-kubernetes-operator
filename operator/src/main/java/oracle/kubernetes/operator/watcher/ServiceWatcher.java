// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.KubernetesUtils;

/**
 * This class handles Service watching. It receives service change events and sends them into the
 * operator for processing.
 */
public class ServiceWatcher extends Watcher<V1Service> {
  private final String ns;

  private ServiceWatcher(
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Service> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping, listener);
    this.ns = ns;
  }

  /**
   * Create service watcher.
   * @param factory thread factory
   * @param ns namespace
   * @param initialResourceVersion initial resource version
   * @param tuning tuning parameters
   * @param listener listener
   * @param isStopping stopping flag
   * @return watcher
   */
  public static ServiceWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Service> listener,
      AtomicBoolean isStopping) {
    ServiceWatcher watcher =
        new ServiceWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  @Override
  public Watchable<V1Service> initiateWatch(ListOptions options) throws ApiException {
    return RequestBuilder.SERVICE.watch(ns,
            options.labelSelector(LabelConstants.DOMAINUID_LABEL + "," + LabelConstants.CREATEDBYOPERATOR_LABEL));
  }

  @Override
  public String getNamespace() {
    return ns;
  }

  @Override
  public String getDomainUid(Response<V1Service> item) {
    return KubernetesUtils.getDomainUidLabel(
        Optional.ofNullable(item.object).map(V1Service::getMetadata).orElse(null));
  }
}
