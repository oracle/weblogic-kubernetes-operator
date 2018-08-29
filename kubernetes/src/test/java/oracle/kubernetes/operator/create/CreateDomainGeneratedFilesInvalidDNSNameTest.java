// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import oracle.kubernetes.operator.utils.DomainYamlFactory;
import org.junit.BeforeClass;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates with APACHE
 * and TRAEFIK load balancer types are correct when adminServerName, managedServerNameBase or/and
 * clusterName in create domain inputs file contain invalid DNS characters. The invalid DNS
 * characters include uppercase letters and underscore "_".
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
