// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import static org.junit.Assert.assertEquals;

import java.util.Properties;
import org.junit.Test;

public class MacroSubstitutorTest {

  @Test
  public void testMacros() {
    final int ID = 123;
    final String SERVER = "ms-1";
    final String CLUSTER = "cluster-1";
    final String DOMAIN = "base_domain";
    final String MACHINE = "slc08urp";

    final MacroSubstitutor MACRO_SUBSTITUTOR =
        new MacroSubstitutor(ID, SERVER, CLUSTER, DOMAIN, MACHINE);

    assertEquals(
        "empty input string should return an empty string",
        "",
        MACRO_SUBSTITUTOR.substituteMacro(""));

    assertEquals(
        "null input string should return null", null, MACRO_SUBSTITUTOR.substituteMacro(null));

    assertEquals(
        "string without macro should remains unchanged",
        "abcdefg 1",
        MACRO_SUBSTITUTOR.substituteMacro("abcdefg 1"));

    assertEquals(
        "string with ${id} macro",
        "myserver-" + ID,
        MACRO_SUBSTITUTOR.substituteMacro("myserver-${id}"));

    assertEquals(
        "string with ${serverName} macro",
        "test-" + SERVER,
        MACRO_SUBSTITUTOR.substituteMacro("test-${serverName}"));

    assertEquals(
        "string with ${clusterName} macro",
        "test-" + CLUSTER,
        MACRO_SUBSTITUTOR.substituteMacro("test-${clusterName}"));

    assertEquals(
        "string with ${domainName} macro",
        "test-" + DOMAIN,
        MACRO_SUBSTITUTOR.substituteMacro("test-${domainName}"));

    assertEquals(
        "string with only macro", SERVER, MACRO_SUBSTITUTOR.substituteMacro("${serverName}"));

    assertEquals(
        "string with multiple macros",
        SERVER + "-" + DOMAIN + "-" + CLUSTER + "-" + ID,
        MACRO_SUBSTITUTOR.substituteMacro("${serverName}-${domainName}-${clusterName}-${id}"));

    System.setProperty("oracle.macrosubstitutortest", "myEnv Value");
    assertEquals(
        "string with system property macro",
        "myEnv Value",
        MACRO_SUBSTITUTOR.substituteMacro("${oracle.macrosubstitutortest}"));

    Properties systemProperties = System.getProperties();
    systemProperties.remove("oracle.macrosubstitutortest");
    assertEquals(
        "string with system property macro but system property not set",
        "test--1",
        MACRO_SUBSTITUTOR.substituteMacro("test-${oracle.macrosubstitutortest}-1"));

    assertEquals(
        "string without complete macro", "test${", MACRO_SUBSTITUTOR.substituteMacro("test${"));
  }
}
