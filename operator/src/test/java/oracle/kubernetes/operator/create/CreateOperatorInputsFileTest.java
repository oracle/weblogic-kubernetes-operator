// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.VersionConstants.*;
import static oracle.kubernetes.operator.utils.CreateOperatorInputs.*;
import static oracle.kubernetes.operator.utils.ExecCreateOperator.execCreateOperator;
import static oracle.kubernetes.operator.utils.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import oracle.kubernetes.operator.utils.CreateOperatorInputs;
import oracle.kubernetes.operator.utils.OperatorFiles;
import oracle.kubernetes.operator.utils.UserProjects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that: - the default create-weblogic-operator-inputs.yaml file has the expected contents -
 * create-weblogic-operator.sh without -i uses the default create-weblogic-operator-inputs.yaml file
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
        yamlEqualTo(
            (new CreateOperatorInputs())
                .version(CREATE_WEBLOGIC_OPERATOR_INPUTS_V1)
                .elkIntegrationEnabled("false")
                .externalDebugHttpPort("30999")
                .externalOperatorCert("")
                .externalOperatorKey("")
                .externalRestOption(EXTERNAL_REST_OPTION_NONE)
                .externalRestHttpsPort("31001")
                .externalSans("")
                .remoteDebugNodePortEnabled("false")
                .weblogicOperatorImage("weblogic-kubernetes-operator:1.0")
                .weblogicOperatorImagePullPolicy("IfNotPresent")
                .weblogicOperatorImagePullSecretName("")
                .internalDebugHttpPort("30999")
                .javaLoggingLevel(JAVA_LOGGING_LEVEL_INFO)
                .namespace("weblogic-operator")
                .remoteDebugNodePortEnabled("false")
                .serviceAccount("weblogic-operator")
                .targetNamespaces("default")));
  }

  @Test
  public void
      createOperatorWithoutSpecifyingInputsFile_usesDefaultInputsFileAndSucceedsAndGeneratesExpectedYamlFiles()
          throws Exception {
    assertThat(
        execCreateOperator(" -g -o " + userProjects.getPath().toString()),
        succeedsAndPrints("Completed"));
    assertThatOnlyTheExpectedGeneratedYamlFilesExist(readDefaultInputsFile());
  }

  @Test
  public void
      createOperatorWithSpecifiedInputsFile_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles()
          throws Exception {
    // customize the namespace name so that we can tell that it generated the yaml files based on
    // this inputs instead of the default one
    CreateOperatorInputs inputs = readDefaultInputsFile().namespace("weblogic-operator-2");
    assertThat(execCreateOperator(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
    assertThatOnlyTheExpectedGeneratedYamlFilesExist(inputs);
  }

  @Test
  public void
      createDomainFromPreCreatedInputsFileInPreCreatedOutputDirectory_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles()
          throws Exception {
    // customize the namespace name so that we can tell that it generated the yaml files based on
    // this inputs instead of the default one
    CreateOperatorInputs inputs = readDefaultInputsFile().namespace("weblogic-operator-2");
    // pre-create the output directory and the inputs file in the output directory, then
    // use that inputs file to create the operator
    OperatorFiles operatorFiles = new OperatorFiles(userProjects.getPath(), inputs);
    Files.createDirectories(operatorFiles.getWeblogicOperatorPath());
    assertThat(
        execCreateOperator(
            userProjects.getPath(),
            inputs,
            operatorFiles.getCreateWeblogicOperatorInputsYamlPath()),
        succeedsAndPrints("Completed"));
    assertThatOnlyTheExpectedGeneratedYamlFilesExist(inputs);
  }

  private void assertThatOnlyTheExpectedGeneratedYamlFilesExist(CreateOperatorInputs inputs)
      throws Exception {
    // Make sure the generated directory has the correct list of files
    OperatorFiles operatorFiles = new OperatorFiles(userProjects.getPath(), inputs);
    List<Path> expectedFiles = operatorFiles.getExpectedContents(true); // include the directory too
    List<Path> actualFiles = userProjects.getContents(operatorFiles.getWeblogicOperatorPath());
    assertThat(
        actualFiles, containsInAnyOrder(expectedFiles.toArray(new Path[expectedFiles.size()])));

    // Make sure that the yaml files are regular files
    for (Path path : operatorFiles.getExpectedContents(false)) { // don't include the directory too
      assertThat("Expect that " + path + " is a regular file", Files.isRegularFile(path), is(true));
    }
  }
}
