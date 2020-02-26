// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.http.HttpClient;
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

  public static final String OVERALL_HEALTH_NOT_AVAILABLE = "Not available";
  public static final String OVERALL_HEALTH_FOR_SERVER_OVERLOADED =
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
          HttpClient.createAuthenticatedClientForServer(
              namespace, secretName, new ReadHealthWithHttpClientStep(service, pod, getNext()));
      return doNext(getClient, packet);
    }
    return doNext(packet);
  }

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
        HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
        DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
        WlsDomainConfig domainConfig =
            (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
        if (domainConfig == null) {
          Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUid());
          domainConfig = scan.getWlsDomainConfig();
        }
        String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
        WlsServerConfig serverConfig = domainConfig.getServerConfig(serverName);

        if (serverConfig == null) {
          // dynamic server
          String clusterName = service.getMetadata().getLabels().get(CLUSTERNAME_LABEL);
          WlsClusterConfig cluster = domainConfig.getClusterConfig(clusterName);
          serverConfig = cluster.getDynamicServersConfig().getServerConfig(serverName);
        }

        if (httpClient == null) {
          LOGGER.info(
              (LoggingFilter) packet.get(LoggingFilter.LOGGING_FILTER_PACKET_KEY),
              MessageKeys.WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT,
              packet.get(ProcessingConstants.SERVER_NAME));
        } else {

          String serviceUrl =
              HttpClient.getServiceUrl(
                  service,
                  pod,
                  serverConfig.getAdminProtocolChannelName(),
                  serverConfig.getListenPort());
          if (serviceUrl != null) {
            Result result =
                httpClient.executePostUrlOnServiceClusterIP(
                    getRetrieveHealthSearchUrl(),
                    serviceUrl,
                    getRetrieveHealthSearchPayload(),
                    false);

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
                (ConcurrentMap<String, ServerHealth>)
                    packet.get(ProcessingConstants.SERVER_HEALTH_MAP);

            serverHealthMap.put(
                (String) packet.get(ProcessingConstants.SERVER_NAME), pair.getRight());
            AtomicInteger remainingServersHealthToRead =
                packet.getValue(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ);
            remainingServersHealthToRead.getAndDecrement();
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
