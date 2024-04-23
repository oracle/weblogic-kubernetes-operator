// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.work.Step;

/**
 * A step which will delete each entry in the specified collection. It does so by chaining back to
 * itself in the response step, in order to process the next entry in the iterator.
 */
public class DeletePodDisruptionBudgetListStep extends AbstractListStep<V1PodDisruptionBudget> {

  DeletePodDisruptionBudgetListStep(Collection<V1PodDisruptionBudget> c, Step next) {
    super(c, next);
  }

  Step createActionStep(V1PodDisruptionBudget pdb) {
    V1ObjectMeta meta = pdb.getMetadata();
    return RequestBuilder.PDB.delete(meta.getNamespace(), meta.getName(), new DefaultResponseStep<>(this));
  }
}
