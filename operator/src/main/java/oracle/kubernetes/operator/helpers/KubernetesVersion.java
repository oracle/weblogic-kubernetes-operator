// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Objects;

import io.kubernetes.client.openapi.models.VersionInfo;

/** Major and minor version of Kubernetes API Server. */
public class KubernetesVersion {
  public static final KubernetesVersion TEST_VERSION = new KubernetesVersion(1, 10);
  private static final String[] MINIMUM_K8S_VERSIONS = {"1.13.5", "1.14.8", "1.15.7"};
  static final KubernetesVersion UNREADABLE = new KubernetesVersion(0, 0);
  private final int major;
  private final int minor;
  private final int revision;
  private final String version;

  /**
   * Construct Kubernetes version.
   * @param major major
   * @param minor minor
   */
  public KubernetesVersion(int major, int minor) {
    this.major = major;
    this.minor = minor;
    revision = 0;
    version = String.format("%d.%d.0", major, minor);
  }

  KubernetesVersion(VersionInfo info) {
    major = Integer.parseInt(info.getMajor());
    minor = getNumericPortion(info.getMinor());
    version = info.getGitVersion();
    revision = getRevision(asDisplayString());
  }

  static String getSupportedVersions() {
    return String.join("+,", MINIMUM_K8S_VERSIONS) + "+";
  }

  // version is a string like 1.2.3[+abcd]
  private static int getRevision(String version) {
    String[] splitVersion = version.split("\\.");
    return splitVersion.length > 2 ? getNumericPortion(splitVersion[2]) : 0;
  }

  private static int getNumericPortion(String numericString) {
    while (!numericString.chars().allMatch(Character::isDigit)) {
      numericString = numericString.substring(0, numericString.length() - 1);
    }
    return numericString.length() == 0 ? 0 : Integer.parseInt(numericString);
  }

  int getMajor() {
    return major;
  }

  int getMinor() {
    return minor;
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

  private Compatibility getCompatibilityWith(String minimumVersion) {
    String[] parts = minimumVersion.split("\\.");
    int allowedMajor = asInteger(parts[0]);
    if (major < allowedMajor) {
      return Compatibility.VERSION_LOWER;
    }
    if (major > allowedMajor) {
      return Compatibility.VERSION_HIGHER;
    }

    int allowedMinor = asInteger(parts[1]);
    if (minor < allowedMinor) {
      return Compatibility.VERSION_LOWER;
    }
    if (minor > allowedMinor) {
      return Compatibility.VERSION_HIGHER;
    }

    int allowedRevision = asInteger(parts[2]);
    return (revision >= allowedRevision)
        ? Compatibility.REVISION_OK
        : Compatibility.REVISION_TOO_LOW;
  }

  private int asInteger(String intString) {
    return Integer.parseInt(intString);
  }

  boolean isPublishNotReadyAddressesSupported() {
    return this.major > 1 || (this.major == 1 && this.minor >= 8);
  }

  boolean isCrdSubresourcesSupported() {
    return this.major > 1 || (this.major == 1 && this.minor >= 10);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof KubernetesVersion && equals((KubernetesVersion) o);
  }

  private boolean equals(KubernetesVersion o) {
    return major == o.major && minor == o.minor;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor);
  }

  @Override
  public String toString() {
    return "KubernetesVersion{" + "major=" + major + ", minor=" + minor + '}';
  }

  enum Compatibility {
    REVISION_OK,
    REVISION_TOO_LOW,
    VERSION_HIGHER,
    VERSION_LOWER
  }
}
