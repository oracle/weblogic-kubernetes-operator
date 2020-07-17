// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.StringTokenizer;

import com.google.common.base.Strings;
import oracle.kubernetes.operator.TuningParameters;

/** A class to create DNS-1123 legal names for Kubernetes objects. */
public class LegalNames {

  private static final String SERVER_PATTERN = "%s-%s";
  private static final String CLUSTER_SERVICE_PATTERN = "%s-cluster-%s";
  public static final String DOMAIN_INTROSPECTOR_JOB_SUFFIX = "-introspect-domain-job";
  private static final String DOMAIN_INTROSPECTOR_JOB_PATTERN = "%s" + DOMAIN_INTROSPECTOR_JOB_SUFFIX;
  private static final String EXTERNAL_SERVICE_PATTERN = "%s-%s-external";

  public static final String DNS_1123_FIELDS_PARAM = "dns1123Fields";

  // Fields that requires values to be DNS1123 legal
  private static final String[] DEFAULT_DNS1123_FIELDS = {
      "ClaimName",          // V1PersistentVolumeClaimVolumeSource
      "ClusterName",        // V1ObjectMetaData
      "ContainerName",      // V1ResourceFieldSelector
      "ExternalName",       // V1ServiceSpec
      "GenerateName",       // V1ObjectMetaData
      "MetricName",         // V2beta1PodsMetricSource, etc
      "Name",
      "NodeName",           // V1PodSpec, etc
      // "NominatedNodeName",  // V1PodStatus - excluded since it is not used within V1PodSpec
      "PersistentVolumeName",// V1VolumeAttachmentSource, etc
      "PriorityClassName",  // V1PodSpec
      "RuntimeClassName",   // V1PodSpec
      "SchedulerName",      // V1PodSpec
      "ScopeName",          // V1ScopedResourceSelectorRequirement
      "SecretName",         // V1SecretVolumeSource, etc
      "ServiceAccountName", // V1PodSpec
      "ServiceName",        // NetworkingV1beta1IngressBackend, etc
      "SingularName",       // V1APIResource
      "StorageClassName",   // V1PersistentVolumeSpec, V1PersistentVolumeClaimSpec
      "VolumeName"         // V1PersistentVolumeClaimSpec, etc
  };

  static String[] dns1123Fields;

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

  /**
   * Returns a list of field names of fields that needs to be in DNS-1123 format from the
   * "dns1123Fields" tuning parameter, if it is configured with a comma delimited values
   * containing field names.
   *
   * @return String array containing a list of fields that are required to be * in DNS-1123 format,
   *         or null if "dns1123Fields" tuning parameter is not configured.
   */
  private static String[] getConfiguredDns1123Fields() {
    String configuredValue = TuningParameters.getInstance().get(DNS_1123_FIELDS_PARAM);

    if (configuredValue == null) {
      return null;
    }

    configuredValue = configuredValue.trim();
    if (Strings.isNullOrEmpty(configuredValue)) {
      return null;
    }

    Collection<String> fields = new ArrayList<>();

    if (configuredValue != null) {
      StringTokenizer st = new StringTokenizer(configuredValue, ",");
      while (st.hasMoreTokens()) {
        fields.add(st.nextToken().trim());
      }
    }
    String[] fieldsArray = new String[fields.size()];
    return fields.toArray(fieldsArray);
  }


  private static String[] getDns1123Fields() {
    if (dns1123Fields == null) {
      dns1123Fields = Optional.ofNullable(getConfiguredDns1123Fields()).orElse(DEFAULT_DNS1123_FIELDS);
    }
    return dns1123Fields;
  }

  /**
   * Returns true if the value in the field is required to be DNS-1123 legal.
   *
   * @param fieldName Name of the field to be checked
   * @return true if the value needs to be DNS1123 legal, false otherwise
   */
  public static boolean isDns1123Required(String fieldName) {
    if (fieldName != null) {
      for (String dns1123Field: getDns1123Fields()) {
        if (dns1123Field.equalsIgnoreCase(fieldName)) {
          return true;
        }
      }
    }
    return false;
  }

}
