// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

public class ResourceVersion implements Comparable<ResourceVersion> {
  private final String value;

  private final String prefix;
  private final Integer version;
  private final String prerelease;
  private final Integer prereleaseVersion;

  public ResourceVersion(String value) {
    this.value = value;

    // version values must follow pattern of characters followed by digits
    // if the initial character token is "v", then the value "alpha" or "beta"
    // may follow the numeric token followed by another token of digits.  Anything
    // else is illegal
    if (value == null || value.length() < 1 || !Character.isAlphabetic(value.charAt(0))) {
      throw new IllegalArgumentException();
    }

    boolean foundVersion = false;
    int i = 1;
    for (; i < value.length(); i++) {
      if (Character.isDigit(value.charAt(i))) {
        foundVersion = true;
        break;
      }
    }

    if (!foundVersion) {
      throw new IllegalArgumentException("Version not found");
    }

    prefix = value.substring(0, i);

    int n = i++;
    for (; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        break;
      }
    }

    version = Integer.valueOf(value.substring(n, i));

    if (i < value.length()) {
      String remainder = value.substring(i);
      if (remainder.startsWith("alpha")) {
        prerelease = "alpha";
        i += 5;
        n = i;
        for (; i < value.length(); i++) {
          if (!Character.isDigit(value.charAt(i))) {
            throw new IllegalArgumentException("Must only have digits after alpha");
          }
        }

        prereleaseVersion = (n < i) ? Integer.valueOf(value.substring(n, i)) : null;
      } else if (remainder.startsWith("beta")) {
        prerelease = "beta";
        i += 4;
        n = i;
        for (; i < value.length(); i++) {
          if (!Character.isDigit(value.charAt(i))) {
            throw new IllegalArgumentException("Must only have digits after beta");
          }
        }

        prereleaseVersion = (n < i) ? Integer.valueOf(value.substring(n, i)) : null;
      } else {
        throw new IllegalArgumentException(
            "Invalid pre-release designation; must be \"alpha\" or \"beta\"");
      }
    } else {
      prerelease = null;
      prereleaseVersion = null;
    }
  }

  public boolean isWellFormed() {
    return "v".equals(prefix) && version != null;
  }

  public boolean isGA() {
    return prerelease == null;
  }

  public boolean isAlpha() {
    return "alpha".equals(prerelease);
  }

  public boolean isBeta() {
    return "beta".equals(prerelease);
  }

  public String getPrefix() {
    return prefix;
  }

  public Integer getVersion() {
    return version;
  }

  public String getPrerelease() {
    return prerelease;
  }

  public Integer getPrereleaseVersion() {
    return prereleaseVersion;
  }

  @Override
  public int compareTo(ResourceVersion o) {
    if (isWellFormed()) {
      if (!o.isWellFormed()) {
        return 1;
      }

      if (isGA()) {
        if (!o.isGA()) {
          return 1;
        }
      } else if (isBeta()) {
        if (o.isGA()) {
          return -1;
        }
        if (o.isAlpha()) {
          return 1;
        }
      } else /* if (isAlpha()) */ {
        if (!o.isAlpha()) {
          return -1;
        }
      }

      if (!isGA()) {
        if (!version.equals(o.version)) {
          return version - o.version;
        }

        return prereleaseVersion - o.prereleaseVersion;
      }

      return version - o.version;
    } else {
      if (o.isWellFormed()) {
        return -1;
      }

      // Reverse order of comparision is intentional
      return o.value.compareTo(value);
    }
  }

  public String toString() {
    return value;
  }

  public boolean equals(Object o) {
    if (!(o instanceof ResourceVersion)) {
      return false;
    }

    return value.equals(((ResourceVersion) o).value);
  }

  public int hashCode() {
    return value.hashCode();
  }
}
