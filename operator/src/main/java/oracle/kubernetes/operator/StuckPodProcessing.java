// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import org.joda.time.DateTime;

public class StuckPodProcessing {


  private final MainDelegate mainDelegate;

  public StuckPodProcessing(MainDelegate mainDelegate) {
    this.mainDelegate = mainDelegate;
  }

  void checkStuckPods(String namespace) {
    Step step = new CallBuilder()
          .withLabelSelectors(LabelConstants.getCreatedbyOperatorSelector())
          .listPodAsync(namespace, new PodListProcessing(namespace, SystemClock.now()));
    mainDelegate.runSteps(step);
  }

  @SuppressWarnings("unchecked")
  private List<V1Pod> getStuckPodList(Packet packet) {
    return (List<V1Pod>) packet.computeIfAbsent("STUCK_PODS", k -> new ArrayList<>());
  }

  class PodListProcessing extends DefaultResponseStep<V1PodList> {

    private final DateTime now;

    public PodListProcessing(String namespace, DateTime dateTime) {
      super(new PodActionsStep(namespace));
      now = dateTime;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1PodList> callResponse) {
      callResponse.getResult().getItems().stream()
            .filter(pod -> isStuck(pod, now))
            .forEach(pod -> addStuckPodToPacket(packet, pod));
      
      return doContinueListOrNext(callResponse, packet);
    }

    private boolean isStuck(V1Pod pod, DateTime now)  {
      return getExpectedDeleteTime(pod).isBefore(now);
    }

    private DateTime getExpectedDeleteTime(V1Pod pod) {
      return getDeletionTimeStamp(pod).plusSeconds((int) getDeletionGracePeriodSeconds(pod));
    }

    private long getDeletionGracePeriodSeconds(V1Pod pod) {
      return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getDeletionGracePeriodSeconds).orElse(1L);
    }

    private DateTime getDeletionTimeStamp(V1Pod pod) {
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
    public NextAction apply(Packet packet) {
      final List<V1Pod> stuckPodList = getStuckPodList(packet);
      if (stuckPodList.isEmpty()) {
        return doNext(packet);
      } else {
        Collection<StepAndPacket> startDetails = new ArrayList<>();

        for (V1Pod pod : stuckPodList) {
          startDetails.add(new StepAndPacket(createDeletePodStep(pod), packet.clone()));
        }
        return doForkJoin(readExistingNamespaces(), packet, startDetails);
      }
    }

    @Nonnull
    private Step readExistingNamespaces() {
      return mainDelegate.getDomainNamespaces().readExistingResources(namespace, mainDelegate.getDomainProcessor());
    }

    private Step createDeletePodStep(V1Pod pod) {
      return new CallBuilder()
            .deletePodAsync(getName(pod), getNamespace(pod), getDomainUid(pod), null, new DefaultResponseStep<>());
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
}
