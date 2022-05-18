// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import oracle.kubernetes.operator.rest.model.AdmissionRequest;
import oracle.kubernetes.operator.rest.model.AdmissionResponse;
import oracle.kubernetes.operator.rest.model.AdmissionResponseStatus;
import oracle.kubernetes.operator.rest.model.AdmissionReview;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_OK;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_FAILED_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.KubernetesConstants.ADMISSION_REVIEW_API_VERSION;
import static oracle.kubernetes.operator.KubernetesConstants.ADMISSION_REVIEW_KIND;
import static oracle.kubernetes.operator.utils.GsonBuilderUtils.readAdmissionReview;
import static oracle.kubernetes.operator.utils.GsonBuilderUtils.writeAdmissionReview;
import static oracle.kubernetes.weblogic.domain.model.CrdSchemaGeneratorTest.inputStreamFromClasspath;
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

  private final AdmissionReview admissionReview = createAdmissionReview();

  private AdmissionReview createAdmissionReview() {
    return new AdmissionReview().apiVersion(V1).kind("AdmissionReview").request(createAdmissionRequest());
  }

  private AdmissionRequest createAdmissionRequest() {
    AdmissionRequest request = new AdmissionRequest();
    request.setUid(RESPONSE_UID);
    request.setKind(new HashMap<>());
    request.setResource(new HashMap<>());
    request.setSubResource(new HashMap<>());
    request.setObject(new Object());
    request.setOldObject(new Object());
    return request;
  }

  @Test
  void whenConversionWebhookRequestSent_hasExpectedResponse() {
    String conversionReview = getAsString(CONVERSION_REVIEW_REQUEST);
    Response response = sendConversionWebhookRequest(conversionReview);
    String responseString = getAsString((ByteArrayInputStream)response.getEntity());

    assertThat(responseString, equalTo(getAsString(CONVERSION_REVIEW_RESPONSE)));
  }

  @Test
  void whenConversionWebhookHasAnException_responseHasFailedStatusAndFailedEventGenerated() {

    String conversionReview = "some_unexpected_string";
    Response response = sendConversionWebhookRequest(conversionReview);
    String responseString = getAsString((ByteArrayInputStream) response.getEntity());

    assertThat(responseString.contains("\"status\":\"Failed\""), equalTo(Boolean.TRUE));

    MatcherAssert.assertThat("Found 1 CONVERSION_FAILED_EVENT event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            CONVERSION_WEBHOOK_FAILED_EVENT, 1), is(true));
  }

  private Response sendConversionWebhookRequest(String conversionReview) {
    return createRequest(WEBHOOK_HREF)
            .post(createWebhookRequest(conversionReview));
  }

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
    AdmissionResponse expectedResponse = new AdmissionResponse();
    expectedResponse.setUid(RESPONSE_UID);
    expectedResponse.setAllowed(true);
    expectedResponse.setStatus(new AdmissionResponseStatus().code(HTTP_OK));
    AdmissionReview expectedReview = new AdmissionReview();
    expectedReview.setResponse(expectedResponse);
    expectedReview.setApiVersion(ADMISSION_REVIEW_API_VERSION);
    expectedReview.setKind(ADMISSION_REVIEW_KIND);

    AdmissionReview responseReview
        = sendValidatingRequestAsAdmissionReview(readAdmissionReview(getAsString(VALIDATING_REVIEW_REQUEST_1)));

    assertThat(responseReview.toString(), equalTo(expectedReview.toString()));
  }

  @Test
  void whenInvalidValidatingWebhookRequestSentUsingJavaResponse_hasExpectedResponse() {
    String resultMessage = "Exception: com.google.gson.JsonSyntaxException";

    AdmissionReview responseReview
        = readAdmissionReview(sendValidatingRequestAsString(getAsString(VALIDATING_REVIEW_REQUEST_2)));

    assertThat(getAllowed(responseReview), equalTo(false));
    assertThat(getResultMessage(responseReview), containsString(resultMessage));
  }

  @Test
  void whenGoodValidatingWebhookRequestSentUsingJavaRequest_hasExpectedResponse() {
    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(admissionReview);

    assertThat(getAllowed(responseReview), equalTo(true));
    assertThat(getResultCode(responseReview), equalTo(HTTP_OK));
    assertThat(getUid(responseReview), equalTo(RESPONSE_UID));
  }

  @Test
  void whenGoodValidatingWebhookRequestSentUsingJavaWithoutRequest_hasExpectedResponse() {
    AdmissionResponseStatus expectedStatus = new AdmissionResponseStatus();
    expectedStatus.setCode(HTTP_OK);
    expectedStatus.setMessage(null);
    admissionReview.request(null);

    AdmissionReview responseReview = sendValidatingRequestAsAdmissionReview(admissionReview);

    assertThat(getResultStatus(responseReview).equals(expectedStatus), equalTo(true));
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

  private String getUid(AdmissionReview admissionReview) {
    return Optional.ofNullable(admissionReview)
        .map(AdmissionReview::getResponse).map(AdmissionResponse::getUid).orElse("");
  }

  private boolean getAllowed(AdmissionReview admissionResponse) {
    return Optional.ofNullable(admissionResponse)
        .map(AdmissionReview::getResponse).map(AdmissionResponse::getAllowed).orElse(false);
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

}
