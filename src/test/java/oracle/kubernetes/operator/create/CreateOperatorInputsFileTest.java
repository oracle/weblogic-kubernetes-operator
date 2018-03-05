// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

import java.nio.file.Files;

import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that:
 * - the default create-weblogic-operator-inputs.yaml file has the expected contents
 * - create-weblogic-operator.sh without -i uses the default create-weblogic-operator-inputs.yaml file
 * - create-weblogic-operator.sh with -i uses the specified inputs file
 */
public class CreateOperatorInputsFileTest extends CreateOperatorTest {

  @Test
  public void defaultInputsFile_hasCorrectContents() throws Exception {
    CreateOperatorInputs i = readDefaultInputsFile();
    assertThat(i.getServiceAccount(), equalTo("weblogic-operator"));
    assertThat(i.getTargetNamespaces(), equalTo("default"));
    assertThat(i.getImage(), equalTo("container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest"));
    assertThat(i.getImagePullPolicy(), equalTo("IfNotPresent"));
    assertThat(i.getImagePullSecretName(), nullValue());
    assertThat(i.getExternalRestOption(), equalTo("none"));
    assertThat(i.getExternalRestHttpsPort(), equalTo("31001"));
    assertThat(i.getExternalSans(), nullValue());
    assertThat(i.getExternalOperatorCert(), nullValue());
    assertThat(i.getExternalOperatorKey(), nullValue());
    assertThat(i.getRemoteDebugNodePortEnabled(), equalTo("false"));
    assertThat(i.getInternalDebugHttpPort(), equalTo("30999"));
    assertThat(i.getExternalDebugHttpPort(), equalTo("30999"));
    assertThat(i.getJavaLoggingLevel(), equalTo("INFO"));
    assertThat(i.getElkIntegrationEnabled(), equalTo("false"));
  }

  @Test
  public void createOperatorWithoutSpecifyingInputsFile_usesDefaultInputsFileAndSucceedsAndGeneratesExpectedYamlFiles() throws Exception {
    String userProjects = scratch().userProjects().toString();
    assertThat(execCreateOperator(" -g -o " + userProjects), succeedsAndPrints("Completed"));
    assertGeneratedYamlFilesExist(readDefaultInputsFile());
  }

  @Test
  public void createOperatorWithSpecifiedInputsFile_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles() throws Exception {
    // customize the namespace name so that we can tell that it generated the yaml files based on this inputs instead of the default one
    CreateOperatorInputs inputs = readDefaultInputsFile().namespace("weblogic-operator-2");
    assertThat(execCreateOperator(inputs), succeedsAndPrints("Completed"));
    assertGeneratedYamlFilesExist(inputs);
  }

  private void assertGeneratedYamlFilesExist(CreateOperatorInputs inputs) {
    assertThat(Files.isRegularFile(weblogicOperatorYamlPath(inputs)), is(true));
    assertThat(Files.isRegularFile(weblogicOperatorSecurityYamlPath(inputs)), is(true));
  }
}
