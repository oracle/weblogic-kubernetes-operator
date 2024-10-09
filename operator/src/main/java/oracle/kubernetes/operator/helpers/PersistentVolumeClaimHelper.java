// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import oracle.kubernetes.operator.PvcAwaiterStepFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.PersistentVolume;
import oracle.kubernetes.weblogic.domain.model.PersistentVolumeClaim;
import oracle.kubernetes.weblogic.domain.model.PersistentVolumeClaimSpec;

import static oracle.kubernetes.common.logging.MessageKeys.PVC_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.PVC_EXISTS;
import static oracle.kubernetes.operator.DomainStatusUpdater.createKubernetesFailureSteps;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.PV_PVC_API_VERSION;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;

/**
 * Operations for dealing with persistent volume claims.
 */
public class PersistentVolumeClaimHelper {

  private PersistentVolumeClaimHelper() {
    // no-op
  }

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Factory for {@link Step} that verifies and creates persistent volume claim if needed.
   *
   * @param next the next step
   * @return Step for creating persistent volume claim
   */
  public static Step createPersistentVolumeClaimStep(Step next) {
    return new CreatePersistentVolumeClaimStep(next);
  }

  static class CreatePersistentVolumeClaimStep extends Step {

    CreatePersistentVolumeClaimStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      if (info.getDomain().getInitPvDomainPersistentVolumeClaim() != null) {
        return doNext(createContext(packet).readAndCreatePersistentVolumeClaimStep(getNext()), packet);
      }
      return doNext(packet);
    }

    protected PersistentVolumeClaimContext createContext(Packet packet) {
      return new PersistentVolumeClaimContext(this, packet);
    }
  }

  static class PersistentVolumeClaimContext extends StepContextBase {
    private final Step conflictStep;

    PersistentVolumeClaimContext(Step conflictStep, Packet packet) {
      super(packet.getSpi(DomainPresenceInfo.class));
      this.conflictStep = conflictStep;
    }

    Step getConflictStep() {
      return new PersistentVolumeClaimContext.ConflictStep();
    }

    Step readAndCreatePersistentVolumeClaimStep(Step next) {
      Step nextStep = next;
      if (Boolean.TRUE.equals(getWaitForPvcToBind())) {
        nextStep = waitForPvcToBind(getPersistentVolumeClaimName(), next);
      }
      return new CallBuilder().readPersistentVolumeClaimAsync(getPersistentVolumeClaimName(), info.getNamespace(),
              new ReadResponseStep(nextStep));
    }

    private Boolean getWaitForPvcToBind() {
      return getDomain().getInitPvDomainWaitForPvcToBind();
    }

    private String getPersistentVolumeClaimName() {
      return Optional.ofNullable(getInitPvDomainPersistentVolumeClaim())
          .map(PersistentVolumeClaim::getMetadata).map(V1ObjectMeta::getName).orElse(null);
    }

    private PersistentVolumeClaim getInitPvDomainPersistentVolumeClaim() {
      return getDomain().getInitPvDomainPersistentVolumeClaim();
    }

    DomainResource getDomain() {
      return info.getDomain();
    }

    String getDomainUid() {
      return getDomain().getDomainUid();
    }

    protected String getPVCCreatedMessageKey() {
      return PVC_CREATED;
    }

    private class CreateResponseStep extends ResponseStep<V1PersistentVolumeClaim> {
      private final String messageKey;

      CreateResponseStep(String messageKey, Step next) {
        super(next);
        this.messageKey = messageKey;
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1PersistentVolumeClaim> callResponse) {
        if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
          return updateDomainStatus(packet, callResponse);
        } else {
          return onFailure(getConflictStep(), packet, callResponse);
        }
      }

      private NextAction updateDomainStatus(Packet packet, CallResponse<V1PersistentVolumeClaim> callResponse) {
        return doNext(createKubernetesFailureSteps(callResponse), packet);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1PersistentVolumeClaim> callResponse) {
        clearExistingKubernetesNetworkException(packet);
        logPersistentVolumeClaimCreated(messageKey);
        addPersistentVolumeClaimToRecord(callResponse.getResult());
        return doNext(packet);
      }
    }

    private class ReadResponseStep extends DefaultResponseStep<V1PersistentVolumeClaim> {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1PersistentVolumeClaim> callResponse) {
        return callResponse.getStatusCode() == HTTP_NOT_FOUND
                ? onSuccess(packet, callResponse)
                : super.onFailure(packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1PersistentVolumeClaim> callResponse) {
        clearExistingKubernetesNetworkException(packet);
        DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
        V1PersistentVolumeClaim persistentVolumeClaim = callResponse.getResult();

        if (persistentVolumeClaim == null) {
          removePersistentVolumeClaimFromRecord();
          return doNext(createNewPersistentVolumeClaim(getNext()), packet);
        } else {
          logPersistentVolumeClaimExists(info.getDomain().getDomainUid(),
              info.getDomain().getInitPvDomainPersistentVolumeClaim());
          addPersistentVolumeClaimToRecord(callResponse.getResult());
        }
        return doNext(packet);
      }

      protected void logPersistentVolumeClaimExists(String domainUid, PersistentVolumeClaim pvc) {
        LOGGER.fine(PVC_EXISTS, pvc.getMetadata().getName(), domainUid);
      }

      private Step createNewPersistentVolumeClaim(Step next) {
        return createPersistentVolumeClaim(getPVCCreatedMessageKey(), next);
      }

      private Step createPersistentVolumeClaim(String messageKey, Step next) {
        return new CallBuilder()
            .createPersistentVolumeClaimAsync(
                info.getNamespace(),
                createModel(),
                new PersistentVolumeClaimHelper.PersistentVolumeClaimContext
                    .CreateResponseStep(messageKey, next));
      }
    }

    private class ConflictStep extends Step {
      @Override
      public NextAction apply(Packet packet) {
        return doNext(
                new CallBuilder().readPersistentVolumeClaimAsync(getPersistentVolumeClaimName(),
                    info.getNamespace(), new ReadResponseStep(conflictStep)), packet);
      }

      private String getPersistentVolumeClaimName() {
        return getInitPvDomainPersistentVolumeClaim().getMetadata().getName();
      }
    }

    public V1PersistentVolumeClaim createModel() {
      return createRecipe();
    }

    V1PersistentVolumeClaim createRecipe() {
      Map<String, String> labels = new HashMap<>();
      labels.put(CREATEDBYOPERATOR_LABEL, "true");
      labels.put(DOMAINUID_LABEL, info.getDomainUid());
      return new V1PersistentVolumeClaim()
              .metadata(getMetadata().labels(labels))
              .apiVersion(PV_PVC_API_VERSION)
              .spec(createSpec(getSpec()));
    }

    @Nonnull
    private PersistentVolumeClaimSpec getSpec() {
      return Optional.ofNullable(getInitPvDomainPersistentVolumeClaim()).map(PersistentVolumeClaim::getSpec)
          .orElse(new PersistentVolumeClaimSpec());
    }

    @Nonnull
    private V1ObjectMeta getMetadata() {
      return Optional.ofNullable(getInitPvDomainPersistentVolumeClaim())
          .map(PersistentVolumeClaim::getMetadata).orElse(new V1ObjectMeta());
    }

    private V1PersistentVolumeClaimSpec createSpec(PersistentVolumeClaimSpec spec) {
      return new V1PersistentVolumeClaimSpec().accessModes(Collections.singletonList(READ_WRITE_MANY))
          .storageClassName(spec.getStorageClassName())
          .volumeName(Optional.ofNullable(spec.getVolumeName()).orElse(getInitPvDomainPersistentVolumeName()))
          .resources(spec.getResources());
    }

    public String getInitPvDomainPersistentVolumeName() {
      return Optional.ofNullable(info.getDomain()).map(DomainResource::getInitPvDomainPersistentVolume)
          .map(PersistentVolume::getMetadata).map(V1ObjectMeta::getName).orElse(null);
    }

    protected void logPersistentVolumeClaimCreated(String messageKey) {
      LOGGER.info(messageKey, getPersistentVolumeClaimName(), getDomainUid());
    }

    protected void addPersistentVolumeClaimToRecord(@Nonnull V1PersistentVolumeClaim pvc) {
      info.addPersistentVolumeClaim(pvc);
    }

    protected void removePersistentVolumeClaimFromRecord() {
      info.removePersistentVolumeClaim(getPersistentVolumeClaimName());
    }
  }

  public static Step waitForPvcToBind(String pvcName, Step next) {
    return new WaitForPvcToBind(pvcName, next);
  }

  static class WaitForPvcToBind extends Step {

    private final String pvcName;

    WaitForPvcToBind(String pvcName, Step next) {
      super(next);
      this.pvcName = pvcName;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      V1PersistentVolumeClaim domainPvc = info.getPersistentVolumeClaim(pvcName);

      PvcAwaiterStepFactory pw = packet.getSpi(PvcAwaiterStepFactory.class);
      return doNext(pw.waitForReady(domainPvc, getNext()), packet);
    }
  }
}