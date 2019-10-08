// Copyright (c) 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownIteratorStep extends Step {
  private List<String> serverNames;

  ServerDownIteratorStep(List<String> serverNames, Step next) {
    super(next);
    this.serverNames = serverNames;
  }

  List<String> getServersToStop() {
    return serverNames;
  }

  @Override
  protected String getDetail() {
    return String.join(",", getServersToStop());
  }

  @Override
  public NextAction apply(Packet packet) {
    Collection<StepAndPacket> startDetails =
        getServersToStop().stream()
            .map(
                serverName ->
                    new StepAndPacket(new ServerDownStep(serverName, null), packet.clone()))
            .collect(Collectors.toList());

    if (startDetails.isEmpty()) {
      return doNext(packet);
    } else {
      return doForkJoin(getNext(), packet, startDetails);
    }
  }
}
