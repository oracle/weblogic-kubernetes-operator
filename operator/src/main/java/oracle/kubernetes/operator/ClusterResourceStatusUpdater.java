// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;

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

  private static StepAndPacket createReplaceClusterResourceStatusStep(ReplaceClusterStatusContext context) {
    LOGGER.fine(MessageKeys.CLUSTER_STATUS, context.getClusterResourceName(),
        getNewStatus(context.getDomain(), context.getClusterName()));
    return new StepAndPacket(createReplaceClusterStatusAsyncStep(context), context.getPacket());
  }

  private static Step createUpdateClusterResourceStatusSteps(Packet packet,
      Collection<ClusterResource> clusterResources) {
    DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
    final List<StepAndPacket> result = clusterResources.stream()
        .filter(res -> isClusterResourceStatusChanged(getNewStatus(info.getDomain(), res.getClusterName()),
            res.getStatus()))
        .map(res -> createReplaceClusterResourceStatusStep(createContext(packet, res))).collect(Collectors.toList());
    return result.isEmpty() ? null : new RunInParallelStep(result);
  }

  private static ClusterStatus getNewStatus(DomainResource domain, String clusterName) {
    return Optional.ofNullable(domain)
        .map(dom -> findClusterStatus(dom.getOrCreateStatus().getClusters(), clusterName))
        .orElse(null);
  }

  private static boolean isClusterResourceStatusChanged(ClusterStatus newStatus, ClusterStatus currentStatus) {
    return !Objects.equals(newStatus, currentStatus);
  }

  private static ClusterStatus findClusterStatus(List<ClusterStatus> clusterStatuses, String clusterName) {
    return clusterStatuses.stream().filter(cs -> clusterName.equals(cs.getClusterName())).findFirst().orElse(null);
  }

  private static Step createReplaceClusterStatusAsyncStep(ReplaceClusterStatusContext context) {
    return new CallBuilder()
        .replaceClusterStatusAsync(
            context.getClusterResourceName(),
            context.getNamespace(),
            context.createReplacementClusterResource(),
            new ClusterResourceStatusReplaceResponseStep(context));
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
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      Step step = Optional.ofNullable(info.getDomain())
          .map(domain -> createUpdateClusterResourceStatusSteps(packet, info.getClusterResources()))
          .orElse(null);
      return doNext(step != null ? step : getNext(), packet);
    }
  }

  private static class ClusterResourceStatusReplaceResponseStep extends DefaultResponseStep<ClusterResource> {
    private final ReplaceClusterStatusContext context;

    ClusterResourceStatusReplaceResponseStep(ReplaceClusterStatusContext context) {
      this.context = context;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<ClusterResource> callResponse) {
      if (callResponse.getResult() != null) {
        packet.getSpi(DomainPresenceInfo.class).addClusterResource(callResponse.getResult());
      }
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<ClusterResource> callResponse) {
      if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
        return super.onFailure(packet, callResponse);
      } else {
        return onFailure(createRetry(), packet, callResponse);
      }
    }

    private Step createRetry() {
      return Step.chain(
          createClusterResourceRefreshStep(),
          new SingleClusterResourceStatusUpdateStep(context.getClusterName()));
    }

    private Step createClusterResourceRefreshStep() {
      return new CallBuilder().readClusterAsync(this.context.getClusterResourceName(),
          this.context.getNamespace(), new ReadClusterResponseStep());
    }
  }

  private static class RunInParallelStep extends Step {
    final Collection<StepAndPacket> statusUpdateSteps;

    RunInParallelStep(Collection<StepAndPacket> statusUpdateSteps) {
      this.statusUpdateSteps = statusUpdateSteps;
    }

    @Override
    public NextAction apply(Packet packet) {
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

    private ReplaceClusterStatusContext(@Nonnull Packet packet, @Nonnull ClusterResource resource) {
      this.packet = packet;
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      this.domain = info.getDomain();
      this.resource = resource;
    }

    Packet getPacket() {
      return packet;
    }

    DomainResource getDomain() {
      return domain;
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
          .withStatus(getNewStatus(domain, getClusterName()));
    }
  }

  private static class ReadClusterResponseStep extends ResponseStep<ClusterResource> {
    @Override
    public NextAction onSuccess(Packet packet, CallResponse<ClusterResource> callResponse) {
      if (callResponse.getResult() != null) {
        packet.getSpi(DomainPresenceInfo.class).addClusterResource(callResponse.getResult());
      }
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<ClusterResource> callResponse) {
      return callResponse.getStatusCode() == HTTP_NOT_FOUND
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
    public NextAction apply(Packet packet) {
      // Get the ClusterResource, that was refreshed, from DomainPresenceInfo.
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      ClusterResource res = info.getClusterResource(clusterName);
      return doNext(createUpdateClusterResourceStatusSteps(packet, Collections.singletonList(res)), packet);
    }
  }
}

