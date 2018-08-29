// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import oracle.kubernetes.operator.utils.DomainYamlFactory;
import org.junit.BeforeClass;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates with APACHE
 * load balacner type are correct when adminServerName, ManagedServerNameBase and clusterName
 * contains invalid DNS charcters. The invalid DNS characters include upper case letters and "_".
 */
public class CreateDomainGeneratedFilesInvalidDNSNameTest
    extends CreateDomainGeneratedFilesOptionalFeaturesDisabledTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineDomainYamlFactory(new ScriptedDomainYamlFactory());
  }

  protected static void defineDomainYamlFactory(DomainYamlFactory factory) throws Exception {
    setup(
        factory,
        factory
            .newDomainValues()
            .adminServerName("admin_Server")
            .managedServerNameBase("Managed_server")
            .clusterName("my_cluster_1"));
  }
}
