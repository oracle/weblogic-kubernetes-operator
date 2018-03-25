// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerHealth;
import oracle.kubernetes.weblogic.domain.v1.SubsystemHealth;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;

/**
 * A helper class to retrieve configuration or health information from WebLogic servers.
 */
public class WlsRetriever {
  public static final String KEY = "wlsDomainConfig";
  
  private ClientHelper clientHelper;
  private String namespace;
  private HttpClient httpClient;
  private String asServiceName;
  private String adminSecretName;

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // timeout for reading server configured for the cluster from admin server - default is 180s
  private static final int READ_CONFIG_TIMEOUT_MILLIS = Integer.getInteger("read.config.timeout.ms", 180000);

  // wait time before retrying to read server configured for the cluster from admin server - default is 1s
  private static final int READ_CONFIG_RETRY_MILLIS = Integer.getInteger("read.config.retry.ms", 1000);

  /**
   * Constructor.
   *
   * @param clientHelper The ClientHelper to be used to obtain the Kubernetes API Client.
   * @param namespace The Namespace in which the target Domain is located.
   * @param asServiceName The name of the Kubernetes Service which provides access to the Admin Server.
   * @param adminSecretName The name of the Kubernetes Secret which contains the credentials to authenticate to the Admin Server.
   * @return The WlsRetriever object for the specified inputs.
   */
  public static WlsRetriever create(ClientHelper clientHelper, String namespace, String asServiceName, String adminSecretName) {
    return new WlsRetriever(clientHelper, namespace, asServiceName, adminSecretName);
  }

  /**
   * Constructor.
   *
   * @param clientHelper The ClientHelper to be used to obtain the Kubernetes API Client.
   * @param namespace The Namespace in which the target Domain is located.
   * @param asServiceName The name of the Kubernetes Service which provides access to the Admin Server.
   * @param adminSecretName The name of the Kubernetes Secret which contains the credentials to authenticate to the Admin Server.
   */
  public WlsRetriever(ClientHelper clientHelper, String namespace, String asServiceName, String adminSecretName) {
    this.clientHelper = clientHelper;
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
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step readConfigStep(Step next) {
    return new ReadStep(RequestType.CONFIG, next);
  }
  
  /**
   * Creates asynchronous {@link Step} to read health from a server instance.
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
        String principal = (String) packet.get(ProcessingConstants.PRINCIPAL);
        
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
        String adminSecretName = spec.getAdminSecret() == null ? null : spec.getAdminSecret().getName();
        
        Step getClient = HttpClient.createAuthenticatedClientForServer(
            principal, namespace, adminSecretName, 
            new WithHttpClientStep(requestType, namespace, sko.getService().get(), next));
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
  
  private static final class WithHttpClientStep extends Step {
    private final RequestType requestType;
    private final String namespace;
    private final V1Service service;

    public WithHttpClientStep(RequestType requestType, String namespace, V1Service service, Step next) {
      super(next);
      this.requestType = requestType;
      this.namespace = namespace;
      this.service = service;
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        Domain dom = info.getDomain();
  
        String serviceURL = HttpClient.getServiceURL(service);
        
        if (RequestType.CONFIG.equals(requestType)) {
          WlsDomainConfig wlsDomainConfig = null;
          String jsonResult = httpClient.executePostUrlOnServiceClusterIP(
              WlsDomainConfig.getRetrieveServersSearchUrl(), serviceURL, namespace, 
              WlsDomainConfig.getRetrieveServersSearchPayload());
          if (jsonResult != null) {
            wlsDomainConfig = WlsDomainConfig.create().load(jsonResult);
          }
    
          // validate domain spec against WLS configuration. Currently this only logs warning messages.
          wlsDomainConfig.updateDomainSpecAsNeeded(dom.getSpec());
    
          info.setScan(wlsDomainConfig);
          info.setLastScanTime(new DateTime());
    
          LOGGER.info(MessageKeys.WLS_CONFIGURATION_READ, (System.currentTimeMillis() - ((Long) packet.get(START_TIME))), wlsDomainConfig);
        } else { // RequestType.HEALTH
          String jsonResult = httpClient.executePostUrlOnServiceClusterIP(
              getRetrieveHealthSearchUrl(), serviceURL, namespace, 
              getRetrieveHealthSearchPayload());
          
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
          
          ServerHealth health = new ServerHealth()
              .withOverallHealth(state != null ? state.asText() : null)
              .withActivationTime(activationTime != null ? new DateTime(activationTime.asLong()) : null);
          if (subName != null) {
            health.getSubsystems().add(new SubsystemHealth()
                .withSubsystemName(subName)
                .withSymptoms(sym));
          }
          
          @SuppressWarnings("unchecked")
          ConcurrentMap<String, ServerHealth> serverHealthMap = (ConcurrentMap<String, ServerHealth>) packet
              .get(ProcessingConstants.SERVER_HEALTH_MAP);
          serverHealthMap.put((String) packet.get(ProcessingConstants.SERVER_NAME), health);
        }

        return doNext(packet);
      } catch (Throwable t) {
        LOGGER.warning(MessageKeys.WLS_CONFIGURATION_READ_FAILED, t);
        
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
   * Returns from admin server selected server configurations of all WLS servers configured in the domain. The method
   * would repeatedly try to connect to the admin server to retrieve the configuration until the configured timeout
   * occurs.
   *
   * @param principal The principal that should be used to retrieve the configuration.
   *
   * @return A WlsClusterConfig object containing selected server configurations of all WLS servers configured in the
   * domain that belongs to a cluster. This method returns an empty configuration object even if it fails to retrieve
   * WLS configuration from the admin server.
   */
  public WlsDomainConfig readConfig(String principal) {

    LOGGER.entering();

    long timeout = READ_CONFIG_TIMEOUT_MILLIS;
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    long startTime = System.currentTimeMillis();
    Future<WlsDomainConfig> future = executorService.submit(() -> getWlsDomainConfig(principal, timeout));
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
      wlsConfig = WlsDomainConfig.create();
    }

    LOGGER.exiting(wlsConfig);
    return wlsConfig;
  }

  /**
   * Method called by the Callable that is submitted from the readConfig method for reading the WLS server
   * configurations
   *
   * @param principal The Service Account or User to use when accessing WebLogic.
   * @param timeout Maximum amount of time in millis to try to read configuration from the admin server before giving up
   * @return A WlsClusterConfig object containing selected server configurations of all WLS servers configured in the
   * cluster. Method returns empty configuration object if timeout occurs before the configuration could be read from
   * the admin server.
   * @throws Exception if an exception occurs in the attempt just prior to the timeout
   */
  private WlsDomainConfig getWlsDomainConfig(String principal, long timeout) throws Exception {
    LOGGER.entering();

    long stopTime = System.currentTimeMillis() + timeout;
    Exception exception = null;
    WlsDomainConfig result = null;
    long timeRemaining = stopTime - System.currentTimeMillis();
    // keep trying and ignore exceptions until timeout.
    while (timeRemaining > 0 && result == null) {
      LOGGER.info(MessageKeys.WLS_CONFIGURATION_READ_TRYING, timeRemaining);
      exception = null;
      ClientHolder client = null;
      try {
        client = clientHelper.take();
        
        connectAdminServer(client, principal);
          String jsonResult = httpClient.executePostUrlOnServiceClusterIP(WlsDomainConfig.getRetrieveServersSearchUrl(), client, asServiceName, namespace, WlsDomainConfig.getRetrieveServersSearchPayload());
        if (jsonResult != null) {
          result = WlsDomainConfig.create().load(jsonResult);
        }
      } catch (Exception e) {
        exception = e;
        LOGGER.info(MessageKeys.WLS_CONFIGURATION_READ_RETRY, e, READ_CONFIG_RETRY_MILLIS);
      } finally {
        if (client != null)
          clientHelper.recycle(client);
      }
      try {
        // sleep before retrying
        Thread.sleep(READ_CONFIG_RETRY_MILLIS);
      } catch (InterruptedException ex) {
        // ignore
      }
      timeRemaining = stopTime - System.currentTimeMillis();
    }
    if (result == null && exception != null) {
      LOGGER.throwing(exception);
      throw exception;
    }
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Connect to the WebLogic Administration Server.
   *
   * @param clientHolder The ClientHolder from which to get the Kubernetes API client.
   * @param principal The principal that should be used to connect to the Admin Server.
   */
  public void connectAdminServer(ClientHolder clientHolder, String principal) {
    if (httpClient == null) {
      httpClient = HttpClient.createAuthenticatedClientForServer(clientHolder, principal, namespace, adminSecretName);
    }
  }

}
