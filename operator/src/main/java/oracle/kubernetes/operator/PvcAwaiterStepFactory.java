// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import oracle.kubernetes.operator.work.Step;

public interface PvcAwaiterStepFactory {
  /**
   * Waits until the PersistentVolumeClaim is bound.
   *
   * @param pvc PersistentVolumeClaim to watch
   * @param next Next processing step once Pod is ready
   * @return Asynchronous step
   */
  Step waitForReady(V1PersistentVolumeClaim pvc, Step next);
}
