// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.VersionInfo;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** Major and minor version of Kubernetes API Server. */
public class KubernetesVersion extends SemanticVersion {
  private static final String[] MINIMUM_K8S_VERSIONS = {"1.21.10", "1.22.7", "1.23.4", "1.24.0"};
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
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof KubernetesVersion)) {
      return false;
    }

    EqualsBuilder builder =
        new EqualsBuilder()
            .appendSuper(super.equals(other));
    return builder.isEquals();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .appendSuper(super.hashCode());
    return builder.toHashCode();
  }

  @Override
  public String toString() {
    return "KubernetesVersion{" + "major=" + getMajor() + ", minor=" + getMinor() + ", revision=" + getRevision() + '}';
  }
}
