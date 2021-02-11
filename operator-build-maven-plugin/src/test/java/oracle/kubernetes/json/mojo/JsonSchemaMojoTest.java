// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.mojosupport.MojoTestBase;
import oracle.kubernetes.mojosupport.TestFileSystem;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import static java.util.Collections.singletonList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_CLASSES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("SameParameterValue")
public class JsonSchemaMojoTest extends MojoTestBase {

  private static final List<String> EMPTY_CLASSPATH = new ArrayList<>();
  private static final String TARGET_DIR = "/target/dir";
  private static final String TEST_ROOT_CLASS = "a.b.c.D";
  private static final File SCHEMA_FILE = createFile(TARGET_DIR, TEST_ROOT_CLASS, ".json");
  private static final File MARKDOWN_FILE = createFile(TARGET_DIR, TEST_ROOT_CLASS, ".md");
  private static final File CLASS_FILE = createFile("/classes", TEST_ROOT_CLASS, ".class");
  private static final String SPECIFIED_FILE_NAME = "specifiedFile.json";
  private static final File SPECIFIED_FILE = new File(TARGET_DIR + "/" + SPECIFIED_FILE_NAME);

  private final TestFileSystem fileSystem = new TestFileSystem();

  private TestMain main;

  public JsonSchemaMojoTest() {
    super(new JsonSchemaMojo());
  }

  @BeforeEach
  public void setUp() throws Exception {
    ClassReader classReader = new ClassReader(JsonSchemaMojo.class.getName());
    classReader.accept(new Visitor(JsonSchemaMojo.class), 0);

    main = new TestMain();
    main.setClasspathResource(CLASS_FILE.toURI().toURL());
    setMojoParameter("compileClasspathElements", EMPTY_CLASSPATH);
    setMojoParameter("rootClass", TEST_ROOT_CLASS);
    setMojoParameter("targetDir", TARGET_DIR);
    setMojoParameter("baseDir", getModuleDir().toString());
    silenceMojoLog();

    mementos.add(StaticStubSupport.install(JsonSchemaMojo.class, "main", main));
    mementos.add(StaticStubSupport.install(JsonSchemaMojo.class, "fileSystem", fileSystem));
  }

  @Test
  public void mojoAnnotatedWithName() {
    assertThat(getClassAnnotation(Mojo.class).getField("name"), equalTo("generate"));
  }

  @Test
  public void mojoAnnotatedWithDefaultPhase() {
    assertThat(getClassAnnotation(Mojo.class).getField("defaultPhase"), equalTo(PROCESS_CLASSES));
  }

  @Test
  public void hasClasspathElementsField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = JsonSchemaMojo.class.getDeclaredField("compileClasspathElements");
    assertThat(classPathField.getType(), equalTo(List.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).getField("defaultValue"),
        equalTo("${project.compileClasspathElements}"));
  }

  @Test
  public void hasTargetDirField_withAnnotation() throws NoSuchFieldException {
    Field targetDirField = JsonSchemaMojo.class.getDeclaredField("targetDir");
    assertThat(targetDirField.getType(), equalTo(String.class));
    assertThat(
        getFieldAnnotation(targetDirField, Parameter.class).getField("defaultValue"),
        equalTo("${project.build.outputDirectory}/schema"));
  }

  @Test
  public void hasExternalSchemasField_withAnnotation() throws NoSuchFieldException {
    Field externalSchemasField = JsonSchemaMojo.class.getDeclaredField("externalSchemas");
    assertThat(externalSchemasField.getType(), equalTo(List.class));
    assertThat(fieldAnnotations.get(externalSchemasField), hasKey(toDescription(Parameter.class)));
  }

  @Test
  public void hasRootClassNameField_withAnnotation() throws NoSuchFieldException {
    Field rootClassField = JsonSchemaMojo.class.getDeclaredField("rootClass");
    assertThat(rootClassField.getType(), equalTo(String.class));
    assertThat(
        getFieldAnnotation(rootClassField, Parameter.class).getField("required"), is(true));
  }

  @Test
  public void hasIncludeAdditionalPropertiesField_withAnnotation() throws NoSuchFieldException {
    Field includeAdditionalPropertiesField =
        JsonSchemaMojo.class.getDeclaredField("includeAdditionalProperties");
    assertThat(includeAdditionalPropertiesField.getType(), equalTo(boolean.class));
    assertThat(
        fieldAnnotations.get(includeAdditionalPropertiesField),
        hasKey(toDescription(Parameter.class)));
  }

  @Test
  public void hasSupportObjectReferencesField_withAnnotation() throws Exception {
    Field supportObjectReferencesField =
        JsonSchemaMojo.class.getDeclaredField("supportObjectReferences");
    assertThat(supportObjectReferencesField.getType(), equalTo(boolean.class));
    assertThat(
        fieldAnnotations.get(supportObjectReferencesField), hasKey(toDescription(Parameter.class)));
    assertThat(getMojoParameter("supportObjectReferences"), is(true));
  }

  @Test
  public void hasKubernetesVersionField_withAnnotation() throws Exception {
    Field supportObjectReferencesField = JsonSchemaMojo.class.getDeclaredField("kubernetesVersion");
    assertThat(supportObjectReferencesField.getType(), equalTo(String.class));
    assertThat(
        fieldAnnotations.get(supportObjectReferencesField), hasKey(toDescription(Parameter.class)));
    assertThat(getMojoParameter("kubernetesVersion"), nullValue());
  }

  @Test
  public void hasGenerateMarkdownField_withAnnotation() throws Exception {
    Field supportObjectReferencesField = JsonSchemaMojo.class.getDeclaredField("generateMarkdown");
    assertThat(supportObjectReferencesField.getType(), equalTo(boolean.class));
    assertThat(
        fieldAnnotations.get(supportObjectReferencesField), hasKey(toDescription(Parameter.class)));
    assertThat(getMojoParameter("generateMarkdown"), is(false));
  }

  @Test
  public void hasOutputFileField_withAnnotation() throws Exception {
    Field field = JsonSchemaMojo.class.getDeclaredField("outputFile");
    assertThat(field.getType(), equalTo(String.class));
    assertThat(fieldAnnotations.get(field), hasKey(toDescription(Parameter.class)));
    assertThat(getMojoParameter("outputFile"), nullValue());
  }

  @Test
  public void whenKubernetesVersionSpecified_passToGenerator() throws Exception {
    setMojoParameter("kubernetesVersion", "1.9.0");

    executeMojo();

    assertThat(main.getKubernetesVersion(), equalTo("1.9.0"));
  }

  @Test
  public void whenKubernetesVersionNotSpecified_passToGenerator() throws Exception {
    setMojoParameter("kubernetesVersion", null);

    executeMojo();

    assertThat(main.getKubernetesVersion(), nullValue());
  }

  @Test
  public void whenExternalSchemaSpecified_passToGenerator() throws Exception {
    setMojoParameter(
        "externalSchemas",
        singletonList(new ExternalSchema("http://schema.json", "src/cache/schema.json")));

    executeMojo();

    assertThat(
        main.getCacheFor(new URL("http://schema.json")),
        equalTo(toModuleUrl("src/cache/schema.json")));
  }

  @Test
  public void whenUnableToUseDefineSchema_haltTheBuild() throws Exception {
    setMojoParameter(
        "externalSchemas",
        singletonList(new ExternalSchema("abcd://schema.json", "src/cache/schema.json")));

    assertThrows(MojoExecutionException.class, this::executeMojo);
  }

  @Test
  public void whenNoClassSpecified_haltTheBuild() throws Exception {
    setMojoParameter("rootClass", null);

    assertThrows(MojoExecutionException.class, this::executeMojo);
  }

  @Test
  public void whenLookingForClassFile_specifyRelativeFilePath() throws Exception {
    executeMojo();

    assertThat(main.getResourceName(), equalTo(classNameToPath(TEST_ROOT_CLASS) + ".class"));
  }

  @Test
  public void whenRootClassNotFound_haltTheBuild() {
    main.setClasspathResource(null);

    assertThrows(MojoExecutionException.class, this::executeMojo);
  }

  @Test
  public void useSpecifiedClasspath() throws Exception {
    String[] classpathElements = new String[] {"a", "b", "c"};
    setMojoParameter("compileClasspathElements", Arrays.asList(classpathElements));
    URL[] classPathUrls = new URL[] {new URL("file:abc"), new URL("file:bcd"), new URL("file:cde")};
    for (int i = 0; i < classpathElements.length; i++) {
      fileSystem.defineUrl(new File(classpathElements[i]), classPathUrls[i]);
    }

    executeMojo();

    assertThat(main.getClasspath(), arrayContaining(classPathUrls));
  }

  @Test
  public void generateToExpectedLocation() throws Exception {
    executeMojo();

    assertThat(main.getSchemaFile(), equalTo(SCHEMA_FILE));
  }

  @Test
  public void whenGenerateMarkdownNotSpecified_dontGenerateMarkdown() throws Exception {
    executeMojo();

    assertThat(main.getMarkdownFile(), nullValue());
  }

  @Test
  public void whenGenerateMarkdownSpecified_generateMarkdown() throws Exception {
    setMojoParameter("generateMarkdown", true);

    executeMojo();

    assertThat(main.getMarkdownFile(), equalTo(MARKDOWN_FILE));
  }

  @Test
  public void whenGenerateMarkdownSpecified_useGeneratedSchemaForMarkdown() throws Exception {
    Map<String, Object> generatedSchema = Map.of();
    main.setGeneratedSchema(generatedSchema);
    setMojoParameter("generateMarkdown", true);

    executeMojo();

    assertThat(main.getMarkdownSchema(), sameInstance(generatedSchema));
  }

  @Test
  public void whenSchemaMoreRecentThanClassFile_dontGenerateNewSchema() throws Exception {
    fileSystem.defineFileContents(CLASS_FILE, "");
    fileSystem.defineFileContents(SCHEMA_FILE, "");
    fileSystem.touch(SCHEMA_FILE);

    executeMojo();

    assertThat(main.getSchemaFile(), nullValue());
  }

  @Test
  public void whenClassFileMoreRecentThanSchema_generateNewSchema() throws Exception {
    fileSystem.defineFileContents(CLASS_FILE, "");
    fileSystem.defineFileContents(SCHEMA_FILE, "");
    fileSystem.touch(CLASS_FILE);

    executeMojo();

    assertThat(main.getSchemaFile(), equalTo(SCHEMA_FILE));
  }

  @Test
  public void whenOutputFileSpecified_generateToIt() throws Exception {
    setMojoParameter("outputFile", SPECIFIED_FILE_NAME);
    executeMojo();

    assertThat(main.getSchemaFile(), equalTo(SPECIFIED_FILE));
  }

  @Test
  public void whenIncludeAdditionalPropertiesSet_setOnMain() throws Exception {
    setMojoParameter("includeAdditionalProperties", true);

    executeMojo();

    assertThat(main.isIncludeAdditionalProperties(), is(true));
  }

  @Test
  public void whenSupportObjectReferencesSet_setOnMain() throws Exception {
    setMojoParameter("supportObjectReferences", true);

    executeMojo();

    assertThat(main.isSupportObjectReferences(), is(true));
  }

}
