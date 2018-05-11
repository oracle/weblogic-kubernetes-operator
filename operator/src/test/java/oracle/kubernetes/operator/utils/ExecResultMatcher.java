// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/** Matcher for testing the results of using java to execute a command */
public class ExecResultMatcher extends TypeSafeDiagnosingMatcher<ExecResult> {
  public static final String MULTI_LINE_REGEXP_PREFIX = "(?s)";
  private int expectedExitValue;
  private String[] stdoutRegExps;
  private String[] stderrRegExps;

  private ExecResultMatcher(int expectedExitValue, String[] stdoutRegExps, String[] stderrRegExps) {
    this.expectedExitValue = expectedExitValue;
    this.stdoutRegExps = stdoutRegExps;
    this.stderrRegExps = stderrRegExps;
  }

  public static ExecResultMatcher succeedsAndPrints(String... stdoutRegExps) {
    return new ExecResultMatcher(0, stdoutRegExps, null);
  }

  public static ExecResultMatcher failsAndPrints(String... stdoutRegExps) {
    return new ExecResultMatcher(1, stdoutRegExps, null);
  }

  public static ExecResultMatcher failsAndPrints(String[] stdoutRegExps, String[] stdErrRegExps) {
    return new ExecResultMatcher(1, stdoutRegExps, stdErrRegExps);
  }

  public static String errorRegexp(String regexp) {
    return toContainsRegExp("ERROR" + toContainsRegExp(regexp));
  }

  public static String toContainsRegExp(String regexp) {
    return ".*" + regexp + ".*";
  }

  public static String[] allOf(String[] list, String... more) {
    String[] combined = new String[list.length + more.length];
    System.arraycopy(list, 0, combined, 0, list.length);
    System.arraycopy(more, 0, combined, list.length, more.length);
    return combined;
  }

  public static String[] toArray(String... vals) {
    return vals;
  }

  @Override
  protected boolean matchesSafely(ExecResult execResult, Description description) {
    boolean rtn = true;
    if (hasIncorrectExitValue(description, execResult)) {
      rtn = false;
    }
    if (hasIncorrectOutputStream(description, "stdout", execResult.stdout(), stdoutRegExps)) {
      rtn = false;
    }
    if (hasIncorrectOutputStream(description, "stderr", execResult.stderr(), stderrRegExps)) {
      rtn = false;
    }
    return rtn;
  }

  private boolean hasIncorrectExitValue(Description description, ExecResult execResult) {
    if (execResult.exitValue() != expectedExitValue) {
      description.appendText("\n  exit value was ").appendValue(execResult.exitValue());
      return true;
    }
    return false;
  }

  private boolean hasIncorrectOutputStream(
      Description description, String streamName, String streamContent, String[] regExps) {
    if (regExps == null) {
      // null regExps implies that the stream should return no content
      if (streamContent.length() != 0) {
        description.appendText("\n  " + streamName + " has ").appendValue(streamContent);
        return true;
      }
    } else {
      List<String> missingRegexps = getMissingRegexps(streamContent, regExps);
      if (!missingRegexps.isEmpty()) {
        description.appendValueList(
            "\n  actual " + streamName + " was\n'" + streamContent + "'\n  is missing [",
            ", ",
            "]",
            missingRegexps);
        return true;
      }
    }
    return false;
  }

  private List<String> getMissingRegexps(String have, String... regExpsWant) {
    List<String> missing = new ArrayList<>();
    for (String regExp : regExpsWant) {
      if (!have.matches(MULTI_LINE_REGEXP_PREFIX + toContainsRegExp(regExp))) {
        missing.add(regExp);
      }
    }
    return missing;
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("\n  exit code ").appendValue(expectedExitValue);
    describeRequiredOutputStream(description, "stdout", stdoutRegExps);
    describeRequiredOutputStream(description, "stderr", stderrRegExps);
  }

  private void describeRequiredOutputStream(
      Description description, String streamName, String[] regExps) {
    if (regExps == null) {
      description.appendText("\n  with an empty " + streamName);
    } else {
      description.appendValueList("\n  with " + streamName + " containing [", ",", "]", regExps);
    }
  }
}
