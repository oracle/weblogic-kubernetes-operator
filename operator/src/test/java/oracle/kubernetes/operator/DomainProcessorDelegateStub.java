// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.JobWatcher.getFailedReason;
import static oracle.kubernetes.operator.JobWatcher.isFailed;
import static oracle.kubernetes.operator.ProcessingConstants.DELEGATE_COMPONENT_NAME;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;

/**
 * A test stub for processing domains in unit tests.
 */
public abstract class DomainProcessorDelegateStub implements DomainProcessorDelegate {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final KubernetesVersion TEST_VERSION = new KubernetesVersion(TEST_VERSION_INFO);

  private final FiberTestSupport testSupport;
  private boolean namespaceRunning = true;
  private boolean waitedForIntrospection;
  private final DomainNamespaces domainNamespaces;

  public DomainProcessorDelegateStub(FiberTestSupport testSupport) {
    this(testSupport, null);
  }

  public DomainProcessorDelegateStub(FiberTestSupport testSupport, DomainNamespaces domainNamespaces) {
    this.testSupport = testSupport;
    this.domainNamespaces = domainNamespaces;
  }

  public static DomainProcessorDelegateStub createDelegate(KubernetesTestSupport testSupport) {
    return createStrictStub(DomainProcessorDelegateStub.class, testSupport);
  }

  public static DomainProcessorDelegateStub createDelegate(KubernetesTestSupport testSupport, DomainNamespaces ns) {
    return createStrictStub(DomainProcessorDelegateStub.class, testSupport, ns);
  }

  public boolean waitedForIntrospection() {
    return waitedForIntrospection;
  }

  public void setNamespaceRunning(boolean namespaceRunning) {
    this.namespaceRunning = namespaceRunning;
  }

  @Override
  public boolean isNamespaceRunning(String namespace) {
    return namespaceRunning;
  }

  @Override
  public DomainNamespaces getDomainNamespaces() {
    return domainNamespaces;
  }

  @Override
  public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
    return new PassThroughWithServerShutdownAwaiterStepFactory();
  }

  @Override
  public JobAwaiterStepFactory getJobAwaiterStepFactory(String namespace) {
    return new TestJobAwaiterStepFactory();
  }

  @Override
  public PvcAwaiterStepFactory getPvcAwaiterStepFactory() {
    return new TestPvcAwaiterStepFactory();
  }

  @Override
  public KubernetesVersion getKubernetesVersion() {
    return TEST_VERSION;
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

  @Override
  public void runSteps(Step firstStep) {
    testSupport.runSteps(firstStep);
  }

  @Override
  public void runSteps(Packet packet, Step firstStep, Runnable completionCallback) {
    testSupport.runSteps(packet, firstStep);
  }

  @Override
  public void addToPacket(Packet packet) {
    packet.getComponents().put(DELEGATE_COMPONENT_NAME, Component.createFor(CoreDelegate.class, this));
  }

  @Override
  public File getDeploymentHome() {
    return new File("/deployment");
  }

  private static class PassthroughPodAwaiterStepFactory implements PodAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return next;
    }

    @Override
    public Step waitForReady(String podName, Step next) {
      return next;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return next;
    }

    @Override
    public Step waitForServerShutdown(String serverName, DomainResource domain, Step next) {
      return next;
    }
  }

  private class PassThroughWithServerShutdownAwaiterStepFactory extends PassthroughPodAwaiterStepFactory {
    @Override
    public Step waitForServerShutdown(String serverName, DomainResource domain, Step next) {
      if (Optional.ofNullable(PodHelper.getServerState(domain, serverName))
          .map(s -> s.equals(SHUTDOWN_STATE)).orElse(false)) {
        return next;
      } else {
        return new DelayStep(next, 1);
      }
    }
  }

  private static class DelayStep extends Step {
    private final int delay;
    private final Step next;

    DelayStep(Step next, int delay) {
      this.delay = delay;
      this.next = next;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doDelay(next, packet, delay, TimeUnit.SECONDS);
    }
  }

  private class TestJobAwaiterStepFactory implements JobAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Job job, Step next) {
      if (isFailed(job) && "DeadlineExceeded".equals(getFailedReason(job))) {
        return new Step() {
          @Override
          public oracle.kubernetes.operator.work.NextAction apply(Packet packet) {
            return doTerminate(new JobWatcher.DeadlineExceededException(job), packet);
          }
        };
      } else {
        waitedForIntrospection = true;
        return null;
      }
    }
  }

  public static class TestPvcAwaiterStepFactory implements PvcAwaiterStepFactory {
    @Override
    public Step waitForReady(V1PersistentVolumeClaim job, Step next) {
      return next;
    }
  }
}
