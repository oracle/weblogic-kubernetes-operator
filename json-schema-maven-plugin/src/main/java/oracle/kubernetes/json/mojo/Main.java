// Copyright 2018,2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.apache.maven.plugin.MojoExecutionException;

public interface Main {

  /**
   * Specifies the Kubernetes version to be used for object definitions.
   *
   * @param kubernetesVersion the desired version
   */
  void setKubernetesVersion(String kubernetesVersion) throws IOException;

  /**
   * Defines an external schema URL to be used for object definitions
   *
   * @param schemaURL the schema URL
   * @param cacheUrl a file url specifying a local cache of the schema
   * @throws IOException if there is a problem using the URLs
   */
  void defineSchemaUrlAndContents(URL schemaURL, URL cacheUrl) throws IOException;

  /**
   * Specifies that deprecated fields should be included when generating a schema. If false, they
   * will be ignored.
   *
   * @param includeDeprecated true if deprecated fields should be included
   */
  void setIncludeDeprecated(boolean includeDeprecated);

  /**
   * Specifies that the "additionalProperties" property will be added to the schema for each object
   * and set to false, to forbid any unspecified properties
   *
   * @param includeAdditionalProperties true if unspecified properties should cause validation to
   *     fail
   */
  void setIncludeAdditionalProperties(boolean includeAdditionalProperties);

  /**
   * Specifies that object fields will be implemented as references using the $ref field, and any
   * not defined in an external schema will be added to "definitions." If false, they will be
   * represented as inline definitions.
   *
   * @param supportObjectReferences true if objects are to be represented as references
   */
  void setSupportObjectReferences(boolean supportObjectReferences);

  /**
   * Specify the classpath for the class whose schema is to be built
   *
   * @param classpathElements a list of elements of a classpath
   */
  void defineClasspath(URL... classpathElements);

  /**
   * Returns a resource from the classpath, corresponding to the specified name.
   *
   * @param name Name of the resource to be returned
   * @return a url to the specified resource, or null if none is found
   */
  URL getResource(String name);

  /**
   * Generates a schema for the specified class, to the specified output file.
   *
   * @param className the root class for the schema
   * @param outputFile the file to generate
   * @throws MojoExecutionException if an exception occurred during the schema generation
   */
  Map<String, Object> generateSchema(String className, File outputFile)
      throws MojoExecutionException;

  /**
   * Generates markdown for the newly-generated schema to the specified output file.
   *
   * @param rootName
   * @param outputFile the file to generate
   * @param schema
   * @throws MojoExecutionException if an exception occurred during the markdown generation
   */
  void generateMarkdown(String rootName, File outputFile, Map<String, Object> schema)
      throws MojoExecutionException;
}
