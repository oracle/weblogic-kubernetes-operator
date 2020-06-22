// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.junit.Test;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class YamlDocGeneratorTest {
  private static final String K8S_VERSION = "1.13.5";
  private SchemaGenerator schemaGenerator = new SchemaGenerator();
  @SuppressWarnings("unused")
  @Description("An annotated field")
  private Double annotatedDouble;
  @SuppressWarnings("unused")
  private DateTime dateTime;
  @SuppressWarnings("unused")
  private Map<String, String> notes;
  @SuppressWarnings("unused")
  private List<String> myList;
  @SuppressWarnings("unused")
  @Description("An example")
  private SimpleObject simpleUsage;
  @SuppressWarnings("unused")
  @Description("An array")
  private SimpleObject[] simpleArray;

  @Test
  public void generateMarkdownForProperty() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("annotatedDouble"));
    assertThat(
        markdown, containsString(tableEntry("`annotatedDouble`", "number", "An annotated field")));
  }

  private String tableEntry(String... columns) {
    return "| " + String.join(" | ", columns) + " |";
  }

  private String generateForProperty(Field field) {
    YamlDocGenerator generator = new YamlDocGenerator(new HashMap<>());

    return generator.generateForProperty(field.getName(), generateSchemaForField(field));
  }

  private Map<String, Object> generateSchemaForField(Field field) {
    Map<String, Object> result = new HashMap<>();
    schemaGenerator.generateFieldIn(result, field);
    return result;
  }

  @Test
  public void whenSchemaHasUknownTypeAndNoReference_useAsSpecified() throws NoSuchFieldException {
    Map<String, Object> schema = ImmutableMap.of("anInt", of("type", "integer"));

    String markdown = new YamlDocGenerator(schema).generateForProperty("anInt", schema);

    assertThat(markdown, containsString(tableEntry("`anInt`", "integer", "")));
  }

  @Test
  public void whenPropertyTypeIsDateTime_doNotGenerateReference() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("dateTime"));
    assertThat(markdown, containsString(tableEntry("`dateTime`", "DateTime", "")));
  }

  @Test
  public void whenPropertyTypeIsMap_doNotGenerateReference() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("notes"));
    assertThat(markdown, containsString(tableEntry("`notes`", "Map", "")));
  }

  @Test
  public void whenPropertyTypeIsArrayOfStrings_generateType() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("myList"));
    assertThat(markdown, containsString(tableEntry("`myList`", "array of string", "")));
  }

  @Test
  public void whenPropertyTypeIsReferenceWithDescription_includeBoth() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("simpleUsage"));
    assertThat(
        markdown,
        containsString(
            tableEntry("`simpleUsage`", linkTo("Simple Object", "#simple-object"), "An example")));
  }

  private String linkTo(String section, String anchor) {
    return "[" + section + "](" + anchor + ")";
  }

  @Test
  public void whenPropertyTypeIsReferenceArrayWithDescription_includeBoth()
      throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("simpleArray"));
    assertThat(
        markdown,
        containsString(
            tableEntry(
                "`simpleArray`",
                "array of " + linkTo("Simple Object", "#simple-object"),
                "An array")));
  }

  @Test
  public void generateMarkdownForSimpleObject() {
    YamlDocGenerator generator = new YamlDocGenerator(new HashMap<>());
    String markdown = generator.generateForClass(generateSchema(SimpleObject.class));
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                tableHeader(),
                tableEntry("`aaBoolean`", "Boolean", "A flag"),
                tableEntry("`aaString`", "string", "A string"),
                tableEntry("`depth`", "number", ""))));
  }

  private Map<String, Object> generateSchema(Class<?> aaClass) {
    return schemaGenerator.generate(aaClass);
  }

  @Test
  public void generateMarkdownForSimpleObjectWithHeader() {
    Map<String, Object> schema = generateSchema(SimpleObject.class);
    YamlDocGenerator generator = new YamlDocGenerator(schema);
    String markdown = generator.generate("simpleObject");
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                "### Simple Object",
                "",
                tableHeader(),
                tableEntry("`aaBoolean`", "Boolean", "A flag"),
                tableEntry("`aaString`", "string", "A string"),
                tableEntry("`depth`", "number", ""))));
  }

  @Test
  public void generateMarkdownForObjectWithReferences() {
    Map<String, Object> schema = generateSchema(ReferencingObject.class);
    YamlDocGenerator generator = new YamlDocGenerator(schema);
    String markdown = generator.generate("start");
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                "### Start",
                "",
                tableHeader(),
                tableEntry("`deprecatedField`", "number", ""),
                tableEntry("`derived`", linkTo("Derived Object", "#derived-object"), ""),
                tableEntry("`simple`", linkTo("Simple Object", "#simple-object"), ""))));
  }

  @Test
  public void whenKubernetesSchemaNotUsed_kubernetesMarkdownIsNull() {
    Map<String, Object> schema = generateSchema(ReferencingObject.class);
    YamlDocGenerator generator = new YamlDocGenerator(schema);
    generator.generate("start");
    assertThat(generator.getKubernetesSchemaMarkdown(), nullValue());
  }

  @Test
  public void generateMarkdownWithReferencedSections() {
    Map<String, Object> schema = generateSchema(ReferencingObject.class);
    YamlDocGenerator generator = new YamlDocGenerator(schema);
    String markdown = generator.generate("start");
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                "### Start",
                "",
                tableHeader(),
                tableEntry("`deprecatedField`", "number", ""),
                tableEntry("`derived`", linkTo("Derived Object", "#derived-object"), ""),
                tableEntry("`simple`", linkTo("Simple Object", "#simple-object"), ""),
                "",
                "### Derived Object",
                "",
                "A simple object used for testing",
                "",
                tableHeader(),
                tableEntry("`aaBoolean`", "Boolean", "A flag"),
                tableEntry("`aaString`", "string", "A string"))));
  }

  private String tableHeader() {
    return tableHeader("Name", "Type", "Description");
  }

  private String tableHeader(String... headers) {
    return tableEntry(headers) + "\n" + tableDivider(headers.length);
  }

  private String tableDivider(int numColumns) {
    StringBuilder sb = new StringBuilder("|");
    for (int i = 0; i < numColumns; i++) {
      sb.append(" --- |");
    }
    return sb.toString();
  }

  @Test
  public void whenExternalSchemaSpecified_returnFileName() throws IOException {
    schemaGenerator.useKubernetesVersion(K8S_VERSION);
    Map<String, Object> schema = schemaGenerator.generate(KubernetesReferenceObject.class);

    YamlDocGenerator generator = new YamlDocGenerator(schema);
    generator.useKubernetesVersion(K8S_VERSION);
    generator.generate("start");
    KubernetesSchemaReference reference = KubernetesSchemaReference.create(K8S_VERSION);
    assertThat(
        generator.getKubernetesSchemaMarkdownFile(), equalTo(reference.getK8sMarkdownLink()));
  }

  @Test
  public void whenExternalSchemaSpecified_generateWithReferencedSections() throws IOException {
    schemaGenerator.useKubernetesVersion(K8S_VERSION);
    Map<String, Object> schema = schemaGenerator.generate(KubernetesReferenceObject.class);

    YamlDocGenerator generator = new YamlDocGenerator(schema);
    KubernetesSchemaReference reference = KubernetesSchemaReference.create(K8S_VERSION);
    generator.useKubernetesVersion(K8S_VERSION);
    String markdown = generator.generate("start");
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                "### Start",
                "",
                tableHeader(),
                tableEntry(
                    "`env`", linkTo("Env Var", reference.getK8sMarkdownLink() + "#env-var"), ""))));
  }

  @Test
  public void whenExternalSchemaSpecified_generateMarkdownForIt() throws IOException {
    schemaGenerator.useKubernetesVersion(K8S_VERSION);
    Map<String, Object> schema = schemaGenerator.generate(KubernetesReferenceObject.class);

    YamlDocGenerator generator = new YamlDocGenerator(schema);
    generator.useKubernetesVersion(K8S_VERSION);
    generator.generate("start");
    assertThat(
        generator.getKubernetesSchemaMarkdown(),
        containsString(
            String.join(
                "\n",
                "### Env Var",
                "",
                "EnvVar represents an environment variable present in a Container.",
                "",
                tableHeader(),
                tableEntry(
                    "`name`",
                    "string",
                    "Name of the environment variable. Must be a C_IDENTIFIER."))));
  }

  @Test
  public void whenExternalSchemaSpecified_generateMarkdownForItsLinks() throws IOException {
    schemaGenerator.useKubernetesVersion(K8S_VERSION);
    Map<String, Object> schema = schemaGenerator.generate(KubernetesReferenceObject.class);

    YamlDocGenerator generator = new YamlDocGenerator(schema);
    generator.useKubernetesVersion(K8S_VERSION);
    generator.generate("start");
    assertThat(
        generator.getKubernetesSchemaMarkdown(),
        containsString("| `valueFrom` | " + linkTo("Env Var Source", "#env-var-source")));
  }

  @Test
  public void whenExternalSchemaSpecified_generateMarkdownForItsDependencies() throws IOException {
    schemaGenerator.useKubernetesVersion(K8S_VERSION);
    Map<String, Object> schema = schemaGenerator.generate(KubernetesReferenceObject.class);

    YamlDocGenerator generator = new YamlDocGenerator(schema);
    generator.useKubernetesVersion(K8S_VERSION);
    generator.generate("start");
    assertThat(
        generator.getKubernetesSchemaMarkdown(),
        containsString(String.join("\n", "### Env Var Source")));
  }
}
