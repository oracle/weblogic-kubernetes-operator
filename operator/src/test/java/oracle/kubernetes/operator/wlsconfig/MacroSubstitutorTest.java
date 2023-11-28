// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MacroSubstitutorTest {

  @Test
  void testMacros() {
    final int ID = 123;
    final String server = "ms-1";
    final String cluster = "cluster-1";
    final String domain = "base_domain";
    final String machine = "slc08urp";

    final MacroSubstitutor macroSubstitutor =
        new MacroSubstitutor(ID, server, cluster, domain, machine);

    assertEquals("", macroSubstitutor.substituteMacro(""), "empty input string should return an empty string");

    assertNull(macroSubstitutor.substituteMacro(null), "null input string should return null");

    assertEquals("abcdefg 1",
        macroSubstitutor.substituteMacro("abcdefg 1"), "string without macro should remains unchanged");

    assertEquals("myserver-" + ID,
        macroSubstitutor.substituteMacro("myserver-${id}"), "string with ${id} macro");

    assertEquals("test-" + server,
        macroSubstitutor.substituteMacro("test-${serverName}"), "string with ${serverName} macro");

    assertEquals("test-" + cluster,
        macroSubstitutor.substituteMacro("test-${clusterName}"), "string with ${clusterName} macro");

    assertEquals("test-" + domain,
        macroSubstitutor.substituteMacro("test-${domainName}"), "string with ${domainName} macro");

    assertEquals(server, macroSubstitutor.substituteMacro("${serverName}"), "string with only macro");

    assertEquals(server + "-" + domain + "-" + cluster + "-" + ID,
        macroSubstitutor.substituteMacro("${serverName}-${domainName}-${clusterName}-${id}"),
        "string with multiple macros");

    System.setProperty("oracle.macrosubstitutortest", "myEnv Value");
    assertEquals("myEnv Value",
        macroSubstitutor.substituteMacro("${oracle.macrosubstitutortest}"), "string with system property macro");

    Properties systemProperties = System.getProperties();
    systemProperties.remove("oracle.macrosubstitutortest");
    assertEquals("test--1",
        macroSubstitutor.substituteMacro("test-${oracle.macrosubstitutortest}-1"),
        "string with system property macro but system property not set");

    assertEquals("test${", macroSubstitutor.substituteMacro("test${"), "string without complete macro");
  }
}
