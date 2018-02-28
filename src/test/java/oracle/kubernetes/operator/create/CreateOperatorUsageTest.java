// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

import static oracle.kubernetes.operator.create.ExecResultMatcher.allOf;
import static oracle.kubernetes.operator.create.ExecResultMatcher.failsAndPrints;
import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.create.ExecResultMatcher.toArray;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test the various create-weblogic-operator.sh command line options that print usage messages
 */
public class CreateOperatorUsageTest extends CreateOperatorTest {

  private static final String[] USAGE = { "usage", "-o", "-i", "-g", "-h" };

  @Test
  public void helpOption_succeedsAndPrintsUsage() throws Exception {
    assertThat(execCreateOperator(" -h"), succeedsAndPrints(USAGE));
  }

  @Test
  public void noOption_failsAndPrintsErrorAndUsage() throws Exception {
    assertThat(execCreateOperator(""), failsAndPrints(allOf(USAGE, CREATE_SCRIPT, "-o must be specified")));
  }

  @Test
  public void missingOutputDir_failsAndPrintsErrorAndUsage() throws Exception {
    assertThat(execCreateOperator(" -o"), failsAndPrints(USAGE, toArray("option requires an argument -- o")));
  }

  @Test
  public void missingInputFileName_failsAndPrintsErrorAndUsage() throws Exception {
    assertThat(execCreateOperator(" -i"), failsAndPrints(USAGE, toArray("option requires an argument -- i")));
  }

  @Test
  public void unsupportedOption_failsAndPrintsErrorAndUsage() throws Exception {
    assertThat(execCreateOperator(" -z"), failsAndPrints(USAGE, toArray("illegal option -- z")));
  }
}
