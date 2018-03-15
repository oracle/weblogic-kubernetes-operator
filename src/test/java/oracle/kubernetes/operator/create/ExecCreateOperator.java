// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Files;
import java.nio.file.Path;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Class for running create-weblogic-operator.sh
 */
public class ExecCreateOperator {

  public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-operator.sh";

  public static ExecResult execCreateOperator(Path userProjectsPath, CreateOperatorInputs inputs) throws Exception {
    Path p = getInputsYamlPath(userProjectsPath);
    newYaml().dump(inputs, Files.newBufferedWriter(p));
    return execCreateOperator(" -g -o " + userProjectsPath.toString() + " -i " + p.toString());
  }

  public static ExecResult execCreateOperator(String options) throws Exception {
    return ExecCommand.exec(CREATE_SCRIPT + options);
  }

  public static Path getInputsYamlPath(Path userProjectsPath) {
    return userProjectsPath.resolve("create-weblogic-operator-inputs.yaml");
  }
}
