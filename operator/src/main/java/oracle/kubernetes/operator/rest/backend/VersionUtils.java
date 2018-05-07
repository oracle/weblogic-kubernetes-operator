// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.backend;

import java.util.ArrayList;
import java.util.List;

/** VersionUtils contains utilities for managing the versions of the WebLogic operator REST api. */
public class VersionUtils {

  private static final String LATEST = "latest";
  private static final String V1 = "v1";
  private static final List<String> versions = new ArrayList<String>();

  static {
    getVersions().add(V1);
  }

  private VersionUtils() {
    // hide implicit public constructor
  }

  /**
   * Get the supported versions of the WebLogic operator REST api.
   *
   * @return a List of version names.
   */
  public static List<String> getVersions() {
    return versions;
  }

  /**
   * Get the un-aliased name of a version of the WebLogic operator REST api.
   *
   * @param version - the potentially aliased name of the api.
   * @return - the un-aliased name of the api.
   */
  public static String getVersion(String version) {
    validateVersion(version);
    return LATEST.equals(version) ? getLatest() : version;
  }

  /**
   * Determines whether a version exists.
   *
   * @param version - the version's name (can be aliased).
   * @return whether not the version exists.
   */
  public static boolean isVersion(String version) {
    return LATEST.equals(version) || versions.contains(version);
  }

  /**
   * Gets the lifecycle of a version.
   *
   * @param version - the version's name (can be aliased). The caller is responsible for calling
   *     isVersion first and should not call this method if the version does not exist.
   * @return the version's lifecyle (either 'active' or 'deprecated')
   */
  public static String getLifecycle(String version) {
    return (isLatest(getVersion(version))) ? "active" : "deprecated";
  }

  /**
   * Get whether or not a version is the latest version of the WebLogic operator REST api.
   *
   * @param version - the version's name (can be aliased). The caller is responsible for calling
   *     isVersion first and should not call this method if the version does
   * @return whether or not this is the latest version of the WebLogic operator REST api.
   */
  public static boolean isLatest(String version) {
    return getLatest().equals(getVersion(version));
  }

  private static String getLatest() {
    return getVersions().get(0);
  }

  private static void validateVersion(String version) {
    if (!isVersion(version)) {
      throw new AssertionError("Invalid version: " + version);
    }
  }
}
