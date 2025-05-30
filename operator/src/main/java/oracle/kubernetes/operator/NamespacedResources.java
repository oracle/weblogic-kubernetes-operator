// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.watcher.ClusterWatcher;
import oracle.kubernetes.operator.watcher.ConfigMapWatcher;
import oracle.kubernetes.operator.watcher.DomainWatcher;
import oracle.kubernetes.operator.watcher.EventWatcher;
import oracle.kubernetes.operator.watcher.JobWatcher;
import oracle.kubernetes.operator.watcher.OperatorEventWatcher;
import oracle.kubernetes.operator.watcher.PodWatcher;
import oracle.kubernetes.operator.watcher.ServiceWatcher;
import oracle.kubernetes.operator.watcher.Watcher;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.LabelConstants.forDomainUidSelector;
import static oracle.kubernetes.operator.LabelConstants.getCreatedByOperatorSelector;

/**
 * A Class to manage listing Kubernetes resources associated with a namespace and doing processing on them.
 */
class NamespacedResources {
  private final CoreDelegate delegate;
  private final String namespace;
  private final String domainUid;
  private final List<Processors> processors = new ArrayList<>();
  private final DomainNamespaces domainNamespaces;

  NamespacedResources(CoreDelegate delegate, String namespace, String domainUid, DomainNamespaces domainNamespaces) {
    this.delegate = delegate;
    this.namespace = namespace;
    this.domainUid = domainUid;
    this.domainNamespaces = domainNamespaces;
  }

  void addProcessing(Processors processor) {
    processors.add(processor);
  }

  Step createListSteps() {
    return Step.chain(
          getConfigMapListSteps(),
          getPodEventListSteps(),
          getOperatorEventListSteps(),
          getJobListSteps(),
          getPodListSteps(),
          getServiceListSteps(),
          getPodDisruptionBudgetListSteps(),
          getDomainListSteps(),
          getClusterListSteps(),
          new CompletionStep()
    );
  }

  private Step getPauseWatchersStep(Watcher<?> watcher) {
    return new PauseWatchersStep<>(watcher);
  }

  private Step getConfigMapListSteps() {
    return getListProcessing(Processors::getConfigMapListProcessing).map(this::createConfigMapListStep).orElse(null);
  }

  private Step createConfigMapListStep(List<Consumer<V1ConfigMapList>> processing) {
    return Step.chain(getPauseWatchersStep(getConfigMapWatcher()),
        delegate.getConfigMapBuilder().list(namespace, new ListResponseStep<>(processing)));
  }

  private ConfigMapWatcher getConfigMapWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getConfigMapWatcher(namespace)).orElse(null);
  }

  private Step getPodEventListSteps() {
    return getListProcessing(Processors::getEventListProcessing).map(this::createPodEventListStep).orElse(null);
  }

  private Step createPodEventListStep(List<Consumer<CoreV1EventList>> processing) {
    return Step.chain(getPauseWatchersStep(getEventWatcher()),
        delegate.getEventBuilder().list(namespace,
            new ListOptions().fieldSelector(ProcessingConstants.READINESS_PROBE_FAILURE_EVENT_FILTER),
            new ListResponseStep<>(processing)));
  }

  private EventWatcher getEventWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getEventWatcher(namespace)).orElse(null);
  }

  private Step getOperatorEventListSteps() {
    return getListProcessing(Processors::getOperatorEventListProcessing)
        .map(this::createOperatorEventListStep).orElse(null);
  }

  private Step createOperatorEventListStep(List<Consumer<CoreV1EventList>> processing) {
    return Step.chain(getPauseWatchersStep(getOperatorEventWatcher()),
        delegate.getEventBuilder().list(namespace,
            new ListOptions().labelSelector(ProcessingConstants.OPERATOR_EVENT_LABEL_FILTER),
            new ListResponseStep<>(processing)));
  }

  private OperatorEventWatcher getOperatorEventWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getDomainEventWatcher(namespace)).orElse(null);
  }

  private Step getPodDisruptionBudgetListSteps() {
    return getListProcessing(Processors::getPodDisruptionBudgetListProcessing)
            .map(this::createPodDisruptionBudgetListStep).orElse(null);
  }

  private Step createPodDisruptionBudgetListStep(List<Consumer<V1PodDisruptionBudgetList>> processing) {
    return Step.chain(getPauseWatchersStep(getPodDisruptionBudgetWatcher()),
        delegate.getPodDisruptionBudgetBuilder().list(namespace,
            new ListOptions().labelSelector(forDomainUidSelector(domainUid) + "," + getCreatedByOperatorSelector()),
            new ListResponseStep<>(processing)));
  }

  private PodDisruptionBudgetWatcher getPodDisruptionBudgetWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getPodDisruptionBudgetWatcher(namespace))
        .orElse(null);
  }

  private Step getJobListSteps() {
    return getListProcessing(Processors::getJobListProcessing).map(this::createJobListStep).orElse(null);
  }

  private Step createJobListStep(List<Consumer<V1JobList>> processing) {
    return Step.chain(getPauseWatchersStep(getJobWatcher()),
        delegate.getJobBuilder().list(namespace,
            new ListOptions().labelSelector(LabelConstants.CREATEDBYOPERATOR_LABEL + "," + getDomainUidLabel()),
            new ListResponseStep<>(processing)));
  }

  private JobWatcher getJobWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getJobWatcher(namespace)).orElse(null);
  }

  private Step getPodListSteps() {
    return getListProcessing(Processors::getPodListProcessing).map(this::createPodListStep).orElse(null);
  }

  private Step createPodListStep(List<Consumer<V1PodList>> processing) {
    return Step.chain(getPauseWatchersStep(getPodWatcher()),
        delegate.getPodBuilder().list(namespace,
            new ListOptions().labelSelector(LabelConstants.CREATEDBYOPERATOR_LABEL + "," + getDomainUidLabel()),
            new ListResponseStep<>(processing)));
  }

  private PodWatcher getPodWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getPodWatcher(namespace)).orElse(null);
  }

  private String getDomainUidLabel() {
    return domainUid == null ? LabelConstants.DOMAINUID_LABEL : LabelConstants.forDomainUidSelector(domainUid);
  }

  private Step getServiceListSteps() {
    return getListProcessing(Processors::getServiceListProcessing).map(this::createServiceListStep).orElse(null);
  }

  private Step createServiceListStep(List<Consumer<V1ServiceList>> processing) {
    return Step.chain(getPauseWatchersStep(getServiceWatcher()),
        delegate.getServiceBuilder().list(namespace,
            new ListOptions().labelSelector(LabelConstants.CREATEDBYOPERATOR_LABEL + "," + getDomainUidLabel()),
            new ListResponseStep<>(processing)));
  }

  private ServiceWatcher getServiceWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getServiceWatcher(namespace)).orElse(null);
  }

  private Step getClusterListSteps() {
    return getListProcessing(Processors::getClusterListProcessing).map(this::createClusterListSteps).orElse(null);
  }

  private Step createClusterListSteps(List<Consumer<ClusterList>> processing) {
    return Step.chain(getPauseWatchersStep(getClusterWatcher()),
        delegate.getClusterBuilder().list(namespace, new ListResponseStep<>(processing)));
  }

  private ClusterWatcher getClusterWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getClusterWatcher(namespace)).orElse(null);
  }

  private Step getDomainListSteps() {
    return getListProcessing(Processors::getDomainListProcessing).map(this::createDomainListSteps).orElse(null);
  }

  private Step createDomainListSteps(List<Consumer<DomainList>> processing) {
    return Step.chain(getPauseWatchersStep(getDomainWatcher()),
        delegate.getDomainBuilder().list(namespace, new ListResponseStep<>(processing)));
  }

  private DomainWatcher getDomainWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getDomainWatcher(namespace)).orElse(null);
  }

  private <L extends KubernetesListObject>
        Optional<List<Consumer<L>>> getListProcessing(Function<Processors, Consumer<L>> method) {
    return nullIfEmpty(processors.stream().map(method).filter(Objects::nonNull).toList());
  }

  private <T> Optional<List<T>> nullIfEmpty(@Nonnull List<T> list) {
    return list.isEmpty() ? Optional.empty() : Optional.of(list);
  }


  class CompletionStep extends Step {
    @Override
    public @Nonnull Result apply(Packet packet) {
      processors.forEach(p -> p.completeProcessing(packet));
      return doNext(packet);
    }
  }

  static class PauseWatchersStep<T> extends Step {
    private final Watcher<T> watcher;

    PauseWatchersStep(Watcher<T> watcher) {
      this.watcher = watcher;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      Optional.ofNullable(watcher).ifPresent(Watcher::pause);
      return doNext(packet);
    }
  }

  private static class ListResponseStep<L extends KubernetesListObject> extends DefaultResponseStep<L> {
    private final List<Consumer<L>> processors;

    ListResponseStep(List<Consumer<L>> processors) {
      this.processors = processors;
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<L> callResponse) {
      processors.forEach(p -> p.accept(callResponse.getObject()));
      return doContinueListOrNext(callResponse, packet);
    }
  }
}
