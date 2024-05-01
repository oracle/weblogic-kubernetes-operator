// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.List;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.LabelConstants.forDomainUidSelector;
import static oracle.kubernetes.operator.LabelConstants.getCreatedByOperatorSelector;

public class DeleteDomainStep extends Step {


  @Override
  public @Nonnull Result apply(Packet packet) {
    return doNext(DomainPresenceInfo.fromPacket(packet).map(this::createNextSteps).orElseThrow(), packet);
  }

  @Nonnull
  private ServerDownIteratorStep createNextSteps(DomainPresenceInfo info) {
    return new ServerDownIteratorStep(getShutdownInfos(info), createDomainDownStep(info));
  }

  private Step createDomainDownStep(DomainPresenceInfo info) {
    return Step.chain(
        deletePods(info),
        deleteServices(info),
        deletePodDisruptionBudgets(info),
        deleteIntrospectorConfigMap(info));
  }

  @Nonnull
  private Step deleteIntrospectorConfigMap(DomainPresenceInfo info) {
    return ConfigMapHelper.deleteIntrospectorConfigMapStep(info.getDomainUid(), info.getNamespace(), getNext());
  }

  private List<DomainPresenceInfo.ServerShutdownInfo> getShutdownInfos(DomainPresenceInfo info) {
    return info.getServerPods()
        .map(PodHelper::getPodServerName)
        .map(this::createShutdownInfo)
        .toList();
  }

  private DomainPresenceInfo.ServerShutdownInfo createShutdownInfo(String serverName) {
    return new DomainPresenceInfo.ServerShutdownInfo(serverName, null);
  }

  private Step deleteServices(DomainPresenceInfo info) {
    return RequestBuilder.SERVICE.list(
        info.getNamespace(),
        new ListOptions().labelSelector(
            forDomainUidSelector(info.getDomainUid()) + "," + getCreatedByOperatorSelector()),
        new ActionResponseStep<>() {
          public Step createSuccessStep(V1ServiceList result, Step next) {
            return new DeleteServiceListStep(result.getItems(), next);
          }
        });
  }

  private Step deletePodDisruptionBudgets(DomainPresenceInfo info) {
    return RequestBuilder.PDB.list(info.getNamespace(),
        new ListOptions().labelSelector(
            forDomainUidSelector(
                info.getDomainUid()) + "," + getCreatedByOperatorSelector()), new ActionResponseStep<>() {
                  public Step createSuccessStep(V1PodDisruptionBudgetList result, Step next) {
                    return new DeletePodDisruptionBudgetListStep(result.getItems(), next);
                  }
                });
  }

  private Step deletePods(DomainPresenceInfo info) {
    return RequestBuilder.POD.deleteCollection(
        info.getNamespace(),
        new ListOptions().labelSelector(
            forDomainUidSelector(info.getDomainUid()) + "," + getCreatedByOperatorSelector()),
        new DeleteOptions(), new DefaultResponseStep<>(null));
  }

}
