// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.ProcessingConstants;
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

  // wait time before retrying to read server configured for the cluster from admin server - default is 1s
  private static final int READ_CONFIG_RETRY_MILLIS = Integer.getInteger("read.config.retry.ms", 1000);

  /**
   * Constructor.
   *
   * @param clientHelper The ClientHelper to be used to obtain the Kubernetes API Client.
   * @param namespace The Namespace in which the target Domain is located.
   * @param asServiceName The name of the Kubernetes Service which provides access to the Admin Server.
   * @param adminSecretName The name of the Kubernetes Secret which contains the credentials to authenticate to the Admin Server.
   * @return The WlsConfigRetriever object for the specified inputs.
   */
  public static WlsConfigRetriever create(ClientHelper clientHelper, String namespace, String asServiceName, String adminSecretName) {
    return new WlsConfigRetriever(clientHelper, namespace, asServiceName, adminSecretName);
  }

  /**
   * Constructor.
   *
   * @param clientHelper The ClientHelper to be used to obtain the Kubernetes API Client.
   * @param namespace The Namespace in which the target Domain is located.
   * @param asServiceName The name of the Kubernetes Service which provides access to the Admin Server.
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
        String principal = (String) packet.get(ProcessingConstants.PRINCIPAL);
        
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
            principal, namespace, adminSecretName, new WithHttpClientStep(namespace, adminServerServiceName, next));
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
        String jsonResult = httpClient.executePostUrlOnServiceClusterIP(WlsDomainConfig.getRetrieveServersSearchUrl(), serviceURL, adminServerServiceName, namespace, WlsDomainConfig.getRetrieveServersSearchPayload());
        if (jsonResult != null) {
          wlsDomainConfig = WlsDomainConfig.create().load(jsonResult);
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
      httpClient = HttpClient.createAuthenticatedClientForAdminServer(clientHolder, principal, namespace, adminSecretName);
    }
  }

}
