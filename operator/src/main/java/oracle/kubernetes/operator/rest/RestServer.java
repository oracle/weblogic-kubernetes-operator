// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import io.kubernetes.client.util.SSLUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
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
import org.glassfish.jersey.server.filter.CsrfProtectionFilter;

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
public class RestServer {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final int CORE_POOL_SIZE = 3;

  private RestConfig config;

  // private String baseHttpUri;
  private String baseExternalHttpsUri;
  private String baseInternalHttpsUri;

  HttpServer externalHttpsServer;
  HttpServer internalHttpsServer;

  private static final String SSL_PROTOCOL = "TLSv1.2";
  private static final String[] SSL_PROTOCOLS = {
    SSL_PROTOCOL
  }; // ONLY support TLSv1.2 (by default, we would get TLSv1 and TLSv1.1 too)

  /**
   * Constructs the WebLogic Operator REST server.
   *
   * @param config - contains the REST server's configuration, which includes the hostnames and port
   *     numbers that the ports run on, the certificates and private keys for ssl, and the backend
   *     implementation that does the real work behind the REST api.
   */
  public RestServer(RestConfig config) {
    LOGGER.entering();
    this.config = config;
    baseExternalHttpsUri = "https://" + config.getHost() + ":" + config.getExternalHttpsPort();
    baseInternalHttpsUri = "https://" + config.getHost() + ":" + config.getInternalHttpsPort();
    LOGGER.exiting();
  }

  /**
   * Returns the in-pod URI of the externally available https REST port.
   *
   * @return the uri
   */
  public String getExternalHttpsUri() {
    return baseExternalHttpsUri;
  }

  /**
   * Returns the in-pod URI of the externally available https REST port.
   *
   * @return the uri
   */
  public String getInternalHttpsUri() {
    return baseInternalHttpsUri;
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
  public void start(Container container) throws Exception {
    LOGGER.entering();
    if (externalHttpsServer != null || internalHttpsServer != null) {
      throw new AssertionError("Already started");
    }
    boolean fullyStarted = false;
    try {
      if (isExternalSSLConfigured()) {
        externalHttpsServer = createExternalHttpsServer(container);
        LOGGER.info(
            "Started the external ssl REST server on "
                + getExternalHttpsUri()
                + "/operator"); // TBD .fine ?
      } else {
        LOGGER.info(
            "Did not start the external ssl REST server because external ssl has not been configured.");
      }

      if (isInternalSSLConfigured()) {
        internalHttpsServer = createInternalHttpsServer(container);
        LOGGER.info(
            "Started the internal ssl REST server on "
                + getInternalHttpsUri()
                + "/operator"); // TBD .fine ?
      } else {
        LOGGER.info(
            "Did not start the internal ssl REST server because internal ssl has not been configured.");
      }

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
    if (externalHttpsServer != null) {
      externalHttpsServer.shutdownNow();
      externalHttpsServer = null;
      LOGGER.info("Stopped the external ssl REST server"); // TBD .fine ?
    }
    if (internalHttpsServer != null) {
      internalHttpsServer.shutdownNow();
      internalHttpsServer = null;
      LOGGER.info("Stopped the internal ssl REST server"); // TBD .fine ?
    }
    LOGGER.exiting();
  }

  private HttpServer createExternalHttpsServer(Container container) throws Exception {
    LOGGER.entering();
    HttpServer result =
        createHttpsServer(
            container,
            createSSLContext(
                createKeyManagers(
                    config.getOperatorExternalCertificateData(),
                    config.getOperatorExternalCertificateFile(),
                    config.getOperatorExternalKeyData(),
                    config.getOperatorExternalKeyFile())),
            getExternalHttpsUri());
    LOGGER.exiting();
    return result;
  }

  private HttpServer createInternalHttpsServer(Container container) throws Exception {
    LOGGER.entering();
    HttpServer result =
        createHttpsServer(
            container,
            createSSLContext(
                createKeyManagers(
                    config.getOperatorInternalCertificateData(),
                    config.getOperatorInternalCertificateFile(),
                    config.getOperatorInternalKeyData(),
                    config.getOperatorInternalKeyFile())),
            getInternalHttpsUri());
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

  private ResourceConfig createResourceConfig() {
    LOGGER.entering();
    // create a resource config that scans for JAX-RS resources and providers
    // in oracle.kubernetes.operator.rest package
    ResourceConfig rc =
        new ResourceConfig()
            .register(JacksonFeature.class)
            .register(CsrfProtectionFilter.class)
            .register(ErrorFilter.class)
            .register(AuthenticationFilter.class)
            .register(RequestDebugLoggingFilter.class)
            .register(ResponseDebugLoggingFilter.class)
            .register(ExceptionMapper.class)
            .packages("oracle.kubernetes.operator.rest.resource");
    Map<String, Object> extraProps = new HashMap<>();

    // attach the rest backend impl to the resource config
    // so that the resource impls can find it
    extraProps.put(RestConfig.REST_CONFIG_PROPERTY, config);
    rc.addProperties(extraProps);

    LOGGER.exiting();
    return rc;
  }

  private SSLContext createSSLContext(KeyManager[] kms) throws Exception {
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

  private static byte[] readFromDataOrFile(String data, String file) throws IOException {
    if (data != null && data.length() > 0) {
      return Base64.decodeBase64(data);
    }
    return Files.readAllBytes(new File(file).toPath());
  }

  private boolean isExternalSSLConfigured() {
    return isSSLConfigured(
        config.getOperatorExternalCertificateData(),
        config.getOperatorExternalCertificateFile(),
        config.getOperatorExternalKeyData(),
        config.getOperatorExternalKeyFile());
  }

  private boolean isInternalSSLConfigured() {
    return isSSLConfigured(
        config.getOperatorInternalCertificateData(),
        config.getOperatorInternalCertificateFile(),
        config.getOperatorInternalKeyData(),
        config.getOperatorInternalKeyFile());
  }

  private boolean isSSLConfigured(
      String certificateData, String certificateFile, String keyData, String keyFile) {
    // don't log keyData since it can contain sensitive data
    LOGGER.entering(certificateData, certificateFile, keyFile);
    boolean certConfigured = isPEMConfigured(certificateData, certificateFile);
    boolean keyConfigured = isPEMConfigured(keyData, keyFile);
    LOGGER.finer("certConfigured=" + certConfigured);
    LOGGER.finer("keyConfigured=" + keyConfigured);
    boolean result = (certConfigured && keyConfigured);
    LOGGER.exiting(result);
    return result;
  }

  private boolean isPEMConfigured(String data, String path) {
    boolean result = false;
    if (data != null && data.length() > 0) {
      result = true;
    } else if (path != null) {
      File f = new File(path);
      if (f.exists() && f.isFile()) {
        result = true;
      }
    }
    return result;
  }
}
