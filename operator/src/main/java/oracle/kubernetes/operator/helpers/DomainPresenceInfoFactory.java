// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import oracle.kubernetes.weblogic.domain.v1.Domain;

public class DomainPresenceInfoFactory {
  private static final DomainPresenceInfoFactory SINGELTON = new DomainPresenceInfoFactory();

  public static DomainPresenceInfoFactory getInstance() {
    return SINGELTON;
  }

  /** A map of domainUID to DomainPresenceInfo */
  private static final ConcurrentMap<String, DomainPresenceInfo> domains =
      new ConcurrentHashMap<>();

  public DomainPresenceInfoFactory() {}

  public DomainPresenceInfo getOrCreate(String ns, String domainUID) {
    DomainPresenceInfo createdInfo =
        new DomainPresenceInfo(ServerKubernetesObjectsFactory.getInstance(), ns);
    DomainPresenceInfo existingInfo = domains.putIfAbsent(domainUID, createdInfo);
    return existingInfo != null ? existingInfo : createdInfo;
  }

  public DomainPresenceInfo getOrCreate(Domain domain) {
    DomainPresenceInfo createdInfo =
        new DomainPresenceInfo(ServerKubernetesObjectsFactory.getInstance(), domain);
    DomainPresenceInfo existingInfo =
        domains.putIfAbsent(domain.getSpec().getDomainUID(), createdInfo);
    return existingInfo != null ? existingInfo : createdInfo;
  }

  public DomainPresenceInfo lookup(String domainUID) {
    return domains.get(domainUID);
  }

  public DomainPresenceInfo remove(String domainUID) {
    return domains.remove(domainUID);
  }

  public Map<String, DomainPresenceInfo> getDomainPresenceInfos() {
    return Collections.unmodifiableMap(domains);
  }
}
