// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import oracle.kubernetes.common.CommonConstants;

public class CommonUtils {

  private CommonUtils() {
    //not called
  }

  /**
   * Returns the image pull policy to use by default, for the specified image.
   * @param imageName the image name to test
   */
  public static String getInferredImagePullPolicy(String imageName) {
    return useLatestImage(imageName) ? "Always" : "IfNotPresent";
  }

  private static boolean useLatestImage(String imageName) {
    return imageName.endsWith(CommonConstants.LATEST_IMAGE_SUFFIX);
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
