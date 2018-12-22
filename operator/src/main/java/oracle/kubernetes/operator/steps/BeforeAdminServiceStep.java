// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;

public class BeforeAdminServiceStep extends Step {
  public BeforeAdminServiceStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();

    WlsDomainConfig domainTopology =
        (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    packet.put(ProcessingConstants.SERVER_NAME, domainTopology.getAdminServerName());
    WlsServerConfig server = domainTopology.getServerConfig(domainTopology.getAdminServerName());
    if (server != null) {
      packet.put(ProcessingConstants.PORT, server.getListenPort());
    }
    packet.put(ProcessingConstants.NODE_PORT, dom.getAdminServerSpec().getNodePort());

    return doNext(packet);
  }
}
