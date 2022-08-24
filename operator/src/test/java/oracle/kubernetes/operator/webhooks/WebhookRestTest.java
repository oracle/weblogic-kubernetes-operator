// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.http.rest.RestConfig;
import oracle.kubernetes.operator.http.rest.RestTestBase;
import oracle.kubernetes.operator.http.rest.backend.RestBackend;
import oracle.kubernetes.operator.webhooks.model.AdmissionRequest;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponseStatus;
import oracle.kubernetes.operator.webhooks.model.AdmissionReview;
import oracle.kubernetes.operator.webhooks.model.ConversionRequest;
import oracle.kubernetes.operator.webhooks.model.ConversionResponse;
import oracle.kubernetes.operator.webhooks.model.ConversionReviewModel;
import oracle.kubernetes.operator.webhooks.model.Result;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.lang.System.lineSeparator;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_FAILED_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.KubernetesConstants.ADMISSION_REVIEW_API_VERSION;
import static oracle.kubernetes.operator.KubernetesConstants.ADMISSION_REVIEW_KIND;
import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_PLURAL;
import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_VERSION;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_GROUP;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_PLURAL;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_VERSION;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_1;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_2;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.GOOD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_IMAGE_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_INTROSPECT_VERSION;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_LOG_HOME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.ORIGINAL_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createAuxiliaryImage;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createCluster;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createDomain;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.setAuxiliaryImages;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.setFromModel;
import static oracle.kubernetes.operator.webhooks.WebhookRestTest.RestConfigStub.create;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readAdmissionReview;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readConversionReview;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.writeAdmissionReview;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.writeClusterToMap;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.writeConversionReview;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.writeDomainToMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
class WebhookRestTest extends RestTestBase {
  private static final String CONVERSION_REVIEW_RESPONSE = "conversion-review-response.yaml";
  private static final String CONVERSION_REVIEW_REQUEST = "conversion-review-request.yaml";
  private static final String VALIDATING_REVIEW_RESPONSE_ACCEPT = "domain-validating-webhook-response-accept.yaml";
  private static final String VALIDATING_REVIEW_REQUEST_1 = "domain-validating-webhook-request-1.yaml";
  private static final String VALIDATING_REVIEW_REQUEST_2 = "domain-validating-webhook-request-2.yaml";
  private static final String V1 = "v1";
  private static final String WEBHOOK_HREF = "/webhook";
  private static final String VALIDATING_WEBHOOK_HREF = "/admission";
  private static final String RESPONSE_UID = "705ab4f5-6393-11e8-b7cc-42010a800002";
  private static final String REJECT_MESSAGE_PATTERN = "Change request to domain resource '%s' cannot be honored"
          + " because the replica count for cluster '%s' would exceed the cluster size '%s'";
  private static final String REJECT_MESSAGE_CLUSTER_PATTERN = "Change request to cluster resource '%s' cannot be "
      + "honored because the replica count would exceed the cluster size '%s'";


  private final AdmissionReview domainReview = createDomainAdmissionReview();
  private final AdmissionReview clusterReview = createClusterAdmissionReview();
  private final DomainResource existingDomain = createDomain();
  private final DomainResource proposedDomain = createDomain();
  private final ClusterResource existingCluster = createCluster();
  private final ClusterResource proposedCluster = createCluster();

  private final ConversionReviewModel conversionReview = createConversionReview();

  private AdmissionReview createDomainAdmissionReview() {
    return new AdmissionReview().apiVersion(V1).kind("AdmissionReview").request(createDomainAdmissionRequest());
  }

  private AdmissionRequest createDomainAdmissionRequest() {
    AdmissionRequest request = new AdmissionRequest();
    request.setUid(RESPONSE_UID);
    request.setKind(new HashMap<>());
    request.setResource(createDomainResource());
    request.setSubResource(new HashMap<>());
    return request;
  }

  private Map<String, String> createDomainResource() {
    Map<String, String> resource = new HashMap<>();
    resource.put("group", DOMAIN_GROUP);
    resource.put("version", DOMAIN_VERSION);
    resource.put("resource", DOMAIN_PLURAL);
    return resource;
  }

  private AdmissionReview createClusterAdmissionReview() {
    return new AdmissionReview().apiVersion(V1).kind("AdmissionReview").request(createClusterAdmissionRequest());
  }

  private AdmissionRequest createClusterAdmissionRequest() {
    AdmissionRequest request = new AdmissionRequest();
    request.setUid(RESPONSE_UID);
    request.setKind(new HashMap<>());
    request.setResource(createClusterResource());
    request.setSubResource(new HashMap<>());
    return request;
  }

  private Map<String, String> createClusterResource() {
    Map<String, String> resource = new HashMap<>();
    resource.put("group", DOMAIN_GROUP);
    resource.put("version", CLUSTER_VERSION);
    resource.put("resource", CLUSTER_PLURAL);
    return resource;
  }

  private ConversionReviewModel createConversionReview() {
    ConversionReviewModel review = new ConversionReviewModel().apiVersion(V1).kind("ConversionReview");
    review.setRequest(createConversionRequest());
    return review;
  }

  private ConversionRequest createConversionRequest() {
    ConversionRequest request = new ConversionRequest();
    request.setUid(RESPONSE_UID);
    return request;
  }

  final RestBackendStub restBackend = createStrictStub(RestBackendStub.class);

  @Override
  protected Application configure() {
    return new WebhookRestServer(create(this::getRestBackend)).createResourceConfig();
  }

  // Note: the #configure method is called during class initialization, before the restBackend field
  // is initialized. We therefore populate the ResourceConfig with this supplier method, so that
  // it will return the initialized and configured field.
  private RestBackend getRestBackend() {
    return restBackend;
  }

  // test cases for conversion webhook

  @Test
  void whenConversionWebhookRequestSent_hasExpectedResponse() {
    String conversionReview = getAsString(CONVERSION_REVIEW_REQUEST);
    String responseString = sendConversionWebhookRequestAsString(conversionReview);

    assertThat(responseString, equalTo(getAsString(CONVERSION_REVIEW_RESPONSE)));
  }

  @Test
  void whenConversionWebhookRequestSent_hasExpectedResponseResult() {
    Result result = new Result().message("").status("Success");
    String conversionReview = getAsString(CONVERSION_REVIEW_REQUEST);

    String responseString = sendConversionWebhookRequestAsString(conversionReview);
    ConversionReviewModel responseReview = readConversionReview(responseString);
    Result responseResult = getResult(responseReview);

    assertThat(responseResult.equals(result), is(true));
    assertThat(responseResult.toString(), equalTo(result.toString()));
    assertThat(responseResult.hashCode(), equalTo(result.hashCode()));
    assertThat(responseResult.getStatus(), equalTo("Success"));
  }

  @Test
  void whenConversionWebhookHasAnException_responseHasFailedStatusAndFailedEventGenerated() {
    String conversionReview = "some_unexpected_string";
    String responseString = sendConversionWebhookRequestAsString(conversionReview);

    assertThat(responseString.contains("\"status\":\"Failed\""), equalTo(Boolean.TRUE));

    MatcherAssert.assertThat("Found 1 CONVERSION_WEBHOOK_FAILED_EVENT event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            CONVERSION_WEBHOOK_FAILED_EVENT, 1), is(true));
  }

  @Test
  void whenConversionWebhookHasAnException_responseHasMessageWithException() {
    String conversionReview = "some_unexpected_string";
    String responseString = sendConversionWebhookRequestAsString(conversionReview);

    ConversionReviewModel responseReview = readConversionReview(responseString);
    Result responseResult = getResult(responseReview);
    assertThat(responseResult.getMessage(), containsString("Exception"));
  }

  private String sendConversionWebhookRequestAsString(String conversionReview) {
    Response response = sendConversionWebhookRequest(conversionReview);
    return getAsString((ByteArrayInputStream) response.getEntity());
  }

  private Response sendConversionWebhookRequest(String conversionReview) {
    return createRequest(WEBHOOK_HREF)
            .post(createWebhookRequest(conversionReview));
  }

  private ConversionReviewModel sendConversionWebhookRequestAsReview(ConversionReviewModel conversionReview) {
    return readConversionReview(sendConversionWebhookRequestAsString(writeConversionReview(conversionReview)));
  }

  @Test
  void whenConversionWebhookRequestSent_hasExpectedResponseInJava() {
    ConversionReviewModel expectedReview = readConversionReview(getAsString(CONVERSION_REVIEW_RESPONSE));
    ConversionReviewModel conversionReview = readConversionReview(getAsString(CONVERSION_REVIEW_REQUEST));
    ConversionReviewModel responseReview  = sendConversionWebhookRequestAsReview(conversionReview);

    assertThat(responseReview.getResponse().equals(expectedReview.getResponse()), is(true));
    assertThat(responseReview.getResponse().toString(), equalTo(expectedReview.getResponse().toString()));
    assertThat(responseReview.getResponse().hashCode(), equalTo(expectedReview.getResponse().hashCode()));
    assertThat(responseReview.toString(), equalTo(expectedReview.toString()));
    assertThat(responseReview.hashCode(), equalTo(expectedReview.hashCode()));
    assertThat(responseReview.equals(expectedReview), is(true));
  }

  @Test
  void whenGoodConversionWebhookRequestSentUsingJavaRequest_hasExpectedResponse() {
    ConversionReviewModel responseReview = sendConversionWebhookRequestAsReview(conversionReview);

    assertThat(getStatus(responseReview), equalTo("Success"));
    assertThat(getConversionUid(responseReview), equalTo(RESPONSE_UID));
    assertThat(getConvertedObject(responseReview).size(),is(0));
  }

  private String getConversionUid(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview)
        .map(ConversionReviewModel::getResponse).map(ConversionResponse::getUid).orElse("");
  }

  private String getStatus(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview)
        .map(ConversionReviewModel::getResponse).map(ConversionResponse::getResult).map(Result::getStatus).orElse("");
  }

  private Result getResult(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview)
        .map(ConversionReviewModel::getResponse).map(ConversionResponse::getResult).orElse(null);
  }

  private List<Object> getConvertedObject(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview)
        .map(ConversionReviewModel::getResponse)
        .map(ConversionResponse::getConvertedObjects).orElse(Collections.emptyList());
  }

  // test cases for validating webhook domainResource

  @Test
  void whenGoodValidatingWebhookRequestSent_hasExpectedResponse() {
    String responseString = sendValidatingRequestAsString(getAsString(VALIDATING_REVIEW_REQUEST_1));

    assertThat(responseString, equalTo(getAsString(VALIDATING_REVIEW_RESPONSE_ACCEPT)));
  }

  @Test
  void whenInvalidValidatingWebhookRequestSent_hasExpectedResponse() {
    String resultAllowed = "\"allowed\":false";
    String resultMessage = "\"message\":\"Exception: com.google.gson.JsonSyntaxException";

    String responseString = sendValidatingRequestAsString(getAsString(VALIDATING_REVIEW_REQUEST_2));

    assertThat(responseString, containsString(resultAllowed));
    assertThat(responseString, containsString(resultMessage));
  }

  @Test
  void whenGoodValidatingWebhookRequestSentUsingJavaResponse_hasExpectedResponse() {
    AdmissionResponse expectedResponse = new AdmissionResponse().uid(RESPONSE_UID).allowed(true);
    AdmissionReview expectedReview = new AdmissionReview()
        .response(expectedResponse).apiVersion(ADMISSION_REVIEW_API_VERSION).kind(ADMISSION_REVIEW_KIND);

    AdmissionReview responseReview
        = sendValidatingRequestAsAdmissionReview(readAdmissionReview(getAsString(VALIDATING_REVIEW_REQUEST_1)));

    assertThat(responseReview.toString(), equalTo(expectedReview.toString()));
  }

  @Test
  void whenInvalidValidatingWebhookRequestSentUsingJavaResponse_hasExpectedResponse() {
    String resultMessage = "Exception: com.google.gson.JsonSyntaxException";

    AdmissionReview responseReview
        = readAdmissionReview(sendValidatingRequestAsString(getAsString(VALIDATING_REVIEW_REQUEST_2)));

    assertThat(isAllowed(responseReview), equalTo(false));
    assertThat(getResultMessage(responseReview), containsString(resultMessage));
  }

  @Test
  void whenGoodValidatingWebhookRequestSentUsingJavaRequest_hasExpectedResponse() {
    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(isAllowed(responseReview), equalTo(true));
    assertThat(getAdmissionUid(responseReview), equalTo(RESPONSE_UID));
  }

  @Test
  void whenGoodValidatingWebhookRequestSentUsingJavaWithoutRequest_hasExpectedResponse() {
    domainReview.request(null);

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(isAllowed(responseReview), equalTo(true));
    assertThat(getResultStatus(responseReview), equalTo(null));
  }

  @Test
  void whenDomainReplicasChangedAloneValid_acceptIt() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneAndInvalid_rejectIt() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(isAllowed(responseReview), equalTo(false));
  }

  @Test
  void whenLogHomeChangedAndFirstClusterReplicasChangedInvalid_rejectItWithExpectedMessage() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(false));
    assertThat(getResponseStatusMessage(responseReview), equalTo(getRejectMessage(0)));
  }

  private String getRejectMessage(int i) {
    return String.format(REJECT_MESSAGE_PATTERN,
        proposedDomain.getDomainUid(),
        proposedDomain.getSpec().getClusters().get(i).getClusterName(),
        ORIGINAL_REPLICAS);
  }

  @Test
  void whenLogHomeChangedAndSecondClusterReplicasChangedInvalid_rejectItWithExpectedMessage() {
    proposedDomain.getSpec().getClusters().get(1).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(false));
    assertThat(getResponseStatusMessage(responseReview), equalTo(getRejectMessage(1)));
  }

  @Test
  void whenIntrospectVersionChangedAndFirstClusterReplicasChangedInvalid_acceptIt() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
    assertThat(getWarnings(responseReview).contains("exceed the cluster size"), equalTo(true));
    assertThat(getWarnings(responseReview).contains(
        "allowed because 'spec.introspectVersion' also changed"), equalTo(true));
  }

  @Test
  void whenIntrospectVersionChangedAndSecondClusterReplicasChangedInvalid_acceptIt() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().getClusters().get(1).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
    assertThat(getWarnings(responseReview).contains("exceed the cluster size"), equalTo(true));
    assertThat(getWarnings(responseReview).contains(
        "allowed because 'spec.introspectVersion' also changed"), equalTo(true));
  }

  @Test
  void whenImageChangedAndOneClusterReplicasChangedInvalid_acceptItWithExpectedMessage() {
    proposedDomain.getSpec().setImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
    assertThat(getWarnings(responseReview).contains("exceed the cluster size"), equalTo(true));
    assertThat(getWarnings(responseReview).contains(
        "allowed because 'spec.image' also changed"), equalTo(true));
  }

  @Test
  void whenMIIAuxiliaryImageChangedAndOneClusterReplicasChangedInvalid_acceptItWithExpectedMessage() {
    setFromModel(existingDomain);
    setFromModel(proposedDomain);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);
    setExistingAndProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
    assertThat(getWarnings(responseReview).contains("exceed the cluster size"), equalTo(true));
    assertThat(getWarnings(responseReview).contains(
        "allowed because 'spec.configuration.model.auxiliaryImages' also changed"), equalTo(true));
  }


  @Test
  void whenProposedObjectMissing_acceptIt() {
    setExistingDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenExistingDomainMissing_acceptIt() {
    setProposedDomain();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenBothExistingAndProposedDomainMissing_acceptIt() {
    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenExistingAndProposedDomainSameAndOneClusterReplicasChangedInvalid_acceptIt() {
    existingDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    setExistingDomain(existingDomain);
    setProposedDomain(existingDomain);
    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenExistingAndProposedDomainEqualsAndOneClusterReplicasChangedInvalid_acceptIt() {
    existingDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    setExistingAndProposedDomain();
    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(domainReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  private void setExistingAndProposedDomain() {
    domainReview.getRequest().oldObject(writeDomainToMap(existingDomain)).object(writeDomainToMap(proposedDomain));
  }

  private void setProposedDomain() {
    setProposedDomain(proposedDomain);
  }

  private void setProposedDomain(DomainResource domain) {
    domainReview.getRequest().setObject(writeDomainToMap(domain));
  }

  private void setExistingDomain() {
    setExistingDomain(existingDomain);
  }

  private void setExistingDomain(DomainResource domain) {
    domainReview.getRequest().setOldObject(writeDomainToMap(domain));
  }

  private void setExistingAndProposedCluster() {
    clusterReview.getRequest().oldObject(writeClusterToMap(existingCluster)).object(writeClusterToMap(proposedCluster));
  }

  private void setExistingCluster() {
    setExistingCluster(existingCluster);
  }

  private void setExistingCluster(ClusterResource cluster) {
    clusterReview.getRequest().setOldObject(writeClusterToMap(cluster));
  }

  private void setProposedCluster() {
    setProposedCluster(proposedCluster);
  }

  private void setProposedCluster(ClusterResource cluster) {
    clusterReview.getRequest().setObject(writeClusterToMap(cluster));
  }

  // test cases for validating webhook ClusterResource

  @Test
  void whenClusterReplicasUnchanged_acceptIt() {
    existingCluster.getSpec().withReplicas(GOOD_REPLICAS);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);
    setExistingAndProposedCluster();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedAloneValid_acceptIt() {
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);
    setExistingAndProposedCluster();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedAloneAndInvalid_rejectIt() {
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);
    setExistingAndProposedCluster();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(isAllowed(responseReview), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedAloneAndInvalid_rejectItWithExpectedMessage() {
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);
    setExistingAndProposedCluster();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(responseReview.getResponse().getStatus().getMessage(), equalTo(getErrorMessage()));
  }

  @Test
  void whenProposedClusterMissing_acceptIt() {
    setExistingCluster();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenExistingClusterMissing_acceptIt() {
    setProposedCluster();

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenBothExistingAndProposedClusterMissing_acceptIt() {
    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenBothExistingAndProposedClusterSpecSame_acceptIt() {
    setProposedCluster(existingCluster);

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedValidAndReadDomainFailed404_acceptIt() {
    testSupport.defineResources(proposedDomain);
    existingCluster.getSpec().withReplicas(GOOD_REPLICAS + 1);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);
    setExistingAndProposedCluster();

    testSupport.failOnRead(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_FORBIDDEN);

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(isAllowed(responseReview), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedUnsetAndReadDomainFailed404_rejectItWithException() {
    testSupport.defineResources(proposedDomain);
    existingCluster.getSpec().withReplicas(GOOD_REPLICAS);
    proposedCluster.getSpec().withReplicas(null);
    setExistingAndProposedCluster();

    testSupport.failOnRead(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_FORBIDDEN);

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(clusterReview);

    assertThat(isAllowed(responseReview), equalTo(false));
    assertThat(getResponseStatusMessage(responseReview).contains("failure reported in test"), equalTo(true));
  }

  private String getErrorMessage() {
    return String.format(REJECT_MESSAGE_CLUSTER_PATTERN, proposedCluster.getClusterName(), ORIGINAL_REPLICAS);
  }

  private AdmissionReview sendValidatingRequestAsAdmissionReview(AdmissionReview admissionReview) {
    return readAdmissionReview(sendValidatingRequestAsString(writeAdmissionReview(admissionReview)));
  }

  private String sendValidatingRequestAsString(String admissionReviewString) {
    Response response = sendValidatingWebhookRequest(admissionReviewString);
    return getAsString((ByteArrayInputStream)response.getEntity());
  }

  private AdmissionResponseStatus getResultStatus(AdmissionReview responseReview) {
    return Optional.ofNullable(responseReview)
        .map(AdmissionReview::getResponse).map(AdmissionResponse::getStatus).orElse(null);
  }

  private String getResultMessage(AdmissionReview responseReview) {
    return Optional.ofNullable(responseReview)
        .map(AdmissionReview::getResponse)
        .map(AdmissionResponse::getStatus)
        .map(AdmissionResponseStatus::getMessage)
        .orElse("");
  }

  private int getResultCode(AdmissionReview responseReview) {
    return Optional.ofNullable(responseReview)
        .map(AdmissionReview::getResponse)
        .map(AdmissionResponse::getStatus)
        .map(AdmissionResponseStatus::getCode)
        .orElse(HTTP_OK);
  }

  private String getAdmissionUid(AdmissionReview admissionReview) {
    return Optional.ofNullable(admissionReview)
        .map(AdmissionReview::getResponse).map(AdmissionResponse::getUid).orElse("");
  }

  private boolean isAllowed(AdmissionReview admissionResponse) {
    return Optional.ofNullable(admissionResponse)
        .map(AdmissionReview::getResponse).map(AdmissionResponse::isAllowed).orElse(false);
  }

  private String getWarnings(AdmissionReview admissionResponse) {
    return Optional.ofNullable(admissionResponse)
        .map(AdmissionReview::getResponse).map(AdmissionResponse::getWarnings).map(this::perLine).orElse("");
  }

  private String perLine(List<String> messages) {
    return String.join(lineSeparator(), messages);
  }

  private String getResponseStatusMessage(AdmissionReview admissionResponse) {
    return Optional.ofNullable(admissionResponse).map(AdmissionReview::getResponse).map(AdmissionResponse::getStatus)
        .map(AdmissionResponseStatus::getMessage).orElse("");
  }

  private Response sendValidatingWebhookRequest(String admissionReview) {
    return createRequest(VALIDATING_WEBHOOK_HREF)
        .post(createWebhookRequest(admissionReview));
  }

  private String getAsString(String fileName) {
    return getAsString(inputStreamFromClasspath(fileName));
  }

  private String getAsString(InputStream inputStream) {
    return new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.joining("\n"));
  }

  private Entity<String> createWebhookRequest(String jsonStr) {
    return Entity.entity(jsonStr, MediaType.APPLICATION_JSON);
  }

  abstract static class RestBackendStub implements RestBackend {
  }

  abstract static class RestConfigStub implements RestConfig {
    private final Supplier<RestBackend> restBackendSupplier;

    RestConfigStub(Supplier<RestBackend> restBackendSupplier) {
      this.restBackendSupplier = restBackendSupplier;
    }

    static RestConfig create(Supplier<RestBackend> restBackendSupplier) {
      return createStrictStub(RestConfigStub.class, restBackendSupplier);
    }

    @Override
    public RestBackend getBackend(String accessToken) {
      return restBackendSupplier.get();
    }

    @Override
    public String getHost() {
      return "localhost";
    }

    @Override
    public int getWebhookHttpsPort() {
      return 8084;
    }
  }

  public static InputStream inputStreamFromClasspath(String path) {
    return WebhookRestTest.class.getResourceAsStream(path);
  }
}
