// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownStep extends Step {
  private final String serverName;

  ServerDownStep(String serverName, Step next) {
    super(next);
    this.serverName = serverName;
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(
        PodHelper.deletePodStep(
            serverName,
            ServiceHelper.deleteServicesStep(
                serverName, new ServerDownFinalizeStep(serverName, getNext()))),
        packet);
  }
}
