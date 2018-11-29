// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;

public class StorageHelper {
  public static Step insertStorageSteps(Domain dom, Step next) {
    if (dom.getRequiredPersistentVolumeClaim() != null) next = new PersistentVolumeClaimStep(next);
    if (dom.getRequiredPersistentVolume() != null) next = new PersistentVolumeStep(next);
    return next;
  }

  static class PersistentVolumeStep extends Step {
    PersistentVolumeStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      new CallBuilder()
          .createPersistentVolumeAsync(
              info.getDomain().getRequiredPersistentVolume(), new DefaultResponseStep<>(getNext()));
      return doNext(packet);
    }
  }

  static class PersistentVolumeClaimStep extends Step {
    PersistentVolumeClaimStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      new CallBuilder()
          .createPersistentVolumeClaimAsync(
              info.getDomain().getRequiredPersistentVolumeClaim(),
              new DefaultResponseStep<>(getNext()));
      return doNext(packet);
    }
  }
}
