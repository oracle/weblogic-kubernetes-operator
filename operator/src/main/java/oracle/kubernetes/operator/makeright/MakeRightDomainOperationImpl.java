// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainValidationSteps;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.JobHelper;
import oracle.kubernetes.operator.helpers.PodDisruptionBudgetHelper;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
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

import static oracle.kubernetes.operator.DomainStatusUpdater.createStatusInitializationStep;
import static oracle.kubernetes.operator.DomainStatusUpdater.createStatusUpdateStep;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;

/**
 * A factory which creates and executes steps to align the cached domain status with the value read from Kubernetes.
 */
public class MakeRightDomainOperationImpl implements MakeRightDomainOperation {

  private final MakeRightExecutor executor;
  private final DomainProcessorDelegate delegate;
  @Nonnull
  private DomainPresenceInfo liveInfo;
  private boolean explicitRecheck;
  private boolean deleting;
  private boolean willInterrupt;
  private boolean inspectionRun;
  private EventHelper.EventData eventData;
  private boolean willThrow;

  /**
   * Create the operation.
   *
   * @param executor an object which can be asked to execute the make right
   * @param delegate a class which handles scheduling and other types of processing
   * @param liveInfo domain presence info read from Kubernetes
   */
  public MakeRightDomainOperationImpl(
      MakeRightExecutor executor, DomainProcessorDelegate delegate, @Nonnull DomainPresenceInfo liveInfo) {
    this.executor = executor;
    this.delegate = delegate;
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
    return cloneWith(presenceInfo).withExplicitRecheck();
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
   * @param eventItem event data
   * @param message   event message
   * @return the updated factory
   */
  public MakeRightDomainOperation withEventData(EventHelper.EventItem eventItem, String message) {
    this.eventData = new EventHelper.EventData(eventItem, message);
    return this;
  }

  /**
   * Set the event data that is associated with this operation.
   *
   * @param eventData event data
   * @return the updated factory
   */
  public MakeRightDomainOperation withEventData(EventHelper.EventData eventData) {
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
  public MakeRightDomainOperation interrupt() {
    willInterrupt = true;
    return this;
  }

  @Override
  public boolean isDeleting() {
    return deleting;
  }

  @Override
  public boolean isWillInterrupt() {
    return willInterrupt;
  }

  @Override
  public boolean isExplicitRecheck() {
    return explicitRecheck;
  }

  /**
   * Modifies the factory to indicate that it should throw.
   * For unit testing only.
   *
   * @return the updated factory
   */
  public MakeRightDomainOperation throwNPE() {
    willThrow = true;
    return this;
  }

  @Override
  public void execute() {
    executor.runMakeRight(this, this::shouldContinue);
  }

  @Override
  public void setInspectionRun() {
    inspectionRun = true;
  }

  @Override
  public @Nonnull
  DomainPresenceInfo getPresenceInfo() {
    return liveInfo;
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
  public boolean wasInspectionRun() {
    return inspectionRun;
  }

  private boolean shouldContinue(DomainPresenceInfo cachedInfo) {
    if (isNewDomain(cachedInfo)) {
      return true;
    } else if (liveInfo.isDomainProcessingHalted(cachedInfo)) {
      return false;
    } else if (shouldRecheck(cachedInfo)) {
      return true;
    }
    cachedInfo.setDomain(liveInfo.getDomain());
    return false;
  }

  private boolean isNewDomain(DomainPresenceInfo cachedInfo) {
    return cachedInfo == null || cachedInfo.getDomain() == null;
  }

  private boolean shouldRecheck(DomainPresenceInfo cachedInfo) {
    return isExplicitRecheck() || liveInfo.isGenerationChanged(cachedInfo);
  }

  @Override
  @Nonnull
  public Packet createPacket() {
    Packet packet = new Packet().with(delegate).with(liveInfo);
    packet.put(MAKE_RIGHT_DOMAIN_OPERATION, this);
    packet
        .getComponents()
        .put(
            ProcessingConstants.DOMAIN_COMPONENT_NAME,
            Component.createFor(delegate.getKubernetesVersion(),
                PodAwaiterStepFactory.class, delegate.getPodAwaiterStepFactory(getNamespace()),
                JobAwaiterStepFactory.class, delegate.getJobAwaiterStepFactory(getNamespace())));
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

    result.add(willThrow ? createThrowStep() : null);
    result.add(Optional.ofNullable(eventData).map(EventHelper::createEventStep).orElse(null));
    result.add(new DomainProcessorImpl.PopulatePacketServerMapsStep());
    result.add(createStatusInitializationStep());
    if (deleting) {
      result.add(new StartPlanStep(liveInfo, createDomainDownPlan()));
    } else {
      result.add(createListClusterResourcesStep(getNamespace()));
      result.add(createDomainValidationStep(getDomain()));
      result.add(new StartPlanStep(liveInfo, createDomainUpPlan(liveInfo)));
    }

    return Step.chain(result);
  }

  private static Step createListClusterResourcesStep(String domainNamespace) {
    return new CallBuilder().listClusterAsync(domainNamespace, new ListClusterResourcesResponseStep());
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
      return clusterResource.getDomainUid().equals(info.getDomainUid());
    }
  }

  private Step createDomainDownPlan() {
    return Step.chain(new DownHeadStep(), new DeleteDomainStep(), new UnregisterStep());
  }

  private class DownHeadStep extends Step {

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
        createStatusUpdateStep(new TailStep()));

    Step domainUpStrategy =
        Step.chain(
            ConfigMapHelper.createOrReplaceFluentdConfigMapStep(),
            domainIntrospectionSteps(),
            new DomainStatusStep(),
            DomainProcessorImpl.bringAdminServerUp(info, delegate.getPodAwaiterStepFactory(info.getNamespace())),
            managedServerStrategy);

    return Step.chain(
        new UpHeadStep(),
        ConfigMapHelper.readExistingIntrospectorConfigMap(),
        DomainPresenceStep.createDomainPresenceStep(domainUpStrategy, managedServerStrategy));
  }

  static Step domainIntrospectionSteps() {
    return Step.chain(
        ConfigMapHelper.readIntrospectionVersionStep(),
        new IntrospectionRequestStep(),
        JobHelper.createIntrospectionStartStep());
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

  private static class UpHeadStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo.fromPacket(packet).ifPresent(info -> info.setDeleting(false));
      return doNext(packet);
    }
  }

  private class UnregisterStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo.fromPacket(packet).ifPresent(executor::unregisterDomainPresenceInfo);
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
      executor.registerDomainPresenceInfo(info);

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
          return list -> list.getItems().forEach(this::addPod);
        }

        private void addPod(V1Pod pod) {
          Optional.ofNullable(PodHelper.getPodServerName(pod)).ifPresent(name -> info.setServerPod(name, pod));
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
      };

      return executor.createNamespacedResourceSteps(processor, info);
    }

  }

  private static class TailStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      return doNext(packet);
    }
  }

  // for unit testing only
  private Step createThrowStep() {
    return new ThrowStep();
  }

  // for unit testing only
  private static class ThrowStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      throw new NullPointerException("Force unit test to handle NPE");
    }
  }
}
