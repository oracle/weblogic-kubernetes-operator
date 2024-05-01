// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.PodHelper;

/**
 * Watches for changes to pods.
 */
public class PodWatcher extends Watcher<V1Pod> implements WatchListener<V1Pod> {
  private final String namespace;
  private final WatchListener<V1Pod> listener;

  // Map of Pod name to callback. Note that since each pod name can be mapped to multiple callback registrations,
  // a concurrent map will not suffice; we therefore use an ordinary map and synchronous accesses.
  private final Map<String, Collection<Consumer<V1Pod>>> modifiedCallbackRegistrations = new HashMap<>();
  private final Map<String, Collection<Consumer<V1Pod>>> deletedCallbackRegistrations = new HashMap<>();

  private PodWatcher(
      String namespace,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Pod> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping);
    setListener(this);
    this.namespace = namespace;
    this.listener = listener;
  }

  /**
   * Factory for PodWatcher.
   *
   * @param factory thread factory
   * @param ns Namespace
   * @param initialResourceVersion Initial resource version or empty string
   * @param tuning Watch tuning parameters
   * @param listener Callback for watch events
   * @param isStopping Stop signal
   * @return Pod watcher for the namespace
   */
  public static PodWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Pod> listener,
      AtomicBoolean isStopping) {
    PodWatcher watcher = new PodWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  private @Nonnull Collection<Consumer<V1Pod>> getOnModifiedCallbacks(String podName) {
    synchronized (modifiedCallbackRegistrations) {
      return Optional.ofNullable(modifiedCallbackRegistrations.get(podName)).orElse(Collections.emptyList());
    }
  }

  private @Nonnull Collection<Consumer<V1Pod>> getOnDeleteCallbacks(String podName) {
    synchronized (deletedCallbackRegistrations) {
      return Optional.ofNullable(deletedCallbackRegistrations.remove(podName)).orElse(Collections.emptyList());
    }
  }

  @Override
  public Watchable<V1Pod> initiateWatch(ListOptions options) throws ApiException {
    return RequestBuilder.POD.watch(namespace,
            options.labelSelector(LabelConstants.DOMAINUID_LABEL + "," + LabelConstants.CREATEDBYOPERATOR_LABEL));
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  @Override
  public String getDomainUid(Watch.Response<V1Pod> item) {
    return KubernetesUtils.getDomainUidLabel(
        Optional.ofNullable(item.object).map(V1Pod::getMetadata).orElse(null));
  }

  /**
   * Receive response.
   * @param item item
   */
  public void receivedResponse(Watch.Response<V1Pod> item) {
    listener.receivedResponse(item);

    V1Pod pod = item.object;
    switch (item.type) {
      case "ADDED", "MODIFIED":
        copyOf(getOnModifiedCallbacks(PodHelper.getPodName(pod))).forEach(c -> c.accept(pod));
        break;
      case "DELETED":
        getOnDeleteCallbacks(PodHelper.getPodName(pod)).forEach(c -> c.accept(pod));
        break;
      case "ERROR":
      default:
    }
  }

  // make a copy to avoid concurrent modification
  private <T> Collection<T> copyOf(Collection<T> collection) {
    return new ArrayList<>(collection);
  }

}
