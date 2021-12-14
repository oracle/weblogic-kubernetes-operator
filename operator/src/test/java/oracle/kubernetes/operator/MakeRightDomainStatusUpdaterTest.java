// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;

public class MakeRightDomainStatusUpdaterTest extends DomainStatusUpdateTestBase {

  @Override
  void processTopology(WlsDomainConfig domainConfig) {
    addTopologyToPacket(domainConfig);
  }
}
