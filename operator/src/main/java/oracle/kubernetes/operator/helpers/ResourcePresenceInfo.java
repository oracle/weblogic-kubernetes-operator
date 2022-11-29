// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.work.PacketComponent;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public abstract class ResourcePresenceInfo implements PacketComponent {

  final String namespace;

  /**
   * Create presence for a domain.
   * @param namespace Namespace
   *
   */
  protected ResourcePresenceInfo(String namespace) {
    this.namespace = namespace;
  }

  /**
   * Gets the namespace.
   *
   * @return Namespace
   */
  public String getNamespace() {
    return namespace;
  }

  public abstract String getResourceName();

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ResourcePresenceInfo{");
    sb.append(", namespace=").append(namespace);
    sb.append(", resourceName=").append(getResourceName());
    sb.append("}");

    return sb.toString();
  }
}
