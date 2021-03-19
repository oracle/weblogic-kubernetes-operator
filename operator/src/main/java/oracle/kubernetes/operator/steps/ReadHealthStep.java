// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.HttpResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.LoggingFilter;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
import oracle.kubernetes.operator.wlsconfig.PortDetails;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.SubsystemHealth;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.steps.HttpRequestProcessing.createRequestStep;
import static oracle.kubernetes.utils.OperatorUtils.emptyToNull;

public class ReadHealthStep extends Step {

  static final String OVERALL_HEALTH_NOT_AVAILABLE = "Not available";
  static final String OVERALL_HEALTH_FOR_SERVER_OVERLOADED =
      OVERALL_HEALTH_NOT_AVAILABLE + " (possibly overloaded)";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

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
    String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    V1Service service = info.getServerService(serverName);

    if (service == null) {
      return doNext(packet);
    } else {
      return doNext(
            Step.chain(
                SecretHelper.createAuthorizationHeaderFactoryStep(),
                new ReadHealthWithHttpStep(service, info.getServerPod(serverName), getNext())),
            packet);
    }
  }

  static final class ReadHealthProcessing extends HttpRequestProcessing {

    ReadHealthProcessing(Packet packet, @Nonnull V1Service service, V1Pod pod) {
      super(packet, service, pod);
    }

    private HttpRequest createRequest() {
      return createRequestBuilder(getRequestUrl())
            .POST(HttpRequest.BodyPublishers.ofString(getRetrieveHealthSearchPayload()))
            .build();
    }

    private String getRequestUrl() {
      return getServiceUrl() + getRetrieveHealthSearchPath();
    }

    protected PortDetails getPortDetails() {
      Integer port = getPort();
      return new PortDetails(port, !port.equals(getWlsServerConfig().getListenPort()));
    }

    private Integer getPort() {
      return Optional.ofNullable(getService().getSpec())
            .map(this::getServicePort)
            .map(V1ServicePort::getPort)
            .orElse(-1);
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
  }

  /**
   * Step to send a query to Kubernetes to obtain the health of a specified server.
   * Packet values used:
   *  SERVER_NAME                       the name of the server
   *  DOMAIN_TOPOLOGY                   the topology of the domain
   */
  static final class ReadHealthWithHttpStep extends Step {
    @Nonnull
    private final V1Service service;
    private final V1Pod pod;

    ReadHealthWithHttpStep(@Nonnull V1Service service, V1Pod pod, Step next) {
      super(next);
      this.service = service;
      this.pod = pod;
    }

    @Override
    public NextAction apply(Packet packet) {
      ReadHealthProcessing processing = new ReadHealthProcessing(packet, service, pod);
      return doNext(createRequestStep(processing.createRequest(), new RecordHealthStep(getNext())), packet);
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
      new HealthResponseProcessing(packet, response).recordFailedStateAndHealth();
      return doNext(packet);
    }


    static class HealthResponseProcessing {
      private final String serverName;
      private final Packet packet;
      private final HttpResponse<String> response;

      public HealthResponseProcessing(Packet packet, HttpResponse<String> response) {
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
        String state = emptyToNull(pair.getLeft());
        ServerHealth health = pair.getRight();
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
        return Optional.ofNullable(getResponse())
            .map(HttpResponse::statusCode)
            .map(this::isServerOverloaded)
            .orElse(false);
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
                  activationTime != null ? OffsetDateTime.ofInstant(
                      Instant.ofEpochMilli(activationTime.asLong()), ZoneId.of("UTC")) : null);
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

  @SuppressWarnings("SameParameterValue")
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
