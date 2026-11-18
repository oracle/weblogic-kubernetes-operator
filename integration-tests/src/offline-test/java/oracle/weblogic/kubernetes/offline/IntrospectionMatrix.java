// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.offline;

import java.util.Arrays;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Selects models and explicit expectations for the installed WebLogic release. */
final class IntrospectionMatrix {
  private IntrospectionMatrix() {
  }

  static String installedVersion(String output) {
    var matcher = Pattern.compile("WebLogic Server (\\d+(?:\\.\\d+){3,4})").matcher(output);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Cannot determine installed WLS version: " + output);
    }
    String version = matcher.group(1);
    if (compare(version, "12.2.1.4.0") < 0) {
      throw new IllegalArgumentException("This profile requires WLS 12.2.1.4 or later: " + version);
    }
    return version;
  }

  static boolean supports(JsonNode testCase, String version) {
    return compare(version, testCase.path("minimumWlsVersion").asText("12.2.1.4.0")) >= 0;
  }

  static ObjectNode expectations(JsonNode testCase, String version) {
    ObjectNode resolved = testCase.deepCopy();
    for (JsonNode override : testCase.path("expectationsBefore")) {
      if (compare(version, override.path("wlsVersion").asText()) < 0) {
        override.properties().stream().filter(e -> !e.getKey().equals("wlsVersion"))
            .forEach(e -> resolved.set(e.getKey(), e.getValue()));
        break;
      }
    }
    return resolved;
  }

  private static int compare(String left, String right) {
    int[] lhs = Arrays.stream(left.split("\\.")).mapToInt(Integer::parseInt).toArray();
    int[] rhs = Arrays.stream(right.split("\\.")).mapToInt(Integer::parseInt).toArray();
    for (int i = 0; i < Math.max(lhs.length, rhs.length); i++) {
      int difference = Integer.compare(i < lhs.length ? lhs[i] : 0, i < rhs.length ? rhs[i] : 0);
      if (difference != 0) {
        return difference;
      }
    }
    return 0;
  }
}
