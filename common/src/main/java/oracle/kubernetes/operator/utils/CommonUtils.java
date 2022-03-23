// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.time.OffsetDateTime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;

import static oracle.kubernetes.operator.CommonConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.CommonConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.CommonConstants.LATEST_IMAGE_SUFFIX;

public class CommonUtils {

  /**
   * Returns the image pull policy to use by default, for the specified image.
   * @param imageName the image name to test
   */
  public static String getInferredImagePullPolicy(String imageName) {
    return useLatestImage(imageName) ? ALWAYS_IMAGEPULLPOLICY : IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  private static boolean useLatestImage(String imageName) {
    return imageName.endsWith(LATEST_IMAGE_SUFFIX);
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
   * Returns the Gson builder with type adapter for OffsetDateTime.
   */
  public static Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            .create();
  }
}
