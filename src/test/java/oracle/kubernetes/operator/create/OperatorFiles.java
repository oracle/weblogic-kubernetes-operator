// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Files;
import java.nio.file.Path;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Manages the input and generated files for an operator
 */
public class OperatorFiles {

  public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-operator.sh";
  private static final String WEBLOGIC_OPERATOR_YAML = "weblogic-operator.yaml";
  private static final String WEBLOGIC_OPERATOR_SECURITY_YAML = "weblogic-operator-security.yaml";

  private Path userProjectsPath;
  private CreateOperatorInputs inputs;

  public OperatorFiles(Path userProjectsPath, CreateOperatorInputs inputs) {
    this.userProjectsPath = userProjectsPath;
    this.inputs = inputs;
  }

  public Path userProjectsPath() { return userProjectsPath; }

  public Path getWeblogicOperatorYamlPath() {
    return getWeblogicOperatorPath().resolve(WEBLOGIC_OPERATOR_YAML);
  }

  public Path getWeblogicOperatorSecurityYamlPath() {
    return getWeblogicOperatorPath().resolve(WEBLOGIC_OPERATOR_SECURITY_YAML);
  }

  public Path getWeblogicOperatorPath() {
    return userProjectsPath().resolve("weblogic-operators").resolve(inputs.getNamespace());
  }

  public ExecResult execCreateOperator() throws Exception {
    Path p = userProjectsPath().resolve("inputs.yaml");
    newYaml().dump(inputs, Files.newBufferedWriter(p));
    return execCreateOperator(" -g -o " + userProjectsPath().toString() + " -i " + p.toString());
  }

  public static ExecResult execCreateOperator(String options) throws Exception {
    return ExecCommand.exec(CREATE_SCRIPT + options);
  }
}
