// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Objects;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;

/** Major, minor and revision version specification for a product. */
public class SemanticVersion implements Comparable<SemanticVersion> {
  public static final SemanticVersion TEST_VERSION = new SemanticVersion(3,1);

  private final int major;
  private final int minor;
  private final int revision;

  /**
   * Construct semantic version.
   * @param major major
   * @param minor minor
   */
  public SemanticVersion(int major, int minor) {
    this(major, minor, 0);
  }

  /**
   * Construct semantic version.
   * @param major major
   * @param minor minor
   * @param revision revision
   */
  public SemanticVersion(int major, int minor, int revision) {
    this.major = major;
    this.minor = minor;
    this.revision = revision;
  }

  /**
   * Construct semantic version.
   * @param fullVersion Version formatted like "1.2[.3[+abcd]]
   */
  public SemanticVersion(String fullVersion) {
    String[] splitVersion = fullVersion.split("\\.");
    this.major = getNumericPortion(splitVersion[0]);
    this.minor = getNumericPortion(splitVersion[1]);
    this.revision = splitVersion.length > 2 ? getNumericPortion(splitVersion[2]) : 0;
  }

  protected static int getNumericPortion(String numericString) {
    while (!numericString.chars().allMatch(Character::isDigit)) {
      numericString = numericString.substring(0, numericString.length() - 1);
    }
    return numericString.isEmpty() ? 0 : Integer.parseInt(numericString);
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  public int getRevision() {
    return revision;
  }

  /**
   * Compatibility check, similar to compare, but that reports more details on revision comparison.
   * @param minimumVersion Minimum version
   * @return Compatibility statement
   */
  public Compatibility getCompatibilityWith(String minimumVersion) {
    SemanticVersion minimum = new SemanticVersion(minimumVersion);
    if (major < minimum.major) {
      return Compatibility.VERSION_LOWER;
    }
    if (major > minimum.major) {
      return Compatibility.VERSION_HIGHER;
    }

    if (minor < minimum.minor) {
      return Compatibility.VERSION_LOWER;
    }
    if (minor > minimum.minor) {
      return Compatibility.VERSION_HIGHER;
    }

    return (revision >= minimum.revision)
            ? Compatibility.REVISION_OK
            : Compatibility.REVISION_TOO_LOW;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof SemanticVersion rhs)) {
      return false;
    }

    EqualsBuilder builder =
        new EqualsBuilder()
            .append(major, rhs.major)
            .append(minor, rhs.minor)
            .append(revision, rhs.revision);
    return builder.isEquals();
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, revision);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + revision;
  }

  @Override
  public int compareTo(@NotNull SemanticVersion o) {
    int majorDiff = this.major - o.major;
    if (majorDiff != 0) {
      return majorDiff;
    }
    int minorDiff = this.minor - o.minor;
    if (minorDiff != 0) {
      return minorDiff;
    }
    return this.revision - o.revision;
  }

  enum Compatibility {
    REVISION_OK,
    REVISION_TOO_LOW,
    VERSION_HIGHER,
    VERSION_LOWER
  }
}
