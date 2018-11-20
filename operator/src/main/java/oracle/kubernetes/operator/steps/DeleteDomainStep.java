// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.forDomainUid;

import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1ServiceList;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class DeleteDomainStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String namespace;
  private final String domainUID;

  public DeleteDomainStep(String namespace, String domainUID) {
    super(null);
    this.namespace = namespace;
    this.domainUID = domainUID;
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(
        Step.chain(
            deleteServices(),
            deletePods(),
            deletePersistentVolumes(),
            deletePersistentVolumeClaims()),
        packet);
  }

  private Step deleteServices() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUid(domainUID), CREATEDBYOPERATOR_LABEL)
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
        .withLabelSelectors(forDomainUid(domainUID), CREATEDBYOPERATOR_LABEL)
        .deleteCollectionPodAsync(namespace, new DefaultResponseStep<>(getNext()));
  }

  private Step deletePersistentVolumes() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUid(domainUID), CREATEDBYOPERATOR_LABEL)
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
        .withLabelSelectors(forDomainUid(domainUID), CREATEDBYOPERATOR_LABEL)
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
