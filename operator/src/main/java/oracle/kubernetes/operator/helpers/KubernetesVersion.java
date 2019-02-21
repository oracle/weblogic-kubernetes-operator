// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.VersionInfo;
import java.util.Objects;

/** Major and minor version of Kubernetes API Server. */
public class KubernetesVersion {
  static KubernetesVersion UNREADABLE = new KubernetesVersion(0, 0);
  private static final String[] MINIMUM_K8S_VERSIONS = {"1.10.11", "1.11.5", "1.12.3"};

  private final int major;
  private final int minor;
  private final int revision;
  private final String version;

  KubernetesVersion(int major, int minor) {
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

  int getMajor() {
    return major;
  }

  int getMinor() {
    return minor;
  }

  String asDisplayString() {
    return version;
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

  enum Compatibility {
    REVISION_OK,
    REVISION_TOO_LOW,
    VERSION_HIGHER,
    VERSION_LOWER
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

  boolean isRulesReviewSupported() {
    return this.major > 1 || (this.major == 1 && this.minor >= 8);
  }

  boolean isCRDSubresourcesSupported() {
    return this.major > 1 || (this.major == 1 && this.minor >= 10);
  }

  // Even though subresources are supported at version 1.10, we've determined that the
  // 'status' subresource and the pattern of using "/status" doesn't actually work
  // until 1.13.  This is validated against the published recent changes doc.
  // *NOTE*: To use this, update CRDHelper to include the status subresource and also
  // update DomainStatusUpdater to use replaceDomainStatusAsync
  boolean isCRDSubresourcesStatusPatternSupported() {
    return this.major > 1 || (this.major == 1 && this.minor >= 13);
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
}
