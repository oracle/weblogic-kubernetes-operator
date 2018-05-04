// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the input and generated files for an operator
 */
public class OperatorFiles {

  public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-operator.sh";
  private static final String CREATE_WEBLOGIC_OPERATOR_INPUTS_YAML = "create-weblogic-operator-inputs.yaml";
  private static final String WEBLOGIC_OPERATOR_YAML = "weblogic-operator.yaml";
  private static final String WEBLOGIC_OPERATOR_SECURITY_YAML = "weblogic-operator-security.yaml";

  private Path userProjectsPath;
  private CreateOperatorInputs inputs;

  public OperatorFiles(Path userProjectsPath, CreateOperatorInputs inputs) {
    this.userProjectsPath = userProjectsPath;
    this.inputs = inputs;
  }

  public Path userProjectsPath() { return userProjectsPath; }

  public Path getCreateWeblogicOperatorInputsYamlPath() {
    return getWeblogicOperatorPath().resolve(CREATE_WEBLOGIC_OPERATOR_INPUTS_YAML);
  }

  public Path getWeblogicOperatorYamlPath() {
    return getWeblogicOperatorPath().resolve(WEBLOGIC_OPERATOR_YAML);
  }

  public Path getWeblogicOperatorSecurityYamlPath() {
    return getWeblogicOperatorPath().resolve(WEBLOGIC_OPERATOR_SECURITY_YAML);
  }

  public Path getWeblogicOperatorPath() {
    return userProjectsPath().resolve("weblogic-operators").resolve(inputs.getNamespace());
  }

  public List<Path> getExpectedContents(boolean includeDirectory) {
    List<Path> rtn = new ArrayList<>();
    rtn.add(getCreateWeblogicOperatorInputsYamlPath());
    rtn.add(getWeblogicOperatorYamlPath());
    rtn.add(getWeblogicOperatorSecurityYamlPath());
    if (includeDirectory) {
      rtn.add(getWeblogicOperatorPath());
    }
    return rtn;
  }
}
