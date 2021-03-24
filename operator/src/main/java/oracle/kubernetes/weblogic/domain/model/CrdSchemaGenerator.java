// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import io.kubernetes.client.custom.Quantity;
import oracle.kubernetes.json.SchemaGenerator;

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
    return generator;
  }
}
