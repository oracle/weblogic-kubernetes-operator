// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;

import static oracle.kubernetes.common.logging.MessageKeys.POD_FORCE_DELETED;

/**
 * Under certain circumstances, when a Kubernetes node goes down, it may mark its pods as terminating, but never
 * actually remove them. This code detects such cases, deletes the pods and triggers the necessary make-right flows.
 */
public class StuckPodProcessing {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final MainDelegate mainDelegate;

  public StuckPodProcessing(MainDelegate mainDelegate) {
    this.mainDelegate = mainDelegate;
  }

  void checkStuckPods(String namespace) {
    Step step = RequestBuilder.POD.list(namespace,
        new ListOptions().labelSelector(LabelConstants.getCreatedByOperatorSelector()),
        new PodListProcessing(namespace, SystemClock.now()));
    mainDelegate.runSteps(BaseMain.createPacketWithLoggingContext(namespace), step, null);
  }

  @SuppressWarnings("unchecked")
  private List<V1Pod> getStuckPodList(Packet packet) {
    return (List<V1Pod>) packet.computeIfAbsent("STUCK_PODS", k -> new ArrayList<>());
  }

  class PodListProcessing extends DefaultResponseStep<V1PodList> {

    private final OffsetDateTime now;

    public PodListProcessing(String namespace, OffsetDateTime dateTime) {
      super(new PodActionsStep(namespace));
      now = dateTime;
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<V1PodList> callResponse) {
      callResponse.getObject().getItems().stream()
            .filter(pod -> isStuck(pod, now))
            .forEach(pod -> addStuckPodToPacket(packet, pod));
      
      return doContinueListOrNext(callResponse, packet);
    }

    private boolean isStuck(V1Pod pod, OffsetDateTime now)  {
      return getExpectedDeleteTime(pod).isBefore(now);
    }

    private OffsetDateTime getExpectedDeleteTime(V1Pod pod) {
      return getDeletionTimeStamp(pod).plusSeconds((int) getDeletionGracePeriodSeconds(pod));
    }

    private long getDeletionGracePeriodSeconds(V1Pod pod) {
      return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getDeletionGracePeriodSeconds).orElse(1L);
    }

    private OffsetDateTime getDeletionTimeStamp(V1Pod pod) {
      return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getDeletionTimestamp).orElse(SystemClock.now());
    }

    private void addStuckPodToPacket(Packet packet, V1Pod stuckPod) {
      getStuckPodList(packet).add(stuckPod);
    }
  }

  class PodActionsStep extends Step {

    private final String namespace;

    public PodActionsStep(String namespace) {
      this.namespace = namespace;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      final List<V1Pod> stuckPodList = getStuckPodList(packet);
      if (stuckPodList.isEmpty()) {
        return doNext(packet);
      } else {
        Collection<Fiber.StepAndPacket> startDetails = new ArrayList<>();

        for (V1Pod pod : stuckPodList) {
          startDetails.add(new Fiber.StepAndPacket(createForcedDeletePodStep(pod), packet.copy()));
        }
        return doForkJoin(readExistingNamespaces(), packet, startDetails);
      }
    }

    @Nonnull
    private Step readExistingNamespaces() {
      return mainDelegate.getDomainNamespaces().readExistingResources(
          namespace, mainDelegate.getDomainProcessor());
    }

    private Step createForcedDeletePodStep(V1Pod pod) {
      return RequestBuilder.POD.delete(getNamespace(pod), getName(pod),
          (DeleteOptions) new DeleteOptions().gracePeriodSeconds(0L),
          new ForcedDeleteResponseStep(getName(pod), getNamespace(pod), getDomainUid(pod)));
    }

    private String getName(V1Pod pod) {
      return Objects.requireNonNull(pod.getMetadata()).getName();
    }

    private String getNamespace(V1Pod pod) {
      return Objects.requireNonNull(pod.getMetadata()).getNamespace();
    }

    private String getDomainUid(V1Pod pod) {
      return PodHelper.getPodDomainUid(pod);
    }
  }

  static class ForcedDeleteResponseStep extends DefaultResponseStep<V1Pod> {

    private final String name;
    private final String namespace;
    private final String domainUID;

    public ForcedDeleteResponseStep(String name, String namespace, String domainUID) {
      this.name = name;
      this.namespace = namespace;
      this.domainUID = domainUID;
    }

    @Override
    @SuppressWarnings("try")
    public Result onSuccess(Packet packet, KubernetesApiResponse<V1Pod> callResponse) {
      try (ThreadLoggingContext ignored =
               ThreadLoggingContext.setThreadContext().namespace(namespace).domainUid(domainUID)) {
        LOGGER.info(POD_FORCE_DELETED, name, namespace);
      }
      return super.onSuccess(packet, callResponse);
    }
  }

}
