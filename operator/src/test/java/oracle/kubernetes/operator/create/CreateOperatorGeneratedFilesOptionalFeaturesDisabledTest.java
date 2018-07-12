// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.V1Service;
import oracle.kubernetes.operator.utils.CreateOperatorInputs;
import org.junit.BeforeClass;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh creates are correct
 * when all optional features are disabled: external rest disabled remote debug port disabled elk
 * disabled no image pull secret
 */
public class CreateOperatorGeneratedFilesOptionalFeaturesDisabledTest
    extends CreateOperatorGeneratedFilesBaseTest {

  @BeforeClass
  public static void setup() throws Exception {
    setup(CreateOperatorInputs.newInputs());
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
    return getExpectedExternalWeblogicOperatorService(false, false);
  }
}
