// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.watcher.ClusterWatcher;
import oracle.kubernetes.operator.watcher.ConfigMapWatcher;
import oracle.kubernetes.operator.watcher.DomainWatcher;
import oracle.kubernetes.operator.watcher.EventWatcher;
import oracle.kubernetes.operator.watcher.JobWatcher;
import oracle.kubernetes.operator.watcher.OperatorEventWatcher;
import oracle.kubernetes.operator.watcher.PodWatcher;
import oracle.kubernetes.operator.watcher.ServiceWatcher;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.watcher.Watcher;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.helpers.KubernetesUtils.getResourceVersion;

/**
 * This class represents the information the operator maintains for a namespace used to maintain
 * one or more domains.
 */
@SuppressWarnings("SameParameterValue")
public class DomainNamespaces {
  private final Map<String, NamespaceStatus> namespaceStatuses = new ConcurrentHashMap<>();
  private final Map<String, AtomicBoolean> namespaceStoppingMap = new ConcurrentHashMap<>();

  private final WatcherControl<ClusterResource, ClusterWatcher> clusterWatchers
      = new WatcherControl<>(ClusterWatcher::create, d -> d::dispatchClusterWatch);
  private final WatcherControl<V1ConfigMap, ConfigMapWatcher> configMapWatchers
        = new WatcherControl<>(ConfigMapWatcher::create, d -> d::dispatchConfigMapWatch);
  private final WatcherControl<DomainResource, DomainWatcher> domainWatchers
        = new WatcherControl<>(DomainWatcher::create, d -> d::dispatchDomainWatch);
  private final WatcherControl<CoreV1Event, EventWatcher> eventWatchers
        = new WatcherControl<>(EventWatcher::create, d -> d::dispatchEventWatch);
  private final WatcherControl<CoreV1Event, OperatorEventWatcher> operatorEventWatchers
      = new WatcherControl<>(OperatorEventWatcher::create, d -> d::dispatchEventWatch);
  private final WatcherControl<V1Job, JobWatcher> jobWatchers
        = new WatcherControl<>(JobWatcher::create, d -> d::dispatchJobWatch);
  private final WatcherControl<V1Pod, PodWatcher> podWatchers
        = new WatcherControl<>(PodWatcher::create, d -> d::dispatchPodWatch);
  private final WatcherControl<V1Service, ServiceWatcher> serviceWatchers
        = new WatcherControl<>(ServiceWatcher::create, d -> d::dispatchServiceWatch);
  private final WatcherControl<V1PodDisruptionBudget, PodDisruptionBudgetWatcher> podDisruptionBudgetWatchers
          = new WatcherControl<>(PodDisruptionBudgetWatcher::create, d -> d::dispatchPodDisruptionBudgetWatch);

  private final SemanticVersion productVersion;

  AtomicBoolean isStopping(String ns) {
    return namespaceStoppingMap.computeIfAbsent(ns, key -> new AtomicBoolean(false));
  }

  AtomicBoolean getStopping(String ns) {
    return namespaceStoppingMap.get(ns);
  }

  boolean isStarting(String ns) {
    return Optional.ofNullable(namespaceStatuses.get(ns))
          .map(NamespaceStatus::isNamespaceStarting)
          .map(AtomicBoolean::get)
          .orElse(false);
  }

  /**
   * Constructs a DomainNamespace object.
   */
  DomainNamespaces(SemanticVersion productVersion) {
    namespaceStatuses.clear();
    namespaceStoppingMap.clear();
    this.productVersion = productVersion;
  }

  /**
   * Returns a collection of the names of the namespaces currently being managed by the operator.
   */
  @Nonnull
  Set<String> getNamespaces() {
    return new TreeSet<>(namespaceStoppingMap.keySet());
  }

  /**
   * Requests all active namespaced-watchers to stop.
   */
  void stopAllWatchers() {
    namespaceStoppingMap.forEach((key, value) -> value.set(true));
  }

  /**
   * Stop the specified namespace and discard its in-memory resources.
   * @param ns a namespace name
   */
  void stopNamespace(String ns) {
    namespaceStoppingMap.remove(ns).set(true);
    namespaceStatuses.remove(ns);
    clusterWatchers.removeWatcher(ns);
    domainWatchers.removeWatcher(ns);
    eventWatchers.removeWatcher(ns);
    operatorEventWatchers.removeWatcher(ns);
    podWatchers.removeWatcher(ns);
    serviceWatchers.removeWatcher(ns);
    podDisruptionBudgetWatchers.removeWatcher(ns);
    configMapWatchers.removeWatcher(ns);
    jobWatchers.removeWatcher(ns);

    DomainProcessorImpl.cleanupNamespace(ns);
  }

  ConfigMapWatcher getConfigMapWatcher(String namespace) {
    return configMapWatchers.getWatcher(namespace);
  }

  ClusterWatcher getClusterWatcher(String namespace) {
    return clusterWatchers.getWatcher(namespace);
  }

  DomainWatcher getDomainWatcher(String namespace) {
    return domainWatchers.getWatcher(namespace);
  }

  EventWatcher getEventWatcher(String namespace) {
    return eventWatchers.getWatcher(namespace);
  }

  OperatorEventWatcher getDomainEventWatcher(String namespace) {
    return operatorEventWatchers.getWatcher(namespace);
  }

  JobWatcher getJobWatcher(String namespace) {
    return jobWatchers.getWatcher(namespace);
  }

  PodWatcher getPodWatcher(String namespace) {
    return podWatchers.getWatcher(namespace);
  }

  ServiceWatcher getServiceWatcher(String namespace) {
    return serviceWatchers.getWatcher(namespace);
  }

  PodDisruptionBudgetWatcher getPodDisruptionBudgetWatcher(String namespace) {
    return podDisruptionBudgetWatchers.getWatcher(namespace);
  }

  /**
   * Returns the internal status object for the specified namespace.
   * @param ns the name of the namespace.
   */
  @Nonnull
  NamespaceStatus getNamespaceStatus(@Nonnull String ns) {
    return namespaceStatuses.computeIfAbsent(ns, key -> new NamespaceStatus());
  }

  static WatchTuning getWatchTuning() {
    return TuningParameters.getInstance().getWatchTuning();
  }

  static ThreadFactory getThreadFactory() {
    return ThreadFactorySingleton.getInstance();
  }

  /**
   * Returns a set-up steps to update the specified namespace.
   * This will include adding any existing domains, pod, services,
   * and will also start watchers for the namespace if they aren't already running.
   * @param ns the name of the namespace
   * @param processor processing to be done to bring up any found domains
   */
  Step readExistingResources(String ns, DomainProcessor processor) {
    NamespacedResources resources = new NamespacedResources(ns, null, this);
    resources.addProcessing(new DomainResourcesValidation(ns, processor).getProcessors());
    resources.addProcessing(createWatcherStartupProcessing(ns, processor));
    return Step.chain(ConfigMapHelper.createScriptConfigMapStep(ns, productVersion), resources.createListSteps());
  }

  public boolean shouldStartNamespace(String ns) {
    return getNamespaceStatus(ns).shouldStartNamespace();
  }

  public void clearNamespaceStartingFlag(String ns) {
    getNamespaceStatus(ns).clearNamespaceStartingFlag();
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

  class WatcherControl<T, W extends Watcher<T>> {
    private final Map<String, W> watchers = new ConcurrentHashMap<>();
    private final WatcherFactory<T,W> factory;
    private final ListenerSelector<T> selector;

    private WatcherControl(WatcherFactory<T, W> factory, ListenerSelector<T> selector) {
      this.factory = factory;
      this.selector = selector;
    }

    void startWatcher(String namespace, String resourceVersion, DomainProcessor domainProcessor) {
      watchers.computeIfAbsent(namespace, n -> createWatcher(n, resourceVersion, selector.apply(domainProcessor)));
      getWatcher(namespace).withResourceVersion(resourceVersion).resume();
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

  private Processors createWatcherStartupProcessing(String ns, DomainProcessor domainProcessor) {
    return new WatcherStartupProcessing(ns, domainProcessor);
  }

  class WatcherStartupProcessing implements Processors {
    private final String ns;
    private final DomainProcessor domainProcessor;

    WatcherStartupProcessing(String ns, DomainProcessor domainProcessor) {
      this.ns = ns;
      this.domainProcessor = domainProcessor;
    }

    @Override
    public Consumer<V1ConfigMapList> getConfigMapListProcessing() {
      return l -> configMapWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<CoreV1EventList> getEventListProcessing() {
      return l -> eventWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<CoreV1EventList> getOperatorEventListProcessing() {
      return l -> operatorEventWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<V1JobList> getJobListProcessing() {
      return l -> jobWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<V1PodList> getPodListProcessing() {
      return l -> podWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<V1ServiceList> getServiceListProcessing() {
      return l -> serviceWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<V1PodDisruptionBudgetList> getPodDisruptionBudgetListProcessing() {
      return l -> podDisruptionBudgetWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<DomainList> getDomainListProcessing() {
      return l -> domainWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }

    @Override
    public Consumer<ClusterList> getClusterListProcessing() {
      return l -> clusterWatchers.startWatcher(ns, getResourceVersion(l), domainProcessor);
    }
  }

  Processors createWatcherResumeProcessing(String ns) {
    return new WatcherResumeProcessing(ns);
  }

  class WatcherResumeProcessing implements Processors {
    private final String ns;

    WatcherResumeProcessing(String ns) {
      this.ns = ns;
    }

    @Override
    public Consumer<V1ConfigMapList> getConfigMapListProcessing() {
      return l -> Optional.ofNullable(configMapWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<CoreV1EventList> getEventListProcessing() {
      return l -> Optional.ofNullable(eventWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<CoreV1EventList> getOperatorEventListProcessing() {
      return l -> Optional.ofNullable(operatorEventWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<V1JobList> getJobListProcessing() {
      return l -> Optional.ofNullable(jobWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<V1PodList> getPodListProcessing() {
      return l -> Optional.ofNullable(podWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<V1ServiceList> getServiceListProcessing() {
      return l -> Optional.ofNullable(serviceWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<V1PodDisruptionBudgetList> getPodDisruptionBudgetListProcessing() {
      return l -> Optional.ofNullable(podDisruptionBudgetWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<DomainList> getDomainListProcessing() {
      return l -> Optional.ofNullable(domainWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }

    @Override
    public Consumer<ClusterList> getClusterListProcessing() {
      return l -> Optional.ofNullable(clusterWatchers.getWatcher(ns))
          .ifPresent(w -> w.withResourceVersion(getResourceVersion(l)).resume());
    }
  }
}
