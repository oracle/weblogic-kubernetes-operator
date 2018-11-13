// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import static com.meterware.simplestub.Stub.createNiceStub;
import static oracle.kubernetes.json.SchemaGenerator.DEFAULT_KUBERNETES_VERSION;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_CLASSES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.objectweb.asm.Opcodes.ASM5;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
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
import org.junit.Ignore;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.TypePath;

public class JsonSchemaMojoTest {

  private static final List<String> EMPTY_CLASSPATH = new ArrayList<>();
  private static final String TARGET_DIR = "/target/dir";
  private static final String TEST_ROOT_CLASS = "a.b.c.D";
  private static final File SCHEMA_FILE = createFile(TARGET_DIR, TEST_ROOT_CLASS, ".json");
  private static final File CLASS_FILE = createFile("/classes", TEST_ROOT_CLASS, ".class");
  private static final String DOT = "\\.";

  private JsonSchemaMojo mojo = new JsonSchemaMojo();
  private Map<String, AnnotationInfo> classAnnotations = new HashMap<>();
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
    Field classPathField = JsonSchemaMojo.class.getDeclaredField("targetDir");
    assertThat(classPathField.getType(), equalTo(String.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).fields.get("defaultValue"),
        equalTo("${project.build.outputDirectory}/schema"));
  }

  @Test
  public void hasKubernetesVersionField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = JsonSchemaMojo.class.getDeclaredField("kubernetesVersion");
    assertThat(classPathField.getType(), equalTo(String.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).fields.get("defaultValue"),
        equalTo(DEFAULT_KUBERNETES_VERSION));
  }

  @Test
  public void hasRootClassNameField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = JsonSchemaMojo.class.getDeclaredField("rootClass");
    assertThat(classPathField.getType(), equalTo(String.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).fields.get("required"), is(true));
  }

  @Test
  @Ignore("need to detect parameter annotation w/o fields")
  public void hasIncludeDeprecatedField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = JsonSchemaMojo.class.getDeclaredField("includeDeprecated");
    assertThat(classPathField.getType(), equalTo(boolean.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).fields.get("required"), is(false));
  }

  @Test
  public void useSpecifiedKubernetesVersion() throws Exception {
    setMojoParameter("kubernetesVersion", "1.2.3");

    mojo.execute();

    assertThat(main.getVersion(), equalTo("1.2.3"));
  }

  @Test(expected = MojoExecutionException.class)
  public void whenUnableToUseKubernetesVersion_haltTheBuild() throws Exception {
    main.reportBadKubernetesException();
    setMojoParameter("kubernetesVersion", "1.2.3");

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

    assertThat(main.getOutputFile(), equalTo(SCHEMA_FILE));
  }

  @Test
  public void whenSchemaMoreRecentThanClassFile_dontGenerateNewSchema() throws Exception {
    fileSystem.defineFileContents(CLASS_FILE, "");
    fileSystem.defineFileContents(SCHEMA_FILE, "");
    fileSystem.touch(SCHEMA_FILE);

    mojo.execute();

    assertThat(main.getOutputFile(), nullValue());
  }

  @Test
  public void whenClassFileMoreRecentThanSchema_generateNewSchema() throws Exception {
    fileSystem.defineFileContents(CLASS_FILE, "");
    fileSystem.defineFileContents(SCHEMA_FILE, "");
    fileSystem.touch(CLASS_FILE);

    mojo.execute();

    assertThat(main.getOutputFile(), equalTo(SCHEMA_FILE));
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
        Field field = getField(fieldName);
        return new MojoFieldVisitor(getOrCreateAnnotationMap(field));
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

    MojoFieldVisitor(Map<String, AnnotationInfo> annotationMap) {
      super(ASM5);
      this.annotationMap = annotationMap;
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
}
