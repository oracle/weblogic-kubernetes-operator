// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.http.HttpAsyncRequestStep;
import oracle.kubernetes.operator.http.HttpResponseStep;
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
import static oracle.kubernetes.operator.ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ;
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

  private static String getRetrieveHealthSearchPath() {
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
      Step getSecretReadHealthAndProcessResponse =
          SecretHelper.getSecretData(
              SecretType.WebLogicCredentials,
              secretName,
              namespace,
              new WithSecretDataStep(
                  new ReadHealthWithHttpStep(service, pod, getNext())));
      return doNext(getSecretReadHealthAndProcessResponse, packet);
    }
    return doNext(packet);
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
        packet.put(ProcessingConstants.ENCODED_CREDENTIALS, createEncodedCredentials(username, password));

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

  static final class ReadHealthProcessing {
    private Packet packet;
    private V1Service service;
    private V1Pod pod;

    ReadHealthProcessing(Packet packet, V1Service service, V1Pod pod) {
      this.packet = packet;
      this.service = service;
      this.pod = pod;
    }

    private String getRequestUrl() {
      return getServiceUrl() + getRetrieveHealthSearchPath();
    }

    private HttpRequest createRequest(String url) {
      return HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Authorization", "Basic " + getEncodedCredentials())
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .header("X-Requested-By", "WebLogic Operator")
          .POST(HttpRequest.BodyPublishers.ofString(getRetrieveHealthSearchPayload()))
          .build();
    }

    private String getServiceUrl() {
      return Optional.ofNullable(getService()).map(V1Service::getSpec).map(this::getServiceUrl).orElse(null);
    }

    private String getServiceUrl(V1ServiceSpec spec) {
      String url = getProtocol(spec) + getPortalIP(spec) + ":" + getPort(spec);
      LOGGER.fine(MessageKeys.SERVICE_URL, url);
      return url;
    }

    private String getProtocol(V1ServiceSpec spec) {
      return getPort(spec).equals(getWlsServerConfig().getListenPort()) ? HTTP_PROTOCOL : HTTPS_PROTOCOL;
    }

    private Integer getPort(V1ServiceSpec spec) {
      return Optional.ofNullable(getServicePort(spec)).map(V1ServicePort::getPort).orElse(-1);
    }

    private V1ServicePort getServicePort(V1ServiceSpec spec) {
      return getAdminProtocolPort(spec).orElse(getFirstPort(spec));
    }

    private Optional<V1ServicePort> getAdminProtocolPort(V1ServiceSpec spec) {
      return Optional.ofNullable(spec.getPorts())
            .stream()
            .flatMap(Collection::stream)
            .filter(this::isAdminProtocolPort)
            .findFirst();
    }

    private boolean isAdminProtocolPort(V1ServicePort port) {
      return Optional.ofNullable(getAdminProtocolChannelName()).map(n -> n.equals(port.getName())).orElse(false);
    }

    private V1ServicePort getFirstPort(V1ServiceSpec spec) {
      return Optional.ofNullable(spec).map(V1ServiceSpec::getPorts).map(l -> l.get(0)).orElse(null);
    }

    private String getAdminProtocolChannelName() {
      return getWlsServerConfig().getAdminProtocolChannelName();
    }

    private String getPortalIP(V1ServiceSpec spec) {
      String portalIP = spec.getClusterIP();
      if ("None".equalsIgnoreCase(spec.getClusterIP())) {
        if (getPod() != null && getPod().getStatus().getPodIP() != null) {
          portalIP = getPod().getStatus().getPodIP();
        } else {
          portalIP =
              getService().getMetadata().getName()
                  + "."
                  + getService().getMetadata().getNamespace()
                  + ".pod.cluster.local";
        }
      }
      return portalIP;
    }

    private WlsServerConfig getWlsServerConfig() {
      // standalone server that does not belong to any cluster
      WlsServerConfig serverConfig = getWlsDomainConfig().getServerConfig(getServerName());

      if (serverConfig == null) {
        // dynamic or configured server in a cluster
        String clusterName = getService().getMetadata().getLabels().get(CLUSTERNAME_LABEL);
        WlsClusterConfig cluster = getWlsDomainConfig().getClusterConfig(clusterName);
        serverConfig = findServerConfig(cluster);
      }
      return serverConfig;
    }

    private WlsServerConfig findServerConfig(WlsClusterConfig wlsClusterConfig) {
      for (WlsServerConfig serverConfig : wlsClusterConfig.getServerConfigs()) {
        if (Objects.equals(getServerName(), serverConfig.getName())) {
          return serverConfig;
        }
      }
      return null;
    }

    private String getServerName() {
      return (String) getPacket().get(ProcessingConstants.SERVER_NAME);
    }

    private WlsDomainConfig getWlsDomainConfig() {
      DomainPresenceInfo info = getPacket().getSpi(DomainPresenceInfo.class);
      WlsDomainConfig domainConfig =
          (WlsDomainConfig) getPacket().get(ProcessingConstants.DOMAIN_TOPOLOGY);
      if (domainConfig == null) {
        Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUid());
        domainConfig = scan.getWlsDomainConfig();
      }
      return domainConfig;
    }

    public Packet getPacket() {
      return packet;
    }

    String getEncodedCredentials() {
      return (String) packet.get(ProcessingConstants.ENCODED_CREDENTIALS);
    }

    public V1Service getService() {
      return service;
    }

    public V1Pod getPod() {
      return pod;
    }
  }

  /**
   * Step to send a query to Kubernetes to obtain the health of a specified server.
   * Packet values used:
   *  SERVER_NAME                       the name of the server
   *  DOMAIN_TOPOLOGY                   the topology of the domain
   */
  static final class ReadHealthWithHttpStep extends Step {
    private final V1Service service;
    private final V1Pod pod;

    ReadHealthWithHttpStep(V1Service service, V1Pod pod, Step next) {
      super(next);
      this.service = service;
      this.pod = pod;
    }

    @Override
    public NextAction apply(Packet packet) {
      ReadHealthProcessing processing = new ReadHealthProcessing(packet, service, pod);
      HttpRequest request = processing.createRequest(processing.getRequestUrl());
      return doNext(createRequestStep(request, new RecordHealthStep(getNext())), packet);
    }

    private HttpAsyncRequestStep createRequestStep(HttpRequest request, RecordHealthStep responseStep) {
      return HttpAsyncRequestStep.create(request, responseStep)
            .withTimeoutSeconds(HTTP_TIMEOUT_SECONDS);
    }

  }

  /**
   * {@link Step} for processing json result object containing the response from the REST call.
   * Packet values used:
   *  SERVER_NAME                       the name of the server
   *  SERVER_STATE_MAP                  a map of server names to state
   *  SERVER_HEALTH_MAP                 a map of server names to health
   *  REMAINING_SERVERS_HEALTH_TO_READ  a counter of the servers whose health needs to be read
   *  (spi) HttpResponse.class          the response from the server
   */
  static final class RecordHealthStep extends HttpResponseStep {

    RecordHealthStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, HttpResponse<String> response) {
      try {
        new HealthResponseProcessing(packet, response).recordStateAndHealth();
        decrementIntegerInPacketAtomically(packet, REMAINING_SERVERS_HEALTH_TO_READ);

        return doNext(packet);
      } catch (Throwable t) {
        // do not retry for health check
        logReadFailure(packet);
        return doNext(packet);
      }
    }

    @Override
    public NextAction onFailure(Packet packet, HttpResponse<String> response) {
      try {
        new HealthResponseProcessing(packet, response).recordFailedStateAndHealth();
      } catch (IOException e) {
        logReadFailure(packet);
      }
      return doNext(packet);
    }


    static class HealthResponseProcessing {
      private final String serverName;
      private Packet packet;
      private HttpResponse<String> response;
      private String state;
      private ServerHealth health;

      public HealthResponseProcessing(Packet packet, HttpResponse<String> response) throws IOException {
        this.packet = packet;
        this.response = response;

        serverName = getServerName();
      }

      private String getServerName() {
        return (String) getPacket().get(ProcessingConstants.SERVER_NAME);
      }

      void recordFailedStateAndHealth() {
        recordStateAndHealth(WebLogicConstants.UNKNOWN_STATE, new ServerHealth().withOverallHealth(getFailedHealth()));
      }

      private String getFailedHealth() {
        return isServerOverloaded()
              ? OVERALL_HEALTH_FOR_SERVER_OVERLOADED
              : OVERALL_HEALTH_NOT_AVAILABLE;
      }

      void recordStateAndHealth() throws IOException {
        Pair<String, ServerHealth> pair = RecordHealthStep.parseServerHealthJson(getResponse().body());
        state = Strings.emptyToNull(pair.getLeft());
        health = pair.getRight();
        recordStateAndHealth(state, health);
      }

      private void recordStateAndHealth(String state, ServerHealth health) {
        Optional.ofNullable(state).ifPresent(this::recordServerState);
        getServerHealthMap().put(serverName, health);
      }

      private void recordServerState(String state) {
        getDomainPresenceInfo().updateLastKnownServerStatus(serverName, state);
        getServerStateMap().put(serverName, state);
      }

      @SuppressWarnings("unchecked")
      private Map<String, ServerHealth> getServerHealthMap() {
        return (Map<String, ServerHealth>) getPacket().get(ProcessingConstants.SERVER_HEALTH_MAP);
      }

      @SuppressWarnings("unchecked")
      private Map<String, String> getServerStateMap() {
        return (Map<String, String>) getPacket().get(SERVER_STATE_MAP);
      }

      private boolean isServerOverloaded() {
        return isServerOverloaded(getResponse().statusCode());
      }

      private boolean isServerOverloaded(int statusCode) {
        return statusCode == 500 || statusCode == 503;
      }

      private HttpResponse<String> getResponse() {
        return response;
      }

      private DomainPresenceInfo getDomainPresenceInfo() {
        return getPacket().getSpi(DomainPresenceInfo.class);
      }

      Packet getPacket() {
        return packet;
      }
    }

    private static Pair<String, ServerHealth> parseServerHealthJson(String jsonResult) throws IOException {
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

  private static void decrementIntegerInPacketAtomically(Packet packet, String key) {
    packet.<AtomicInteger>getValue(key).getAndDecrement();
  }

  private static void logReadFailure(Packet packet) {
    LOGGER.info(
          (LoggingFilter) packet.get(LoggingFilter.LOGGING_FILTER_PACKET_KEY),
          MessageKeys.WLS_HEALTH_READ_FAILED,
          packet.get(ProcessingConstants.SERVER_NAME));
  }
}
