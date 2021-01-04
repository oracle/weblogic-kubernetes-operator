// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;

public abstract class CreateOperatorGeneratedFilesDebugEnabledTestBase
    extends CreateOperatorGeneratedFilesTestBase {

  protected static void defineOperatorYamlFactory(OperatorYamlFactory factory) throws Exception {
    setup(factory, factory.newOperatorValues().enableDebugging());
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
  public V1Deployment getExpectedWeblogicOperatorDeployment() {
    V1Deployment expected = super.getExpectedWeblogicOperatorDeployment();
    expectRemoteDebug(expected.getSpec().getTemplate().getSpec().getContainers().get(0), "n");
    return expected;
  }
}
