// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1ObjectMeta;
import java.util.Map;
import oracle.kubernetes.operator.LabelConstants;

/** Helper methods for managing versions. */
public class VersionHelper {
  /**
   * Determines whether a resource matches a version.
   *
   * @param meta Metadata
   * @param resourceVersion resource version
   * @return true, if the labeled and expected versions match
   */
  public static boolean matchesResourceVersion(V1ObjectMeta meta, String resourceVersion) {
    if (meta == null) {
      return false;
    }
    Map<String, String> labels = meta.getLabels();
    if (labels == null) {
      return false;
    }
    String val = labels.get(LabelConstants.RESOURCE_VERSION_LABEL);
    if (val == null) {
      return false;
    }
    return val.equals(resourceVersion);
  }
}
