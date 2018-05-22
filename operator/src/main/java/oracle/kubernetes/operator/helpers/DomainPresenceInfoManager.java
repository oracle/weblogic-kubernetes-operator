// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import oracle.kubernetes.weblogic.domain.v1.Domain;

public class DomainPresenceInfoManager {
  /** A map of domainUID to DomainPresenceInfo */
  private static final Map<String, DomainPresenceInfo> domains = new ConcurrentHashMap<>();

  private DomainPresenceInfoManager() {}

  public static DomainPresenceInfo getOrCreate(String ns, String domainUID) {
    DomainPresenceInfo createdInfo = new DomainPresenceInfo(ns);
    DomainPresenceInfo existingInfo = domains.putIfAbsent(domainUID, createdInfo);
    return existingInfo != null ? existingInfo : createdInfo;
  }

  public static DomainPresenceInfo getOrCreate(Domain domain) {
    DomainPresenceInfo oldInfo = lookup(domain.getSpec().getDomainUID());
    DomainPresenceInfo oldInfo2 = domains.get(domain.getSpec().getDomainUID());
    DomainPresenceInfo createdInfo = new DomainPresenceInfo(domain);
    DomainPresenceInfo existingInfo =
        domains.putIfAbsent(domain.getSpec().getDomainUID(), createdInfo);
    if (existingInfo == null && oldInfo != null) System.out.println("This can't be");
    return existingInfo != null ? existingInfo : createdInfo;
  }

  public static DomainPresenceInfo lookup(String domainUID) {
    return domains.get(domainUID);
  }

  public static DomainPresenceInfo remove(String domainUID) {
    return domains.remove(domainUID);
  }

  public static Map<String, DomainPresenceInfo> getDomainPresenceInfos() {
    return Collections.unmodifiableMap(domains);
  }
}
