// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1Ingress;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class DeleteIngressListStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Iterator<V1beta1Ingress> it;

  public DeleteIngressListStep(Collection<V1beta1Ingress> c, Step next) {
    super(next);
    this.it = c.iterator();
  }

  @Override
  public NextAction apply(Packet packet) {
    CallBuilderFactory factory = new CallBuilderFactory();

    if (it.hasNext()) {
      V1beta1Ingress v1beta1Ingress = it.next();
      V1ObjectMeta meta = v1beta1Ingress.getMetadata();
      String ingressName = meta.getName();
      String namespace = meta.getNamespace();
      LOGGER.finer(MessageKeys.REMOVING_INGRESS, ingressName, namespace);
      Step delete =
          factory
              .create()
              .deleteIngressAsync(
                  ingressName,
                  meta.getNamespace(),
                  new V1DeleteOptions(),
                  new ResponseStep<V1Status>(this) {
                    @Override
                    public NextAction onFailure(
                        Packet packet,
                        ApiException e,
                        int statusCode,
                        Map<String, List<String>> responseHeaders) {
                      if (statusCode == CallBuilder.NOT_FOUND) {
                        return onSuccess(packet, null, statusCode, responseHeaders);
                      }
                      return super.onFailure(packet, e, statusCode, responseHeaders);
                    }

                    @Override
                    public NextAction onSuccess(
                        Packet packet,
                        V1Status result,
                        int statusCode,
                        Map<String, List<String>> responseHeaders) {
                      return doNext(packet);
                    }
                  });
      return doNext(delete, packet);
    }
    return doNext(packet);
  }
}
