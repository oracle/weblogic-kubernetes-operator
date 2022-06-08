// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import oracle.kubernetes.operator.http.rest.BaseRestServer;
import oracle.kubernetes.operator.http.rest.ErrorFilter;
import oracle.kubernetes.operator.http.rest.ExceptionMapper;
import oracle.kubernetes.operator.http.rest.RequestDebugLoggingFilter;
import oracle.kubernetes.operator.http.rest.ResponseDebugLoggingFilter;
import oracle.kubernetes.operator.http.rest.RestConfig;
import oracle.kubernetes.operator.webhooks.resource.AdmissionWebhookResource;
import oracle.kubernetes.operator.webhooks.resource.ConversionWebhookResource;
import oracle.kubernetes.operator.work.Container;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * The WebhookRestServer that serves as an endpoint for the domain custom resource conversion webhook.
 *
 * <p>It provides the following ports that host the conversion webhook's REST api:
 *
 * <ul>
 *   <li>webhook https port - this port can only be used inside of a Kubernetes cluster since its
 *       SSL certificate contains the the in-cluster hostnames for contacting this port.
 * </ul>
 */
public class WebhookRestServer extends BaseRestServer {
  private final String baseWebhookHttpsUri;
  private final AtomicReference<HttpServer> webhookHttpsServer = new AtomicReference<>();

  /**
   * Constructs the conversion webhook REST server.
   *
   * @param config - contains the webhook REST server's configuration, which includes the hostnames and port
   *     numbers that the ports run on, the certificates and private keys for ssl, and the backend
   *     implementation that does the real work behind the REST api.
   */
  public WebhookRestServer(RestConfig config) {
    super(config);
    LOGGER.entering();
    baseWebhookHttpsUri = "https://" + config.getHost() + ":" + config.getWebhookHttpsPort();
    LOGGER.exiting();
  }

  /**
   * Create an instance of the conversion webhook's RestServer.
   *
   * @param restConfig - the conversion webhook's REST configuration.
   * @return Conversion webhook RestServer
   */
  public static WebhookRestServer create(RestConfig restConfig) {
    LOGGER.entering();
    try {
      return new WebhookRestServer(restConfig);
    } finally {
      LOGGER.exiting();
    }
  }

  /**
   * Defines a resource configuration that scans for JAX-RS resources and providers in the REST
   * package.
   *
   * @param restConfig the conversion webhook REST configuration
   * @return a resource configuration
   */
  @Override
  protected ResourceConfig createResourceConfig(RestConfig restConfig) {
    return new ResourceConfig()
        .register(JacksonFeature.class)
        .register(ErrorFilter.class)
        .register(RequestDebugLoggingFilter.class)
        .register(ResponseDebugLoggingFilter.class)
        .register(ExceptionMapper.class)
        .packages(ConversionWebhookResource.class.getPackageName(), AdmissionWebhookResource.class.getPackageName())
        .setProperties(Map.of(RestConfig.REST_CONFIG_PROPERTY, restConfig));
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
   * Starts conversion webhook's REST api.
   *
   * @param container Container
   * @throws IOException if the REST api could not be started.
   *     When an exception is thrown, then none of the ports will be left running,
   *     however it is still OK to call stop (which will be a no-op).
   * @throws UnrecoverableKeyException Unrecoverable key
   * @throws CertificateException Bad certificate
   * @throws NoSuchAlgorithmException No such algorithm
   * @throws KeyStoreException Bad keystore
   * @throws InvalidKeySpecException Invalid key
   * @throws KeyManagementException Key management failed
   */
  @Override
  public void start(Container container)
      throws UnrecoverableKeyException, CertificateException, IOException, NoSuchAlgorithmException,
      KeyStoreException, InvalidKeySpecException, KeyManagementException {
    LOGGER.entering();
    boolean fullyStarted = false;
    try {
      webhookHttpsServer.set(createWebhookHttpsServer(container));
      LOGGER.info(
              "Started the webhook ssl REST server on "
                      + getWebhookHttpsUri());
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
   * Stops conversion webhook's REST api.
   *
   * <p>Since it only stops ports that are running, it is safe to call this even if start threw an
   * exception or didn't start any ports because none were configured.
   */
  public void stop() {
    LOGGER.entering();
    Optional.ofNullable(webhookHttpsServer.getAndSet(null)).ifPresent(HttpServer::shutdownNow);
    LOGGER.exiting();
  }

  private HttpServer createWebhookHttpsServer(Container container)
      throws UnrecoverableKeyException, CertificateException, IOException, NoSuchAlgorithmException,
      KeyStoreException, InvalidKeySpecException, KeyManagementException {
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
