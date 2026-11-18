// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.offline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Runs the production introspector in an isolated Docker or Podman container. */
final class OfflineContainer implements AutoCloseable {
  private static final String SETUP = """
      set -e
      mkdir -p /weblogic-operator/scripts /weblogic-operator/secrets \
        /weblogic-operator/model-runtime-secret /weblogic-operator/wdt-config-map \
        /weblogic-operator/introspector /weblogic-operator/introspectormii \
        /weblogic-operator/introspectormd5 /tmp/offline-wdt /tmp/offline-models \
        /tmp/offline-domain /tmp/offline-log /tmp/offline-nm
      cp -R /tmp/offline-input/scripts/. /weblogic-operator/scripts/
      cp /tmp/offline-input/model.yaml /tmp/offline-models/model.yaml
      cp /tmp/offline-input/username /tmp/offline-input/password /weblogic-operator/secrets/
      cp /tmp/offline-input/runtime-password /weblogic-operator/model-runtime-secret/password
      unzip -q /tmp/offline-input/weblogic-deploy.zip -d /tmp/offline-wdt
      chmod -R a+rwX /weblogic-operator /tmp/offline-wdt /tmp/offline-models \
        /tmp/offline-domain /tmp/offline-log /tmp/offline-nm
      chmod a+x /weblogic-operator/scripts/*.sh /tmp/offline-wdt/weblogic-deploy/bin/*.sh
      """;
  private final String engine;
  private final String name = "wko-offline-" + UUID.randomUUID();
  private final Path results;
  private final long timeout;

  OfflineContainer(String engine, Path results, long timeout) {
    this.engine = engine;
    this.results = results;
    this.timeout = timeout;
  }

  String resolveImage(String image) throws Exception {
    if (run("image-inspect", "image", "inspect", "--format", "{{.Id}}", image) != 0) {
      checked("image-pull", "pull", image);
      checked("image-inspect", "image", "inspect", "--format", "{{.Id}}", image);
    }
    return Files.readString(results.resolve("image-inspect.log")).strip();
  }

  String version(String image) throws Exception {
    var args = new ArrayList<>(List.of("run", "--rm", "--name", name));
    addBuildLabel(args);
    args.addAll(List.of("--entrypoint", "/bin/bash", image, "-c",
        "java -cp \"$ORACLE_HOME/wlserver/server/lib/weblogic.jar\" weblogic.version"));
    checked("wls-version", args.toArray(String[]::new));
    return Files.readString(results.resolve("wls-version.log"));
  }

  int introspect(String image, Path input) throws Exception {
    var args = new ArrayList<>(List.of("create", "--name", name, "--entrypoint", "/bin/bash"));
    addBuildLabel(args);
    for (String env : List.of("DOMAIN_UID=offline-test", "NAMESPACE=offline-test",
        "DOMAIN_HOME=/tmp/offline-domain", "NODEMGR_HOME=/tmp/offline-nm", "WDT_DOMAIN_TYPE=WLS",
        "WDT_MODEL_HOME=/tmp/offline-models", "WDT_INSTALL_HOME=/tmp/offline-wdt/weblogic-deploy",
        "DOMAIN_SOURCE_TYPE=FromModel", "OPERATOR_ENVVAR_NAMES=d",
        "CREDENTIALS_SECRET_NAME=weblogic-credentials", "CREDENTIALS_SECRET_PATH=/weblogic-operator/secrets",
        "LOG_HOME=/tmp/offline-log", "USER_MEM_ARGS=-Xms64m -Xmx512m",
        "NODEMGR_MEM_ARGS=-Xms32m -Xmx128m", "ISTIO_ENABLED=false")) {
      args.addAll(List.of("-e", env));
    }
    args.addAll(List.of(image, "-c", "sleep infinity"));
    checked("create", args.toArray(String[]::new));
    checked("copy-input", "cp", input.toAbsolutePath() + "/.", name + ":/tmp/offline-input");
    checked("start", "start", name);
    checked("setup", "exec", "--user", "0", name, "/bin/bash", "-c", SETUP);
    return run("introspector", "exec", name, "/bin/bash", "/weblogic-operator/scripts/introspectDomain.sh");
  }

  int copyTopology() throws Exception {
    return run("copy-topology", "cp", name + ":/tmp/introspect/offline-test/topology.yaml",
        results.resolve("topology.yaml").toString());
  }

  int copyDomainConfig() throws Exception {
    return run("copy-config", "cp", name + ":/tmp/offline-domain/config/config.xml",
        results.resolve("config.xml").toString());
  }

  private void addBuildLabel(List<String> arguments) {
    String buildId = System.getenv("WKO_OFFLINE_BUILD_ID");
    if (buildId != null && !buildId.isBlank()) {
      arguments.addAll(List.of("--label", "oracle.weblogic.offline.build=" + buildId));
    }
  }

  private void checked(String stage, String... arguments) throws Exception {
    int exit = run(stage, arguments);
    if (exit != 0) {
      throw new IOException(engine + " " + stage + " exited " + exit + "; see " + results.resolve(stage + ".log"));
    }
  }

  private int run(String stage, String... arguments) throws IOException, InterruptedException {
    Files.createDirectories(results);
    var command = new ArrayList<>(List.of(engine));
    command.addAll(Arrays.asList(arguments));
    var process = new ProcessBuilder(command).redirectErrorStream(true)
        .redirectOutput(results.resolve(stage + ".log").toFile()).start();
    try {
      if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
        throw new IOException(engine + " " + stage + " timed out; see " + results.resolve(stage + ".log"));
      }
      return process.exitValue();
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  @Override
  public void close() throws IOException {
    // Remove only this test's unique container, including when the CLI times out.
    try {
      run("cleanup", "rm", "-f", name);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while removing " + name, e);
    }
  }
}
