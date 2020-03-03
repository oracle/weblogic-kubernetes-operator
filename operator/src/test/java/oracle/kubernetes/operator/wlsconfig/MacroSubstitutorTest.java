// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacroSubstitutorTest {

  @Test
  public void testMacros() {
    final int ID = 123;
    final String server = "ms-1";
    final String cluster = "cluster-1";
    final String domain = "base_domain";
    final String machine = "slc08urp";

    final MacroSubstitutor macroSubstitutor =
        new MacroSubstitutor(ID, server, cluster, domain, machine);

    assertEquals(
        "empty input string should return an empty string",
        "",
        macroSubstitutor.substituteMacro(""));

    assertEquals(
        "null input string should return null", null, macroSubstitutor.substituteMacro(null));

    assertEquals(
        "string without macro should remains unchanged",
        "abcdefg 1",
        macroSubstitutor.substituteMacro("abcdefg 1"));

    assertEquals(
        "string with ${id} macro",
        "myserver-" + ID,
        macroSubstitutor.substituteMacro("myserver-${id}"));

    assertEquals(
        "string with ${serverName} macro",
        "test-" + server,
        macroSubstitutor.substituteMacro("test-${serverName}"));

    assertEquals(
        "string with ${clusterName} macro",
        "test-" + cluster,
        macroSubstitutor.substituteMacro("test-${clusterName}"));

    assertEquals(
        "string with ${domainName} macro",
        "test-" + domain,
        macroSubstitutor.substituteMacro("test-${domainName}"));

    assertEquals(
        "string with only macro", server, macroSubstitutor.substituteMacro("${serverName}"));

    assertEquals(
        "string with multiple macros",
        server + "-" + domain + "-" + cluster + "-" + ID,
        macroSubstitutor.substituteMacro("${serverName}-${domainName}-${clusterName}-${id}"));

    System.setProperty("oracle.macrosubstitutortest", "myEnv Value");
    assertEquals(
        "string with system property macro",
        "myEnv Value",
        macroSubstitutor.substituteMacro("${oracle.macrosubstitutortest}"));

    Properties systemProperties = System.getProperties();
    systemProperties.remove("oracle.macrosubstitutortest");
    assertEquals(
        "string with system property macro but system property not set",
        "test--1",
        macroSubstitutor.substituteMacro("test-${oracle.macrosubstitutortest}-1"));

    assertEquals(
        "string without complete macro", "test${", macroSubstitutor.substituteMacro("test${"));
  }
}
