// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.nio.file.Files;

import static oracle.kubernetes.operator.create.CreateOperatorInputs.*;
import static oracle.kubernetes.operator.create.ExecCreateOperator.execCreateOperator;
import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
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
    CreateOperatorInputs i = readDefaultInputsFile();
    assertThat(i.getServiceAccount(), equalTo("weblogic-operator"));
    assertThat(i.getTargetNamespaces(), equalTo("default"));
    assertThat(i.getImage(), equalTo("container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest"));
    assertThat(i.getImagePullPolicy(), equalTo("IfNotPresent"));
    assertThat(i.getImagePullSecretName(), equalTo(""));
    assertThat(i.getExternalRestOption(), equalTo(EXTERNAL_REST_OPTION_NONE));
    assertThat(i.getExternalRestHttpsPort(), equalTo("31001"));
    assertThat(i.getExternalSans(), equalTo(""));
    assertThat(i.getExternalOperatorCert(), equalTo(""));
    assertThat(i.getExternalOperatorKey(), equalTo(""));
    assertThat(i.getRemoteDebugNodePortEnabled(), equalTo("false"));
    assertThat(i.getInternalDebugHttpPort(), equalTo("30999"));
    assertThat(i.getExternalDebugHttpPort(), equalTo("30999"));
    assertThat(i.getJavaLoggingLevel(), equalTo(JAVA_LOGGING_LEVEL_INFO));
    assertThat(i.getElkIntegrationEnabled(), equalTo("false"));
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
    assertThat(Files.isRegularFile(operatorFiles.getWeblogicOperatorYamlPath()), is(true));
    assertThat(Files.isRegularFile(operatorFiles.getWeblogicOperatorSecurityYamlPath()), is(true));
  }
}
