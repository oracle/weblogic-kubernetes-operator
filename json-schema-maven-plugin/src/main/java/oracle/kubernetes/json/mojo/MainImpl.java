// Copyright 2018,2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import oracle.kubernetes.json.SchemaGenerator;
import oracle.kubernetes.json.YamlDocGenerator;
import org.apache.maven.plugin.MojoExecutionException;

public class MainImpl implements Main {
  private SchemaGenerator generator = new SchemaGenerator();
  private ClassLoader classLoader;
  private String kubernetesVersion;

  @Override
  public void setIncludeDeprecated(boolean includeDeprecated) {
    generator.setIncludeDeprecated(includeDeprecated);
  }

  @Override
  public void setIncludeAdditionalProperties(boolean includeAdditionalProperties) {
    generator.setIncludeAdditionalProperties(includeAdditionalProperties);
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
  public void setKubernetesVersion(String kubernetesVersion) throws IOException {
    this.kubernetesVersion = kubernetesVersion;
    generator.useKubernetesVersion(kubernetesVersion);
  }

  @Override
  public void defineSchemaUrlAndContents(URL schemaURL, URL cacheUrl) throws IOException {
    generator.addExternalSchema(schemaURL, cacheUrl);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  public Map<String, Object> generateSchema(String className, File outputFile)
      throws MojoExecutionException {
    outputFile.getParentFile().mkdirs();
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

    YamlDocGenerator generator = new YamlDocGenerator(schema);
    try (FileWriter writer = new FileWriter(outputFile)) {
      if (kubernetesVersion != null) generator.useKubernetesVersion(kubernetesVersion);
      writer.write(generator.generate(rootName));
    } catch (IOException e) {
      throw new MojoExecutionException("Error generating markdown", e);
    }

    String kubernetesSchemaMarkdownFile = generator.getKubernetesSchemaMarkdownFile();
    if (kubernetesSchemaMarkdownFile == null) return;

    File kubernetesFile = new File(outputFile.getParent(), kubernetesSchemaMarkdownFile);
    try (FileWriter writer = new FileWriter(kubernetesFile)) {
      writer.write(generator.getKubernetesSchemaMarkdown());
    } catch (IOException e) {
      throw new MojoExecutionException("Error generating markdown", e);
    }
  }
}
