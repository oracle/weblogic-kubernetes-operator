// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.LabelConstants;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.utils.OperatorUtils.isNullOrEmpty;

public class KubernetesUtils {

  private KubernetesUtils() {
    // no-op
  }

  /**
   * Returns true if the two maps of values match. A null map is considered to match an empty map.
   *
   * @param first  the first map to compare
   * @param second the second map to compare
   * @return true if the maps match.
   */
  static <K, V> boolean mapEquals(Map<K, V> first, Map<K, V> second) {
    return Objects.equals(first, second) || (isNullOrEmpty(first) && isNullOrEmpty(second));
  }

  /**
   * Returns true if the current map is missing values from the required map. This method is
   * typically used to compare labels and annotations against specifications derived from the
   * domain.
   *
   * @param current  a map of the values found in a Kubernetes resource
   * @param required a map of the values specified for the resource by the domain
   * @return true if there is a problem that must be fixed by patching
   */
  static boolean isMissingValues(Map<String, String> current, Map<String, String> required) {
    if (!hasAllRequiredNames(current, required)) {
      return true;
    }
    for (Map.Entry<String, String> entry : required.entrySet()) {
      if (!Objects.equals(current.get(entry.getKey()), entry.getValue())) {
        return true;
      }
    }

    return false;
  }

  private static boolean hasAllRequiredNames(Map<String, ?> current, Map<String, ?> required) {
    return current.keySet().containsAll(required.keySet());
  }

  /**
   * Adds patches to the specified patch builder to correct differences in the current vs required
   * maps.
   *
   * @param patchBuilder a builder for the patches
   * @param basePath     the base for the patch path (excluding the name)
   * @param current      a map of the values found in a Kubernetes resource
   * @param required     a map of the values specified for the resource by the domain
   */
  static void addPatches(
        JsonPatchBuilder patchBuilder,
        String basePath,
        Map<String, String> current,
        Map<String, String> required) {

    for (Map.Entry<String, String> entry : required.entrySet()) {
      String name = entry.getKey();
      // We must encode each '/' and '~' in a JSON patch token using '~1' and '~0', otherwise
      // the JSON patch will incorrectly treat '/' and '~' as special delimiters. (RFC 6901).
      // The resulting patched JSON will have '/' and '~' within the token (not ~0 or ~1).
      String encodedPath = basePath + name.replace("~", "~0").replace("/", "~1");
      if (!current.containsKey(name)) {
        patchBuilder.add(encodedPath, entry.getValue());
      } else {
        patchBuilder.replace(encodedPath, entry.getValue());
      }
    }
  }

  /**
   * Returns the name of the resource, extracted from its metadata.
   *
   * @param resource a Kubernetes resource
   * @return the name, if found
   */
  static String getResourceName(Object resource) {
    return Optional.ofNullable(getResourceMetadata(resource)).map(V1ObjectMeta::getName).orElse(null);
  }

  /**
   * Returns the metadata of the resource.
   *
   * @param resource a Kubernetes resource
   * @return the metadata, if found; otherwise a newly created one.
   */
  static V1ObjectMeta getResourceMetadata(Object resource) {
    if (resource instanceof KubernetesObject) {
      return ((KubernetesObject) resource).getMetadata();
    } else {
      return new V1ObjectMeta();
    }
  }

  /**
   * Returns true if the first metadata indicates a newer resource than does the second. 'Newer'
   * indicates that the creation time is later.
   *
   * @param m1 the first item to compare
   * @param m2 the second item to compare
   * @return true if the first object is newer than the second object
   */
  public static boolean isFirstNewer(V1ObjectMeta m1, V1ObjectMeta m2) {
    OffsetDateTime time1 = Optional.ofNullable(m1).map(V1ObjectMeta::getCreationTimestamp).orElse(OffsetDateTime.MIN);
    OffsetDateTime time2 = Optional.ofNullable(m2).map(V1ObjectMeta::getCreationTimestamp).orElse(OffsetDateTime.MAX);

    return time1.isAfter(time2);
  }

  /**
   * Returns the resource version associated with the specified list.
   *
   * @param list the result of a Kubernetes list operation.
   * @return Resource version
   */
  public static String getResourceVersion(KubernetesListObject list) {
    return Optional.ofNullable(list)
          .map(KubernetesListObject::getMetadata)
          .map(V1ListMeta::getResourceVersion)
          .orElse("");
  }

  public static V1ObjectMeta withOperatorLabels(String uid, V1ObjectMeta meta) {
    return meta.putLabelsItem(LabelConstants.DOMAINUID_LABEL, uid)
          .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true");
  }

  static boolean isOperatorCreated(V1ObjectMeta metadata) {
    return Boolean.parseBoolean(getOperatorCreatedLabel(metadata));
  }

  private static String getOperatorCreatedLabel(V1ObjectMeta metadata) {
    return Optional.ofNullable(metadata.getLabels())
          .map(labels -> labels.get(CREATEDBYOPERATOR_LABEL))
          .orElse("false");
  }

  /**
   * Returns the value of the domainUID label in the given Kubernetes resource metadata.
   *
   * @param metadata the Kubernetes Metadata object
   * @return value of the domainUID label
   */
  public static String getDomainUidLabel(V1ObjectMeta metadata) {
    return Optional.ofNullable(metadata)
          .map(V1ObjectMeta::getLabels)
          .map(labels -> labels.get(DOMAINUID_LABEL))
          .orElse(null);
  }

  /**
   * Reads operator product version that created a resource, if available.
   *
   * @param metadata Metadata from a resource
   * @return Operator product version that created the resource
   */
  public static SemanticVersion getProductVersionFromMetadata(V1ObjectMeta metadata) {
    return Optional.ofNullable(metadata)
        .map(V1ObjectMeta::getLabels)
        .map(labels -> labels.get(LabelConstants.OPERATOR_VERSION))
        .map(SemanticVersion::new)
        .orElse(null);
  }
}