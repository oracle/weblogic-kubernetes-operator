// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.After;
import org.junit.Before;

import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that the yaml files that create-weblogic-operator.sh generates,
 * based on the inputs the customer can specify, are correct.
 */
public class CreateOperatorGeneratedFilesTest extends CreateOperatorTest {

  protected CreateOperatorInputs inputs;
  protected ParsedWeblogicOperatorYaml weblogicOperatorYaml;
  protected ParsedWeblogicOperatorSecurityYaml weblogicOperatorSecurityYaml;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    inputs = createInputs();
    if (inputs != null) {
      assertThat(execCreateOperator(inputs), succeedsAndPrints("Completed"));
      weblogicOperatorYaml = new ParsedWeblogicOperatorYaml(weblogicOperatorYamlPath(inputs), inputs);
      weblogicOperatorSecurityYaml = new ParsedWeblogicOperatorSecurityYaml(weblogicOperatorSecurityYamlPath(inputs), inputs);
    } else {
      weblogicOperatorYaml = null;
      weblogicOperatorSecurityYaml = null;
    }
  }

  @After
  @Override
  public void tearDown() throws Exception {
    weblogicOperatorYaml = null;
    weblogicOperatorSecurityYaml = null;
    inputs = createInputs();
    super.tearDown();
  }

  protected CreateOperatorInputs createInputs() throws Exception {
    return newInputs();
  }
}
