// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudgetList;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.LabelConstants.forDomainUidSelector;
import static oracle.kubernetes.operator.LabelConstants.getCreatedByOperatorSelector;

public class DeleteDomainStep extends Step {
  private final DomainPresenceInfo info;
  private final String namespace;
  private final String domainUid;

  /**
   * Construct delete domain step.
   * @param info domain presence
   * @param namespace namespace
   * @param domainUid domain UID
   */
  public DeleteDomainStep(DomainPresenceInfo info, String namespace, String domainUid) {
    super(null);
    this.info = info;
    this.namespace = namespace;
    this.domainUid = domainUid;
  }

  @Override
  public NextAction apply(Packet packet) {
    Step serverDownStep =
        Step.chain(
            deletePods(),
            deleteServices(),
            deletePodDisruptionBudgets(),
            ConfigMapHelper.deleteIntrospectorConfigMapStep(domainUid, namespace, getNext()));
    if (info != null) {
      List<DomainPresenceInfo.ServerShutdownInfo> ssi = new ArrayList<>();
      info.getServerPods().map(PodHelper::getPodServerName).collect(Collectors.toList())
              .forEach(s -> ssi.add(new DomainPresenceInfo.ServerShutdownInfo(s, null)));
      serverDownStep =
          new ServerDownIteratorStep(
              ssi,
              serverDownStep);
    }

    return doNext(serverDownStep, packet);
  }

  private Step deleteServices() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUidSelector(domainUid), getCreatedByOperatorSelector())
        .listServiceAsync(
            namespace,
            new ActionResponseStep<>() {
              public Step createSuccessStep(V1ServiceList result, Step next) {
                return new DeleteServiceListStep(result.getItems(), next);
              }
            });
  }

  private Step deletePodDisruptionBudgets() {
    return new CallBuilder()
            .withLabelSelectors(forDomainUidSelector(domainUid), getCreatedByOperatorSelector())
            .listPodDisruptionBudgetAsync(
                    namespace,
                    new ActionResponseStep<>() {
                    public Step createSuccessStep(V1beta1PodDisruptionBudgetList result, Step next) {
                      return new DeletePodDisruptionBudgetListStep(result.getItems(), next);
                    }
                  });
  }

  private Step deletePods() {
    return new CallBuilder()
        .withLabelSelectors(forDomainUidSelector(domainUid), getCreatedByOperatorSelector())
        .deleteCollectionPodAsync(namespace, new DefaultResponseStep<>(null));
  }

}
