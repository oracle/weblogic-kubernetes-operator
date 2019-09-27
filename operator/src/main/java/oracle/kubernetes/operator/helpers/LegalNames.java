// Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/** A class to create DNS-1123 legal names for Kubernetes objects. */
public class LegalNames {

  private static final String SERVER_PATTERN = "%s-%s";
  private static final String CLUSTER_SERVICE_PATTERN = "%s-cluster-%s";
  private static final String DOMAIN_INTROSPECTOR_JOB_PATTERN = "%s-introspect-domain-job";
  private static final String EXTERNAL_SERVICE_PATTERN = "%s-%s-external";

  public static String toServerServiceName(String domainUid, String serverName) {
    return toServerName(domainUid, serverName);
  }

  private static String toServerName(String domainUid, String serverName) {
    return toDns1123LegalName(String.format(SERVER_PATTERN, domainUid, serverName));
  }

  public static String toEventName(String domainUid, String serverName) {
    return toServerName(domainUid, serverName);
  }

  public static String toPodName(String domainUid, String serverName) {
    return toServerName(domainUid, serverName);
  }

  public static String toClusterServiceName(String domainUid, String clusterName) {
    return toDns1123LegalName(String.format(CLUSTER_SERVICE_PATTERN, domainUid, clusterName));
  }

  public static String toJobIntrospectorName(String domainUid) {
    return toDns1123LegalName(String.format(DOMAIN_INTROSPECTOR_JOB_PATTERN, domainUid));
  }

  public static String toExternalServiceName(String domainUid, String serverName) {
    return toDns1123LegalName(String.format(EXTERNAL_SERVICE_PATTERN, domainUid, serverName));
  }

  /**
   * Converts value to nearest DNS-1123 legal name, which can be used as a Kubernetes identifier.
   *
   * @param value Input value
   * @return nearest DNS-1123 legal name
   */
  public static String toDns1123LegalName(String value) {
    return value.toLowerCase().replace('_', '-');
  }
}
