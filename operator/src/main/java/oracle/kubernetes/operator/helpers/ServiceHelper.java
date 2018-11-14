// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl..

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1Status;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.HealthCheckHelper.KubernetesVersion;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import org.apache.commons.lang3.builder.EqualsBuilder;

@SuppressWarnings("deprecation")
public class ServiceHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ServiceHelper() {}

  /**
   * Create asynchronous step for internal cluster service
   *
   * @param next Next processing step
   * @return Step for internal service creation
   */
  public static Step createForServerStep(Step next) {
    return new ForServerStep(next);
  }

  private static class ForServerStep extends ServiceHelperStep {
    ForServerStep(Step next) {
      super(next);
    }

    @Override
    protected ServiceStepContext createContext(Packet packet) {
      return new ForServerStepContext(this, packet);
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

  private static class ForServerStepContext extends ServerServiceStepContext {
    private final KubernetesVersion version;
    private final Integer port;
    private final Integer nodePort;

    ForServerStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      version = packet.getSPI(KubernetesVersion.class);
      port = (Integer) packet.get(ProcessingConstants.PORT);
      nodePort = (Integer) packet.get(ProcessingConstants.NODE_PORT);
    }

    @Override
    protected V1ServiceSpec createServiceSpec() {
      V1ServiceSpec serviceSpec = super.createServiceSpec();
      if (isPublishNotReadyAddressesSupported()) {
        serviceSpec.setPublishNotReadyAddresses(Boolean.TRUE);
      }
      if (nodePort == null) {
        serviceSpec.clusterIP("None");
      }
      return serviceSpec;
    }

    @Override
    protected V1ServicePort createServicePort() {
      V1ServicePort servicePort = new V1ServicePort().port(port);
      if (nodePort != null) {
        servicePort.setNodePort(nodePort);
      }
      return servicePort;
    }

    @Override
    protected String getSpecType() {
      return nodePort == null ? "ClusterIP" : "NodePort";
    }

    private boolean isPublishNotReadyAddressesSupported() {
      return version != null && version.isPublishNotReadyAddressesSupported();
    }

    @Override
    protected V1ObjectMeta createMetadata() {
      return super.createMetadata()
          .putAnnotationsItem("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true");
    }

    @Override
    protected String createServiceName() {
      return LegalNames.toServerServiceName(getDomainUID(), getServerName());
    }

    @Override
    protected V1Service getServiceFromRecord() {
      return sko.getService().get();
    }

    @Override
    protected void addServiceToRecord(@Nonnull V1Service service) {
      sko.getService().set(service);
    }

    @Override
    protected void removeServiceFromRecord() {
      sko.getService().set(null);
    }
  }

  private abstract static class ServerServiceStepContext extends ServiceStepContext {
    protected final String serverName;
    protected final ServerKubernetesObjects sko;

    ServerServiceStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
      sko = ServerKubernetesObjectsManager.getOrCreate(info, getServerName());
    }

    @Override
    protected V1ServiceSpec createServiceSpec() {
      return super.createServiceSpec()
          .putSelectorItem(LabelConstants.SERVERNAME_LABEL, getServerName());
    }

    @Override
    protected V1ObjectMeta createMetadata() {
      return super.createMetadata().putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName());
    }

    String getServerName() {
      return serverName;
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
      return getServerName().equals(getAsName());
    }

    private String getAsName() {
      return info.getDomain().getAsName();
    }

    @Override
    protected String getServiceCreatedMessageKey() {
      return isForAdminServer() ? ADMIN_SERVICE_CREATED : MANAGED_SERVICE_CREATED;
    }
  }

  private abstract static class ServiceStepContext {
    private final Step conflictStep;
    DomainPresenceInfo info;

    ServiceStepContext(Step conflictStep, Packet packet) {
      this.conflictStep = conflictStep;
      info = packet.getSPI(DomainPresenceInfo.class);
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
      return new V1Service().spec(createServiceSpec()).metadata(createMetadata());
    }

    protected V1ServiceSpec createServiceSpec() {
      return new V1ServiceSpec()
          .type(getSpecType())
          .putSelectorItem(LabelConstants.DOMAINUID_LABEL, getDomainUID())
          .putSelectorItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
          .ports(Collections.singletonList(createServicePort()));
    }

    protected V1ObjectMeta createMetadata() {
      return new V1ObjectMeta()
          .name(createServiceName())
          .namespace(getNamespace())
          .putLabelsItem(
              LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUID())
          .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, getDomainName())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    String getDomainName() {
      return getDomain().getDomainName();
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

    protected abstract void logServiceCreated(String messageKey);

    protected abstract String getSpecType();

    protected abstract V1ServicePort createServicePort();

    protected abstract V1Service getServiceFromRecord();

    protected abstract void addServiceToRecord(V1Service service);

    protected abstract void removeServiceFromRecord();

    Step verifyService(Step next) {
      V1Service service = getServiceFromRecord();
      if (service == null) {
        return createNewService(next);
      } else if (validateCurrentService(createModel(), service)) {
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
   * Factory for {@link Step} that deletes per-managed server service
   *
   * @param sko Server Kubernetes Objects
   * @param next Next processing step
   * @return Step for deleting per-managed server service
   */
  public static Step deleteServiceStep(ServerKubernetesObjects sko, Step next) {
    return new DeleteServiceStep(sko, next);
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

  /**
   * Create asynchronous step for internal cluster service
   *
   * @param next Next processing step
   * @return Step for internal service creation
   */
  public static Step createForClusterStep(Step next) {
    return new ForClusterStep(next);
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
    private final Integer port;

    ClusterStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      port = (Integer) packet.get(ProcessingConstants.PORT);
    }

    protected V1ServiceSpec createServiceSpec() {
      return super.createServiceSpec()
          .putSelectorItem(LabelConstants.CLUSTERNAME_LABEL, clusterName);
    }

    @Override
    protected String getSpecType() {
      return "ClusterIP";
    }

    protected V1ObjectMeta createMetadata() {
      return super.createMetadata().putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, clusterName);
    }

    @Override
    protected V1ServicePort createServicePort() {
      return new V1ServicePort().port(port);
    }

    protected String createServiceName() {
      return LegalNames.toClusterServiceName(getDomainUID(), clusterName);
    }

    @Override
    protected V1Service getServiceFromRecord() {
      return info.getClusters().get(clusterName);
    }

    @Override
    protected void addServiceToRecord(@Nonnull V1Service service) {
      info.getClusters().put(clusterName, service);
    }

    @Override
    protected void removeServiceFromRecord() {
      info.getClusters().remove(clusterName);
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
  }

  private static boolean validateCurrentService(V1Service build, V1Service current) {
    V1ServiceSpec buildSpec = build.getSpec();
    V1ServiceSpec currentSpec = current.getSpec();

    if (!VersionHelper.matchesResourceVersion(
        current.getMetadata(), VersionConstants.DEFAULT_DOMAIN_VERSION)) {
      return false;
    }

    String buildType = buildSpec.getType();
    String currentType = currentSpec.getType();
    if (currentType == null) {
      currentType = "ClusterIP";
    }
    if (!currentType.equals(buildType)) {
      return false;
    }

    List<V1ServicePort> buildPorts = buildSpec.getPorts();
    List<V1ServicePort> currentPorts = currentSpec.getPorts();

    outer:
    for (V1ServicePort bp : buildPorts) {
      for (V1ServicePort cp : currentPorts) {
        if (cp.getPort().equals(bp.getPort())) {
          if (!"NodePort".equals(buildType)
              || bp.getNodePort() == null
              || bp.getNodePort().equals(cp.getNodePort())) {
            continue outer;
          }
        }
      }
      return false;
    }

    return true;
  }

  /**
   * Create asynchronous step for external channel
   *
   * @param next Next processing step
   * @return Step for external channel creation
   */
  public static Step createForExternalChannelStep(Step next) {
    return new ForExternalChannelStep(next);
  }

  private static class ForExternalChannelStep extends ServiceHelperStep {
    ForExternalChannelStep(Step next) {
      super(next);
    }

    @Override
    protected ServiceStepContext createContext(Packet packet) {
      return new ExternalChannelServiceStepContext(this, packet);
    }
  }

  private static class ExternalChannelServiceStepContext extends ServerServiceStepContext {
    private final NetworkAccessPoint networkAccessPoint;

    ExternalChannelServiceStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      networkAccessPoint =
          (NetworkAccessPoint) packet.get(ProcessingConstants.NETWORK_ACCESS_POINT);
    }

    protected String getSpecType() {
      return "NodePort";
    }

    protected V1ServicePort createServicePort() {
      return new V1ServicePort()
          .port(networkAccessPoint.getListenPort())
          .nodePort(networkAccessPoint.getPublicPort());
    }

    protected V1ObjectMeta createMetadata() {
      V1ObjectMeta metadata =
          super.createMetadata().putLabelsItem(LabelConstants.CHANNELNAME_LABEL, getChannelName());

      for (Map.Entry<String, String> entry : getChannelServiceLabels().entrySet())
        metadata.putLabelsItem(entry.getKey(), entry.getValue());
      for (Map.Entry<String, String> entry : getChannelServiceAnnotations().entrySet())
        metadata.putAnnotationsItem(entry.getKey(), entry.getValue());
      return metadata;
    }

    Map<String, String> getChannelServiceLabels() {
      return getDomain().getChannelServiceLabels(getChannelName());
    }

    private Map<String, String> getChannelServiceAnnotations() {
      return getDomain().getChannelServiceAnnotations(getChannelName());
    }

    private String getChannelName() {
      return networkAccessPoint.getName();
    }

    @Override
    protected String createServiceName() {
      return LegalNames.toNAPName(getDomainUID(), getServerName(), networkAccessPoint);
    }

    @Override
    protected V1Service getServiceFromRecord() {
      return sko.getChannels().get(getChannelName());
    }

    @Override
    protected void addServiceToRecord(@Nonnull V1Service service) {
      sko.getChannels().put(getChannelName(), service);
    }

    @Override
    protected void removeServiceFromRecord() {
      sko.getChannels().remove(getChannelName());
    }
  }
}
