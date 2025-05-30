// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfigurationList;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.BaseMain.GIT_BRANCH_KEY;
import static oracle.kubernetes.operator.BaseMain.GIT_BUILD_TIME_KEY;
import static oracle.kubernetes.operator.BaseMain.GIT_BUILD_VERSION_KEY;
import static oracle.kubernetes.operator.BaseMain.GIT_COMMIT_KEY;
import static oracle.kubernetes.operator.BaseMain.deploymentHome;
import static oracle.kubernetes.operator.BaseMain.probesHome;
import static oracle.kubernetes.operator.work.Cancellable.createCancellable;

public class CoreDelegateImpl implements CoreDelegate {

  protected final String buildVersion;
  protected final SemanticVersion productVersion;
  protected final KubernetesVersion kubernetesVersion;
  protected final ScheduledExecutorService scheduledExecutorService;
  protected final String deploymentImpl;
  protected final String deploymentBuildTime;
  protected String domainCrdResourceVersion;
  protected String clusterCrdResourceVersion;

  private final RequestBuilder.VersionCodeRequestBuilder versionBuilder;
  private final RequestBuilder<DomainResource, DomainList> domainBuilder;
  private final RequestBuilder<ClusterResource, ClusterList> clusterBuilder;
  private final RequestBuilder<V1Namespace, V1NamespaceList> namespaceBuilder;
  private final RequestBuilder.PodRequestBuilder podBuilder;
  private final RequestBuilder<V1Service, V1ServiceList> serviceBuilder;
  private final RequestBuilder<V1ConfigMap, V1ConfigMapList> configMapBuilder;
  private final RequestBuilder<V1Secret, V1SecretList> secretBuilder;
  private final RequestBuilder<CoreV1Event, CoreV1EventList> eventBuilder;
  private final RequestBuilder<V1PersistentVolume, V1PersistentVolumeList> persistentVolumeBuilder;
  private final RequestBuilder<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> persistentVolumeClaimBuilder;
  private final RequestBuilder<V1CustomResourceDefinition, V1CustomResourceDefinitionList>
      customResourceDefinitionBuilder;
  private final RequestBuilder<V1ValidatingWebhookConfiguration, V1ValidatingWebhookConfigurationList>
      validatingWebhookConfigurationBuilder;
  private final RequestBuilder<V1Job, V1JobList> jobBuilder;
  private final RequestBuilder<V1PodDisruptionBudget, V1PodDisruptionBudgetList> podDisruptionBudgetBuilder;
  private final RequestBuilder<V1TokenReview, KubernetesListObject> tokenReviewBuilder;
  private final RequestBuilder<V1SelfSubjectRulesReview, KubernetesListObject> selfSubjectRulesReviewBuilder;
  private final RequestBuilder<V1SubjectAccessReview, KubernetesListObject> subjectAccessReviewBuilder;

  CoreDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
    buildVersion = getBuildVersion(buildProps);
    deploymentImpl = getBranch(buildProps) + "." + getCommit(buildProps);
    deploymentBuildTime = getBuildTime(buildProps);

    versionBuilder = new RequestBuilder.VersionCodeRequestBuilder();
    domainBuilder = new RequestBuilder<>(DomainResource.class, DomainList.class,
            "weblogic.oracle", "v9", "domains", "domain");
    clusterBuilder = new RequestBuilder<>(ClusterResource.class, ClusterList.class,
            "weblogic.oracle", "v1", "clusters", "cluster");
    namespaceBuilder = new RequestBuilder<>(V1Namespace.class, V1NamespaceList.class,
            "", "v1", "namespaces", "namespace");
    podBuilder = new RequestBuilder.PodRequestBuilder();
    serviceBuilder = new RequestBuilder<>(V1Service.class, V1ServiceList.class,
            "", "v1", "services", "service");
    configMapBuilder = new RequestBuilder<>(V1ConfigMap.class, V1ConfigMapList.class,
            "", "v1", "configmaps", "configmap");
    secretBuilder = new RequestBuilder<>(V1Secret.class, V1SecretList.class,
            "", "v1", "secrets", "secret");
    eventBuilder = new RequestBuilder<>(CoreV1Event.class, CoreV1EventList.class,
            "", "v1", "events", "event");
    persistentVolumeBuilder = new RequestBuilder<>(V1PersistentVolume.class, V1PersistentVolumeList.class,
            "", "v1", "persistentvolumes", "persistentvolume");
    persistentVolumeClaimBuilder = new RequestBuilder<>(V1PersistentVolumeClaim.class,
            V1PersistentVolumeClaimList.class,
            "", "v1", "persistentvolumeclaims", "persistentvolumeclaim");
    customResourceDefinitionBuilder = new RequestBuilder<>(V1CustomResourceDefinition.class,
            V1CustomResourceDefinitionList.class,
            "apiextensions.k8s.io", "v1", "customresourcedefinitions", "customresourcedefinition");
    validatingWebhookConfigurationBuilder = new RequestBuilder<>(V1ValidatingWebhookConfiguration.class,
            V1ValidatingWebhookConfigurationList.class,
            "admissionregistration.k8s.io", "v1", "validatingwebhookconfigurations", "validatingwebhookconfiguration");
    jobBuilder = new RequestBuilder<>(V1Job.class, V1JobList.class,
            "batch", "v1", "jobs", "job");
    podDisruptionBudgetBuilder = new RequestBuilder<>(V1PodDisruptionBudget.class, V1PodDisruptionBudgetList.class,
            "policy", "v1", "poddisruptionbudgets", "poddisruptionbudget");
    tokenReviewBuilder = new RequestBuilder<>(V1TokenReview.class, KubernetesListObject.class,
            "authentication.k8s.io", "v1", "tokenreviews", "tokenreview");
    selfSubjectRulesReviewBuilder = new RequestBuilder<>(V1SelfSubjectRulesReview.class, KubernetesListObject.class,
            "authorization.k8s.io", "v1", "selfsubjectrulesreviews", "selfsubjectrulesreview");
    subjectAccessReviewBuilder = new RequestBuilder<>(V1SubjectAccessReview.class, KubernetesListObject.class,
            "authorization.k8s.io", "v1", "selfsubjectaccessreviews", "selfsubjectaccessreview");

    productVersion = new SemanticVersion(buildVersion);
    kubernetesVersion = HealthCheckHelper.performK8sVersionCheck(this);

    this.scheduledExecutorService = scheduledExecutorService;

    PodHelper.setProductVersion(productVersion.toString());
  }

  protected static String getBuildVersion(Properties buildProps) {
    return Optional.ofNullable(buildProps.getProperty(GIT_BUILD_VERSION_KEY)).orElse("1.0");
  }

  protected static String getBranch(Properties buildProps) {
    return getBuildProperty(buildProps, GIT_BRANCH_KEY);
  }

  protected static String getCommit(Properties buildProps) {
    return getBuildProperty(buildProps, GIT_COMMIT_KEY);
  }

  protected static String getBuildTime(Properties buildProps) {
    return getBuildProperty(buildProps, GIT_BUILD_TIME_KEY);
  }

  protected static String getBuildProperty(Properties buildProps, String key) {
    return Optional.ofNullable(buildProps.getProperty(key)).orElse("unknown");
  }

  @Override
  public @Nonnull
  SemanticVersion getProductVersion() {
    return productVersion;
  }

  @Override
  public KubernetesVersion getKubernetesVersion() {
    return kubernetesVersion;
  }

  @Override
  public String getDomainCrdResourceVersion() {
    return domainCrdResourceVersion;
  }

  @Override
  public void setDomainCrdResourceVersion(String resourceVersion) {
    this.domainCrdResourceVersion = resourceVersion;
  }

  @Override
  public String getClusterCrdResourceVersion() {
    return clusterCrdResourceVersion;
  }

  @Override
  public void setClusterCrdResourceVersion(String resourceVersion) {
    this.clusterCrdResourceVersion = resourceVersion;
  }

  @Override
  public File getDeploymentHome() {
    return deploymentHome;
  }

  @Override
  public File getProbesHome() {
    return probesHome;
  }

  @Override
  public void runStepsInternal(Packet packet, Step firstStep, Runnable completionAction) {
    Fiber f = new Fiber(scheduledExecutorService, firstStep, packet, andThenDo(completionAction));
    f.start();
  }

  private static BaseMain.NullCompletionCallback andThenDo(Runnable completionAction) {
    return new BaseMain.NullCompletionCallback(completionAction);
  }

  @Override
  public Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
    ScheduledFuture<?> future = scheduledExecutorService.schedule(command, delay, unit);
    return createCancellable(future);
  }

  @Override
  public Cancellable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    ScheduledFuture<?> future = scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    return createCancellable(future);
  }

  @Override
  public RequestBuilder.VersionCodeRequestBuilder getVersionBuilder() {
    return versionBuilder;
  }

  @Override
  public RequestBuilder<DomainResource, DomainList> getDomainBuilder() {
    return domainBuilder;
  }

  @Override
  public RequestBuilder<ClusterResource, ClusterList> getClusterBuilder() {
    return clusterBuilder;
  }

  @Override
  public RequestBuilder<V1Namespace, V1NamespaceList> getNamespaceBuilder() {
    return namespaceBuilder;
  }

  @Override
  public RequestBuilder.PodRequestBuilder getPodBuilder() {
    return podBuilder;
  }

  @Override
  public RequestBuilder<V1Service, V1ServiceList> getServiceBuilder() {
    return serviceBuilder;
  }

  @Override
  public RequestBuilder<V1ConfigMap, V1ConfigMapList> getConfigMapBuilder() {
    return configMapBuilder;
  }

  @Override
  public RequestBuilder<V1Secret, V1SecretList> getSecretBuilder() {
    return secretBuilder;
  }

  @Override
  public RequestBuilder<CoreV1Event, CoreV1EventList> getEventBuilder() {
    return eventBuilder;
  }

  @Override
  public RequestBuilder<V1PersistentVolume, V1PersistentVolumeList> getPersistentVolumeBuilder() {
    return persistentVolumeBuilder;
  }

  @Override
  public RequestBuilder<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> getPersistentVolumeClaimBuilder() {
    return persistentVolumeClaimBuilder;
  }

  @Override
  public RequestBuilder<V1CustomResourceDefinition, V1CustomResourceDefinitionList>
      getCustomResourceDefinitionBuilder() {
    return customResourceDefinitionBuilder;
  }

  @Override
  public RequestBuilder<V1ValidatingWebhookConfiguration, V1ValidatingWebhookConfigurationList>
      getValidatingWebhookConfigurationBuilder() {
    return validatingWebhookConfigurationBuilder;
  }

  @Override
  public RequestBuilder<V1Job, V1JobList> getJobBuilder() {
    return jobBuilder;
  }

  @Override
  public RequestBuilder<V1PodDisruptionBudget, V1PodDisruptionBudgetList> getPodDisruptionBudgetBuilder() {
    return podDisruptionBudgetBuilder;
  }

  @Override
  public RequestBuilder<V1TokenReview, KubernetesListObject> getTokenReviewBuilder() {
    return tokenReviewBuilder;
  }

  @Override
  public RequestBuilder<V1SelfSubjectRulesReview, KubernetesListObject> getSelfSubjectRulesReviewBuilder() {
    return selfSubjectRulesReviewBuilder;
  }

  @Override
  public RequestBuilder<V1SubjectAccessReview, KubernetesListObject> getSubjectAccessReviewBuilder() {
    return subjectAccessReviewBuilder;
  }
}
