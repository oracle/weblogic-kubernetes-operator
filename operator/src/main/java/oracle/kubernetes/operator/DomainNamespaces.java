// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1EventList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.helpers.KubernetesUtils.getResourceVersion;

/**
 * This class represents the information the operator maintains for a namespace used to maintain
 * one or more domains.
 */
@SuppressWarnings("SameParameterValue")
public class DomainNamespaces {
  private static final Map<String, NamespaceStatus> namespaceStatuses = new ConcurrentHashMap<>();
  private static final Map<String, AtomicBoolean> namespaceStoppingMap = new ConcurrentHashMap<>();
  private static final WatchListener<V1Job> NULL_LISTENER = w -> { };

  private static final WatcherControl<V1ConfigMap, ConfigMapWatcher> configMapWatchers
        = new WatcherControl<>(ConfigMapWatcher::create, d -> d::dispatchConfigMapWatch);
  private static final WatcherControl<Domain, DomainWatcher> domainWatchers
        = new WatcherControl<>(DomainWatcher::create, d -> d::dispatchDomainWatch);
  private static final WatcherControl<V1Event, EventWatcher> eventWatchers
        = new WatcherControl<>(EventWatcher::create, d -> d::dispatchEventWatch);
  private static final WatcherControl<V1Job, JobWatcher> jobWatchers
        = new WatcherControl<>(JobWatcher::create, d -> NULL_LISTENER);
  private static final WatcherControl<V1Pod, PodWatcher> podWatchers
        = new WatcherControl<>(PodWatcher::create, d -> d::dispatchPodWatch);
  private static final WatcherControl<V1Service, ServiceWatcher> serviceWatchers
        = new WatcherControl<>(ServiceWatcher::create, d -> d::dispatchServiceWatch);

  static AtomicBoolean isStopping(String ns) {
    return namespaceStoppingMap.computeIfAbsent(ns, (key) -> new AtomicBoolean(false));
  }

  /**
   * Returns a collection of the names of the namespaces currently being managed by the operator.
   */
  @Nonnull
  static Set<String> getNamespaces() {
    return new TreeSet<>(namespaceStoppingMap.keySet());
  }

  /**
   * Requests all active namespaced-watchers to stop.
   */
  static void stopAllWatchers() {
    namespaceStoppingMap.forEach((key, value) -> value.set(true));
  }

  /**
   * Stop the specified namespace and discard its in-memory resources.
   * @param ns a namespace name
   */
  static void stopNamespace(String ns) {
    namespaceStoppingMap.remove(ns).set(true);
    namespaceStatuses.remove(ns);

    domainWatchers.removeWatcher(ns);
    eventWatchers.removeWatcher(ns);
    podWatchers.removeWatcher(ns);
    serviceWatchers.removeWatcher(ns);
    configMapWatchers.removeWatcher(ns);
    jobWatchers.removeWatcher(ns);
  }

  static ConfigMapWatcher getConfigMapWatcher(String namespace) {
    return configMapWatchers.getWatcher(namespace);
  }

  static DomainWatcher getDomainWatcher(String namespace) {
    return domainWatchers.getWatcher(namespace);
  }

  static EventWatcher getEventWatcher(String namespace) {
    return eventWatchers.getWatcher(namespace);
  }

  public static JobWatcher getJobWatcher(String namespace) {
    return jobWatchers.getWatcher(namespace);
  }

  static PodWatcher getPodWatcher(String namespace) {
    return podWatchers.getWatcher(namespace);
  }

  static ServiceWatcher getServiceWatcher(String namespace) {
    return serviceWatchers.getWatcher(namespace);
  }

  /**
   * Returns the internal status object for the specified namespace.
   * @param ns the name of the namespace.
   */
  @Nonnull
  static NamespaceStatus getNamespaceStatus(@Nonnull String ns) {
    return namespaceStatuses.computeIfAbsent(ns, (key) -> new NamespaceStatus());
  }

  static void startConfigMapWatcher(String ns, String initialResourceVersion, DomainProcessor processor) {
    configMapWatchers.startWatcher(ns, initialResourceVersion, processor);
  }

  private static WatchTuning getWatchTuning() {
    return TuningParameters.getInstance().getWatchTuning();
  }

  private static ThreadFactory getThreadFactory() {
    return ThreadFactorySingleton.getInstance();
  }

  /**
   * Returns a set up steps to update the specified namespace.
   * This will include adding any existing domains, pod, services,
   * and will also start watchers for the namespace if they aren't already running.
   * @param ns the name of the namespace
   * @param processor processing to be done to bring up any found domains
   */
  public static Step readExistingResources(String ns, DomainProcessor processor) {
    NamespacedResources resources = new NamespacedResources(ns, null);
    resources.addProcessing(new DomainResourcesValidation(ns, processor).getProcessors());
    resources.addProcessing(createWatcherStartupProcessing(ns, processor));
    return Step.chain(ConfigMapHelper.createScriptConfigMapStep(ns), resources.createListSteps());
  }

  interface WatcherFactory<T, W extends Watcher<T>> {
    W create(
          ThreadFactory threadFactory,
          String namespace,
          String initialResourceVersion,
          WatchTuning watchTuning,
          WatchListener<T> dispatchMethod,
          AtomicBoolean stopping);
  }

  interface ListenerSelector<T> extends Function<DomainProcessor, WatchListener<T>> { }

  static class WatcherControl<T, W extends Watcher<T>> {
    private final Map<String, W> watchers = new ConcurrentHashMap<>();
    private final WatcherFactory<T,W> factory;
    private final ListenerSelector<T> selector;

    public WatcherControl(WatcherFactory<T, W> factory, ListenerSelector<T> selector) {
      this.factory = factory;
      this.selector = selector;
    }

    void startWatcher(String namespace, String resourceVersion, DomainProcessor domainProcessor) {
      watchers.computeIfAbsent(namespace, n -> createWatcher(n, resourceVersion, selector.apply(domainProcessor)));
    }

    W createWatcher(String ns, String resourceVersion, WatchListener<T> listener) {
      return factory.create(getThreadFactory(), ns, resourceVersion, getWatchTuning(), listener, isStopping(ns));
    }

    W getWatcher(String ns) {
      return watchers.get(ns);
    }

    void removeWatcher(String ns) {
      watchers.remove(ns);
    }
  }

  static NamespacedResources.Processors createWatcherStartupProcessing(String ns, DomainProcessor domainProcessor) {
    return new WatcherStartupProcessing(ns, domainProcessor);
  }

  static class WatcherStartupProcessing extends NamespacedResources.Processors {
    private final String ns;
    private final DomainProcessor domainProcessor;

    WatcherStartupProcessing(String ns, DomainProcessor domainProcessor) {
      this.ns = ns;
      this.domainProcessor = domainProcessor;
    }

    @Override
    Consumer<V1ConfigMapList> getConfigMapListProcessing() {
      return l -> configMapWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    Consumer<V1EventList> getEventListProcessing() {
      return l -> eventWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    Consumer<V1JobList> getJobListProcessing() {
      return l -> jobWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    Consumer<V1PodList> getPodListProcessing() {
      return l -> podWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    Consumer<V1ServiceList> getServiceListProcessing() {
      return l -> serviceWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    Consumer<DomainList> getDomainListProcessing() {
      return l -> domainWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }
  }
}
