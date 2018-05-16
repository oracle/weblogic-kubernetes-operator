// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerKubernetesObjectsFactory {
  private static final ServerKubernetesObjectsFactory SINGLETON =
      new ServerKubernetesObjectsFactory();

  public static ServerKubernetesObjectsFactory getInstance() {
    return SINGLETON;
  }

  /** A map of pod names to ServerKubernetesObjects */
  private static final Map<String, ServerKubernetesObjects> serverMap = new ConcurrentHashMap<>();

  ServerKubernetesObjectsFactory() {}

  ServerKubernetesObjects getOrCreate(DomainPresenceInfo info, String serverName) {
    return getOrCreate(info, info.getDomain().getSpec().getDomainUID(), serverName);
  }

  public ServerKubernetesObjects getOrCreate(
      DomainPresenceInfo info, String domainUID, String serverName) {
    ServerKubernetesObjects created = new ServerKubernetesObjects();
    ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, created);
    return (current == null) ? created : current;
  }

  public ServerKubernetesObjects lookup(String serverLegalName) {
    return serverMap.get(serverLegalName);
  }

  void register(String domainUID, String serverName, ServerKubernetesObjects sko) {
    serverMap.put(LegalNames.toServerName(domainUID, serverName), sko);
  }

  void unregister(String domainUID, String serverName) {
    serverMap.remove(LegalNames.toServerName(domainUID, serverName));
  }

  Map<String, ServerKubernetesObjects> getServerKubernetesObjects() {
    return Collections.unmodifiableMap(serverMap);
  }
}
