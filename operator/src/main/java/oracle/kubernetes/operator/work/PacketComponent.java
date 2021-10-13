// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

public interface PacketComponent {

  /**
   * Adds this instance to the specified packet in a way that it can be identified and retrieved.
   * @param packet the packet to modify
   */
  void addToPacket(Packet packet);
}
