// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import oracle.kubernetes.json.SchemaGenerator;
import oracle.kubernetes.json.YamlDocGenerator;
import org.apache.maven.plugin.MojoExecutionException;

public class MainImpl implements Main {
  private final SchemaGenerator generator = new SchemaGenerator();
  private ClassLoader classLoader;
  private String kubernetesVersion;

  @Override
  public void setIncludeAdditionalProperties(boolean includeAdditionalProperties) {
    generator.setForbidAdditionalProperties(includeAdditionalProperties);
  }

  @Override
  public void setSupportObjectReferences(boolean supportObjectReferences) {
    generator.setSupportObjectReferences(supportObjectReferences);
  }

  @Override
  public void defineClasspath(URL... classpathElements) {
    classLoader = new URLClassLoader(classpathElements, getClass().getClassLoader());
  }

  @Override
  public URL getResource(String name) {
    return classLoader.getResource(name);
  }

  @Override
  public void setKubernetesVersion(String kubernetesVersion) throws IOException, URISyntaxException {
    this.kubernetesVersion = kubernetesVersion;
    generator.useKubernetesVersion(kubernetesVersion);
  }

  @Override
  public void defineSchemaUrlAndContents(URL schemaUrl, URL cacheUrl) throws IOException {
    generator.addExternalSchema(schemaUrl, cacheUrl);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  public Map<String, Object> generateSchema(String className, File outputFile)
      throws MojoExecutionException {
    outputFile.getParentFile().mkdirs();
    generator.initializeDefinedObjects();
    try (FileWriter writer = new FileWriter(outputFile)) {
      Class<?> theClass = classLoader.loadClass(className);
      Map<String, Object> schema = generator.generate(theClass);
      writer.write(SchemaGenerator.prettyPrint(schema));
      return schema;
    } catch (IOException e) {
      throw new MojoExecutionException("Error generating schema", e);
    } catch (ClassNotFoundException e) {
      throw new MojoExecutionException("Class " + className + " not found");
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  public void generateMarkdown(String rootName, File outputFile, Map<String, Object> schema)
      throws MojoExecutionException {
    outputFile.getParentFile().mkdirs();

    YamlDocGenerator yamlDocGenerator = new YamlDocGenerator(schema);
    try (FileWriter writer = new FileWriter(outputFile)) {
      if (kubernetesVersion != null) {
        yamlDocGenerator.useKubernetesVersion(kubernetesVersion);
      }
      writer.write(yamlDocGenerator.generate(rootName));
    } catch (IOException e) {
      throw new MojoExecutionException("Error generating markdown", e);
    }

    String kubernetesSchemaMarkdownFile = yamlDocGenerator.getKubernetesSchemaMarkdownFile();
    if (kubernetesSchemaMarkdownFile == null) {
      return;
    }

    File kubernetesFile = new File(outputFile.getParent(), kubernetesSchemaMarkdownFile);
    try (FileWriter writer = new FileWriter(kubernetesFile)) {
      writer.write(yamlDocGenerator.getKubernetesSchemaMarkdown());
    } catch (IOException e) {
      throw new MojoExecutionException("Error generating markdown", e);
    }
  }
}
