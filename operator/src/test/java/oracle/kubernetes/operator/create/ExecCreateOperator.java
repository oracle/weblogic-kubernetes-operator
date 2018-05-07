// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

import java.nio.file.Files;
import java.nio.file.Path;

/** Class for running create-weblogic-operator.sh */
public class ExecCreateOperator {

  public static final String CREATE_SCRIPT =
      "src/test/scripts/unit-test-create-weblogic-operator.sh";

  public static ExecResult execCreateOperator(Path userProjectsPath, CreateOperatorInputs inputs)
      throws Exception {
    return execCreateOperator(userProjectsPath, inputs, getInputsYamlPath(userProjectsPath));
  }

  public static ExecResult execCreateOperator(
      Path userProjectsPath, CreateOperatorInputs inputs, Path inputsYamlPath) throws Exception {
    newYaml().dump(inputs, Files.newBufferedWriter(inputsYamlPath));
    return execCreateOperator(
        " -g -o " + userProjectsPath.toString() + " -i " + inputsYamlPath.toString());
  }

  public static ExecResult execCreateOperator(String options) throws Exception {
    return ExecCommand.exec(CREATE_SCRIPT + options);
  }

  public static Path getInputsYamlPath(Path userProjectsPath) {
    return userProjectsPath.resolve("create-weblogic-operator-inputs.yaml");
  }
}
