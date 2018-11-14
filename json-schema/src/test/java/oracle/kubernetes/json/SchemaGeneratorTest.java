// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class SchemaGeneratorTest {

  private static final String K8S_SCHEMA_URL =
      "https://github.com/garethr/kubernetes-json-schema/blob/master/v1.9.0/_definitions.json";
  private static final String K8S_CACHE_FILE = "caches/kubernetes-1.9.0.json";
  private SchemaGenerator generator = new SchemaGenerator();

  private URL schemaUrl;
  private URL cacheUrl;

  @Before
  public void setUp() throws Exception {
    schemaUrl = new URL(K8S_SCHEMA_URL);
    cacheUrl = getClass().getResource(K8S_CACHE_FILE);
  }

  @Test
  public void generateSchemaForBoolean() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("unAnnotatedBoolean"));

    assertThat(schema, hasJsonPath("$.unAnnotatedBoolean.type", equalTo("boolean")));
  }

  private Object generateForField(Field field) {
    Map<String, Object> result = new HashMap<>();
    generator.generateFieldIn(result, field);
    return result;
  }

  @SuppressWarnings("unused")
  private boolean unAnnotatedBoolean;

  @Test
  public void generateSchemaForInteger() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("unAnnotatedInteger"));

    assertThat(schema, hasJsonPath("$.unAnnotatedInteger.type", equalTo("number")));
  }

  @SuppressWarnings("unused")
  private int unAnnotatedInteger;

  @Test
  public void generateSchemaForLong() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("unAnnotatedLong"));

    assertThat(schema, hasJsonPath("$.unAnnotatedLong.type", equalTo("number")));
  }

  @SuppressWarnings("unused")
  private long unAnnotatedLong;

  @Test
  public void generateSchemaForString() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("unAnnotatedString"));

    assertThat(schema, hasJsonPath("$.unAnnotatedString.type", equalTo("string")));
  }

  @SuppressWarnings("unused")
  private String unAnnotatedString;

  @Test
  public void generateSchemaForIntArray() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("intArray"));

    assertThat(schema, hasJsonPath("$.intArray.type", equalTo("array")));
    assertThat(schema, hasJsonPath("$.intArray.items.type", equalTo("number")));
  }

  @SuppressWarnings("unused")
  private int[] intArray;

  @Test
  public void generateSchemaForEnum() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("colors"));

    assertThat(schema, hasJsonPath("$.colors.type", equalTo("string")));
    assertThat(
        schema, hasJsonPath("$.colors.enum", arrayContainingInAnyOrder("RED", "YELLOW", "GREEN")));
  }

  @SuppressWarnings("unused")
  private enum TrafficLightColors {
    RED,
    YELLOW,
    GREEN
  }

  @SuppressWarnings("unused")
  private TrafficLightColors colors;

  @Test
  public void generateSchemaForAnnotatedDouble() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("annotatedDouble"));

    assertThat(schema, hasJsonPath("$.annotatedDouble.type", equalTo("number")));
    assertThat(schema, hasJsonPath("$.annotatedDouble.description", equalTo("An annotated field")));
  }

  @SuppressWarnings("unused")
  @Description("An annotated field")
  private Double annotatedDouble;

  @Test
  public void doNotGenerateSchemaForStatics() {
    Object schema = generator.generate(SimpleObject.class);

    assertThat(schema, hasNoJsonPath("$.properties.staticInt"));
    assertThat(schema, hasJsonPath("$.required", not(arrayContaining("staticInt"))));
  }

  @SuppressWarnings("unused")
  @Test
  public void generateSchemaForSimpleObject() {
    Object schema = generator.generate(SimpleObject.class);

    assertThat(
        schema, hasJsonPath("$.$schema", equalTo("http://json-schema.org/draft-04/schema#")));
    assertThat(schema, hasJsonPath("$.type", equalTo("object")));
    assertThat(schema, hasJsonPath("$.additionalProperties", equalTo("false")));
    assertThat(schema, hasJsonPath("$.properties.aBoolean.type", equalTo("boolean")));
    assertThat(schema, hasJsonPath("$.properties.aString.type", equalTo("string")));
    assertThat(schema, hasJsonPath("$.properties.aBoolean.description", equalTo("A flag")));
    assertThat(schema, hasJsonPath("$.properties.aString.description", equalTo("A string")));
  }

  @Test
  public void generateReferenceForSimpleObjectField() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("simpleObject"));

    assertThat(schema, hasJsonPath("$.simpleObject.$ref", equalTo("#/definitions/SimpleObject")));
  }

  @Test
  public void whenAdditionalPropertiesDisabled_doNotGenerateTheProperty() {
    generator.setIncludeAdditionalProperties(false);

    Object schema = generator.generate(SimpleObject.class);

    assertThat(schema, hasNoJsonPath("$.additionalProperties"));
  }

  @SuppressWarnings("unused")
  private SimpleObject simpleObject;

  @Test
  public void generateSchemaForDerivedObject() {
    Object schema = generator.generate(DerivedObject.class);

    assertThat(schema, hasJsonPath("$.type", equalTo("object")));
    assertThat(schema, hasJsonPath("$.additionalProperties", equalTo("false")));
    assertThat(schema, hasJsonPath("$.properties.aBoolean.type", equalTo("boolean")));
    assertThat(schema, hasJsonPath("$.properties.aString.type", equalTo("string")));
    assertThat(schema, hasJsonPath("$.properties.anInt.type", equalTo("number")));
    assertThat(schema, hasJsonPath("$.properties.aBoolean.description", equalTo("A flag")));
    assertThat(schema, hasJsonPath("$.properties.aString.description", equalTo("A string")));
    assertThat(schema, hasJsonPath("$.properties.anInt.description", equalTo("An int")));
    assertThat(schema, hasJsonPath("$.properties.depth.type", equalTo("number")));
    assertThat(
        schema, hasJsonPath("$.required", arrayContainingInAnyOrder("aBoolean", "anInt", "depth")));
  }

  @Test
  public void generateDefinitionsForReferencedClasses() {
    Object schema = generator.generate(ReferencingObject.class);

    assertThat(
        schema, hasJsonPath("$.properties.simple.$ref", equalTo("#/definitions/SimpleObject")));
    assertThat(
        schema, hasJsonPath("$.properties.derived.$ref", equalTo("#/definitions/DerivedObject")));
    assertThat(
        schema,
        hasJsonPath("$.definitions.SimpleObject.properties.aBoolean.type", equalTo("boolean")));
    assertThat(
        schema,
        hasJsonPath("$.definitions.SimpleObject.properties.aString.type", equalTo("string")));
    assertThat(
        schema,
        hasJsonPath("$.definitions.DerivedObject.properties.aString.type", equalTo("string")));
    assertThat(
        schema,
        hasJsonPath("$.definitions.DerivedObject.properties.anInt.type", equalTo("number")));
  }

  @Test
  public void whenSupportObjectReferencesDisabled_includeNestedClasses() {
    generator.setSupportObjectReferences(false);
    Object schema = generator.generate(ReferencingObject.class);

    assertThat(schema, hasJsonPath("$.properties.simple.type", equalTo("object")));
    assertThat(
        schema, hasJsonPath("$.properties.simple.properties.aBoolean.type", equalTo("boolean")));
    assertThat(
        schema, hasJsonPath("$.properties.simple.properties.aString.type", equalTo("string")));
    assertThat(schema, hasJsonPath("$.properties.derived.type", equalTo("object")));
    assertThat(
        schema, hasJsonPath("$.properties.derived.properties.aString.type", equalTo("string")));
    assertThat(
        schema, hasJsonPath("$.properties.derived.properties.anInt.type", equalTo("number")));
  }

  @Test
  public void generateDefinitionsForTransitiveReferences() {
    Object schema = generator.generate(TransitiveObject.class);

    assertThat(
        schema,
        hasJsonPath("$.definitions.SimpleObject.properties.aBoolean.type", equalTo("boolean")));
    assertThat(
        schema,
        hasJsonPath(
            "$.definitions.ReferencingObject.properties.simple.$ref",
            equalTo("#/definitions/SimpleObject")));
  }

  @Test
  public void treatContainerValuesProperties_asArrays() {
    Object schema = generator.generate(TransitiveObject.class);

    assertThat(schema, hasJsonPath("$.properties.simpleObjects.type", equalTo("array")));
    assertThat(
        schema,
        hasJsonPath(
            "$.properties.simpleObjects.items.$ref", equalTo("#/definitions/SimpleObject")));
  }

  @Test
  public void whenFieldIsDeprecated_skipIt() {
    Object schema = generator.generate(ReferencingObject.class);

    assertThat(schema, hasNoJsonPath("$.properties.deprecatedField"));
  }

  @Test
  public void whenFieldIsDeprecatedButIncludeDeprecatedSpecified_includeIt() {
    generator.setIncludeDeprecated(true);
    Object schema = generator.generate(ReferencingObject.class);

    assertThat(schema, hasJsonPath("$.properties.deprecatedField.type", equalTo("number")));
  }

  @Test
  public void whenObjectDefinedInExternalSchema_useFullReference() throws IOException {
    URL schemaUrl = getClass().getResource("k8smini.json");
    generator.addExternalSchema(schemaUrl, cacheUrl);
    Object schema = generator.generate(ExternalReferenceObject.class);

    assertThat(schema, hasJsonPath("$.properties.env.type", equalTo("array")));
    assertThat(
        schema,
        hasJsonPath(
            "$.properties.env.items.$ref",
            equalTo(schemaUrl + "#/definitions/io.k8s.api.core.v1.EnvVar")));
    assertThat(
        schema, hasJsonPath("$.properties.simple.$ref", equalTo("#/definitions/SimpleObject")));
  }

  @Test
  public void whenObjectDefinedInCachedKubernetesSchema_useFullReference() throws IOException {
    generator.addExternalSchema(schemaUrl, cacheUrl);
    Object schema = generator.generate(ExternalReferenceObject.class);

    assertThat(schema, hasJsonPath("$.properties.env.type", equalTo("array")));
    assertThat(
        schema,
        hasJsonPath(
            "$.properties.env.items.$ref",
            equalTo(schemaUrl + "#/definitions/io.k8s.api.core.v1.EnvVar")));
    assertThat(
        schema, hasJsonPath("$.properties.simple.$ref", equalTo("#/definitions/SimpleObject")));
  }

  @Test
  public void whenObjectDefinedInCachedKubernetesSchema_doNotAddToDefinitions() throws IOException {
    generator.addExternalSchema(schemaUrl, cacheUrl);
    Object schema = generator.generate(ExternalReferenceObject.class);

    assertThat(schema, hasNoJsonPath("$.definitions.V1EnvVar"));
  }

  // todo (future, maybe): generate $id nodes where they can simplify $ref urls
  // todo (future, maybe): support oneOf, allOf, anyOf, not ? - would need annotations.
  // todo access remote url if no cache found for kubernetes schema
}
