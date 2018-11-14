// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import oracle.kubernetes.json.SchemaGenerator;
import org.apache.maven.plugin.MojoExecutionException;

public class MainImpl implements Main {
  private SchemaGenerator generator = new SchemaGenerator();
  private ClassLoader classLoader;

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
  public void defineSchemaUrlAndContents(URL schemaURL, URL cacheUrl) throws IOException {
    generator.addExternalSchema(schemaURL, cacheUrl);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  public void generateSchema(String className, File outputFile) throws MojoExecutionException {
    outputFile.getParentFile().mkdirs();
    try (FileWriter writer = new FileWriter(outputFile)) {
      Class<?> theClass = classLoader.loadClass(className);
      writer.write(SchemaGenerator.prettyPrint(generator.generate(theClass)));
    } catch (IOException e) {
      throw new MojoExecutionException("Error generating schema", e);
    } catch (ClassNotFoundException e) {
      throw new MojoExecutionException("Class " + className + " not found");
    }
  }
}
