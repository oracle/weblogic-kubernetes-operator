// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Files;
import java.nio.file.Path;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Class for running create-weblogic-domain.sh
 */
public class ExecCreateDomain {

  public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-domain.sh";

  public static ExecResult execCreateDomain(Path userProjectsPath, CreateDomainInputs inputs) throws Exception {
    return execCreateDomain(userProjectsPath, inputs, getInputsYamlPath(userProjectsPath));
  }

  public static ExecResult execCreateDomain(Path userProjectsPath, CreateDomainInputs inputs, Path inputsYamlPath) throws Exception {
    newYaml().dump(inputs, Files.newBufferedWriter(inputsYamlPath));
    return execCreateDomain(" -g -o " + userProjectsPath.toString() + " -i " + inputsYamlPath.toString());
  }

  public static ExecResult execCreateDomain(String options) throws Exception {
    return ExecCommand.exec(CREATE_SCRIPT + options);
  }

  public static Path getInputsYamlPath(Path userProjectsPath) {
    return userProjectsPath.resolve("create-weblogic-domain-inputs.yaml");
  }
}
