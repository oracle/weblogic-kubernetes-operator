// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.ShutdownType;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SecretHelper;
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

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.steps.HttpRequestProcessing.createRequestStep;

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
  public static Step createShutdownManagedServerStep(Step next, String serverName, V1Pod pod) {
    return new ShutdownManagedServerStep(next, serverName, pod);
  }

  private static String getManagedServerShutdownPath(Boolean isGracefulShutdown) {
    StringBuilder stringBuilder = new StringBuilder("/management/weblogic/latest/serverRuntime/");
    String shutdownString = isGracefulShutdown ? "shutdown" : "forceShutdown";
    return stringBuilder.append(shutdownString).toString();
  }

  private static String getManagedServerShutdownPayload(Boolean isGracefulShutdown,
      Boolean ignoreSessions, Long timeout, Boolean waitForAllSessions) {
    if (!isGracefulShutdown) {
      return "{}";
    }

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("{  \"ignoreSessions\": ")
        .append(ignoreSessions)
        .append(", \"timeout\": ")
        .append(timeout)
        .append(", \"waitForAllSessions\": ")
        .append(waitForAllSessions)
        .append("}");

    return stringBuilder.toString();
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

    ShutdownManagedServerProcessing(Packet packet, @Nonnull V1Service service, V1Pod pod) {
      super(packet, service, pod);
    }

    private HttpRequest createRequest() {
      String serverName = pod.getMetadata().getLabels().get(LabelConstants.SERVERNAME_LABEL);
      String clusterName = getClusterNameFromServiceLabel();
      Optional<List<V1EnvVar>> envVarList = Optional.ofNullable(pod.getSpec()).flatMap(this::getEnvVars);

      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      Shutdown shutdown = Optional.ofNullable(info.getDomain().getServer(serverName, clusterName))
          .map(ServerSpec::getShutdown).orElse(null);

      Boolean isGracefulShutdown = isGracefulShutdown(envVarList, shutdown);
      Long timeout = getTimeout(envVarList, shutdown);
      Boolean ignoreSessions = getIgnoreSessions(envVarList, shutdown);
      Boolean waitForAllSessions = getWaitForAllSessions(envVarList, shutdown);

      HttpRequest request = createRequestBuilder(getRequestUrl(isGracefulShutdown))
            .POST(HttpRequest.BodyPublishers.ofString(getManagedServerShutdownPayload(
                isGracefulShutdown, ignoreSessions, timeout, waitForAllSessions))).build();
      return request;
    }

    private Optional<List<V1EnvVar>> getEnvVars(V1PodSpec v1PodSpec) {
      return getContainer(v1PodSpec).map(V1Container::getEnv);
    }

    protected Optional<V1Container> getContainer(V1PodSpec v1PodSpec) {
      return v1PodSpec.getContainers().stream().filter(this::isK8sContainer).findFirst();
    }

    protected boolean isK8sContainer(V1Container c) {
      return KubernetesConstants.WLS_CONTAINER_NAME.equals(c.getName());
    }

    private String getEnvValue(List<V1EnvVar> vars, String name) {
      for (V1EnvVar var : vars) {
        if (var.getName().equals(name)) {
          return var.getValue();
        }
      }
      return null;
    }

    private Boolean isGracefulShutdown(Optional<List<V1EnvVar>> envVarList, Shutdown shutdown) {
      String shutdownType = null;
      if (envVarList.isPresent()) {
        shutdownType = getEnvValue(envVarList.get(), "SHUTDOWN_TYPE");
      }

      shutdownType = shutdownType == null ? Optional.ofNullable(shutdown).map(Shutdown::getShutdownType)
          .orElse(ShutdownType.Graceful.name()) : shutdownType;


      return shutdownType.equalsIgnoreCase(ShutdownType.Graceful.name());
    }

    private Boolean getWaitForAllSessions(Optional<List<V1EnvVar>> envVarList, Shutdown shutdown) {
      String waitForAllSessions = null;
      if (envVarList.isPresent()) {
        waitForAllSessions = getEnvValue(envVarList.get(), "SHUTDOWN_WAIT_FOR_ALL_SESSIONS");
      }

      return waitForAllSessions == null ? Optional.ofNullable(shutdown).map(Shutdown::getWaitForAllSessions)
              .orElse(Shutdown.DEFAULT_WAIT_FOR_ALL_SESSIONS) : Boolean.valueOf(waitForAllSessions);
    }

    private Boolean getIgnoreSessions(Optional<List<V1EnvVar>> envVarList, Shutdown shutdown) {
      String ignoreSessions = null;
      if (envVarList.isPresent()) {
        ignoreSessions = getEnvValue(envVarList.get(), "SHUTDOWN_IGNORE_SESSIONS");
      }

      return ignoreSessions == null ? Optional.ofNullable(shutdown).map(Shutdown::getIgnoreSessions)
              .orElse(Shutdown.DEFAULT_IGNORESESSIONS) : Boolean.valueOf(ignoreSessions);
    }

    private Long getTimeout(Optional<List<V1EnvVar>> envVarList, Shutdown shutdown) {
      String timeout = null;
      if (envVarList.isPresent()) {
        timeout = getEnvValue(envVarList.get(), "SHUTDOWN_TIMEOUT");
      }
      return timeout == null ? Optional.ofNullable(shutdown).map(Shutdown::getTimeoutSeconds)
              .orElse(Shutdown.DEFAULT_TIMEOUT) : Long.valueOf(timeout);
    }

    private String getRequestUrl(Boolean isGracefulShutdown) {
      return getServiceUrl() + getManagedServerShutdownPath(isGracefulShutdown);
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
          new ShutdownManagedServerResponseStep(PodHelper.getPodServerName(pod), getNext())), packet);
    }

  }

  static final class ShutdownManagedServerResponseStep extends HttpResponseStep {
    String serverName;

    ShutdownManagedServerResponseStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public NextAction onSuccess(Packet packet, HttpResponse<String> response) {
      LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_SUCCESS, serverName);
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, HttpResponse<String> response) {
      LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_FAILURE, serverName, response);
      return doNext(packet);
    }
  }
}
