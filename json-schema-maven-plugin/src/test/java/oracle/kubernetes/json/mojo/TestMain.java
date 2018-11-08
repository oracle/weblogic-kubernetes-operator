// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.maven.plugin.MojoExecutionException;

public class TestMain implements Main {
  private String version;
  private URL[] classpath;
  private IOException badKubernetesVersionException;
  private URL classpathResource;
  private String className;
  private File outputFile;
  private String resourceName;

  TestMain() throws MalformedURLException {
    classpathResource = new URL("file:abc");
  }

  String getVersion() {
    return version;
  }

  void reportBadKubernetesException() {
    badKubernetesVersionException = new IOException();
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

  File getOutputFile() {
    return outputFile;
  }

  @Override
  public void setKubernetesVersion(String version) {
    this.version = version;
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
  public void generateSchema(String className, File outputFile) throws MojoExecutionException {
    this.className = className;
    this.outputFile = outputFile;
    if (badKubernetesVersionException != null) throw new MojoExecutionException("bad version");
  }
}
