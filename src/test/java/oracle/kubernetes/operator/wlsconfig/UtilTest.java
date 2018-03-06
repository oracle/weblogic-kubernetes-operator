// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.Test;

import static org.junit.Assert.*;

public class UtilTest {

  @Test
  public void verifyMachineNamePrefixFromGetMachineNamePrefix() throws Exception {
    DomainSpec domainSpec = new DomainSpec().domainUID("domain1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertEquals("domain1-cluster1-machine", Util.getMachineNamePrefix(domainSpec, wlsClusterConfig));
  }

  @Test
  public void verifyNoNPEwithNullArgumentsToGetMachineNamePrefix() throws Exception {
    DomainSpec domainSpec = new DomainSpec().domainUID("domain1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");

    assertNull(Util.getMachineNamePrefix(null, wlsClusterConfig));
    assertNull(Util.getMachineNamePrefix(domainSpec, null));
    assertNull(Util.getMachineNamePrefix(null, null));
  }


}