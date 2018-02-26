// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * create-weblogic-operator.sh tests
 */
public class CreateOperatorTest {

  private static final String CREATE_SCRIPT = "kubernetes/create-weblogic-operator.sh";
  private static String[] USAGE_REGEXPS = { "usage", "-o", "-i", "-g", "-h" };

  private ScratchDir scratch;

  @Before
  public void setUp() throws Exception {
    this.scratch = new ScratchDir();
  }

  @After
  public void tearDown() throws Exception {
    this.scratch.remove();
  }

  private ScratchDir scratch() { return this.scratch; }

  @Test
  public void testHelpOption() throws Exception {
    createOperator(" -h")
      .assertSuccess(USAGE_REGEXPS);
  }

  @Test
  public void testNoArgs() throws Exception {
    createOperator("")
      .assertFailure(USAGE_REGEXPS)
      .assertOutContains(CREATE_SCRIPT, "-o"); // it should print the name of the script and a complaint that -o must be specified
  }

  @Test
  public void testMissingOutputDirName() throws Exception {
    createOperator(" -o")
      .assertFailure(USAGE_REGEXPS, toArray("option requires an argument -- o"));
  }

  @Test
  public void testMissingInputFileName() throws Exception {
    createOperator(" -i")
      .assertFailure(USAGE_REGEXPS, toArray("option requires an argument -- i"));
  }

  @Test
  public void testUnsupportedOption() throws Exception {
    createOperator(" -z")
      .assertFailure(USAGE_REGEXPS, toArray("illegal option -- z"));
  }

  @Test
  public void testDefaultCreate() throws Exception {
    String userProjects = (new UserProjectsDir()).path().toString();
    createOperator(" -g -o " + userProjects).assertSuccess("Completed");
  }

  private class UserProjectsDir {
    private Path path;
    private UserProjectsDir() throws Exception {
      this.path = scratch().path().resolve("user-projects");
      Files.createDirectory(this.path);
    }
    private Path path() { return this.path; }
  }

  private class ScratchDir {
    private Path path;
    private ScratchDir() throws Exception {
      this.path = Files.createTempDirectory("CreateOperatorTest");

    }
    private Path path() { return this.path; }
    private void remove() throws Exception {
      final List<Path> pathsToDelete = Files.walk(path()).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
      for(Path p : pathsToDelete) {
        Files.deleteIfExists(p);
      }
    }
  }

  private String errorRegexp(String regexp) {
    return "ERROR: " + containsRegexp(regexp);
  }

  private String containsRegexp(String regexp) {
    return ".*" + regexp + ".*";
  }

  private ExecResult createOperator(String options) throws Exception {
    return exec(CREATE_SCRIPT + options);
  }

  private ExecResult exec(String command) throws Exception {
    Process p = Runtime.getRuntime().exec(command);
    try {
      p.waitFor();
      return new ExecResult(p.exitValue(), read(p.getInputStream()), read(p.getErrorStream()));
    } finally {
      p.destroy();
    }
  }

  private class ExecResult {
    private int exitValue;
    private String out;
    private String err;

    private ExecResult(int exitValue, String out, String err) throws Exception {
      this.exitValue = exitValue;
      this.out = out;
      this.err = err;
    }

    private int exitValue() { return this.exitValue; }
    private String out() { return this.out; }
    private String err() { return this.err; }

    private ExecResult assertSuccess(String... outRegexpsWant) {
      return assertSuccessExit().assertEmptyErr().assertOutContains(outRegexpsWant);
    }

    private ExecResult assertFailure(String... outRegexpsWant) {
      return assertFailureExit().assertEmptyErr().assertOutContains(outRegexpsWant);
    }

    private ExecResult assertFailure(String[] outRegexpsWant, String[] errRegexpsWant) {
      return assertFailureExit().assertErrContains(errRegexpsWant).assertOutContains(outRegexpsWant);
    }

    private ExecResult assertSuccessExit() {
      assertEquals(0, exitValue());
      return this;
    }

    private ExecResult assertFailureExit() {
      assertEquals(1, exitValue());
      return this;
    }

    private ExecResult assertEmptyErr() {
      assertEquals("", err());
      return this;
    }

    private ExecResult assertOutContains(String... regexpsWant) {
      assertContainsRegexps(out(), regexpsWant);
      return this;
    }

    private ExecResult assertErrContains(String... regexpsWant) {
      assertContainsRegexps(err(), regexpsWant);
      return this;
    }

    private void assertContainsRegexps(String have, String... regexpsWant) {
      for (String r : regexpsWant) {
        assertTrue(have.matches("(?s)" + containsRegexp(r))); // (?s) turns on multi-line matching
      }
    }
  }

  private String[] toArray(String ... vals) {
    return vals;
  }

  private String read(InputStream is) throws Exception {
    return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
  }
}
