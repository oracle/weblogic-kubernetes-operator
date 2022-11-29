// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import oracle.kubernetes.common.CommonConstants;

public class CommonUtils {

  private static CheckedFunction<String, String> getMD5Hash = CommonUtils::getMD5Hash;

  public static final int MAX_ALLOWED_VOLUME_NAME_LENGTH = 63;
  public static final String VOLUME_NAME_SUFFIX = "-volume";

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

  /**
   * Returns a truncated volume name if the name exceeds the max allowed limit of characters for volume name.
   * @param volumeName volume name
   * @return truncated volume name if the name exceeds the limit, else return volumeName
   * @throws NoSuchAlgorithmException Thrown when particular cryptographic algorithm
   *                                  is not available in the environment.
   */
  public static String getLegalVolumeName(String volumeName) throws NoSuchAlgorithmException {
    return volumeName.length() > (MAX_ALLOWED_VOLUME_NAME_LENGTH)
        ? getShortName(volumeName)
        : volumeName;
  }


  private static String getShortName(String resourceName) throws NoSuchAlgorithmException {
    String volumeSuffix = Optional.ofNullable(getMD5Hash.apply(resourceName)).orElse("");
    return resourceName.substring(0, MAX_ALLOWED_VOLUME_NAME_LENGTH - volumeSuffix.length()) + volumeSuffix;
  }

  /**
   * Gets the MD5 hash of a string.
   *
   * @param data input string
   * @return MD5 hash value of the data, null in case of an exception.
   */
  public static String getMD5Hash(String data) throws NoSuchAlgorithmException {
    return bytesToHex(MessageDigest.getInstance("MD5").digest(data.getBytes(StandardCharsets.UTF_8)));
  }

  private static String bytesToHex(byte[] hash) {
    StringBuilder result = new StringBuilder();
    for (byte b : hash) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  @FunctionalInterface
  public interface CheckedFunction<T, R> {
    R apply(T t) throws NoSuchAlgorithmException;
  }
}
