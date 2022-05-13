// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.meterware.simplestub.Memento;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import oracle.kubernetes.common.utils.BaseTestUtils;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.rest.model.AdmissionRequest;
import oracle.kubernetes.operator.rest.model.AdmissionResponse;
import oracle.kubernetes.operator.rest.model.AdmissionResponseStatus;
import oracle.kubernetes.operator.rest.model.AdmissionReview;
import oracle.kubernetes.operator.rest.model.ScaleClusterParamsModel;
import oracle.kubernetes.utils.TestUtils;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_FAILED_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.rest.AuthenticationFilter.ACCESS_TOKEN_PREFIX;
import static oracle.kubernetes.operator.rest.RestTest.JsonArrayMatcher.withValues;
import static oracle.kubernetes.operator.utils.GsonBuilderUtils.readAdmissionReview;
import static oracle.kubernetes.operator.utils.GsonBuilderUtils.writeAdmissionReview;
import static oracle.kubernetes.weblogic.domain.model.CrdSchemaGeneratorTest.inputStreamFromClasspath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
class RestTest extends JerseyTest {

  private static final String CONVERSION_REVIEW_RESPONSE = "conversion-review-response.yaml";
  private static final String CONVERSION_REVIEW_REQUEST = "conversion-review-request.yaml";
  private static final String VALIDATING_REVIEW_RESPONSE_ACCEPT = "domain-validating-webhook-response-accept.yaml";
  private static final String VALIDATING_REVIEW_REQUEST_1 = "domain-validating-webhook-request-1.yaml";
  private static final String VALIDATING_REVIEW_REQUEST_2 = "domain-validating-webhook-request-2.yaml";
  private static final String V1 = "v1";
  private static final String OPERATOR_HREF = "/operator";
  private static final String WEBHOOK_HREF = "/webhook";
  private static final String VALIDATING_WEBHOOK_HREF = "/admission";
  private static final String V1_HREF = OPERATOR_HREF + "/" + V1;
  private static final String LATEST_HREF = OPERATOR_HREF + "/latest";

  private static final String V1_SWAGGER_HREF = V1_HREF + "/swagger";
  private static final String V1_DOMAINS_HREF = V1_HREF + "/domains";
  private static final String SWAGGER_HREF = LATEST_HREF + "/swagger";
  private static final String DOMAINS_HREF = LATEST_HREF + "/domains";
  private static final String DOMAIN1_HREF = DOMAINS_HREF + "/uid1";
  private static final String DOMAIN2_HREF = DOMAINS_HREF + "/uid2";
  private static final String DOMAIN1_CLUSTERS_HREF = DOMAIN1_HREF + "/clusters";
  private static final String ACCESS_TOKEN = "dummy token";
  private static final String RESPONSE_UID = "705ab4f5-6393-11e8-b7cc-42010a800002";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final RestBackendStub restBackend = createStrictStub(RestBackendStub.class);
  private boolean includeRequestedByHeader = true;
  private String authorizationHeader = ACCESS_TOKEN_PREFIX + " " + ACCESS_TOKEN;
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

  @BeforeEach
  public void setupRestTest() throws Exception {
    setUp();
    mementos.add(testSupport.install());
    mementos.add(BaseTestUtils.silenceJsonPathLogger());
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @AfterEach
  public void restore() throws Exception {
    tearDown();
    mementos.forEach(Memento::revert);
  }

  @Override
  protected Application configure() {
    return new OperatorRestServer(RestConfigStub.create(this::getRestBackend)).createResourceConfig();
  }

  // Note: the #configure method is called during class initialization, before the restBackend field
  // is initialized. We therefore populate the ResourceConfig with this supplier method, so that
  // it will return the initialized and configured field.
  private RestBackend getRestBackend() {
    return restBackend;
  }

  @Override
  protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
    return new InMemoryTestContainerFactory();
  }

  @Test
  void whenNoAuthenticationHeader_rejectRequest() {
    excludeAuthorizationHeader();

    assertThat(createRequest(OPERATOR_HREF).get().getStatus(), equalTo(HTTP_UNAUTHORIZED));
  }

  @Test
  void whenAuthenticationHeaderLacksBearerPrefix_rejectRequest() {
    removeBearerPrefix();

    assertThat(createRequest(OPERATOR_HREF).get().getStatus(), equalTo(HTTP_UNAUTHORIZED));
  }

  @Test
  void whenAuthenticationHeaderLacksAccessToken_rejectRequest() {
    removeAccessToken();

    assertThat(createRequest(OPERATOR_HREF).get().getStatus(), equalTo(HTTP_UNAUTHORIZED));
  }

  @Test
  void operatorEndPoint_returnsVersion() {
    Map result = getJsonResponse(OPERATOR_HREF);

    assertThat(result, hasJsonPath("$.items[0].version", equalTo("v1")));
    assertThat(result, hasJsonPath("$.items[0].latest", equalTo(true)));
    assertThat(result, hasJsonPath("$.items[0].lifecycle", equalTo("active")));
    assertThat(result, hasJsonPath("$.items[0].links[?(@.rel=='self')].href", withValues(V1_HREF)));
  }

  private Map getJsonResponse(String href) {
    return new Gson().fromJson(createRequest(href).get(String.class), Map.class);
  }

  private Invocation.Builder createRequest(String href) {
    Invocation.Builder request = target(href).request();
    if (authorizationHeader != null) {
      request.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
    if (includeRequestedByHeader) {
      request.header("X-Requested-By", "TestClient");
    }
    return request;
  }

  @Test
  void v1EndPoint_returnsVersionAndLinks() {
    Map result = getJsonResponse(V1_HREF);

    assertThat(result, hasJsonPath("$.version", equalTo("v1")));
    assertThat(result, hasJsonPath("$.latest", equalTo(true)));
    assertThat(result, hasJsonPath("$.lifecycle", equalTo("active")));
    assertThat(
        result, hasJsonPath("$.links[*].href", withValues(V1_DOMAINS_HREF, V1_SWAGGER_HREF)));
  }

  @Test
  void latestVersionEndPoint_returnsVersionAndLinks() {
    Map result = getJsonResponse(LATEST_HREF);

    assertThat(result, hasJsonPath("$.version", equalTo("v1")));
    assertThat(result, hasJsonPath("$.latest", equalTo(true)));
    assertThat(result, hasJsonPath("$.lifecycle", equalTo("active")));
    assertThat(result, hasJsonPath("$.links[*].href", withValues(DOMAINS_HREF, SWAGGER_HREF)));
  }

  @Test
  void nonexistingVersionEndPoint_fails() {
    assertThat(getResponseStatus(OPERATOR_HREF + "/v99"), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  void swaggerEndPoint_returnsSwaggerFile() {
    Map result = getJsonResponse(SWAGGER_HREF);

    assertThat(result, hasJsonPath("$.swagger", equalTo("2.0")));
    assertThat(result, hasJsonPath("$.info.version", equalTo("v1")));
    assertThat(result, hasJsonPath("$.paths./operator.get.tags", withValues("Version")));
  }

  @Test
  void domainsEndPoint_returnsListOfDomainsAndLinks() {
    defineDomains("uid1", "uid2");

    Map result = getJsonResponse(DOMAINS_HREF);

    assertThat(result, hasJsonPath("$.links[?(@.rel=='self')].href", withValues(DOMAINS_HREF)));
    assertThat(result, hasJsonPath("$.links[?(@.rel=='parent')].href", withValues(LATEST_HREF)));
    assertThat(result, hasJsonPath("$.items[*].domainUID", withValues("uid1", "uid2")));
    assertThat(
        result,
        hasJsonPath("$.items[?(@.domainUID=='uid1')].links[*].href", withValues(DOMAIN1_HREF)));
    assertThat(
        result,
        hasJsonPath("$.items[?(@.domainUID=='uid2')].links[*].href", withValues(DOMAIN2_HREF)));
  }

  @Test
  void existingDomainEndPoint_returnsDomainsUidAndClusterLink() {
    defineDomains("uid1", "uid2");

    Map result = getJsonResponse(DOMAINS_HREF + "/uid1");

    assertThat(result, hasJsonPath("$.domainUID", equalTo("uid1")));
    assertThat(
        result, hasJsonPath("$.links[?(@.rel=='self')].href", withValues(DOMAINS_HREF + "/uid1")));
    assertThat(result, hasJsonPath("$.links[?(@.rel=='parent')].href", withValues(DOMAINS_HREF)));
    assertThat(
        result,
        hasJsonPath("$.links[?(@.rel=='clusters')].href", withValues(DOMAIN1_CLUSTERS_HREF)));
  }

  @Test
  void nonexistingDomainEndPoint_fails() {
    defineDomains("uid1", "uid2");

    assertThat(getResponseStatus(DOMAINS_HREF + "/uid3"), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  void clustersEndPoint_returnsListOfClustersAndLinks() {
    defineClusters("uid1", "cluster1", "cluster2");

    Map result = getJsonResponse(DOMAIN1_CLUSTERS_HREF);

    assertThat(
        result, hasJsonPath("$.links[?(@.rel=='self')].href", withValues(DOMAIN1_CLUSTERS_HREF)));
    assertThat(result, hasJsonPath("$.links[?(@.rel=='parent')].href", withValues(DOMAIN1_HREF)));
    assertThat(result, hasJsonPath("$.items[*].cluster", withValues("cluster1", "cluster2")));
    assertThat(
        result,
        hasJsonPath(
            "$.items[?(@.cluster=='cluster1')].links[*].href",
            withValues(DOMAIN1_CLUSTERS_HREF + "/cluster1")));
    assertThat(
        result,
        hasJsonPath(
            "$.items[?(@.cluster=='cluster2')].links[*].href",
            withValues(DOMAIN1_CLUSTERS_HREF + "/cluster2")));
  }

  @Test
  void existingClusterEndPoint_returnsClusterNameAndScalingLink() {
    defineClusters("uid1", "cluster1", "cluster2");

    Map result = getJsonResponse(DOMAIN1_CLUSTERS_HREF + "/cluster1");

    assertThat(result, hasJsonPath("$.cluster", equalTo("cluster1")));
    assertThat(
        result,
        hasJsonPath(
            "$.links[?(@.rel=='self')].href", withValues(DOMAIN1_CLUSTERS_HREF + "/cluster1")));
    assertThat(
        result, hasJsonPath("$.links[?(@.rel=='parent')].href", withValues(DOMAIN1_CLUSTERS_HREF)));
    assertThat(
        result,
        hasJsonPath(
            "$.links[?(@.title=='scale')].href",
            withValues(DOMAIN1_CLUSTERS_HREF + "/cluster1/scale")));
  }

  @Test
  void nonexistingClusterEndPoint_fails() {
    defineClusters("uid1", "cluster1", "cluster2");

    assertThat(getResponseStatus(DOMAIN1_CLUSTERS_HREF + "/cluster3"), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  void scaleExistingCluster() {
    defineClusters("uid1", "cluster1", "cluster2");

    sendScaleRequest("cluster1", 3);

    assertThat(restBackend.getNumManagedServers("uid1", "cluster1"), equalTo(3));
  }

  private Response sendScaleRequest(String cluster, int numManagedServers) {
    return createRequest(DOMAIN1_CLUSTERS_HREF + String.format("/%s/scale", cluster))
        .post(createScaleRequest(numManagedServers));
  }

  @Test
  void whenClusterUndefined_scalingIsRejected() {
    assertThat(sendScaleRequest("cluster1", 3).getStatus(), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  void whenRequestedByHeaderMissing_scalingIsRejected() {
    excludeRequestedByHeader();
    defineClusters("uid1", "cluster1", "cluster2");

    assertThat(
        sendScaleRequest("cluster1", 3).getStatus(), equalTo(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  void whenConversionWebhookRequestSent_hasExpectedResponse() {
    String conversionReview = getAsString(CONVERSION_REVIEW_REQUEST);
    Response response = sendConversionWebhookRequest(conversionReview);
    String responseString = getAsString((ByteArrayInputStream)response.getEntity());

    assertThat(responseString, equalTo(getAsString(CONVERSION_REVIEW_RESPONSE)));
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
    expectedReview.setApiVersion("admission.k8s.io/v1");
    expectedReview.setKind("AdmissionReview");

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

  private void excludeRequestedByHeader() {
    includeRequestedByHeader = false;
  }

  private void excludeAuthorizationHeader() {
    authorizationHeader = null;
  }

  private void removeBearerPrefix() {
    authorizationHeader = ACCESS_TOKEN;
  }

  private void removeAccessToken() {
    authorizationHeader = ACCESS_TOKEN_PREFIX;
  }

  private void defineDomains(String... uids) {
    Arrays.stream(uids).forEach(restBackend::addDomain);
  }

  private void defineClusters(String domain, String... clusters) {
    restBackend.addDomain(domain, clusters);
  }

  private int getResponseStatus(String href) {
    return createRequest(href).get().getStatus();
  }

  private Entity<ScaleClusterParamsModel> createScaleRequest(int count) {
    return Entity.entity(createScaleClusterParams(count), MediaType.APPLICATION_JSON);
  }

  private ScaleClusterParamsModel createScaleClusterParams(int count) {
    ScaleClusterParamsModel params = new ScaleClusterParamsModel();
    params.setManagedServerCount(count);
    return params;
  }

  @SuppressWarnings("unused")
  static class JsonArrayMatcher extends TypeSafeDiagnosingMatcher<List<Object>> {
    private final Object[] expectedContents;

    private JsonArrayMatcher(Object[] expectedContents) {
      this.expectedContents = expectedContents;
    }

    static JsonArrayMatcher withValues(Object... expectedContents) {
      return new JsonArrayMatcher(expectedContents);
    }

    @Override
    protected boolean matchesSafely(List<Object> item, Description mismatchDescription) {
      Set<Object> actuals = new HashSet<>(item);
      for (Object o : expectedContents) {
        if (!actuals.remove(o)) {
          return reportMismatch(item, mismatchDescription);
        }
      }
      return true;
    }

    private boolean reportMismatch(List<Object> item, Description mismatchDescription) {
      mismatchDescription.appendValueList("JSON array containing [", ",", "]", item);
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendValueList("JSON array containing [", ",", "]", expectedContents);
    }
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
    public int getExternalHttpsPort() {
      return 8081;
    }

    @Override
    public int getInternalHttpsPort() {
      return 8082;
    }
  }

  abstract static class RestBackendStub implements RestBackend {
    private final Map<String, List<ClusterState>> domainClusters = new HashMap<>();

    void addDomain(String domain, String... clusterNames) {
      domainClusters.put(
          domain, Arrays.stream(clusterNames).map(ClusterState::new).collect(Collectors.toList()));
    }

    Integer getNumManagedServers(String domain, String clusterName) {
      return getClusterStateStream(domain, clusterName)
          .findFirst()
          .map(ClusterState::getScale)
          .orElse(0);
    }

    @Override
    public Set<String> getDomainUids() {
      return domainClusters.keySet();
    }

    @Override
    public boolean isDomainUid(String domainUid) {
      return domainClusters.containsKey(domainUid);
    }

    @Override
    public Set<String> getClusters(String domainUid) {
      return domainClusters.get(domainUid).stream()
          .map(ClusterState::getClusterName)
          .collect(Collectors.toSet());
    }

    @Override
    public boolean isCluster(String domainUid, String cluster) {
      return getClusters(domainUid).contains(cluster);
    }

    @Override
    public void scaleCluster(String domainUid, String cluster, int managedServerCount) {
      getClusterStateStream(domainUid, cluster).forEach(cs -> cs.setScale(managedServerCount));
    }

    Stream<ClusterState> getClusterStateStream(String domainUid, String cluster) {
      return domainClusters.get(domainUid).stream().filter(cs -> cs.hasClusterName(cluster));
    }
  }

  static class ClusterState {
    private final String clusterName;
    private Integer scale;

    ClusterState(String clusterName) {
      this.clusterName = clusterName;
    }

    boolean hasClusterName(String clusterName) {
      return this.clusterName.equals(clusterName);
    }

    String getClusterName() {
      return clusterName;
    }

    Integer getScale() {
      return scale;
    }

    void setScale(Integer scale) {
      this.scale = scale;
    }
  }
}
