// Copyright (c) 2019, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.GetOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainConditionType;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CLUSTER;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CUSTOM_RESOURCE_DEFINITION;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SUBJECT_ACCESS_REVIEW;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.TOKEN_REVIEW;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KubernetesTestSupportTest {

  private static final String NS = "namespace1";
  private static final String POD_LOG_CONTENTS = "asdfghjkl";
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(SystemClockTestSupport.installClock());
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  // tests for non-namespaced resources

  @Test
  void afterCreateCrd_crdExists() {
    V1CustomResourceDefinition crd = createCrd("mycrd");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.CRD.create(crd, responseStep));

    assertThat(testSupport.getResources(CUSTOM_RESOURCE_DEFINITION), contains(crd));
  }

  @Test
  void afterCreateCrd_incrementNumCalls() {
    V1CustomResourceDefinition crd = createCrd("mycrd");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.CRD.create(crd, responseStep));

    assertThat(testSupport.getNumCalls(), equalTo(1));
  }

  @SuppressWarnings("SameParameterValue")
  private V1CustomResourceDefinition createCrd(String name) {
    return new V1CustomResourceDefinition().metadata(new V1ObjectMeta().name(name));
  }

  @Test
  void afterCreateCrd_crdReturnedInCallResponse() {
    V1CustomResourceDefinition crd = createCrd("mycrd");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.CRD.create(crd, responseStep));

    assertThat(responseStep.callResponse.getObject(), equalTo(crd));
  }

  @Test
  void whenCrdWithSameNameExists_createFails() {
    testSupport.defineResources(createCrd("mycrd"));

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.CRD.create(createCrd("mycrd"), responseStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  void afterReplaceExistingCrd_crdExists() {
    V1CustomResourceDefinition oldCrd = createCrd("mycrd");
    testSupport.defineResources(oldCrd);

    V1CustomResourceDefinition crd = createCrd("mycrd");
    Objects.requireNonNull(crd.getMetadata()).putLabelsItem("be", "different");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.CRD.update(crd, responseStep));

    assertThat(testSupport.getResources(CUSTOM_RESOURCE_DEFINITION), contains(crd));
  }

  @Test
  void whenCrdDefined_readJustMetaData() throws ApiException {
    final V1CustomResourceDefinition crd = createCrd("mycrd");
    Objects.requireNonNull(crd.getMetadata()).setGeneration(1234L);
    testSupport.defineResources(crd);

    V1CustomResourceDefinition result =
        RequestBuilder.CRD.get("mycrd", new GetOptions().isPartialObjectMetadataRequest(true));

    assertThat(result.getMetadata().getGeneration(), equalTo(1234L));
  }

  @Test
  void afterReplaceDomainWithTimeStampEnabled_timeStampIsChanged() {
    DomainResource originalDomain = createDomain(NS, "domain1");
    testSupport.defineResources(originalDomain);
    testSupport.setAddCreationTimestamp(true);

    SystemClockTestSupport.increment();
    testSupport.runSteps(RequestBuilder.DOMAIN.update(
            createDomain(NS, "domain1"), (ResponseStep<DomainResource>) null));

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(getCreationTimestamp(updatedDomain), not(equalTo(getCreationTimestamp(originalDomain))));
  }

  @Test
  void afterReplaceDomainWithTimeStampDisabled_timeStampIsNotChanged() {
    DomainResource originalDomain = createDomain(NS, "domain1");
    testSupport.defineResources(originalDomain);

    SystemClockTestSupport.increment();
    testSupport.runSteps(RequestBuilder.DOMAIN.update(
            createDomain(NS, "domain1"), (ResponseStep<DomainResource>) null));

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(getCreationTimestamp(updatedDomain), equalTo(getCreationTimestamp(originalDomain)));
  }

  @Test
  void afterClusterStatusReplaced_resourceVersionIsIncremented() {
    ClusterResource originalCluster = createCluster(NS, "cluster1");
    testSupport.defineResources(originalCluster);
    originalCluster.getMetadata().setResourceVersion("123");

    testSupport.runSteps(RequestBuilder.CLUSTER.updateStatus(createCluster(NS, "cluster1")
        .withStatus(new ClusterStatus().withMaximumReplicas(8)),
        ClusterResource::getStatus, (ResponseStep<ClusterResource>) null));

    ClusterResource updatedCluster = testSupport.getResourceWithName(CLUSTER, "cluster1");
    assertThat(updatedCluster.getMetadata().getResourceVersion(), equalTo("124"));
  }

  @Test
  void afterDomainStatusReplaced_resourceVersionIsIncremented() {
    DomainResource originalDomain = createDomain(NS, "domain1");
    testSupport.defineResources(originalDomain);
    originalDomain.getMetadata().setResourceVersion("123");

    testSupport.runSteps(RequestBuilder.DOMAIN.updateStatus(createDomain(NS, "domain1")
        .withStatus(new DomainStatus().addCondition(new DomainCondition(DomainConditionType.COMPLETED))),
        DomainResource::getStatus, (ResponseStep<DomainResource>) null));

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(updatedDomain.getMetadata().getResourceVersion(), equalTo("124"));
  }

  @Test
  void afterPatchDomainWithTimeStampEnabled_timeStampIsNotChanged() {
    DomainResource originalDomain = createDomain(NS, "domain1");
    testSupport.defineResources(originalDomain);
    testSupport.setAddCreationTimestamp(true);
    SystemClockTestSupport.increment();

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);
    testSupport.runSteps(RequestBuilder.DOMAIN.patch(NS, "domain1", V1Patch.PATCH_FORMAT_JSON_PATCH,
            new V1Patch(patchBuilder.build().toString()), (ResponseStep<DomainResource>) null));

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(getCreationTimestamp(updatedDomain), equalTo(getCreationTimestamp(originalDomain)));
  }

  @Test
  void afterPatchDomainAsynchronously_statusIsUnchanged() {
    DomainResource originalDomain = createDomain(NS, "domain")
        .withStatus(new DomainStatus().withMessage("leave this"));
    testSupport.defineResources(originalDomain);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.replace("/status/message", "changed it");
    patchBuilder.add("/status/reason", "added a reason");
    testSupport.runSteps(RequestBuilder.DOMAIN.patch(NS, "domain", V1Patch.PATCH_FORMAT_JSON_PATCH,
        getPatchBody(patchBuilder), (ResponseStep<DomainResource>) null));

    assertThat(getDomainStatus("domain").getMessage(), equalTo("leave this"));
    assertThat(getDomainStatus("domain").getReason(), nullValue());
  }

  @Test
  void afterPatchDomainSynchronously_statusIsUnchanged() throws ApiException {
    DomainResource originalDomain = createDomain(NS, "domain")
        .withStatus(new DomainStatus().withMessage("leave this"));
    testSupport.defineResources(originalDomain);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.replace("/status/message", "changed it");
    patchBuilder.add("/status/reason", "added a reason");
    RequestBuilder.DOMAIN.patch(NS, "domain", V1Patch.PATCH_FORMAT_JSON_PATCH, getPatchBody(patchBuilder));

    assertThat(getDomainStatus("domain").getMessage(), equalTo("leave this"));
    assertThat(getDomainStatus("domain").getReason(), nullValue());
  }

  private V1Patch getPatchBody(JsonPatchBuilder patchBuilder) {
    return new V1Patch(patchBuilder.build().toString());
  }

  @Test
  void afterReplaceDomainAsync_statusIsUnchanged() {
    DomainResource originalDomain
        = createDomain(NS, "domain1").withStatus(new DomainStatus().withMessage("leave this"));
    testSupport.defineResources(originalDomain);

    DomainResource newDomain = createDomain(NS, "domain1");
    testSupport.runSteps(RequestBuilder.DOMAIN.update(newDomain, (ResponseStep<DomainResource>) null));

    assertThat(getDomainStatus("domain1").getMessage(), equalTo("leave this"));
  }

  private @Nonnull DomainStatus getDomainStatus(String name) {
    return Optional.ofNullable((DomainResource) testSupport.getResourceWithName(DOMAIN, name))
          .map(DomainResource::getStatus)
          .orElse(new DomainStatus());

  }

  @Test
  void afterReplaceDomainStatusAsync_specIsUnchanged() {
    DomainResource originalDomain = createDomain(NS, "domain1").withSpec(new DomainSpec().withReplicas(5));
    testSupport.defineResources(originalDomain);

    DomainResource newDomain = createDomain(NS, "domain1");
    testSupport.runSteps(RequestBuilder.DOMAIN.updateStatus(newDomain,
        DomainResource::getStatus, (ResponseStep<DomainResource>) null));

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(updatedDomain.getSpec().getReplicas(), equalTo(5));
  }

  @Test
  void afterReplaceDomainStatusSynchronously_specIsUnchanged() throws ApiException {
    DomainResource originalDomain = createDomain(NS, "domain1").withSpec(new DomainSpec().withReplicas(5));
    testSupport.defineResources(originalDomain);

    DomainResource newDomain = createDomain(NS, "domain1");
    RequestBuilder.DOMAIN.updateStatus(newDomain, DomainResource::getStatus);

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(updatedDomain.getSpec().getReplicas(), equalTo(5));
  }

  private OffsetDateTime getCreationTimestamp(DomainResource domain) {
    return domain.getMetadata().getCreationTimestamp();
  }

  @Test
  void afterCreateTokenReview_tokenReviewExists() throws ApiException {
    V1TokenReview tokenReview = new V1TokenReview().metadata(new V1ObjectMeta().name("tr"));

    RequestBuilder.TR.create(tokenReview);

    assertThat(testSupport.getResources(TOKEN_REVIEW), contains(tokenReview));
  }

  @Test
  void afterCreateSubjectAccessReview_subjectAccessReviewExists() throws ApiException {
    V1SubjectAccessReview sar = new V1SubjectAccessReview().metadata(new V1ObjectMeta().name("rr"));

    RequestBuilder.SAR.create(sar);

    assertThat(testSupport.getResources(SUBJECT_ACCESS_REVIEW), contains(sar));
  }

  // tests for namespaced resources

  @Test
  void afterCreatePod_podExists() {
    V1Pod pod = createPod(NS, "mycrd");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.POD.create(pod, responseStep));

    assertThat(testSupport.getResources(POD), contains(pod));
  }

  private V1Pod createPod(String namespace, String name) {
    return new V1Pod().metadata(new V1ObjectMeta().namespace(namespace).name(name));
  }

  @Test
  void afterCreatePodsWithSameNameAndDifferentNamespaces_bothExist() {
    V1Pod pod1 = createPod("ns1", "mycrd");
    V1Pod pod2 = createPod("ns2", "mycrd");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.POD.create(pod1, responseStep));
    testSupport.runSteps(RequestBuilder.POD.create(pod2, responseStep));

    assertThat(testSupport.getResources(POD), containsInAnyOrder(pod1, pod2));
  }

  @Test
  void whenHttpErrorAssociatedWithResource_callResponseIsError() {
    testSupport.failOnResource(POD, "pod1", "ns2", HTTP_BAD_REQUEST);

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.POD.delete("ns2", "pod1", responseStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(responseStep.callResponse.getHttpStatusCode(), equalTo(HTTP_BAD_REQUEST));
  }

  @Test
  void whenHttpErrorNotAssociatedWithResource_ignoreIt() {
    testSupport.failOnResource(POD, "pod1", "ns2", HTTP_BAD_REQUEST);

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.POD.delete("ns2", "pod2", responseStep));
  }

  @Test
  void listPodSelectsByLabel() {
    V1Pod pod1 = createLabeledPod("pod1", "ns1", ImmutableMap.of("k1", "v1", "k2", "v2"));
    V1Pod pod2 = createLabeledPod("pod2", "ns1", ImmutableMap.of("k1", "v2"));
    V1Pod pod3 = createLabeledPod("pod3", "ns1", ImmutableMap.of("k1", "v1", "k2", "v3"));
    V1Pod pod4 = createLabeledPod("pod4", "ns2", ImmutableMap.of("k1", "v1", "k2", "v3"));
    testSupport.defineResources(pod1, pod2, pod3, pod4);

    TestResponseStep<V1PodList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.POD.list("ns1", new ListOptions().labelSelector("k1=v1"), responseStep));

    assertThat(responseStep.callResponse.getObject().getItems(), containsInAnyOrder(pod1, pod3));
  }

  private V1Pod createLabeledPod(String name, String namespace, Map<String, String> labels) {
    return new V1Pod().metadata(new V1ObjectMeta().namespace(namespace).name(name).labels(labels));
  }

  @Test
  void afterPatchPodWithoutExistingLabel_podHasLabel() {
    V1Pod pod1 = createLabeledPod("pod1", "ns1", ImmutableMap.of());
    testSupport.defineResources(pod1);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder().add("/metadata/labels/k1", "v2");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.POD.patch("ns1", "pod1",
        V1Patch.PATCH_FORMAT_JSON_PATCH, new V1Patch(patchBuilder.build().toString()), responseStep));

    V1Pod pod2 = (V1Pod) testSupport.getResources(POD).stream().findFirst().orElse(pod1);
    assertThat(Objects.requireNonNull(pod2.getMetadata()).getLabels(), hasEntry("k1", "v2"));
  }

  @Test
  void afterPatchPodWithExistingLabel_labelValueChanged() {
    V1Pod pod1 = createLabeledPod("pod1", "ns1", ImmutableMap.of("k1", "v1"));
    testSupport.defineResources(pod1);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder().replace("/metadata/labels/k1", "v2");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.POD.patch("ns1", "pod1",
        V1Patch.PATCH_FORMAT_JSON_PATCH, new V1Patch(patchBuilder.build().toString()), responseStep));

    V1Pod pod2 = (V1Pod) testSupport.getResources(POD).stream().findFirst().orElse(pod1);
    assertThat(Objects.requireNonNull(pod2.getMetadata()).getLabels(), hasEntry("k1", "v2"));
  }

  @Test
  void listClusterResource_returnsAllInNamespace() {
    ClusterResource cluster1 = createCluster("ns1", "cluster1");
    ClusterResource cluster2 = createCluster("ns1", "cluster2");
    ClusterResource cluster3 = createCluster("ns2", "cluster3");
    testSupport.defineResources(cluster1, cluster2, cluster3);

    TestResponseStep<ClusterList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.CLUSTER.list("ns1", responseStep));

    assertThat(responseStep.callResponse.getObject().getItems(),
               containsInAnyOrder(cluster1, cluster2));
  }

  @Test
  void afterReplaceClusterStatusAsync_specIsUnchanged() {
    ClusterResource originalCluster = createCluster(NS, "cluster1").spec(new ClusterSpec().withReplicas(5));
    testSupport.defineResources(originalCluster);

    ClusterResource newCluster = createCluster(NS, "cluster1");
    testSupport.runSteps(RequestBuilder.CLUSTER.updateStatus(
            newCluster, ClusterResource::getStatus, (ResponseStep<ClusterResource>) null));

    ClusterResource updatedCluster = testSupport.getResourceWithName(CLUSTER, "cluster1");
    assertThat(updatedCluster.getSpec().getReplicas(), equalTo(5));
  }

  private ClusterResource createCluster(String namespace, String name) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().name(name).namespace(namespace))
        .withStatus(new ClusterStatus());
  }

  @Test
  void listDomain_returnsAllInNamespace() {
    DomainResource dom1 = createDomain("ns1", "domain1");
    DomainResource dom2 = createDomain("ns1", "domain2");
    DomainResource dom3 = createDomain("ns2", "domain3");
    testSupport.defineResources(dom1, dom2, dom3);

    TestResponseStep<DomainList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.DOMAIN.list("ns1", responseStep));

    assertThat(responseStep.callResponse.getObject().getItems(), containsInAnyOrder(dom1, dom2));
  }

  private DomainResource createDomain(String namespace, String name) {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().name(name).namespace(namespace))
        .withStatus(new DomainStatus());
  }

  @Test
  void listService_returnsAllInNamespace() {
    V1Service s1 = createService("ns1", "service1");
    V1Service s2 = createService("ns1", "service2");
    V1Service s3 = createService("ns2", "service3");
    testSupport.defineResources(s1, s2, s3);

    TestResponseStep<V1ServiceList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.SERVICE.list("ns1", responseStep));

    assertThat(responseStep.callResponse.getObject().getItems(), containsInAnyOrder(s1, s2));
  }

  private V1Service createService(String namespace, String name) {
    return new V1Service().metadata(new V1ObjectMeta().name(name).namespace(namespace));
  }

  @Test
  void listEventWithSelector_returnsMatches() {
    CoreV1Event s1 = createEvent("ns1", "event1", "walk").involvedObject(kind("bird"));
    CoreV1Event s2 = createEvent("ns1", "event2", "walk");
    CoreV1Event s3 = createEvent("ns1", "event3", "walk").involvedObject(kind("bird"));
    CoreV1Event s4 = createEvent("ns1", "event4", "run").involvedObject(kind("frog"));
    CoreV1Event s5 = createEvent("ns1", "event5", "run").involvedObject(kind("bird"));
    CoreV1Event s6 = createEvent("ns2", "event3", "walk").involvedObject(kind("bird"));
    testSupport.defineResources(s1, s2, s3, s4, s5, s6);

    TestResponseStep<CoreV1EventList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.EVENT.list("ns1",
        new ListOptions().fieldSelector("action=walk,involvedObject.kind=bird"), responseStep));

    assertThat(responseStep.callResponse.getObject().getItems(), containsInAnyOrder(s1, s3));
  }

  private V1ObjectReference kind(String kind) {
    return new V1ObjectReference().kind(kind);
  }

  private CoreV1Event createEvent(String namespace, String name, String act) {
    return new CoreV1Event().metadata(new V1ObjectMeta().name(name).namespace(namespace)).action(act);
  }

  @Test
  void listSecrets_returnsAllInNamespace() {
    V1Secret s1 = createSecret("ns1", "secret1");
    V1Secret s2 = createSecret("ns1", "secret2");
    V1Secret s3 = createSecret("ns2", "secret3");
    testSupport.defineResources(s1, s2, s3);
    
    TestResponseStep<V1SecretList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.SECRET.list("ns1", responseStep));

    assertThat(responseStep.callResponse.getObject().getItems(), containsInAnyOrder(s1, s2));

  }

  private V1Secret createSecret(String namespace, String name) {
    return new V1Secret().metadata(new V1ObjectMeta().name(name).namespace(namespace));
  }

  @Test
  void whenConfigMapNotFound_readStatusIsNotFound() {
    TestResponseStep<V1ConfigMap> endStep = new TestResponseStep<>();
    testSupport.runSteps(RequestBuilder.CM.get("", "", endStep));

    assertThat(endStep.callResponse.getHttpStatusCode(), equalTo(HTTP_NOT_FOUND));
    assertThrows(ApiException.class, () -> endStep.callResponse.throwsApiException());
  }

  @Test
  void whenDefined_readPodLog() {
    TestResponseStep<RequestBuilder.StringObject> endStep = new TestResponseStep<>();
    testSupport.definePodLog("name", "namespace", POD_LOG_CONTENTS);

    testSupport.runSteps(RequestBuilder.POD.logs("namespace", "name", "", endStep));

    assertThat(endStep.callResponse.getObject().value(), equalTo(POD_LOG_CONTENTS));
  }

  @Test
  void deleteNamespace_deletesAllMatchingNamespacedResources() {
    DomainResource dom1 = createDomain("ns1", "domain1");
    DomainResource dom2 = createDomain("ns2", "domain2");
    V1Service s1 = createService("ns1", "service1");
    V1Service s2 = createService("ns2", "service2");
    V1Pod p1 = createPod("ns1", "pod1");
    V1Pod p2 = createPod("ns2", "pod2");
    testSupport.defineResources(dom1, dom2, s1, s2, p1, p2);

    testSupport.deleteNamespace("ns1");

    assertThat(getResourcesInNamespace("ns1"), empty());
    assertThat(getResourcesInNamespace("ns2"), hasSize(3));
  }

  private List<KubernetesObject> getResourcesInNamespace(String name) {
    List<KubernetesObject> result = new ArrayList<>();
    result.addAll(getResourcesInNamespace(DOMAIN, name));
    result.addAll(getResourcesInNamespace(SERVICE, name));
    result.addAll(getResourcesInNamespace(POD, name));
    return result;
  }

  private List<KubernetesObject> getResourcesInNamespace(String resourceType, String name) {
    return testSupport.<KubernetesObject>getResources(resourceType).stream()
          .filter(n -> name.equals(getNamespace(n)))
          .collect(Collectors.toCollection(ArrayList::new));
  }

  private String getNamespace(KubernetesObject object) {
    return object.getMetadata().getNamespace();
  }

  static class TestResponseStep<T extends KubernetesType> extends DefaultResponseStep<T> {

    private KubernetesApiResponse<T> callResponse;
    private final Semaphore responseAvailableSignal = new Semaphore(0);

    TestResponseStep() {
      super(null);
    }

    @Override
    public Result onFailure(Packet packet, KubernetesApiResponse<T> callResponse) {
      this.callResponse = callResponse;
      responseAvailableSignal.release();
      return super.onFailure(packet, callResponse);
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<T> callResponse) {
      this.callResponse = callResponse;
      responseAvailableSignal.release();
      return super.onSuccess(packet, callResponse);
    }
  }
}
