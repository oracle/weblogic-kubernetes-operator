// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.meterware.pseudoserver.PseudoServer;
import com.meterware.pseudoserver.PseudoServlet;
import com.meterware.pseudoserver.WebResource;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1ServiceReference;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1WebhookClientConfig;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1ValidatingWebhook;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfigurationList;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.CONTINUE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.PROGRESSING;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("SameParameterValue")
class CallBuilderTest {
  private static final String NAMESPACE = "testspace";
  private static final String UID = "uid";
  private static final String CLUSTER_RESOURCE = String.format(
          "/apis/weblogic.oracle/" + KubernetesConstants.CLUSTER_VERSION + "/namespaces/%s/clusters",
          NAMESPACE);
  private static final String DOMAIN_RESOURCE = String.format(
      "/apis/weblogic.oracle/" + KubernetesConstants.DOMAIN_VERSION + "/namespaces/%s/domains",
      NAMESPACE);
  private static final String SERVICE_RESOURCE = String.format("/api/v1/namespaces/%s/services", NAMESPACE);
  private static final String POD_RESOURCE = String.format("/api/v1/namespaces/%s/pods", NAMESPACE);
  private static final String JOB_RESOURCE = String.format("/apis/batch/v1/namespaces/%s/jobs", NAMESPACE);
  private static final String PDB_RESOURCE = String.format(
      "/apis/policy/v1/namespaces/%s/poddisruptionbudgets", NAMESPACE);
  private static final String CM_RESOURCE = String.format("/api/v1/namespaces/%s/configmaps", NAMESPACE);
  private static final String SECRET_RESOURCE = String.format("/api/v1/namespaces/%s/secrets", NAMESPACE);
  private static final String EVENT_RESOURCE = String.format("/api/v1/namespaces/%s/events", NAMESPACE);
  private static final String SAR_RESOURCE = "/apis/authorization.k8s.io/v1/subjectaccessreviews";
  private static final String SSAR_RESOURCE = "/apis/authorization.k8s.io/v1/selfsubjectaccessreviews";
  private static final String SSRR_RESOURCE = "/apis/authorization.k8s.io/v1/selfsubjectrulesreviews";
  private static final String TR_RESOURCE = "/apis/authentication.k8s.io/v1/tokenreviews";
  private static final String CRD_RESOURCE = "/apis/apiextensions.k8s.io/v1/customresourcedefinitions";
  private static final String VALIDATING_WEBHOOK_CONFIGURATION_RESOURCE
      = "/apis/admissionregistration.k8s.io/v1/validatingwebhookconfigurations";
  private static final String NAMESPACE_RESOURCE = "/api/v1/namespaces";
  private static final String PVC_RESOURCE = String.format(
      "/api/v1/namespaces/%s/persistentvolumeclaims", NAMESPACE);
  private static final String PV_RESOURCE = "/api/v1/persistentvolumes";

  private static final ApiClient apiClient = new ApiClient();
  private final List<Memento> mementos = new ArrayList<>();
  private final CallBuilder callBuilder = new CallBuilder();
  private final PseudoServer server = new PseudoServer();

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  private static String toJson(Object object) {
    return new GsonBuilder().create().toJson(object);
  }

  @BeforeEach
  public void setUp() throws NoSuchFieldException, IOException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(PseudoServletCallDispatcher.installSync(getHostPath()));
    mementos.add(PseudoServletCallDispatcher.installAsync(getHostPath()));
  }

  private String getHostPath() throws IOException {
    return "http://localhost:" + server.getConnectedPort();
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  @ResourceLock(value = "server")
  void getVersionCode_returnsAVersionInfo() throws ApiException {
    VersionInfo versionInfo = new VersionInfo().major("1").minor("2");
    defineHttpGetResponse("/version/", versionInfo);

    assertThat(callBuilder.readVersionCode(), equalTo(versionInfo));
  }

  @Test
  @ResourceLock(value = "server")
  void getVersionCode_firstAttemptFailsAndThenReturnsAVersionInfo() throws Exception {
    VersionInfo versionInfo = new VersionInfo().major("1").minor("2");
    defineHttpGetResponse("/version/", new FailOnceGetServlet(versionInfo, HTTP_BAD_REQUEST));

    assertThat(callBuilder.executeSynchronousCallWithRetry(
        callBuilder::readVersionCode, 1), equalTo(versionInfo));
  }

  static class FailOnceGetServlet extends JsonGetServlet {

    final int errorCode;
    int numGetResponseCalled = 0;

    FailOnceGetServlet(Object returnValue, int errorCode) {
      super(returnValue);
      this.errorCode = errorCode;
    }

    @Override
    public WebResource getGetResponse() throws IOException {
      if (numGetResponseCalled++ > 0) {
        return super.getGetResponse();
      }
      return new WebResource("", errorCode);
    }
  }

  @Test
  @ResourceLock(value = "server")
  void listDomainSync_returnsList() throws ApiException {
    DomainList list = new DomainList().withItems(Arrays.asList(new DomainResource(), new DomainResource()));
    defineHttpGetResponse(DOMAIN_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    assertThat(callBuilder.withFieldSelector("xxx").listDomain(NAMESPACE), equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readDomainSync_returnsResource() throws ApiException {
    DomainResource domain = new DomainResource().withMetadata(new V1ObjectMeta().name(UID).namespace(NAMESPACE));
    defineHttpGetResponse(DOMAIN_RESOURCE, UID, domain);

    assertThat(callBuilder.readDomain(UID, NAMESPACE), equalTo(domain));
  }

  @Test
  @ResourceLock(value = "server")
  void readDomain_returnsResource() throws InterruptedException {
    DomainResource resource = new DomainResource().withMetadata(createMetadata());
    defineHttpGetResponse(DOMAIN_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<DomainResource> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readDomainAsync(UID, NAMESPACE, responseStep));

    DomainResource received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceDomain_sendsNewDomain() throws ApiException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    DomainResource domain = new DomainResource().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID, domain, (json) -> requestBody.set(fromJson(json, DomainResource.class)));

    callBuilder.replaceDomain(UID, NAMESPACE, domain);

    assertThat(requestBody.get(), equalTo(domain));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceDomainStatus_sendsNewDomain() throws ApiException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    DomainResource domain = new DomainResource().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID + "/status", domain, (json) -> requestBody.set(fromJson(json, DomainResource.class)));

    callBuilder.replaceDomainStatus(UID, NAMESPACE, domain);

    assertThat(requestBody.get(), equalTo(domain));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceDomain_errorResponseCode_throws() {
    DomainResource domain = new DomainResource().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_BAD_REQUEST));

    assertThrows(ApiException.class, () -> callBuilder.replaceDomain(UID, NAMESPACE, domain));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceDomain_conflictResponseCode_throws() {
    DomainResource domain = new DomainResource().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_CONFLICT));

    assertThrows(ApiException.class, () -> callBuilder.replaceDomain(UID, NAMESPACE, domain));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceDomainAsync_returnsUpdatedDomain() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    DomainResource domain = new DomainResource().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID, domain, (json) -> requestBody.set(fromJson(json, DomainResource.class)));

    KubernetesTestSupportTest.TestResponseStep<DomainResource> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().replaceDomainAsync(UID, NAMESPACE, domain, responseStep));

    DomainResource received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(domain));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceDomainStatusAsync_returnsUpdatedDomain() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    DomainResource domain = new DomainResource().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID + "/status", domain, (json) -> requestBody.set(fromJson(json, DomainResource.class)));

    KubernetesTestSupportTest.TestResponseStep<DomainResource> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().replaceDomainStatusAsync(UID, NAMESPACE, domain, responseStep));

    DomainResource received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(domain));
  }

  @Test
  @ResourceLock(value = "server")
  void patchDomainAsync_returnsUpdatedDomain() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    DomainResource domain
        = new DomainResource().withMetadata(createMetadata()).withSpec(new DomainSpec().withReplicas(5));
    defineHttpPatchResponse(
        DOMAIN_RESOURCE, UID, domain, requestBody::set);

    KubernetesTestSupportTest.TestResponseStep<DomainResource> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);
    testSupport.runSteps(new CallBuilder().patchDomainAsync(UID, NAMESPACE,
        new V1Patch(patchBuilder.build().toString()), responseStep));

    DomainResource received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(domain));
  }

  @Test
  @ResourceLock(value = "server")
  void createClusterSyncUntyped_returnsNewResource() throws ApiException {
    Map<String, Object> resource = toMap(new ClusterResource().withMetadata(createMetadata()));
    defineHttpPostResponse(
        CLUSTER_RESOURCE, resource, (json) -> fromJson(json, Map.class));

    Object received = callBuilder.createClusterUntyped(NAMESPACE, resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceClusterSyncUntyped_returnsUpdatedResource() throws ApiException {
    Map<String, Object> resource = toMap(new ClusterResource().withMetadata(createMetadata()));
    defineHttpPutResponse(
        CLUSTER_RESOURCE, UID, resource, (json) -> fromJson(json, Map.class));

    Object received = callBuilder.replaceClusterUntyped(UID, NAMESPACE, resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void listClusters_returnsList() throws ApiException {
    ClusterList list = new ClusterList().withItems(Arrays.asList(new ClusterResource(), new ClusterResource()));
    defineHttpGetResponse(CLUSTER_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    assertThat(callBuilder.withFieldSelector("xxx").listCluster(NAMESPACE), equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void listClustersUntyped_returnsList() throws ApiException {
    Map<String, Object> list = toMap(new ClusterList().withItems(
        Arrays.asList(new ClusterResource(), new ClusterResource())));
    defineHttpGetResponse(CLUSTER_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    assertThat(callBuilder.withFieldSelector("xxx").listClusterUntyped(NAMESPACE), equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void listClusters_returnsNull() throws ApiException {
    defineHttpGetResponse(CLUSTER_RESOURCE, (Object) null);

    assertThat(callBuilder.listCluster(NAMESPACE), nullValue());
  }

  @Test
  @ResourceLock(value = "server")
  void listClusterAsync_returnsClusters() throws InterruptedException {
    ClusterResource cluster1 = new ClusterResource();
    ClusterResource cluster2 = new ClusterResource();

    ClusterList list = new ClusterList().withItems(Arrays.asList(cluster1, cluster2))
            .withMetadata(new V1ListMeta()._continue(CONTINUE));
    list.setApiVersion(KubernetesConstants.API_VERSION_CLUSTER_WEBLOGIC_ORACLE);
    final String clusterList = "ClusterList";
    list.setKind(clusterList);

    defineHttpGetResponse(CLUSTER_RESOURCE, list);

    KubernetesTestSupportTest.TestResponseStep<ClusterList> responseStep
            = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().listClusterAsync(NAMESPACE, responseStep));

    ClusterList received = responseStep.waitForAndGetCallResponse().getResult();
    assertThat(received.getItems(), hasSize(2));
    assertThat(received, equalTo(list));
    assertThat(received.getMetadata().getContinue(), equalTo(CONTINUE));
    assertThat(received.getItems(), containsInAnyOrder(cluster1, cluster2));
    assertThat(received.hashCode(), equalTo(list.hashCode()));
    assertThat(received.toString(), containsString("kind="
            + KubernetesConstants.CLUSTER));
    assertThat(received.getApiVersion(),
            equalTo(KubernetesConstants.API_VERSION_CLUSTER_WEBLOGIC_ORACLE));
    assertThat(received.getKind(), equalTo(clusterList));
  }

  @Test
  @ResourceLock(value = "server")
  void listClusterAsync_returnsAndVerifyClusterResource() throws InterruptedException {
    ClusterResource cluster1 = new ClusterResource();
    ClusterStatus status = new ClusterStatus().withClusterName("cluster-1").withReplicas(2)
            .withMaximumReplicas(8);
    final String myKind = "MyKind";
    final String apiVersion = "apiVersion";
    cluster1.setStatus(status);
    cluster1.setKind(myKind);
    cluster1.setApiVersion(apiVersion);

    ClusterList list = new ClusterList().withItems(List.of(cluster1));

    defineHttpGetResponse(CLUSTER_RESOURCE, list);

    KubernetesTestSupportTest.TestResponseStep<ClusterList> responseStep
            = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().listClusterAsync(NAMESPACE, responseStep));

    ClusterList result = responseStep.waitForAndGetCallResponse().getResult();
    assertThat(result.getItems(), hasSize(1));
    ClusterResource received = result.getItems().get(0);
    assertThat(received.getKind(), equalTo(myKind));
    assertThat(received.getApiVersion(), equalTo(apiVersion));
    assertThat(received.toString(), containsString("apiVersion=apiVersion"));
    assertThat(received.hashCode(), equalTo(cluster1.hashCode()));
    assertThat(received.getStatus(), equalTo(status));
  }

  @Test
  @ResourceLock(value = "server")
  void patchClusterResource_returnsResource() throws ApiException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    String resourceName = UID + "-cluster1";
    ClusterResource resource = new ClusterResource()
            .withMetadata(createMetadata().name(resourceName))
            .withReplicas(5);
    defineHttpPatchResponse(
            CLUSTER_RESOURCE, resourceName, resource, requestBody::set);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);

    ClusterResource received = callBuilder.patchCluster(resourceName, NAMESPACE,
            new V1Patch(patchBuilder.build().toString()));

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void patchMissingClusterResource_returnsNull() throws ApiException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    defineHttpPatchResponse(
            CLUSTER_RESOURCE, UID + "-cluster1", null, requestBody::set);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);

    ClusterResource received = callBuilder.patchCluster(UID + "-cluster1", NAMESPACE,
            new V1Patch(patchBuilder.build().toString()));

    assertThat(received, nullValue());
  }

  @Test
  @ResourceLock(value = "server")
  void readCluster_returnsResource() throws InterruptedException {
    ClusterResource resource = new ClusterResource().withMetadata(createMetadata());
    defineHttpGetResponse(CLUSTER_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<ClusterResource> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readClusterAsync(UID, NAMESPACE, responseStep));

    ClusterResource received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  private JsonElement convertToJson(Object o) {
    Gson gson = new GsonBuilder().create();
    return gson.toJsonTree(o);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toMap(Object o) {
    Gson gson = new GsonBuilder().create();
    return gson.fromJson(convertToJson(o), Map.class);
  }

  @Test
  @ResourceLock(value = "server")
  void readClusterUntyped_returnsResource() throws ApiException {
    Map<String, Object> resource = toMap(new ClusterResource().withMetadata(createMetadata()));
    defineHttpGetResponse(CLUSTER_RESOURCE, UID, resource);

    assertThat(callBuilder.readClusterUntyped(UID, NAMESPACE), equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceClusterStatusAsync_returnsUpdatedClusterResource() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    ClusterResource clusterResource = new ClusterResource().withMetadata(createMetadata());
    defineHttpPutResponse(
        CLUSTER_RESOURCE, UID + "/status", clusterResource, (json) ->
            requestBody.set(fromJson(json, ClusterResource.class)));

    KubernetesTestSupportTest.TestResponseStep<ClusterResource> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().replaceClusterStatusAsync(UID, NAMESPACE, clusterResource, responseStep));

    ClusterResource received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(clusterResource));
  }


  @Test
  @ResourceLock(value = "server")
  void listDomainsAsync_returnsDomains() throws InterruptedException {
    DomainResource domain1 = new DomainResource();
    DomainStatus domainStatus1 = new DomainStatus().withStartTime(null);
    domain1.setStatus(domainStatus1);

    domainStatus1.getConditions().add(new DomainCondition(PROGRESSING).withLastTransitionTime(null));
    domainStatus1.getConditions().add(new DomainCondition(AVAILABLE).withLastTransitionTime(null));

    DomainResource domain2 = new DomainResource();
    DomainStatus domainStatus2 = new DomainStatus().withStartTime(null);
    domain2.setStatus(domainStatus2);

    domainStatus2.getConditions().add(new DomainCondition(PROGRESSING).withLastTransitionTime(null));
    domainStatus2.getConditions().add(new DomainCondition(FAILED).withLastTransitionTime(null));

    DomainList list = new DomainList().withItems(Arrays.asList(domain1, domain2));
    defineHttpGetResponse(DOMAIN_RESOURCE, list);

    KubernetesTestSupportTest.TestResponseStep<DomainList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().listDomainAsync(NAMESPACE, responseStep));

    DomainList received = responseStep.waitForAndGetCallResponse().getResult();
    assertThat(received.getItems(), hasSize(2));
    assertThat(received.getItems().get(0), allOf(hasCondition(PROGRESSING), hasCondition(AVAILABLE)));
    assertThat(received.getItems().get(1), allOf(hasCondition(PROGRESSING), hasCondition(FAILED)));
  }

  @Test
  @ResourceLock(value = "server")
  void listService_returnsList() throws InterruptedException {
    V1ServiceList list = new V1ServiceList().items(Arrays.asList(new V1Service(), new V1Service()));
    defineHttpGetResponse(SERVICE_RESOURCE, list).expectingParameter("labelSelector", "yyy");

    KubernetesTestSupportTest.TestResponseStep<V1ServiceList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withLabelSelectors("yyy").listServiceAsync(NAMESPACE, responseStep));

    V1ServiceList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readService_returnsResource() throws InterruptedException {
    V1Service resource = new V1Service().metadata(createMetadata());
    defineHttpGetResponse(SERVICE_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1Service> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readServiceAsync(UID, NAMESPACE, UID, responseStep));

    V1Service received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createService_returnsNewService() throws InterruptedException {
    V1Service service = new V1Service().metadata(createMetadata());
    defineHttpPostResponse(
        SERVICE_RESOURCE, service, (json) -> fromJson(json, V1Service.class));

    KubernetesTestSupportTest.TestResponseStep<V1Service> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createServiceAsync(NAMESPACE, service, responseStep));

    V1Service received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(service));
  }

  @Test
  @ResourceLock(value = "server")
  void deleteService_returnsDeletedService() throws InterruptedException {
    V1Service service = new V1Service().metadata(createMetadata());
    defineHttpDeleteResponse(
        SERVICE_RESOURCE, UID, service, (json) -> fromJson(json, V1Service.class));

    KubernetesTestSupportTest.TestResponseStep<V1Service> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .deleteServiceAsync(Objects.requireNonNull(service.getMetadata()).getName(),
                NAMESPACE, UID, new DeleteOptions(), responseStep));

    V1Service received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(service));
  }

  @Test
  @ResourceLock(value = "server")
  void listSecret_returnsList() throws InterruptedException {
    V1SecretList list = new V1SecretList().items(Arrays.asList(new V1Secret(), new V1Secret()));
    defineHttpGetResponse(SECRET_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1SecretList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx").listSecretsAsync(NAMESPACE, responseStep));

    V1SecretList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readSecret_returnsResource() throws InterruptedException {
    V1Secret resource = new V1Secret().metadata(createMetadata());
    defineHttpGetResponse(SECRET_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1Secret> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readSecretAsync(UID, NAMESPACE, responseStep));

    V1Secret received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createSecret_returnsNewSecret() throws InterruptedException {
    V1Secret secret = new V1Secret().metadata(createMetadata());
    defineHttpPostResponse(
        SECRET_RESOURCE, secret, (json) -> fromJson(json, V1Secret.class));

    KubernetesTestSupportTest.TestResponseStep<V1Secret> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createSecretAsync(NAMESPACE, secret, responseStep));

    V1Secret received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(secret));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceSecret_returnsUpdatedResource() throws InterruptedException {
    V1Secret secret = new V1Secret().metadata(createMetadata());
    defineHttpPutResponse(
        SECRET_RESOURCE, UID, secret, (json) -> fromJson(json, V1Secret.class));

    KubernetesTestSupportTest.TestResponseStep<V1Secret> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .replaceSecretAsync(Objects.requireNonNull(secret.getMetadata()).getName(), NAMESPACE, secret, responseStep));

    V1Secret received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(secret));
  }

  @Test
  @ResourceLock(value = "server")
  void createPod_returnsNewResource() throws InterruptedException {
    V1Pod resource = new V1Pod().metadata(createMetadata());
    defineHttpPostResponse(
        POD_RESOURCE, resource, (json) -> fromJson(json, V1Pod.class));

    KubernetesTestSupportTest.TestResponseStep<V1Pod> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createPodAsync(NAMESPACE, resource, responseStep));

    V1Pod received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void listPods_returnsList() throws InterruptedException {
    V1PodList list = new V1PodList()
        .items(Arrays.asList(new V1Pod(), new V1Pod()));
    defineHttpGetResponse(POD_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1PodList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx")
        .listPodAsync(NAMESPACE, responseStep));

    V1PodList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readPod_returnsResource() throws InterruptedException {
    V1Pod resource = new V1Pod().metadata(createMetadata());
    defineHttpGetResponse(POD_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1Pod> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readPodAsync(UID, NAMESPACE, UID, responseStep));

    V1Pod received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void readPodLog_returnsLog() throws InterruptedException {
    String log = "Runtime log";
    defineHttpGetResponse(
        POD_RESOURCE, UID + "/log", log);

    KubernetesTestSupportTest.TestResponseStep<String> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readPodLogAsync(UID, NAMESPACE, UID, responseStep));

    String received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(log));
  }

  @Test
  @ResourceLock(value = "server")
  void patchPodAsync_returnsUpdatedResource() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    V1Pod resource = new V1Pod().metadata(createMetadata());
    defineHttpPatchResponse(
        POD_RESOURCE, UID, resource, requestBody::set);

    KubernetesTestSupportTest.TestResponseStep<V1Pod> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);
    testSupport.runSteps(new CallBuilder().patchPodAsync(UID, NAMESPACE, UID,
        new V1Patch(patchBuilder.build().toString()), responseStep));

    V1Pod received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void patchPod_returnsUpdatedResource() throws ApiException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    V1Pod resource = new V1Pod().metadata(createMetadata());
    defineHttpPatchResponse(
        POD_RESOURCE, UID, resource, requestBody::set);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);

    V1Pod received = callBuilder.patchPod(UID, NAMESPACE, UID, new V1Patch(patchBuilder.build().toString()));

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void deletePod_returnsDeletedResource() throws InterruptedException {
    V1Status response = new V1Status();
    V1Pod resource = new V1Pod().metadata(createMetadata());
    defineHttpDeleteResponse(POD_RESOURCE, UID, response, (Consumer<String>) null);

    KubernetesTestSupportTest.TestResponseStep<Object> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .deletePodAsync(Objects.requireNonNull(resource.getMetadata()).getName(),
            NAMESPACE, UID, new DeleteOptions(), responseStep));

    Object received = responseStep.waitForAndGetCallResponse().getResult();

    assertNotNull(received);
  }

  @Test
  @ResourceLock(value = "server")
  void deletePodCollection_returnsStatus() throws InterruptedException {
    V1Status response = new V1Status();
    defineHttpDeleteResponse(POD_RESOURCE, null, response, (Consumer<String>) null);

    KubernetesTestSupportTest.TestResponseStep<V1Status> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .deleteCollectionPodAsync(NAMESPACE, responseStep));

    V1Status received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(response));
  }

  @Test
  @ResourceLock(value = "server")
  void listJobs_returnsList() throws InterruptedException {
    V1JobList list = new V1JobList()
        .items(Arrays.asList(new V1Job(), new V1Job()));
    defineHttpGetResponse(JOB_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1JobList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx")
        .listJobAsync(NAMESPACE, responseStep));

    V1JobList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void createJob_returnsNewResource() throws InterruptedException {
    V1Job resource = new V1Job().metadata(createMetadata());
    defineHttpPostResponse(
        JOB_RESOURCE, resource, (json) -> fromJson(json, V1Job.class));

    KubernetesTestSupportTest.TestResponseStep<V1Job> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createJobAsync(NAMESPACE, UID, resource, responseStep));

    V1Job received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void readJob_returnsResource() throws InterruptedException {
    V1Job resource = new V1Job().metadata(createMetadata());
    defineHttpGetResponse(JOB_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1Job> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readJobAsync(UID, NAMESPACE, UID, responseStep));

    V1Job received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void deleteJob_returnsDeletedResource() throws InterruptedException {
    V1Status response = new V1Status();
    V1Job resource = new V1Job().metadata(createMetadata());
    defineHttpDeleteResponse(JOB_RESOURCE, UID, response, (Consumer<String>) null);

    KubernetesTestSupportTest.TestResponseStep<V1Status> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .deleteJobAsync(Objects.requireNonNull(resource.getMetadata()).getName(),
            NAMESPACE, UID, new DeleteOptions(), responseStep));

    V1Status received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(response));
  }

  @Test
  @ResourceLock(value = "server")
  void listPodDisruptionBudgets_returnsList() throws InterruptedException {
    V1PodDisruptionBudgetList list = new V1PodDisruptionBudgetList()
        .items(Arrays.asList(new V1PodDisruptionBudget(), new V1PodDisruptionBudget()));
    defineHttpGetResponse(PDB_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1PodDisruptionBudgetList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx")
        .listPodDisruptionBudgetAsync(NAMESPACE, responseStep));

    V1PodDisruptionBudgetList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readPodDisruptionBudget_returnsResource() throws InterruptedException {
    V1PodDisruptionBudget resource = new V1PodDisruptionBudget().metadata(createMetadata());
    defineHttpGetResponse(PDB_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1PodDisruptionBudget> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readPodDisruptionBudgetAsync(UID, NAMESPACE, responseStep));

    V1PodDisruptionBudget received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createPodDisruptionBudget_returnsNewResource() throws InterruptedException {
    V1PodDisruptionBudget resource = new V1PodDisruptionBudget().metadata(createMetadata());
    defineHttpPostResponse(
        PDB_RESOURCE, resource, (json) -> fromJson(json, V1PodDisruptionBudget.class));

    KubernetesTestSupportTest.TestResponseStep<V1PodDisruptionBudget> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createPodDisruptionBudgetAsync(NAMESPACE, resource, responseStep));

    V1PodDisruptionBudget received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void patchPodDisruptionBudgetAsync_returnsUpdatedResource() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    V1PodDisruptionBudget resource = new V1PodDisruptionBudget().metadata(createMetadata());
    defineHttpPatchResponse(
        PDB_RESOURCE, UID, resource, requestBody::set);

    KubernetesTestSupportTest.TestResponseStep<V1PodDisruptionBudget> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);
    testSupport.runSteps(new CallBuilder().patchPodDisruptionBudgetAsync(UID, NAMESPACE,
        new V1Patch(patchBuilder.build().toString()), responseStep));

    V1PodDisruptionBudget received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void deletePodDisruptionBudget_returnsDeletedResource() throws InterruptedException {
    V1Status response = new V1Status();
    V1PodDisruptionBudget resource = new V1PodDisruptionBudget().metadata(createMetadata());
    defineHttpDeleteResponse(PDB_RESOURCE, UID, response, (Consumer<String>) null);

    KubernetesTestSupportTest.TestResponseStep<V1Status> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .deletePodDisruptionBudgetAsync(Objects.requireNonNull(resource.getMetadata()).getName(),
            NAMESPACE, UID, new DeleteOptions(), responseStep));

    V1Status received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(response));
  }

  @Test
  @ResourceLock(value = "server")
  void createSubjectAccessReview_returnsNewResource() throws ApiException {
    V1SubjectAccessReview resource = new V1SubjectAccessReview().metadata(createMetadata());
    defineHttpPostResponse(
        SAR_RESOURCE, resource, (json) -> fromJson(json, V1SubjectAccessReview.class));

    V1SubjectAccessReview received = callBuilder.createSubjectAccessReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createSelfSubjectAccessReview_returnsNewResource() throws ApiException {
    V1SelfSubjectAccessReview resource = new V1SelfSubjectAccessReview().metadata(createMetadata());
    defineHttpPostResponse(
        SSAR_RESOURCE, resource, (json) -> fromJson(json, V1Service.class));

    V1SelfSubjectAccessReview received = callBuilder.createSelfSubjectAccessReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createSelfSubjectRulesReview_returnsNewResource() throws ApiException {
    V1SelfSubjectRulesReview resource = new V1SelfSubjectRulesReview().metadata(createMetadata());
    defineHttpPostResponse(
        SSRR_RESOURCE, resource, (json) -> fromJson(json, V1Service.class));

    V1SelfSubjectRulesReview received = callBuilder.createSelfSubjectRulesReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createTokensReview_returnsNewResource() throws ApiException {
    V1TokenReview resource = new V1TokenReview().metadata(createMetadata());
    defineHttpPostResponse(
        TR_RESOURCE, resource, (json) -> fromJson(json, V1Service.class));

    V1TokenReview received = callBuilder.createTokenReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createCRD_returnsNewResource() throws InterruptedException {
    V1CustomResourceDefinition resource = new V1CustomResourceDefinition().metadata(createMetadata());
    defineHttpPostResponse(
        CRD_RESOURCE, resource, (json) -> fromJson(json, V1CustomResourceDefinition.class));

    KubernetesTestSupportTest.TestResponseStep<V1CustomResourceDefinition> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createCustomResourceDefinitionAsync(resource, responseStep));

    V1CustomResourceDefinition received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void readCRD_returnsResource() throws InterruptedException {
    V1CustomResourceDefinition resource = new V1CustomResourceDefinition().metadata(createMetadata());
    defineHttpGetResponse(CRD_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1CustomResourceDefinition> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readCustomResourceDefinitionAsync(UID, responseStep));

    V1CustomResourceDefinition received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void readCRDMetadata_returnsResourceMetadata() throws ApiException {
    V1CustomResourceDefinition resource = new V1CustomResourceDefinition().metadata(createMetadata());
    defineHttpGetResponse(CRD_RESOURCE, UID, resource);

    assertThat(callBuilder.readCRDMetadata(UID).getMetadata(), equalTo(createMetadata()));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceCRD_returnsUpdatedResource() throws InterruptedException {
    V1CustomResourceDefinition resource = new V1CustomResourceDefinition().metadata(createMetadata());
    defineHttpPutResponse(
        CRD_RESOURCE, UID, resource, (json) -> fromJson(json, V1CustomResourceDefinition.class));

    KubernetesTestSupportTest.TestResponseStep<V1CustomResourceDefinition> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .replaceCustomResourceDefinitionAsync(Objects.requireNonNull(resource.getMetadata()).getName(),
                resource, responseStep));

    V1CustomResourceDefinition received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void listConfigMaps_returnsList() throws InterruptedException {
    V1ConfigMapList list = new V1ConfigMapList().items(Arrays.asList(new V1ConfigMap(), new V1ConfigMap()));
    defineHttpGetResponse(CM_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1ConfigMapList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx").listConfigMapsAsync(NAMESPACE, responseStep));

    V1ConfigMapList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readConfigMap_returnsResource() throws InterruptedException {
    V1ConfigMap resource = new V1ConfigMap().metadata(createMetadata());
    defineHttpGetResponse(CM_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1ConfigMap> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readConfigMapAsync(UID, NAMESPACE, UID, responseStep));

    V1ConfigMap received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createConfigMap_returnsNewResource() throws InterruptedException {
    V1ConfigMap resource = new V1ConfigMap().metadata(createMetadata());
    defineHttpPostResponse(
        CM_RESOURCE, resource, (json) -> fromJson(json, V1ConfigMap.class));

    KubernetesTestSupportTest.TestResponseStep<V1ConfigMap> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createConfigMapAsync(NAMESPACE, resource, responseStep));

    V1ConfigMap received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceConfigMap_returnsUpdatedResource() throws InterruptedException {
    V1ConfigMap resource = new V1ConfigMap().metadata(createMetadata());
    defineHttpPutResponse(
        CM_RESOURCE, UID, resource, (json) -> fromJson(json, V1ConfigMap.class));

    KubernetesTestSupportTest.TestResponseStep<V1ConfigMap> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .replaceConfigMapAsync(Objects.requireNonNull(resource.getMetadata()).getName(),
                NAMESPACE, resource, responseStep));

    V1ConfigMap received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void patchConfigMapAsync_returnsUpdatedResource() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    V1ConfigMap resource = new V1ConfigMap().metadata(createMetadata());
    defineHttpPatchResponse(
        CM_RESOURCE, UID, resource, requestBody::set);

    KubernetesTestSupportTest.TestResponseStep<V1ConfigMap> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);
    testSupport.runSteps(new CallBuilder().patchConfigMapAsync(UID, NAMESPACE, UID,
        new V1Patch(patchBuilder.build().toString()), responseStep));

    V1ConfigMap received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void deleteConfigMap_returnsDeletedResource() throws InterruptedException {
    V1Status response = new V1Status();
    V1ConfigMap resource = new V1ConfigMap().metadata(createMetadata());
    defineHttpDeleteResponse(CM_RESOURCE, UID, response, (Consumer<String>) null)
        .expectingParameter("gracePeriodSeconds", "5");

    KubernetesTestSupportTest.TestResponseStep<V1Status> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withGracePeriodSeconds(5)
        .deleteConfigMapAsync(Objects.requireNonNull(resource.getMetadata()).getName(),
            NAMESPACE, UID, new DeleteOptions(), responseStep));

    V1Status received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(response));
  }

  @Test
  @ResourceLock(value = "server")
  void listEvents_returnsList() throws InterruptedException {
    CoreV1EventList list = new CoreV1EventList().items(Arrays.asList(new CoreV1Event(), new CoreV1Event()));
    defineHttpGetResponse(EVENT_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<CoreV1EventList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx").listEventAsync(NAMESPACE, responseStep));

    CoreV1EventList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readEvent_returnsResource() throws InterruptedException {
    CoreV1Event resource = new CoreV1Event().metadata(createMetadata());
    defineHttpGetResponse(EVENT_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<CoreV1Event> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readEventAsync(UID, NAMESPACE, responseStep));

    CoreV1Event received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createEvent_returnsNewResource() throws InterruptedException {
    CoreV1Event resource = new CoreV1Event().metadata(createMetadata());
    defineHttpPostResponse(
        EVENT_RESOURCE, resource, (json) -> fromJson(json, CoreV1Event.class));

    KubernetesTestSupportTest.TestResponseStep<CoreV1Event> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createEventAsync(NAMESPACE, resource, responseStep));

    CoreV1Event received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createEventSync_returnsNewResource() throws ApiException {
    CoreV1Event resource = new CoreV1Event().metadata(createMetadata());
    defineHttpPostResponse(
        EVENT_RESOURCE, resource, (json) -> fromJson(json, CoreV1Event.class));

    CoreV1Event received = callBuilder.createEvent(NAMESPACE, resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceEvent_returnsUpdatedResource() throws InterruptedException {
    CoreV1Event resource = new CoreV1Event().metadata(createMetadata());
    defineHttpPutResponse(
        EVENT_RESOURCE, UID, resource, (json) -> fromJson(json, CoreV1Event.class));

    KubernetesTestSupportTest.TestResponseStep<CoreV1Event> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .replaceEventAsync(resource.getMetadata().getName(), NAMESPACE, resource, responseStep));

    CoreV1Event received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void listNamespaces_returnsList() throws InterruptedException {
    V1NamespaceList list = new V1NamespaceList()
        .items(Arrays.asList(new V1Namespace(), new V1Namespace()));
    defineHttpGetResponse(NAMESPACE_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1NamespaceList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx")
        .listNamespaceAsync(responseStep));

    V1NamespaceList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  public static final String TEST_VALIDATING_WEBHOOK_NAME = "weblogic.validating.webhooktest";

  @Test
  @ResourceLock(value = "server")
  void listValidatingWebhookConfigurations_returnsList() throws InterruptedException {
    V1ValidatingWebhookConfigurationList list = new V1ValidatingWebhookConfigurationList()
        .items(Arrays.asList(new V1ValidatingWebhookConfiguration(), new V1ValidatingWebhookConfiguration()));
    defineHttpGetResponse(VALIDATING_WEBHOOK_CONFIGURATION_RESOURCE, list)
        .expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1ValidatingWebhookConfigurationList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx")
        .listValidatingWebhookConfigurationAsync(responseStep));

    V1ValidatingWebhookConfigurationList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  @ResourceLock(value = "server")
  void readValidatingWebhookConfiguration_returnsResource() throws InterruptedException {
    V1ValidatingWebhookConfiguration resource =
        new V1ValidatingWebhookConfiguration().metadata(createNameOnlyMetadata(TEST_VALIDATING_WEBHOOK_NAME));
    defineHttpGetResponse(VALIDATING_WEBHOOK_CONFIGURATION_RESOURCE, TEST_VALIDATING_WEBHOOK_NAME, resource);

    KubernetesTestSupportTest.TestResponseStep<V1ValidatingWebhookConfiguration> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .readValidatingWebhookConfigurationAsync(TEST_VALIDATING_WEBHOOK_NAME, responseStep));

    V1ValidatingWebhookConfiguration received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createValidatingWebhookConfiguration_returnsNewResource() throws InterruptedException {
    V1ValidatingWebhookConfiguration resource =
        new V1ValidatingWebhookConfiguration().metadata(createNameOnlyMetadata(TEST_VALIDATING_WEBHOOK_NAME));
    defineHttpPostResponse(VALIDATING_WEBHOOK_CONFIGURATION_RESOURCE,
        resource, (json) -> fromJson(json, V1ValidatingWebhookConfiguration.class));

    KubernetesTestSupportTest.TestResponseStep<V1ValidatingWebhookConfiguration> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createValidatingWebhookConfigurationAsync(resource, responseStep));

    V1ValidatingWebhookConfiguration received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void replaceValodatingWebhookConfiguration_returnsUpdatedResource() throws InterruptedException {
    V1ValidatingWebhookConfiguration validatingWebhookConfig
        = new V1ValidatingWebhookConfiguration()
        .metadata(createNameOnlyMetadata(TEST_VALIDATING_WEBHOOK_NAME))
        .addWebhooksItem(new V1ValidatingWebhook());
    defineHttpPutResponse(
        VALIDATING_WEBHOOK_CONFIGURATION_RESOURCE, TEST_VALIDATING_WEBHOOK_NAME,
        validatingWebhookConfig, (json) -> fromJson(json, V1Secret.class));

    KubernetesTestSupportTest.TestResponseStep<V1ValidatingWebhookConfiguration> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .replaceValidatingWebhookConfigurationAsync(
            Objects.requireNonNull(validatingWebhookConfig.getMetadata()).getName(),
                validatingWebhookConfig, responseStep));

    V1ValidatingWebhookConfiguration received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(validatingWebhookConfig));
  }

  @Test
  @ResourceLock(value = "server")
  void patchValidatingWebhookConfigurationAsync_returnsUpdatedResource() throws InterruptedException {
    AtomicReference<Object> requestBody = new AtomicReference<>();
    V1ValidatingWebhookConfiguration resource
        = new V1ValidatingWebhookConfiguration().metadata(createNameOnlyMetadata(TEST_VALIDATING_WEBHOOK_NAME))
        .addWebhooksItem(new V1ValidatingWebhook().clientConfig(new AdmissionregistrationV1WebhookClientConfig()
            .service(new AdmissionregistrationV1ServiceReference().namespace("ns1"))));
    defineHttpPatchResponse(
        VALIDATING_WEBHOOK_CONFIGURATION_RESOURCE, TEST_VALIDATING_WEBHOOK_NAME,
        resource, requestBody::set);

    KubernetesTestSupportTest.TestResponseStep<V1ValidatingWebhookConfiguration> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.replace("/webhooks/0/timeoutSeconds",  5);
    testSupport.runSteps(new CallBuilder().patchValidatingWebhookConfigurationAsync(TEST_VALIDATING_WEBHOOK_NAME,
        new V1Patch(patchBuilder.build().toString()), responseStep));

    V1ValidatingWebhookConfiguration received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void deleteValidatingWebhookConfiguration_returnsDeletedResource() throws InterruptedException {
    V1Status response = new V1Status();
    V1ValidatingWebhookConfiguration resource =
        new V1ValidatingWebhookConfiguration().metadata(createNameOnlyMetadata(TEST_VALIDATING_WEBHOOK_NAME));
    defineHttpDeleteResponse(VALIDATING_WEBHOOK_CONFIGURATION_RESOURCE,
        TEST_VALIDATING_WEBHOOK_NAME, response, (Consumer<String>) null)
        .expectingParameter("gracePeriodSeconds", "5");

    KubernetesTestSupportTest.TestResponseStep<V1Status> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withGracePeriodSeconds(5)
        .deleteValidatingWebhookConfigurationAsync(Objects.requireNonNull(resource.getMetadata()).getName(),
            new DeleteOptions(), responseStep));

    V1Status received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(response));
  }

  @Test
  @ResourceLock(value = "server")
  void readPersistentVolumeClaim_returnsResource() throws InterruptedException {
    V1PersistentVolumeClaim resource = new V1PersistentVolumeClaim().metadata(createMetadata());
    defineHttpGetResponse(PVC_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1PersistentVolumeClaim> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readPersistentVolumeClaimAsync(UID, NAMESPACE, responseStep));

    V1PersistentVolumeClaim received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createPersistentVolumeClaim_returnsNewResource() throws InterruptedException {
    V1PersistentVolumeClaim resource = new V1PersistentVolumeClaim().metadata(createMetadata());
    defineHttpPostResponse(
        PVC_RESOURCE, resource, (json) -> fromJson(json, V1PersistentVolumeClaim.class));

    KubernetesTestSupportTest.TestResponseStep<V1PersistentVolumeClaim> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createPersistentVolumeClaimAsync(NAMESPACE, resource, responseStep));

    V1PersistentVolumeClaim received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void readPersistentVolume_returnsResource() throws InterruptedException {
    V1PersistentVolume resource = new V1PersistentVolume().metadata(createMetadata());
    defineHttpGetResponse(PV_RESOURCE, UID, resource);

    KubernetesTestSupportTest.TestResponseStep<V1PersistentVolume> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().readPersistentVolumeAsync(UID, responseStep));

    V1PersistentVolume received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  @ResourceLock(value = "server")
  void createPersistentVolume_returnsNewResource() throws InterruptedException {
    V1PersistentVolume resource = new V1PersistentVolume().metadata(createMetadata());
    defineHttpPostResponse(
        PV_RESOURCE, resource, (json) -> fromJson(json, V1PersistentVolumeClaim.class));

    KubernetesTestSupportTest.TestResponseStep<V1PersistentVolume> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createPersistentVolumeAsync(resource, responseStep));

    V1PersistentVolume received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  private Object fromJson(String json, Class<?> aaClass) {
    return new GsonBuilder().create().fromJson(json, aaClass);
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta().namespace(NAMESPACE).name(UID);
  }

  private V1ObjectMeta createNameOnlyMetadata(String name) {
    return new V1ObjectMeta().name(name);
  }

  /** defines a get request for an list of items. */
  private JsonServlet defineHttpGetResponse(String resourceName, Object response) {
    JsonGetServlet servlet = new JsonGetServlet(response);
    defineResource(resourceName, servlet);
    return servlet;
  }

  private JsonServlet defineHttpGetResponse(
      String resourceName, String name, Object response) {
    JsonServlet servlet = new JsonGetServlet(response);
    defineResource(resourceName + "/" + name, servlet);
    return servlet;
  }

  private void defineHttpGetResponse(String resourceName, PseudoServlet pseudoServlet) {
    defineResource(resourceName, pseudoServlet);
  }

  private void defineResource(String resourceName, PseudoServlet servlet) {
    server.setResource(resourceName, servlet);
  }

  private void defineHttpPutResponse(
      String resourceName, String name, Object response, Consumer<String> bodyValidation) {
    defineResource(resourceName + "/" + name, new JsonPutServlet(response, bodyValidation));
  }

  @SuppressWarnings("unused")
  private void defineHttpPutResponse(
      String resourceName, String name, Object response, PseudoServlet pseudoServlet) {
    defineResource(resourceName + "/" + name, pseudoServlet);
  }

  private void defineHttpPostResponse(
      String resourceName, Object response, Consumer<String> bodyValidation) {
    defineResource(resourceName, new JsonPostServlet(response, bodyValidation));
  }

  @SuppressWarnings("unused")
  private void defineHttpPostResponse(
      String resourceName, String name, Object response, PseudoServlet pseudoServlet) {
    defineResource(resourceName + "/" + name, pseudoServlet);
  }

  private JsonServlet defineHttpDeleteResponse(
      String resourceName, String name, Object response, Consumer<String> bodyValidation) {
    StringBuilder compositeName = new StringBuilder(resourceName);
    if (name != null) {
      compositeName.append("/").append(name);
    }
    JsonServlet servlet = new JsonDeleteServlet(response, bodyValidation);
    defineResource(compositeName.toString(), servlet);
    return servlet;
  }

  @SuppressWarnings("unused")
  private void defineHttpDeleteResponse(
      String resourceName, String name, Object response, PseudoServlet pseudoServlet) {
    defineResource(resourceName + "/" + name, pseudoServlet);
  }

  private void defineHttpPatchResponse(
      String resourceName, String name, Object response, Consumer<String> bodyValidation) {
    defineResource(resourceName + "/" + name, new JsonPatchServlet(response, bodyValidation));
  }

  @SuppressWarnings("unused")
  private void defineHttpPatchResponse(
      String resourceName, String name, Object response, PseudoServlet pseudoServlet) {
    defineResource(resourceName + "/" + name, pseudoServlet);
  }

  static class PseudoServletCallDispatcher implements SynchronousCallDispatcher, AsyncRequestStepFactory {
    private static String basePath;
    private SynchronousCallDispatcher underlyingSyncDispatcher;
    private AsyncRequestStepFactory underlyingAsyncRequestStepFactory;

    static Memento installSync(String basePath) throws NoSuchFieldException {
      PseudoServletCallDispatcher.basePath = basePath;
      PseudoServletCallDispatcher dispatcher = new PseudoServletCallDispatcher();
      Memento memento = StaticStubSupport.install(CallBuilder.class, "dispatcher", dispatcher);
      dispatcher.setUnderlyingSyncDispatcher(memento.getOriginalValue());
      return memento;
    }

    static Memento installAsync(String basePath) throws NoSuchFieldException {
      PseudoServletCallDispatcher.basePath = basePath;
      PseudoServletCallDispatcher dispatcher = new PseudoServletCallDispatcher();
      Memento memento = StaticStubSupport.install(CallBuilder.class, "stepFactory", dispatcher);
      dispatcher.setUnderlyingAsyncRequestStepFactory(memento.getOriginalValue());
      return memento;
    }

    void setUnderlyingSyncDispatcher(SynchronousCallDispatcher underlyingSyncDispatcher) {
      this.underlyingSyncDispatcher = underlyingSyncDispatcher;
    }

    void setUnderlyingAsyncRequestStepFactory(AsyncRequestStepFactory underlyingAsyncRequestStepFactory) {
      this.underlyingAsyncRequestStepFactory = underlyingAsyncRequestStepFactory;
    }

    @Override
    public <T> T execute(
        SynchronousCallFactory<T> factory, RequestParams requestParams, Pool<ApiClient> pool)
        throws ApiException {
      return underlyingSyncDispatcher.execute(factory, requestParams, createSingleUsePool());
    }

    @Override
    public <T> Step createRequestAsync(ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory,
                                       RetryStrategy retryStrategy, Pool<ApiClient> helper,
                                       int timeoutSeconds, int maxRetryCount, Integer gracePeriodSeconds,
                                       String fieldSelector, String labelSelector, String resourceVersion) {
      return underlyingAsyncRequestStepFactory.createRequestAsync(
          next, requestParams, factory, retryStrategy, createSingleUsePool(), timeoutSeconds, maxRetryCount,
          gracePeriodSeconds, fieldSelector, labelSelector, resourceVersion);
    }

    private Pool<ApiClient> createSingleUsePool() {
      return new Pool<>() {
        @Override
        protected ApiClient create() {
          ApiClient client = apiClient;
          client.setBasePath(basePath);
          return client;
        }

        @Override
        public void discard(ApiClient client) {

        }
      };
    }
  }

  static class ErrorCodePutServlet extends PseudoServlet {

    final int errorCode;
    int numGetPutResponseCalled = 0;

    ErrorCodePutServlet(int errorCode) {
      this.errorCode = errorCode;
    }

    @Override
    public WebResource getPutResponse() {
      numGetPutResponseCalled++;
      return new WebResource("", errorCode);
    }
  }

  abstract static class JsonServlet extends PseudoServlet {

    private final WebResource response;
    private final List<ParameterExpectation> parameterExpectations = new ArrayList<>();

    JsonServlet(Object returnValue) {
      response = new WebResource(toJson(returnValue), "application/json");
    }

    protected String[] getParameter(String name) {
      return splitQuery(getRequest().getURI()).get(name);
    }

    private Map<String, String[]> splitQuery(String uri) {
      if (Strings.isNullOrEmpty(uri)) {
        return Collections.emptyMap();
      }
      String query = uri.substring(uri.indexOf('?') + 1);
      if (Strings.isNullOrEmpty(query)) {
        return Collections.emptyMap();
      }
      Map<String, String[]> parameters = new HashMap<>();
      for (String s : query.split("&")) {
        Pair<String, String> p = splitQueryParameter(s);
        if (!parameters.containsKey(p.getLeft())) {
          parameters.put(p.getLeft(), new String[] { p.getRight() });
        } else {
          String[] current = parameters.get(p.getLeft());
          String[] updated = new String[current.length + 1];
          System.arraycopy(current, 0, updated, 0, current.length);
          updated[current.length] = p.getRight();
          parameters.put(p.getLeft(), updated);
        }
      }
      return parameters;
    }

    public Pair<String, String> splitQueryParameter(String it) {
      final int idx = it.indexOf("=");
      final String key = idx > 0 ? it.substring(0, idx) : it;
      final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
      return new Pair<>(key, value);
    }

    public WebResource getPatchResponse() throws IOException {
      throw new UnsupportedOperationException();
    }

    WebResource getResponse() throws IOException {
      validateParameters();
      return response;
    }

    @Override
    public WebResource getResponse(String methodType) throws IOException {
      if (methodType.equalsIgnoreCase("GET")) {
        return getGetResponse();
      } else if (methodType.equalsIgnoreCase("PUT")) {
        return getPutResponse();
      } else if (methodType.equalsIgnoreCase("POST")) {
        return getPostResponse();
      } else if (methodType.equalsIgnoreCase("DELETE")) {
        return getDeleteResponse();
      } else if (methodType.equalsIgnoreCase("PATCH")) {
        return getPatchResponse();
      } else {
        throw new IllegalArgumentException(methodType);
      }
    }

    private void validateParameters() throws IOException {
      List<String> validationErrors = new ArrayList<>();
      for (ParameterExpectation expectation : parameterExpectations) {
        String error = expectation.validate();
        if (error != null) {
          validationErrors.add(error);
        }
      }

      if (!validationErrors.isEmpty()) {
        throw new IOException(String.join("\n", validationErrors));
      }
    }

    @SuppressWarnings("UnusedReturnValue")
    JsonServlet expectingParameter(String name, String value) {
      parameterExpectations.add(new ParameterExpectation(name, value));
      return this;
    }

    class ParameterExpectation {
      private final String name;
      private final String expectedValue;

      ParameterExpectation(String name, String expectedValue) {
        this.name = name;
        this.expectedValue = expectedValue;
      }

      String validate() {
        String value = getParameter(name) == null ? null : String.join(",", getParameter(name));
        if (expectedValue.equals(value)) {
          return null;
        }

        return String.format("Expected parameter %s = %s but was %s", name, expectedValue, value);
      }
    }
  }

  static class JsonGetServlet extends JsonServlet {

    private JsonGetServlet(Object returnValue) {
      super(returnValue);
    }

    @Override
    public WebResource getGetResponse() throws IOException {
      return getResponse();
    }
  }

  abstract static class JsonBodyServlet extends JsonServlet {
    private final Consumer<String> bodyValidation;

    private JsonBodyServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue);
      this.bodyValidation = bodyValidation;
    }

    @Override
    WebResource getResponse() throws IOException {
      if (bodyValidation != null) {
        bodyValidation.accept(new String(getBody()));
      }
      return super.getResponse();
    }
  }

  static class JsonPutServlet extends JsonBodyServlet {

    private JsonPutServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue, bodyValidation);
    }

    @Override
    public WebResource getPutResponse() throws IOException {
      return getResponse();
    }
  }

  static class JsonPostServlet extends JsonBodyServlet {

    private JsonPostServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue, bodyValidation);
    }

    @Override
    public WebResource getPostResponse() throws IOException {
      return getResponse();
    }
  }

  static class JsonDeleteServlet extends JsonBodyServlet {

    private JsonDeleteServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue, bodyValidation);
    }

    @Override
    public WebResource getDeleteResponse() throws IOException {
      return getResponse();
    }
  }

  static class JsonPatchServlet extends JsonBodyServlet {

    private JsonPatchServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue, bodyValidation);
    }

    @Override
    public WebResource getPatchResponse() throws IOException {
      return getResponse();
    }
  }
}
