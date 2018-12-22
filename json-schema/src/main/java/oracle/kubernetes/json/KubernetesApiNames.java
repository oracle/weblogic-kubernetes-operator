// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

class KubernetesApiNames {

  public static boolean matches(String className, Class<?> candidateClass) {
    if (!candidateClass.getName().startsWith("io.kubernetes.client")) return false;

    String[] parts = className.split("\\.");
    if (parts.length < 2) return false;
    String last = parts[parts.length - 1];
    String nextToLast = parts[parts.length - 2];

    String simpleName = candidateClass.getSimpleName();
    return matches(simpleName, nextToLast, last);
  }

  private static boolean matches(String simpleName, String nextToLast, String last) {
    return simpleName.equals(last) || matchesPackagePlusClass(simpleName, nextToLast, last);
  }

  private static boolean matchesPackagePlusClass(
      String simpleName, String nextToLast, String last) {
    return simpleName.endsWith(last) && simpleName.equalsIgnoreCase(nextToLast + last);
  }
}
