// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the external rest and/or debug enabled input parameters
 * are correct when external rest is custom-cert and the remote debug port is enabled.
 */
public class CreateOperatorGeneratedFilesExtRestCustomDebugOnTest extends CreateOperatorGeneratedFilesTest {

  @Override
  protected CreateOperatorInputs createInputs() throws Exception {
    return enableDebugging(setupExternalRestCustomCert(super.createInputs()));
  }

  @Test
  public void generatesCorrectExternalOperatorService() throws Exception {
    weblogicOperatorYaml.assertThatExternalOperatorServiceIsCorrect(inputs, true, true);
  }
}
