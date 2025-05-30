// Copyright (c) 2019, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.work.Cancellable.createCancellable;

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
    return createCancellable(future);
  }

  @Override
  public Cancellable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    ScheduledFuture<?> future = testSupport.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    return createCancellable(future);
  }

  @Override
  public void runSteps(Step firstStep) {
    testSupport.runSteps(firstStep);
  }

  @Override
  public void runSteps(Packet packet, Step firstStep, Runnable completionCallback) {
    packet.put(ProcessingConstants.DELEGATE_COMPONENT_NAME, this);
    testSupport.runSteps(packet, firstStep);
  }

  public RequestBuilder.PodRequestBuilder getPodBuilder() {
    return new RequestBuilder.PodRequestBuilder(getResourceCache());
  }

  public RequestBuilder<V1ConfigMap, V1ConfigMapList> getConfigMapBuilder() {
    return new RequestBuilder<>(getResourceCache(), V1ConfigMap.class, V1ConfigMapList.class,
            "", "v1", "configmaps", "configmap");
  }

  public RequestBuilder<V1Secret, V1SecretList> getSecretBuilder() {
    return new RequestBuilder<>(getResourceCache(), V1Secret.class, V1SecretList.class,
            "", "v1", "secrets", "secret");
  }

  public RequestBuilder<CoreV1Event, CoreV1EventList> getEventBuilder() {
    return new RequestBuilder<>(getResourceCache(), CoreV1Event.class, CoreV1EventList.class,
            "", "v1", "events", "event");
  }

  public RequestBuilder<V1Job, V1JobList> getJobBuilder() {
    return new RequestBuilder<>(getResourceCache(), V1Job.class, V1JobList.class,
            "batch", "v1", "jobs", "job");
  }

  public RequestBuilder<V1Service, V1ServiceList> getServiceBuilder() {
    return new RequestBuilder<>(getResourceCache(), V1Service.class, V1ServiceList.class,
            "", "v1", "services", "service");
  }

  public RequestBuilder<V1PodDisruptionBudget, V1PodDisruptionBudgetList> getPodDisruptionBudgetBuilder() {
    return new RequestBuilder<>(getResourceCache(), V1PodDisruptionBudget.class, V1PodDisruptionBudgetList.class,
            "policy", "v1", "poddisruptionbudgets", "poddisruptionbudget");
  }

  public RequestBuilder<DomainResource, DomainList> getDomainBuilder() {
    return new RequestBuilder<>(getResourceCache(), DomainResource.class, DomainList.class,
            "weblogic.oracle", "v9", "domains", "domain");
  }

  public RequestBuilder<ClusterResource, ClusterList> getClusterBuilder() {
    return new RequestBuilder<>(getResourceCache(), ClusterResource.class, ClusterList.class,
            "weblogic.oracle", "v1", "clusters", "cluster");
  }

  public ResourceCache getResourceCache() {
    return null;
  }

  @Override
  public File getDeploymentHome() {
    return new File("/deployment");
  }

}
