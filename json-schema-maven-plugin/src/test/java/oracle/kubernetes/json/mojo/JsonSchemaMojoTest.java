// Copyright 2018,2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import static com.meterware.simplestub.Stub.createNiceStub;
import static java.util.Collections.singletonList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_CLASSES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.objectweb.asm.Opcodes.ASM5;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.TypePath;

@SuppressWarnings("SameParameterValue")
public class JsonSchemaMojoTest {

  private static final List<String> EMPTY_CLASSPATH = new ArrayList<>();
  private static final String TARGET_DIR = "/target/dir";
  private static final String TEST_ROOT_CLASS = "a.b.c.D";
  private static final File SCHEMA_FILE = createFile(TARGET_DIR, TEST_ROOT_CLASS, ".json");
  private static final File MARKDOWN_FILE = createFile(TARGET_DIR, TEST_ROOT_CLASS, ".md");
  private static final File CLASS_FILE = createFile("/classes", TEST_ROOT_CLASS, ".class");
  private static final String DOT = "\\.";
  private static final String SPECIFIED_FILE_NAME = "specifiedFile.json";
  private static final File SPECIFIED_FILE = new File(TARGET_DIR + "/" + SPECIFIED_FILE_NAME);

  private JsonSchemaMojo mojo = new JsonSchemaMojo();
  private Map<String, AnnotationInfo> classAnnotations = new HashMap<>();

  // a map of fields to their annotations
  private Map<Field, Map<String, AnnotationInfo>> fieldAnnotations = new HashMap<>();
  private List<Memento> mementos = new ArrayList<>();
  private TestMain main;
  private TestFileSystem fileSystem = new TestFileSystem();

  @SuppressWarnings("SameParameterValue")
  private static File createFile(String dir, String className, String extension) {
    return new File(dir + File.separator + classNameToPath(className) + extension);
  }

  private static String classNameToPath(String className) {
    return className.replaceAll(DOT, File.separator);
  }

  @Before
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

  private void silenceMojoLog() {
    mojo.setLog(createNiceStub(Log.class));
  }

  private void setMojoParameter(String fieldName, Object value) throws Exception {
    Field field = mojo.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(mojo, value);
  }

  private Object getMojoParameter(String fieldName) throws Exception {
    Field field = mojo.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(mojo);
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void mojoExtendsBaseClass() {
    assertThat(mojo, instanceOf(AbstractMojo.class));
  }

  @Test
  public void mojoHasGoalAnnotation() {
    assertThat(getClassAnnotation(Mojo.class), notNullValue());
  }

  @Test
  public void mojoAnnotatedWithName() {
    assertThat(getClassAnnotation(Mojo.class).fields.get("name"), equalTo("generate"));
  }

  @Test
  public void mojoAnnotatedWithDefaultPhase() {
    assertThat(getClassAnnotation(Mojo.class).fields.get("defaultPhase"), equalTo(PROCESS_CLASSES));
  }

  @Test
  public void hasClasspathElementsField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = JsonSchemaMojo.class.getDeclaredField("compileClasspathElements");
    assertThat(classPathField.getType(), equalTo(List.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).fields.get("defaultValue"),
        equalTo("${project.compileClasspathElements}"));
  }

  @Test
  public void hasTargetDirField_withAnnotation() throws NoSuchFieldException {
    Field targetDirField = JsonSchemaMojo.class.getDeclaredField("targetDir");
    assertThat(targetDirField.getType(), equalTo(String.class));
    assertThat(
        getFieldAnnotation(targetDirField, Parameter.class).fields.get("defaultValue"),
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
        getFieldAnnotation(rootClassField, Parameter.class).fields.get("required"), is(true));
  }

  @Test
  public void hasIncludeDeprecatedField_withAnnotation() throws NoSuchFieldException {
    Field includeDeprecatedField = JsonSchemaMojo.class.getDeclaredField("includeDeprecated");
    assertThat(includeDeprecatedField.getType(), equalTo(boolean.class));
    assertThat(
        fieldAnnotations.get(includeDeprecatedField), hasKey(toDescription(Parameter.class)));
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

    mojo.execute();

    assertThat(main.getKubernetesVersion(), equalTo("1.9.0"));
  }

  @Test
  public void whenKubernetesVersionNotSpecified_passToGenerator() throws Exception {
    setMojoParameter("kubernetesVersion", null);

    mojo.execute();

    assertThat(main.getKubernetesVersion(), nullValue());
  }

  @Test
  public void whenExternalSchemaSpecified_passToGenerator() throws Exception {
    setMojoParameter(
        "externalSchemas",
        singletonList(new ExternalSchema("http://schema.json", "src/cache/schema.json")));

    mojo.execute();

    assertThat(
        main.getCacheFor(new URL("http://schema.json")),
        equalTo(toModuleUrl("src/cache/schema.json")));
  }

  @Test(expected = MojoExecutionException.class)
  public void whenUnableToUseDefineSchema_haltTheBuild() throws Exception {
    setMojoParameter(
        "externalSchemas",
        singletonList(new ExternalSchema("abcd://schema.json", "src/cache/schema.json")));

    mojo.execute();
  }

  @Test(expected = MojoExecutionException.class)
  public void whenNoClassSpecified_haltTheBuild() throws Exception {
    setMojoParameter("rootClass", null);
    mojo.execute();
  }

  @Test
  public void whenLookingForClassFile_specifyRelativeFilePath() throws Exception {
    mojo.execute();

    assertThat(main.getResourceName(), equalTo(classNameToPath(TEST_ROOT_CLASS) + ".class"));
  }

  @Test(expected = MojoExecutionException.class)
  public void whenRootClassNotFound_haltTheBuild() throws Exception {
    main.setClasspathResource(null);
    mojo.execute();
  }

  @Test
  public void useSpecifiedClasspath() throws Exception {
    String[] classpathElements = new String[] {"a", "b", "c"};
    setMojoParameter("compileClasspathElements", Arrays.asList(classpathElements));
    URL[] classPathUrls = new URL[] {new URL("file:abc"), new URL("file:bcd"), new URL("file:cde")};
    for (int i = 0; i < classpathElements.length; i++)
      fileSystem.defineURL(new File(classpathElements[i]), classPathUrls[i]);

    mojo.execute();

    assertThat(main.getClasspath(), arrayContaining(classPathUrls));
  }

  @Test
  public void generateToExpectedLocation() throws Exception {
    mojo.execute();

    assertThat(main.getSchemaFile(), equalTo(SCHEMA_FILE));
  }

  @Test
  public void whenGenerateMarkdownNotSpecified_dontGenerateMarkdown() throws Exception {
    mojo.execute();

    assertThat(main.getMarkdownFile(), nullValue());
  }

  @Test
  public void whenGenerateMarkdownSpecified_generateMarkdown() throws Exception {
    setMojoParameter("generateMarkdown", true);

    mojo.execute();

    assertThat(main.getMarkdownFile(), equalTo(MARKDOWN_FILE));
  }

  @Test
  public void whenGenerateMarkdownSpecified_useGeneratedSchemaForMarkdown() throws Exception {
    ImmutableMap<String, Object> generatedSchema = ImmutableMap.of();
    main.setGeneratedSchema(generatedSchema);
    setMojoParameter("generateMarkdown", true);

    mojo.execute();

    assertThat(main.getMarkdownSchema(), sameInstance(generatedSchema));
  }

  @Test
  public void whenSchemaMoreRecentThanClassFile_dontGenerateNewSchema() throws Exception {
    fileSystem.defineFileContents(CLASS_FILE, "");
    fileSystem.defineFileContents(SCHEMA_FILE, "");
    fileSystem.touch(SCHEMA_FILE);

    mojo.execute();

    assertThat(main.getSchemaFile(), nullValue());
  }

  @Test
  public void whenClassFileMoreRecentThanSchema_generateNewSchema() throws Exception {
    fileSystem.defineFileContents(CLASS_FILE, "");
    fileSystem.defineFileContents(SCHEMA_FILE, "");
    fileSystem.touch(CLASS_FILE);

    mojo.execute();

    assertThat(main.getSchemaFile(), equalTo(SCHEMA_FILE));
  }

  @Test
  public void whenOutputFileSpecified_generateToIt() throws Exception {
    setMojoParameter("outputFile", SPECIFIED_FILE_NAME);
    mojo.execute();

    assertThat(main.getSchemaFile(), equalTo(SPECIFIED_FILE));
  }

  @Test
  public void whenIncludeDeprecatedSet_setOnMain() throws Exception {
    setMojoParameter("includeDeprecated", true);

    mojo.execute();

    assertThat(main.isIncludeDeprecated(), is(true));
  }

  @Test
  public void whenIncludeAdditionalPropertiesSet_setOnMain() throws Exception {
    setMojoParameter("includeAdditionalProperties", true);

    mojo.execute();

    assertThat(main.isIncludeAdditionalProperties(), is(true));
  }

  @Test
  public void whenSupportObjectReferencesSet_setOnMain() throws Exception {
    setMojoParameter("supportObjectReferences", true);

    mojo.execute();

    assertThat(main.isSupportObjectReferences(), is(true));
  }

  @SuppressWarnings("SameParameterValue")
  private AnnotationInfo getClassAnnotation(Class<? extends Annotation> annotationClass) {
    return classAnnotations.get(toDescription(annotationClass));
  }

  @SuppressWarnings("SameParameterValue")
  private AnnotationInfo getFieldAnnotation(Field field, Class<? extends Annotation> annotation) {
    return fieldAnnotations.get(field).get(toDescription(annotation));
  }

  private class Visitor extends ClassVisitor {
    private Class<?> theClass;

    Visitor(Class<?> theClass) {
      super(ASM5);
      this.theClass = theClass;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        int typeRef, TypePath typePath, String desc, boolean visible) {
      return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new ClassAnnotationVisitor(desc);
    }

    @Override
    public FieldVisitor visitField(int flags, String fieldName, String desc, String s, Object v) {
      try {
        return new MojoFieldVisitor(getField(fieldName));
      } catch (NoSuchFieldException e) {
        return super.visitField(flags, fieldName, desc, s, v);
      }
    }

    private Field getField(String fieldName) throws NoSuchFieldException {
      return theClass.getDeclaredField(fieldName);
    }
  }

  private Map<String, AnnotationInfo> getOrCreateAnnotationMap(Field field) {
    Map<String, AnnotationInfo> map = fieldAnnotations.get(field);
    return map != null ? map : createAnnotationMap(field);
  }

  private Map<String, AnnotationInfo> createAnnotationMap(Field field) {
    Map<String, AnnotationInfo> map = new HashMap<>();
    fieldAnnotations.put(field, map);
    return map;
  }

  private abstract class MojoAnnotationVisitor extends AnnotationVisitor {
    private String annotationClassDesc;
    private Map<String, AnnotationInfo> annotations;

    MojoAnnotationVisitor(Map<String, AnnotationInfo> annotations, String desc) {
      super(ASM5);
      this.annotations = annotations;
      annotationClassDesc = desc;
      annotations.put(desc, new AnnotationInfo());
    }

    @Override
    public void visit(String name, Object value) {
      getOrCreateAnnotationInfo(annotationClassDesc, annotations).fields.put(name, value);
    }

    @Override
    public void visitEnum(String name, String enumDesc, String value) {
      getOrCreateAnnotationInfo(annotationClassDesc, annotations)
          .fields
          .put(name, getEnumConstant(getEnumClass(enumDesc), value));
    }

    Class<?> getEnumClass(String desc) {
      try {
        String className = desc.substring(1, desc.length() - 1).replaceAll("/", DOT);
        return Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e.toString());
      }
    }

    private Object getEnumConstant(Class<?> enumClass, String value) {
      for (Object constant : enumClass.getEnumConstants()) {
        if (value.equalsIgnoreCase(constant.toString())) return constant;
      }
      throw new RuntimeException("No enum constant " + value + " in " + enumClass);
    }
  }

  private class ClassAnnotationVisitor extends MojoAnnotationVisitor {

    ClassAnnotationVisitor(String annotationDescriptor) {
      super(classAnnotations, annotationDescriptor);
    }
  }

  private class FieldAnnotationVisitor extends MojoAnnotationVisitor {
    FieldAnnotationVisitor(Map<String, AnnotationInfo> annotationMap, String desc) {
      super(annotationMap, desc);
    }
  }

  private class MojoFieldVisitor extends FieldVisitor {
    private final Map<String, AnnotationInfo> annotationMap;

    MojoFieldVisitor(Field field) {
      super(ASM5);
      this.annotationMap = getOrCreateAnnotationMap(field);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new FieldAnnotationVisitor(annotationMap, desc);
    }
  }

  private String toDescription(Class<? extends Annotation> aClass) {
    return "L" + aClass.getName().replaceAll(DOT, "/") + ';';
  }

  private AnnotationInfo getOrCreateAnnotationInfo(
      String description, Map<String, AnnotationInfo> map) {
    AnnotationInfo info = map.get(description);
    return info != null ? info : createAnnotationInfo(map, description);
  }

  private AnnotationInfo createAnnotationInfo(Map<String, AnnotationInfo> map, String description) {
    AnnotationInfo info = new AnnotationInfo();
    map.put(description, info);
    return info;
  }

  private class AnnotationInfo {
    private Map<String, Object> fields = new HashMap<>();
  }

  private URL toModuleUrl(String path) throws URISyntaxException, MalformedURLException {
    return new File(getModuleDir(), path).toURI().toURL();
  }

  private File getModuleDir() throws URISyntaxException {
    return getTargetDir(getClass()).getParentFile();
  }

  private static File getTargetDir(Class<?> aClass) throws URISyntaxException {
    File dir = getPackageDir(aClass);
    while (dir.getParent() != null && !dir.getName().equals("target")) {
      dir = dir.getParentFile();
    }
    return dir;
  }

  private static File getPackageDir(Class<?> aClass) throws URISyntaxException {
    URL url = aClass.getResource(aClass.getSimpleName() + ".class");
    return Paths.get(url.toURI()).toFile().getParentFile();
  }
}
