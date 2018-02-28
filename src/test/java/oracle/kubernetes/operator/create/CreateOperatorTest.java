// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.io.*;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Base test class for testing create-weblogic-operator.sh
 */
public class CreateOperatorTest extends CreateTest {

  protected static final String CREATE_SCRIPT = "kubernetes/create-weblogic-operator.sh";
  protected static final String DEFAULT_INPUTS = "kubernetes/create-weblogic-operator-inputs.yaml";
  protected static final String WEBLOGIC_OPERATOR_YAML = "weblogic-operator.yaml";
  protected static final String WEBLOGIC_OPERATOR_SECURITY_YAML = "weblogic-operator-security.yaml";

/*
  private void assertGeneratedYamls(CreateOperatorInputs inputs) {
    assertGeneratedWeblogicOperatorYaml(inputs);
    assertGeneratedWeblogicOperatorSecurityYaml(inputs);
  }

  private void assertGeneratedWeblogicOperatorYaml(CreateOperatorInputs inputs) {
    assertThat(Files.isRegularFile(weblogicOperatorYamlPath(inputs)), is(true));
    // TBD - check the yaml file contents - this is a big job
  }

  private void assertGeneratedWeblogicOperatorSecurityYaml(CreateOperatorInputs inputs) {
    assertThat(Files.isRegularFile(weblogicOperatorSecurityYamlPath(inputs)), is(true));
    // TBD - check the yaml file contents - this is a big job
  }
*/

  protected CreateOperatorInputs readDefaultInputsFile() throws IOException {
    Reader r = Files.newBufferedReader(defaultInputsPath(), Charset.forName("UTF-8"));
    return (CreateOperatorInputs)newYaml().loadAs(r, CreateOperatorInputs.class);
  }

  protected Path defaultInputsPath() {
    return FileSystems.getDefault().getPath(DEFAULT_INPUTS);
  }

  protected Path weblogicOperatorYamlPath(CreateOperatorInputs inputs) {
    return weblogicOperatorPath(inputs).resolve(WEBLOGIC_OPERATOR_YAML);
  }

  protected Path weblogicOperatorSecurityYamlPath(CreateOperatorInputs inputs) {
    return weblogicOperatorPath(inputs).resolve(WEBLOGIC_OPERATOR_SECURITY_YAML);
  }

  protected Path weblogicOperatorPath(CreateOperatorInputs inputs) {
    return scratch().userProjects().resolve("weblogic-operators").resolve(inputs.namespace);
  }

  protected ExecResult execCreateOperator(CreateOperatorInputs inputs) throws Exception {
    Path p = scratch().path().resolve("inputs.yaml");
    newYaml().dump(inputs, Files.newBufferedWriter(p));
    return execCreateOperator(" -g -o " + scratch().userProjects().toString() + " -i " + p.toString());
  }

  protected ExecResult execCreateOperator(String options) throws Exception {
    return exec(CREATE_SCRIPT + options);
  }
}
