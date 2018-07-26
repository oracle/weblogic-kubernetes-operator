// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import org.junit.BeforeClass;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates are correct
 * when the admin node port is disabled, the t3 channel is disabled, there is no weblogic domain
 * image pull secret, production mode is disabled and the weblogic domain storage type is HOST_PATH.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesDisabledTest
    extends CreateDomainGeneratedFilesOptionalFeaturesDisabledTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineDomainYamlFactory(new ScriptedDomainYamlFactory());
  }
}
