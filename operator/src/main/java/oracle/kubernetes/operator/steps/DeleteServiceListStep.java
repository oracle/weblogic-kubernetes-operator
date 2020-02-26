// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;

import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.work.Step;

/**
 * A step which will delete each entry in the specified collection. It does so by chaining back to
 * itself in the response step, in order to process the next entry in the iterator.
 */
public class DeleteServiceListStep extends AbstractListStep<V1Service> {

  DeleteServiceListStep(Collection<V1Service> c, Step next) {
    super(c, next);
  }

  Step createDeleteStep(V1Service service) {
    V1ObjectMeta meta = service.getMetadata();
    V1DeleteOptions deleteOptions = new V1DeleteOptions();
    return new CallBuilder()
        .deleteServiceAsync(
            meta.getName(), meta.getNamespace(), deleteOptions, new DefaultResponseStep<>(this));
  }
}
