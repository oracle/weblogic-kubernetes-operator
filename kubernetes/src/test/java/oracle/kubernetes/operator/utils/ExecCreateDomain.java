// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.YamlUtils.newYaml;

import java.nio.file.Files;
import java.nio.file.Path;

/** Class for running create-weblogic-domain.sh */
public class ExecCreateDomain {

  public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-domain.sh";

  public static ExecResult execCreateDomain(Path userProjectsPath, DomainValues inputs)
      throws Exception {
    return execCreateDomain(userProjectsPath, inputs, getInputsYamlPath(userProjectsPath));
  }

  public static ExecResult execCreateDomain(
      Path userProjectsPath, DomainValues inputs, Path inputsYamlPath) throws Exception {
    newYaml().dump(inputs, Files.newBufferedWriter(inputsYamlPath));
    return execCreateDomain(
        " -g -o " + userProjectsPath.toString() + " -i " + inputsYamlPath.toString());
  }

  public static ExecResult execCreateDomain(String options) throws Exception {
    return ExecCommand.exec(
        PathUtils.getModuleDir(ExecCreateDomain.class) + "/" + CREATE_SCRIPT + options);
  }

  public static Path getInputsYamlPath(Path userProjectsPath) {
    return userProjectsPath.resolve("create-weblogic-domain-inputs.yaml");
  }
}
