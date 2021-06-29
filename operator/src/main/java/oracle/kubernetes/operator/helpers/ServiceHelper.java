// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1Status;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.ActionResponseStep;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.DeleteServiceListStep;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.AdminServerSpec;
import oracle.kubernetes.weblogic.domain.model.AdminService;
import oracle.kubernetes.weblogic.domain.model.Channel;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;

import static oracle.kubernetes.operator.LabelConstants.forDomainUidSelector;
import static oracle.kubernetes.operator.LabelConstants.getCreatedByOperatorSelector;
import static oracle.kubernetes.operator.helpers.KubernetesUtils.getDomainUidLabel;
import static oracle.kubernetes.operator.helpers.OperatorServiceType.EXTERNAL;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;

public class ServiceHelper {
  public static final String CLUSTER_IP_TYPE = "ClusterIP";
  public static final String NODE_PORT_TYPE = "NodePort";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ServiceHelper() {
  }

  /**
   * Create asynchronous step for internal cluster service.
   *
   * @param next Next processing step
   * @return Step for internal service creation
   */
  public static Step createForServerStep(Step next) {
    return createForServerStep(false, next);
  }

  /**
   * Create asynchronous step for internal cluster service.
   *
   * @param isPreserveServices true, if this service is for a placeholder service with no pod
   * @param next Next processing step
   * @return Step for internal service creation
   */
  public static Step createForServerStep(boolean isPreserveServices, Step next) {
    return new ForServerStep(isPreserveServices, next);
  }

  static V1Service createServerServiceModel(Packet packet) {
    return new ServerServiceStepContext(false, null, packet).createModel();
  }

  public static void addToPresence(DomainPresenceInfo info, V1Service service) {
    OperatorServiceType.getType(service).addToPresence(info, service);
  }

  public static void updatePresenceFromEvent(DomainPresenceInfo info, V1Service service) {
    OperatorServiceType.getType(service).updateFromEvent(info, service);
  }

  public static boolean isServerService(V1Service service) {
    return OperatorServiceType.getType(service) == OperatorServiceType.SERVER;
  }

  public static boolean deleteFromEvent(DomainPresenceInfo info, V1Service service) {
    return OperatorServiceType.getType(service).deleteFromEvent(info, service);
  }

  public static String getServiceDomainUid(V1Service service) {
    return getLabelValue(service, LabelConstants.DOMAINUID_LABEL);
  }

  static String getLabelValue(V1Service service, String labelName) {
    return Optional.ofNullable(service)
          .map(V1Service::getMetadata)
          .map(V1ObjectMeta::getLabels)
          .map(l -> l.get(labelName))
          .orElse(null);
  }

  public static String getServerName(V1Service service) {
    return getLabelValue(service, LabelConstants.SERVERNAME_LABEL);
  }

  public static String getClusterName(V1Service service) {
    return getLabelValue(service, LabelConstants.CLUSTERNAME_LABEL);
  }

  static boolean isNodePortType(V1Service service) {
    return NODE_PORT_TYPE.equals(getSpecType(service));
  }

  private static String getSpecType(V1Service service) {
    return Optional.ofNullable(service.getSpec()).map(V1ServiceSpec::getType).orElse("");
  }

  /**
   * Factory for {@link Step} that deletes services associated with a specific server.
   *
   * @param serverName Server name
   * @param next Next processing step
   * @return Step for deleting per-managed server and channel services
   */
  public static Step deleteServicesStep(String serverName, Step next) {
    return new DeleteServiceStep(serverName, next);
  }

  /**
   * Create asynchronous step for internal cluster service.
   *
   * @param next Next processing step
   * @return Step for internal service creation
   */
  public static Step createForClusterStep(Step next) {
    return new ForClusterStep(next);
  }

  static V1Service createClusterServiceModel(Packet packet) {
    return new ClusterStepContext(null, packet).createModel();
  }

  private static boolean canUseCurrentService(V1Service model, V1Service current) {
    return AnnotationHelper.getHash(model).equals(AnnotationHelper.getHash(current));
  }

  /**
   * Create asynchronous step for external, NodePort service.
   *
   * @param next Next processing step
   * @return Step for creating external service
   */
  public static Step createForExternalServiceStep(Step next) {
    return new ForExternalServiceStep(next);
  }

  static V1Service createExternalServiceModel(Packet packet) {
    return new ExternalServiceStepContext(null, packet).createModel();
  }

  private static class ForServerStep extends ServiceHelperStep {
    private final boolean isPreserveServices;

    ForServerStep(boolean isPreserveServices, Step next) {
      super(next);
      this.isPreserveServices = isPreserveServices;
    }

    @Override
    protected ServiceStepContext createContext(Packet packet) {
      return new ServerServiceStepContext(isPreserveServices, this, packet);
    }
  }

  private abstract static class ServiceHelperStep extends Step {
    ServiceHelperStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doVerifyService(getNext(), packet);
    }

    private NextAction doVerifyService(Step next, Packet packet) {
      return doNext(createContext(packet).verifyService(next), packet);
    }

    protected abstract ServiceStepContext createContext(Packet packet);
  }

  private static class ServerServiceStepContext extends ServiceStepContext {
    protected final String serverName;
    protected final String clusterName;
    protected final KubernetesVersion version;
    final WlsServerConfig scan;
    private final boolean isPreserveServices;

    ServerServiceStepContext(boolean isPreserveServices, Step conflictStep, Packet packet) {
      super(conflictStep, packet, OperatorServiceType.SERVER);
      this.isPreserveServices = isPreserveServices;
      serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      scan = (WlsServerConfig) packet.get(ProcessingConstants.SERVER_SCAN);
      version = packet.getSpi(KubernetesVersion.class);
    }

    @Override
    protected V1ServiceSpec createServiceSpec() {
      V1ServiceSpec serviceSpec =
          super.createServiceSpec()
              .clusterIP(isPreserveServices ? null : "None")
              .ports(createServicePorts())
              .putSelectorItem(LabelConstants.SERVERNAME_LABEL, getServerName());
      if (isPublishNotReadyAddressesSupported()) {
        serviceSpec.setPublishNotReadyAddresses(Boolean.TRUE);
      }
      return serviceSpec;
    }

    boolean isPublishNotReadyAddressesSupported() {
      return version != null && version.isPublishNotReadyAddressesSupported();
    }

    @Override
    protected V1ObjectMeta createMetadata() {
      V1ObjectMeta metadata =
          super.createMetadata()
              .putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName())
              .putAnnotationsItem("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true");

      if (getClusterName() != null) {
        metadata.putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, getClusterName());
      }

      return metadata;
    }

    private ServerSpec getServerSpec() {
      return getDomain().getServer(getServerName(), getClusterName());
    }

    @Override
    protected Map<String, String> getServiceLabels() {
      Map<String, String> serviceLabels = getServerSpec().getServiceLabels();
      if (isForAdminServer()) {
        serviceLabels.putAll(getDomain().getAdminServerSpec().getServiceLabels());
      }
      return serviceLabels;
    }

    @Override
    protected Map<String, String> getServiceAnnotations() {
      Map<String, String> serviceAnnotations = getServerSpec().getServiceAnnotations();
      if (isForAdminServer()) {
        serviceAnnotations.putAll(getDomain().getAdminServerSpec().getServiceAnnotations());
      }
      return serviceAnnotations;
    }

    String getServerName() {
      return serverName;
    }

    private String getClusterName() {
      return clusterName;
    }

    @Override
    protected void logServiceExists() {
      LOGGER.fine(getServiceExistsMessageKey(), getDomainUid(), getServerName());
    }

    private String getServiceExistsMessageKey() {
      return isForAdminServer() ? ADMIN_SERVICE_EXISTS : MANAGED_SERVICE_EXISTS;
    }

    @Override
    protected void logServiceCreated(String messageKey) {
      LOGGER.info(messageKey, getDomainUid(), getServerName());
    }

    @Override
    protected String getServiceReplaceMessageKey() {
      return isForAdminServer() ? ADMIN_SERVICE_REPLACED : MANAGED_SERVICE_REPLACED;
    }

    private boolean isForAdminServer() {
      return getServerName().equals(domainTopology.getAdminServerName());
    }

    @Override
    protected String getServiceCreatedMessageKey() {
      return isForAdminServer() ? ADMIN_SERVICE_CREATED : MANAGED_SERVICE_CREATED;
    }

    protected List<V1ServicePort> createServicePorts() {
      if (scan == null) {
        return null;
      }

      List<V1ServicePort> ports = new ArrayList<>();
      addServicePorts(ports, scan);
      return ports;
    }

    @Override
    void addServicePortIfNeeded(List<V1ServicePort> ports, String portName, String protocol, Integer port) {
      if (port == null) {
        return;
      }

      addServicePortIfNeeded(ports, createServicePort(portName, port));
      if (isSipProtocol(protocol)) {
        addServicePortIfNeeded(ports, createSipUdpServicePort(portName, port));
      }
    }

    @Override
    protected String getSpecType() {
      return CLUSTER_IP_TYPE;
    }

    @Override
    protected String createServiceName() {
      return LegalNames.toServerServiceName(getDomainUid(), getServerName());
    }

    @Override
    protected V1Service getServiceFromRecord() {
      return info.getServerService(serverName);
    }

    @Override
    protected void addServiceToRecord(@Nonnull V1Service service) {
      info.setServerService(serverName, service);
    }

    @Override
    protected void removeServiceFromRecord() {
      info.setServerService(serverName, null);
    }
  }

  private abstract static class ServiceStepContext extends StepContextBase {

    private final Step conflictStep;
    final WlsDomainConfig domainTopology;
    private final OperatorServiceType serviceType;

    ServiceStepContext(Step conflictStep, Packet packet, OperatorServiceType serviceType) {
      super(packet.getSpi(DomainPresenceInfo.class));
      this.conflictStep = conflictStep;
      domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
      this.serviceType = serviceType;
    }

    Step getConflictStep() {
      return new ConflictStep();
    }

    V1Service createModel() {
      return withNonHashedElements(AnnotationHelper.withSha256Hash(createRecipe()));
    }

    V1Service withNonHashedElements(V1Service service) {
      V1ObjectMeta metadata = service.getMetadata();
      updateForOwnerReference(metadata);
      return service;
    }

    V1Service createRecipe() {
      return serviceType.withTypeLabel(
          new V1Service().spec(createServiceSpec()).metadata(createMetadata()));
    }

    protected V1ServiceSpec createServiceSpec() {
      V1ServiceSpec spec = new V1ServiceSpec()
          .type(getSpecType())
          .putSelectorItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
          .putSelectorItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
          .ports(createServicePorts());
      Optional.ofNullable(getSessionAffinity()).ifPresent(spec::setSessionAffinity);
      return spec;
    }

    void addServicePorts(List<V1ServicePort> ports, WlsServerConfig serverConfig) {
      for (NetworkAccessPoint networkAccessPoint : getNetworkAccessPoints(serverConfig)) {
        addNapServicePort(ports, networkAccessPoint);
      }
      if (!isIstioEnabled()) {
        addServicePortIfNeeded(ports, "default", serverConfig.getListenPort());
        addServicePortIfNeeded(ports, "default-secure", serverConfig.getSslListenPort());
        addServicePortIfNeeded(ports, "default-admin", serverConfig.getAdminPort());
      }

      Optional.ofNullable(getDomain().getMonitoringExporterSpecification()).ifPresent(specification -> {
        if (specification.getConfiguration() != null) {
          addServicePortIfNeeded(ports, getMetricsPortName(), specification.getRestPort());
        }
      });
    }

    private String getMetricsPortName() {
      return getDomain().isIstioEnabled() ? "tcp-metrics" : "metrics";
    }

    List<NetworkAccessPoint> getNetworkAccessPoints(@Nonnull WlsServerConfig config) {
      return Optional.ofNullable(config.getNetworkAccessPoints()).orElse(Collections.emptyList());
    }

    void addNapServicePort(List<V1ServicePort> ports, NetworkAccessPoint nap) {
      addServicePortIfNeeded(ports, nap.getName(), nap.getProtocol(), nap.getListenPort());
    }

    void addServicePortIfNeeded(List<V1ServicePort> ports, String portName, Integer port) {
      addServicePortIfNeeded(ports, portName, null, port);
    }

    abstract void addServicePortIfNeeded(List<V1ServicePort> ports, String portName, String protocol, Integer port);

    protected void addServicePortIfNeeded(List<V1ServicePort> ports, V1ServicePort port) {
      if (isNoDuplicatedName(ports, port) && isNoDuplicatedProtocolAndPort(ports, port)) {
        ports.add(port);
      }
    }

    private boolean isNoDuplicatedName(List<V1ServicePort> ports, V1ServicePort port) {
      return ports.stream().noneMatch(p -> Objects.equals(p.getName(), port.getName()));
    }

    private boolean isNoDuplicatedProtocolAndPort(List<V1ServicePort> ports, V1ServicePort port) {
      return ports.stream().noneMatch(p -> isProtocolMatch(p, port) && p.getPort().equals(port.getPort()));
    }

    private boolean isProtocolMatch(V1ServicePort one, V1ServicePort two) {
      if (one.getProtocol() == null) {
        return two.getProtocol() == null || "TCP".equals(two.getProtocol());
      }
      if (two.getProtocol() == null) {
        return "TCP".equals(one.getProtocol());
      }
      return one.getProtocol().equals(two.getProtocol());
    }

    V1ServicePort createServicePort(String portName, Integer port) {
      return new V1ServicePort()
          .name(LegalNames.toDns1123LegalName(portName))
          .port(port)
          .protocol("TCP");
    }

    V1ServicePort createSipUdpServicePort(String portName, Integer port) {
      if (isIstioEnabled()) {
        // The introspector will have already prefixed the portName with either "tcp-" or "tls-". Remove the prefix.
        portName = portName.substring(4);
      }

      return new V1ServicePort()
          .name("udp-" + LegalNames.toDns1123LegalName(portName))
          .port(port)
          .protocol("UDP");
    }

    protected boolean isSipProtocol(String protocol) {
      return "sip".equals(protocol) || "sips".equals(protocol);
    }

    protected V1ObjectMeta createMetadata() {
      V1ObjectMeta metadata =
          new V1ObjectMeta().name(createServiceName()).namespace(getNamespace());

      // Add custom labels
      getServiceLabels().forEach(metadata::putLabelsItem);

      metadata
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
          .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, getDomainName())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");

      // Add custom annotations
      getServiceAnnotations().forEach(metadata::putAnnotationsItem);
      return metadata;
    }

    String getDomainName() {
      return domainTopology.getName();
    }

    Domain getDomain() {
      return info.getDomain();
    }

    String getDomainUid() {
      return getDomain().getDomainUid();
    }

    String getNamespace() {
      return info.getNamespace();
    }

    boolean isIstioEnabled() {
      return getDomain().isIstioEnabled();
    }

    protected abstract String createServiceName();

    abstract Map<String, String> getServiceLabels();

    abstract Map<String, String> getServiceAnnotations();

    String getSessionAffinity() {
      return null;
    }

    protected abstract void logServiceCreated(String messageKey);

    protected abstract String getSpecType();

    protected abstract List<V1ServicePort> createServicePorts();

    protected abstract V1Service getServiceFromRecord();

    protected abstract void addServiceToRecord(V1Service service);

    protected abstract void removeServiceFromRecord();

    Step verifyService(Step next) {
      V1Service service = getServiceFromRecord();
      if (service == null) {
        return createNewService(next);
      } else if (canUseCurrentService(createModel(), service)) {
        logServiceExists();
        return next;
      } else {
        removeServiceFromRecord();
        return deleteAndReplaceService(next);
      }
    }

    protected abstract void logServiceExists();

    private Step createNewService(Step next) {
      return createService(getServiceCreatedMessageKey(), next);
    }

    protected abstract String getServiceCreatedMessageKey();

    private Step deleteAndReplaceService(Step next) {
      if (serviceType == EXTERNAL) {
        return deleteAndReplaceNodePortService();
      } else {
        V1DeleteOptions deleteOptions = new V1DeleteOptions();
        return new CallBuilder()
            .deleteServiceAsync(
                createServiceName(), getNamespace(), getDomainUid(), deleteOptions, new DeleteServiceResponse(next));
      }
    }

    private Step deleteAndReplaceNodePortService() {
      return new CallBuilder()
              .withLabelSelectors(forDomainUidSelector(info.getDomainUid()), getCreatedByOperatorSelector())
              .listServiceAsync(
                      getNamespace(),
                      new ActionResponseStep<V1ServiceList>() {
                      public Step createSuccessStep(V1ServiceList result, Step next) {
                        Collection<V1Service> c = Optional.ofNullable(result).map(list -> list.getItems().stream()
                                  .filter(s -> isNodePortType(s))
                                  .collect(Collectors.toList())).orElse(new ArrayList<>());
                        return new DeleteServiceListStep(c, createReplacementService(next));
                      }
                    });
    }

    private Step createReplacementService(Step next) {
      return createService(getServiceReplaceMessageKey(), next);
    }

    protected abstract String getServiceReplaceMessageKey();

    private Step createService(String messageKey, Step next) {
      return new CallBuilder()
          .createServiceAsync(getNamespace(), createModel(), new CreateResponse(messageKey, next));
    }

    private class ConflictStep extends Step {
      @Override
      public NextAction apply(Packet packet) {
        return doNext(
            new CallBuilder()
                .readServiceAsync(
                    createServiceName(), getNamespace(), getDomainUid(), new ReadServiceResponse(conflictStep)),
            packet);
      }

      @Override
      public boolean equals(Object other) {
        if (other == this) {
          return true;
        }
        if (!(other instanceof ConflictStep)) {
          return false;
        }
        ConflictStep rhs = ((ConflictStep) other);
        return new EqualsBuilder().append(conflictStep, rhs.getConflictStep()).isEquals();
      }

      private Step getConflictStep() {
        return conflictStep;
      }
    }

    private class ReadServiceResponse extends DefaultResponseStep<V1Service> {
      ReadServiceResponse(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1Service> callResponse) {
        return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
            ? onSuccess(packet, callResponse)
            : onFailure(getConflictStep(), packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Service> callResponse) {
        V1Service service = callResponse.getResult();
        if (service == null) {
          removeServiceFromRecord();
        } else {
          addServiceToRecord(service);
        }
        return doNext(packet);
      }
    }

    private class DeleteServiceResponse extends ResponseStep<V1Status> {
      DeleteServiceResponse(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1Status> callResponse) {
        return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
            ? onSuccess(packet, callResponse)
            : onFailure(getConflictStep(), packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Status> callResponse) {
        return doNext(createReplacementService(getNext()), packet);
      }
    }

    private class CreateResponse extends ResponseStep<V1Service> {
      private final String messageKey;

      CreateResponse(String messageKey, Step next) {
        super(next);
        this.messageKey = messageKey;
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1Service> callResponse) {
        if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
          return updateDomainStatus(packet, callResponse);
        } else {
          return onFailure(getConflictStep(), packet, callResponse);
        }
      }

      private NextAction updateDomainStatus(Packet packet, CallResponse<V1Service> callResponse) {
        return doNext(DomainStatusUpdater.createFailureRelatedSteps(callResponse, null), packet);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Service> callResponse) {
        logServiceCreated(messageKey);
        addServiceToRecord(callResponse.getResult());
        return doNext(packet);
      }
    }
  }

  private static class DeleteServiceStep extends Step {
    private final String serverName;

    DeleteServiceStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return doNext(createActionStep(info), packet);
    }

    private Step createActionStep(DomainPresenceInfo info) {
      return Optional.ofNullable(info.removeServerService(serverName))
            .map(V1Service::getMetadata)
            .map(this::deleteService)
            .orElse(getNext());
    }

    Step deleteService(V1ObjectMeta metadata) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      return new CallBuilder()
          .deleteServiceAsync(metadata.getName(),
              metadata.getNamespace(), getDomainUidLabel(metadata), deleteOptions,
              new DefaultResponseStep<>(getNext()));
    }
  }

  private static class ForClusterStep extends ServiceHelperStep {
    ForClusterStep(Step next) {
      super(next);
    }

    @Override
    protected ServiceStepContext createContext(Packet packet) {
      return new ClusterStepContext(this, packet);
    }
  }

  private static class ClusterStepContext extends ServiceStepContext {
    private final String clusterName;
    private final WlsDomainConfig config;

    ClusterStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet, OperatorServiceType.CLUSTER);
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    }

    protected V1ServiceSpec createServiceSpec() {
      return super.createServiceSpec()
          .putSelectorItem(LabelConstants.CLUSTERNAME_LABEL, clusterName);
    }

    protected List<V1ServicePort> createServicePorts() {
      List<V1ServicePort> ports = new ArrayList<>();
      for (WlsServerConfig server : getServerConfigs(config.getClusterConfig(clusterName))) {
        addServicePorts(ports, server);
      }

      return ports;
    }

    private List<WlsServerConfig> getServerConfigs(WlsClusterConfig clusterConfig) {
      return Optional.ofNullable(clusterConfig)
          .flatMap(c -> Optional.ofNullable(c.getServerConfigs()))
          .orElse(Collections.emptyList());
    }

    @Override
    void addServicePortIfNeeded(List<V1ServicePort> ports, String portName, String protocol, Integer port) {
      if (port != null) {
        addServicePortIfNeeded(ports, createServicePort(portName, port));
      }
      if (isSipProtocol(protocol)) {
        V1ServicePort udpPort = createSipUdpServicePort(portName, port);
        addServicePortIfNeeded(ports, udpPort);
      }
    }

    @Override
    protected String getSpecType() {
      return CLUSTER_IP_TYPE;
    }

    protected V1ObjectMeta createMetadata() {
      return super.createMetadata().putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, clusterName);
    }

    protected String createServiceName() {
      return LegalNames.toClusterServiceName(getDomainUid(), clusterName);
    }

    @Override
    protected V1Service getServiceFromRecord() {
      return info.getClusterService(clusterName);
    }

    @Override
    protected void addServiceToRecord(@Nonnull V1Service service) {
      info.setClusterService(clusterName, service);
    }

    @Override
    protected void removeServiceFromRecord() {
      info.removeClusterService(clusterName);
    }

    @Override
    protected void logServiceCreated(String messageKey) {
      LOGGER.info(messageKey, getDomainUid(), clusterName);
    }

    @Override
    protected void logServiceExists() {
      LOGGER.fine(CLUSTER_SERVICE_EXISTS, getDomainUid(), clusterName);
    }

    @Override
    protected String getServiceCreatedMessageKey() {
      return CLUSTER_SERVICE_CREATED;
    }

    @Override
    protected String getServiceReplaceMessageKey() {
      return CLUSTER_SERVICE_REPLACED;
    }

    ClusterSpec getClusterSpec() {
      return getDomain().getCluster(clusterName);
    }

    @Override
    Map<String, String> getServiceLabels() {
      return getClusterSpec().getClusterLabels();
    }

    @Override
    Map<String, String> getServiceAnnotations() {
      return getClusterSpec().getClusterAnnotations();
    }

    @Override
    String getSessionAffinity() {
      return getClusterSpec().getClusterSessionAffinity();
    }
  }

  private static class ForExternalServiceStep extends ServiceHelperStep {
    ForExternalServiceStep(Step next) {
      super(next);
    }

    @Override
    protected ServiceStepContext createContext(Packet packet) {
      return new ExternalServiceStepContext(this, packet);
    }
  }

  private static class ExternalServiceStepContext extends ServiceStepContext {

    private final String adminServerName;

    ExternalServiceStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet, EXTERNAL);
      adminServerName = (String) packet.get(ProcessingConstants.SERVER_NAME);
    }

    @Override
    protected V1ObjectMeta createMetadata() {
      return super.createMetadata().putLabelsItem(LabelConstants.SERVERNAME_LABEL, adminServerName);
    }

    @Override
    protected V1ServiceSpec createServiceSpec() {
      return super.createServiceSpec()
          .putSelectorItem(LabelConstants.SERVERNAME_LABEL, adminServerName);
    }

    @Override
    protected String createServiceName() {
      return LegalNames.toExternalServiceName(getDomainUid(), adminServerName);
    }

    @Override
    protected String getSpecType() {
      return NODE_PORT_TYPE;
    }

    @Override
    protected V1Service getServiceFromRecord() {
      return info.getExternalService(adminServerName);
    }

    @Override
    protected void addServiceToRecord(V1Service service) {
      info.setExternalService(adminServerName, service);
    }

    @Override
    protected void removeServiceFromRecord() {
      info.setExternalService(adminServerName, null);
    }

    @Override
    protected String getServiceCreatedMessageKey() {
      return EXTERNAL_CHANNEL_SERVICE_CREATED;
    }

    @Override
    protected String getServiceReplaceMessageKey() {
      return EXTERNAL_CHANNEL_SERVICE_REPLACED;
    }

    @Override
    Map<String, String> getServiceLabels() {
      return getNullableAdminService().map(AdminService::getLabels).orElse(Collections.emptyMap());
    }

    @Override
    Map<String, String> getServiceAnnotations() {
      return getNullableAdminService().map(AdminService::getAnnotations).orElse(Collections.emptyMap());
    }

    @Override
    protected void logServiceCreated(String messageKey) {
      LOGGER.info(messageKey, getDomainUid());
    }

    @Override
    protected void logServiceExists() {
      LOGGER.fine(EXTERNAL_CHANNEL_SERVICE_EXISTS, getDomainUid());
    }

    protected List<V1ServicePort> createServicePorts() {
      List<V1ServicePort> ports = new ArrayList<>();
      WlsServerConfig scan = domainTopology.getServerConfig(domainTopology.getAdminServerName());
      if (scan == null) {
        return null;
      }

      addServicePorts(ports, scan);
      return ports;
    }

    @Override
    void addServicePortIfNeeded(List<V1ServicePort> ports, String channelName, String protocol, Integer internalPort) {
      Channel channel = getChannel(channelName);

      if (channel == null && isIstioEnabled()) {
        if (channelName != null) {
          String[] tokens = channelName.split("-");
          if (tokens.length > 0) {
            if ("http".equals(tokens[0]) || "https".equals(tokens[0]) || "tcp".equals(tokens[0])
                  || "tls".equals(tokens[0])) {
              int index = channelName.indexOf('-');
              channel = getChannel(channelName.substring(index + 1));
            }
          }
        }
      }
      if (channel == null || internalPort == null) {
        return;
      }

      addServicePortIfNeeded(ports,
          createServicePort(channelName, internalPort)
            .nodePort(Optional.ofNullable(channel.getNodePort()).orElse(internalPort)));
    }

    private Channel getChannel(String channelName) {
      return getNullableAdminService().map(a -> a.getChannel(channelName)).orElse(null);
    }

    private Optional<AdminService> getNullableAdminService() {
      return Optional.ofNullable(getDomain().getAdminServerSpec())
          .map(AdminServerSpec::getAdminService);
    }
  }
}
