// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.joda.time.DateTime;

import io.kubernetes.client.models.V1ObjectMeta;

/**
 * A helper class to retrieve configuration information from WebLogic.
 */
public class WlsConfigRetriever {
  public static final String KEY = "wlsDomainConfig";

  private ClientHelper clientHelper;
  private String namespace;
  private HttpClient httpClient;
  private String asServiceName;
  private String adminSecretName;

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // timeout for reading server configured for the cluster from admin server - default is 180s
  private static final int READ_CONFIG_TIMEOUT_MILLIS = Integer.getInteger("read.config.timeout.ms", 180000);

  // timeout for updating server configuration - default is 60s
  private static final int UPDATE_CONFIG_TIMEOUT_MILLIS = Integer.getInteger("update.config.timeout.ms", 60000);

  // wait time before retrying to read server configured for the cluster from admin server - default is 1s
  private static final int READ_CONFIG_RETRY_MILLIS = Integer.getInteger("read.config.retry.ms", 1000);

  /**
   * Constructor.
   *
   * @param clientHelper    The ClientHelper to be used to obtain the Kubernetes API Client.
   * @param namespace       The Namespace in which the target Domain is located.
   * @param asServiceName   The name of the Kubernetes Service which provides access to the Admin Server.
   * @param adminSecretName The name of the Kubernetes Secret which contains the credentials to authenticate to the Admin Server.
   * @return The WlsConfigRetriever object for the specified inputs.
   */
  public static WlsConfigRetriever create(ClientHelper clientHelper, String namespace, String asServiceName, String adminSecretName) {
    return new WlsConfigRetriever(clientHelper, namespace, asServiceName, adminSecretName);
  }

  /**
   * Constructor.
   *
   * @param clientHelper    The ClientHelper to be used to obtain the Kubernetes API Client.
   * @param namespace       The Namespace in which the target Domain is located.
   * @param asServiceName   The name of the Kubernetes Service which provides access to the Admin Server.
   * @param adminSecretName The name of the Kubernetes Secret which contains the credentials to authenticate to the Admin Server.
   */
  public WlsConfigRetriever(ClientHelper clientHelper, String namespace, String asServiceName, String adminSecretName) {
    this.clientHelper = clientHelper;
    this.namespace = namespace;
    this.asServiceName = asServiceName;
    this.adminSecretName = adminSecretName;
  }

  /**
   * Creates asynchronous {@link Step} to read configuration from an admin server
   *
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step readConfigStep(Step next) {
    return new ReadConfigStep(next);
  }

  private static final String START_TIME = "WlsConfigRetriever-startTime";
  private static final String RETRY_COUNT = "WlsConfigRetriever-retryCount";
  private static final Random R = new Random();
  private static final int HIGH = 1000;
  private static final int LOW = 100;

  private static final class ReadConfigStep extends Step {
    public ReadConfigStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

        packet.putIfAbsent(START_TIME, Long.valueOf(System.currentTimeMillis()));

        Domain dom = info.getDomain();
        V1ObjectMeta meta = dom.getMetadata();
        DomainSpec spec = dom.getSpec();
        String namespace = meta.getNamespace();

        String domainUID = spec.getDomainUID();

        String name = CallBuilder.toDNS1123LegalName(domainUID + "-" + spec.getAsName());

        String adminSecretName = spec.getAdminSecret() == null ? null : spec.getAdminSecret().getName();
        String adminServerServiceName = name;

        Step getClient = HttpClient.createAuthenticatedClientForAdminServer(
                namespace, adminSecretName, new WithHttpClientStep(namespace, adminServerServiceName, next));
        packet.remove(RETRY_COUNT);
        return doNext(getClient, packet);
      } catch (Throwable t) {
        LOGGER.info(MessageKeys.EXCEPTION, t);

        // Not clear why we should have a maximum time to read the domain configuration.  We already know that the 
        // admin server Pod is READY and failing to read the config just means failure, so might as well keep trying.

        // exponential back-off
        Integer retryCount = (Integer) packet.get(RETRY_COUNT);
        if (retryCount == null) {
          retryCount = 0;
        }
        long waitTime = (2 << ++retryCount) * 1000 + (R.nextInt(HIGH - LOW) + LOW);
        packet.put(RETRY_COUNT, retryCount);
        return doRetry(packet, waitTime, TimeUnit.MILLISECONDS);
      }
    }
  }

  private static final class WithHttpClientStep extends Step {
    private final String namespace;
    private final String adminServerServiceName;

    public WithHttpClientStep(String namespace, String adminServerServiceName, Step next) {
      super(next);
      this.namespace = namespace;
      this.adminServerServiceName = adminServerServiceName;
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        Domain dom = info.getDomain();

        String serviceURL = HttpClient.getServiceURL(info.getAdmin().getService());

        WlsDomainConfig wlsDomainConfig = null;
        String jsonResult = httpClient.executePostUrlOnServiceClusterIP(WlsDomainConfig.getRetrieveServersSearchUrl(), serviceURL, WlsDomainConfig.getRetrieveServersSearchPayload());
        if (jsonResult != null) {
          wlsDomainConfig = WlsDomainConfig.create(jsonResult);
        }

        // validate domain spec against WLS configuration. Currently this only logs warning messages.
        wlsDomainConfig.updateDomainSpecAsNeeded(dom.getSpec());

        info.setScan(wlsDomainConfig);
        info.setLastScanTime(new DateTime());

        LOGGER.info(MessageKeys.WLS_CONFIGURATION_READ, (System.currentTimeMillis() - ((Long) packet.get(START_TIME))), wlsDomainConfig);

        return doNext(packet);
      } catch (Throwable t) {
        LOGGER.warning(MessageKeys.WLS_CONFIGURATION_READ_FAILED, t);

        // exponential back-off
        Integer retryCount = (Integer) packet.get(RETRY_COUNT);
        if (retryCount == null) {
          retryCount = 0;
        }
        long waitTime = (2 << ++retryCount) * 1000 + (R.nextInt(HIGH - LOW) + LOW);
        packet.put(RETRY_COUNT, retryCount);
        return doRetry(packet, waitTime, TimeUnit.MILLISECONDS);
      }
    }
  }

  /**
   * Returns from admin server selected server configurations of all WLS servers configured in the domain. The method
   * would repeatedly try to connect to the admin server to retrieve the configuration until the configured timeout
   * occurs.
   *
   * @return A WlsClusterConfig object containing selected server configurations of all WLS servers configured in the
   * domain that belongs to a cluster. This method returns an empty configuration object even if it fails to retrieve
   * WLS configuration from the admin server.
   */
  public WlsDomainConfig readConfig() {

    LOGGER.entering();

    final long timeout = READ_CONFIG_TIMEOUT_MILLIS;
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    long startTime = System.currentTimeMillis();
    Future<WlsDomainConfig> future = executorService.submit(() -> getWlsDomainConfig(timeout));
    executorService.shutdown();
    WlsDomainConfig wlsConfig = null;
    try {
      wlsConfig = future.get(timeout, TimeUnit.MILLISECONDS);
      LOGGER.info(MessageKeys.WLS_CONFIGURATION_READ, (System.currentTimeMillis() - startTime), wlsConfig);
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.warning(MessageKeys.WLS_CONFIGURATION_READ_FAILED, e);
    } catch (TimeoutException e) {
      LOGGER.warning(MessageKeys.WLS_CONFIGURATION_READ_TIMED_OUT, timeout);
    }
    if (wlsConfig == null) {
      wlsConfig = new WlsDomainConfig(null);
    }

    LOGGER.exiting(wlsConfig);
    return wlsConfig;
  }

  /**
   * Method called by the Callable that is submitted from the readConfig method for reading the WLS server
   * configurations
   *
   * @param timeoutMillis Maximum amount of time in milliseconds to try to read configuration from the admin server
   *                     before giving up
   *
   * @return A WlsClusterConfig object containing selected server configurations of all WLS servers configured in the
   * cluster. Method returns empty configuration object if timeout occurs before the configuration could be read from
   * the admin server.
   * @throws Exception if an exception occurs in the attempt just prior to the timeout
   */
  private WlsDomainConfig getWlsDomainConfig(final long timeoutMillis) throws Exception {
    LOGGER.entering();

    WlsDomainConfig result = null;
    String jsonResult = executePostUrlWithRetry(
            WlsDomainConfig.getRetrieveServersSearchUrl(),
            WlsDomainConfig.getRetrieveServersSearchPayload(),
            timeoutMillis);
    if (jsonResult != null) {
      result = WlsDomainConfig.create(jsonResult);
    }
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Invokes a HTTP POST request using the provided URL and payload, and retry the request until it
   * succeeded or the specified timeout period has reached.
   *
   * @param url The URL of the HTTP post request to be invoked
   * @param payload The payload of the HTTP Post request to be invoked
   * @param timeoutMillis Timeout in milliseconds
   * @return The Json string returned from the HTTP POST request
   * @throws Exception Any exception thrown while trying to invoke the HTTP POST request
   */
  private String executePostUrlWithRetry(final String url, final String payload, final long timeoutMillis)
          throws Exception {
    LOGGER.entering();

    long stopTime = System.currentTimeMillis() + timeoutMillis;
    Exception exception = null;
    String jsonResult = null;
    long timeRemaining = stopTime - System.currentTimeMillis();
    // keep trying and ignore exceptions until timeout.
    while (timeRemaining > 0 && jsonResult == null) {
      LOGGER.info(MessageKeys.WLS_CONFIGURATION_READ_TRYING, timeRemaining);
      exception = null;
      try {
        jsonResult = executePostUrl(url, payload);
      } catch (Exception e) {
        exception = e;
        LOGGER.info(MessageKeys.WLS_CONFIGURATION_READ_RETRY, e, READ_CONFIG_RETRY_MILLIS);
      }
      if (jsonResult == null) {
        try {
          // sleep before retrying
          Thread.sleep(READ_CONFIG_RETRY_MILLIS);
        } catch (InterruptedException ex) {
          // ignore
        }
      }
      timeRemaining = stopTime - System.currentTimeMillis();
    }
    if (jsonResult == null && exception != null) {
      LOGGER.throwing(exception);
      throw exception;
    }
    LOGGER.exiting(jsonResult);
    return jsonResult;
  }

  /**
   * Invokes a HTTP POST request using the provided URL and payload
   *
   * @param url The URL of the HTTP post request to be invoked
   * @param payload The payload of the HTTP Post request to be invoked
   *
   * @return The Json string returned from the HTTP POST request
   * @throws Exception Any exception thrown while invoking the HTTP POST request
   */
  private String executePostUrl(final String url, final String payload)
          throws Exception {
    LOGGER.entering();

    String jsonResult = null;
    ClientHolder client = null;
    try {
      client = clientHelper.take();

      connectAdminServer(client);
      jsonResult = httpClient.executePostUrlOnServiceClusterIP(url, client, asServiceName, namespace, payload);
    } finally {
      if (client != null)
        clientHelper.recycle(client);
    }
    LOGGER.exiting(jsonResult);
    return jsonResult;
  }
  /**
   * Update the dynamic cluster size of the WLS cluster configuration.
   *
   * @param wlsClusterConfig The WlsClusterConfig object of the WLS cluster whose cluster size needs to be updated
   * @param clusterSize The desire dynamic cluster size
   * @return true if the request to update the cluster size is successful, false if it was not successful within the
   *         time period, or the cluster is not a dynamic cluster
   */
  public boolean updateDynamicClusterSize(final WlsClusterConfig wlsClusterConfig, int clusterSize) {

    LOGGER.entering();

    final long timeout = UPDATE_CONFIG_TIMEOUT_MILLIS;

    String clusterName = wlsClusterConfig == null? "null": wlsClusterConfig.getClusterName();

    if (wlsClusterConfig == null || !wlsClusterConfig.hasDynamicServers()) {
      LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_INVALID_CLUSTER, clusterName);
      return false;
    }

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    long startTime = System.currentTimeMillis();
    Future<Boolean> future = executorService.submit(() -> doUpdateDynamicClusterSize(wlsClusterConfig, clusterSize));
    executorService.shutdown();
    boolean result = false;
    try {
      result = future.get(timeout, TimeUnit.MILLISECONDS);
      if (result) {
        LOGGER.info(MessageKeys.WLS_CLUSTER_SIZE_UPDATED, clusterName, clusterSize, (System.currentTimeMillis() - startTime));
      } else {
        LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_FAILED, clusterName,  null);
      }
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_FAILED, clusterName,  e);
    } catch (TimeoutException e) {
      LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_TIMED_OUT, clusterName, timeout);
    }
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Method called by the Callable that is submitted from the updateDynamicClusterSize method for updating the
   * WLS dynamic cluster size configuration.
   *
   * @param wlsClusterConfig The WlsClusterConfig object of the WLS cluster whose cluster size needs to be updated. The
   *                         caller should make sure that the cluster is a dynamic cluster.
   * @param clusterSize The desire dynamic cluster size
   * @return true if the request to update the cluster size is successful, false if it was not successful
   */

  private boolean doUpdateDynamicClusterSize(final WlsClusterConfig wlsClusterConfig,
                                             final int clusterSize) throws Exception {
    LOGGER.entering();

    final String EXPECTED_RESULT = "{}";

    String jsonResult = executePostUrl(
            wlsClusterConfig.getUpdateDynamicClusterSizeUrl(),
            wlsClusterConfig.getUpdateDynamicClusterSizePayload(clusterSize));
    boolean result = false;
    if (EXPECTED_RESULT.equals(jsonResult)) {
      result = true;
    } else {
      LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_FAILED, wlsClusterConfig.getClusterName(),  jsonResult);
    }
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Connect to the WebLogic Administration Server.
   *
   * @param clientHolder The ClientHolder from which to get the Kubernetes API client.
   */
  public void connectAdminServer(ClientHolder clientHolder) {
    if (httpClient == null) {
      httpClient = HttpClient.createAuthenticatedClientForAdminServer(clientHolder, namespace, adminSecretName);
    }
  }

}