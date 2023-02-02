// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;

public class CrdHelperTestBase {
  public static final SemanticVersion PRODUCT_VERSION = new SemanticVersion(3, 0, 0);

  protected V1CustomResourceDefinition defineCrd(SemanticVersion operatorVersion, String crdName) {
    return new V1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion, crdName))
        .spec(createSpec());
  }

  @SuppressWarnings("SameParameterValue")
  private V1ObjectMeta createMetadata(SemanticVersion operatorVersion, String crdName) {
    return new V1ObjectMeta()
        .name(crdName)
        .putLabelsItem(LabelConstants.OPERATOR_VERSION,
            Optional.ofNullable(operatorVersion).map(SemanticVersion::toString).orElse(null));
  }

  private V1CustomResourceDefinitionSpec createSpec() {
    return new V1CustomResourceDefinitionSpec()
        .group(KubernetesConstants.DOMAIN_GROUP)
        .scope("Namespaced")
        .addVersionsItem(new V1CustomResourceDefinitionVersion()
            .served(true).name(KubernetesConstants.OLD_DOMAIN_VERSION))
        .names(
            new V1CustomResourceDefinitionNames()
                .plural(KubernetesConstants.DOMAIN_PLURAL)
                .singular(KubernetesConstants.DOMAIN_SINGULAR)
                .kind(KubernetesConstants.DOMAIN)
                .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT)));
  }
}
