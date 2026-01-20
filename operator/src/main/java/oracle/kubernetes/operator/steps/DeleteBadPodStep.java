// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Delete individual pod if the pod is detected to be Evicted or Succeeded (not running at all).
 */
public class DeleteBadPodStep extends Step {

  public DeleteBadPodStep(Step next) {
    super(next);
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    DomainPresenceInfo dpi = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
    String serverName = (String) packet.get("serverName");
    if (serverName == null || dpi == null) {
      return doNext(packet);
    }

    return doNext(
            PodHelper.deletePodStep(serverName, true, null),
            packet);
  }


}