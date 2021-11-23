// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

class DomainPresenceInfoUpdateStep extends Step {
  public final String serverName;

  public DomainPresenceInfoUpdateStep(String serverName, Step next) {
    super(next);
    this.serverName = serverName;
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    info.setServerPod(serverName, null);
    return doNext(packet);
  }
}