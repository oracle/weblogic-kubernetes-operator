// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.SystemPropertySupport;
import oracle.kubernetes.mojosupport.MojoTestBase;
import oracle.kubernetes.mojosupport.TestFileSystem;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import static com.meterware.simplestub.Stub.createNiceStub;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.BLUE_FG;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.BOLD;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.GREEN_FG;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.RED_FG;
import static org.apache.maven.plugins.annotations.LifecyclePhase.TEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class ShUnit2MojoTest extends MojoTestBase {

  private static final String TEST_SCRIPT = "shunit2";
  private static final String OS_NAME_PROPERTY = "os.name";
  private static final String SKIP_UNIT_TESTS_PROPERTY = "skip.unit.tests";
  private static final String[] INSTALLED_OSX_BASH_VERSION = {
      "GNU bash, version 3.2.57(1)-release (x86_64-apple-darwin19)",
      "Copyright (C) 2007 Free Software Foundation, Inc."
  };

  private static final String[] HOMEBREW_BASH_VERSION = {
      "GNU bash, version 5.0.18(1)-release (x86_64-apple-darwin19.6.0)",
      "Copyright (C) 2019 Free Software Foundation, Inc.",
      "License GPLv3+: GNU GPL version 3 or later <http://gnu.org/licenses/gpl.html>"
  };

  private final ShUnit2Mojo mojo;
  private final Function<String, BashProcessBuilder> builderFunction = this::createProcessBuilder;
  private final TestDelegate delegate = new TestDelegate();
  private final TestFileSystem fileSystem = new TestFileSystem();
  private static final File TEST_CLASSES_DIRECTORY = new File("/test-classes");
  private static final File LATEST_SHUNIT2_DIRECTORY = new File(TEST_CLASSES_DIRECTORY, "shunit2/2.1.8");
  private static final File EARLIER_SHUNIT2_DIRECTORY = new File(TEST_CLASSES_DIRECTORY, "shunit2/2.1.6");
  private static final File SOURCE_DIRECTORY = new File("/sources");
  private static final File TEST_SOURCE_DIRECTORY = new File("/tests");

  public ShUnit2MojoTest() {
    super(new ShUnit2Mojo());
    mojo = getMojo();
  }

  @BeforeEach
  public void setUp() throws Exception {
    ClassReader classReader = new ClassReader(ShUnit2Mojo.class.getName());
    classReader.accept(new Visitor(ShUnit2Mojo.class), 0);

    mementos.add(StaticStubSupport.install(ShUnit2Mojo.class, "fileSystem", fileSystem));
    mementos.add(StaticStubSupport.install(ShUnit2Mojo.class, "builderFunction", builderFunction));
    mementos.add(SystemPropertySupport.install(OS_NAME_PROPERTY, "Linux"));
    mementos.add(SystemPropertySupport.install(SKIP_UNIT_TESTS_PROPERTY, ""));

    setMojoParameter("outputDirectory", TEST_CLASSES_DIRECTORY);
    setMojoParameter("sourceDirectory", SOURCE_DIRECTORY);
    setMojoParameter("testSourceDirectory", TEST_SOURCE_DIRECTORY);
    silenceMojoLog();

    fileSystem.defineFileContents(new File(LATEST_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
  }

  BashProcessBuilder createProcessBuilder(String command) {
    return new BashProcessBuilder(delegate, command);
  }

  @Test
  public void mojoAnnotatedWithName() {
    assertThat(getClassAnnotation(Mojo.class).getField("name"), equalTo("shunit2"));
  }

  @Test
  public void mojoAnnotatedWithDefaultPhase() {
    assertThat(getClassAnnotation(Mojo.class).getField("defaultPhase"), equalTo(TEST));
  }

  @Test
  public void hasOutputDirectoryField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = ShUnit2Mojo.class.getDeclaredField("outputDirectory");
    assertThat(classPathField.getType(), equalTo(File.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).getField("defaultValue"),
        equalTo("${project.build.testOutputDirectory}"));
  }

  @Test
  public void hasTestSourceDirectoryField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = ShUnit2Mojo.class.getDeclaredField("testSourceDirectory");
    assertThat(classPathField.getType(), equalTo(File.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).getField("defaultValue"),
        equalTo("${project.basedir}/src/test/sh"));
  }

  @Test
  public void hasSourceDirectoryField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = ShUnit2Mojo.class.getDeclaredField("sourceDirectory");
    assertThat(classPathField.getType(), equalTo(File.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).getField("defaultValue"),
        equalTo("${project.basedir}/src/main/sh"));
  }

  @Test
  public void useCopiedShUnit2Directory() throws MojoExecutionException {
    fileSystem.defineFileContents(new File(LATEST_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");

    assertThat(mojo.getEffectiveShUnit2Directory(), equalTo(LATEST_SHUNIT2_DIRECTORY));
  }

  @Test
  public void whenMultipleShUnit2VersionsInstalled_selectLatest() throws MojoExecutionException {
    fileSystem.defineFileContents(new File(EARLIER_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");
    fileSystem.defineFileContents(new File(LATEST_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");

    assertThat(mojo.getEffectiveShUnit2Directory(), equalTo(LATEST_SHUNIT2_DIRECTORY));
  }

  @Test
  public void whenLatestShUnit2VersionsMissing_selectPrior() throws MojoExecutionException {
    fileSystem.clear();
    fileSystem.defineFileContents(new File(EARLIER_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");
    fileSystem.defineFileContents(LATEST_SHUNIT2_DIRECTORY, "");

    assertThat(mojo.getEffectiveShUnit2Directory(), equalTo(EARLIER_SHUNIT2_DIRECTORY));
  }

  @Test
  public void whenShUnit2NotInstalled_reportFailure() {
    fileSystem.clear();
    fileSystem.defineFileContents(TEST_CLASSES_DIRECTORY, "");

    assertThrows(MojoExecutionException.class, this::executeMojo);
  }

  @Test
  public void ifSkipUnitTestSet_skipTesting() throws MojoFailureException, MojoExecutionException {
    System.setProperty(SKIP_UNIT_TESTS_PROPERTY, "true");

    executeMojo();

    assertThat(getInfoLines(), empty());
  }

  @Test
  public void onMacOS_warnIfBashIsOld() throws MojoFailureException, MojoExecutionException {
    System.setProperty(OS_NAME_PROPERTY, "Mac OS X");
    defineExecution().withOutputs(INSTALLED_OSX_BASH_VERSION);

    executeMojo();

    assertThat(getWarningLines(),
          contains("Bash 3.2 is too old to run unit tests. Install a later version with Homebrew: "
                + AnsiUtils.createFormatter(BOLD, BLUE_FG).format("brew install bash") + "."));
  }

  @Test
  public void onMacOS_dontRunTestsIfBashIsOld() throws MojoFailureException, MojoExecutionException {
    System.setProperty(OS_NAME_PROPERTY, "Mac OS X");
    defineExecution().withOutputs(INSTALLED_OSX_BASH_VERSION);
    defineExecution().withOutputs("This is an example", "and here is another", "Ran 2 tests.");

    executeMojo();

    assertThat(getInfoLines(), empty());
  }

  @Test
  public void onMacOS_dontWarnIfBashVersionIsSupported() throws MojoFailureException, MojoExecutionException {
    System.setProperty(OS_NAME_PROPERTY, "Mac OS X");
    defineExecution().withOutputs(HOMEBREW_BASH_VERSION);

    executeMojo();

    assertThat(getWarningLines(), empty());
  }

  @Test
  public void onMacOS_runTestsIfBashVersionIsSupported() throws MojoFailureException, MojoExecutionException {
    System.setProperty(OS_NAME_PROPERTY, "Mac OS X");
    defineExecution().withOutputs(HOMEBREW_BASH_VERSION);
    defineExecution().withOutputs("This is an example", "and here is another", "Ran 2 tests.");

    executeMojo();

    assertThat(getInfoLines(), contains("This is an example", "and here is another",
                    createExpectedSuccessSummary(2, "test1.sh")));
  }

  @Test
  public void onExecution_specifyTheSelectedShUnit2ScriptPath() throws MojoFailureException, MojoExecutionException {
    executeMojo();

    assertThat(delegate.getShUnit2ScriptPath(), equalTo(LATEST_SHUNIT2_DIRECTORY + "/shunit2"));
  }

  @Test
  public void onExecution_specifyPathToSourceScripts() throws MojoFailureException, MojoExecutionException {
    executeMojo();

    assertThat(delegate.getSourceScriptDir(), equalTo(SOURCE_DIRECTORY.getAbsolutePath()));
  }

  @Test
  public void onExecution_specifyTestScripts() throws MojoFailureException, MojoExecutionException {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test3.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "nothing.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "2ndtest.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "4thtest"), "");

    executeMojo();

    assertThat(delegate.getScriptPaths(),
          arrayContainingInAnyOrder("/tests/test1.sh", "/tests/2ndtest.sh", "/tests/test3.sh", "/tests/4thtest"));
  }

  @Test
  public void onExecution_logOutputs() throws MojoFailureException, MojoExecutionException {
    defineExecution().withOutputs("This is an example", "and here is another", "Ran 2 tests.");

    executeMojo();

    assertThat(getInfoLines(), contains("This is an example", "and here is another",
                    createExpectedSuccessSummary(2, "test1.sh")));
  }

  protected ProcessStub defineExecution() {
    return delegate.defineScriptExecution();
  }

  @SuppressWarnings("SameParameterValue")
  private String createExpectedSuccessSummary(int numTestsRun, String testScript) {
    return AnsiUtils.createFormatter(BOLD, GREEN_FG).format("Tests ")
          + AnsiUtils.createFormatter(BOLD, GREEN_FG).format("run: " + numTestsRun)
          + String.format(", Failures: 0, Errors: 0 - in /tests/%s", testScript);
  }

  @Test
  public void whenErrorDetected_reportInSummary() throws MojoExecutionException {
    defineExecution().withErrors("This is an example", "and here is another").withOutputs("Ran 3 tests.");

    try {
      executeMojo();
      fail("Should have thrown an exception");
    } catch (MojoFailureException ignored) {
      assertThat(getErrorLines(),
            contains("This is an example", "and here is another",
                  createExpectedErrorSummary(3, 2, "test1.sh")));
    }
  }

  @SuppressWarnings("SameParameterValue")
  private String createExpectedErrorSummary(int numTestsRun, int numErrors, String testScript) {
    return AnsiUtils.createFormatter(BOLD, RED_FG).format("Tests ")
          + AnsiUtils.createFormatter(BOLD).format("run: " + numTestsRun)
          + ", Failures: 0, "
          + AnsiUtils.createFormatter(BOLD, RED_FG).format("Errors: " + numErrors)
          + AnsiUtils.createFormatter(BOLD, RED_FG).format(" <<< FAILURE! - in /tests/" + testScript);
  }

  @Test
  public void onExecution_logErrors() throws MojoExecutionException {
    defineExecution().withErrors("This is an example", "and here is another");

    try {
      executeMojo();
      fail("Should have thrown an exception");
    } catch (MojoFailureException ignored) {
      assertThat(getErrorLines(), both(hasItem("This is an example")).and(hasItem("and here is another")));
    }
  }

  @Test
  public void onExecution_ignoreNonZeroReturnCodeErrors() throws MojoExecutionException {
    defineExecution().withErrors("This is an example",
          "\u001B[1;31mERROR:\u001B[0m testPartyLikeItIs1999() returned non-zero return code.");

    try {
      executeMojo();
      fail("Should have thrown an exception");
    } catch (MojoFailureException ignored) {
      assertThat(getErrorLines(), hasItem("This is an example"));
    }
  }

  @Test
  public void onExecution_ignoreFailureMessage() throws MojoFailureException, MojoExecutionException {
    defineExecution().withOutputs("This is an example", "FAILED (failures=2)");

    executeMojo();

    assertThat(getErrorLines(), empty());
  }

  @Test
  public void onExecution_recordReportedNumberOfTests() throws MojoFailureException, MojoExecutionException {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test2.sh"), "");
    final AnsiUtils.AnsiFormatter shUnit2RunCountFormat = AnsiUtils.createFormatter(BOLD, BLUE_FG);
    defineExecution().withOutputs(String.format("Ran %s tests.", shUnit2RunCountFormat.format("3")));
    defineExecution().withOutputs(String.format("Ran %s tests.", shUnit2RunCountFormat.format("2")));

    executeMojo();

    assertThat(mojo.getTestSuites().stream().map(TestSuite::numTestsRun).collect(Collectors.toList()), contains(3, 2));
  }

  @Test
  public void onExecution_recordReportedNumberOfFailures() throws MojoExecutionException {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test2.sh"), "");
    defineExecution()
          .withOutputs("test1", "test2", createExpectedTestFailure("expected up but was down"))
          .withErrors("test2 returned non-zero return code.");
    defineExecution()
          .withOutputs("test3", createExpectedTestFailure("expected blue but was red"),
                       "test4", createExpectedTestFailure("expected left but was right"));

    try {
      executeMojo();
    } catch (MojoFailureException e) {
      assertThat(getFailuresByTestSuite(), contains(1, 2));
    }
  }

  @Nonnull
  protected List<Integer> getFailuresByTestSuite() {
    return mojo.getTestSuites().stream().map(TestSuite::numFailures).collect(Collectors.toList());
  }

  private String createExpectedTestFailure(String explanation) {
    return AnsiUtils.createFormatter(BOLD, RED_FG).format("ASSERT:") + explanation;
  }

  @Test
  public void whenAnyTestsFail_mojoThrowsException() {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test2.sh"), "");
    defineExecution()
          .withOutputs("test1", "test2", createExpectedTestFailure("expected up but was down"))
          .withErrors("test2 returned non-zero return code.");
    defineExecution()
          .withOutputs("test3", createExpectedTestFailure("expected blue but was red"),
                       "test4", createExpectedTestFailure("expected left but was right"));

    assertThrows(MojoFailureException.class, this::executeMojo);
  }

  // todo print tests run, failures at end of each testsuite
  // todo print total tests run, total failures across multiple tests

  static class TestDelegate implements BiFunction<String, Map<String, String>, Process> {
    private final ArrayDeque<ProcessStub> processStubs = new ArrayDeque<>();
    private final List<String> commands = new ArrayList<>();
    private Map<String, String> environmentVariables;

    ProcessStub defineScriptExecution() {
      final ProcessStub processStub = createNiceStub(ProcessStub.class);
      processStubs.add(processStub);
      return processStub;
    }

    String getShUnit2ScriptPath() {
      return environmentVariables.get(ShUnit2Mojo.SHUNIT2_PATH);
    }

    String getSourceScriptDir() {
      return environmentVariables.get(ShUnit2Mojo.SCRIPTPATH);
    }

    String[] getScriptPaths() {
      return commands.toArray(new String[0]);
    }

    @Override
    public Process apply(String command, Map<String, String> environmentVariables) {
      this.commands.add(command);
      this.environmentVariables = environmentVariables;
      return Optional.ofNullable(processStubs.pollFirst()).orElseGet(this::defineScriptExecution);
    }
  }


  abstract static class ProcessStub extends Process {
    private final List<String> outputLines = new ArrayList<>();
    private final List<String> errorLines = new ArrayList<>();

    ProcessStub withOutputs(String... lines) {
      outputLines.addAll(Arrays.asList(lines));
      return this;
    }

    ProcessStub withErrors(String... lines) {
      errorLines.addAll(Arrays.asList(lines));
      return this;
    }

    @Override
    public InputStream getInputStream() {
      return new StringListInputStream(outputLines);
    }

    @Override
    public InputStream getErrorStream() {
      return new StringListInputStream(errorLines);
    }
  }

  static class StringListInputStream extends ByteArrayInputStream {

    public StringListInputStream(List<String> inputs) {
      super(toByteArray(inputs));
    }

    private static byte[] toByteArray(List<String> inputs) {
      return String.join(System.lineSeparator(), inputs).getBytes(StandardCharsets.UTF_8);
    }
  }
}
