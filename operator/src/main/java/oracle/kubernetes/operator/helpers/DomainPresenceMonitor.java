// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.weblogic.domain.v2.Domain;

class DomainPresenceMonitor {

  private static String serverNameAsKey;
  private static ServerKubernetesObjects result;
  private static Domain domain;
  private static String serverName;

  static void clear() {
    serverNameAsKey = null;
    domain = null;
    serverName = null;
  }

  static String getExplanation() {
    StringBuilder sb = new StringBuilder();
    if (serverNameAsKey != null)
      format(sb, "putIfAbsent called with %s and returned %s", serverNameAsKey, result);
    if (domain != null) format(sb, "Domain was not null");
    if (serverName != null) format(sb, "registered with key %s", serverName);

    return sb.toString();
  }

  private static void format(StringBuilder sb, String pattern, Object... values) {
    sb.append(String.format(pattern, values)).append("\n");
  }

  static void putIfAbsent(String key, ServerKubernetesObjects result) {
    DomainPresenceMonitor.serverNameAsKey = key;
    DomainPresenceMonitor.result = result;
  }

  static void putIfAbsentDomain(Domain domain) {
    DomainPresenceMonitor.domain = domain;
  }

  static void registered(String registeredName) {
    DomainPresenceMonitor.serverName = registeredName;
  }
}
