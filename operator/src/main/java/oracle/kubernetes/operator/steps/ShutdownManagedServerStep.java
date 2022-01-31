// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.ShutdownType;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.HttpAsyncRequestStep;
import oracle.kubernetes.operator.http.HttpResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
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
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import oracle.kubernetes.weblogic.domain.model.Shutdown;

import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;

public class ShutdownManagedServerStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private String serverName;
  private V1Pod pod;

  private ShutdownManagedServerStep(Step next, String serverName, V1Pod pod) {
    super(next);
    this.serverName = serverName;
    this.pod = pod;
  }

  /**
   * Creates asynchronous {@link Step}.
   *
   * @param next Next processing step
   * @param serverName name of server
   * @param pod server pod
   * @return asynchronous step
   */
  static Step createShutdownManagedServerStep(Step next, String serverName, V1Pod pod) {
    return new ShutdownManagedServerStep(next, serverName, pod);
  }

  private static String getManagedServerShutdownPath(Boolean isGracefulShutdown) {
    String shutdownString = isGracefulShutdown ? "shutdown" : "forceShutdown";
    return "/management/weblogic/latest/serverRuntime/" + shutdownString;
  }

  private static String getManagedServerShutdownPayload(Boolean isGracefulShutdown,
      Boolean ignoreSessions, Long timeout, Boolean waitForAllSessions) {
    if (!isGracefulShutdown) {
      return "{}";
    }

    return "{  \"ignoreSessions\": "
        + ignoreSessions
        + ", \"timeout\": "
        + timeout
        + ", \"waitForAllSessions\": "
        + waitForAllSessions
        + "}";
  }

  @Override
  public NextAction apply(Packet packet) {
    LOGGER.fine(MessageKeys.BEGIN_SERVER_SHUTDOWN_REST, serverName);
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    V1Service service = info.getServerService(serverName);

    if (service == null) {
      return doNext(packet);
    } else {
      return doNext(
            Step.chain(
                SecretHelper.createAuthorizationSourceStep(),
                new ShutdownManagedServerWithHttpStep(service, pod, getNext())),
            packet);
    }
  }

  static final class ShutdownManagedServerProcessing extends HttpRequestProcessing {
    private Boolean isGracefulShutdown;
    private Long timeout;
    private Boolean ignoreSessions;
    private Boolean waitForAllSessions;

    ShutdownManagedServerProcessing(Packet packet, @Nonnull V1Service service, V1Pod pod) {
      super(packet, service, pod);
      initializeRequestPayloadParameters();
    }

    private HttpRequest createRequest() {
      return createRequestBuilder(getRequestUrl(isGracefulShutdown))
            .POST(HttpRequest.BodyPublishers.ofString(getManagedServerShutdownPayload(
                isGracefulShutdown, ignoreSessions, timeout, waitForAllSessions))).build();
    }

    private void initializeRequestPayloadParameters() {
      String serverName = getServerName();
      String clusterName = getClusterNameFromServiceLabel();
      List<V1EnvVar> envVarList = getV1EnvVars();

      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      Shutdown shutdown = Optional.ofNullable(info.getDomain().getServer(serverName, clusterName))
          .map(ServerSpec::getShutdown).orElse(null);

      isGracefulShutdown = isGracefulShutdown(envVarList, shutdown);
      timeout = getTimeout(envVarList, shutdown);
      ignoreSessions = getIgnoreSessions(envVarList, shutdown);
      waitForAllSessions = getWaitForAllSessions(envVarList, shutdown);
    }

    private List<V1EnvVar> getV1EnvVars() {
      return Optional.ofNullable(pod.getSpec())
              .map(this::getEnvVars).orElse(Collections.emptyList());
    }

    private List<V1EnvVar> getEnvVars(V1PodSpec v1PodSpec) {
      return getContainer(v1PodSpec).map(V1Container::getEnv).get();
    }

    Optional<V1Container> getContainer(V1PodSpec v1PodSpec) {
      return v1PodSpec.getContainers().stream().filter(this::isK8sContainer).findFirst();
    }

    boolean isK8sContainer(V1Container c) {
      return WLS_CONTAINER_NAME.equals(c.getName());
    }

    private String getEnvValue(List<V1EnvVar> vars, String name) {
      for (V1EnvVar var : vars) {
        if (var.getName().equals(name)) {
          return var.getValue();
        }
      }
      return null;
    }

    private Boolean isGracefulShutdown(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String shutdownType = getEnvValue(envVarList, "SHUTDOWN_TYPE");

      shutdownType = shutdownType == null ? Optional.ofNullable(shutdown).map(Shutdown::getShutdownType)
          .orElse(ShutdownType.Graceful.name()) : shutdownType;


      return shutdownType.equalsIgnoreCase(ShutdownType.Graceful.name());
    }

    private Boolean getWaitForAllSessions(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String waitForAllSessions = getEnvValue(envVarList, "SHUTDOWN_WAIT_FOR_ALL_SESSIONS");

      return waitForAllSessions == null ? Optional.ofNullable(shutdown).map(Shutdown::getWaitForAllSessions)
              .orElse(Shutdown.DEFAULT_WAIT_FOR_ALL_SESSIONS) : Boolean.valueOf(waitForAllSessions);
    }

    private Boolean getIgnoreSessions(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String ignoreSessions = getEnvValue(envVarList, "SHUTDOWN_IGNORE_SESSIONS");

      return ignoreSessions == null ? Optional.ofNullable(shutdown).map(Shutdown::getIgnoreSessions)
              .orElse(Shutdown.DEFAULT_IGNORESESSIONS) : Boolean.valueOf(ignoreSessions);
    }

    private Long getTimeout(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String timeout = getEnvValue(envVarList, "SHUTDOWN_TIMEOUT");

      return timeout == null ? Optional.ofNullable(shutdown).map(Shutdown::getTimeoutSeconds)
              .orElse(Shutdown.DEFAULT_TIMEOUT) : Long.valueOf(timeout);
    }

    Long getRequestTimeoutSeconds() {
      // Add a 10 second fudge factor here to account for any delay in
      // connecting and issuing the shutdown request.
      return timeout + PodHelper.DEFAULT_ADDITIONAL_DELETE_TIME;
    }

    private String getRequestUrl(Boolean isGracefulShutdown) {
      return getServiceUrl() + getManagedServerShutdownPath(isGracefulShutdown);
    }

    protected PortDetails getPortDetails() {
      Integer port = getWlsServerPort();
      boolean isSecure = port != null && getWlsServerConfig() != null
          && !port.equals(getWlsServerConfig().getListenPort());
      return new PortDetails(port, isSecure);
    }

    private Integer getWlsServerPort() {
      Integer listenPort = Optional.ofNullable(getWlsServerConfig()).map(WlsServerConfig::getListenPort)
          .orElse(null);

      if (listenPort == null) {
        // This can only happen if the running server pod does not exist in the WLS Domain.
        // This is a rare case where the server was deleted from the WLS Domain config.
        listenPort = getListenPortFromPod(this.pod);
      }

      return listenPort;
    }

    private Integer getListenPortFromPod(V1Pod pod) {
      return getContainer(pod.getSpec()).map(V1Container::getPorts).get().stream()
          .filter(this::isTCPProtocol).findFirst().get().getContainerPort();
    }

    boolean isTCPProtocol(V1ContainerPort port) {
      return "TCP".equals(port.getProtocol());
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

    HttpAsyncRequestStep createRequestStep(
        ShutdownManagedServerResponseStep shutdownManagedServerResponseStep) {
      HttpAsyncRequestStep requestStep = HttpAsyncRequestStep.create(createRequest(),
          shutdownManagedServerResponseStep).withTimeoutSeconds(getRequestTimeoutSeconds());
      shutdownManagedServerResponseStep.requestStep = requestStep;
      return requestStep;
    }
  }

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
      ShutdownManagedServerResponseStep shutdownManagedServerResponseStep =
          new ShutdownManagedServerResponseStep(PodHelper.getPodServerName(pod),
          processing.getRequestTimeoutSeconds(), getNext());
      HttpAsyncRequestStep requestStep = processing.createRequestStep(shutdownManagedServerResponseStep);
      return doNext(requestStep, packet);
    }

  }

  static final class ShutdownManagedServerResponseStep extends HttpResponseStep {
    private static final String SHUTDOWN_REQUEST_RETRY_COUNT = "shutdownRequestRetryCount";
    private String serverName;
    private Long requestTimeout;
    private HttpAsyncRequestStep requestStep;

    ShutdownManagedServerResponseStep(String serverName, Long requestTimeout, Step next) {
      super(next);
      this.serverName = serverName;
      this.requestTimeout = requestTimeout;
    }

    @Override
    public NextAction onSuccess(Packet packet, HttpResponse<String> response) {
      LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_SUCCESS, serverName);
      removeShutdownRequestRetryCount(packet);
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, HttpResponse<String> response) {
      if (getThrowableResponse(packet) != null) {
        Throwable throwable = getThrowableResponse(packet);
        if (getShutdownRequestRetryCount(packet) == null) {
          addShutdownRequestRetryCountToPacket(packet, 1);
          if (requestStep != null) {
            // Retry request
            LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_RETRY, serverName);
            return doNext(requestStep, packet);
          }
        }
        LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_THROWABLE, serverName, throwable.getMessage());
      } else if (getResponse(packet) == null) {
        // Request timed out
        LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_TIMEOUT, serverName, Long.toString(requestTimeout));
      } else {
        LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_FAILURE, serverName, response);
      }

      removeShutdownRequestRetryCount(packet);
      return doNext(packet);
    }

    private HttpResponse getResponse(Packet packet) {
      return packet.getSpi(HttpResponse.class);
    }

    private Throwable getThrowableResponse(Packet packet) {
      return packet.getSpi(Throwable.class);
    }

    private static Integer getShutdownRequestRetryCount(Packet packet) {
      return (Integer) packet.get(SHUTDOWN_REQUEST_RETRY_COUNT);
    }

    private static void addShutdownRequestRetryCountToPacket(Packet packet, Integer count) {
      packet.put(SHUTDOWN_REQUEST_RETRY_COUNT, count);
    }

    private static void removeShutdownRequestRetryCount(Packet packet) {
      packet.remove(SHUTDOWN_REQUEST_RETRY_COUNT);
    }

    void setHttpAsyncRequestStep(HttpAsyncRequestStep requestStep) {
      this.requestStep = requestStep;
    }
  }
}
