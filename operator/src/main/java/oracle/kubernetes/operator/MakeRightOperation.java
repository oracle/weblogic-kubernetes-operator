// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.ResourcePresenceInfo;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.PacketComponent;
import oracle.kubernetes.operator.work.Step;

/**
 * Defines the operation to bring a running domain into compliance with its domain resource and introspection result,
 * or to log a ClusterCreated/Changed/Deleted event.
 */
public interface MakeRightOperation<T extends ResourcePresenceInfo> extends PacketComponent {

  void execute();

  @Nonnull
  Packet createPacket();

  Step createSteps();

  boolean isWillInterrupt();

  T getPresenceInfo();

  boolean hasEventData();

  /**
   * Get the event data associated with this make-right operation.
   *
   * @return the event data.
   */
  EventHelper.EventData getEventData();

  boolean isExplicitRecheck();
}
