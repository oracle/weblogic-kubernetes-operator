// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
  private Map<String, Object> schema;
  private List<String> referencesNeeded = new ArrayList<>();
  private Set<String> referencesGenerated = new HashSet<>();
  private KubernetesSchemaReference kubernetesReference;
  private YamlDocGenerator kubernetesGenerator;

  public YamlDocGenerator(Map<String, Object> schema) {
    this.schema = schema;
  }

  public String generate(String schemaName) {
    return generate(schemaName, schema);
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

  private static String toStructureName(String reference) {
    return StringUtils.capitalize(
        String.join(" ", StringUtils.splitByCharacterTypeCamelCase(getSimpleName(reference))));
  }

  private static String getSimpleName(String reference) {
    return reference.substring(reference.lastIndexOf(".") + 1);
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
    return "| "
        + fieldName
        + " | "
        + emptyIfNull(getType(fieldName, subSchema))
        + " | "
        + emptyIfNull(getDescription(fieldName, subSchema))
        + " |";
  }

  private String getType(String fieldName, Map<String, Object> subSchema) {
    if (subSchema == null) return "";
    Map<String, Object> fieldMap = subMap(subSchema, fieldName);
    Type type = new Type(fieldMap);
    return type.getString();
  }

  private String getDescription(String fieldName, Map<String, Object> subSchema) {
    if (subSchema == null) return "";
    Map<String, Object> fieldMap = subMap(subSchema, fieldName);
    String rawDescription = (String) fieldMap.get("description");
    return rawDescription == null ? "" : rawDescription.replace("\n", "<br/>");
  }

  public void useKubernetesVersion(String k8sVersion) throws IOException {
    kubernetesReference = KubernetesSchemaReference.create(k8sVersion);
    URL cacheUrl = kubernetesReference.getKubernetesSchemaCacheUrl();
    if (cacheUrl != null)
      kubernetesGenerator = new YamlDocGenerator(SchemaGenerator.loadCachedSchema(cacheUrl));
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

  private class Type {
    Map<String, Object> fieldMap;
    String specifiedType;

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
          return "array of " + getReference();
        default:
          return getReference();
      }
    }

    private String getReference() {
      Reference reference = createReference(getReferenceString());
      if (!reference.shouldGenerateReference()) return "";
      return String.format("[%s](%s)", reference.getStructureName(), reference.getLink());
    }

    private String getReferenceString() {
      String reference = (String) fieldMap.get("$ref");
      if (reference != null) return reference;
      else if (fieldMap.get("items") == null) return null;
      else return (String) subMap(fieldMap, "items").get("$ref");
    }
  }

  private void addReferenceIfNeeded(String reference) {
    if (referencesNeeded.contains(reference) || referencesGenerated.contains(reference)) return;

    referencesNeeded.add(reference);
  }

  private Reference createReference(String referenceString) {
    if (referenceString == null) return new NullReference();
    else if (referenceString.startsWith(DEFINITION_PREFIX))
      return new LocalReference(referenceString);
    else return new ExternalReference(referenceString);
  }

  private abstract class Reference {
    private String typeName;

    private Reference(String typeName) {
      this.typeName = typeName;
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

    abstract boolean shouldGenerateReference();

    abstract boolean isLocal();
  }

  private class NullReference extends Reference {
    NullReference() {
      super(null);
    }

    @Override
    boolean isLocal() {
      return false;
    }

    @Override
    boolean shouldGenerateReference() {
      return false;
    }
  }

  private static String toLocalTypeName(String ref) {
    String typeName = ref.substring(DEFINITION_PREFIX.length());
    return typeName.substring(typeName.lastIndexOf(".") + 1);
  }

  private class LocalReference extends Reference {

    LocalReference(String ref) {
      super(toLocalTypeName(ref));
      addReferenceIfNeeded(getTypeName());
    }

    @Override
    boolean isLocal() {
      return true;
    }

    @Override
    boolean shouldGenerateReference() {
      return !(getTypeName().equals("DateTime")) && !(getTypeName().equals("Map"));
    }
  }

  private class ExternalReference extends Reference {
    private final String url;

    ExternalReference(String ref) {
      super(ref.substring(ref.lastIndexOf(".") + 1));
      url = ref.substring(0, ref.indexOf("#"));
      if (kubernetesGenerator != null) kubernetesGenerator.addReferenceIfNeeded(toK8sName(ref));
    }

    private String toK8sName(String ref) {
      return ref.substring(ref.lastIndexOf("/") + 1);
    }

    @Override
    boolean isLocal() {
      return false;
    }

    @Override
    boolean shouldGenerateReference() {
      return true;
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> subMap(Map<String, Object> schemaMap, String name) {
    return (Map<String, Object>) Optional.ofNullable(schemaMap.get(name)).orElse(new HashMap<>());
  }

  private String emptyIfNull(String aString) {
    return aString == null ? "" : aString;
  }
}
