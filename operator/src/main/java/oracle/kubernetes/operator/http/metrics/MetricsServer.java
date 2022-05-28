// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.metrics;

import java.io.IOException;

import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet;
import oracle.kubernetes.operator.http.BaseServer;
import oracle.kubernetes.operator.work.Container;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.server.ResourceConfig;

public class MetricsServer extends BaseServer {

  private HttpServer metricsHttpServer = null;

  @Override
  public void start(Container container) throws IOException {
    DefaultExports.initialize();
    metricsHttpServer = createHttpServer(container, "http://0.0.0.0:8083");
  }

  @Override
  public void stop() {
    metricsHttpServer.shutdownNow();
    metricsHttpServer = null;
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
