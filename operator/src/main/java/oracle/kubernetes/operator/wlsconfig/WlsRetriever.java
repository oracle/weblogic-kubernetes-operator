// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerHealth;
import oracle.kubernetes.weblogic.domain.v1.SubsystemHealth;
import org.joda.time.DateTime;

/**
 * A helper class to retrieve configuration or health information from WebLogic servers. It also
 * contains method to perform configuration updates to a WebLogic domain.
 */
public class WlsRetriever {
  public static final String KEY = "wlsDomainConfig";

  private String namespace;
  private HttpClient httpClient;
  private String asServiceName;
  private String adminSecretName;

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // timeout for reading server configured for the cluster from admin server - default is 180s
  private static final int READ_CONFIG_TIMEOUT_MILLIS =
      Integer.getInteger("read.config.timeout.ms", 180000);

  // timeout for updating server configuration - default is 60s
  private static final int UPDATE_CONFIG_TIMEOUT_MILLIS =
      Integer.getInteger("update.config.timeout.ms", 60000);

  // wait time before retrying to read server configured for the cluster from admin server - default
  // is 1s
  private static final int READ_CONFIG_RETRY_MILLIS =
      Integer.getInteger("read.config.retry.ms", 1000);

  /**
   * Constructor.
   *
   * @param namespace The Namespace in which the target Domain is located.
   * @param asServiceName The name of the Kubernetes Service which provides access to the Admin
   *     Server.
   * @param adminSecretName The name of the Kubernetes Secret which contains the credentials to
   *     authenticate to the Admin Server.
   * @return The WlsRetriever object for the specified inputs.
   */
  public static WlsRetriever create(
      String namespace, String asServiceName, String adminSecretName) {
    return new WlsRetriever(namespace, asServiceName, adminSecretName);
  }

  /**
   * Constructor.
   *
   * @param namespace The Namespace in which the target Domain is located.
   * @param asServiceName The name of the Kubernetes Service which provides access to the Admin
   *     Server.
   * @param adminSecretName The name of the Kubernetes Secret which contains the credentials to
   *     authenticate to the Admin Server.
   */
  public WlsRetriever(String namespace, String asServiceName, String adminSecretName) {
    this.namespace = namespace;
    this.asServiceName = asServiceName;
    this.adminSecretName = adminSecretName;
  }

  private static final String START_TIME = "WlsRetriever-startTime";
  private static final String RETRY_COUNT = "WlsRetriever-retryCount";
  private static final Random R = new Random();
  private static final int HIGH = 50;
  private static final int LOW = 10;
  private static final int SCALE = 100;
  private static final int MAX = 10000;

  private enum RequestType {
    CONFIG,
    HEALTH
  }

  /**
   * Creates asynchronous {@link Step} to read configuration from an admin server
   *
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step readConfigStep(Step next) {
    return new ReadStep(RequestType.CONFIG, next);
  }

  /**
   * Creates asynchronous {@link Step} to read health from a server instance.
   *
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step readHealthStep(Step next) {
    return new ReadStep(RequestType.HEALTH, next);
  }

  private static final class ReadStep extends Step {
    private final RequestType requestType;

    public ReadStep(RequestType requestType, Step next) {
      super(next);
      this.requestType = requestType;
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

        String serverName;
        if (RequestType.CONFIG.equals(requestType)) {
          serverName = spec.getAsName();
        } else {
          serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
        }

        ServerKubernetesObjects sko = info.getServers().get(serverName);
        String adminSecretName =
            spec.getAdminSecret() == null ? null : spec.getAdminSecret().getName();

        Step getClient =
            HttpClient.createAuthenticatedClientForServer(
                namespace,
                adminSecretName,
                new WithHttpClientStep(requestType, sko.getService().get(), getNext()));
        packet.remove(RETRY_COUNT);
        return doNext(getClient, packet);
      } catch (Throwable t) {
        LOGGER.info(MessageKeys.EXCEPTION, t);
        // exponential back-off
        Integer retryCount = (Integer) packet.get(RETRY_COUNT);
        if (retryCount == null) {
          retryCount = 0;
        }
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);
        packet.put(RETRY_COUNT, retryCount);
        return doRetry(packet, waitTime, TimeUnit.MILLISECONDS);
      }
    }
  }

  /** Step for updating the cluster size of a WebLogic dynamic cluster */
  static final class UpdateDynamicClusterStep extends Step {

    final WlsClusterConfig wlsClusterConfig;
    final int targetClusterSize;

    /**
     * Constructor
     *
     * @param wlsClusterConfig The WlsClusterConfig object for the WebLogic dynamic cluster to be
     *     updated
     * @param targetClusterSize The target dynamic cluster size
     * @param next The next Step to be performed
     */
    public UpdateDynamicClusterStep(
        WlsClusterConfig wlsClusterConfig, int targetClusterSize, Step next) {
      super(next);
      this.wlsClusterConfig = wlsClusterConfig;
      this.targetClusterSize = targetClusterSize;
    }

    /** {@inheritDoc} */
    @Override
    public NextAction apply(Packet packet) {

      String clusterName = wlsClusterConfig == null ? "null" : wlsClusterConfig.getClusterName();

      if (wlsClusterConfig == null || !wlsClusterConfig.hasDynamicServers()) {
        LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_INVALID_CLUSTER, clusterName);
      } else {
        try {
          LOGGER.info(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_STARTING, clusterName, targetClusterSize);
          HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
          DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

          long startTime = System.currentTimeMillis();

          String serviceURL = HttpClient.getServiceURL(info.getAdmin().getService().get());

          boolean successful =
              updateDynamicClusterSizeWithServiceURL(
                  wlsClusterConfig, targetClusterSize, httpClient, serviceURL);

          if (successful) {
            LOGGER.info(
                MessageKeys.WLS_CLUSTER_SIZE_UPDATED,
                clusterName,
                targetClusterSize,
                (System.currentTimeMillis() - startTime));
          } else {
            LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_FAILED, clusterName, null);
          }
        } catch (Throwable t) {
          LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_FAILED, clusterName, t);
        }
      }
      return doNext(packet);
    }
  }

  private static final class WithHttpClientStep extends Step {
    private final RequestType requestType;
    private final V1Service service;

    public WithHttpClientStep(RequestType requestType, V1Service service, Step next) {
      super(next);
      this.requestType = requestType;
      this.service = service;
    }

    /** {@inheritDoc} */
    @Override
    public NextAction apply(Packet packet) {
      try {
        HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        Domain dom = info.getDomain();

        String serviceURL = HttpClient.getServiceURL(service);

        if (RequestType.CONFIG.equals(requestType)) {
          WlsDomainConfig wlsDomainConfig = null;
          String jsonResult =
              httpClient
                  .executePostUrlOnServiceClusterIP(
                      WlsDomainConfig.getRetrieveServersSearchUrl(),
                      serviceURL,
                      WlsDomainConfig.getRetrieveServersSearchPayload(),
                      true)
                  .getResponse();

          wlsDomainConfig = WlsDomainConfig.create(jsonResult);

          List<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();

          // This logs warning messages as well as returning a list of suggested
          // WebLogic configuration updates, but it does not update the DomainSpec.
          wlsDomainConfig.validate(dom.getSpec(), suggestedConfigUpdates);

          info.setScan(wlsDomainConfig);
          info.setLastScanTime(new DateTime());

          LOGGER.info(
              MessageKeys.WLS_CONFIGURATION_READ,
              (System.currentTimeMillis() - ((Long) packet.get(START_TIME))),
              wlsDomainConfig);

          // If there are suggested WebLogic configuration update, perform them as the
          // next Step, then read the updated WebLogic configuration again after the
          // update(s) are performed.
          if (!suggestedConfigUpdates.isEmpty()) {
            Step nextStep =
                new WithHttpClientStep(
                    requestType,
                    service,
                    getNext()); // read WebLogic config again after config updates
            for (ConfigUpdate suggestedConfigUpdate : suggestedConfigUpdates) {
              nextStep = suggestedConfigUpdate.createStep(nextStep);
            }
            return doNext(nextStep, packet);
          }

        } else { // RequestType.HEALTH
          String jsonResult =
              httpClient
                  .executePostUrlOnServiceClusterIP(
                      getRetrieveHealthSearchUrl(),
                      serviceURL,
                      getRetrieveHealthSearchPayload(),
                      true)
                  .getResponse();

          ObjectMapper mapper = new ObjectMapper();
          JsonNode root = mapper.readTree(jsonResult);

          JsonNode state = null;
          JsonNode subsystemName = null;
          JsonNode symptoms = null;
          JsonNode overallHealthState = root.path("overallHealthState");
          if (overallHealthState != null) {
            state = overallHealthState.path("state");
            subsystemName = overallHealthState.path("subsystemName");
            symptoms = overallHealthState.path("symptoms");
          }
          JsonNode activationTime = root.path("activationTime");

          List<String> sym = new ArrayList<>();
          if (symptoms != null) {
            Iterator<JsonNode> it = symptoms.elements();
            while (it.hasNext()) {
              sym.add(it.next().asText());
            }
          }

          String subName = null;
          if (subsystemName != null) {
            String s = subsystemName.asText();
            if (s != null && !"null".equals(s)) {
              subName = s;
            }
          }

          ServerHealth health =
              new ServerHealth()
                  .withOverallHealth(state != null ? state.asText() : null)
                  .withActivationTime(
                      activationTime != null ? new DateTime(activationTime.asLong()) : null);
          if (subName != null) {
            health
                .getSubsystems()
                .add(new SubsystemHealth().withSubsystemName(subName).withSymptoms(sym));
          }

          @SuppressWarnings("unchecked")
          ConcurrentMap<String, ServerHealth> serverHealthMap =
              (ConcurrentMap<String, ServerHealth>)
                  packet.get(ProcessingConstants.SERVER_HEALTH_MAP);
          serverHealthMap.put((String) packet.get(ProcessingConstants.SERVER_NAME), health);
        }

        return doNext(packet);
      } catch (Throwable t) {
        if (RequestType.CONFIG.equals(requestType)) {
          LOGGER.warning(MessageKeys.WLS_CONFIGURATION_READ_FAILED, t);
        } else {
          LOGGER.warning(
              MessageKeys.WLS_HEALTH_READ_FAILED, packet.get(ProcessingConstants.SERVER_NAME), t);
        }

        // exponential back-off
        Integer retryCount = (Integer) packet.get(RETRY_COUNT);
        if (retryCount == null) {
          retryCount = 0;
        }
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);
        packet.put(RETRY_COUNT, retryCount);
        return doRetry(packet, waitTime, TimeUnit.MILLISECONDS);
      }
    }
  }

  public static String getRetrieveHealthSearchUrl() {
    return "/management/weblogic/latest/serverRuntime/search";
  }

  // overallHealthState, healthState

  public static String getRetrieveHealthSearchPayload() {
    return "{ fields: [ 'overallHealthState', 'activationTime' ], links: [] }";
  }

  /**
   * Returns from admin server selected server configurations of all WLS servers configured in the
   * domain. The method would repeatedly try to connect to the admin server to retrieve the
   * configuration until the configured timeout occurs.
   *
   * @return A WlsClusterConfig object containing selected server configurations of all WLS servers
   *     configured in the domain that belongs to a cluster. This method returns an empty
   *     configuration object even if it fails to retrieve WLS configuration from the admin server.
   */
  public WlsDomainConfig readConfig() {

    LOGGER.entering();

    final long timeout = READ_CONFIG_TIMEOUT_MILLIS;
    ScheduledExecutorService executorService =
        ContainerResolver.getInstance().getContainer().getSPI(ScheduledExecutorService.class);
    long startTime = System.currentTimeMillis();
    Future<WlsDomainConfig> future = executorService.submit(() -> getWlsDomainConfig(timeout));
    WlsDomainConfig wlsConfig = null;
    try {
      wlsConfig = future.get(timeout, TimeUnit.MILLISECONDS);
      LOGGER.info(
          MessageKeys.WLS_CONFIGURATION_READ, (System.currentTimeMillis() - startTime), wlsConfig);
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
   * Method called by the Callable that is submitted from the readConfig method for reading the WLS
   * server configurations
   *
   * @param timeoutMillis Maximum amount of time in milliseconds to try to read configuration from
   *     the admin server before giving up
   * @return A WlsClusterConfig object containing selected server configurations of all WLS servers
   *     configured in the cluster. Method returns empty configuration object if timeout occurs
   *     before the configuration could be read from the admin server.
   * @throws Exception if an exception occurs in the attempt just prior to the timeout
   */
  private WlsDomainConfig getWlsDomainConfig(final long timeoutMillis) throws Exception {
    LOGGER.entering();

    WlsDomainConfig result = null;
    String jsonResult =
        executePostUrlWithRetry(
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
  private String executePostUrlWithRetry(
      final String url, final String payload, final long timeoutMillis) throws Exception {
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
        String serviceURL = connectAndGetServiceURL();
        jsonResult =
            httpClient.executePostUrlOnServiceClusterIP(url, serviceURL, payload).getResponse();
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
   * Static method to update the WebLogic dynamic cluster size configuration.
   *
   * @param wlsClusterConfig The WlsClusterConfig object of the WLS cluster whose cluster size needs
   *     to be updated. The caller should make sure that the cluster is a dynamic cluster.
   * @param targetClusterSize The target dynamic cluster size
   * @param httpClient HttpClient object for issuing the REST request
   * @param serviceURL service URL of the WebLogic admin server
   * @return true if the request to update the cluster size is successful, false if it was not
   *     successful
   */
  private static boolean updateDynamicClusterSizeWithServiceURL(
      final WlsClusterConfig wlsClusterConfig,
      final int targetClusterSize,
      final HttpClient httpClient,
      final String serviceURL) {
    LOGGER.entering();

    boolean result = false;
    // Update the dynamic cluster size of the WebLogic cluster
    String jsonResult =
        httpClient
            .executePostUrlOnServiceClusterIP(
                wlsClusterConfig.getUpdateDynamicClusterSizeUrl(),
                serviceURL,
                wlsClusterConfig.getUpdateDynamicClusterSizePayload(targetClusterSize))
            .getResponse();

    result = wlsClusterConfig.checkUpdateDynamicClusterSizeJsonResult(jsonResult);
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Connect to the WebLogic Administration Server and returns the service URL
   *
   * @return serviceURL for issuing HTTP requests to the admin server
   */
  String connectAndGetServiceURL() {
    if (httpClient == null) {
      httpClient = HttpClient.createAuthenticatedClientForServer(namespace, adminSecretName);
    }

    return HttpClient.getServiceURL(asServiceName, namespace);
  }
}
