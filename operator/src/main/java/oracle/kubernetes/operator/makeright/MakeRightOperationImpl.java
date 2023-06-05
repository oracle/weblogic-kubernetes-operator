// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import oracle.kubernetes.operator.DomainProcessorDelegate;
import oracle.kubernetes.operator.MakeRightExecutor;
import oracle.kubernetes.operator.MakeRightOperation;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.ResourcePresenceInfo;

/**
 * A factory which creates and executes steps to align the cached domain or cluster status with the value read
 * from Kubernetes.
 */
public abstract class MakeRightOperationImpl<T extends ResourcePresenceInfo> implements MakeRightOperation<T> {
  protected T liveInfo;
  protected boolean willInterrupt;
  protected boolean deleting;
  protected EventData eventData;

  protected final MakeRightExecutor executor;
  protected final DomainProcessorDelegate delegate;
  protected boolean explicitRecheck;

  /**
   * Create the operation.
   *
   * @param executor an object which can be asked to execute the make right
   * @param delegate a class which handles scheduling and other types of processing
   */
  protected MakeRightOperationImpl(
      MakeRightExecutor executor, DomainProcessorDelegate delegate) {
    this.executor = executor;
    this.delegate = delegate;
  }

  public boolean isWillInterrupt() {
    return willInterrupt;
  }

  public boolean hasEventData() {
    return eventData != null;
  }

  public EventData getEventData() {
    return eventData;
  }

}
