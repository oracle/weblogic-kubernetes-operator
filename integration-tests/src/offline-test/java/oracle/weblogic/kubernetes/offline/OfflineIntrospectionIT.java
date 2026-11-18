// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.offline;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.kubernetes.operator.helpers.DomainTopology;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs shared WDT models and release-specific regression cases through the production introspector. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfflineIntrospectionIT {
  private static final String PREFIX = "wko.it.introspection.";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final List<JsonNode> cases = new ArrayList<>();
  private Path results;
  private Path input;
  private String engine;
  private String imageId;
  private long timeout;

  @BeforeAll
  void prepare() throws Exception {
    var filter = Pattern.compile(System.getProperty(PREFIX + "cases", ".*"));
    try (var resource = getClass().getResourceAsStream("/offline-introspection/cases.json")) {
      assertNotNull(resource, "Missing model matrix");
      MAPPER.readTree(resource).forEach(c -> {
        if (filter.matcher(c.path("name").asText()).matches()) {
          cases.add(c);
        }
      });
    }
    assertFalse(cases.isEmpty(), "No model cases matched " + filter);
    results = Path.of(System.getProperty(PREFIX + "results")).toAbsolutePath()
        .resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID());
    input = Files.createDirectories(results.resolve("input"));
    engine = System.getProperty("WLSIMG_BUILDER",
        System.getenv().getOrDefault("WLSIMG_BUILDER", WLSIMG_BUILDER_DEFAULT));
    timeout = Long.parseLong(System.getProperty(PREFIX + "timeout.seconds", "600"));
    assertTrue(timeout > 0, "Timeout must be positive");
    String image = System.getProperty(PREFIX + "image");
    URI wdtUrl = URI.create(System.getProperty(PREFIX + "wdt.url"));
    Path wdt = input.resolve("weblogic-deploy.zip");
    String wdtVersion = WdtArchive.fetch(wdtUrl, wdt, System.getProperty(PREFIX + "wdt.version", ""));
    var metadata = new LinkedHashMap<String, Object>();
    metadata.put("image", image);
    metadata.put("engine", engine);
    metadata.put("wdtUrl", wdtUrl.toString());
    metadata.put("wdtVersion", wdtVersion);
    metadata.put("wdtSha256", sha256(wdt));
    try (var container = new OfflineContainer(engine, results, timeout)) {
      imageId = container.resolveImage(image);
      metadata.put("imageId", imageId);
      String version = container.version(imageId);
      metadata.put("wlsVersionOutput", version.strip());
      MAPPER.writerWithDefaultPrettyPrinter().writeValue(results.resolve("versions.json").toFile(), metadata);
      String installedVersion = IntrospectionMatrix.installedVersion(version);
      metadata.put("wlsVersion", installedVersion);
      var omitted = new LinkedHashMap<String, String>();
      cases.removeIf(c -> {
        if (IntrospectionMatrix.supports(c, installedVersion)) {
          return false;
        }
        omitted.put(c.path("name").asText(), "Requires WLS " + c.path("minimumWlsVersion").asText());
        return true;
      });
      cases.replaceAll(c -> IntrospectionMatrix.expectations(c, installedVersion));
      metadata.put("selectedCases", cases.stream().map(c -> c.path("name").asText()).toList());
      metadata.put("omittedCases", omitted);
      MAPPER.writerWithDefaultPrettyPrinter().writeValue(results.resolve("versions.json").toFile(), metadata);
      assertFalse(cases.isEmpty(), "No cases matched " + filter + " for installed WLS " + installedVersion);
      System.out.println("WLS " + installedVersion + ": " + cases.size() + " models selected; "
          + omitted.size() + " excluded by version. See versions.json for details.");
    }
    Path scripts = Path.of(System.getProperty(PREFIX + "scripts"));
    var hashes = new LinkedHashMap<String, String>();
    try (var paths = Files.walk(scripts)) {
      for (Path source : paths.sorted().toList()) {
        Path relative = scripts.relativize(source);
        Path target = input.resolve("scripts").resolve(relative);
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target);
          hashes.put(relative.toString(), sha256(source));
        }
      }
    }
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(results.resolve("scripts-sha256.json").toFile(), hashes);
    Files.writeString(input.resolve("username"), "weblogic");
    Files.writeString(input.resolve("password"), "Wls26Matrix_Test1");
    Files.writeString(input.resolve("runtime-password"), "OfflineIntrospection_Test1");
    System.out.println("Offline introspection results: " + results);
  }

  @TestFactory
  Stream<DynamicTest> introspectModels() {
    return cases.stream().map(c -> DynamicTest.dynamicTest(c.path("name").asText(), () -> verify(c)));
  }

  private void verify(JsonNode testCase) throws Exception {
    String name = testCase.path("name").asText();
    Path caseResults = Files.createDirectories(results.resolve(name));
    String model = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(testCase.path("model"));
    // JSON is also valid YAML and preserves omitted fields, booleans and string-valued flags.
    Files.writeString(caseResults.resolve("model.yaml"), model);
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(caseResults.resolve("case.json").toFile(), testCase);
    Files.writeString(input.resolve("model.yaml"), model);
    System.out.println("Introspecting " + name);
    try (var container = new OfflineContainer(engine, caseResults, timeout)) {
      int exit = container.introspect(imageId, input);
      int topologyCopy = container.copyTopology();
      int configCopy = container.copyDomainConfig();
      if (testCase.has("expectedError")) {
        assertNotEquals(0, exit, "Expected WDT to reject the model");
        assertTrue(Files.readString(caseResults.resolve("introspector.log"))
            .contains(testCase.path("expectedError").asText()), "Expected WDT diagnostic; see " + caseResults);
        assertNotEquals(0, topologyCopy, "Failed domain creation must not publish a topology");
        return;
      }
      assertEquals(0, exit, "Introspector failed; see " + caseResults);
      assertEquals(0, topologyCopy, "Missing topology.yaml; see " + caseResults);
      assertEquals(0, configCopy, "Missing domain config.xml; see " + caseResults);
      var topology = DomainTopology.parseDomainTopologyYaml(Files.readString(caseResults.resolve("topology.yaml")));
      assertNotNull(topology, "Operator cannot parse topology.yaml");
      assertTrue(topology.getDomainValid(), () -> topology.getValidationErrors().toString());
      WlsDomainConfig domain = topology.getDomain();
      assertEquals(testCase.path("model").path("topology").path("Name").asText(), domain.getName());
      var admin = domain.getServerConfig(domain.getAdminServerName());
      assertChannels(testCase.path("channels"), admin);
      var naps = admin.getNetworkAccessPoints();
      testCase.path("requiredForwardChannels").properties().forEach(entry -> {
        var nap = naps.stream().filter(n -> n.getName().equals(entry.getKey())).findFirst().orElse(null);
        assertNotNull(nap, "Missing forwarding channel " + entry.getKey());
        var expected = entry.getValue();
        assertEquals(expected.path("protocol").asText(), nap.getProtocol(), entry.getKey() + " protocol");
        assertEquals(expected.path("listenPort").asInt(), nap.getListenPort(), entry.getKey() + " listenPort");
        assertEquals(expected.path("publicPort").asInt(), nap.getPublicPort(), entry.getKey() + " publicPort");
      });
      testCase.path("forbiddenForwardChannels").forEach(channel ->
          assertTrue(naps.stream().noneMatch(n -> n.getName().equals(channel.asText())),
              "Unexpected forwarding channel " + channel));
      if (testCase.has("managedChannels")) {
        assertManagedChannels(testCase, domain);
      }
    }
  }

  private void assertManagedChannels(JsonNode testCase, WlsDomainConfig domain) {
    var clusters = testCase.path("model").path("topology").path("Cluster");
    clusters.properties().forEach(entry -> {
      var dynamic = entry.getValue().path("DynamicServers");
      var servers = domain.getClusterConfig(entry.getKey()).getServerConfigs();
      assertEquals(dynamic.path("DynamicClusterSize").asInt(), servers.size());
      assertFalse(servers.isEmpty(), "Missing managed servers");
      servers.forEach(server -> assertChannels(testCase.path("managedChannels"), server));
      var template = domain.getServerTemplates().stream()
          .filter(s -> s.getName().equals(dynamic.path("ServerTemplate").asText())).findFirst().orElseThrow();
      assertChannels(testCase.path("managedChannels"), template);
      if (testCase.has("managedServerNames")) {
        assertEquals(MAPPER.convertValue(testCase.path("managedServerNames"), List.class),
            servers.stream().map(WlsServerConfig::getName).toList());
      }
    });
  }

  private void assertChannels(JsonNode expected, WlsServerConfig server) {
    assertNotNull(server, "Missing server configuration");
    var actual = new LinkedHashMap<String, Integer>();
    if (server.getListenPort() != null) {
      actual.put("Default", server.getListenPort());
    }
    if (server.getSslListenPort() != null) {
      actual.put("DefaultSecure", server.getSslListenPort());
    }
    if (server.getAdminPort() != null) {
      actual.put("DefaultAdministration", server.getAdminPort());
    }
    Optional.ofNullable(server.getNetworkAccessPoints()).orElse(List.of()).stream()
        .filter(n -> !n.getName().startsWith("internal-"))
        .forEach(n -> actual.put(n.getName(), n.getListenPort()));
    var expectedPorts = new LinkedHashMap<String, Integer>();
    expected.properties().forEach(entry -> expectedPorts.put(entry.getKey(), entry.getValue().asInt()));
    assertEquals(expectedPorts, actual, server.getName() + " ports");
  }

  private static String sha256(Path path) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
  }
}
