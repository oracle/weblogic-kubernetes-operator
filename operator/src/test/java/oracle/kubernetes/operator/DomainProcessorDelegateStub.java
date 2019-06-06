// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStrictStub;

import io.kubernetes.client.models.V1Pod;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;

/** A test stub for processing domains in unit tests. */
public abstract class DomainProcessorDelegateStub implements DomainProcessorDelegate {
  private FiberTestSupport testSupport;

  public static DomainProcessorDelegate createDelegate(KubernetesTestSupport testSupport) {
    return createStrictStub(DomainProcessorDelegateStub.class, testSupport);
  }

  public DomainProcessorDelegateStub(FiberTestSupport testSupport) {
    this.testSupport = testSupport;
  }

  @Override
  public boolean isNamespaceRunning(String namespace) {
    return true;
  }

  @Override
  public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
    return new PassthroughPodAwaiterStepFactory();
  }

  @Override
  public KubernetesVersion getVersion() {
    return KubernetesVersion.TEST_VERSION;
  }

  @Override
  public FiberGate createFiberGate() {
    return testSupport.createFiberGate();
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return testSupport.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }

  private static class PassthroughPodAwaiterStepFactory implements PodAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return next;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return next;
    }
  }
}
