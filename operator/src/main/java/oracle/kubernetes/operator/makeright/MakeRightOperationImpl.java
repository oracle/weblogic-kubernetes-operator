// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import oracle.kubernetes.operator.DomainProcessorDelegate;
import oracle.kubernetes.operator.MakeRightExecutor;
import oracle.kubernetes.operator.MakeRightOperation;
import oracle.kubernetes.operator.helpers.ResourcePresenceInfo;

/**
 * A factory which creates and executes steps to align the cached domain status with the value read from Kubernetes.
 */
public abstract class MakeRightOperationImpl implements MakeRightOperation {

  final MakeRightExecutor executor;
  final DomainProcessorDelegate delegate;

  /**
   * Create the operation.
   *  @param executor an object which can be asked to execute the make right
   * @param delegate a class which handles scheduling and other types of processing
   */
  public MakeRightOperationImpl(
      MakeRightExecutor executor, DomainProcessorDelegate delegate) {
    this.executor = executor;
    this.delegate = delegate;
  }

  public abstract ResourcePresenceInfo getPresenceInfo();
}
