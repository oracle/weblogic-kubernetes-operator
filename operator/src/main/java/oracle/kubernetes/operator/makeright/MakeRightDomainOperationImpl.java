// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.DomainProcessorDelegate;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.MakeRightExecutor;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.Processors;
import oracle.kubernetes.operator.PvcAwaiterStepFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainValidationSteps;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.JobHelper;
import oracle.kubernetes.operator.helpers.PersistentVolumeClaimHelper;
import oracle.kubernetes.operator.helpers.PersistentVolumeHelper;
import oracle.kubernetes.operator.helpers.PodDisruptionBudgetHelper;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.DeleteDomainStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.MonitoringExporterSteps;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.DomainStatusUpdater.createLastStatusUpdateStep;
import static oracle.kubernetes.operator.DomainStatusUpdater.createStatusInitializationStep;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_DELETED;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;

/**
 * A factory which creates and executes steps to align the cached domain status with the value read from Kubernetes.
 */
public class MakeRightDomainOperationImpl extends MakeRightOperationImpl<DomainPresenceInfo>
    implements MakeRightDomainOperation {

  public static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private boolean inspectionRun;
  private boolean retryOnFailure;

  /**
   * Create the operation.
   *
   * @param executor an object which can be asked to execute the make right
   * @param delegate a class which handles scheduling and other types of processing
   * @param liveInfo domain presence info read from Kubernetes
   */
  public MakeRightDomainOperationImpl(
      MakeRightExecutor executor, DomainProcessorDelegate delegate, @Nonnull DomainPresenceInfo liveInfo) {
    super(executor, delegate);
    this.liveInfo = liveInfo;
  }

  private MakeRightDomainOperation cloneWith(@Nonnull DomainPresenceInfo presenceInfo) {
    final MakeRightDomainOperationImpl result = new MakeRightDomainOperationImpl(executor, delegate, presenceInfo);
    result.deleting = deleting;
    return result;
  }

  @Override
  public MakeRightDomainOperation createRetry(@Nonnull DomainPresenceInfo presenceInfo) {
    presenceInfo.setPopulated(false);
    return cloneWith(presenceInfo).withExplicitRecheck().retryOnFailure();
  }

  /**
   * Modifies the factory to run even if the domain spec is unchanged.
   *
   * @return the updated factory
   */
  @Override
  public MakeRightDomainOperation withExplicitRecheck() {
    explicitRecheck = true;
    return this;
  }

  /**
   * Set the event data that is associated with this operation.
   *
   * @param eventData event data
   * @return the updated factory
   */
  @Override
  public MakeRightDomainOperation withEventData(EventData eventData) {
    this.eventData = eventData;
    return this;
  }

  /**
   * Modifies the factory to handle shutting down the domain.
   *
   * @return the updated factory
   */
  @Override
  public MakeRightDomainOperation forDeletion() {
    deleting = true;
    return this;
  }

  /**
   * Modifies the factory to indicate that it should interrupt any current make-right thread.
   *
   * @return the updated factory
   */
  @Override
  public MakeRightDomainOperation interrupt() {
    willInterrupt = true;
    return this;
  }

  @Override
  public MakeRightDomainOperation retryOnFailure() {
    this.retryOnFailure = true;
    return this;
  }

  @Override
  public boolean isDeleting() {
    return deleting;
  }

  @Override
  public boolean isExplicitRecheck() {
    return explicitRecheck;
  }

  @Override
  public boolean isRetryOnFailure() {
    return retryOnFailure;
  }

  @Override
  public void execute() {
    executor.runMakeRight(this);
  }

  @Override
  public void setInspectionRun() {
    inspectionRun = true;
  }

  @Override
  public void setLiveInfo(@Nonnull DomainPresenceInfo info) {
    this.liveInfo = info;
  }

  @Override
  public void clear() {
    this.eventData = null;
    this.explicitRecheck = false;
    this.deleting = false;
    this.willInterrupt = false;
    this.inspectionRun = false;
  }

  @Override
  public void addToPacket(Packet packet) {
    MakeRightDomainOperation.super.addToPacket(packet);
  }


  @Override
  public boolean wasInspectionRun() {
    return inspectionRun;
  }

  @Override
  @Nonnull
  public Packet createPacket() {
    Packet packet = new Packet().with(delegate).with(liveInfo).with(this);
    packet
        .getComponents()
        .put(
            ProcessingConstants.DOMAIN_COMPONENT_NAME,
            Component.createFor(delegate.getKubernetesVersion(),
                PodAwaiterStepFactory.class, delegate.getPodAwaiterStepFactory(getNamespace()),
                JobAwaiterStepFactory.class, delegate.getJobAwaiterStepFactory(getNamespace()),
                PvcAwaiterStepFactory.class, delegate.getPvcAwaiterStepFactory()));
    return packet;
  }

  private DomainResource getDomain() {
    return liveInfo.getDomain();
  }

  private String getNamespace() {
    return liveInfo.getNamespace();
  }

  @Override
  public Step createSteps() {
    final List<Step> result = new ArrayList<>();

    result.add(new UpdateDomainPresenceInfoStep(liveInfo));
    if (deleting || domainHasDeletionTimestamp()) {
      result.add(new StartPlanStep(liveInfo, createDomainDownPlan()));
    } else {
      result.add(getCreateEventStep());
      result.add(new DomainProcessorImpl.PopulatePacketServerMapsStep());
      result.add(createStatusInitializationStep(hasEventData()));
      result.add(createListClusterResourcesStep(getNamespace()));
      result.add(createDomainValidationStep(getDomain()));
      result.add(new StartPlanStep(liveInfo, createDomainUpPlan(liveInfo)));
    }
    
    return Step.chain(result);
  }

  private boolean domainHasDeletionTimestamp() {
    return Optional.ofNullable(liveInfo.getDomain()).map(DomainResource::getMetadata)
        .map(V1ObjectMeta::getDeletionTimestamp).isPresent();
  }

  private static Step createListClusterResourcesStep(String domainNamespace) {
    return new CallBuilder().listClusterAsync(domainNamespace, new ListClusterResourcesResponseStep());
  }

  @Override
  public DomainPresenceInfo getPresenceInfo() {
    return liveInfo;
  }

  static class ListClusterResourcesResponseStep extends DefaultResponseStep<ClusterList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<ClusterList> callResponse) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      callResponse.getResult().getItems().stream().filter(c -> isForDomain(c, info))
          .forEach(info::addClusterResource);

      return doContinueListOrNext(callResponse, packet);
    }

    private boolean isForDomain(ClusterResource clusterResource, DomainPresenceInfo info) {
      return info.doesReferenceCluster(clusterResource.getMetadata().getName());
    }
  }

  private Step createDomainDownPlan() {
    return Step.chain(
        createEventStep(new EventData(DOMAIN_DELETED)),
        new DeleteDomainStep(),
        new UnregisterStatusUpdaterStep(),
        new UnregisterEventK8SObjectsStep());
  }

  private class UnregisterStatusUpdaterStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo.fromPacket(packet).ifPresent(this::unregisterStatusUpdater);
      return doNext(packet);
    }

    private void unregisterStatusUpdater(DomainPresenceInfo info) {
      info.setDeleting(true);
      executor.endScheduledDomainStatusUpdates(info);
    }
  }

  private Step createDomainValidationStep(@Nullable DomainResource domain) {
    return domain == null ? null : DomainValidationSteps.createDomainValidationSteps(getNamespace());
  }

  private Step createDomainUpPlan(DomainPresenceInfo info) {
    Step managedServerStrategy = Step.chain(
        new ManagedServersUpStep(null),
        MonitoringExporterSteps.updateExporterSidecars(),
        createLastStatusUpdateStep(new TailStep()));

    Step domainUpStrategy =
        Step.chain(
            ConfigMapHelper.createOrReplaceFluentdConfigMapStep(),
            domainIntrospectionSteps(),
            new DomainStatusStep(),
            DomainProcessorImpl.bringAdminServerUp(delegate.getPodAwaiterStepFactory(info.getNamespace())),
            managedServerStrategy);

    if (info.getDomain().getInitializeDomainOnPV() != null) {
      domainUpStrategy = Step.chain(initializePvPvcStep(), domainUpStrategy);
    }

    Step introspectionAndDomainPresenceSteps = Step.chain(ConfigMapHelper.readExistingIntrospectorConfigMap(),
        DomainPresenceStep.createDomainPresenceStep(domainUpStrategy, managedServerStrategy));

    return new UpHeadStep(introspectionAndDomainPresenceSteps);
  }

  private Step getCreateEventStep() {
    return Optional.ofNullable(eventData).map(EventHelper::createEventStep).orElse(null);
  }

  static Step domainIntrospectionSteps() {
    return Step.chain(
        ConfigMapHelper.readIntrospectionVersionStep(),
        new IntrospectionRequestStep(),
        JobHelper.createIntrospectionStartStep());
  }

  static Step initializePvPvcStep() {
    return Step.chain(PersistentVolumeHelper.createPersistentVolumeStep(null),
        PersistentVolumeClaimHelper.createPersistentVolumeClaimStep(null));
  }

  /**
   * Compares the domain introspection version to current introspection state label and requests introspection
   * if they don't match.
   */
  private static class IntrospectionRequestStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      final String requestedIntrospectVersion = getRequestedIntrospectVersion(packet);
      if (!Objects.equals(requestedIntrospectVersion, packet.get(INTROSPECTION_STATE_LABEL))) {
        packet.put(DOMAIN_INTROSPECT_REQUESTED, Optional.ofNullable(requestedIntrospectVersion).orElse("0"));
      }

      return doNext(packet);
    }

    private String getRequestedIntrospectVersion(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getIntrospectVersion)
          .orElse(null);
    }
  }

  private class DomainStatusStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo.fromPacket(packet).ifPresent(executor::scheduleDomainStatusUpdates);
      return doNext(packet);
    }
  }

  private class UpHeadStep extends Step {
    UpHeadStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return doNext(new CallBuilder().readDomainAsync(info.getDomainName(), info.getNamespace(),
          new ReadDomainResponseStep(getNext())), packet);
    }
  }

  class ReadDomainResponseStep extends ResponseStep<DomainResource> {
    ReadDomainResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainResource> callResponse) {
      DomainPresenceInfo.fromPacket(packet).ifPresent(info -> updateCache(info, callResponse.getResult()));
      return doNext(packet);
    }

    private void updateCache(DomainPresenceInfo info, DomainResource domain) {
      info.setDeleting(isBeingDeleted(domain));
      info.setDomain(domain);
    }

    private boolean isBeingDeleted(DomainResource domain) {
      return domain == null || domain.getMetadata().getDeletionTimestamp() != null;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<DomainResource> callResponse) {
      if (callResponse.getStatusCode() == HTTP_NOT_FOUND) {
        DomainPresenceInfo.fromPacket(packet).ifPresent(i -> i.setDeleting(true));
        return doNext(createDomainDownPlan(), packet);
      }
      return super.onFailure(packet, callResponse);
    }
  }

  private class UnregisterEventK8SObjectsStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo.fromPacket(packet).ifPresent(executor::unregisterDomainEventK8SObjects);
      return doNext(packet);
    }
  }

  class UpdateDomainPresenceInfoStep extends Step {

    private final DomainPresenceInfo info;

    UpdateDomainPresenceInfoStep(DomainPresenceInfo info) {
      super();
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (deleting) {
        executor.unregisterDomainPresenceInfo(info);
      } else {
        executor.registerDomainPresenceInfo(info);
      }
      return doNext(packet);
    }
  }


  class StartPlanStep extends Step {

    private final DomainPresenceInfo info;

    StartPlanStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(getNextSteps(), packet);
    }

    private Step getNextSteps() {
      if (lookForPodsAndServices()) {
        return Step.chain(getRecordExistingResourcesSteps(), getNext());
      } else {
        return getNext();
      }
    }

    private boolean lookForPodsAndServices() {
      return !info.isPopulated() && info.isNotDeleting();
    }

    private Step getRecordExistingResourcesSteps() {
      final Processors processor = new Processors() {
        @Override
        public Consumer<V1PodList> getPodListProcessing() {
          return this::processList;
        }

        private void processList(V1PodList list) {
          info.addServerNamesFromPodList(list.getItems().stream()
              .map(PodHelper::getPodServerName).collect(Collectors.toList()));
          list.getItems().forEach(this::addPod);
        }

        private void addPod(V1Pod pod) {
          Optional.ofNullable(PodHelper.getPodServerName(pod))
              .ifPresent(name -> info.setServerPodFromEvent(name, pod));
        }

        @Override
        public Consumer<V1ServiceList> getServiceListProcessing() {
          return list -> list.getItems().forEach(this::addService);
        }

        private void addService(V1Service service) {
          ServiceHelper.addToPresence(info, service);
        }

        @Override
        public Consumer<V1PodDisruptionBudgetList> getPodDisruptionBudgetListProcessing() {
          return list -> list.getItems().forEach(this::addPodDisruptionBudget);
        }

        private void addPodDisruptionBudget(V1PodDisruptionBudget pdb) {
          PodDisruptionBudgetHelper.addToPresence(info, pdb);
        }

        @Override
        public void completeProcessing(Packet packet) {
          info.getServerNames().stream().filter(
              s -> !info.getServerNamesFromPodList().contains(s)).collect(Collectors.toList())
              .forEach(name -> info.deleteServerPodFromEvent(name, null));
          info.clearServerPodNamesFromList();
        }
      };

      return executor.createNamespacedResourceSteps(processor, info, delegate.getDomainNamespaces());
    }

  }

  private static class TailStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      return doNext(packet);
    }
  }

}
