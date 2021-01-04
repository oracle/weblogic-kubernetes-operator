// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownStep extends Step {
  private final String serverName;
  private final boolean isPreserveServices;

  ServerDownStep(String serverName, Step next) {
    this(serverName, false, next);
  }

  ServerDownStep(String serverName, boolean isPreserveServices, Step next) {
    super(next);
    this.serverName = serverName;
    this.isPreserveServices = isPreserveServices;
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    V1Pod oldPod = info.getServerPod(serverName);

    Step next;
    if (isPreserveServices) {
      next = getNext();
    } else {
      next = ServiceHelper.deleteServicesStep(serverName, getNext());
    }

    if (oldPod != null) {
      PodAwaiterStepFactory pw = packet.getSpi(PodAwaiterStepFactory.class);
      next = pw.waitForDelete(oldPod, next);
    }

    return doNext(oldPod != null ? PodHelper.deletePodStep(serverName, next) : next, packet);
  }
}
