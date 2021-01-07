// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import oracle.kubernetes.mojosupport.FileSystem;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.BLUE_FG;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.BOLD;

/*
 Will run all shunit2 tests in the testSourceDirectory named "test*" or "*test" with environment variables:
  SHUNIT2_PATH - pointing to the shunit2 script to include
  SCRIPTPATH - pointing to the sourceDirectory that contains scripts to test

 This mojo will run as part of the build in which it is configured. To run from the command line,

   1. add the following to ~/.m2/settings.xml:

      <pluginGroups>
        <pluginGroup>oracle.kubernetes</pluginGroup>
      </pluginGroups>

   2. execute:

      mvn operator-build:shunit2 -pl <module>

      where <module> is the name of the subdirectory containing the module on which the plugin is to be run.
 */


@Mojo(
    name = "shunit2",
    defaultPhase = LifecyclePhase.TEST,
    requiresDependencyResolution = ResolutionScope.NONE)
public class ShUnit2Mojo extends AbstractMojo {

  static final String SHUNIT2_PATH = "SHUNIT2_PATH";
  static final String SCRIPTPATH = "SCRIPTPATH";

  private static final String BASH_TOO_OLD_MESSAGE =
        "Bash %d.%d is too old to run unit tests."
        + " Install a later version with Homebrew: "
        + AnsiUtils.createFormatter(BOLD, BLUE_FG).format("brew install bash")
        + ".";
  private static final Pattern DIGITS = Pattern.compile("\\d+");
  private static final Pattern VERSION_PATTERN = Pattern.compile("GNU bash, version (\\d+)\\.(\\d+)");
  private static final String SHUNIT2_SCRIPT_ROOT = "shunit2";
  private static final int MINIMUM_SUPPORTED_BASH_MAJOR_VERSION = 4;
  private static final int MINIMUM_SUPPORTED_BASH_MINOR_VERSION = 2;

  @SuppressWarnings("FieldMayBeFinal") // not final to allow unit test to change it
  private static FileSystem fileSystem = FileSystem.LIVE_FILE_SYSTEM;

  @SuppressWarnings("FieldMayBeFinal") // not final to allow unit test to change it
  private static Function<String, BashProcessBuilder> builderFunction = BashProcessBuilder::new;

  /** The directory into which the mojo will copy the shunit2 script. */
  @SuppressWarnings("unused") // set by Maven
  @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true, required = true)
  private File outputDirectory;

  /** The directory containing the scripts to be tested. Test scripts will find SOURCE_DIR set with this value. */
  @SuppressWarnings("unused") // set by Maven
  @Parameter(defaultValue = "${project.basedir}/src/main/sh", readonly = true, required = true)
  private File sourceDirectory;

  /** The director this mojo will search for tests to execute. */
  @Parameter(defaultValue = "${project.basedir}/src/test/sh", readonly = true, required = true)
  @SuppressWarnings("unused") // set by Maven
  private File testSourceDirectory;

  private Map<String, String> environmentVariables;
  private List<TestSuite> testSuites;

  @Override
  public void execute() throws MojoFailureException, MojoExecutionException {
    if (isEnvironmentNotSupported() || isSkipTestsRequested()) {
      return;
    }
    
    environmentVariables = getEnvironmentVariables();
    testSuites = Arrays.stream(getScriptPaths()).map(this::createTestSuite).collect(Collectors.toList());

    testSuites.forEach(TestSuite::run);
    if ((totalNumFailures() + totalNumErrors()) != 0) {
      throw new MojoFailureException(String.format("%d failures, %d errors", totalNumFailures(), totalNumErrors()));
    }
  }

  private boolean isSkipTestsRequested() {
    return "true".equalsIgnoreCase(System.getProperty("skip.unit.tests"));
  }

  private boolean isEnvironmentNotSupported() throws MojoExecutionException {
    return isMacOSX() && isBashTooOld(getBashVersion());
  }

  private boolean isMacOSX() {
    return System.getProperty("os.name").startsWith("Mac ");
  }

  private boolean isBashTooOld(int[] bashVersion) {
    if (isBashVersionTooOld(bashVersion)) {
      getLog().warn(createVersionTooOldMessage(bashVersion));
      return true;
    }

    return false;
  }

  private boolean isBashVersionTooOld(int[] version) {
    return version[0] < MINIMUM_SUPPORTED_BASH_MAJOR_VERSION
          || (version[0] == MINIMUM_SUPPORTED_BASH_MAJOR_VERSION && version[1] < MINIMUM_SUPPORTED_BASH_MINOR_VERSION);
  }

  private int[] getBashVersion() throws MojoExecutionException {
    try {
      final Process process = builderFunction.apply("-version").build();
      process.waitFor();
      return extractVersionFromResponse(process);
    } catch (InterruptedException | IOException e) {
      throw new MojoExecutionException("Unable to check bash version", e);
    }
  }

  private int[] extractVersionFromResponse(Process process) throws IOException {
    try (final InputStream inputStream = process.getInputStream()) {
      return new BufferedReader(new InputStreamReader(inputStream)).lines()
            .map(this::parseVersionNumber)
            .filter(Objects::nonNull)
            .findAny()
            .orElseThrow(() -> new IOException("No bash version detected"));
    }
  }

  private int[] parseVersionNumber(String message) {
    final Matcher matcher = VERSION_PATTERN.matcher(message);
    if (!matcher.find()) {
      return null;
    } else {
      return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    }
  }

  private String createVersionTooOldMessage(int[] version) {
    return String.format(BASH_TOO_OLD_MESSAGE, version[0], version[1]);
  }


  private int totalNumFailures() {
    return testSuites.stream().mapToInt(TestSuite::numFailures).sum();
  }

  private int totalNumErrors() {
    return testSuites.stream().mapToInt(TestSuite::getNumErrors).sum();
  }

  private TestSuite createTestSuite(String scriptPath) {
    return new TestSuite(builderFunction, scriptPath, getLog(), environmentVariables);
  }

  List<TestSuite> getTestSuites() {
    return testSuites;
  }

  private Map<String, String> getEnvironmentVariables() throws MojoExecutionException {
    return Map.of(
          SHUNIT2_PATH, getEffectiveShUnit2Directory() + "/shunit2",
          SCRIPTPATH, sourceDirectory.getAbsolutePath());
  }

  private String[] getScriptPaths() {
    return Arrays.stream(fileSystem.listFiles(testSourceDirectory, this::isTestScript))
          .map(File::getAbsolutePath)
          .toArray(String[]::new);
  }

  private boolean isTestScript(File directory, String fileName) {
    return isTestName(fileName.toLowerCase().split("\\.")[0]);
  }

  private boolean isTestName(String baseName) {
    return baseName.startsWith("test") || baseName.endsWith("test");
  }

  File getEffectiveShUnit2Directory() throws MojoExecutionException {
    return lookupShUnit2Install();
  }

  private File lookupShUnit2Install() throws MojoExecutionException {
    return Optional.ofNullable(lookupLatestShUnit2Install())
          .orElseThrow(() -> new MojoExecutionException("Cannot find shunit2 installation."));
  }

  // It is possible that we have more than one version of shunit2 built into the plugin, which the copy-resources
  // phase will copy into the classes directory. This method iterates through them, selects the highest version,
  // looking only at those which actually contain a 'shunit2' script, and returns the selected install directory.
  private File lookupLatestShUnit2Install() {
    return Optional.of(getShUnitRootDirectory())
          .filter(this::exists)
          .map(this::getVersionSubdirectories).orElse(Stream.empty())
          .max(this::compare)
          .orElse(null);
  }

  private File getShUnitRootDirectory() {
    return new File(outputDirectory, SHUNIT2_SCRIPT_ROOT);
  }

  private boolean exists(File file) {
    return fileSystem.exists(file);
  }

  @Nonnull
  private Stream<File> getVersionSubdirectories(File rootDirectory) {
    return Arrays.stream(fileSystem.listFiles(rootDirectory, this::hasShUnit2Install));
  }

  boolean hasShUnit2Install(File directory, String fileName) {
    return fileSystem.exists(new File(directory, String.join(File.separator, fileName, SHUNIT2_SCRIPT_ROOT)));
  }

  // The last element of each file is expected to be a version in the form <major>.<minor>.<version>. This comparator
  // sorts the lowest version first, so that "1.2.4" compared with "1.3.1" will return -1.
  int compare(File first, File second) {
    return Long.compare(toLong(first), toLong(second));
  }

  // Given a File representing a path to a version directory (consisting of numbers and periods), converts it to a long.
  private long toLong(File versionFile) {
    long result = 0;
    final Matcher m = DIGITS.matcher(versionFile.getName());
    while (m.find()) {
      result = (result * 100) + Long.parseLong(m.group());
    }
    return result;
  }


}
