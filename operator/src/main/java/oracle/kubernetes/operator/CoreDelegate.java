// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.http.metrics.MetricsServer;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.ProcessingConstants.DELEGATE_COMPONENT_NAME;

public interface CoreDelegate {

  String SHUTDOWN_MARKER_NAME = "marker.shutdown";

  SemanticVersion getProductVersion();

  KubernetesVersion getKubernetesVersion();

  String getDomainCrdResourceVersion();

  void setDomainCrdResourceVersion(String resourceVersion);

  String getClusterCrdResourceVersion();

  void setClusterCrdResourceVersion(String resourceVersion);

  File getDeploymentHome();

  default File getShutdownMarker() {
    return new File(getDeploymentHome(), SHUTDOWN_MARKER_NAME);
  }

  File getProbesHome();

  default boolean createNewFile(File file) throws IOException {
    return file.createNewFile();
  }

  default int getMetricsPort() {
    return MetricsServer.DEFAULT_METRICS_PORT;
  }

  default void runSteps(Step firstStep) {
    runSteps(new Packet(), firstStep, null);
  }

  default void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
    packet.put(DELEGATE_COMPONENT_NAME, this);
    runStepsInternal(packet, firstStep, completionAction);
  }

  void runStepsInternal(Packet packet, Step firstStep, Runnable completionAction);

  Cancellable schedule(Runnable command, long delay, TimeUnit unit);

  Cancellable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

  RequestBuilder.VersionCodeRequestBuilder getVersionBuilder();

  RequestBuilder<DomainResource, DomainList> getDomainBuilder();

  RequestBuilder<ClusterResource, ClusterList> getClusterBuilder();

  RequestBuilder<V1Namespace, V1NamespaceList> getNamespaceBuilder();

  RequestBuilder.PodRequestBuilder getPodBuilder();

  RequestBuilder<V1Service, V1ServiceList> getServiceBuilder();

  RequestBuilder<V1ConfigMap, V1ConfigMapList> getConfigMapBuilder();

  RequestBuilder<V1Secret, V1SecretList> getSecretBuilder();

  RequestBuilder<CoreV1Event, CoreV1EventList> getEventBuilder();

  RequestBuilder<V1PersistentVolume, V1PersistentVolumeList> getPersistentVolumeBuilder();

  RequestBuilder<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> getPersistentVolumeClaimBuilder();

  RequestBuilder<V1CustomResourceDefinition, V1CustomResourceDefinitionList> getCustomResourceDefinitionBuilder();

  RequestBuilder<V1ValidatingWebhookConfiguration, V1ValidatingWebhookConfigurationList>
      getValidatingWebhookConfigurationBuilder();

  RequestBuilder<V1Job, V1JobList> getJobBuilder();

  RequestBuilder<V1PodDisruptionBudget, V1PodDisruptionBudgetList> getPodDisruptionBudgetBuilder();

  RequestBuilder<V1TokenReview, KubernetesListObject> getTokenReviewBuilder();

  RequestBuilder<V1SelfSubjectRulesReview, KubernetesListObject> getSelfSubjectRulesReviewBuilder();

  RequestBuilder<V1SubjectAccessReview, KubernetesListObject> getSubjectAccessReviewBuilder();
}
