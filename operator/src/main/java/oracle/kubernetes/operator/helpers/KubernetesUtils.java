// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1ObjectMeta;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
}
