// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

public class YamlDocGenerator {
  private static final String CLASS_TABLE_HEADER =
      "| Name | Type | Description |\n" + "| --- | --- | --- |";
  private static final String DEFINITION_PREFIX = "#/definitions/";
  private static final List<String> NON_REFERENCE_TYPES = Arrays.asList("Map", "DateTime");
  private final Map<String, Object> schema;
  private final List<String> referencesNeeded = new ArrayList<>();
  private final Set<String> referencesGenerated = new HashSet<>();
  private KubernetesSchemaReference kubernetesReference;
  private YamlDocGenerator kubernetesGenerator;

  public YamlDocGenerator(Map<String, Object> schema) {
    this.schema = schema;
  }

  private static String toStructureName(String reference) {
    return StringUtils.capitalize(
        String.join(" ", StringUtils.splitByCharacterTypeCamelCase(getSimpleName(reference))));
  }

  private static String getSimpleName(String reference) {
    return reference.substring(reference.lastIndexOf(".") + 1);
  }

  private static String toLocalTypeName(String ref) {
    String typeName = ref.substring(DEFINITION_PREFIX.length());
    return typeName.substring(typeName.lastIndexOf(".") + 1);
  }

  /**
   * Generate YAML documentation.
   *
   * @param schemaName Schema name
   * @return documentation
   */
  public String generate(String schemaName) {
    return generate(schemaName, schema);
  }

  /**
   * Generate YAML documentation.
   *
   * @param reference Reference
   * @param schema Schema
   * @return documentation
   */
  public String generate(String reference, Map<String, Object> schema) {
    referencesNeeded.remove(reference);
    StringBuilder sb = new StringBuilder("### ");
    sb.append(toStructureName(reference)).append("\n\n");
    Optional.ofNullable(getDescription(schema)).ifPresent(s -> sb.append(s).append("\n\n"));
    sb.append(generateForClass(schema));
    while (!referencesNeeded.isEmpty()) {
      generateForDefinition(sb, referencesNeeded.get(0));
    }
    return sb.toString();
  }

  private String getDescription(Map<String, Object> schema) {
    return (String) schema.get("description");
  }

  private String getDescription(String fieldName, Map<String, Object> subSchema) {
    if (subSchema == null) {
      return "";
    }
    Map<String, Object> fieldMap = subMap(subSchema, fieldName);
    String rawDescription = (String) fieldMap.get("description");
    return rawDescription == null ? "" : rawDescription.replace("\n", "<br/>");
  }

  private void generateForDefinition(StringBuilder sb, String reference) {
    Map<String, Object> definitions = subMap(schema, "definitions");
    Map<String, Object> definition = subMap(definitions, reference);
    referencesGenerated.add(reference);
    sb.append("\n\n").append(generate(reference, definition));
  }

  String generateForClass(Map<String, Object> classSchema) {
    StringBuilder sb = new StringBuilder(CLASS_TABLE_HEADER);

    Map<String, Object> properties =
        Optional.ofNullable(subMap(classSchema, "properties")).orElse(new HashMap<>());
    for (String propertyName : getSortedKeys(properties)) {
      sb.append("\n").append(generateForProperty(propertyName, properties));
    }
    return sb.toString();
  }

  private List<String> getSortedKeys(Map<String, Object> properties) {
    List<String> keys = new ArrayList<>(properties.keySet());
    keys.sort(String.CASE_INSENSITIVE_ORDER);
    return keys;
  }

  String generateForProperty(String fieldName, Map<String, Object> subSchema) {
    return "| "
        + "`"
        + fieldName
        + "`"
        + " | "
        + emptyIfNull(getType(fieldName, subSchema))
        + " | "
        + emptyIfNull(getDescription(fieldName, subSchema))
        + " |";
  }

  private String getType(String fieldName, Map<String, Object> subSchema) {
    if (subSchema == null) {
      return "";
    }
    Map<String, Object> fieldMap = subMap(subSchema, fieldName);
    Type type = new Type(fieldMap);

    String val = type.getString();
    if ("boolean".equals(val)) {
      val = "Boolean";
    }
    return val;
  }

  /**
   * Assigns kubernetes version to use.
   *
   * @param k8sVersion Kubernetes version
   * @throws IOException IO exception
   */
  public void useKubernetesVersion(String k8sVersion) throws IOException {
    kubernetesReference = KubernetesSchemaReference.create(k8sVersion);
    URL cacheUrl = kubernetesReference.getKubernetesSchemaCacheUrl();
    if (cacheUrl != null) {
      kubernetesGenerator = new YamlDocGenerator(SchemaGenerator.loadCachedSchema(cacheUrl));
    }
  }

  /**
   * Returns the markdown generated (if any) for referenced Kubernetes objects.
   *
   * @return a string for a kubernetes schema markdown
   */
  public String getKubernetesSchemaMarkdown() {
    return kubernetesGenerator == null ? null : kubernetesGenerator.generate("Kubernetes Objects");
  }

  /**
   * Returns the name of the file to generate for the Kubernetes markdown.
   *
   * @return a string representing the local file name
   */
  public String getKubernetesSchemaMarkdownFile() {
    return kubernetesReference == null ? null : kubernetesReference.getK8sMarkdownLink();
  }

  private void addReferenceIfNeeded(String reference) {
    if (referencesNeeded.contains(reference) || referencesGenerated.contains(reference)) {
      return;
    }

    referencesNeeded.add(reference);
  }

  private Reference createReference(String referenceString) {
    if (referenceString == null) {
      return new NullReference();
    } else if (referenceString.startsWith(DEFINITION_PREFIX)) {
      return new LocalReference(referenceString);
    } else {
      return new ExternalReference(referenceString);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> subMap(Map<String, Object> schemaMap, String name) {
    return (Map<String, Object>) Optional.ofNullable(schemaMap.get(name)).orElse(new HashMap<>());
  }

  private String emptyIfNull(String someString) {
    return someString == null ? "" : someString;
  }

  private class Type {
    final Map<String, Object> fieldMap;
    final String specifiedType;

    Type(Map<String, Object> fieldMap) {
      this.fieldMap = fieldMap;
      this.specifiedType = emptyIfNull((String) fieldMap.get("type"));
    }

    String getString() {
      switch (specifiedType) {
        case "number":
        case "boolean":
        case "string":
          return specifiedType;
        case "array":
          Type subType = getItems();
          return "array of " + subType.getString();
        default:
          return Optional.ofNullable(getReference()).orElse(specifiedType);
      }
    }

    @SuppressWarnings("unchecked")
    private Type getItems() {
      return new Type((Map<String, Object>) fieldMap.get("items"));
    }

    private String getReference() {
      return createReference(getReferenceString()).getString();
    }

    private String getReferenceString() {
      return (String) fieldMap.get("$ref");
    }
  }

  private abstract class Reference {
    private final String typeName;

    private Reference(String typeName) {
      this.typeName = typeName;
    }

    String getString() {
      return String.format("[%s](%s)", getStructureName(), getLink());
    }

    String getTypeName() {
      return typeName;
    }

    String getLink() {
      return "#"
          + String.join("-", StringUtils.splitByCharacterTypeCamelCase(typeName)).toLowerCase();
    }

    private String getStructureName() {
      return toStructureName(typeName);
    }
  }

  private class NullReference extends Reference {
    NullReference() {
      super(null);
    }

    @Override
    String getString() {
      return null;
    }
  }

  private class LocalReference extends Reference {

    LocalReference(String ref) {
      super(toLocalTypeName(ref));
      if (isReferenceType()) {
        addReferenceIfNeeded(getTypeName());
      }
    }

    @Override
    String getString() {
      return isReferenceType() ? super.getString() : getTypeName();
    }

    private boolean isReferenceType() {
      return !NON_REFERENCE_TYPES.contains(getTypeName());
    }
  }

  private class ExternalReference extends Reference {
    private final String url;

    ExternalReference(String ref) {
      super(ref.substring(ref.lastIndexOf(".") + 1));
      url = ref.substring(0, ref.indexOf("#"));
      if (kubernetesGenerator != null) {
        kubernetesGenerator.addReferenceIfNeeded(toK8sName(ref));
      }
    }

    private String toK8sName(String ref) {
      return ref.substring(ref.lastIndexOf("/") + 1);
    }

    @Override
    String getLink() {
      return getKubernetesSchemaLink() + super.getLink();
    }

    private String getKubernetesSchemaLink() {
      return matchesKubernetesVersion() ? kubernetesReference.getK8sMarkdownLink() : "";
    }

    private boolean matchesKubernetesVersion() {
      return kubernetesReference != null && kubernetesReference.matchesUrl(url);
    }
  }
}
