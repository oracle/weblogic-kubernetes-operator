// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.YamlUtils.newYaml;

import java.nio.file.Files;
import java.nio.file.Path;

/** Class for running create-weblogic-operator.sh */
public class ExecCreateOperator {

  public static final String CREATE_SCRIPT =
      "src/test/scripts/unit-test-create-weblogic-operator.sh";

  public static ExecResult execCreateOperator(Path userProjectsPath, OperatorValues inputs)
      throws Exception {
    return execCreateOperator(userProjectsPath, inputs, getInputsYamlPath(userProjectsPath));
  }
  // ?? if the dump uses the same names as the values file, can do this
  // ?? but what about leaving existing defaults?
  // ?? maybe read in values file and only write differences?
  // ?? want values as a map... can get from this?
  public static ExecResult execCreateOperator(
      Path userProjectsPath, OperatorValues inputs, Path inputsYamlPath) throws Exception {
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
