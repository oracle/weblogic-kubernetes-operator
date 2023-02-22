// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.metrics;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet;
import oracle.kubernetes.operator.http.BaseServer;
import oracle.kubernetes.operator.work.Container;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.server.ResourceConfig;

public class MetricsServer extends BaseServer {
  public static final int DEFAULT_METRICS_PORT = 8083;

  private final AtomicReference<HttpServer> metricsHttpServer = new AtomicReference<>();
  private final int port;

  // for test
  public MetricsServer(int port) {
    this.port = port;
  }

  // for test
  public HttpServer getMetricsHttpServer() {
    return metricsHttpServer.get();
  }

  @Override
  public void start(Container container) throws IOException {
    DefaultExports.initialize();
    metricsHttpServer.set(createHttpServer(container, "http://0.0.0.0:" + port));
  }

  @Override
  public void stop() {
    Optional.ofNullable(metricsHttpServer.getAndSet(null)).ifPresent(HttpServer::shutdownNow);
  }

  @Override
  protected void configureServer(HttpServer h) {
    WebappContext webappContext = new WebappContext("metricscontex");
    webappContext.addServlet("MetricsServlet", new MetricsServlet()).addMapping("/metrics");
    webappContext.deploy(h);
  }

  @Override
  protected ResourceConfig createResourceConfig() {
    return new ResourceConfig();
  }
}
