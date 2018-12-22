// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownStep extends Step {
  private final String serverName;
  private final ServerKubernetesObjects sko;

  public ServerDownStep(String serverName, ServerKubernetesObjects sko, Step next) {
    super(next);
    this.serverName = serverName;
    this.sko = sko;
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(
        PodHelper.deletePodStep(
            sko,
            ServiceHelper.deleteServicesStep(
                sko, new ServerDownFinalizeStep(serverName, getNext()))),
        packet);
  }
}
