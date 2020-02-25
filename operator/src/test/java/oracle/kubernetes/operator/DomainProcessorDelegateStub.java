// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;

import static com.meterware.simplestub.Stub.createStrictStub;

/** A test stub for processing domains in unit tests. */
public abstract class DomainProcessorDelegateStub implements DomainProcessorDelegate {
  private FiberTestSupport testSupport;

  public DomainProcessorDelegateStub(FiberTestSupport testSupport) {
    this.testSupport = testSupport;
  }

  public static DomainProcessorDelegate createDelegate(KubernetesTestSupport testSupport) {
    return createStrictStub(DomainProcessorDelegateStub.class, testSupport);
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
  public V1SubjectRulesReviewStatus getSubjectRulesReviewStatus(String namespace) {
    return null;
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
