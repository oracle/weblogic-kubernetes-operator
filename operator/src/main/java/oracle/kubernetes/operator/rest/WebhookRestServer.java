// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.Map;

import oracle.kubernetes.operator.rest.resource.ConversionWebhookResource;
import oracle.kubernetes.operator.work.Container;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * The RestServer runs the WebLogic operator's REST api.
 *
 * <p>It provides the following ports that host the WebLogic operator's REST api:
 *
 * <ul>
 *   <li>external http port - this port can be used both inside and outside of a Kubernetes cluster.
 *   <li>external https port - this port can be only be used outside of a Kubernetes cluster since
 *       its SSL certificate contains the external hostnames for contacting this port.
 *   <li>internal https port - this port can only be used inside of a Kubernetes cluster since its
 *       SSL certificate contains the the in-cluster hostnames for contacting this port.
 * </ul>
 */
public class WebhookRestServer extends BaseRestServer {
  private final String baseWebhookHttpsUri;
  private HttpServer webhookHttpsServer;

  /**
   * Constructs the WebLogic Operator REST server.
   *
   * @param config - contains the REST server's configuration, which includes the hostnames and port
   *     numbers that the ports run on, the certificates and private keys for ssl, and the backend
   *     implementation that does the real work behind the REST api.
   */
  private WebhookRestServer(RestConfig config) {
    super(config);
    baseWebhookHttpsUri = "https://" + config.getHost() + ":" + config.getWebhookHttpsPort();
  }

  /**
   * Create singleton instance of the WebLogic Operator's RestServer. Should only be called once.
   *
   * @param restConfig - the WebLogic Operator's REST configuration. Throws IllegalStateException if
   *     instance already created.
   */
  public static synchronized void create(RestConfig restConfig) {
    LOGGER.entering();
    try {
      if (INSTANCE == null) {
        INSTANCE = new WebhookRestServer(restConfig);
        return;
      }

      throw new IllegalStateException();
    } finally {
      LOGGER.exiting();
    }
  }

  /**
   * Defines a resource configuration that scans for JAX-RS resources and providers in the REST
   * package.
   *
   * @param restConfig the operator REST configuration
   * @return a resource configuration
   */
  @Override
  ResourceConfig createResourceConfig(RestConfig restConfig) {
    ResourceConfig rc =
        new ResourceConfig()
            .register(JacksonFeature.class)
            .register(ErrorFilter.class)
            .register(RequestDebugLoggingFilter.class)
            .register(ResponseDebugLoggingFilter.class)
            .register(ExceptionMapper.class)
            .packages(ConversionWebhookResource.class.getPackageName())
            .setProperties(Map.of(RestConfig.REST_CONFIG_PROPERTY, restConfig));
    return rc;
  }

  /**
   * Returns the in-pod URI of the externally available https REST port.
   *
   * @return the uri
   */
  String getWebhookHttpsUri() {
    return baseWebhookHttpsUri;
  }

  /**
   * Starts WebLogic operator's REST api.
   *
   * <p>If a port has not been configured, then it logs that fact, does not start that port, and
   * continues (v.s. throwing an exception and not starting any ports).
   *
   * @param container Container
   * @throws Exception if the REST api could not be started for reasons other than a port was not
   *     configured. When an exception is thrown, then none of the ports will be leftrunning,
   *     however it is still OK to call stop (which will be a no-op).
   */
  @Override
  public void start(Container container) throws Exception {
    LOGGER.entering();
    boolean fullyStarted = false;
    try {
      webhookHttpsServer = createWebhookHttpsServer(container);
      LOGGER.info(
              "Started the webhook ssl REST server on "
                      + getWebhookHttpsUri()
                      + "/webhook"); // TBD .fine ?
      fullyStarted = true;
    } finally {
      if (!fullyStarted) {
        // if we didn't get a chance to start all of the ports because an exception
        // was thrown, then stop the ones we did manage to start
        stop();
      }
    }
    LOGGER.exiting();
  }

  /**
   * Stops WebLogic operator's REST api.
   *
   * <p>Since it only stops ports that are running, it is safe to call this even if start threw an
   * exception or didn't start any ports because none were configured.
   */
  public void stop() {
    LOGGER.entering();
    if (webhookHttpsServer != null) {
      webhookHttpsServer.shutdownNow();
      webhookHttpsServer = null;
      LOGGER.fine("Stopped the webhook ssl REST server");
    }
    LOGGER.exiting();
  }

  private HttpServer createWebhookHttpsServer(Container container) throws Exception {
    LOGGER.entering();
    HttpServer result =
            createHttpsServer(
                    container,
                    createSslContext(
                            createKeyManagers(
                                    config.getWebhookCertificateData(),
                                    config.getWebhookCertificateFile(),
                                    config.getWebhookKeyData(),
                                    config.getWebhookKeyFile())),
                    getWebhookHttpsUri());
    LOGGER.exiting();
    return result;
  }
}
