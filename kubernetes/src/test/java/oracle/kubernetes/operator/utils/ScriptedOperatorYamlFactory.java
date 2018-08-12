// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.ExecCreateOperator.execCreateOperator;
import static oracle.kubernetes.operator.utils.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.utils.UserProjects.createUserProjectsDirectory;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;

public class ScriptedOperatorYamlFactory extends OperatorYamlFactory {

  @Override
  public OperatorValues createDefaultValues() throws Exception {
    return CreateOperatorInputs.readDefaultInputsFile();
  }

  @Override
  public GeneratedOperatorObjects generate(OperatorValues inputValues) throws Exception {
    UserProjects userProjects = createUserProjectsDirectory();
    OperatorFiles operatorFiles = new OperatorFiles(userProjects.getPath(), inputValues);
    assertThat(
        execCreateOperator(userProjects.getPath(), inputValues), succeedsAndPrints("Completed"));

    Path yamlPath = operatorFiles.getWeblogicOperatorYamlPath();
    Path securityYamlPath = operatorFiles.getWeblogicOperatorSecurityYamlPath();
    return new GeneratedOperatorObjects(
        new ParsedWeblogicOperatorYaml(yamlPath, inputValues),
        new ParsedWeblogicOperatorSecurityYaml(securityYamlPath, inputValues));
  }

  @Override
  public boolean alwaysExpectExternalCredentials() {
    return true;
  }
}
