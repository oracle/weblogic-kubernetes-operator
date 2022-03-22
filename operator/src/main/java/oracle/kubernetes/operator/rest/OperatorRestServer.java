// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.Map;

import oracle.kubernetes.operator.rest.resource.VersionsResource;
import oracle.kubernetes.operator.work.Container;
import org.glassfish.grizzly.http.server.HttpServer;
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
public class OperatorRestServer extends BaseRestServer {
  private final String baseExternalHttpsUri;
  private final String baseInternalHttpsUri;
  private HttpServer externalHttpsServer;
  private HttpServer internalHttpsServer;

  /**
   * Constructs the WebLogic Operator REST server.
   *
   * @param config - contains the REST server's configuration, which includes the hostnames and port
   *     numbers that the ports run on, the certificates and private keys for ssl, and the backend
   *     implementation that does the real work behind the REST api.
   */
  OperatorRestServer(RestConfig config) {
    super(config);
    LOGGER.entering();
    baseExternalHttpsUri = "https://" + config.getHost() + ":" + config.getExternalHttpsPort();
    baseInternalHttpsUri = "https://" + config.getHost() + ":" + config.getInternalHttpsPort();
    LOGGER.exiting();
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
        INSTANCE = new OperatorRestServer(restConfig);
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
            .register(CsrfProtectionFilter.class)
            .register(AuthenticationFilter.class)
            .packages(VersionsResource.class.getPackageName());
    rc.setProperties(Map.of(RestConfig.REST_CONFIG_PROPERTY, restConfig));
    return rc;
  }

  /**
   * Returns the in-pod URI of the externally available https REST port.
   *
   * @return the uri
   */
  String getExternalHttpsUri() {
    return baseExternalHttpsUri;
  }

  /**
   * Returns the in-pod URI of the externally available https REST port.
   *
   * @return the uri
   */
  String getInternalHttpsUri() {
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
  @Override
  public void start(Container container) throws Exception {
    LOGGER.entering();
    if (externalHttpsServer != null || internalHttpsServer != null) {
      throw new AssertionError("Already started");
    }
    boolean fullyStarted = false;
    try {
      if (isExternalSslConfigured()) {
        externalHttpsServer = createExternalHttpsServer(container);
        LOGGER.info(
            "Started the external ssl REST server on "
                + getExternalHttpsUri()
                + "/operator"); // TBD .fine ?
      }

      if (isInternalSslConfigured()) {
        internalHttpsServer = createInternalHttpsServer(container);
        LOGGER.info(
            "Started the internal ssl REST server on "
                + getInternalHttpsUri()
                + "/operator"); // TBD .fine ?
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
      LOGGER.fine("Stopped the external ssl REST server");
    }
    if (internalHttpsServer != null) {
      internalHttpsServer.shutdownNow();
      internalHttpsServer = null;
      LOGGER.fine("Stopped the internal ssl REST server");
    }
    LOGGER.exiting();
  }

  private HttpServer createExternalHttpsServer(Container container) throws Exception {
    LOGGER.entering();
    HttpServer result =
        createHttpsServer(
            container,
            createSslContext(
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
            createSslContext(
                createKeyManagers(
                    config.getOperatorInternalCertificateData(),
                    config.getOperatorInternalCertificateFile(),
                    config.getOperatorInternalKeyData(),
                    config.getOperatorInternalKeyFile())),
            getInternalHttpsUri());
    LOGGER.exiting();
    return result;
  }

  private boolean isExternalSslConfigured() {
    return isSslConfigured(
        config.getOperatorExternalCertificateData(),
        config.getOperatorExternalCertificateFile(),
        config.getOperatorExternalKeyData(),
        config.getOperatorExternalKeyFile());
  }

  private boolean isInternalSslConfigured() {
    return isSslConfigured(
        config.getOperatorInternalCertificateData(),
        config.getOperatorInternalCertificateFile(),
        config.getOperatorInternalKeyData(),
        config.getOperatorInternalKeyFile());
  }
}
