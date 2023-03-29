// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimStatus;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** Watches for pvcs to become Ready or leave Ready state. */
public class PvcWatcher implements PvcAwaiterStepFactory {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // Map of Pvc name to Runnable
  private final Map<String,Consumer<V1PersistentVolumeClaim>> completeCallbackRegistrations = new ConcurrentHashMap<>();
  public final DomainProcessor processor;

  PvcWatcher(DomainProcessor processor) {
    this.processor = processor;
  }

  /**
   * Test if PersistentVolumeClaim is bound.
   * @param pvc PersistentVolumeClaim
   * @return true, if bound
   */
  public static boolean isBound(V1PersistentVolumeClaim pvc) {
    if (pvc == null) {
      return false;
    }

    V1PersistentVolumeClaimStatus status = pvc.getStatus();
    LOGGER.fine("Status phase of pvc " + getName(pvc) + " is : " + getPhase(status));
    if (status != null) {
      String phase = getPhase(status);
      if (ProcessingConstants.BOUND.equals(phase)) {
        LOGGER.fine(MessageKeys.PVC_IS_BOUND, getName(pvc));
        return true;
      }
    }
    return false;
  }

  private static String getPhase(V1PersistentVolumeClaimStatus status) {
    return Optional.ofNullable(status).map(V1PersistentVolumeClaimStatus::getPhase).orElse(null);
  }

  private static String getName(V1PersistentVolumeClaim pvc) {
    return Optional.ofNullable(pvc.getMetadata()).map(V1ObjectMeta::getName).orElse(null);
  }

  /**
   * Waits until the PersistentVolumeClaim is Ready.
   *
   * @param pvc PersistentVolumeClaim to watch
   * @param next Next processing step once PersistentVolumeClaim is ready
   * @return Asynchronous step
   */
  @Override
  public Step waitForReady(V1PersistentVolumeClaim pvc, Step next) {
    return new WaitForPvcReadyStep(pvc, next);
  }

  private class WaitForPvcReadyStep extends WaitForReadyStep<V1PersistentVolumeClaim> {

    private WaitForPvcReadyStep(V1PersistentVolumeClaim pvc, Step next) {
      super(pvc, next);
    }

    // A pvc is considered ready once the status is Bound.
    @Override
    boolean isReady(V1PersistentVolumeClaim pvc) {
      return isBound(pvc);
    }

    @Override
    boolean shouldProcessCallback(V1PersistentVolumeClaim pvc) {
      return isReady(pvc);
    }

    @Override
    V1ObjectMeta getMetadata(V1PersistentVolumeClaim resource) {
      return resource.getMetadata();
    }

    private void addOnModifiedCallback(String pvcName, Consumer<V1PersistentVolumeClaim> callback) {
      completeCallbackRegistrations.put(pvcName, callback);
    }

    @Override
    void addCallback(String name, Consumer<V1PersistentVolumeClaim> callback) {
      addOnModifiedCallback(name, callback);
    }

    private void removeOnModifiedCallback(String pvcName, Consumer<V1PersistentVolumeClaim> callback) {
      completeCallbackRegistrations.remove(pvcName, callback);
    }

    @Override
    void removeCallback(String name, Consumer<V1PersistentVolumeClaim> callback) {
      removeOnModifiedCallback(name, callback);
    }

    @Override
    Step createReadAsyncStep(String name, String namespace, String domainUid,
                             ResponseStep<V1PersistentVolumeClaim> responseStep) {
      return new CallBuilder().readPersistentVolumeClaimAsync(name, namespace, responseStep);
    }

    @Override
    void logWaiting(String name) {
      LOGGER.fine(MessageKeys.WAITING_FOR_PVC_TO_BIND, name);
    }

    @Override
    protected DefaultResponseStep<V1PersistentVolumeClaim> resumeIfReady(Callback callback) {
      return new DefaultResponseStep<>(null) {
        @Override
        public NextAction onSuccess(Packet packet, CallResponse<V1PersistentVolumeClaim> callResponse) {
          DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
          V1PersistentVolumeClaim pvc = callResponse.getResult();
          if (isReady(callResponse.getResult()) || callback.didResumeFiber()) {
            callback.proceedFromWait(callResponse.getResult());
            processor.updateDomainStatus(pvc, info);
            return doNext(packet);
          }
          if (callback.getAndIncrementRecheckCount() % ProcessingConstants.PVC_WAIT_STATUS_UPDATE_COUNT == 0) {
            processor.updateDomainStatus(pvc, info);
          }
          return doDelay(createReadAndIfReadyCheckStep(callback), packet,
                  getWatchBackstopRecheckDelaySeconds(), TimeUnit.SECONDS);
        }
      };
    }
  }
}
