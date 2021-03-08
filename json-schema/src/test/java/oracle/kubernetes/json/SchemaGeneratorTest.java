// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SchemaGeneratorTest {

  private static final String K8S_SCHEMA_URL =
      "https://github.com/garethr/kubernetes-json-schema/blob/master/v1.9.0/_definitions.json";
  private static final String K8S_CACHE_FILE = "caches/kubernetes-1.9.0.json";
  private final SchemaGenerator generator = new SchemaGenerator();

  private URL schemaUrl;
  private URL cacheUrl;
  @SuppressWarnings("unused")
  private boolean unAnnotatedBoolean;
  @SuppressWarnings("unused")
  private int unAnnotatedInteger;
  @SuppressWarnings("unused")
  private long unAnnotatedLong;
  @SuppressWarnings("unused")
  private String unAnnotatedString;
  @SuppressWarnings("unused")
  private int[] intArray;
  @SuppressWarnings("unused")
  private volatile boolean ignoreMe;
  @SuppressWarnings("unused")
  private TrafficLightColors colors;
  @SuppressWarnings("unused")
  @EnumClass(TrafficLightColors.class)
  private String colorString;
  @SuppressWarnings("unused")
  @EnumClass(value = TrafficLightColors.class, qualifier = "forSmallLight")
  private String twoColorString;
  @SuppressWarnings("unused")
  @Range(minimum = 7)
  private int valueWithMinimum;
  @SuppressWarnings("unused")
  @Range(maximum = 43)
  private int valueWithMaximum;
  @SuppressWarnings("unused")
  @Range(minimum = 12, maximum = 85)
  private int valueWithRange;
  @SuppressWarnings("unused")
  @Pattern("[A-Z][a-zA-Z_]*")
  private String codeName;
  @SuppressWarnings("unused")
  @Description("An annotated field")
  private Double annotatedDouble;

  @SuppressWarnings("unused")
  private SimpleObject simpleObject;
  
  @SuppressWarnings("unused")
  private Map<String,String> stringMap;

  @SuppressWarnings("unused")
  private Map<String,Object> objectMap;

  @SuppressWarnings("unused")
  @PreserveUnknown
  private Map<String,Object> arbitraryObjectMap;

  @SuppressWarnings({"unused", "rawtypes"})
  private Map genericMap;

  @SuppressWarnings("unused")
  private Map<String,SpecializedObject> specializedMap;

  @BeforeEach
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

  @Test
  public void generateSchemaForInteger() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("unAnnotatedInteger"));

    assertThat(schema, hasJsonPath("$.unAnnotatedInteger.type", equalTo("number")));
  }

  @Test
  public void generateSchemaForLong() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("unAnnotatedLong"));

    assertThat(schema, hasJsonPath("$.unAnnotatedLong.type", equalTo("number")));
  }

  @Test
  public void generateSchemaForString() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("unAnnotatedString"));

    assertThat(schema, hasJsonPath("$.unAnnotatedString.type", equalTo("string")));
  }

  @Test
  public void generateSchemaForIntArray() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("intArray"));

    assertThat(schema, hasJsonPath("$.intArray.type", equalTo("array")));
    assertThat(schema, hasJsonPath("$.intArray.items.type", equalTo("number")));
  }

  @Test
  public void doNotGenerateSchemaForVolatileFields() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("ignoreMe"));

    assertThat(schema, hasNoJsonPath("$.ignoreMe"));
  }

  @Test
  public void generateSchemaForEnum() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("colors"));

    assertThat(schema, hasJsonPath("$.colors.type", equalTo("string")));
    assertThat(
        schema, hasJsonPath("$.colors.enum", arrayContainingInAnyOrder("RED", "YELLOW", "GREEN")));
  }

  @Test
  public void generateSchemaForEnumAnnotatedString() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("colorString"));

    assertThat(schema, hasJsonPath("$.colorString.type", equalTo("string")));
    assertThat(
        schema,
        hasJsonPath("$.colorString.enum", arrayContainingInAnyOrder("RED", "YELLOW", "GREEN")));
  }

  @Test
  public void generateSchemaForEnumAnnotatedStringWithQualifier() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("twoColorString"));

    assertThat(schema, hasJsonPath("$.twoColorString.type", equalTo("string")));
    assertThat(
        schema, hasJsonPath("$.twoColorString.enum", arrayContainingInAnyOrder("RED", "GREEN")));
  }

  @Test
  public void whenIntegerAnnotatedWithMinimumOnly_addToSchema() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("valueWithMinimum"));

    assertThat(schema, hasJsonPath("$.valueWithMinimum.minimum", equalTo(7)));
    assertThat(schema, hasNoJsonPath("$.valueWithMinimum.maximum"));
  }

  @Test
  public void whenIntegerAnnotatedWithMaximumOnly_addToSchema() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("valueWithMaximum"));

    assertThat(schema, hasNoJsonPath("$.valueWithMaximum.minimum"));
    assertThat(schema, hasJsonPath("$.valueWithMaximum.maximum", equalTo(43)));
  }

  @Test
  public void whenIntegerAnnotatedWithRange_addToSchema() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("valueWithRange"));

    assertThat(schema, hasJsonPath("$.valueWithRange.minimum", equalTo(12)));
    assertThat(schema, hasJsonPath("$.valueWithRange.maximum", equalTo(85)));
  }

  @Test
  public void whenStringAnnotatedWithPatterne_addToSchema() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("codeName"));

    assertThat(schema, hasJsonPath("$.codeName.pattern", equalTo("[A-Z][a-zA-Z_]*")));
  }

  @Test
  public void generateSchemaForAnnotatedDouble() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("annotatedDouble"));

    assertThat(schema, hasJsonPath("$.annotatedDouble.type", equalTo("number")));
    assertThat(schema, hasJsonPath("$.annotatedDouble.description", equalTo("An annotated field")));
  }

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
    assertThat(schema, hasJsonPath("$.properties.aaBoolean.type", equalTo("boolean")));
    assertThat(schema, hasJsonPath("$.properties.aaString.type", equalTo("string")));
    assertThat(schema, hasJsonPath("$.properties.aaBoolean.description", equalTo("A flag")));
    assertThat(schema, hasJsonPath("$.properties.aaString.description", equalTo("A string")));
  }

  @Test
  public void generateReferenceForSimpleObjectField() throws NoSuchFieldException {
    Object schema = generateForField(getClass().getDeclaredField("simpleObject"));

    assertThat(schema, hasJsonPath("$.simpleObject.$ref", equalTo("#/definitions/SimpleObject")));
  }

  @Test
  public void whenAdditionalPropertiesDisabled_doNotGenerateTheProperty() {
    generator.setForbidAdditionalProperties(false);

    Object schema = generator.generate(SimpleObject.class);

    assertThat(schema, hasNoJsonPath("$.additionalProperties"));
  }

  @Test
  public void generateSchemaForDerivedObject() {
    Object schema = generator.generate(DerivedObject.class);

    assertThat(schema, hasJsonPath("$.description", equalTo("A simple object used for testing")));
    assertThat(schema, hasJsonPath("$.type", equalTo("object")));
    assertThat(schema, hasJsonPath("$.additionalProperties", equalTo("false")));
    assertThat(schema, hasJsonPath("$.properties.aaBoolean.type", equalTo("boolean")));
    assertThat(schema, hasJsonPath("$.properties.aaString.type", equalTo("string")));
    assertThat(schema, hasJsonPath("$.properties.anInt.type", equalTo("number")));
    assertThat(schema, hasJsonPath("$.properties.aaBoolean.description", equalTo("A flag")));
    assertThat(schema, hasJsonPath("$.properties.aaString.description", equalTo("A string")));
    assertThat(schema, hasJsonPath("$.properties.anInt.description", equalTo("An int\nvalue")));
    assertThat(schema, hasJsonPath("$.properties.depth.type", equalTo("number")));
    assertThat(
        schema,
        hasJsonPath("$.required", arrayContainingInAnyOrder("aaBoolean", "anInt", "depth")));
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
        hasJsonPath("$.definitions.SimpleObject.properties.aaBoolean.type", equalTo("boolean")));
    assertThat(
        schema,
        hasJsonPath("$.definitions.SimpleObject.properties.aaString.type", equalTo("string")));
    assertThat(
        schema,
        hasJsonPath("$.definitions.DerivedObject.properties.aaString.type", equalTo("string")));
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
        schema, hasJsonPath("$.properties.simple.properties.aaBoolean.type", equalTo("boolean")));
    assertThat(
        schema, hasJsonPath("$.properties.simple.properties.aaString.type", equalTo("string")));
    assertThat(schema, hasJsonPath("$.properties.derived.type", equalTo("object")));
    assertThat(
        schema, hasJsonPath("$.properties.derived.properties.aaString.type", equalTo("string")));
    assertThat(
        schema, hasJsonPath("$.properties.derived.properties.anInt.type", equalTo("number")));
  }

  @Test
  void whenFieldIsMapAndNoObjectReferences_additionalPropertiesTypeDefaultsToString() throws NoSuchFieldException {
    generator.setSupportObjectReferences(false);
    Object schema = generateForField(getClass().getDeclaredField("genericMap"));

    assertThat(schema, hasJsonPath("$.genericMap.additionalProperties.type", equalTo("string")));
  }

  @Test
  void whenFieldIsStringMapAndNoObjectReferences_additionalPropertiesTypeMatchesField() throws NoSuchFieldException {
    generator.setSupportObjectReferences(false);
    Object schema = generateForField(getClass().getDeclaredField("stringMap"));

    assertThat(schema, hasJsonPath("$.stringMap.additionalProperties.type", equalTo("string")));
  }

  @Test
  void whenFieldIsObjectMapAndNoObjectReferences_additionalPropertiesIsFalse() throws NoSuchFieldException {
    generator.setSupportObjectReferences(false);
    Object schema = generateForField(getClass().getDeclaredField("objectMap"));

    assertThat(schema, hasJsonPath("$.objectMap.type", equalTo("object")));
    assertThat(schema, hasJsonPath("$.objectMap.additionalProperties", equalTo("false")));
  }

  @Test
  void whenFieldIsObjectMapAnnotatedWithPreserveFields_addK8sPreserveElement() throws NoSuchFieldException {
    generator.setSupportObjectReferences(false);
    Object schema = generateForField(getClass().getDeclaredField("arbitraryObjectMap"));

    assertThat(schema, hasJsonPath("$.arbitraryObjectMap.x-kubernetes-preserve-unknown-fields", equalTo("true")));
    assertThat(schema, hasJsonPath("$.arbitraryObjectMap.type", equalTo("object")));
    assertThat(schema, hasJsonPath("$.arbitraryObjectMap.additionalProperties", equalTo("false")));
  }

  @Test
  public void generateDefinitionsForTransitiveReferences() {
    Object schema = generator.generate(TransitiveObject.class);

    assertThat(
        schema,
        hasJsonPath("$.definitions.SimpleObject.properties.aaBoolean.type", equalTo("boolean")));
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
  public void whenFieldIsDeprecated_includeIt() {
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

  @Test
  public void whenK8sVersionSpecified_useFullReferenceForK8sObject() throws IOException {
    generator.useKubernetesVersion("1.9.0");
    Object schema = generator.generate(ExternalReferenceObject.class);

    assertThat(
        schema,
        hasJsonPath(
            "$.properties.env.items.$ref",
            equalTo(schemaUrl + "#/definitions/io.k8s.api.core.v1.EnvVar")));
  }

  @Test
  public void whenNonCachedK8sVersionSpecified_throwException() {
    assertThrows(IOException.class, () -> generator.useKubernetesVersion("1.12.0"));
  }

  @Test
  void useExternalSchemaItem() throws NoSuchFieldException {
    generator.setSupportObjectReferences(false);
    generator.defineAdditionalProperties(SpecializedObject.class, "string");
    Object schema = generateForField(getClass().getDeclaredField("specializedMap"));

    assertThat(schema, hasJsonPath("$.specializedMap.additionalProperties.type", equalTo("string")));
  }

  @SuppressWarnings("unused")
  private enum TrafficLightColors {
    RED,
    YELLOW {
      @Override
      boolean forSmallLight() {
        return false;
      }
    },
    GREEN;

    boolean forSmallLight() {
      return true;
    }
  }

  // todo (future, maybe): generate $id nodes where they can simplify $ref urls
  // todo (future, maybe): support oneOf, allOf, anyOf, not ? - would need annotations.
  // todo access remote url if no cache found for kubernetes schema
}
