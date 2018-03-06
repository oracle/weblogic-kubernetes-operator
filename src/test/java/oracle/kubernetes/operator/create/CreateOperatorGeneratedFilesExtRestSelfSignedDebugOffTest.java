// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that  are affected by the external rest and/or debug enabled input parameters
 * are correct when external rest is self-signed-cert and the remote debug port is disabled.
 */
public class CreateOperatorGeneratedFilesExtRestSelfSignedDebugOffTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateOperatorInputs.newInputs().setupExternalRestSelfSignedCert();
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrectOperatorConfigMap() throws Exception {
    weblogicOperatorYaml().assertThatOperatorConfigMapIsCorrect(inputs, inputs.externalOperatorSelfSignedCertPem());
  }

  @Test
  public void generatesCorrectOperatorSecrets() throws Exception {
    weblogicOperatorYaml().assertThatOperatorSecretsAreCorrect(inputs, inputs.externalOperatorSelfSignedKeyPem());
  }

  @Test
  public void generatesCorrectExternalOperatorService() throws Exception {
    weblogicOperatorYaml().assertThatExternalOperatorServiceIsCorrect(inputs, false, true);
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}
