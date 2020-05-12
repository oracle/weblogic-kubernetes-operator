// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.HttpClientPool;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.http.Result;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.LoggingFilter;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.SubsystemHealth;
import org.joda.time.DateTime;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;

public class ReadHealthStep extends Step {

  private static final String HTTP_PROTOCOL = "http://";
  private static final String HTTPS_PROTOCOL = "https://";
  public static final String OVERALL_HEALTH_NOT_AVAILABLE = "Not available";
  public static final String OVERALL_HEALTH_FOR_SERVER_OVERLOADED =
      OVERALL_HEALTH_NOT_AVAILABLE + " (possibly overloaded)";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final Integer HTTP_TIMEOUT_SECONDS = 60;

  private ReadHealthStep(Step next) {
    super(next);
  }

  /**
   * Creates asynchronous {@link Step} to read health from a server instance.
   *
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step createReadHealthStep(Step next) {
    return new ReadHealthStep(next);
  }

  private static String getRetrieveHealthSearchUrl() {
    return "/management/weblogic/latest/serverRuntime/search";
  }

  private static String getRetrieveHealthSearchPayload() {
    return "{ fields: [ 'state', 'overallHealthState', 'activationTime' ], links: [] }";
  }

  // overallHealthState, healthState

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    V1ObjectMeta meta = dom.getMetadata();
    String namespace = meta.getNamespace();

    String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);

    String secretName = dom.getWebLogicCredentialsSecretName();

    V1Service service = info.getServerService(serverName);
    V1Pod pod = info.getServerPod(serverName);
    if (service != null) {
      Step getClient =
          getEncodedCredentialsForServer(
              namespace,
              secretName,
              new ReadHealthWithHttpClientStep(
                  service, pod, new ProcessResponseFromHttpClientStep(getNext())));
      return doNext(getClient, packet);
    }
    return doNext(packet);
  }

  /**
   * Asynchronous {@link Step} for getting encoded credentials for a server instance.
   *
   * @param namespace Namespace
   * @param adminSecretName Admin secret name
   * @param next Next processing step
   * @return step to create client
   */
  public static Step getEncodedCredentialsForServer(
      String namespace, String adminSecretName, Step next) {
    return new EncodedCredentialsForServerStep(
        namespace, adminSecretName, new WithSecretDataStep(next));
  }

  private static class EncodedCredentialsForServerStep extends Step {
    private final String namespace;
    private final String adminSecretName;

    EncodedCredentialsForServerStep(String namespace, String adminSecretName, Step next) {
      super(next);
      this.namespace = namespace;
      this.adminSecretName = adminSecretName;
    }

    @Override
    public NextAction apply(Packet packet) {
      Step readSecret =
          SecretHelper.getSecretData(
              SecretType.WebLogicCredentials, adminSecretName, namespace, getNext());
      return doNext(readSecret, packet);
    }
  }

  private static class WithSecretDataStep extends Step {

    WithSecretDataStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      Map<String, byte[]> secretData =
          (Map<String, byte[]>) packet.get(SecretHelper.SECRET_DATA_KEY);
      if (secretData != null) {
        byte[] username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
        byte[] password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);
        packet.put(ProcessingConstants.KEY, createEncodedCredentials(username, password));

        clearCredential(username);
        clearCredential(password);
      }
      return doNext(packet);
    }
  }

  /**
   * Create encoded credentials from username and password.
   *
   * @param username Username
   * @param password Password
   * @return encoded credentials
   */
  private static String createEncodedCredentials(final byte[] username, final byte[] password) {
    // Get encoded credentials with authentication information.
    String encodedCredentials = null;
    if (username != null && password != null) {
      byte[] usernameAndPassword = new byte[username.length + password.length + 1];
      System.arraycopy(username, 0, usernameAndPassword, 0, username.length);
      usernameAndPassword[username.length] = (byte) ':';
      System.arraycopy(password, 0, usernameAndPassword, username.length + 1, password.length);
      encodedCredentials = java.util.Base64.getEncoder().encodeToString(usernameAndPassword);
    }
    return encodedCredentials;
  }

  /**
   * Erase authentication credential so that it is not sitting in memory where a rogue program can
   * find it.
   */
  private static void clearCredential(byte[] credential) {
    if (credential != null) {
      Arrays.fill(credential, (byte) 0);
    }
  }

  /**
   * {@link Step} for asynchronous invocation of REST call to read health of server instance.
   *
   * @param service The name of the Service that you want the URL for.
   * @param pod The pod for headless services
   * @param next Next processing step
   * @return step Step to process result response from the REST call
   */
  static final class ReadHealthWithHttpClientStep extends Step {
    private final V1Service service;
    private final V1Pod pod;

    ReadHealthWithHttpClientStep(V1Service service, V1Pod pod, Step next) {
      super(next);
      this.service = service;
      this.pod = pod;
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        HttpClientPool helper = HttpClientPool.getInstance();
        String encodedCredentials = (String) packet.get(ProcessingConstants.KEY);
        HttpClient httpClient = helper.take();

        DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
        WlsDomainConfig domainConfig =
            (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
        if (domainConfig == null) {
          Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUid());
          domainConfig = scan.getWlsDomainConfig();
        }
        String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
        // standalone server that does not belong to any cluster
        WlsServerConfig serverConfig = domainConfig.getServerConfig(serverName);

        if (serverConfig == null) {
          // dynamic or configured server in a cluster
          String clusterName = service.getMetadata().getLabels().get(CLUSTERNAME_LABEL);
          WlsClusterConfig cluster = domainConfig.getClusterConfig(clusterName);
          serverConfig = findServerConfig(cluster, serverName);
        }

        if (encodedCredentials == null) {
          LOGGER.info(
              (LoggingFilter) packet.get(LoggingFilter.LOGGING_FILTER_PACKET_KEY),
              MessageKeys.WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT,
              packet.get(ProcessingConstants.SERVER_NAME));
        } else {

          String serviceUrl =
              getServiceUrl(
                  service,
                  pod,
                  serverConfig.getAdminProtocolChannelName(),
                  serverConfig.getListenPort());
          if (serviceUrl != null) {
            return doSuspend(
                (fiber) -> {
                  try {
                    String url = serviceUrl + getRetrieveHealthSearchUrl();
                    HttpRequest request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                            .header("Authorization", "Basic " + encodedCredentials)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("X-Requested-By", "WebLogic Operator")
                            .POST(
                                HttpRequest.BodyPublishers.ofString(
                                    getRetrieveHealthSearchPayload()))
                            .build();
                    httpClient
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(
                            response ->
                                new Result(
                                    response.body(),
                                    response.statusCode(),
                                    response.statusCode() == ProcessingConstants.HTTP_OK))
                        .whenComplete(
                            (input, exception) -> {
                              if (exception != null) {
                                LOGGER.severe(
                                    MessageKeys.HTTP_METHOD_FAILED,
                                    "POST",
                                    url,
                                    input.getResponse());
                              } else {
                                packet.put(ProcessingConstants.RESULT, input);
                              }
                              fiber.resume(packet);
                            });
                  } catch (Exception e) {
                    fiber.resume(packet);
                    LOGGER.severe(MessageKeys.HTTP_METHOD_FAILED, "POST", e.getMessage());
                  }
                });
          }
        }
        return doNext(packet);
      } catch (Throwable t) {
        // do not retry for health check
        LOGGER.info(
            (LoggingFilter) packet.get(LoggingFilter.LOGGING_FILTER_PACKET_KEY),
            MessageKeys.WLS_HEALTH_READ_FAILED,
            packet.get(ProcessingConstants.SERVER_NAME),
            t);
        return doNext(packet);
      }
    }

    /**
     * Returns the URL to access the Service; using the Service clusterIP and port. If the service
     * is headless, then the pod's IP is returned, if available.
     *
     * @param service The name of the Service that you want the URL for.
     * @param pod The pod for headless services
     * @param adminChannel administration channel name
     * @param defaultPort default port, if enabled. Other ports will use SSL.
     * @return The URL of the Service or null if the URL cannot be found.
     */
    public static String getServiceUrl(
        V1Service service, V1Pod pod, String adminChannel, Integer defaultPort) {
      if (service != null) {
        V1ServiceSpec spec = service.getSpec();
        if (spec != null) {
          String portalIP = spec.getClusterIP();
          if ("None".equalsIgnoreCase(spec.getClusterIP())) {
            if (pod != null && pod.getStatus().getPodIP() != null) {
              portalIP = pod.getStatus().getPodIP();
            } else {
              portalIP =
                  service.getMetadata().getName()
                      + "."
                      + service.getMetadata().getNamespace()
                      + ".pod.cluster.local";
            }
          }
          Integer port = -1; // uninitialized
          if (adminChannel != null) {
            for (V1ServicePort sp : spec.getPorts()) {
              if (adminChannel.equals(sp.getName())) {
                port = sp.getPort();
                break;
              }
            }
            if (port == -1) {
              return null;
            }
          } else {
            port = spec.getPorts().iterator().next().getPort();
          }
          portalIP += ":" + port;
          String serviceUrl =
              (port.equals(defaultPort) ? HTTP_PROTOCOL : HTTPS_PROTOCOL) + portalIP;
          LOGGER.fine(MessageKeys.SERVICE_URL, serviceUrl);
          return serviceUrl;
        }
      }
      return null;
    }

    private WlsServerConfig findServerConfig(WlsClusterConfig wlsClusterConfig, String serverName) {
      for (WlsServerConfig serverConfig : wlsClusterConfig.getServerConfigs()) {
        if (Objects.equals(serverName, serverConfig.getName())) {
          return serverConfig;
        }
      }
      return null;
    }
  }

  /**
   * {@link Step} for processing json result object containing the response from the REST call.
   *
   * @param next Next processing step
   * @return step Next processing step
   */
  static final class ProcessResponseFromHttpClientStep extends Step {

    ProcessResponseFromHttpClientStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        Result result = (Result) packet.get(ProcessingConstants.RESULT);
        DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
        String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
        Pair<String, ServerHealth> pair = createServerHealthFromResult(result);

        String state = pair.getLeft();
        if (state != null && !state.isEmpty()) {
          ConcurrentMap<String, String> serverStateMap =
              (ConcurrentMap<String, String>) packet.get(SERVER_STATE_MAP);
          info.updateLastKnownServerStatus(serverName, state);
          serverStateMap.put(serverName, state);
        }

        @SuppressWarnings("unchecked")
        ConcurrentMap<String, ServerHealth> serverHealthMap =
            (ConcurrentMap<String, ServerHealth>) packet.get(ProcessingConstants.SERVER_HEALTH_MAP);

        serverHealthMap.put((String) packet.get(ProcessingConstants.SERVER_NAME), pair.getRight());
        AtomicInteger remainingServersHealthToRead =
            packet.getValue(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ);
        remainingServersHealthToRead.getAndDecrement();
        return doNext(packet);
      } catch (Throwable t) {
        // do not retry for health check
        LOGGER.info(
            (LoggingFilter) packet.get(LoggingFilter.LOGGING_FILTER_PACKET_KEY),
            MessageKeys.WLS_HEALTH_READ_FAILED,
            packet.get(ProcessingConstants.SERVER_NAME),
            t);
        return doNext(packet);
      }
    }

    private Pair<String, ServerHealth> createServerHealthFromResult(Result restResult)
        throws IOException {
      if (restResult.isSuccessful()) {
        return parseServerHealthJson(restResult.getResponse());
      }
      return new Pair<>(
          WebLogicConstants.UNKNOWN_STATE,
          new ServerHealth()
              .withOverallHealth(
                  restResult.isServerOverloaded()
                      ? OVERALL_HEALTH_FOR_SERVER_OVERLOADED
                      : OVERALL_HEALTH_NOT_AVAILABLE));
    }

    private Pair<String, ServerHealth> parseServerHealthJson(String jsonResult) throws IOException {
      if (jsonResult == null) {
        return null;
      }

      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(jsonResult);

      JsonNode healthState = null;
      JsonNode subsystemName = null;
      JsonNode symptoms = null;
      JsonNode overallHealthState = root.path("overallHealthState");
      if (overallHealthState != null) {
        healthState = overallHealthState.path("state");
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
              .withOverallHealth(healthState != null ? healthState.asText() : null)
              .withActivationTime(
                  activationTime != null ? new DateTime(activationTime.asLong()) : null);
      if (subName != null) {
        health
            .getSubsystems()
            .add(new SubsystemHealth().withSubsystemName(subName).withSymptoms(sym));
      }

      JsonNode state = root.path("state");

      String stateVal = null;
      if (state != null) {
        String s = state.asText();
        if (s != null && !"null".equals(s)) {
          stateVal = s;
        }
      }

      return new Pair<>(stateVal, health);
    }
  }
}
