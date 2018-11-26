// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ServerKubernetesObjectsManager {
  /** A map of pod names to ServerKubernetesObjects per namespace */
  private static final ConcurrentMap<String, Map<String, ServerKubernetesObjects>> serverMap =
      new ConcurrentHashMap<>();

  private ServerKubernetesObjectsManager() {}

  static void clear() {
    serverMap.clear();
  }

  static ServerKubernetesObjects getOrCreate(DomainPresenceInfo info, String serverName) {
    return getOrCreate(info, info.getDomain().getSpec().getDomainUID(), serverName);
  }

  public static ServerKubernetesObjects getOrCreate(
      DomainPresenceInfo info, String domainUID, String serverName) {
    ServerKubernetesObjects created = new ServerKubernetesObjects();
    ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, created);
    return (current == null) ? created : current;
  }

  public static ServerKubernetesObjects lookup(String ns, String serverLegalName) {
    Map<String, ServerKubernetesObjects> map = serverMap.get(ns);
    return map != null ? map.get(serverLegalName) : null;
  }

  static void register(
      String ns, String domainUID, String serverName, ServerKubernetesObjects sko) {
    Map<String, ServerKubernetesObjects> map =
        serverMap.computeIfAbsent(ns, k -> new ConcurrentHashMap<>());
    map.put(LegalNames.toServerName(domainUID, serverName), sko);
  }

  static void unregister(String ns, String domainUID, String serverName) {
    Map<String, ServerKubernetesObjects> map = serverMap.get(ns);
    if (map != null) {
      map.remove(LegalNames.toServerName(domainUID, serverName));
    }
  }

  static void unregister(String ns, String serverLegalName) {
    Map<String, ServerKubernetesObjects> map = serverMap.get(ns);
    if (map != null) {
      map.remove(serverLegalName);
    }
  }

  public static Map<String, ServerKubernetesObjects> getServerKubernetesObjects(String ns) {
    Map<String, ServerKubernetesObjects> map = serverMap.get(ns);
    if (map != null) {
      return Collections.unmodifiableMap(map);
    }
    return Collections.emptyMap();
  }
}
