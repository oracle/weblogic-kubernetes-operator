// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks release boundaries so older images cannot accidentally use newer expectations. */
class OfflineIntrospectionVersionIT {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void acceptsSupportedInstalledReleases() {
    for (String version : new String[]{"12.2.1.4.0", "14.1.1.0.0", "14.1.2.0.0", "15.1.1.0.0", "26.1.0.0.0"}) {
      assertEquals(version, IntrospectionMatrix.installedVersion("WebLogic Server " + version + " build-info"));
    }
  }

  @Test
  void rejectsEarlierReleaseAndUnrecognizedOutput() {
    assertThrows(IllegalArgumentException.class, () ->
        IntrospectionMatrix.installedVersion("WebLogic Server 12.2.1.3.0"));
    assertThrows(IllegalArgumentException.class, () -> IntrospectionMatrix.installedVersion("Java version 25"));
  }

  @Test
  void gatesNewerCasesUsingTheInstalledRelease() throws Exception {
    var testCase = MAPPER.readTree("{\"minimumWlsVersion\":\"26.1.0.0.0\"}");
    assertFalse(IntrospectionMatrix.supports(testCase, "14.1.2.0.0"));
    assertFalse(IntrospectionMatrix.supports(testCase, "26.0.0.0.0"));
    assertTrue(IntrospectionMatrix.supports(testCase, "26.1.0.0.0"));
    assertTrue(IntrospectionMatrix.supports(testCase, "27.1.0.0.0"));
  }

  @Test
  void resolvesTemplatePortsWithoutChangingTheModel() throws Exception {
    try (var input = getClass().getResourceAsStream("/offline-introspection/cases.json")) {
      var cases = MAPPER.readTree(input);
      int checked = 0;
      for (var testCase : cases) {
        String name = testCase.path("name").asText();
        if (!name.equals("t18_template_omitted") && !name.equals("t19_template_default_ports")) {
          continue;
        }
        for (String version : new String[]{"12.2.1.4.0", "14.1.1.0.0"}) {
          var resolved = IntrospectionMatrix.expectations(testCase, version);
          assertEquals(name.equals("t18_template_omitted") ? 7100 : 7001,
              resolved.path("managedChannels").path("Default").asInt());
          assertEquals(name.equals("t18_template_omitted") ? 8100 : 7002,
              resolved.path("managedChannels").path("DefaultSecure").asInt());
          assertEquals(testCase.path("model"), resolved.path("model"));
        }
        for (String version : new String[]{"14.1.2.0.0", "15.1.1.0.0", "26.0.0.0.0"}) {
          var resolved = IntrospectionMatrix.expectations(testCase, version);
          assertEquals(7001, resolved.path("managedChannels").path("Default").asInt());
          assertEquals(7002, resolved.path("managedChannels").path("DefaultSecure").asInt());
          assertEquals(testCase.path("model"), resolved.path("model"));
        }
        for (String version : new String[]{"26.1.0.0.0", "27.1.0.0.0"}) {
          var resolved = IntrospectionMatrix.expectations(testCase, version);
          assertEquals(name.equals("t18_template_omitted") ? 7100 : 7001,
              resolved.path("managedChannels").path("Default").asInt());
          assertEquals(8100, resolved.path("managedChannels").path("DefaultSecure").asInt());
          assertEquals(testCase.path("model"), resolved.path("model"));
        }
        checked++;
      }
      assertEquals(2, checked, "Missing template regression cases");
    }
  }

  @Test
  void resolvesForwardingPublicPortIndependentlyOfListenPort() throws Exception {
    try (var input = getClass().getResourceAsStream("/offline-introspection/cases.json")) {
      var cases = MAPPER.readTree(input);
      int checked = 0;
      for (var testCase : cases) {
        if (!testCase.path("name").asText().equals("t34_dev_ssl_only_custom")) {
          continue;
        }
        for (String version : new String[]{"12.2.1.4.0", "14.1.1.0.0", "14.1.2.0.0", "15.1.1.0.0", "26.1.0.0.0"}) {
          var resolved = IntrospectionMatrix.expectations(testCase, version);
          var channel = resolved.path("requiredForwardChannels").path("internal-t3s");
          assertEquals("t3s", channel.path("protocol").asText());
          assertEquals(18334, channel.path("listenPort").asInt());
          assertEquals(version.equals("12.2.1.4.0") || version.equals("14.1.1.0.0") ? 0 : 18334,
              channel.path("publicPort").asInt());
          assertEquals(testCase.path("model"), resolved.path("model"));
        }
        checked++;
      }
      assertEquals(1, checked, "Missing SSL-only forwarding case");
    }
  }
}
