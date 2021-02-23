// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.VersionInfo;

/** Major and minor version of Kubernetes API Server. */
public class KubernetesVersion extends SemanticVersion {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final KubernetesVersion TEST_VERSION = new KubernetesVersion(TEST_VERSION_INFO);
  private static final String[] MINIMUM_K8S_VERSIONS = {"1.14.8", "1.15.7", "1.16.0", "1.17.0", "1.18.0"};
  static final KubernetesVersion UNREADABLE = new KubernetesVersion(0, 0);
  private final String version;

  /**
   * Construct Kubernetes version.
   * @param major major
   * @param minor minor
   */
  public KubernetesVersion(int major, int minor) {
    super(major, minor);
    version = String.format("%d.%d.0", major, minor);
  }

  KubernetesVersion(VersionInfo info) {
    super(Integer.parseInt(info.getMajor()), getNumericPortion(info.getMinor()), getRevision(info.getGitVersion()));
    version = info.getGitVersion();
  }

  static String getSupportedVersions() {
    return String.join("+,", MINIMUM_K8S_VERSIONS) + "+";
  }

  // version is a string like 1.2.3[+abcd]
  private static int getRevision(String version) {
    String[] splitVersion = version.split("\\.");
    return splitVersion.length > 2 ? getNumericPortion(splitVersion[2]) : 0;
  }

  String asDisplayString() {
    return version;
  }

  boolean isCompatible() {
    int numHigher = 0;
    for (String minimumVersion : MINIMUM_K8S_VERSIONS) {
      switch (getCompatibilityWith(minimumVersion)) {
        case REVISION_OK:
          return true;
        case REVISION_TOO_LOW:
          return false;
        case VERSION_HIGHER:
          numHigher++;
          break;
        default:
      }
    }

    return numHigher == MINIMUM_K8S_VERSIONS.length;
  }

  boolean isPublishNotReadyAddressesSupported() {
    return getMajor() > 1 || (getMajor() == 1 && getMinor() >= 8);
  }

  boolean isCrdV1Supported() {
    return getMajor() > 1 || (getMajor() == 1 && getMinor() >= 16);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof KubernetesVersion && equals(o);
  }

  @Override
  public String toString() {
    return "KubernetesVersion{" + "major=" + getMajor() + ", minor=" + getMinor() + ", revision=" + getRevision() + '}';
  }
}
