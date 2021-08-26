// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.openapi.models.V1ServicePort;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.HttpResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
import oracle.kubernetes.operator.wlsconfig.PortDetails;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.steps.HttpRequestProcessing.createRequestStep;

public class ShutdownManagedServerStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private String serverName;

  private ShutdownManagedServerStep(Step next, String serverName) {
    super(next);
    this.serverName = serverName;
  }

  /**
   * Creates asynchronous {@link Step}.
   *
   * @param next Next processing step
   * @param serverName name of server
   * @return asynchronous step
   */
  public static Step createShutdownManagedServerStep(Step next, String serverName) {
    return new ShutdownManagedServerStep(next, serverName);
  }

  private static String getManagedServerShutdownPath() {
    return "/management/weblogic/latest/serverRuntime/shutdown";
  }

  private static String getManagedServerShutdownPayload() {
    return "{  \"ignoreSessions\": true, \"timeout\": 60, \"waitForAllSessions\": false }";
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    V1Service service = info.getServerService(serverName);

    if (service == null) {
      return doNext(packet);
    } else {
      return doNext(
            Step.chain(
                SecretHelper.createAuthorizationSourceStep(),
                new ShutdownManagedServerWithHttpStep(service, info.getServerPod(serverName), getNext())),
            packet);
    }
  }

  static final class ShutdownManagedServerProcessing extends HttpRequestProcessing {

    ShutdownManagedServerProcessing(Packet packet, @Nonnull V1Service service, V1Pod pod) {
      super(packet, service, pod);
    }

    private HttpRequest createRequest() {
      HttpRequest request = createRequestBuilder(getRequestUrl())
            .POST(HttpRequest.BodyPublishers.ofString(getManagedServerShutdownPayload()))
            .build();
      LOGGER.info("ShutdownManagedServerProcessing.createRequest request: " + request);
      return request;
    }

    private String getRequestUrl() {
      return getServiceUrl() + getManagedServerShutdownPath();
    }

    protected PortDetails getPortDetails() {
      Integer port = getWlsServerPort();
      return new PortDetails(port, !port.equals(getWlsServerConfig().getListenPort()));
    }

    private Integer getWlsServerPort() {
      return getWlsServerConfig().getListenPort();
    }

    private WlsServerConfig getWlsServerConfig() {
      // standalone server that does not belong to any cluster
      WlsServerConfig serverConfig = getWlsDomainConfig().getServerConfig(getServerName());

      if (serverConfig == null) {
        // dynamic or configured server in a cluster
        String clusterName = getClusterNameFromServiceLabel();
        WlsClusterConfig cluster = getWlsDomainConfig().getClusterConfig(clusterName);
        serverConfig = findServerConfig(cluster);
      }
      return serverConfig;
    }

    private String getClusterNameFromServiceLabel() {
      return Optional.of(getService())
          .map(V1Service::getMetadata)
          .map(V1ObjectMeta::getLabels)
          .map(m -> m.get(CLUSTERNAME_LABEL))
          .orElse(null);
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
      return this.pod.getMetadata().getLabels().get(LabelConstants.SERVERNAME_LABEL);
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
  static final class ShutdownManagedServerWithHttpStep extends Step {
    @Nonnull
    private final V1Service service;
    private final V1Pod pod;

    ShutdownManagedServerWithHttpStep(@Nonnull V1Service service, V1Pod pod, Step next) {
      super(next);
      this.service = service;
      this.pod = pod;
    }

    @Override
    public NextAction apply(Packet packet) {
      ShutdownManagedServerProcessing processing = new ShutdownManagedServerProcessing(packet, service, pod);
      return doNext(createRequestStep(processing.createRequest(),
          new ShutdownManagedServerResponseStep(getNext())), packet);
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
  static final class ShutdownManagedServerResponseStep extends HttpResponseStep {

    ShutdownManagedServerResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, HttpResponse<String> response) {
      LOGGER.info("ShutdownManagedServerStep.onSuccess response: " + response);
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, HttpResponse<String> response) {
      if (response != null) {
        LOGGER.info("ShutdownManagedServerStep.onFailure response: " + response);
      }
      return doNext(packet);
    }
  }
}
