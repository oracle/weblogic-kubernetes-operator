// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the external rest and/or debug enabled input parameters
 * are correct when external rest is custom-cert and the remote debug port is disabled.
 */
public class CreateOperatorGeneratedFilesExtRestCustomDebugOffTest extends CreateOperatorGeneratedFilesTest {

  @Override
  protected CreateOperatorInputs createInputs() throws Exception {
    return setupExternalRestCustomCert(super.createInputs());
  }

  @Test
  public void generatesCorrectOperatorConfigMap() throws Exception {
    weblogicOperatorYaml.assertThatOperatorConfigMapIsCorrect(inputs, inputs.getExternalOperatorCert());
  }

  @Test
  public void generatesCorrectOperatorSecrets() throws Exception {
    weblogicOperatorYaml.assertThatOperatorSecretsAreCorrect(inputs, inputs.getExternalOperatorKey());
  }

  @Test
  public void generatesCorrectExternalOperatorService() throws Exception {
    weblogicOperatorYaml.assertThatExternalOperatorServiceIsCorrect(inputs, false, true);
  }
}
