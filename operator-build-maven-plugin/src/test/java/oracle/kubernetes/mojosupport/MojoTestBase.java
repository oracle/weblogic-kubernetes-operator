// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojosupport;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.TypePath;

import static com.meterware.simplestub.Stub.createNiceStub;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.objectweb.asm.Opcodes.ASM7;

public abstract class MojoTestBase {

  private final AbstractMojo mojo;
  private static final String DOT = "\\.";
  // a map of fields to their annotations
  protected Map<Field, Map<String, AnnotationInfo>> fieldAnnotations = new HashMap<>();
  protected List<Memento> mementos = new ArrayList<>();
  private final Map<String, AnnotationInfo> classAnnotations = new HashMap<>();
  private final LogStub logStub = createNiceStub(LogStub.class);

  @SuppressWarnings("SameParameterValue")
  protected static File createFile(String dir, String className, String extension) {
    return new File(dir + File.separator + MojoTestBase.classNameToPath(className) + extension);
  }

  protected static String classNameToPath(String className) {
    return className.replaceAll(DOT, File.separator);
  }

  private static File getTargetDir(Class<?> aaClass) throws URISyntaxException {
    File dir = MojoTestBase.getPackageDir(aaClass);
    while (dir.getParent() != null && !dir.getName().equals("target")) {
      dir = dir.getParentFile();
    }
    return dir;
  }

  private static File getPackageDir(Class<?> aaClass) throws URISyntaxException {
    URL url = aaClass.getResource(aaClass.getSimpleName() + ".class");
    return Paths.get(url.toURI()).toFile().getParentFile();
  }

  public MojoTestBase(AbstractMojo mojo) {
    this.mojo = mojo;
  }

  public void executeMojo() throws MojoFailureException, MojoExecutionException {
    mojo.execute();
  }

  protected List<String> getInfoLines() {
    return logStub.infoMessages;
  }

  protected List<String> getWarningLines() {
    return logStub.warningMessages;
  }

  protected List<String> getErrorLines() {
    return logStub.errorMessages;
  }

  protected void silenceMojoLog() {
    mojo.setLog(logStub);
  }

  protected void setMojoParameter(String fieldName, Object value) throws Exception {
    Field field = mojo.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(mojo, value);
  }

  protected Object getMojoParameter(String fieldName) throws Exception {
    Field field = mojo.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(mojo);
  }

  /**
   * Tear down test.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void mojoExtendsBaseClass() {
    assertThat(mojo, instanceOf(AbstractMojo.class));
  }

  @Test
  public void mojoHasGoalAnnotation() {
    assertThat(getClassAnnotation(Mojo.class), notNullValue());
  }

  @SuppressWarnings("SameParameterValue")
  protected AnnotationInfo getClassAnnotation(Class<? extends Annotation> annotationClass) {
    return classAnnotations.get(toDescription(annotationClass));
  }

  @SuppressWarnings("SameParameterValue")
  protected AnnotationInfo getFieldAnnotation(Field field, Class<? extends Annotation> annotation) {
    return fieldAnnotations.get(field).get(toDescription(annotation));
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

  protected String toDescription(Class<? extends Annotation> aaClass) {
    return "L" + aaClass.getName().replaceAll(DOT, "/") + ';';
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

  @SuppressWarnings("SameParameterValue")
  protected URL toModuleUrl(String path) throws URISyntaxException, MalformedURLException {
    return new File(getModuleDir(), path).toURI().toURL();
  }

  protected File getModuleDir() throws URISyntaxException {
    return MojoTestBase.getTargetDir(getClass()).getParentFile();
  }

  protected class Visitor extends ClassVisitor {
    private final Class<?> theClass;

    public Visitor(Class<?> theClass) {
      super(ASM7);
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

  private abstract class MojoAnnotationVisitor extends AnnotationVisitor {

    private final String annotationClassDesc;
    private final Map<String, AnnotationInfo> annotations;

    MojoAnnotationVisitor(Map<String, AnnotationInfo> annotations, String desc) {
      super(ASM7);
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
        if (value.equalsIgnoreCase(constant.toString())) {
          return constant;
        }
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
      super(ASM7);
      this.annotationMap = getOrCreateAnnotationMap(field);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new FieldAnnotationVisitor(annotationMap, desc);
    }
  }

  public static class AnnotationInfo {
    private final Map<String, Object> fields = new HashMap<>();

    public Object getField(String name) {
      return fields.get(name);
    }
  }

  abstract static class LogStub implements Log {
    private final List<String> infoMessages = new ArrayList<>();
    private final List<String> warningMessages = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();

    @Override
    public boolean isInfoEnabled() {
      return true;
    }

    @Override
    public void info(CharSequence content) {
      info(content, null);
    }

    @Override
    public void info(CharSequence content, Throwable error) {
      infoMessages.add(createLogMessage(content, error));
    }

    @Override
    public void info(Throwable error) {
      info(null, error);
    }

    @Nonnull
    public static String createLogMessage(CharSequence content, Throwable error) {
      final List<String> builder = new ArrayList<>();
      Optional.ofNullable(content).map(CharSequence::toString).ifPresent(builder::add);
      Optional.ofNullable(error).map(Throwable::toString).ifPresent(builder::add);
      return String.join(System.lineSeparator(), builder);
    }

    @Override
    public boolean isWarnEnabled() {
      return true;
    }

    @Override
    public void warn(CharSequence content) {
      warningMessages.add(createLogMessage(content, null));
    }

    @Override
    public boolean isErrorEnabled() {
      return true;
    }

    @Override
    public void error(CharSequence content) {
      error(content, null);
    }

    @Override
    public void error(CharSequence content, Throwable error) {
      errorMessages.add(createLogMessage(content, error));
    }

    @Override
    public void error(Throwable error) {
      error(null, error);
    }
  }
}
