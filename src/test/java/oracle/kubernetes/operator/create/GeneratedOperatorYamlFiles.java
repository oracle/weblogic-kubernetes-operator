// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.ExecCreateOperator.execCreateOperator;
import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.create.UserProjects.createUserProjectsDirectory;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Generates the operator yaml files for a set of valid operator input params.
 * Creates and managed the user projects directory that the files are stored in.
 * Parses the generated yaml files into typed java objects.
 */
public class GeneratedOperatorYamlFiles {

  private UserProjects userProjects;
  private OperatorFiles operatorFiles;
  private ParsedWeblogicOperatorYaml weblogicOperatorYaml;
  private ParsedWeblogicOperatorSecurityYaml weblogicOperatorSecurityYaml;

  public static GeneratedOperatorYamlFiles generateOperatorYamlFiles(CreateOperatorInputs inputs) throws Exception {
    return new GeneratedOperatorYamlFiles(inputs);
  }

  private GeneratedOperatorYamlFiles(CreateOperatorInputs inputs) throws Exception {
    userProjects = createUserProjectsDirectory();
    boolean ok = false;
    try {
      operatorFiles = new OperatorFiles(userProjects.getPath(), inputs);
      assertThat(execCreateOperator(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
      weblogicOperatorYaml =
        new ParsedWeblogicOperatorYaml(operatorFiles.getWeblogicOperatorYamlPath(), inputs);
      weblogicOperatorSecurityYaml =
        new ParsedWeblogicOperatorSecurityYaml(operatorFiles.getWeblogicOperatorSecurityYamlPath(), inputs);
      ok = true;
    } finally {
      if (!ok) {
        remove();
      }
    }
  }

  public ParsedWeblogicOperatorYaml getWeblogicOperatorYaml() { return weblogicOperatorYaml; }
  public ParsedWeblogicOperatorSecurityYaml getWeblogicOperatorSecurityYaml() { return weblogicOperatorSecurityYaml; }

  public void remove() throws Exception {
    userProjects.remove();
  }
}
