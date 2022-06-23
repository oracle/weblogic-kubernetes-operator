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

  NamespacedResources(String namespace, String domainUid) {
    this.namespace = namespace;
    this.domainUid = domainUid;
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
          getClusterListSteps(),
          getDomainListSteps(),
          new CompletionStep()
    );
  }

  private Step getConfigMapListSteps() {
    return getListProcessing(Processors::getConfigMapListProcessing).map(this::createConfigMapListStep).orElse(null);
  }

  private Step createConfigMapListStep(List<Consumer<V1ConfigMapList>> processing) {
    return new CallBuilder()
             .listConfigMapsAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getPodEventListSteps() {
    return getListProcessing(Processors::getEventListProcessing).map(this::createPodEventListStep).orElse(null);
  }

  private Step createPodEventListStep(List<Consumer<CoreV1EventList>> processing) {
    return new CallBuilder()
            .withFieldSelector(ProcessingConstants.READINESS_PROBE_FAILURE_EVENT_FILTER)
            .listEventAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getOperatorEventListSteps() {
    return getListProcessing(Processors::getOperatorEventListProcessing)
        .map(this::createOperatorEventListStep).orElse(null);
  }

  private Step createOperatorEventListStep(List<Consumer<CoreV1EventList>> processing) {
    return new CallBuilder()
        .withLabelSelectors(ProcessingConstants.OPERATOR_EVENT_LABEL_FILTER)
        .listEventAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getPodDisruptionBudgetListSteps() {
    return getListProcessing(Processors::getPodDisruptionBudgetListProcessing)
            .map(this::createPodDisruptionBudgetListStep).orElse(null);
  }

  private Step createPodDisruptionBudgetListStep(List<Consumer<V1PodDisruptionBudgetList>> processing) {
    return new CallBuilder().withLabelSelectors(forDomainUidSelector(domainUid), getCreatedByOperatorSelector())
            .listPodDisruptionBudgetAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getJobListSteps() {
    return getListProcessing(Processors::getJobListProcessing).map(this::createJobListStep).orElse(null);
  }

  private Step createJobListStep(List<Consumer<V1JobList>> processing) {
    return createSubResourceCallBuilder().listJobAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getPodListSteps() {
    return getListProcessing(Processors::getPodListProcessing).map(this::createPodListStep).orElse(null);
  }

  private Step createPodListStep(List<Consumer<V1PodList>> processing) {
    return createSubResourceCallBuilder().listPodAsync(namespace, new ListResponseStep<>(processing));
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
    return createSubResourceCallBuilder().listServiceAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getClusterListSteps() {
    return getListProcessing(Processors::getClusterListProcessing).map(this::createClusterListSteps).orElse(null);
  }

  private Step createClusterListSteps(List<Consumer<ClusterList>> processing) {
    return new CallBuilder().listClusterAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getDomainListSteps() {
    return getListProcessing(Processors::getDomainListProcessing).map(this::createDomainListSteps).orElse(null);
  }

  private Step createDomainListSteps(List<Consumer<DomainList>> processing) {
    return new CallBuilder().listDomainAsync(namespace, new ListResponseStep<>(processing));
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
