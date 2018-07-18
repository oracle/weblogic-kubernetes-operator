// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.*;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1Service;
import oracle.kubernetes.operator.utils.CreateOperatorInputs;
import org.junit.BeforeClass;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh creates are correct
 * when the remote debugging port is enabled and all other optional features are disabled.
 */
public class CreateOperatorGeneratedFilesDebugEnabledTest
    extends CreateOperatorGeneratedFilesBaseTest {

  @BeforeClass
  public static void setup() throws Exception {
    setup(CreateOperatorInputs.newInputs().enableDebugging());
  }

  @Override
  protected String getExpectedExternalWeblogicOperatorCert() {
    return ""; // no cert
  }

  @Override
  protected String getExpectedExternalWeblogicOperatorKey() {
    return ""; // no key
  }

  @Override
  protected V1Service getExpectedExternalWeblogicOperatorService() {
    return getExpectedExternalWeblogicOperatorService(true, false);
  }

  @Override
  public ExtensionsV1beta1Deployment getExpectedWeblogicOperatorDeployment() {
    ExtensionsV1beta1Deployment expected = super.getExpectedWeblogicOperatorDeployment();
    V1Container operatorContainer =
        expected.getSpec().getTemplate().getSpec().getContainers().get(0);
    operatorContainer.addEnvItem(
        newEnvVar().name("REMOTE_DEBUG_PORT").value(getInputs().getInternalDebugHttpPort()));
    return expected;
  }
}
