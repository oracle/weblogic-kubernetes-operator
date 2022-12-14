// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
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
  private final String namespace;
  private final String domainUid;
  private final List<Processors> processors = new ArrayList<>();
  private final DomainNamespaces domainNamespaces;

  NamespacedResources(String namespace, String domainUid, DomainNamespaces domainNamespaces) {
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
        new CallBuilder().listConfigMapsAsync(namespace, new ListResponseStep<>(processing)));
  }

  private ConfigMapWatcher getConfigMapWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getConfigMapWatcher(namespace)).orElse(null);
  }

  private Step getPodEventListSteps() {
    return getListProcessing(Processors::getEventListProcessing).map(this::createPodEventListStep).orElse(null);
  }

  private Step createPodEventListStep(List<Consumer<CoreV1EventList>> processing) {
    return Step.chain(getPauseWatchersStep(getEventWatcher()),
        new CallBuilder().withFieldSelector(ProcessingConstants.READINESS_PROBE_FAILURE_EVENT_FILTER)
            .listEventAsync(namespace, new ListResponseStep<>(processing)));
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
        new CallBuilder().withLabelSelectors(ProcessingConstants.OPERATOR_EVENT_LABEL_FILTER)
            .listEventAsync(namespace, new ListResponseStep<>(processing)));
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
        new CallBuilder().withLabelSelectors(forDomainUidSelector(domainUid), getCreatedByOperatorSelector())
            .listPodDisruptionBudgetAsync(namespace, new ListResponseStep<>(processing)));
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
        createSubResourceCallBuilder().listJobAsync(namespace, new ListResponseStep<>(processing)));
  }

  private JobWatcher getJobWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getJobWatcher(namespace)).orElse(null);
  }

  private Step getPodListSteps() {
    return getListProcessing(Processors::getPodListProcessing).map(this::createPodListStep).orElse(null);
  }

  private Step createPodListStep(List<Consumer<V1PodList>> processing) {
    return Step.chain(getPauseWatchersStep(getPodWatcher()),
        createSubResourceCallBuilder().listPodAsync(namespace, new ListResponseStep<>(processing)));
  }

  private PodWatcher getPodWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getPodWatcher(namespace)).orElse(null);
  }

  private CallBuilder createSubResourceCallBuilder() {
    return new CallBuilder().withLabelSelectors(LabelConstants.CREATEDBYOPERATOR_LABEL, getDomainUidLabel());
  }

  private String getDomainUidLabel() {
    return domainUid == null ? LabelConstants.DOMAINUID_LABEL : LabelConstants.forDomainUidSelector(domainUid);
  }

  private Step getServiceListSteps() {
    return getListProcessing(Processors::getServiceListProcessing).map(this::createServiceListStep).orElse(null);
  }

  private Step createServiceListStep(List<Consumer<V1ServiceList>> processing) {
    return Step.chain(getPauseWatchersStep(getServiceWatcher()),
        createSubResourceCallBuilder().listServiceAsync(namespace, new ListResponseStep<>(processing)));
  }

  private ServiceWatcher getServiceWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getServiceWatcher(namespace)).orElse(null);
  }

  private Step getClusterListSteps() {
    return getListProcessing(Processors::getClusterListProcessing).map(this::createClusterListSteps).orElse(null);
  }

  private Step createClusterListSteps(List<Consumer<ClusterList>> processing) {
    return Step.chain(getPauseWatchersStep(getClusterWatcher()),
        new CallBuilder().listClusterAsync(namespace, new ListResponseStep<>(processing)));
  }

  private ClusterWatcher getClusterWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getClusterWatcher(namespace)).orElse(null);
  }

  private Step getDomainListSteps() {
    return getListProcessing(Processors::getDomainListProcessing).map(this::createDomainListSteps).orElse(null);
  }

  private Step createDomainListSteps(List<Consumer<DomainList>> processing) {
    return Step.chain(getPauseWatchersStep(getDomainWatcher()),
        new CallBuilder().listDomainAsync(namespace, new ListResponseStep<>(processing)));
  }

  private DomainWatcher getDomainWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getDomainWatcher(namespace)).orElse(null);
  }

  private <L extends KubernetesListObject>
        Optional<List<Consumer<L>>> getListProcessing(Function<Processors, Consumer<L>> method) {
    return nullIfEmpty(processors.stream().map(method).filter(Objects::nonNull).collect(Collectors.toList()));
  }

  private <T> Optional<List<T>> nullIfEmpty(@Nonnull List<T> list) {
    return list.isEmpty() ? Optional.empty() : Optional.of(list);
  }


  class CompletionStep extends Step {
    @Override
    public NextAction apply(Packet packet) {
      processors.forEach(p -> p.completeProcessing(packet));
      return doNext(packet);
    }
  }

  class PauseWatchersStep<T> extends Step {
    private final Watcher<T> watcher;

    PauseWatchersStep(Watcher<T> watcher) {
      this.watcher = watcher;
    }

    @Override
    public NextAction apply(Packet packet) {
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
    public NextAction onSuccess(Packet packet, CallResponse<L> callResponse) {
      processors.forEach(p -> p.accept(callResponse.getResult()));
      return doContinueListOrNext(callResponse, packet);
    }
  }
}
