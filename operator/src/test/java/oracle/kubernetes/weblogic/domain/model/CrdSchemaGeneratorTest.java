// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.meterware.simplestub.Memento;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import oracle.kubernetes.json.Feature;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_2;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_3;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_4;
import static oracle.kubernetes.weblogic.domain.model.DomainTestBase.DOMAIN_V2_SAMPLE_YAML_5;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CrdSchemaGeneratorTest {

  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TuningParametersStub.install());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

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

  @Test
  public void whenMixOfEnabledDisabledFeatures_validateSchemaOnlyContainsEnabled() {
    final Map<String, Object> schema = createSchema(SomeObject.class);

    assertThat(schema, hasKey("properties"));
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    assertThat(properties, hasKey("enabled"));
    assertThat(properties, not(hasKey("disabled")));
  }

  private static class SomeObject {
    @Feature(TuningParametersStub.ENABLED_FEATURE)
    private ClassForEnabledFeature enabled;
    @Feature(TuningParametersStub.DISABLED_FEATURE)
    private ClassForDisabledFeature disabled;
    private String five;
  }

  private static class ClassForEnabledFeature {
    private String one;
    private String two;
  }

  private static class ClassForDisabledFeature {
    private String three;
    private String four;
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

  private static Map<String, Object> createSchema(Class<?> someClass) {
    return CrdSchemaGenerator.createCrdSchemaGenerator().generate(someClass);
  }

  @SuppressWarnings("SameParameterValue")
  private static JsonSchema createJsonSchema(Class<?> someClass) throws JsonProcessingException {
    final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
    final Map<String, Object> schema = createSchema(someClass);
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
