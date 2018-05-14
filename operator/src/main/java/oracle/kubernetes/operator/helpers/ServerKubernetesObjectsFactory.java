// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.concurrent.ConcurrentMap;

public class ServerKubernetesObjectsFactory {
  /** A map of server names to server kubernetes objects. */
  private final ConcurrentMap<String, ServerKubernetesObjects> serverMap;

  public ServerKubernetesObjectsFactory(ConcurrentMap<String, ServerKubernetesObjects> serverMap) {
    this.serverMap = serverMap;
  }

  ServerKubernetesObjects getOrCreate(DomainPresenceInfo info, String serverName) {
    return getOrCreate(info, info.getDomain().getSpec().getDomainUID(), serverName);
  }

  public ServerKubernetesObjects getOrCreate(
      DomainPresenceInfo info, String domainUID, String serverName) {
    ServerKubernetesObjects created = new ServerKubernetesObjects();
    ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, created);
    if (current == null) {
      serverMap.put(LegalNames.toServerName(domainUID, serverName), created);
      return created;
    }
    return current;
  }

  public ServerKubernetesObjects lookup(String serverLegalName) {
    return serverMap.get(serverLegalName);
  }
}
