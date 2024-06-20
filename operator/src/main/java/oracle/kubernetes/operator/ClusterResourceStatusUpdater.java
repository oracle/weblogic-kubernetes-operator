// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.ClusterResourceEventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterCondition;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.helpers.EventHelper.createClusterResourceEventData;

/**
 * Updates for status of Cluster resources.
 */
@SuppressWarnings("WeakerAccess")
public class ClusterResourceStatusUpdater {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ClusterResourceStatusUpdater() {
  }

  /**
   * Creates an asynchronous step to update cluster status.
   *
   * @param next the next step
   * @return the new step
   */
  public static Step createClusterResourceStatusUpdaterStep(Step next) {
    return new ClusterResourceStatusUpdaterStep(next);
  }

  private static ReplaceClusterStatusContext createContext(Packet packet, ClusterResource resource) {
    return new ReplaceClusterStatusContext(packet, resource);
  }

  private static class ClusterResourceStatusUpdaterStep extends Step {

    ClusterResourceStatusUpdaterStep(Step next) {
      super(next);
    }

    /**
     * Invokes step using the packet as input/output context.
     *
     * @param packet Packet
     * @return Next action
     */
    @Override
    public @Nonnull Result apply(Packet packet) {
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      Step step = Optional.ofNullable(info.getDomain())
          .map(domain -> createUpdateClusterResourceStatusSteps(packet, info.getClusterResources()))
          .orElse(null);
      return doNext(chainStep(step, getNext()), packet);
    }

    private static Step createUpdateClusterResourceStatusSteps(Packet packet,
                                                               Collection<ClusterResource> clusterResources) {
      List<Fiber.StepAndPacket> result = clusterResources.stream()
          .filter(res -> createContext(packet, res).isClusterResourceStatusChanged())
          .map(res -> new Fiber.StepAndPacket(
                  createContext(packet, res).createReplaceClusterResourceStatusStep(), packet))
          .toList();
      return result.isEmpty() ? null : new RunInParallelStep(result);
    }

    private static Step chainStep(Step one, Step two) {
      if (one == null) {
        return two;
      }
      if (two == null) {
        return one;
      }
      return Step.chain(one, two);
    }
  }

  private static class ClusterResourceStatusReplaceResponseStep extends DefaultResponseStep<ClusterResource> {
    private final ReplaceClusterStatusContext context;

    ClusterResourceStatusReplaceResponseStep(ReplaceClusterStatusContext context) {
      this.context = context;
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<ClusterResource> callResponse) {
      if (callResponse.getObject() != null) {
        DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
        info.addClusterResource(callResponse.getObject());
      }
      return doNext(packet);
    }

    @Override
    public Result onFailure(Packet packet, KubernetesApiResponse<ClusterResource> callResponse) {
      if (isUnrecoverable(callResponse)) {
        return super.onFailure(packet, callResponse);
      } else {
        return onFailure(createRetry(), packet, callResponse);
      }
    }

    private Step createRetry() {
      return createClusterResourceRefreshStep(new SingleClusterResourceStatusUpdateStep(context.getClusterName()));
    }

    private Step createClusterResourceRefreshStep(Step next) {
      return RequestBuilder.CLUSTER.get(
          context.getNamespace(), context.getClusterResourceName(), new ReadClusterResponseStep(next));
    }
  }

  private static class RunInParallelStep extends Step {
    final Collection<Fiber.StepAndPacket> statusUpdateSteps;

    RunInParallelStep(Collection<Fiber.StepAndPacket> statusUpdateSteps) {
      this.statusUpdateSteps = statusUpdateSteps;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      if (statusUpdateSteps.isEmpty()) {
        return doNext(getNext(), packet);
      } else {
        return doForkJoin(getNext(), packet, statusUpdateSteps);
      }
    }
  }

  private static class ReplaceClusterStatusContext {
    private final Packet packet;
    private final DomainResource domain;
    private final ClusterResource resource;
    private ClusterStatus newStatus;
    private final boolean isMakeRight;

    private ReplaceClusterStatusContext(@Nonnull Packet packet, @Nonnull ClusterResource resource) {
      this.packet = packet;
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      this.domain = info.getDomain();
      this.resource = resource;
      isMakeRight = MakeRightDomainOperation.isMakeRight(packet);
    }

    String getClusterName() {
      return resource.getClusterName();
    }

    String getClusterResourceName() {
      return resource.getMetadata().getName();
    }

    String getNamespace() {
      return resource.getNamespace();
    }

    ClusterResource createReplacementClusterResource() {
      return new ClusterResource()
          .withKind(CLUSTER)
          .withMetadata(resource.getMetadata())
          .spec(null)
          .withStatus(getNewStatus());
    }

    private ClusterStatus getNewStatus() {
      if (newStatus == null) {
        newStatus = createNewStatus();
      }

      return newStatus;
    }

    private ClusterStatus createNewStatus() {
      return Optional.ofNullable(domain)
        .map(dom -> findClusterStatus(dom.getOrCreateStatus().getClusters(), getClusterName()))
        .orElse(null);
    }

    boolean isClusterResourceStatusChanged() {
      return !Objects.equals(getNewStatus(), resource.getStatus());
    }

    private Step createReplaceClusterResourceStatusStep() {
      LOGGER.fine(MessageKeys.CLUSTER_STATUS, getClusterResourceName(),
          getNewStatus());

      ClusterStatus newClusterStatus = getNewStatus();
      if (isMakeRight) {
        // Only set observedGeneration during a make-right, but not during a background status update
        Optional.ofNullable(newClusterStatus)
            .ifPresent(cs -> cs.setObservedGeneration(resource.getMetadata().getGeneration()));
      }

      final List<Step> result = new ArrayList<>();
      result.add(createReplaceClusterStatusAsyncStep());

      // add steps to create events for updating conditions
      Optional.ofNullable(newClusterStatus)
          .map(ncs -> getClusterStatusConditionEvents(ncs.getConditions())).orElse(Collections.emptyList())
          .stream().map(EventHelper::createClusterResourceEventStep).forEach(result::add);

      return Step.chain(result);
    }

    private List<EventData> getClusterStatusConditionEvents(List<ClusterCondition> conditions) {
      List<EventData> list = new ArrayList<>();
      list.addAll(getClusterStatusConditionTrueEvents(conditions));
      list.addAll(getClusterStatusConditionFalseEvents(conditions));
      list.sort(Comparator.comparing(EventData::getOrdering));
      return list;
    }

    private List<EventData> getClusterStatusConditionFalseEvents(List<ClusterCondition> conditions) {
      return conditions.stream().filter(cc -> "False".equals(cc.getStatus()))
           .map(this::toFalseClusterResourceEvent)
           .filter(Objects::nonNull)
           .toList();
    }

    private List<EventData> getClusterStatusConditionTrueEvents(List<ClusterCondition> conditions) {
      return conditions.stream().filter(cc -> "True".equals(cc.getStatus()))
          .map(this::toTrueClusterResourceEvent)
          .filter(Objects::nonNull)
          .toList();
    }

    private EventData toTrueClusterResourceEvent(ClusterCondition condition) {
      return Optional.ofNullable(condition.getType().getAddedEvent())
          .map(eventItem -> createClusterResourceEventData(eventItem, resource, domain.getDomainUid()))
          .map(this::initializeClusterResourceEventData)
          .orElse(null);
    }

    private EventData toFalseClusterResourceEvent(ClusterCondition removedCondition) {
      return Optional.ofNullable(removedCondition.getType().getRemovedEvent())
          .map(eventItem -> createClusterResourceEventData(eventItem, resource, domain.getDomainUid()))
          .map(this::initializeClusterResourceEventData)
          .orElse(null);
    }

    private EventData initializeClusterResourceEventData(ClusterResourceEventData eventData) {
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      return eventData.resourceName(resource.getMetadata().getName())
          .namespace(resource.getNamespace()).domainPresenceInfo(info);
    }

    private static ClusterStatus findClusterStatus(List<ClusterStatus> clusterStatuses, String clusterName) {
      return clusterStatuses.stream().filter(cs -> clusterName.equals(cs.getClusterName())).findFirst().orElse(null);
    }

    private Step createReplaceClusterStatusAsyncStep() {
      return RequestBuilder.CLUSTER.updateStatus(
          createReplacementClusterResource(), ClusterResource::getStatus,
          new ClusterResourceStatusReplaceResponseStep(this));
    }
  }

  private static class ReadClusterResponseStep extends ResponseStep<ClusterResource> {

    private ReadClusterResponseStep(Step step) {
      super(step);
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<ClusterResource> callResponse) {
      if (callResponse.getObject() != null) {
        DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
        info.addClusterResource(callResponse.getObject());
      }
      return doNext(packet);
    }

    @Override
    public Result onFailure(Packet packet, KubernetesApiResponse<ClusterResource> callResponse) {
      return callResponse.getHttpStatusCode() == HTTP_NOT_FOUND
          ? doNext(null, packet)
          : super.onFailure(packet, callResponse);
    }
  }

  private static class SingleClusterResourceStatusUpdateStep extends Step {
    private final String clusterName;

    SingleClusterResourceStatusUpdateStep(String clusterName) {
      this.clusterName = clusterName;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      // Get the ClusterResource, that was refreshed, from DomainPresenceInfo.
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      ClusterResource res = info.getClusterResource(clusterName);
      return doNext(createContext(packet, res).createReplaceClusterResourceStatusStep(), packet);
    }
  }
}

