// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.VersionInfo;

/** Major and minor version of Kubernetes API Server. */
public class KubernetesVersion extends SemanticVersion {
  private static final String[] MINIMUM_K8S_VERSIONS = {"1.16.15", "1.17.13", "1.18.10", "1.19.7"};
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

  public KubernetesVersion(VersionInfo info) {
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

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof KubernetesVersion && equals(o);
  }

  @Override
  public String toString() {
    return "KubernetesVersion{" + "major=" + getMajor() + ", minor=" + getMinor() + ", revision=" + getRevision() + '}';
  }
}
