// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import static oracle.kubernetes.json.SchemaGenerator.DEFAULT_KUBERNETES_VERSION;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
  name = "generate",
  defaultPhase = LifecyclePhase.PROCESS_CLASSES,
  requiresDependencyResolution = ResolutionScope.COMPILE
)
public class JsonSchemaMojo extends AbstractMojo {

  private static final String DOT = "\\.";

  @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
  private List<String> compileClasspathElements;

  @Parameter(defaultValue = "${project.build.outputDirectory}/schema")
  private String targetDir;

  @Parameter(defaultValue = DEFAULT_KUBERNETES_VERSION)
  private String kubernetesVersion;

  @Parameter(required = true)
  private String rootClass;

  @Parameter private boolean includeDeprecated;

  private static Main main = new MainImpl();
  private static FileSystem fileSystem = FileSystem.LIVE_FILE_SYSTEM;

  @Override
  public void execute() throws MojoExecutionException {
    main.setKubernetesVersion(kubernetesVersion);
    main.defineClasspath(toUrls(compileClasspathElements));

    if (rootClass == null) throw new MojoExecutionException("No root class specified");
    URL classUrl = main.getResource(toClassFileName(rootClass));
    if (classUrl == null) throw new MojoExecutionException("Class " + rootClass + " not found");

    if (updateNeeded(new File(classUrl.getPath()), getSchemaFile())) {
      getLog().info("Changes detected -- generating schema for " + rootClass + ".");
      main.generateSchema(rootClass, getSchemaFile());
    } else {
      getLog().info("Schema up-to-date. Skipping generation.");
    }
  }

  private boolean updateNeeded(File inputFile, File outputFile) {
    return !fileSystem.exists(outputFile)
        || fileSystem.getLastModified(outputFile) < fileSystem.getLastModified(inputFile);
  }

  private File getSchemaFile() {
    File target = new File(targetDir);
    fileSystem.createDirectory(target);
    return new File(target, classNameToFile(rootClass) + ".json");
  }

  private URL[] toUrls(List<String> paths) {
    return paths.stream().map(this::toURL).toArray(URL[]::new);
  }

  private URL toURL(String classpathElement) {
    try {
      return fileSystem.toURL(new File(classpathElement));
    } catch (MalformedURLException e) {
      throw new UncheckedMalformedUrlException(e);
    }
  }

  private String toClassFileName(String className) {
    return classNameToFile(className) + ".class";
  }

  private String classNameToFile(String className) {
    return className.replaceAll(DOT, File.separator);
  }

  private class UncheckedMalformedUrlException extends RuntimeException {
    UncheckedMalformedUrlException(Throwable cause) {
      super(cause);
    }
  }
}
