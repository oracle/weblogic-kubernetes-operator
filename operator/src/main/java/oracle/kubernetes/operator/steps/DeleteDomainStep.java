// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1IngressList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class DeleteDomainStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String namespace;
  private final String domainUID;

  public DeleteDomainStep(String namespace, String domainUID) {
    super(null);
    this.namespace = namespace;
    this.domainUID = domainUID;
  }

  @Override
  public NextAction apply(Packet packet) {
    CallBuilderFactory factory =
        ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);

    Step deletePods =
        factory
            .create()
            .with(
                $ -> {
                  $.labelSelector =
                      LabelConstants.DOMAINUID_LABEL
                          + "="
                          + domainUID
                          + ","
                          + LabelConstants.CREATEDBYOPERATOR_LABEL;
                })
            .deleteCollectionPodAsync(
                namespace,
                new ResponseStep<V1Status>(next) {
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

    Step serviceList =
        factory
            .create()
            .with(
                $ -> {
                  $.labelSelector =
                      LabelConstants.DOMAINUID_LABEL
                          + "="
                          + domainUID
                          + ","
                          + LabelConstants.CREATEDBYOPERATOR_LABEL;
                })
            .listServiceAsync(
                namespace,
                new ResponseStep<V1ServiceList>(deletePods) {
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
                      V1ServiceList result,
                      int statusCode,
                      Map<String, List<String>> responseHeaders) {
                    if (result != null) {
                      return doNext(
                          new DeleteServiceListStep(result.getItems(), deletePods), packet);
                    }
                    return doNext(packet);
                  }
                });

    LOGGER.finer(MessageKeys.LIST_INGRESS_FOR_DOMAIN, domainUID, namespace);
    Step deleteIngress =
        factory
            .create()
            .with(
                $ -> {
                  $.labelSelector =
                      LabelConstants.DOMAINUID_LABEL
                          + "="
                          + domainUID
                          + ","
                          + LabelConstants.CREATEDBYOPERATOR_LABEL;
                })
            .listIngressAsync(
                namespace,
                new ResponseStep<V1beta1IngressList>(serviceList) {
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
                      V1beta1IngressList result,
                      int statusCode,
                      Map<String, List<String>> responseHeaders) {
                    if (result != null) {

                      return doNext(
                          new DeleteIngressListStep(result.getItems(), serviceList), packet);
                    }
                    return doNext(packet);
                  }
                });

    return doNext(deleteIngress, packet);
  }
}
