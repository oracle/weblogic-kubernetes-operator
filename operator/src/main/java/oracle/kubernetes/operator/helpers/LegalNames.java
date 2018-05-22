// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;

/** A class to create DNS-1123 legal names for Kubernetes objects. */
public class LegalNames {

  private static final String INGRESS_PATTERN = "%s-%s";
  private static final String SERVER_PATTERN = "%s-%s";
  private static final String CLUSTER_SERVICE_PATTERN = "%s-cluster-%s";
  private static final String NAP_PATTERN = "%s-%s-extchannel-%s";

  static String toIngressName(String domainUID, String clusterName) {
    return toDNS1123LegalName(String.format(INGRESS_PATTERN, domainUID, clusterName));
  }

  public static String toServerServiceName(String domainUID, String serverName) {
    return toServerName(domainUID, serverName);
  }

  public static String toServerName(String domainUID, String serverName) {
    return toDNS1123LegalName(String.format(SERVER_PATTERN, domainUID, serverName));
  }

  static String toPodName(String domainUID, String serverName) {
    return toServerName(domainUID, serverName);
  }

  static String toClusterServiceName(String domainUID, String clusterName) {
    return toDNS1123LegalName(String.format(CLUSTER_SERVICE_PATTERN, domainUID, clusterName));
  }

  static String toNAPName(String domainUID, String serverName, NetworkAccessPoint nap) {
    return toDNS1123LegalName(String.format(NAP_PATTERN, domainUID, serverName, nap.getName()));
  }

  /**
   * Converts value to nearest DNS-1123 legal name, which can be used as a Kubernetes identifier
   *
   * @param value Input value
   * @return nearest DNS-1123 legal name
   */
  private static String toDNS1123LegalName(String value) {
    return value.toLowerCase().replace('_', '-');
  }
}
