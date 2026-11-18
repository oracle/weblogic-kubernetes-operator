// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.offline;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Downloads and validates the WDT distribution once per matrix run. */
final class WdtArchive {
  private static final Pattern VERSION = Pattern.compile("WebLogic Deploy Tooling\\s+(\\S+)");

  private WdtArchive() {
  }

  static String fetch(URI source, Path destination, String expectedVersion) throws Exception {
    Files.createDirectories(destination.getParent());
    switch (source.getScheme()) {
      case "file" -> Files.copy(Path.of(source), destination);
      case "http", "https" -> download(source, destination);
      default -> throw new IllegalArgumentException("WDT URL must use http://, https:// or file://");
    }
    try (var zip = new ZipFile(destination.toFile())) {
      for (String name : new String[]{"VERSION.txt", "lib/weblogic-deploy-core.jar", "bin/createDomain.sh"}) {
        if (zip.getEntry("weblogic-deploy/" + name) == null) {
          throw new IOException("WDT ZIP is missing weblogic-deploy/" + name);
        }
      }
      String contents;
      try (var input = zip.getInputStream(zip.getEntry("weblogic-deploy/VERSION.txt"))) {
        contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      }
      var matcher = VERSION.matcher(contents);
      if (!matcher.find()) {
        throw new IOException("Cannot determine WDT version from VERSION.txt");
      }
      String version = matcher.group(1);
      if (!expectedVersion.isBlank() && !expectedVersion.equals(version)) {
        throw new IOException("Expected WDT " + expectedVersion + " but downloaded " + version);
      }
      return version;
    }
  }

  private static void download(URI source, Path destination) throws Exception {
    try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30)).build()) {
      var request = HttpRequest.newBuilder(source).timeout(Duration.ofMinutes(5)).GET().build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
      if (response.statusCode() != 200) {
        Files.deleteIfExists(destination);
        throw new IOException("WDT download returned HTTP " + response.statusCode());
      }
    }
  }
}
