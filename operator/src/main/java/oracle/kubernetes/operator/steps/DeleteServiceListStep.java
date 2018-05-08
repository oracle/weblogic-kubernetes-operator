// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1Status;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class DeleteServiceListStep extends Step {
  private final Iterator<V1Service> it;

  public DeleteServiceListStep(Collection<V1Service> c, Step next) {
    super(next);
    this.it = c.iterator();
  }

  @Override
  public NextAction apply(Packet packet) {
    CallBuilderFactory factory =
        ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);

    if (it.hasNext()) {
      V1Service service = it.next();
      V1ObjectMeta meta = service.getMetadata();
      Step delete =
          factory
              .create()
              .deleteServiceAsync(
                  meta.getName(),
                  meta.getNamespace(),
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
