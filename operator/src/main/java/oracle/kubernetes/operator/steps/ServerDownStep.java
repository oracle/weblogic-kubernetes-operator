// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
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
  public @Nonnull Result apply(Packet packet) {
    DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
    V1Pod oldPod = info.getServerPod(serverName);

    Step next;
    if (isPreserveServices) {
      next = getNext();
    } else {
      next = ServiceHelper.deleteServicesStep(serverName, getNext());
    }

    if (oldPod != null) {
      return doNext(createShutdownManagedServerStep(oldPod, next), packet);
    }

    return doNext(packet);
  }

  @Nonnull
  private Step createShutdownManagedServerStep(V1Pod oldPod, Step next) {
    return ShutdownManagedServerStep
        .createShutdownManagedServerStep(PodHelper.deletePodStep(serverName, true, next), serverName, oldPod);
  }
}
