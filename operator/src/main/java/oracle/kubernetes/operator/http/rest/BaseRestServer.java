// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import oracle.kubernetes.operator.http.BaseServer;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * The base class for the RestServer that runs the WebLogic operator's REST api and
 * WebhookRestServer that runs domain custom resource conversion webhook's REST api.
 */
public abstract class BaseRestServer extends BaseServer {
  protected static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  protected final RestConfig config;

  /**
   * Base constructor for the RestServer and WebhookRestServer.
   *
   * @param config - contains the REST server's configuration, which includes the hostnames and port
   *     numbers that the ports run on, the certificates and private keys for ssl, and the backend
   *     implementation that does the real work behind the REST api.
   */
  protected BaseRestServer(RestConfig config) {
    this.config = config;
  }

  /**
   * Defines a resource configuration.
   *
   * @param restConfig the operator or conversion webhook REST configuration
   * @return a resource configuration
   */
  protected abstract ResourceConfig createResourceConfig(RestConfig restConfig);

  /**
   * Defines a resource configuration by calling the abstract method with the config passed in the constructor.
   *
   * @return a resource configuration
   */
  public ResourceConfig createResourceConfig() {
    LOGGER.entering();

    ResourceConfig rc = createResourceConfig(config);

    LOGGER.exiting();
    return rc;
  }

}