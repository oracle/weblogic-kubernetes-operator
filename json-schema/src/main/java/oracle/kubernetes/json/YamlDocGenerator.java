// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class YamlDocGenerator {
  private static final String CLASS_TABLE_HEADER = "| Name | Description |\n" + "| --- | --- |";
  private static final String DEFINITION_PREFIX = "#/definitions/";
  private Map<String, Object> schema;
  private List<String> referencesNeeded = new ArrayList<>();
  private Set<String> referencesGenerated = new HashSet<>();

  public YamlDocGenerator(Map<String, Object> schema) {
    this.schema = schema;
  }

  public String generate(String reference, Map<String, Object> schema) {
    referencesNeeded.remove(reference);
    StringBuilder sb = new StringBuilder("### ");
    sb.append(toStructureName(reference)).append("\n\n").append(generateForClass(schema));
    while (!referencesNeeded.isEmpty()) {
      generateForDefinition(sb, referencesNeeded.get(0));
    }
    return sb.toString();
  }

  private void generateForDefinition(StringBuilder sb, String reference) {
    Map<String, Object> definitions = subMap(schema, "definitions");
    Map<String, Object> definition = subMap(definitions, reference);
    referencesGenerated.add(reference);
    sb.append("\n\n").append(generate(reference, definition));
  }

  private String toStructureName(String reference) {
    return StringUtils.capitalize(
        String.join(" ", StringUtils.splitByCharacterTypeCamelCase(reference)));
  }

  String generateForClass(Map<String, Object> classSchema) {
    StringBuilder sb = new StringBuilder(CLASS_TABLE_HEADER);

    Map<String, Object> properties =
        Optional.ofNullable(subMap(classSchema, "properties")).orElse(new HashMap<>());
    for (String propertyName : getSortedKeys(properties))
      sb.append("\n").append(generateForProperty(propertyName, properties));
    return sb.toString();
  }

  private List<String> getSortedKeys(Map<String, Object> properties) {
    List<String> keys = new ArrayList<>(properties.keySet());
    keys.sort(String.CASE_INSENSITIVE_ORDER);
    return keys;
  }

  String generateForProperty(String fieldName, Map<String, Object> subSchema) {
    return "| " + fieldName + " | " + emptyIfNull(getDescription(fieldName, subSchema)) + " |";
  }

  private String getDescription(String fieldName, Map<String, Object> subSchema) {
    if (subSchema == null) return "";
    Description description = new Description(subMap(subSchema, fieldName));

    return description.getString();
  }

  private class Description {
    Map<String, Object> fieldMap;

    Description(Map<String, Object> fieldMap) {
      this.fieldMap = fieldMap;
    }

    private String getString() {
      String ref = getReference();
      if (ref != null && ref.startsWith(DEFINITION_PREFIX)) {
        String reference = ref.substring(DEFINITION_PREFIX.length());
        if (isGeneratingReferenceFor(reference)) {
          addReferenceIfNeeded(reference);
          StringBuilder sb = new StringBuilder(getSpecifiedDescription());
          if (sb.length() > 0) sb.append(' ');
          sb.append("See section '").append(toStructureName(reference)).append("'");
          return sb.toString();
        }
      }
      return getSpecifiedDescription();
    }

    private String getReference() {
      String reference = (String) fieldMap.get("$ref");
      if (reference != null) return reference;
      else if (fieldMap.get("items") == null) return null;
      else return (String) subMap(fieldMap, "items").get("$ref");
    }

    private String getSpecifiedDescription() {
      String rawDescription = (String) fieldMap.get("description");
      if (rawDescription == null) return "";
      else if (rawDescription.trim().endsWith(".")) return rawDescription.trim();
      else return rawDescription.trim() + ".";
    }

    private boolean isGeneratingReferenceFor(String reference) {
      return !(reference.equals("DateTime")) && !(reference.equals("Map"));
    }

    private void addReferenceIfNeeded(String reference) {
      if (referencesNeeded.contains(reference) || referencesGenerated.contains(reference)) return;

      referencesNeeded.add(reference);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> subMap(Map<String, Object> schemaMap, String name) {
    return (Map<String, Object>) Optional.ofNullable(schemaMap.get(name)).orElse(new HashMap<>());
  }

  private String emptyIfNull(String aString) {
    return aString == null ? "" : aString;
  }
}
