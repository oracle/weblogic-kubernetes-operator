// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import java.util.List;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.work.Step;

class DeletePersistentVolumeClaimListStep extends AbstractListStep<V1PersistentVolumeClaim> {
  DeletePersistentVolumeClaimListStep(List<V1PersistentVolumeClaim> items, Step next) {
    super(items, next);
  }

  @Override
  Step createDeleteStep(V1PersistentVolumeClaim item) {
    V1ObjectMeta meta = item.getMetadata();
    V1DeleteOptions deleteOptions = new V1DeleteOptions();
    return new CallBuilder()
        .deletePersistentVolumeClaimAsync(
            meta.getName(), meta.getNamespace(), deleteOptions, new DefaultResponseStep<>(this));
  }
}
