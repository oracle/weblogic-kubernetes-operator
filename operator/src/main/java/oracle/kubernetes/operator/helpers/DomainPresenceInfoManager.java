// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.weblogic.domain.v2.Domain;

public class DomainPresenceInfoManager {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** A map of domainUID to DomainPresenceInfo per namespace */
  private static final ConcurrentMap<String, Map<String, DomainPresenceInfo>> domains =
      new ConcurrentHashMap<>();

  private DomainPresenceInfoManager() {}

  public static DomainPresenceInfo getOrCreate(String ns, String domainUID) {
    DomainPresenceInfo createdInfo = new DomainPresenceInfo(ns, domainUID);
    Map<String, DomainPresenceInfo> map =
        domains.computeIfAbsent(ns, (key) -> new ConcurrentHashMap<>());
    DomainPresenceInfo existingInfo = map.putIfAbsent(domainUID, createdInfo);

    return existingInfo != null ? existingInfo : createdInfo;
  }

  public static DomainPresenceInfo getOrCreate(Domain domain) {
    String domainUID = domain.getSpec().getDomainUID();
    DomainPresenceInfo createdInfo = new DomainPresenceInfo(domain);
    Map<String, DomainPresenceInfo> map =
        domains.computeIfAbsent(createdInfo.getNamespace(), (key) -> new ConcurrentHashMap<>());
    DomainPresenceInfo existingInfo = map.putIfAbsent(domainUID, createdInfo);

    domains.forEach(
        (key, value) -> {
          if (!createdInfo.getNamespace().equals(key)) {
            if (value != null) {
              DomainPresenceInfo domainWithSameDomainUID = value.get(domainUID);
              if (domainWithSameDomainUID != null) {
                Domain d = domainWithSameDomainUID.getDomain();
                if (d != null) {
                  LOGGER.warning(
                      MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED,
                      domainUID,
                      d.getMetadata().getName(),
                      domain.getMetadata().getName());
                }
              }
            }
          }
        });

    return existingInfo != null ? existingInfo : createdInfo;
  }

  public static DomainPresenceInfo lookup(String ns, String domainUID) {
    Map<String, DomainPresenceInfo> map = domains.get(ns);
    return map != null ? map.get(domainUID) : null;
  }

  public static DomainPresenceInfo remove(String ns, String domainUID) {
    Map<String, DomainPresenceInfo> map = domains.get(ns);
    return map != null ? map.remove(domainUID) : null;
  }

  public static Map<String, DomainPresenceInfo> getDomainPresenceInfos(String ns) {
    Map<String, DomainPresenceInfo> map = domains.get(ns);
    return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
  }
}
