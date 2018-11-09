// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.LabelConstants.forDomainUid;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;

public class ListPersistentVolumeClaimStep extends Step {
  public ListPersistentVolumeClaimStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    V1ObjectMeta meta = dom.getMetadata();
    DomainSpec spec = dom.getSpec();
    String namespace = meta.getNamespace();

    String domainUID = spec.getDomainUID();

    Step list =
        new CallBuilder()
            .withLabelSelectors(forDomainUid(domainUID))
            .listPersistentVolumeClaimAsync(
                namespace,
                new ResponseStep<V1PersistentVolumeClaimList>(getNext()) {
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
                      V1PersistentVolumeClaimList result,
                      int statusCode,
                      Map<String, List<String>> responseHeaders) {
                    info.setClaims(result);
                    return doNext(packet);
                  }
                });

    return doNext(list, packet);
  }
}
