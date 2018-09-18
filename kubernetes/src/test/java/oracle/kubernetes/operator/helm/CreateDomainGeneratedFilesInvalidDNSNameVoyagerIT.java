// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static oracle.kubernetes.operator.utils.DomainValues.LOAD_BALANCER_VOYAGER;

import oracle.kubernetes.operator.create.CreateDomainGeneratedFilesVoyagerTestBase;
import oracle.kubernetes.operator.utils.DomainYamlFactory;
import org.junit.BeforeClass;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates with
 * VOYAGER load balancer type are correct when adminServerName, managedServerNameBase or/and
 * clusterName in create domain inputs file contain invalid DNS characters. The invalid DNS
 * characters include uppercase letters and underscore "_".
 */
public class CreateDomainGeneratedFilesInvalidDNSNameVoyagerIT
    extends CreateDomainGeneratedFilesVoyagerTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineDomainYamlFactory(new HelmDomainYamlFactory());
  }

  protected static void defineDomainYamlFactory(DomainYamlFactory factory) throws Exception {
    setup(
        factory,
        withFeaturesEnabled(factory.newDomainValues())
            .adminServerName("admin_Server")
            .managedServerNameBase("Managed_server")
            .clusterName("my_cluster_1")
            .loadBalancer(LOAD_BALANCER_VOYAGER));
  }
}
