// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimStatus;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.DomainProcessor;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/** Watches for pvcs to become Ready or leave Ready state. */
public class PvcWatcher {

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

}
