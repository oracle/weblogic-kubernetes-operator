// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Collections;
import java.util.Optional;

import io.kubernetes.client.custom.Quantity;
import oracle.kubernetes.json.SchemaGenerator;
import oracle.kubernetes.operator.TuningParameters;

public class CrdSchemaGenerator {

  /**
   * Creates a schema generator, suitably customized for generating Kubernetes CRD schemas.
   */
  public static SchemaGenerator createCrdSchemaGenerator() {
    SchemaGenerator generator = new SchemaGenerator();
    generator.defineAdditionalProperties(Quantity.class, "string");
    generator.setForbidAdditionalProperties(false);
    generator.setSupportObjectReferences(false);
    generator.setIncludeSchemaReference(false);
    generator.addPackageToSuppressDescriptions("io.kubernetes.client.openapi.models");
    generator.defineEnabledFeatures(
        Optional.ofNullable(TuningParameters.getInstance())
            .map(TuningParameters::getFeatureGates)
            .map(TuningParameters.FeatureGates::getEnabledFeatures)
            .orElse(Collections.emptyList()));
    return generator;
  }
}
