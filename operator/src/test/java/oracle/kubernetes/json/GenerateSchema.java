// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

public class GenerateSchema {

  /**
   * generate schema.
   * @param args args
   * @throws Exception on failure
   */
  public static void main(String... args) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper);

    JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(DomainResource.class);

    String jsonSchemaAsString =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema);
    System.out.println(jsonSchemaAsString);
  }
}
