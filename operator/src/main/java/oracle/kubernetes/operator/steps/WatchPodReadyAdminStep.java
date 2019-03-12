// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1Pod;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class WatchPodReadyAdminStep extends Step {

  private final PodAwaiterStepFactory podAwaiterStepFactory;

  public WatchPodReadyAdminStep(PodAwaiterStepFactory podAwaiterStepFactory, Step next) {
    super(next);
    this.podAwaiterStepFactory = podAwaiterStepFactory;
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
    WlsDomainConfig domainTopology =
        (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    V1Pod adminPod = info.getServers().get(domainTopology.getAdminServerName()).getPod().get();

    PodAwaiterStepFactory pw = podAwaiterStepFactory;
    packet
        .getComponents()
        .put(
            ProcessingConstants.PODWATCHER_COMPONENT_NAME,
            Component.createFor(PodAwaiterStepFactory.class, pw));

    return doNext(pw.waitForReady(adminPod, getNext()), packet);
  }
}
