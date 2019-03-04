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
    // may follow the numberic token followed by another token of digits.  Anything
    // else is illegal
    if (value == null || value.length() < 1 || !Character.isAlphabetic(value.charAt(0))) {
      throw new IllegalArgumentException();
    }

    boolean foundVersion = false;
    int i=1;
    for (; i<value.length(); i++) {
      if (Character.isDigit(value.charAt(i))) {
        foundVersion = true;
        break;
      }
    }

    if (!foundVersion) {
      throw new IllegalArgumentException("Version not found");
    }

    prefix = value.substring(0, i);

    int n=i++;
    for (; i<value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        break;
      }
    }

    version = Integer.valueOf(value.substring(n, i));

    if (i < value.length()) {
      String remainder = value.substring(i);
      if (remainder.startsWith("alpha")) {
        prerelease = "alpha";
        n = i+5;
        for (; i<value.length(); i++) {
          if (!Character.isDigit(value.charAt(i))) {
            break;
          }
        }

        prereleaseVersion = Integer.valueOf(value.substring(n, i));
      } else if (remainder.startsWith("beta")) {
        prerelease = "beta";
        n = i+4;
        for (; i<value.length(); i++) {
          if (!Character.isDigit(value.charAt(i))) {
            break;
          }
        }

        prereleaseVersion = Integer.valueOf(value.substring(n, i));
      } else {
        throw new IllegalArgumentException("Invalid pre-release designation; must be \"alpha\" or \"beta\"");
      }
    } else {
      prerelease = null;
      prereleaseVersion = null;
    }
  }

  public boolean isWellFormed() {
    return "v".equals(prefix) && version != null;
  }

  @Override
  public int compareTo(ResourceVersion o) {

  }
}
