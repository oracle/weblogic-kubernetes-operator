// Copyright (c) 2020, 2026, Oracle and/or its affiliates.
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
import io.kubernetes.client.openapi.models.EventsV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
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
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

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
    return new PauseWatchersStep<>(namespace, domainUid, watcher);
  }

  private Step getConfigMapListSteps() {
    return getListProcessing(Processors::getConfigMapListProcessing).map(this::createConfigMapListStep).orElse(null);
  }

  private Step createConfigMapListStep(List<Consumer<V1ConfigMapList>> processing) {
    return Step.chain(getPauseWatchersStep(getConfigMapWatcher()),
        RequestBuilder.CM.list(namespace, new ListResponseStep<>(processing)));
  }

  private ConfigMapWatcher getConfigMapWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getConfigMapWatcher(namespace)).orElse(null);
  }

  private Step getPodEventListSteps() {
    return getListProcessing(Processors::getEventListProcessing).map(this::createPodEventListStep).orElse(null);
  }

  private Step createPodEventListStep(List<Consumer<EventsV1EventList>> processing) {
    return Step.chain(getPauseWatchersStep(getEventWatcher()),
        RequestBuilder.EVENT.list(namespace,
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

  private Step createOperatorEventListStep(List<Consumer<EventsV1EventList>> processing) {
    return Step.chain(getPauseWatchersStep(getOperatorEventWatcher()),
        RequestBuilder.EVENT.list(namespace,
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
        RequestBuilder.PDB.list(namespace,
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
        RequestBuilder.JOB.list(namespace,
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
        RequestBuilder.POD.list(namespace,
            new ListOptions().labelSelector(LabelConstants.CREATEDBYOPERATOR_LABEL + "," + getDomainUidLabel()),
            new PodListResponseStep(processing)));
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
        RequestBuilder.SERVICE.list(namespace,
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
        RequestBuilder.CLUSTER.list(namespace, new ListResponseStep<>(processing)));
  }

  private ClusterWatcher getClusterWatcher() {
    return Optional.ofNullable(domainNamespaces).map(n -> n.getClusterWatcher(namespace)).orElse(null);
  }

  private Step getDomainListSteps() {
    return getListProcessing(Processors::getDomainListProcessing).map(this::createDomainListSteps).orElse(null);
  }

  private Step createDomainListSteps(List<Consumer<DomainList>> processing) {
    return Step.chain(getPauseWatchersStep(getDomainWatcher()),
        RequestBuilder.DOMAIN.list(namespace, new ListResponseStep<>(processing)));
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
    private final String namespace;
    private final String domainUid;
    private final Watcher<T> watcher;

    PauseWatchersStep(String namespace, String domainUid, Watcher<T> watcher) {
      this.namespace = namespace;
      this.domainUid = domainUid;
      this.watcher = watcher;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      if (LOGGER.isFineEnabled()) {
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=watcher-control phase=pause-request "
                + "namespace={0} domainUid={1} requestedWatcherType={2} requestedWatcher={3} "
                + "fiber={4} thread={5}",
            namespace,
            domainUid,
            watcher == null ? null : watcher.getClass().getSimpleName(),
            getIdentity(watcher),
            packet.getFiber(),
            Thread.currentThread().getName());
      }
      Optional.ofNullable(watcher).ifPresent(Watcher::pause);
      return doNext(packet);
    }
  }

  private class PodListResponseStep extends ListResponseStep<V1PodList> {

    PodListResponseStep(List<Consumer<V1PodList>> processors) {
      super(processors);
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<V1PodList> callResponse) {
      logPodListResponse(packet, callResponse.getObject());
      return super.onSuccess(packet, callResponse);
    }
  }

  private void logPodListResponse(Packet packet, V1PodList list) {
    if (!LOGGER.isFineEnabled()) {
      return;
    }

    LOGGER.fine(
        "WKO-POD-STARTUP-TRACE component=pod-list phase=response namespace={0} domainUid={1} "
            + "listResourceVersion={2} itemCount={3} fiber={4} thread={5}",
        namespace,
        domainUid,
        Optional.ofNullable(list).map(V1PodList::getMetadata).map(m -> m.getResourceVersion()).orElse(null),
        Optional.ofNullable(list).map(V1PodList::getItems).map(List::size).orElse(0),
        packet.getFiber(),
        Thread.currentThread().getName());

    Optional.ofNullable(list).map(V1PodList::getItems).orElse(List.of())
        .forEach(pod -> logPodListItem(packet, pod));
  }

  private void logPodListItem(Packet packet, V1Pod pod) {
    String nodeName = Optional.ofNullable(pod).map(V1Pod::getSpec).map(s -> s.getNodeName()).orElse(null);
    LOGGER.fine(
        "WKO-POD-STARTUP-TRACE component=pod-list phase=item namespace={0} requestedDomainUid={1} "
            + "domainUid={2} cluster={3} server={4} pod={5} resourceVersion={6} "
            + "creationTimestamp={7} node={8} scheduled={9} podScheduledTransitionTime={10} "
            + "ready={11} deleting={12} fiber={13} thread={14}",
        namespace,
        domainUid,
        PodHelper.getPodDomainUid(pod),
        PodHelper.getPodClusterName(pod),
        PodHelper.getPodServerName(pod),
        PodHelper.getPodName(pod),
        Optional.ofNullable(pod).map(V1Pod::getMetadata).map(m -> m.getResourceVersion()).orElse(null),
        Optional.ofNullable(pod).map(V1Pod::getMetadata).map(m -> m.getCreationTimestamp()).orElse(null),
        nodeName,
        nodeName != null,
        getPodScheduledTransitionTime(pod),
        PodHelper.isReady(pod),
        Optional.ofNullable(pod).map(V1Pod::getMetadata).map(m -> m.getDeletionTimestamp()).isPresent(),
        packet.getFiber(),
        Thread.currentThread().getName());
  }

  private Object getPodScheduledTransitionTime(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getStatus)
        .map(s -> s.getConditions())
        .orElse(List.of())
        .stream()
        .filter(c -> KubernetesConstants.POD_SCHEDULED.equals(c.getType()))
        .findFirst()
        .map(c -> c.getLastTransitionTime())
        .orElse(null);
  }

  private static String getIdentity(Object object) {
    return object == null ? null : Integer.toHexString(System.identityHashCode(object));
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
