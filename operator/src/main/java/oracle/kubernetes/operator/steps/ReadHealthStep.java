// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.http.HttpClient;
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
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.SubsystemHealth;
import org.joda.time.DateTime;

public class ReadHealthStep extends Step {

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

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    V1ObjectMeta meta = dom.getMetadata();
    DomainSpec spec = dom.getSpec();
    String namespace = meta.getNamespace();

    String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);

    String secretName =
        spec.getWebLogicCredentialsSecret() == null
            ? null
            : spec.getWebLogicCredentialsSecret().getName();

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

  private static String getRetrieveHealthSearchUrl() {
    return "/management/weblogic/latest/serverRuntime/search";
  }

  // overallHealthState, healthState

  private static String getRetrieveHealthSearchPayload() {
    return "{ fields: [ 'overallHealthState', 'activationTime' ], links: [] }";
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
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        WlsDomainConfig domainConfig =
            (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
        if (domainConfig == null) {
          Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUID());
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

          String serviceURL =
              HttpClient.getServiceURL(
                  service,
                  pod,
                  serverConfig.getAdminProtocolChannelName(),
                  serverConfig.getListenPort());
          if (serviceURL != null) {
            String jsonResult =
                httpClient
                    .executePostUrlOnServiceClusterIP(
                        getRetrieveHealthSearchUrl(),
                        serviceURL,
                        getRetrieveHealthSearchPayload(),
                        true)
                    .getResponse();

            ServerHealth health = parseServerHealthJson(jsonResult);

            @SuppressWarnings("unchecked")
            ConcurrentMap<String, ServerHealth> serverHealthMap =
                (ConcurrentMap<String, ServerHealth>)
                    packet.get(ProcessingConstants.SERVER_HEALTH_MAP);
            serverHealthMap.put((String) packet.get(ProcessingConstants.SERVER_NAME), health);
            packet.put(ProcessingConstants.SERVER_HEALTH_READ, Boolean.TRUE);
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

    private ServerHealth parseServerHealthJson(String jsonResult) throws IOException {
      if (jsonResult == null) return null;

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

      return health;
    }
  }
}
