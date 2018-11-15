// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.weblogic.domain.v2.Domain;

public class DomainPresenceInfoManager {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** A map of domainUID to DomainPresenceInfo */
  private static final Map<String, DomainPresenceInfo> domains = new ConcurrentHashMap<>();

  private DomainPresenceInfoManager() {}

  public static DomainPresenceInfo getOrCreate(String ns, String domainUID) {
    DomainPresenceInfo createdInfo = new DomainPresenceInfo(ns);
    DomainPresenceInfo existingInfo = domains.putIfAbsent(domainUID, createdInfo);

    // check for duplicate domainUID
    if (existingInfo != null) {
      if (!ns.equals(existingInfo.getNamespace())) {
        Domain existingDomain = existingInfo.getDomain();
        String existingName = existingDomain != null ? existingDomain.getMetadata().getName() : "";
        LOGGER.warning(MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED, domainUID, existingName, "");
      }

      return existingInfo;
    }

    return createdInfo;
  }

  public static DomainPresenceInfo getOrCreate(Domain domain) {
    DomainPresenceInfo createdInfo = new DomainPresenceInfo(domain);
    DomainPresenceInfo existingInfo =
        domains.putIfAbsent(domain.getSpec().getDomainUID(), createdInfo);

    if (existingInfo != null) {
      Domain existingDomain = existingInfo.getDomain();
      String existingName = existingDomain != null ? existingDomain.getMetadata().getName() : "";
      LOGGER.warning(
          MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED,
          domain.getSpec().getDomainUID(),
          existingName,
          domain.getMetadata().getName());

      return existingInfo;
    }

    return createdInfo;
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
