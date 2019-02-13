// Copyright 2018,2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1ObjectMeta;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.json.JsonPatchBuilder;
import org.apache.commons.collections.MapUtils;

class KubernetesUtils {

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
   * Returns true if the labels on the current artifact metadata match those on the build version.
   * This excludes any weblogic-specific labels, as identified by the existence of the weblogic.
   * prefix.
   *
   * @param build the desired version of the metadata
   * @param current the current version of the metadata
   * @return true if the labels match
   */
  static boolean areLabelsValid(V1ObjectMeta build, V1ObjectMeta current) {
    return mapEquals(getCustomerLabels(current), getCustomerLabels(build));
  }

  private static Map<String, String> getCustomerLabels(V1ObjectMeta metadata) {
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, String> entry : metadata.getLabels().entrySet())
      if (!isOperatorLabel(entry)) result.put(entry.getKey(), entry.getValue());
    return result;
  }

  private static boolean isOperatorLabel(Map.Entry<String, String> label) {
    return label.getKey().startsWith("weblogic.");
  }

  /**
   * Returns true if the annotations on the current artifact metadata match those on the build
   * version.
   *
   * @param build the desired version of the metadata
   * @param current the current version of the metadata
   * @return true if the annotations match
   */
  static boolean areAnnotationsValid(V1ObjectMeta build, V1ObjectMeta current) {
    return mapEquals(current.getAnnotations(), build.getAnnotations());
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
    if (!hasAllRequiredNames(current, required)) return true;
    for (String name : required.keySet())
      if (!Objects.equals(current.get(name), required.get(name))) return true;

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
      if (!current.containsKey(name)) patchBuilder.add(basePath + name, required.get(name));
      else patchBuilder.replace(basePath + name, required.get(name));
    }
  }
}
