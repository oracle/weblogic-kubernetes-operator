// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class TestMain implements Main {
  private URL[] classpath;
  private URL classpathResource;
  private String className;
  private File schemaFile;
  private String resourceName;
  private Map<URL, URL> schemas = new HashMap<>();
  private String kubernetesVersion;
  private boolean includeAdditionalProperties;
  private boolean supportObjectReferences;
  private File markdownFile;
  private Map<String, Object> schema;
  private Map<String, Object> markdownSchema;

  TestMain() throws MalformedURLException {
    classpathResource = new URL("file:abc");
  }

  URL[] getClasspath() {
    return classpath;
  }

  String getResourceName() {
    return resourceName;
  }

  void setClasspathResource(URL classpathResource) {
    this.classpathResource = classpathResource;
  }

  String getClassName() {
    return className;
  }

  File getSchemaFile() {
    return schemaFile;
  }

  File getMarkdownFile() {
    return markdownFile;
  }

  public Map<String, Object> getMarkdownSchema() {
    return markdownSchema;
  }

  void setGeneratedSchema(Map<String, Object> schema) {
    this.schema = schema;
  }

  URL getCacheFor(URL schemaUrl) {
    return schemas.get(schemaUrl);
  }

  String getKubernetesVersion() {
    return kubernetesVersion;
  }

  @Override
  public void setKubernetesVersion(String kubernetesVersion) {
    this.kubernetesVersion = kubernetesVersion;
  }

  boolean isIncludeAdditionalProperties() {
    return includeAdditionalProperties;
  }

  @Override
  public void setIncludeAdditionalProperties(boolean includeAdditionalProperties) {
    this.includeAdditionalProperties = includeAdditionalProperties;
  }

  boolean isSupportObjectReferences() {
    return supportObjectReferences;
  }

  @Override
  public void setSupportObjectReferences(boolean supportObjectReferences) {
    this.supportObjectReferences = supportObjectReferences;
  }

  @Override
  public void defineSchemaUrlAndContents(URL schemaUrl, URL cacheUrl) {
    schemas.put(schemaUrl, cacheUrl);
  }

  @Override
  public void defineClasspath(URL... classpath) {
    this.classpath = classpath;
  }

  @Override
  public URL getResource(String name) {
    resourceName = name;
    return classpathResource;
  }

  @Override
  public Map<String, Object> generateSchema(String className, File outputFile) {
    this.className = className;
    this.schemaFile = outputFile;
    return schema;
  }

  @Override
  public void generateMarkdown(String rootName, File markdownFile, Map<String, Object> schema) {
    this.markdownFile = markdownFile;
    this.markdownSchema = schema;
  }
}
