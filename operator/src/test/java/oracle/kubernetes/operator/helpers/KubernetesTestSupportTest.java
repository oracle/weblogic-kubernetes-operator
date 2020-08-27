// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.json.Json;
import javax.json.JsonPatchBuilder;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1EventList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1NamespaceSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CUSTOM_RESOURCE_DEFINITION;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SUBJECT_ACCESS_REVIEW;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.TOKEN_REVIEW;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class KubernetesTestSupportTest {

  private static final String NS = "namespace1";
  private static final String POD_LOG_CONTENTS = "asdfghjkl";
  List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(SystemClockTestSupport.installClock());
  }

  /**
   * Tear down test.
   * @throws Exception on failure
   */
  @After
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  // tests for non-namespaced resources

  @Test
  public void afterCreateCrd_crdExists() {
    V1CustomResourceDefinition crd = createCrd("mycrd");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createCustomResourceDefinitionAsync(crd, responseStep));

    assertThat(testSupport.getResources(CUSTOM_RESOURCE_DEFINITION), contains(crd));
  }

  @Test
  public void afterCreateCrd_incrementNumCalls() {
    V1CustomResourceDefinition crd = createCrd("mycrd");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createCustomResourceDefinitionAsync(crd, responseStep));

    assertThat(testSupport.getNumCalls(), equalTo(1));
  }

  @SuppressWarnings("SameParameterValue")
  private V1CustomResourceDefinition createCrd(String name) {
    return new V1CustomResourceDefinition().metadata(new V1ObjectMeta().name(name));
  }

  @Test
  public void afterCreateCrd_crdReturnedInCallResponse() {
    V1CustomResourceDefinition crd = createCrd("mycrd");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createCustomResourceDefinitionAsync(crd, responseStep));

    assertThat(responseStep.callResponse.getResult(), equalTo(crd));
  }

  @Test
  public void whenCrdWithSameNameExists_createFails() {
    testSupport.defineResources(createCrd("mycrd"));

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    Step steps =
          new CallBuilder().createCustomResourceDefinitionAsync(createCrd("mycrd"), responseStep);
    testSupport.runSteps(steps);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void afterReplaceExistingCrd_crdExists() {
    V1CustomResourceDefinition oldCrd = createCrd("mycrd");
    testSupport.defineResources(oldCrd);

    V1CustomResourceDefinition crd = createCrd("");
    Objects.requireNonNull(crd.getMetadata()).putLabelsItem("be", "different");

    TestResponseStep<V1CustomResourceDefinition> responseStep = new TestResponseStep<>();
    Step steps = new CallBuilder().replaceCustomResourceDefinitionAsync("mycrd", crd, responseStep);
    testSupport.runSteps(steps);

    assertThat(testSupport.getResources(CUSTOM_RESOURCE_DEFINITION), contains(crd));
  }

  @Test
  public void afterReplaceDomainWithTimeStampEnabled_timeStampIsChanged() {
    Domain originalDomain = createDomain(NS, "domain1");
    testSupport.defineResources(originalDomain);
    testSupport.setAddCreationTimestamp(true);

    SystemClockTestSupport.increment();
    Step steps = new CallBuilder().replaceDomainAsync("domain1", NS, createDomain(NS, "domain1"), null);
    testSupport.runSteps(steps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(getCreationTimestamp(updatedDomain), not(equalTo(getCreationTimestamp(originalDomain))));
  }

  @Test
  public void afterReplaceDomainWithTimeStampDisabled_timeStampIsNotChanged() {
    Domain originalDomain = createDomain(NS, "domain1");
    testSupport.defineResources(originalDomain);

    SystemClockTestSupport.increment();
    Step steps = new CallBuilder().replaceDomainAsync("domain1", NS, createDomain(NS, "domain1"), null);
    testSupport.runSteps(steps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(getCreationTimestamp(updatedDomain), equalTo(getCreationTimestamp(originalDomain)));
  }

  @Test
  public void afterPatchDomainWithTimeStampEnabled_timeStampIsNotChanged() {
    Domain originalDomain = createDomain(NS, "domain1");
    testSupport.defineResources(originalDomain);
    testSupport.setAddCreationTimestamp(true);
    SystemClockTestSupport.increment();

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/spec/replicas", 5);
    Step steps = new CallBuilder().patchDomainAsync("domain1", NS, new V1Patch(patchBuilder.build().toString()), null);
    testSupport.runSteps(steps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(getCreationTimestamp(updatedDomain), equalTo(getCreationTimestamp(originalDomain)));
  }

  @Test
  public void afterPatchDomainAsynchronously_statusIsUnchanged() {
    Domain originalDomain = createDomain(NS, "domain").withStatus(new DomainStatus().withMessage("leave this"));
    testSupport.defineResources(originalDomain);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.replace("/status/message", "changed it");
    patchBuilder.add("/status/reason", "added a reason");
    Step steps = new CallBuilder().patchDomainAsync("domain", NS, getPatchBody(patchBuilder), null);
    testSupport.runSteps(steps);

    assertThat(getDomainStatus("domain").getMessage(), equalTo("leave this"));
    assertThat(getDomainStatus("domain").getReason(), nullValue());
  }

  @Test
  public void afterPatchDomainSynchronously_statusIsUnchanged() throws ApiException {
    Domain originalDomain = createDomain(NS, "domain").withStatus(new DomainStatus().withMessage("leave this"));
    testSupport.defineResources(originalDomain);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.replace("/status/message", "changed it");
    patchBuilder.add("/status/reason", "added a reason");
    new CallBuilder().patchDomain("domain", NS, getPatchBody(patchBuilder));

    assertThat(getDomainStatus("domain").getMessage(), equalTo("leave this"));
    assertThat(getDomainStatus("domain").getReason(), nullValue());
  }

  private V1Patch getPatchBody(JsonPatchBuilder patchBuilder) {
    return new V1Patch(patchBuilder.build().toString());
  }

  @Test
  public void afterReplaceDomainAsync_statusIsUnchanged() {
    Domain originalDomain = createDomain(NS, "domain1").withStatus(new DomainStatus().withMessage("leave this"));
    testSupport.defineResources(originalDomain);

    Domain newDomain = createDomain(NS, "domain1");
    Step steps = new CallBuilder().replaceDomainAsync("domain1", NS, newDomain, null);
    testSupport.runSteps(steps);

    assertThat(getDomainStatus("domain1").getMessage(), equalTo("leave this"));
  }

  private @Nonnull DomainStatus getDomainStatus(String name) {
    return Optional.ofNullable((Domain) testSupport.getResourceWithName(DOMAIN, name))
          .map(Domain::getStatus)
          .orElse(new DomainStatus());

  }

  @Test
  public void afterReplaceDomainStatusAsync_specIsUnchanged() {
    Domain originalDomain = createDomain(NS, "domain1").withSpec(new DomainSpec().withReplicas(5));
    testSupport.defineResources(originalDomain);

    Domain newDomain = createDomain(NS, "domain1");
    Step steps = new CallBuilder().replaceDomainStatusAsync("domain1", NS, newDomain, null);
    testSupport.runSteps(steps);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(updatedDomain.getSpec().getReplicas(), equalTo(5));
  }

  @Test
  public void afterReplaceDomainStatusSynchronously_specIsUnchanged() throws ApiException {
    Domain originalDomain = createDomain(NS, "domain1").withSpec(new DomainSpec().withReplicas(5));
    testSupport.defineResources(originalDomain);

    Domain newDomain = createDomain(NS, "domain1");
    new CallBuilder().replaceDomainStatus("domain1", NS, newDomain);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, "domain1");
    assertThat(updatedDomain.getSpec().getReplicas(), equalTo(5));
  }

  private DateTime getCreationTimestamp(Domain domain) {
    return domain.getMetadata().getCreationTimestamp();
  }

  @Test
  public void afterCreateTokenReview_tokenReviewExists() throws ApiException {
    V1TokenReview tokenReview = new V1TokenReview().metadata(new V1ObjectMeta().name("tr"));

    new CallBuilder().createTokenReview(tokenReview);

    assertThat(testSupport.getResources(TOKEN_REVIEW), contains(tokenReview));
  }

  @Test
  public void whenHttpErrorNotAssociatedWithResource_dontThrowException() throws ApiException {
    testSupport.failOnResource(TOKEN_REVIEW, "tr2", HTTP_BAD_REQUEST);
    V1TokenReview tokenReview = new V1TokenReview().metadata(new V1ObjectMeta().name("tr"));

    new CallBuilder().createTokenReview(tokenReview);
  }

  @Test(expected = ApiException.class)
  public void whenHttpErrorAssociatedWithResource_throwException() throws ApiException {
    testSupport.failOnResource(TOKEN_REVIEW, "tr", HTTP_BAD_REQUEST);
    V1TokenReview tokenReview = new V1TokenReview().metadata(new V1ObjectMeta().name("tr"));

    new CallBuilder().createTokenReview(tokenReview);
  }

  @Test
  public void afterCreateSubjectAccessReview_subjectAccessReviewExists() throws ApiException {
    V1SubjectAccessReview sar = new V1SubjectAccessReview().metadata(new V1ObjectMeta().name("rr"));

    new CallBuilder().createSubjectAccessReview(sar);

    assertThat(testSupport.getResources(SUBJECT_ACCESS_REVIEW), contains(sar));
  }

  // tests for namespaced resources

  @Test
  public void afterCreatePod_podExists() {
    V1Pod pod = createPod(NS, "mycrd");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createPodAsync(NS, pod, responseStep));

    assertThat(testSupport.getResources(POD), contains(pod));
  }

  private V1Pod createPod(String namespace, String name) {
    return new V1Pod().metadata(new V1ObjectMeta().namespace(namespace).name(name));
  }

  @Test
  public void afterCreatePodsWithSameNameAndDifferentNamespaces_bothExist() {
    V1Pod pod1 = createPod("ns1", "mycrd");
    V1Pod pod2 = createPod("ns2", "mycrd");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createPodAsync("ns1", pod1, responseStep));
    testSupport.runSteps(new CallBuilder().createPodAsync("ns2", pod2, responseStep));

    assertThat(testSupport.getResources(POD), containsInAnyOrder(pod1, pod2));
  }

  @Test
  public void afterDeletePod_podsInDifferentNamespacesStillExist() {
    V1Pod pod1 = createPod("ns1", "mycrd");
    V1Pod pod2 = createPod("ns2", "mycrd");
    V1Pod pod3 = createPod("ns3", "another");
    testSupport.defineResources(pod1, pod2, pod3);

    TestResponseStep<V1Status> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().deletePodAsync("mycrd", "ns2", "", null, responseStep));

    assertThat(testSupport.getResources(POD), containsInAnyOrder(pod1, pod3));
  }

  @Test
  public void whenHttpErrorAssociatedWithResource_callResponseIsError() {
    testSupport.failOnResource(POD, "pod1", "ns2", HTTP_BAD_REQUEST);

    TestResponseStep<V1Status> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().deletePodAsync("pod1", "ns2", "", null, responseStep));

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
    assertThat(responseStep.callResponse.getStatusCode(), equalTo(HTTP_BAD_REQUEST));
  }

  @Test
  public void whenHttpErrorNotAssociatedWithResource_ignoreIt() {
    testSupport.failOnResource(POD, "pod1", "ns2", HTTP_BAD_REQUEST);

    TestResponseStep<V1Status> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().deletePodAsync("pod2", "ns2", "", null, responseStep));
  }

  @Test
  public void listPodSelectsByLabel() {
    V1Pod pod1 = createLabeledPod("pod1", "ns1", ImmutableMap.of("k1", "v1", "k2", "v2"));
    V1Pod pod2 = createLabeledPod("pod2", "ns1", ImmutableMap.of("k1", "v2"));
    V1Pod pod3 = createLabeledPod("pod3", "ns1", ImmutableMap.of("k1", "v1", "k2", "v3"));
    V1Pod pod4 = createLabeledPod("pod4", "ns2", ImmutableMap.of("k1", "v1", "k2", "v3"));
    testSupport.defineResources(pod1, pod2, pod3, pod4);

    TestResponseStep<V1PodList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(
          new CallBuilder().withLabelSelector("k1=v1").listPodAsync("ns1", responseStep));

    assertThat(responseStep.callResponse.getResult().getItems(), containsInAnyOrder(pod1, pod3));
  }

  private V1Pod createLabeledPod(String name, String namespace, Map<String, String> labels) {
    return new V1Pod().metadata(new V1ObjectMeta().namespace(namespace).name(name).labels(labels));
  }

  @Test
  public void afterPatchPodWithoutExistingLabel_podHasLabel() {
    V1Pod pod1 = createLabeledPod("pod1", "ns1", ImmutableMap.of());
    testSupport.defineResources(pod1);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder().add("/metadata/labels/k1", "v2");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(
          new CallBuilder().patchPodAsync("pod1", "ns1", "",
              new io.kubernetes.client.custom.V1Patch(patchBuilder.build().toString()), responseStep));

    V1Pod pod2 = (V1Pod) testSupport.getResources(POD).stream().findFirst().orElse(pod1);
    assertThat(Objects.requireNonNull(pod2.getMetadata()).getLabels(), hasEntry("k1", "v2"));
  }

  @Test
  public void afterPatchPodWithExistingLabel_labelValueChanged() {
    V1Pod pod1 = createLabeledPod("pod1", "ns1", ImmutableMap.of("k1", "v1"));
    testSupport.defineResources(pod1);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder().replace("/metadata/labels/k1", "v2");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(
          new CallBuilder().patchPodAsync("pod1", "ns1", "",
              new io.kubernetes.client.custom.V1Patch(patchBuilder.build().toString()), responseStep));

    V1Pod pod2 = (V1Pod) testSupport.getResources(POD).stream().findFirst().orElse(pod1);
    assertThat(Objects.requireNonNull(pod2.getMetadata()).getLabels(), hasEntry("k1", "v2"));
  }

  @Test
  public void listDomain_returnsAllInNamespace() {
    Domain dom1 = createDomain("ns1", "domain1");
    Domain dom2 = createDomain("ns1", "domain2");
    Domain dom3 = createDomain("ns2", "domain3");
    testSupport.defineResources(dom1, dom2, dom3);

    TestResponseStep<DomainList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().listDomainAsync("ns1", responseStep));

    assertThat(responseStep.callResponse.getResult().getItems(), containsInAnyOrder(dom1, dom2));
  }

  private Domain createDomain(String namespace, String name) {
    return new Domain().withMetadata(new V1ObjectMeta().name(name).namespace(namespace));
  }

  @Test
  public void listService_returnsAllInNamespace() {
    V1Service s1 = createService("ns1", "service1");
    V1Service s2 = createService("ns1", "service2");
    V1Service s3 = createService("ns2", "service3");
    testSupport.defineResources(s1, s2, s3);

    TestResponseStep<V1ServiceList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().listServiceAsync("ns1", responseStep));

    assertThat(responseStep.callResponse.getResult().getItems(), containsInAnyOrder(s1, s2));
  }

  private V1Service createService(String namespace, String name) {
    return new V1Service().metadata(new V1ObjectMeta().name(name).namespace(namespace));
  }

  @Test
  public void listEventWithSelector_returnsMatches() {
    V1Event s1 = createEvent("ns1", "event1", "walk").involvedObject(kind("bird"));
    V1Event s2 = createEvent("ns1", "event2", "walk");
    V1Event s3 = createEvent("ns1", "event3", "walk").involvedObject(kind("bird"));
    V1Event s4 = createEvent("ns1", "event4", "run").involvedObject(kind("frog"));
    V1Event s5 = createEvent("ns1", "event5", "run").involvedObject(kind("bird"));
    V1Event s6 = createEvent("ns2", "event3", "walk").involvedObject(kind("bird"));
    testSupport.defineResources(s1, s2, s3, s4, s5, s6);

    TestResponseStep<V1EventList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(
          new CallBuilder()
                .withFieldSelector("action=walk,involvedObject.kind=bird")
                .listEventAsync("ns1", responseStep));

    assertThat(responseStep.callResponse.getResult().getItems(), containsInAnyOrder(s1, s3));
  }

  private V1ObjectReference kind(String kind) {
    return new V1ObjectReference().kind(kind);
  }

  private V1Event createEvent(String namespace, String name, String act) {
    return new V1Event().metadata(new V1ObjectMeta().name(name).namespace(namespace)).action(act);
  }

  @Test
  public void listSecrets_returnsAllInNamespace() {
    V1Secret s1 = createSecret("ns1", "secret1");
    V1Secret s2 = createSecret("ns1", "secret2");
    V1Secret s3 = createSecret("ns2", "secret3");
    testSupport.defineResources(s1, s2, s3);
    
    TestResponseStep<V1SecretList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(
          new CallBuilder()
                .listSecretsAsync("ns1", responseStep));

    assertThat(responseStep.callResponse.getResult().getItems(), containsInAnyOrder(s1, s2));

  }

  private V1Secret createSecret(String namespace, String name) {
    return new V1Secret().metadata(new V1ObjectMeta().name(name).namespace(namespace));
  }

  @Test
  public void listConfigMap_includesResourceVersion() {
    testSupport.defineResources(createNamespaces(150));

    final V1ListMeta meta = getConfigMapListResponseMeta("ns1", new CallBuilder());

    assertThat(meta.getResourceVersion(), notNullValue());
  }

  private V1ListMeta getConfigMapListResponseMeta(String namespace, CallBuilder callBuilder) {
    TestResponseStep<V1ConfigMapList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(callBuilder.listConfigMapsAsync(namespace, responseStep));
    return responseStep.callResponse.getResult().getMetadata();
  }

  @Test
  public void listConfigMapTwice_hasSameResourceVersion() {
    testSupport.defineResources(createNamespaces(150));

    final V1ListMeta meta1 = getConfigMapListResponseMeta("ns1", new CallBuilder());
    final V1ListMeta meta2 = getConfigMapListResponseMeta("ns1", new CallBuilder());

    assertThat(meta1.getResourceVersion(), equalTo(meta2.getResourceVersion()));
  }

  @Test
  public void whenConfigMapAdded_listResourceVersionChanged() {
    testSupport.defineResources(createNamespaces(150));

    final V1ListMeta meta1 = getConfigMapListResponseMeta("ns1", new CallBuilder());
    testSupport.runSteps(createConfigMapAsyncStep("ns1", "cm1"));
    final V1ListMeta meta2 = getConfigMapListResponseMeta("ns1", new CallBuilder());

    assertThat(meta1.getResourceVersion(), not(equalTo(meta2.getResourceVersion())));
  }

  public Step createConfigMapAsyncStep(String namespace, String name) {
    return new CallBuilder().createConfigMapAsync(namespace,
          new V1ConfigMap().metadata(new V1ObjectMeta().namespace(namespace).name(name)), new TestResponseStep<>());
  }

  @Test
  public void whenConfigMapAddedInOtherNamespace_listResourceVersionNotChanged() {
    testSupport.defineResources(createNamespaces(150));

    final V1ListMeta meta1 = getConfigMapListResponseMeta("ns1", new CallBuilder());
    testSupport.runSteps(createConfigMapAsyncStep("ns2", "cm1"));
    final V1ListMeta meta2 = getConfigMapListResponseMeta("ns1", new CallBuilder());

    assertThat(meta1.getResourceVersion(), equalTo(meta2.getResourceVersion()));
  }

  @Test
  public void whenNumberOfEntriesLessThanLimit_listNamespaceReturnsAllEntries() {
    testSupport.defineResources(createNamespaces(150));

    final CallResponse<V1NamespaceList> callResponse = getNamespaceListResponse(new CallBuilder());

    assertThat(callResponse.getResult().getItems(), hasSize(150));
  }

  @Nonnull
  public V1Namespace[] createNamespaces(int numNamespaces) {
    return IntStream.rangeClosed(1, numNamespaces).boxed().map(this::createNamespace).toArray(V1Namespace[]::new);
  }

  private V1Namespace createNamespace(int i) {
    return new V1Namespace().metadata(createNamespaceMetaData(i)).spec(new V1NamespaceSpec());
  }

  private V1ObjectMeta createNamespaceMetaData(int i) {
    return new V1ObjectMeta().name("ns" + i);
  }

  @Test
  public void whenNumberOfEntriesLessThanLimit_continueTokenIsEmpty() {
    testSupport.defineResources(createNamespaces(150));

    final CallResponse<V1NamespaceList> callResponse = getNamespaceListResponse(new CallBuilder());

    assertThat(getResponseMetadata(callResponse).getContinue(), emptyString());
  }

  @Test
  public void whenNumberOfEntriesGreaterThanLimit_listNamespaceReturnsOnlyTheLimitedNumberOfEntries() {
    testSupport.defineResources(createNamespaces(150));

    final CallResponse<V1NamespaceList> callResponse = getNamespaceListResponse(new CallBuilder().withLimit(100));

    assertThat(callResponse.getResult().getItems(), hasSize(100));
  }

  private CallResponse<V1NamespaceList> getNamespaceListResponse(CallBuilder callBuilder) {
    TestResponseStep<V1NamespaceList> responseStep = new TestResponseStep<>();
    testSupport.runSteps(callBuilder.listNamespaceAsync(responseStep));
    return responseStep.callResponse;
  }

  @Test
  public void whenNumberOfEntriesGreaterThanLimit_responseIncludesContinueToken() {
    testSupport.defineResources(createNamespaces(150));

    final CallResponse<V1NamespaceList> callResponse = getNamespaceListResponse(new CallBuilder().withLimit(100));

    assertThat(getResponseMetadata(callResponse).getContinue(), not(emptyString()));
  }

  public V1ListMeta getResponseMetadata(CallResponse<V1NamespaceList> callResponse) {
    return callResponse.getResult().getMetadata();
  }

  @Test
  public void whenNumberOfEntriesGreaterThanLimit_responseIncludesNumberRemaining() {
    testSupport.defineResources(createNamespaces(150));

    final CallResponse<V1NamespaceList> callResponse = getNamespaceListResponse(new CallBuilder().withLimit(100));

    assertThat(getResponseMetadata(callResponse).getRemainingItemCount(), equalTo(50L));
  }

  @Test
  public void afterPartialListingReturned_newRequestWithContinueTokenGetsNonOverlappingChunk() {
    testSupport.defineResources(createNamespaces(250));

    final CallResponse<V1NamespaceList> callResponse1 = getNamespaceListResponse(new CallBuilder().withLimit(100));
    final String token = getResponseMetadata(callResponse1).getContinue();
    final CallResponse<V1NamespaceList> callResponse2 = getNamespaceListResponse(new CallBuilder().withContinue(token));

    final Set<V1Namespace> items1 = new HashSet<>(callResponse1.getResult().getItems());
    final Set<V1Namespace> items2 = new HashSet<>(callResponse2.getResult().getItems());
    items1.retainAll(items2);
    assertThat(items1, empty());
  }

  @Test
  public void afterPartialListingReturned_newRequestWithContinueTokenGetsRemainingIfUnderLimit() {
    testSupport.defineResources(createNamespaces(150));

    final CallResponse<V1NamespaceList> callResponse1 = getNamespaceListResponse(new CallBuilder().withLimit(100));
    final String token = getResponseMetadata(callResponse1).getContinue();
    final CallResponse<V1NamespaceList> callResponse2 = getNamespaceListResponse(new CallBuilder().withContinue(token));

    assertThat(callResponse2.getResult().getItems(), hasSize(50));
  }

  @Test
  public void afterPartialListingsReturnAllItems_newRequestRestarts() {
    testSupport.defineResources(createNamespaces(150));

    final CallResponse<V1NamespaceList> response1 = getNamespaceListResponse(new CallBuilder().withLimit(100));
    final String token1 = getResponseMetadata(response1).getContinue();
    final CallResponse<V1NamespaceList> response2 = getNamespaceListResponse(new CallBuilder().withContinue(token1));
    final String token2 = getResponseMetadata(response2).getContinue();
    final CallResponse<V1NamespaceList> response3 = getNamespaceListResponse(new CallBuilder().withContinue(token2));

    assertThat(response3.getResult().getItems(), hasSize(150));
  }

  @Test
  public void afterPartialListingReturned_newRequestWithoutContinueTokenGetsSameItems() {
    testSupport.defineResources(createNamespaces(250));

    final CallResponse<V1NamespaceList> callResponse1 = getNamespaceListResponse(new CallBuilder().withLimit(100));
    final CallResponse<V1NamespaceList> callResponse2 = getNamespaceListResponse(new CallBuilder().withLimit(100));

    final Set<V1Namespace> items1 = new HashSet<>(callResponse1.getResult().getItems());
    final Set<V1Namespace> items2 = new HashSet<>(callResponse2.getResult().getItems());
    assertThat(items1, equalTo(items2));
  }

  @Test
  public void afterPartialListingFollowedByItemCreated_requestWithContinueTokenRestartsList() {
    testSupport.defineResources(createConfigMaps("ns1", 50));

    final V1ListMeta meta1 = getConfigMapListResponseMeta("ns1", new CallBuilder().withLimit(10));
    testSupport.runSteps(createConfigMapAsyncStep("ns1", "added"));
    final V1ListMeta meta2 = getConfigMapListResponseMeta("ns1",
                                                    new CallBuilder().withLimit(10).withContinue(meta1.getContinue()));

    assertThat(meta2.getRemainingItemCount(), equalTo(41L));
  }

  @Nonnull
  public V1ConfigMap[] createConfigMaps(String ns, int numConfigMaps) {
    return IntStream.rangeClosed(1, numConfigMaps).boxed().map(i -> createConfigMap(ns, i)).toArray(V1ConfigMap[]::new);
  }

  private V1ConfigMap createConfigMap(String ns, int i) {
    return new V1ConfigMap().metadata(createConfigMapMetaData(ns, i)).data(Collections.emptyMap());
  }

  private V1ObjectMeta createConfigMapMetaData(String namespace, int i) {
    return new V1ObjectMeta().namespace(namespace).name("cm" + i);
  }

  @Test
  public void whenConfigMapNotFound_readStatusIsNotFound() {
    TestResponseStep<V1ConfigMap> endStep = new TestResponseStep<>();
    Packet packet = testSupport.runSteps(new CallBuilder().readConfigMapAsync("", "", "", endStep));

    assertThat(packet.getSpi(CallResponse.class).getStatusCode(), equalTo(CallBuilder.NOT_FOUND));
    assertThat(packet.getSpi(CallResponse.class).getE(), notNullValue());
  }

  @Test
  public void whenDefined_readPodLog() {
    TestResponseStep<String> endStep = new TestResponseStep<>();
    testSupport.definePodLog("name", "namespace", POD_LOG_CONTENTS);

    testSupport.runSteps(new CallBuilder().readPodLogAsync("name", "namespace", "", endStep));

    assertThat(endStep.callResponse.getResult(), equalTo(POD_LOG_CONTENTS));
  }

  static class TestResponseStep<T> extends DefaultResponseStep<T> {

    private CallResponse<T> callResponse;

    TestResponseStep() {
      super(null);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<T> callResponse) {
      this.callResponse = callResponse;
      return super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<T> callResponse) {
      this.callResponse = callResponse;
      return super.onSuccess(packet, callResponse);
    }
  }
}
