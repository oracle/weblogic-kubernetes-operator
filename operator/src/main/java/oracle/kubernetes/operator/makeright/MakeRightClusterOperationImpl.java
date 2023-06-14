// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.DomainProcessorDelegate;
import oracle.kubernetes.operator.MakeRightClusterOperation;
import oracle.kubernetes.operator.MakeRightExecutor;
import oracle.kubernetes.operator.helpers.ClusterPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.ClusterResourceEventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.jetbrains.annotations.NotNull;

/**
 * A factory which creates and executes steps to align the cached cluster status with the value read from Kubernetes.
 */
public class MakeRightClusterOperationImpl extends MakeRightOperationImpl<ClusterPresenceInfo>
    implements MakeRightClusterOperation {

  /**
   * Create the operation.
   *
   * @param executor an object which can be asked to execute the make right
   * @param delegate a class which handles scheduling and other types of processing
   * @param liveInfo cluster presence info read from Kubernetes
   */
  public MakeRightClusterOperationImpl(
      MakeRightExecutor executor, DomainProcessorDelegate delegate, @Nonnull ClusterPresenceInfo liveInfo) {
    super(executor, delegate);
    this.liveInfo = liveInfo;
  }

  /**
   * Modifies the factory to run even if the domain spec is unchanged.
   *
   * @return the updated factory
   */
  @Override
  public MakeRightClusterOperation withExplicitRecheck() {
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
  public MakeRightClusterOperation withEventData(ClusterResourceEventData eventData) {
    this.eventData = eventData;
    return this;
  }

  /**
   * Modifies the factory to indicate that it should interrupt any current make-right thread.
   *
   * @return the updated factory
   */
  @Override
  public MakeRightClusterOperation interrupt() {
    willInterrupt = true;
    return this;
  }

  @Override
  public void execute() {
    executor.runMakeRight(this);
  }

  @Override
  @Nonnull
  public Packet createPacket() {
    return new Packet().with(delegate).with(liveInfo).with(this);
  }

  @Override
  public Step createSteps() {
    final List<Step> result = new ArrayList<>();
    result.add(getStartPlanStep());
    result.add(Optional.ofNullable(eventData).map(EventHelper::createEventStep).orElse(null));
    return Step.chain(result);
  }

  @NotNull
  private StartPlanStep getStartPlanStep() {
    if (isDeleting()) {
      return new StartPlanStep(liveInfo, true);
    } else {
      return new StartPlanStep(liveInfo, false);
    }
  }

  private boolean isDeleting() {
    return getEventItem() == EventHelper.EventItem.CLUSTER_DELETED;
  }

  private EventHelper.EventItem getEventItem() {
    return Optional.ofNullable(getEventData()).map(EventData::getItem).orElse(null);
  }

  @Override
  public void addToPacket(Packet packet) {
    // no op
  }

  @Override
  public ClusterPresenceInfo getPresenceInfo() {
    return liveInfo;
  }

  @Override
  public boolean isExplicitRecheck() {
    return explicitRecheck;
  }

  class StartPlanStep extends Step {

    private final ClusterPresenceInfo info;
    private final boolean deleting;

    StartPlanStep(ClusterPresenceInfo info, boolean deleting) {
      super();
      this.info = info;
      this.deleting = deleting;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (deleting) {
        executor.unregisterClusterPresenceInfo(info);
      } else {
        executor.registerClusterPresenceInfo(info);
      }

      return doNext(getNext(), packet);
    }
  }
}
