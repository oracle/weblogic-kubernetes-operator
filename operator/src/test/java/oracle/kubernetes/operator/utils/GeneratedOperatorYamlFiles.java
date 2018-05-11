// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.ExecCreateOperator.*;
import static oracle.kubernetes.operator.utils.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.utils.UserProjects.createUserProjectsDirectory;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;

/**
 * Generates the operator yaml files for a set of valid operator input params. Creates and managed
 * the user projects directory that the files are stored in. Parses the generated yaml files into
 * typed java objects.
 */
public class GeneratedOperatorYamlFiles {

  private UserProjects userProjects;
  private OperatorFiles operatorFiles;
  private ParsedWeblogicOperatorYaml weblogicOperatorYaml;
  private ParsedWeblogicOperatorSecurityYaml weblogicOperatorSecurityYaml;

  public static GeneratedOperatorYamlFiles generateOperatorYamlFiles(CreateOperatorInputs inputs)
      throws Exception {
    return new GeneratedOperatorYamlFiles(inputs);
  }

  private GeneratedOperatorYamlFiles(CreateOperatorInputs inputs) throws Exception {
    userProjects = createUserProjectsDirectory();
    boolean ok = false;
    try {
      operatorFiles = new OperatorFiles(userProjects.getPath(), inputs);
      assertThat(
          execCreateOperator(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
      weblogicOperatorYaml =
          new ParsedWeblogicOperatorYaml(operatorFiles.getWeblogicOperatorYamlPath(), inputs);
      weblogicOperatorSecurityYaml =
          new ParsedWeblogicOperatorSecurityYaml(
              operatorFiles.getWeblogicOperatorSecurityYamlPath(), inputs);
      ok = true;
    } finally {
      if (!ok) {
        remove();
      }
    }
  }

  public Path getInputsYamlPath() {
    return ExecCreateOperator.getInputsYamlPath(userProjects.getPath());
  }

  public OperatorFiles getOperatorFiles() {
    return operatorFiles;
  }

  public ParsedWeblogicOperatorYaml getWeblogicOperatorYaml() {
    return weblogicOperatorYaml;
  }

  public ParsedWeblogicOperatorSecurityYaml getWeblogicOperatorSecurityYaml() {
    return weblogicOperatorSecurityYaml;
  }

  public void remove() throws Exception {
    userProjects.remove();
  }
}
