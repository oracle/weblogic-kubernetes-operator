// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
    name = "generate",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class JsonSchemaMojo extends AbstractMojo {

  private static final String DOT = "\\.";
  private static Main main = new MainImpl();
  private static FileSystem fileSystem = FileSystem.LIVE_FILE_SYSTEM;
  @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
  private List<String> compileClasspathElements;
  @Parameter(defaultValue = "${project.build.outputDirectory}/schema")
  private String targetDir;
  @Parameter private String kubernetesVersion;
  @Parameter private final List<ExternalSchema> externalSchemas = Collections.emptyList();
  @Parameter(required = true)
  private String rootClass;
  @Parameter private boolean includeDeprecated;
  @Parameter private boolean generateMarkdown;
  @Parameter private boolean includeAdditionalProperties;
  @SuppressWarnings("FieldCanBeLocal")
  @Parameter
  private final boolean supportObjectReferences = true;
  @Parameter(defaultValue = "${basedir}")
  private String baseDir;
  @Parameter private String outputFile;

  @Override
  public void execute() throws MojoExecutionException {
    main.defineClasspath(toUrls(compileClasspathElements));
    main.setIncludeDeprecated(includeDeprecated);
    main.setIncludeAdditionalProperties(includeAdditionalProperties);
    main.setSupportObjectReferences(supportObjectReferences);
    addExternalSchemas();

    if (rootClass == null) {
      throw new MojoExecutionException("No root class specified");
    }
    URL classUrl = main.getResource(toClassFileName(rootClass));
    if (classUrl == null) {
      throw new MojoExecutionException("Class " + rootClass + " not found");
    }

    if (updateNeeded(new File(classUrl.getPath()), getSchemaFile())) {
      getLog().info("Changes detected -- generating schema for " + rootClass + ".");
      generate();
    } else {
      getLog().info("Schema up-to-date. Skipping generation.");
    }
  }

  private void generate() throws MojoExecutionException {
    Map<String, Object> generatedSchema = main.generateSchema(rootClass, getSchemaFile());
    if (generateMarkdown) {
      getLog().info(" -- generating markdown for " + rootClass + ".");
      main.generateMarkdown("Domain", getMarkdownFile(), generatedSchema);
    }
  }

  private void addExternalSchemas() throws MojoExecutionException {
    try {
      if (kubernetesVersion != null) {
        main.setKubernetesVersion(kubernetesVersion);
      }
      for (ExternalSchema externalSchema : externalSchemas) {
        main.defineSchemaUrlAndContents(
            externalSchema.getUrl(), externalSchema.getCacheUrl(baseDir));
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to define external schema: ", e);
    }
  }

  private boolean updateNeeded(File inputFile, File outputFile) {
    return !fileSystem.exists(outputFile)
        || fileSystem.getLastModified(outputFile) < fileSystem.getLastModified(inputFile);
  }

  private File getSchemaFile() {
    File target = new File(targetDir);
    fileSystem.createDirectory(target);
    return new File(target, getOutputFile());
  }

  private String getOutputFile() {
    return Optional.ofNullable(outputFile).orElse(classNameToFile(rootClass) + ".json");
  }

  private File getMarkdownFile() {
    return new File(getSchemaFile().getAbsolutePath().replace(".json", ".md"));
  }

  private URL[] toUrls(List<String> paths) {
    return paths.stream().map(this::toUrl).toArray(URL[]::new);
  }

  private URL toUrl(String classpathElement) {
    try {
      return fileSystem.toUrl(new File(classpathElement));
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
