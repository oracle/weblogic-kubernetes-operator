// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.openapi.models.V1Deployment;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;

public abstract class CreateOperatorGeneratedFilesDebugEnabledTestBase
    extends CreateOperatorGeneratedFilesTestBase {

  protected static void defineOperatorYamlFactory(OperatorYamlFactory factory) throws Exception {
    setup(factory, factory.newOperatorValues().enableDebugging());
  }

  @Override
  public V1Deployment getExpectedWeblogicOperatorDeployment() {
    V1Deployment expected = super.getExpectedWeblogicOperatorDeployment();
    expectRemoteDebug(expected.getSpec().getTemplate().getSpec().getContainers().get(0), "n");
    return expected;
  }
}
