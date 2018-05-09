// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.concurrent.ConcurrentMap;

public class ServerKubernetesObjectsFactory {
  private final ConcurrentMap<String, ServerKubernetesObjects> serverMap;

  public ServerKubernetesObjectsFactory(ConcurrentMap<String, ServerKubernetesObjects> serverMap) {
    this.serverMap = serverMap;
  }

  public ServerKubernetesObjects getOrCreate(DomainPresenceInfo info, String serverName) {
    return getOrCreate(info, info.getDomain().getSpec().getDomainUID(), serverName);
  }

  public ServerKubernetesObjects getOrCreate(
      DomainPresenceInfo info, String domainUID, String serverName) {
    ServerKubernetesObjects created = new ServerKubernetesObjects();
    ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, created);
    if (current == null) {
      String podName = CallBuilder.toDNS1123LegalName(domainUID + "-" + serverName);
      serverMap.put(podName, created);
      return created;
    }
    return current;
  }

  public ServerKubernetesObjects lookup(String podName) {
    return serverMap.get(podName);
  }
}
