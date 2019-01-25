// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;
import org.junit.Test;

public class YamlDocGeneratorTest {
  private SchemaGenerator schemaGenerator = new SchemaGenerator();

  @Test
  public void generateMarkdownForProperty() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("annotatedDouble"));
    assertThat(markdown, containsString(tableEntry("annotatedDouble", "An annotated field.")));
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

  @SuppressWarnings("unused")
  @Description("An annotated field")
  private Double annotatedDouble;

  @Test
  public void whenPropertyTypeIsDateTime_doNotGenerateReference() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("dateTime"));
    assertThat(markdown, containsString(tableEntry("dateTime", "")));
  }

  @SuppressWarnings("unused")
  private DateTime dateTime;

  @Test
  public void whenPropertyTypeIsMap_doNotGenerateReference() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("notes"));
    assertThat(markdown, containsString(tableEntry("notes", "")));
  }

  @SuppressWarnings("unused")
  private Map<String, String> notes;

  @Test
  public void whenPropertyTypeIsReferenceWithDescription_includeBoth() throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("simpleUsage"));
    assertThat(
        markdown,
        containsString(tableEntry("simpleUsage", "An example. See section 'Simple Object'")));
  }

  @SuppressWarnings("unused")
  @Description("An example")
  private SimpleObject simpleUsage;

  @Test
  public void whenPropertyTypeIsReferenceArrayWithDescription_includeBoth()
      throws NoSuchFieldException {
    String markdown = generateForProperty(getClass().getDeclaredField("simpleArray"));
    assertThat(
        markdown,
        containsString(tableEntry("simpleArray", "An array. See section 'Simple Object'")));
  }

  @SuppressWarnings("unused")
  @Description("An array")
  private SimpleObject[] simpleArray;

  @Test
  public void generateMarkdownForSimpleObject() {
    YamlDocGenerator generator = new YamlDocGenerator(new HashMap<>());
    String markdown = generator.generateForClass(generateSchema(SimpleObject.class));
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                tableHeader("Name", "Description"),
                tableEntry("aBoolean", "A flag."),
                tableEntry("aString", "A string."),
                tableEntry("depth", ""))));
  }

  private Map<String, Object> generateSchema(Class<?> aClass) {
    return schemaGenerator.generate(aClass);
  }

  @Test
  public void generateMarkdownForSimpleObjectWithHeader() {
    Map<String, Object> schema = generateSchema(SimpleObject.class);
    YamlDocGenerator generator = new YamlDocGenerator(schema);
    String markdown = generator.generate("simpleObject", schema);
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                "### Simple Object",
                "",
                tableHeader("Name", "Description"),
                tableEntry("aBoolean", "A flag."),
                tableEntry("aString", "A string."),
                tableEntry("depth", ""))));
  }

  @Test
  public void generateMarkdownForObjectWithReferences() {
    Map<String, Object> schema = generateSchema(ReferencingObject.class);
    YamlDocGenerator generator = new YamlDocGenerator(schema);
    String markdown = generator.generate("start", schema);
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                "### Start",
                "",
                tableHeader("Name", "Description"),
                tableEntry("derived", "See section 'Derived Object'"),
                tableEntry("simple", "See section 'Simple Object'"))));
  }

  @Test
  public void generateMarkdownWithReferencedSections() {
    Map<String, Object> schema = generateSchema(ReferencingObject.class);
    YamlDocGenerator generator = new YamlDocGenerator(schema);
    String markdown = generator.generate("start", schema);
    assertThat(
        markdown,
        containsString(
            String.join(
                "\n",
                "### Start",
                "",
                tableHeader("Name", "Description"),
                tableEntry("derived", "See section 'Derived Object'"),
                tableEntry("simple", "See section 'Simple Object'"),
                "",
                "### Derived Object",
                "",
                tableHeader("Name", "Description"),
                tableEntry("aBoolean", "A flag."),
                tableEntry("anInt", "An int."))));
  }

  private String tableHeader(String... headers) {
    return tableEntry(headers) + "\n" + tableDivider(headers.length);
  }

  private String tableDivider(int numColumns) {
    StringBuilder sb = new StringBuilder("|");
    for (int i = 0; i < numColumns; i++) sb.append(" --- |");
    return sb.toString();
  }
}
