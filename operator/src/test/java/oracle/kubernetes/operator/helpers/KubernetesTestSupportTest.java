// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonPatchBuilder;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1EventList;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class KubernetesTestSupportTest {

  private static final String NS = "namespace1";
  private static final String POD_LOG_CONTENTS = "asdfghjkl";
  List<Memento> mementos = new ArrayList<>();
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();

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
    crd.getMetadata().putLabelsItem("be", "different");

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
    testSupport.runSteps(new CallBuilder().deletePodAsync("mycrd", "ns2", null, responseStep));

    assertThat(testSupport.getResources(POD), containsInAnyOrder(pod1, pod3));
  }

  @Test
  public void whenHttpErrorAssociatedWithResource_callResponseIsError() {
    testSupport.failOnResource(POD, "pod1", "ns2", HTTP_BAD_REQUEST);

    TestResponseStep<V1Status> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().deletePodAsync("pod1", "ns2", null, responseStep));

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
    assertThat(responseStep.callResponse.getStatusCode(), equalTo(HTTP_BAD_REQUEST));
  }

  @Test
  public void whenHttpErrorNotAssociatedWithResource_ignoreIt() {
    testSupport.failOnResource(POD, "pod1", "ns2", HTTP_BAD_REQUEST);

    TestResponseStep<V1Status> responseStep = new TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().deletePodAsync("pod2", "ns2", null, responseStep));
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
          new CallBuilder().withLabelSelectors("k1=v1").listPodAsync("ns1", responseStep));

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
          new CallBuilder().patchPodAsync("pod1", "ns1",
                new V1Patch(patchBuilder.build().toString()), responseStep));

    V1Pod pod2 = (V1Pod) testSupport.getResources(POD).stream().findFirst().orElse(pod1);
    assertThat(pod2.getMetadata().getLabels(), hasEntry("k1", "v2"));
  }

  @Test
  public void afterPatchPodWithExistingLabel_labelValueChanged() {
    V1Pod pod1 = createLabeledPod("pod1", "ns1", ImmutableMap.of("k1", "v1"));
    testSupport.defineResources(pod1);

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder().replace("/metadata/labels/k1", "v2");

    TestResponseStep<V1Pod> responseStep = new TestResponseStep<>();
    testSupport.runSteps(
          new CallBuilder().patchPodAsync("pod1", "ns1",
                new V1Patch(patchBuilder.build().toString()), responseStep));

    V1Pod pod2 = (V1Pod) testSupport.getResources(POD).stream().findFirst().orElse(pod1);
    assertThat(pod2.getMetadata().getLabels(), hasEntry("k1", "v2"));
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
  public void whenConfigMapNotFound_readStatusIsNotFound() {
    TestResponseStep<V1ConfigMap> endStep = new TestResponseStep<>();
    Packet packet = testSupport.runSteps(new CallBuilder().readConfigMapAsync("", "", endStep));

    assertThat(packet.getSpi(CallResponse.class).getStatusCode(), equalTo(CallBuilder.NOT_FOUND));
    assertThat(packet.getSpi(CallResponse.class).getE(), notNullValue());
  }

  @Test
  public void whenDefined_readPodLog() {
    TestResponseStep<String> endStep = new TestResponseStep<>();
    testSupport.definePodLog("name", "namespace", POD_LOG_CONTENTS);

    testSupport.runSteps(new CallBuilder().readPodLogAsync("name", "namespace", endStep));

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
