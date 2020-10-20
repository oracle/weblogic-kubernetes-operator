// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1EventList;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainList;

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
          getScriptConfigMapSteps(),
          getConfigMapListSteps(),
          getEventListSteps(),
          getJobListSteps(),
          getPodListSteps(),
          getServiceListSteps(),
          getDomainListSteps(),
          new CompletionStep()
    );
  }

  /**
   * A class which describes some processing to be performed on the resources as they are read.
   */
  abstract static class Processors {

    /**
     * Return the processing to be performed on the script config map. May be null.
     */
    Consumer<Packet> getConfigMapProcessing() {
      return null;
    }

    /**
     * Return the processing to be performed on a list of config maps found in Kubernetes. May be null.
     */
    Consumer<V1ConfigMapList> getConfigMapListProcessing() {
      return null;
    }

    /**
     * Return the processing to be performed on a list of events found in Kubernetes. May be null.
     */
    Consumer<V1EventList> getEventListProcessing() {
      return null;
    }

    /**
     * Return the processing to be performed on a list of jobs found in Kubernetes. May be null.
     */
    Consumer<V1JobList> getJobListProcessing() {
      return null;
    }

    /**
     * Return the processing to be performed on a list of pods found in Kubernetes. May be null.
     */
    Consumer<V1PodList> getPodListProcessing() {
      return null;
    }

    /**
     * Return the processing to be performed on a list of services found in Kubernetes. May be null.
     */
    Consumer<V1ServiceList> getServiceListProcessing() {
      return null;
    }

    /**
     * Return the processing to be performed on a list of domains found in Kubernetes. May be null.
     */
    Consumer<DomainList> getDomainListProcessing() {
      return null;
    }

    /**
     * Do any post-processing of intermediate results.
     * @param packet the packet in the fiber
     */
    void completeProcessing(Packet packet) {
    }
  }

  private Step getScriptConfigMapSteps() {
    return getResourceProcessing(Processors::getConfigMapProcessing).map(this::createConfigMapSteps).orElse(null);
  }

  private Step createConfigMapSteps(List<Consumer<Packet>> processing) {
    return Step.chain(
          ConfigMapHelper.createScriptConfigMapStep(namespace),
          new Main.ConfigMapAfterStep(processing)
    );
  }

  private Step getConfigMapListSteps() {
    return getListProcessing(Processors::getConfigMapListProcessing).map(this::createConfigMapListStep).orElse(null);
  }

  private Step createConfigMapListStep(List<Consumer<V1ConfigMapList>> processing) {
    return new CallBuilder()
             .listConfigMapsAsync(namespace, new ListResponseStep<>(processing));
  }

  private Step getEventListSteps() {
    return getListProcessing(Processors::getEventListProcessing).map(this::createEventListStep).orElse(null);
  }

  private Step createEventListStep(List<Consumer<V1EventList>> processing) {
    return new CallBuilder()
            .withFieldSelector(ProcessingConstants.READINESS_PROBE_FAILURE_EVENT_FILTER)
            .listEventAsync(namespace, new ListResponseStep<>(processing));
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

  private Optional<List<Consumer<Packet>>> getResourceProcessing(Function<Processors, Consumer<Packet>> method) {
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
