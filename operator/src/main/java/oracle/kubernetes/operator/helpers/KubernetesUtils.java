// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;

import io.kubernetes.client.models.V1ObjectMeta;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.json.JsonPatchBuilder;
import oracle.kubernetes.operator.LabelConstants;
import org.apache.commons.collections.MapUtils;
import org.joda.time.DateTime;

public class KubernetesUtils {

  /**
   * Returns true if the two maps of values match. A null map is considered to match an empty map.
   *
   * @param first the first map to compare
   * @param second the second map to compare
   * @return true if the maps match.
   */
  static <K, V> boolean mapEquals(Map<K, V> first, Map<K, V> second) {
    return Objects.equals(first, second) || (MapUtils.isEmpty(first) && MapUtils.isEmpty(second));
  }

  /**
   * Returns true if the current map is missing values from the required map. This method is
   * typically used to compare labels and annotations against specifications derived from the
   * domain.
   *
   * @param current a map of the values found in a Kubernetes resource
   * @param required a map of the values specified for the resource by the domain
   * @return true if there is a problem that must be fixed by patching
   */
  static boolean isMissingValues(Map<String, String> current, Map<String, String> required) {
    if (!hasAllRequiredNames(current, required)) {
      return true;
    }
    for (String name : required.keySet()) {
      if (!Objects.equals(current.get(name), required.get(name))) {
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
   * @param basePath the base for the patch path (excluding the name)
   * @param current a map of the values found in a Kubernetes resource
   * @param required a map of the values specified for the resource by the domain
   */
  static void addPatches(
      JsonPatchBuilder patchBuilder,
      String basePath,
      Map<String, String> current,
      Map<String, String> required) {
    for (String name : required.keySet()) {
      if (!current.containsKey(name)) {
        patchBuilder.add(basePath + name, required.get(name));
      } else {
        patchBuilder.replace(basePath + name, required.get(name));
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
    return getResourceMetadata(resource).getName();
  }

  /**
   * Returns the metadata of the resource.
   *
   * @param resource a Kubernetes resource
   * @return the metadata, if found; otherwise a newly created one.
   */
  static V1ObjectMeta getResourceMetadata(Object resource) {
    try {
      Field metadataField = resource.getClass().getDeclaredField("metadata");
      metadataField.setAccessible(true);
      return (V1ObjectMeta) metadataField.get(resource);
    } catch (NoSuchFieldException
        | SecurityException
        | IllegalArgumentException
        | IllegalAccessException e) {
      return new V1ObjectMeta();
    }
  }

  /**
   * Returns true if the first metadata indicates a newer resource than does the second. 'Newer'
   * indicates that the creation time is later. If two items have the same creation time, a higher
   * resource version indicates the newer resource.
   *
   * @param first the first item to compare
   * @param second the second item to compare
   * @return true if the first object is newer than the second object
   */
  public static boolean isFirstNewer(V1ObjectMeta first, V1ObjectMeta second) {
    if (second == null) return true;
    if (first == null) return false;

    DateTime time1 = first.getCreationTimestamp();
    DateTime time2 = second.getCreationTimestamp();

    if (time1.equals(time2)) {
      return getResourceVersion(first) > getResourceVersion(second);
    } else {
      return time1.isAfter(time2);
    }
  }

  private static int getResourceVersion(V1ObjectMeta metadata) {
    return Integer.parseInt(metadata.getResourceVersion());
  }

  public static V1ObjectMeta withOperatorLabels(V1ObjectMeta meta, String uid) {
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
}
