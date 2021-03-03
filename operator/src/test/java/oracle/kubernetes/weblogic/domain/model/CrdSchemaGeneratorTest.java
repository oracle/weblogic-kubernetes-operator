// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_2;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_3;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_4;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_5;

public class CrdSchemaGeneratorTest {

  @ParameterizedTest
  @ValueSource(strings = {DOMAIN_V2_SAMPLE_YAML, DOMAIN_V2_SAMPLE_YAML_2, DOMAIN_V2_SAMPLE_YAML_3,
        DOMAIN_V2_SAMPLE_YAML_4, DOMAIN_V2_SAMPLE_YAML_5})
  void validateSchemaAgainstSamples(String fileName) throws IOException {
    final JsonNode jsonToValidate = convertToJson(fileName);
    final JsonSchema schema = createJsonSchema(Domain.class);

    Set<ValidationMessage> validationResult = schema.validate(jsonToValidate);
    if (!validationResult.isEmpty()) {
      throw new RuntimeException(toExceptionMessage(fileName, validationResult));
    }
  }

  @Nonnull
  private String toExceptionMessage(String fileName, Set<ValidationMessage> validationResult) {
    return Stream.concat(toPrefixStream(fileName), toFailureStream(validationResult))
          .collect(Collectors.joining(System.lineSeparator()));
  }

  @Nonnull
  private Stream<String> toPrefixStream(String fileName) {
    return Stream.of(fileName + " does not match schema:");
  }

  @Nonnull
  private Stream<String> toFailureStream(Set<ValidationMessage> validationResult) {
    return validationResult.stream().map(this::toIndentedString);
  }

  @Nonnull
  private String toIndentedString(ValidationMessage message) {
    return "  " + message;
  }

  @SuppressWarnings("SameParameterValue")
  private static JsonSchema createJsonSchema(Class<?> someClass) throws JsonProcessingException {
    final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
    final Map<String, Object> schema = CrdSchemaGenerator.createCrdSchemaGenerator().generate(someClass);
    return schemaFactory.getSchema(toJsonInputStream(schema));
  }

  private JsonNode convertToJson(String fileName) throws IOException {
    return new ObjectMapper().readTree(toJsonInputStream(readAsYaml(fileName)));
  }

  private Object readAsYaml(String fileName) throws IOException {
    InputStream yamlStream = inputStreamFromClasspath(fileName);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    return yamlReader.readValue(yamlStream, Object.class);
  }

  @Nonnull
  private static ByteArrayInputStream toJsonInputStream(Object obj) throws JsonProcessingException {
    return new ByteArrayInputStream(new ObjectMapper().writeValueAsBytes(obj));
  }

  private static InputStream inputStreamFromClasspath(String path) {
    return CrdSchemaGenerator.class.getResourceAsStream(path);
  }
}
