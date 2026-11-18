// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.offline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the WDT URL contract without downloading a release or running a container. */
class OfflineIntrospectionDownloadIT {
  @TempDir
  Path temporary;

  @Test
  void downloadsHttpZipThroughRedirect() throws Exception {
    byte[] zip = archive();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().add("Location", "/wdt.zip");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/wdt.zip", exchange -> {
      exchange.sendResponseHeaders(200, zip.length);
      try (var output = exchange.getResponseBody()) {
        output.write(zip);
      }
    });
    server.createContext("/missing", exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      assertEquals("4.4.6", WdtArchive.fetch(URI.create(base + "/redirect"), temporary.resolve("wdt.zip"), "4.4.6"));
      var error = assertThrows(IOException.class, () ->
          WdtArchive.fetch(URI.create(base + "/missing"), temporary.resolve("missing.zip"), ""));
      assertTrue(error.getMessage().contains("HTTP 404"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void copiesFileUrlAndChecksVersion() throws Exception {
    Path source = Files.write(temporary.resolve("source.zip"), archive());
    assertEquals("4.4.6", WdtArchive.fetch(source.toUri(), temporary.resolve("copy.zip"), ""));
    var error = assertThrows(IOException.class, () ->
        WdtArchive.fetch(source.toUri(), temporary.resolve("wrong-version.zip"), "4.4.5"));
    assertTrue(error.getMessage().contains("Expected WDT 4.4.5 but downloaded 4.4.6"));
  }

  @Test
  void rejectsNonWdtZip() throws Exception {
    Path source = Files.writeString(temporary.resolve("invalid.zip"), "not a zip");
    assertThrows(IOException.class, () -> WdtArchive.fetch(source.toUri(), temporary.resolve("copy.zip"), ""));
  }

  private byte[] archive() throws IOException {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (String name : new String[]{"VERSION.txt", "lib/weblogic-deploy-core.jar", "bin/createDomain.sh"}) {
        zip.putNextEntry(new ZipEntry("weblogic-deploy/" + name));
        if (name.equals("VERSION.txt")) {
          zip.write("WebLogic Deploy Tooling 4.4.6".getBytes(StandardCharsets.UTF_8));
        }
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }
}
