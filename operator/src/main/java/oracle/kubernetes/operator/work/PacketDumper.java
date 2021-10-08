// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

/**
 * An interface for a class that can write selected contents of a packet to a string builder for diagnostics.
 */
public interface PacketDumper {
  void dump(StringBuilder sb, Packet packet);
}
