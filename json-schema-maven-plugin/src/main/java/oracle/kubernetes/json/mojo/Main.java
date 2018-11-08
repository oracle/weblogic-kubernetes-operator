// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.net.URL;
import org.apache.maven.plugin.MojoExecutionException;

public interface Main {

  /** Specify the Kubernetes version to support. */
  void setKubernetesVersion(String version) throws MojoExecutionException;

  /**
   * Specify the classpath for the class whose schema is to be built
   *
   * @param classpathElements a list of elements of a classpath
   */
  void defineClasspath(URL... classpathElements);

  /**
   * Returns a resource from the classpath, corresponding to the specified name.
   *
   * @return a url to the specified resource, or null if none is found
   */
  URL getResource(String name);

  /**
   * Generates a schema for the specified class, to the specified output file.
   *
   * @param className the root class for the schema
   * @param outputFile the file to generate
   */
  void generateSchema(String className, File outputFile) throws MojoExecutionException;
}
