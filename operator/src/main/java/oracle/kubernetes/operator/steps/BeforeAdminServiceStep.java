// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class BeforeAdminServiceStep extends Step {
  public BeforeAdminServiceStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    WlsDomainConfig domainTopology =
        (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    String adminServerName = domainTopology.getAdminServerName();
    packet.put(ProcessingConstants.SERVER_NAME, adminServerName);
    packet.put(ProcessingConstants.SERVER_SCAN, domainTopology.getServerConfig(adminServerName));

    return doNext(packet);
  }
}
