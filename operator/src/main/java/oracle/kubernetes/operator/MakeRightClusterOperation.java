// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.helpers.ClusterPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;

/**
 * Defines the operation to log a ClusterCreated/Changed/Deleted event.
 */
public interface MakeRightClusterOperation extends MakeRightOperation<ClusterPresenceInfo> {

  /**
   * Set the event data that is associated with this operation.
   *
   * @param eventData event data
   * @return the updated factory
   */
  MakeRightClusterOperation withEventData(EventHelper.ClusterResourceEventData eventData);

  /**
   * Modifies the factory to indicate that it should interrupt any current make-right thread.
   *
   * @return the updated factory
   */
  MakeRightClusterOperation interrupt();

  MakeRightClusterOperation withExplicitRecheck();
}
