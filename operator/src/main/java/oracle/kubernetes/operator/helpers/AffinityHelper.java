// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;

public class AffinityHelper {

  public static final String CLUSTER_NAME_VARIABLE = "$(CLUSTER_NAME)";
  public static final String DOMAIN_UID_VARIABLE = "$(DOMAIN_UID)";

  private String clusterName = null;
  private String domainUID = null;

  public AffinityHelper clusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  public AffinityHelper domainUID(String domainUID) {
    this.domainUID = domainUID;
    return this;
  }

  /**
   * Creates default anti-affinity using cluster-name and domain-uid variables.
   */
  public static V1Affinity getDefaultAntiAffinity() {
    return new AffinityHelper().clusterName(CLUSTER_NAME_VARIABLE).domainUID(DOMAIN_UID_VARIABLE)
        .getAntiAffinity();
  }

  /**
   * Creates default anti-affinity for the cluster-name and domain uid.
   */
  public V1Affinity getAntiAffinity() {
    return new V1Affinity().podAntiAffinity(
        new V1PodAntiAffinity().addPreferredDuringSchedulingIgnoredDuringExecutionItem(
            new V1WeightedPodAffinityTerm()
                .weight(100)
                .podAffinityTerm(
                    new V1PodAffinityTerm()
                        .labelSelector(getLabelSelector())
                        .topologyKey("kubernetes.io/hostname")
                )));
  }

  private V1LabelSelector getLabelSelector() {
    List<V1LabelSelectorRequirement> labelSelectorRequirements = new ArrayList<>();
    Optional.ofNullable(clusterName)
        .ifPresent(c -> labelSelectorRequirements.add(
            new V1LabelSelectorRequirement().key(CLUSTERNAME_LABEL).operator("In").addValuesItem(c)));
    Optional.ofNullable(domainUID)
        .ifPresent(domainUid -> labelSelectorRequirements.add(
            new V1LabelSelectorRequirement().key(DOMAINUID_LABEL).operator("In").addValuesItem(domainUid)));

    return new V1LabelSelector()
        .matchExpressions(labelSelectorRequirements);
  }
}