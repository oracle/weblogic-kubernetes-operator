// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1Ingress;
import java.util.Collection;
import java.util.Iterator;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * A step which will delete each entry in the specified collection. It does so by chaining back to
 * itself in the response step, in order to process the next entry in the iterator.
 */
public class DeleteIngressListStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Iterator<V1beta1Ingress> it;

  DeleteIngressListStep(Collection<V1beta1Ingress> c, Step next) {
    super(next);
    this.it = c.iterator();
  }

  @Override
  public NextAction apply(Packet packet) {
    if (it.hasNext()) {
      return doNext(createDeleteStep(it.next()), packet);
    } else {
      return doNext(packet);
    }
  }

  private Step createDeleteStep(V1beta1Ingress v1beta1Ingress) {
    V1ObjectMeta meta = v1beta1Ingress.getMetadata();
    LOGGER.finer(MessageKeys.REMOVING_INGRESS, meta.getName(), meta.getNamespace());
    return new CallBuilder()
        .deleteIngressAsync(
            meta.getName(),
            meta.getNamespace(),
            new V1DeleteOptions(),
            new DefaultResponseStep<>(this));
  }
}
