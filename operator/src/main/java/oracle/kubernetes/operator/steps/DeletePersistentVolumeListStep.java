// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;

import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.work.Step;

class DeletePersistentVolumeListStep extends AbstractListStep<V1PersistentVolume> {
  DeletePersistentVolumeListStep(Collection<V1PersistentVolume> c, Step next) {
    super(c, next);
  }

  Step createDeleteStep(V1PersistentVolume volume) {
    V1ObjectMeta meta = volume.getMetadata();
    V1DeleteOptions deleteOptions = new V1DeleteOptions();
    return new CallBuilder()
        .deletePersistentVolumeAsync(
            meta.getName(), deleteOptions, new DefaultResponseStep<>(this));
  }
}
