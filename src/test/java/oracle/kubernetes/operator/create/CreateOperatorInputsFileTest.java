// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.nio.file.Files;

import static oracle.kubernetes.operator.create.CreateOperatorInputs.*;
import static oracle.kubernetes.operator.create.ExecCreateOperator.*;
import static oracle.kubernetes.operator.create.ExecResultMatcher.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that:
 * - the default create-weblogic-operator-inputs.yaml file has the expected contents
 * - create-weblogic-operator.sh without -i uses the default create-weblogic-operator-inputs.yaml file
 * - create-weblogic-operator.sh with -i uses the specified inputs file
 */
public class CreateOperatorInputsFileTest {

  private UserProjects userProjects;

  @Before
  public void setup() throws Exception {
    userProjects = UserProjects.createUserProjectsDirectory();
  }

  @After
  public void tearDown() throws Exception {
    if (userProjects != null) {
      userProjects.remove();
    }
  }

  @Test
  public void defaultInputsFile_hasCorrectContents() throws Exception {
    assertThat(
      readDefaultInputsFile(),
      yamlEqualTo((new CreateOperatorInputs())
        .elkIntegrationEnabled("false")
        .externalDebugHttpPort("30999")
        .externalOperatorCert("")
        .externalOperatorKey("")
        .externalRestOption(EXTERNAL_REST_OPTION_NONE)
        .externalRestHttpsPort("31001")
        .externalSans("")
        .remoteDebugNodePortEnabled("false")
        .image("container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest")
        .imagePullPolicy("IfNotPresent")
        .imagePullSecretName("")
        .internalDebugHttpPort("30999")
        .javaLoggingLevel(JAVA_LOGGING_LEVEL_INFO)
        .namespace("weblogic-operator")
        .remoteDebugNodePortEnabled("false")
        .serviceAccount("weblogic-operator")
        .targetNamespaces("default")));
  }

  @Test
  public void createOperatorWithoutSpecifyingInputsFile_usesDefaultInputsFileAndSucceedsAndGeneratesExpectedYamlFiles() throws Exception {
    assertThat(execCreateOperator(" -g -o " + userProjects.getPath().toString()), succeedsAndPrints("Completed"));
    assertGeneratedYamlFilesExist(readDefaultInputsFile());
  }

  @Test
  public void createOperatorWithSpecifiedInputsFile_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles() throws Exception {
    // customize the namespace name so that we can tell that it generated the yaml files based on this inputs instead of the default one
    CreateOperatorInputs inputs = readDefaultInputsFile().namespace("weblogic-operator-2");
    assertThat(execCreateOperator(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
    assertGeneratedYamlFilesExist(inputs);
  }

  private void assertGeneratedYamlFilesExist(CreateOperatorInputs inputs) {
    OperatorFiles operatorFiles = new OperatorFiles(userProjects.getPath(), inputs);
    assertThat(Files.isRegularFile(operatorFiles.getCreateWeblogicOperatorInputsYamlPath()), is(true));
    assertThat(Files.isRegularFile(operatorFiles.getWeblogicOperatorYamlPath()), is(true));
    assertThat(Files.isRegularFile(operatorFiles.getWeblogicOperatorSecurityYamlPath()), is(true));
    // TBD - assert that the generated per-operator directory doesn't contain any extra files?
    // TBD - assert that the copy of the inputs in generated per-operator directory matches the origin one
  }
}
