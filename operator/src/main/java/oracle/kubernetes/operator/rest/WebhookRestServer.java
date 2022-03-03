// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;

import io.kubernetes.client.util.SSLUtils;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.resource.ConversionWebhookResource;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;
import org.apache.commons.codec.binary.Base64;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;


/**
 * The Webhook RestServer that serves as endpoint for the conversion webhook for WebLogic operator.
 *
 */
public class WebhookRestServer {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  private static final int CORE_POOL_SIZE = 2;
  private static final String SSL_PROTOCOL = "TLSv1.2";
  private static final String[] SSL_PROTOCOLS = {
    SSL_PROTOCOL
  }; // ONLY support TLSv1.2 (by default, we would get TLSv1 and TLSv1.1 too)
  private static WebhookRestServer INSTANCE = null;
  private final WebhookRestConfig config;
  // private String baseHttpUri;
  private final String baseHttpsUri;
  private HttpServer httpsServer;

  /**
   * Constructs the conversion webhook REST server.
   *
   * @param config - contains the webhook REST server's configuration, which includes the hostnames and port
   *     numbers that the ports run on, the certificates and private keys for ssl, and the backend
   *     implementation that does the real work behind the REST api.
   */
  private WebhookRestServer(WebhookRestConfig config) {
    LOGGER.entering();
    this.config = config;
    baseHttpsUri = "https://" + config.getHost() + ":" + config.getHttpsPort();
    LOGGER.exiting();
  }

  /**
   * Create singleton instance of the conversion webhook's RestServer. Should only be called once.
   *
   * @param restConfig - the conversion webhook's REST configuration. Throws IllegalStateException if
   *     instance already created.
   */
  public static synchronized void create(WebhookRestConfig restConfig) {
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
   * Accessor for obtaining reference to the RestServer singleton instance.
   *
   * @return RestServer - Singleton instance of the RestServer
   */
  public static synchronized WebhookRestServer getInstance() {
    return INSTANCE;
  }

  /**
   * Release RestServer singleton instance. Should only be called once. Throws IllegalStateException
   * if singleton instance not created.
   */
  public static void destroy() {
    LOGGER.entering();
    try {
      if (INSTANCE != null) {
        INSTANCE = null;
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
  static ResourceConfig createResourceConfig(WebhookRestConfig restConfig) {
    ResourceConfig rc =
        new ResourceConfig()
            .register(JacksonFeature.class)
            .register(ErrorFilter.class)
            .register(RequestDebugLoggingFilter.class)
            .register(ResponseDebugLoggingFilter.class)
            .register(ExceptionMapper.class)
            .packages(ConversionWebhookResource.class.getPackageName());
    rc.setProperties(Map.of(WebhookRestConfig.WEBHOOK_REST_CONFIG_PROPERTY, restConfig));
    return rc;
  }

  private ResourceConfig createResourceConfig() {
    LOGGER.entering();

    ResourceConfig rc = createResourceConfig(config);

    LOGGER.exiting();
    return rc;
  }

  private static byte[] readFromDataOrFile(String data, String file) throws IOException {
    if (data != null && data.length() > 0) {
      return Base64.decodeBase64(data);
    }
    return Files.readAllBytes(new File(file).toPath());
  }

  /**
   * Returns the in-pod URI of the available https REST port.
   *
   * @return the uri
   */
  String getHttpsUri() {
    return baseHttpsUri;
  }

  /**
   * Starts Webhook REST api for WebLogic operator.
   *
   * <p>If a port has not been configured, then it logs that fact, does not start that port, and
   * continues (v.s. throwing an exception and not starting any ports).
   *
   * @param container Container
   * @throws Exception if the REST api could not be started for reasons other than a port was not
   *     configured. When an exception is thrown, then none of the ports will be leftrunning,
   *     however it is still OK to call stop (which will be a no-op).
   */
  public void start(Container container) throws Exception {
    LOGGER.entering();
    if (httpsServer != null) {
      throw new AssertionError("Already started");
    }
    boolean fullyStarted = false;
    try {
      httpsServer = createHttpsServer(container);
      LOGGER.info(
          "Started the Webhook REST server on "
              + getHttpsUri()
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
    if (httpsServer != null) {
      httpsServer.shutdownNow();
      httpsServer = null;
      LOGGER.fine("Stopped the webhook REST server");
    }
    LOGGER.exiting();
  }

  private HttpServer createHttpsServer(Container container) throws Exception {
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
            getHttpsUri());
    LOGGER.exiting();
    return result;
  }

  private HttpServer createHttpsServer(Container container, SSLContext ssl, String uri)
      throws Exception {
    HttpServer h =
        GrizzlyHttpServerFactory.createHttpServer(
            URI.create(uri),
            createResourceConfig(),
            true, // used for call
            // org.glassfish.jersey.grizzly2.httpserver.NetworkListener#setSecure(boolean)}.
            new SSLEngineConfigurator(ssl)
                .setClientMode(false)
                .setNeedClientAuth(false)
                .setEnabledProtocols(SSL_PROTOCOLS),
            false);

    // We discovered the default thread pool configuration was generating hundreds of
    // threads.  Tune it down to something more modest.  Note: these are core
    // pool sizes, so they can still grow if there is sufficient load.
    Collection<NetworkListener> nlc = h.getListeners();
    if (nlc != null) {
      for (NetworkListener nl : nlc) {
        TCPNIOTransport transport = nl.getTransport();
        ThreadPoolConfig t = transport.getWorkerThreadPoolConfig();
        if (t == null) {
          t = ThreadPoolConfig.defaultConfig();
          transport.setWorkerThreadPoolConfig(t);
        }
        t.setCorePoolSize(CORE_POOL_SIZE);
        ThreadFactory x = t.getThreadFactory();
        ThreadFactory tf = x != null ? x : Executors.defaultThreadFactory();
        t.setThreadFactory(
            (r) -> {
              Thread n =
                  tf.newThread(
                      () -> {
                        ContainerResolver.getDefault().enterContainer(container);
                        r.run();
                      });
              if (!n.isDaemon()) {
                n.setDaemon(true);
              }
              return n;
            });

        t = transport.getKernelThreadPoolConfig();
        if (t == null) {
          t = ThreadPoolConfig.defaultConfig();
          transport.setKernelThreadPoolConfig(t);
        }
        t.setCorePoolSize(CORE_POOL_SIZE);
        x = t.getThreadFactory();
        ThreadFactory tf2 = x != null ? x : Executors.defaultThreadFactory();
        t.setThreadFactory(
            (r) -> {
              Thread n =
                  tf2.newThread(
                      () -> {
                        ContainerResolver.getDefault().enterContainer(container);
                        r.run();
                      });
              if (!n.isDaemon()) {
                n.setDaemon(true);
              }
              return n;
            });
        transport.setSelectorRunnersCount(CORE_POOL_SIZE);
      }
    }

    h.start();
    return h;
  }

  private SSLContext createSslContext(KeyManager[] kms) throws Exception {
    SSLContext ssl = SSLContext.getInstance(SSL_PROTOCOL);
    ssl.init(kms, null, new SecureRandom());
    return ssl;
  }

  private KeyManager[] createKeyManagers(
      String certificateData, String certificateFile, String keyData, String keyFile)
      throws Exception {
    LOGGER.entering(certificateData, certificateFile);
    KeyManager[] result =
        SSLUtils.keyManagers(
            readFromDataOrFile(certificateData, certificateFile),
            readFromDataOrFile(keyData, keyFile),
            "", // Let utility figure it out, "RSA", // key algorithm
            "", // operator key passphrase in the temp keystore that gets created to hold the
            // keypair
            null, // file name of the temp keystore
            null // pass phrase of the temp keystore
            );
    LOGGER.exiting(result);
    return result;
  }
}
