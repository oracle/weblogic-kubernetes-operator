// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the external rest and/or debug enabled input parameters
 * are correct when external rest is custom-cert and the remote debug port is disabled.
 */
public class CreateOperatorGeneratedFilesExtRestCustomDebugOffTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateOperatorInputs.newInputs().setupExternalRestCustomCert();
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorConfigMap() throws Exception {
    assertThat(
      weblogicOperatorYaml().getOperatorConfigMap(),
      equalTo(weblogicOperatorYaml().getExpectedOperatorConfigMap(inputs.externalOperatorCustomCertPem())));
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorSecrets() throws Exception {
    ParsedWeblogicOperatorYaml.assertThat_secretsAreEqual(
      weblogicOperatorYaml().getOperatorSecrets(),
      weblogicOperatorYaml().getExpectedOperatorSecrets(inputs.externalOperatorCustomKeyPem()));
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_externalOperatorService() throws Exception {
    assertThat(
      weblogicOperatorYaml().getExternalOperatorService(),
      equalTo(weblogicOperatorYaml().getExpectedExternalOperatorService(false, true)));
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}
