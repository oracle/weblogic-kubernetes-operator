// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.LabelConstants.forDomainUidSelector;
import static oracle.kubernetes.operator.LabelConstants.getCreatedbyOperatorSelector;

import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1ServiceList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class DeleteDomainStep extends Step {
  private final DomainPresenceInfo info;
  private final String namespace;
  private final String domainUID;

  public DeleteDomainStep(DomainPresenceInfo info, String namespace, String domainUID) {
    super(null);
    this.info = info;
    this.namespace = namespace;
    this.domainUID = domainUID;
  }

  @Override
  public NextAction apply(Packet packet) {
    Step serverDownStep =
        Step.chain(
            deletePods(),
            deleteServices(),
            deletePersistentVolumes(),
            deletePersistentVolumeClaims(),
            ConfigMapHelper.deleteDomainIntrospectorConfigMapStep(domainUID, namespace, getNext()));
    if (info != null) {
      Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop = new ArrayList<>();
      serversToStop.addAll(info.getServers().entrySet());
      serverDownStep = new ServerDownIteratorStep(serversToStop, serverDownStep);
    }

    return doNext(serverDownStep, packet);
  }

  private Step deleteServices() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUidSelector(domainUID), getCreatedbyOperatorSelector())
        .listServiceAsync(
            namespace,
            new ActionResponseStep<V1ServiceList>() {
              Step createSuccessStep(V1ServiceList result, Step next) {
                return new DeleteServiceListStep(result.getItems(), next);
              }
            });
  }

  private Step deletePods() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUidSelector(domainUID), getCreatedbyOperatorSelector())
        .deleteCollectionPodAsync(namespace, new DefaultResponseStep<>(null));
  }

  private Step deletePersistentVolumes() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUidSelector(domainUID), getCreatedbyOperatorSelector())
        .listPersistentVolumeAsync(
            new ActionResponseStep<V1PersistentVolumeList>() {
              @Override
              Step createSuccessStep(V1PersistentVolumeList result, Step next) {
                return new DeletePersistentVolumeListStep(result.getItems(), next);
              }
            });
  }

  private Step deletePersistentVolumeClaims() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUidSelector(domainUID), getCreatedbyOperatorSelector())
        .listPersistentVolumeClaimAsync(
            namespace,
            new ActionResponseStep<V1PersistentVolumeClaimList>() {
              @Override
              Step createSuccessStep(V1PersistentVolumeClaimList result, Step next) {
                return new DeletePersistentVolumeClaimListStep(result.getItems(), next);
              }
            });
  }

  /**
   * A response step which treats a NOT_FOUND status as success with a null result. On success with
   * a non-null response, runs a specified new step before continuing the step chain.
   */
  abstract static class ActionResponseStep<T> extends DefaultResponseStep<T> {
    ActionResponseStep() {}

    abstract Step createSuccessStep(T result, Step next);

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<T> callResponse) {
      return callResponse.getResult() == null
          ? doNext(packet)
          : doNext(createSuccessStep(callResponse.getResult(), getNext()), packet);
    }
  }
}
