// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;

import io.kubernetes.client.util.SSLUtils;
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
import org.glassfish.jersey.server.ResourceConfig;

/**
 * The base class for the RestServer that runs the WebLogic operator's REST api and
 * WebhookRestServer that runs domain custom resource conversion webhook's REST api.
 */
public abstract class BaseRestServer {
  static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  static final int CORE_POOL_SIZE = 3;
  static final String SSL_PROTOCOL = "TLSv1.2";
  static final String[] SSL_PROTOCOLS = {
    SSL_PROTOCOL
  }; // ONLY support TLSv1.2 (by default, we would get TLSv1 and TLSv1.1 too)
  final RestConfig config;

  /**
   * Base constructor for the RestServer and WebhookRestServer.
   *
   * @param config - contains the REST server's configuration, which includes the hostnames and port
   *     numbers that the ports run on, the certificates and private keys for ssl, and the backend
   *     implementation that does the real work behind the REST api.
   */
  BaseRestServer(RestConfig config) {
    this.config = config;
  }

  /**
   * Defines a resource configuration.
   *
   * @param restConfig the operator or conversion webhook REST configuration
   * @return a resource configuration
   */
  abstract ResourceConfig createResourceConfig(RestConfig restConfig);

  ResourceConfig createResourceConfig() {
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
   * Starts WebLogic operator's or conversion webhook's REST api.
   *
   * @param container Container
   * @throws Exception if the REST api could not be started for reasons other than a port was not
   *     configured. When an exception is thrown, then none of the ports will be left running,
   *     however it is still OK to call stop (which will be a no-op).
   */
  public abstract void start(Container container) throws Exception;

  /**
   * Stops WebLogic operator's or conversion webhook's REST api.
   *
   * <p>Since it only stops ports that are running, it is safe to call this even if start threw an
   * exception or didn't start any ports because none were configured.
   */
  public abstract void stop();

  HttpServer createHttpsServer(Container container, SSLContext ssl, String uri)
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
            r -> {
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
            r -> {
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

  SSLContext createSslContext(KeyManager[] kms) throws Exception {
    SSLContext ssl = SSLContext.getInstance(SSL_PROTOCOL);
    ssl.init(kms, null, new SecureRandom());
    return ssl;
  }

  KeyManager[] createKeyManagers(
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

  boolean isSslConfigured(
      String certificateData, String certificateFile, String keyData, String keyFile) {
    // don't log keyData since it can contain sensitive data
    LOGGER.entering(certificateData, certificateFile, keyFile);
    boolean certConfigured = isPemConfigured(certificateData, certificateFile);
    boolean keyConfigured = isPemConfigured(keyData, keyFile);
    LOGGER.finer("certConfigured=" + certConfigured);
    LOGGER.finer("keyConfigured=" + keyConfigured);
    boolean result = (certConfigured && keyConfigured);
    LOGGER.exiting(result);
    return result;
  }

  boolean isPemConfigured(String data, String path) {
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