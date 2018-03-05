// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the elk related input parameters are correct when
 * elk is enabled.
 */
public class CreateOperatorGeneratedFilesELKEnabledTest extends CreateOperatorGeneratedFilesTest {

  @Override
  protected CreateOperatorInputs createInputs() throws Exception {
    return super.createInputs().elkIntegrationEnabled("true");
  }

  @Test
  public void generatesCorrectDeploymentElkArtifacts() throws Exception {
    // TBD
  }
}
