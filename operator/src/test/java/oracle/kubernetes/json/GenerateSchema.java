// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class GenerateSchema {

  /**
   * generate schema.
   * @param args args
   * @throws Exception on failure
   */
  public static void main(String... args) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper);

    // If using JsonSchema to generate HTML5 GUI:
    // JsonSchemaGenerator html5 = new JsonSchemaGenerator(objectMapper,
    // JsonSchemaConfig.html5EnabledSchema() );

    // If you want to configure it manually:
    // JsonSchemaConfig config = JsonSchemaConfig.create(...);
    // JsonSchemaGenerator generator = new JsonSchemaGenerator(objectMapper, config);

    JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(Domain.class);

    String jsonSchemaAsString =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema);
    System.out.println(jsonSchemaAsString);
  }
}
