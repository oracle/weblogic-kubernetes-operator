// Copyright (c) 2019, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static com.meterware.simplestub.Stub.createStrictStub;

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
  public KubernetesVersion getKubernetesVersion() {
    return TEST_VERSION;
  }

  @Override
  public FiberGate createFiberGate() {
    return testSupport.createFiberGate();
  }

  @Override
  public Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
    ScheduledFuture<?> future = testSupport.schedule(command, delay, unit);
    return () -> future.cancel(true);
  }

  @Override
  public Cancellable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    ScheduledFuture<?> future = testSupport.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    return () -> future.cancel(true);
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
  public File getDeploymentHome() {
    return new File("/deployment");
  }

}
