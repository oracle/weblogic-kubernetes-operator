// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl..

package oracle.kubernetes.operator.helpers;

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

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1Status;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
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

public class ServiceHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ServiceHelper() {}

  /**
   * Create asynchronous step for internal cluster service.
   *
   * @param next Next processing step
   * @return Step for internal service creation
   */
  public static Step createForServerStep(Step next) {
    return new ForServerStep(next);
  }

  static V1Service createServerServiceModel(Packet packet) {
    return new ServerServiceStepContext(null, packet).createModel();
  }

  public static void addToPresence(DomainPresenceInfo info, V1Service service) {
    KubernetesServiceType.getType(service).addToPresence(info, service);
  }

  public static void updatePresenceFromEvent(DomainPresenceInfo info, V1Service service) {
    KubernetesServiceType.getType(service).updateFromEvent(info, service);
  }

  public static V1Service[] getServerServices(DomainPresenceInfo info) {
    return KubernetesServiceType.SERVER.getServices(info);
  }

  public static boolean isServerService(V1Service service) {
    return KubernetesServiceType.getType(service) == KubernetesServiceType.SERVER;
  }

  public static boolean deleteFromEvent(DomainPresenceInfo info, V1Service service) {
    return KubernetesServiceType.getType(service).deleteFromEvent(info, service);
  }

  public static String getServiceDomainUID(V1Service service) {
    return getLabelValue(service, LabelConstants.DOMAINUID_LABEL);
  }

  static String getLabelValue(V1Service service, String labelName) {
    if (service == null) return null;

    V1ObjectMeta meta = service.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(labelName);
    }
    return null;
  }

  public static String getServerName(V1Service service) {
    return getLabelValue(service, LabelConstants.SERVERNAME_LABEL);
  }

  public static String getClusterName(V1Service service) {
    return getLabelValue(service, LabelConstants.CLUSTERNAME_LABEL);
  }

  private static class ForServerStep extends ServiceHelperStep {
    ForServerStep(Step next) {
      super(next);
    }

    @Override
    protected ServiceStepContext createContext(Packet packet) {
      return new ServerServiceStepContext(this, packet);
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

    ServerServiceStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet, KubernetesServiceType.SERVER);
      serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      scan = (WlsServerConfig) packet.get(ProcessingConstants.SERVER_SCAN);
      version = packet.getSPI(KubernetesVersion.class);
    }

    @Override
    protected V1ServiceSpec createServiceSpec() {
      V1ServiceSpec serviceSpec =
          super.createServiceSpec()
              .clusterIP("None")
              .ports(createServicePorts())
              .putSelectorItem(LabelConstants.SERVERNAME_LABEL, getServerName());
      if (isPublishNotReadyAddressesSupported())
        serviceSpec.setPublishNotReadyAddresses(Boolean.TRUE);
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

      if (getClusterName() != null)
        metadata.putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, getClusterName());

      return metadata;
    }

    private ServerSpec getServerSpec() {
      return getDomain().getServer(getServerName(), getClusterName());
    }

    @Override
    protected Map<String, String> getServiceLabels() {
      return getServerSpec().getServiceLabels();
    }

    @Override
    protected Map<String, String> getServiceAnnotations() {
      return getServerSpec().getServiceAnnotations();
    }

    String getServerName() {
      return serverName;
    }

    private String getClusterName() {
      return clusterName;
    }

    @Override
    protected void logServiceExists() {
      LOGGER.fine(getServiceExistsMessageKey(), getDomainUID(), getServerName());
    }

    private String getServiceExistsMessageKey() {
      return isForAdminServer() ? ADMIN_SERVICE_EXISTS : MANAGED_SERVICE_EXISTS;
    }

    @Override
    protected void logServiceCreated(String messageKey) {
      LOGGER.info(messageKey, getDomainUID(), getServerName());
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
      if (scan == null) return null;

      ports = null;
      addServicePorts(scan);
      return ports;
    }

    @Override
    void addServicePortIfNeeded(String portName, Integer port) {
      if (port == null) return;

      addPort(createServicePort(portName, port));
    }

    @Override
    protected String getSpecType() {
      return "ClusterIP";
    }

    @Override
    protected String createServiceName() {
      return LegalNames.toServerServiceName(getDomainUID(), getServerName());
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

  private abstract static class ServiceStepContext {
    private final Step conflictStep;
    protected List<V1ServicePort> ports;
    DomainPresenceInfo info;
    WlsDomainConfig domainTopology;
    private KubernetesServiceType serviceType;

    ServiceStepContext(Step conflictStep, Packet packet, KubernetesServiceType serviceType) {
      this.conflictStep = conflictStep;
      info = packet.getSPI(DomainPresenceInfo.class);
      domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
      this.serviceType = serviceType;
    }

    Step getConflictStep() {
      return new ConflictStep();
    }

    private class ConflictStep extends Step {
      @Override
      public NextAction apply(Packet packet) {
        return doNext(
            new CallBuilder()
                .readServiceAsync(
                    createServiceName(), getNamespace(), new ReadServiceResponse(conflictStep)),
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

    V1Service createModel() {
      return AnnotationHelper.withSha256Hash(createRecipe());
    }

    V1Service createRecipe() {
      return serviceType.withTypeLabel(
          new V1Service().spec(createServiceSpec()).metadata(createMetadata()));
    }

    protected V1ServiceSpec createServiceSpec() {
      return new V1ServiceSpec()
          .type(getSpecType())
          .putSelectorItem(LabelConstants.DOMAINUID_LABEL, getDomainUID())
          .putSelectorItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
          .ports(createServicePorts());
    }

    void addServicePorts(WlsServerConfig serverConfig) {
      getNetworkAccessPoints(serverConfig).forEach(this::addNapServicePort);
      addServicePortIfNeeded("default", serverConfig.getListenPort());
      addServicePortIfNeeded("default-secure", serverConfig.getSslListenPort());
      addServicePortIfNeeded("default-admin", serverConfig.getAdminPort());
    }

    List<NetworkAccessPoint> getNetworkAccessPoints(@Nonnull WlsServerConfig config) {
      return Optional.ofNullable(config.getNetworkAccessPoints()).orElse(Collections.emptyList());
    }

    void addPort(V1ServicePort port) {
      if (ports == null) ports = new ArrayList<>();
      ports.add(port);
    }

    void addNapServicePort(NetworkAccessPoint nap) {
      addServicePortIfNeeded(nap.getName(), nap.getListenPort());
    }

    abstract void addServicePortIfNeeded(String portName, Integer port);

    V1ServicePort createServicePort(String portName, Integer port) {
      return new V1ServicePort()
          .name(LegalNames.toDNS1123LegalName(portName))
          .port(port)
          .protocol("TCP");
    }

    protected V1ObjectMeta createMetadata() {
      V1ObjectMeta metadata =
          new V1ObjectMeta().name(createServiceName()).namespace(getNamespace());

      // Add custom labels
      getServiceLabels().forEach(metadata::putLabelsItem);

      metadata
          .putLabelsItem(
              LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUID())
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

    String getDomainUID() {
      return getDomain().getDomainUID();
    }

    String getNamespace() {
      return info.getNamespace();
    }

    protected abstract String createServiceName();

    abstract Map<String, String> getServiceLabels();

    abstract Map<String, String> getServiceAnnotations();

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

    protected abstract void logServiceExists();

    private Step createNewService(Step next) {
      return createService(getServiceCreatedMessageKey(), next);
    }

    protected abstract String getServiceCreatedMessageKey();

    private Step deleteAndReplaceService(Step next) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      return new CallBuilder()
          .deleteServiceAsync(
              createServiceName(), getNamespace(), deleteOptions, new DeleteServiceResponse(next));
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

    private Step createReplacementService(Step next) {
      return createService(getServiceReplaceMessageKey(), next);
    }

    protected abstract String getServiceReplaceMessageKey();

    private Step createService(String messageKey, Step next) {
      return new CallBuilder()
          .createServiceAsync(getNamespace(), createModel(), new CreateResponse(messageKey, next));
    }

    private class CreateResponse extends ResponseStep<V1Service> {
      private String messageKey;

      CreateResponse(String messageKey, Step next) {
        super(next);
        this.messageKey = messageKey;
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1Service> callResponse) {
        return onFailure(getConflictStep(), packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Service> callResponse) {
        logServiceCreated(messageKey);
        addServiceToRecord(callResponse.getResult());
        return doNext(packet);
      }
    }
  }

  /**
   * Factory for {@link Step} that deletes per-managed server and channel services.
   *
   * @param sko Server Kubernetes Objects
   * @param next Next processing step
   * @return Step for deleting per-managed server and channel services
   */
  public static Step deleteServicesStep(ServerKubernetesObjects sko, Step next) {
    return new DeleteServicesIteratorStep(sko, next);
  }

  private static class DeleteServicesIteratorStep extends Step {
    private final ServerKubernetesObjects sko;

    DeleteServicesIteratorStep(ServerKubernetesObjects sko, Step next) {
      super(next);
      this.sko = sko;
    }

    @Override
    public NextAction apply(Packet packet) {
      Collection<StepAndPacket> startDetails = new ArrayList<>();

      startDetails.add(new StepAndPacket(new DeleteServiceStep(sko, null), packet.clone()));
      ConcurrentMap<String, V1Service> channels = sko.getChannels();
      for (Map.Entry<String, V1Service> entry : channels.entrySet()) {
        startDetails.add(
            new StepAndPacket(
                new DeleteChannelServiceStep(channels, entry.getKey(), null), packet.clone()));
      }

      if (startDetails.isEmpty()) {
        return doNext(packet);
      }
      return doForkJoin(getNext(), packet, startDetails);
    }
  }

  private static class DeleteServiceStep extends Step {
    private final ServerKubernetesObjects sko;

    DeleteServiceStep(ServerKubernetesObjects sko, Step next) {
      super(next);
      this.sko = sko;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      V1Service oldService = removeServiceFromRecord();

      if (oldService != null) {
        return doNext(
            deleteService(oldService.getMetadata().getName(), info.getNamespace()), packet);
      }
      return doNext(packet);
    }

    Step deleteService(String name, String namespace) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      return new CallBuilder()
          .deleteServiceAsync(name, namespace, deleteOptions, new DefaultResponseStep<>(getNext()));
    }

    // Set service to null so that watcher doesn't try to recreate service
    private V1Service removeServiceFromRecord() {
      return sko.getService().getAndSet(null);
    }
  }

  private static class DeleteChannelServiceStep extends Step {
    private final ConcurrentMap<String, V1Service> channels;
    private final String channelName;

    DeleteChannelServiceStep(
        ConcurrentMap<String, V1Service> channels, String channelName, Step next) {
      super(next);
      this.channels = channels;
      this.channelName = channelName;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      V1Service oldService = removeServiceFromRecord();

      if (oldService != null) {
        return doNext(
            deleteService(oldService.getMetadata().getName(), info.getNamespace()), packet);
      }
      return doNext(packet);
    }

    Step deleteService(String name, String namespace) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      return new CallBuilder()
          .deleteServiceAsync(name, namespace, deleteOptions, new DefaultResponseStep<>(getNext()));
    }

    // Set service to null so that watcher doesn't try to recreate service
    private V1Service removeServiceFromRecord() {
      return channels.remove(channelName);
    }
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
      super(conflictStep, packet, KubernetesServiceType.CLUSTER);
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    }

    protected V1ServiceSpec createServiceSpec() {
      return super.createServiceSpec()
          .putSelectorItem(LabelConstants.CLUSTERNAME_LABEL, clusterName);
    }

    Map<String, V1ServicePort> ports = new HashMap<>();

    protected List<V1ServicePort> createServicePorts() {
      for (WlsServerConfig server : getServerConfigs(config.getClusterConfig(clusterName)))
        addServicePorts(server);

      return ports.isEmpty() ? null : new ArrayList<>(ports.values());
    }

    private List<WlsServerConfig> getServerConfigs(WlsClusterConfig clusterConfig) {
      return Optional.ofNullable(clusterConfig)
          .flatMap(c -> Optional.ofNullable(c.getServerConfigs()))
          .orElse(Collections.emptyList());
    }

    void addServicePortIfNeeded(String portName, Integer port) {
      if (port != null) {
        ports.putIfAbsent(portName, createServicePort(portName, port));
      }
    }

    @Override
    protected String getSpecType() {
      return "ClusterIP";
    }

    protected V1ObjectMeta createMetadata() {
      return super.createMetadata().putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, clusterName);
    }

    protected String createServiceName() {
      return LegalNames.toClusterServiceName(getDomainUID(), clusterName);
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
      LOGGER.info(messageKey, getDomainUID(), clusterName);
    }

    @Override
    protected void logServiceExists() {
      LOGGER.fine(CLUSTER_SERVICE_EXISTS, getDomainUID(), clusterName);
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
      super(conflictStep, packet, KubernetesServiceType.EXTERNAL);
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
      return LegalNames.toExternalServiceName(getDomainUID(), adminServerName);
    }

    @Override
    protected String getSpecType() {
      return "NodePort";
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
      return getAdminService().map(AdminService::getLabels).orElse(Collections.emptyMap());
    }

    @Override
    Map<String, String> getServiceAnnotations() {
      return getAdminService().map(AdminService::getAnnotations).orElse(Collections.emptyMap());
    }

    @Override
    protected void logServiceCreated(String messageKey) {
      LOGGER.info(messageKey, getDomainUID());
    }

    @Override
    protected void logServiceExists() {
      LOGGER.fine(EXTERNAL_CHANNEL_SERVICE_EXISTS, getDomainUID());
    }

    protected List<V1ServicePort> createServicePorts() {
      WlsServerConfig scan = domainTopology.getServerConfig(domainTopology.getAdminServerName());
      if (scan == null) return null;

      addServicePorts(scan);
      return ports;
    }

    void addServicePortIfNeeded(String channelName, Integer internalPort) {
      Channel channel = getChannel(channelName);
      if (channel == null || internalPort == null) return;

      addPort(
          createServicePort(channelName, internalPort)
              .nodePort(Optional.ofNullable(channel.getNodePort()).orElse(internalPort)));
    }

    private Channel getChannel(String channelName) {
      return getAdminService().map(a -> a.getChannel(channelName)).orElse(null);
    }

    private Optional<AdminService> getAdminService() {
      return Optional.ofNullable(getDomain().getAdminServerSpec())
          .map(AdminServerSpec::getAdminService);
    }
  }
}
