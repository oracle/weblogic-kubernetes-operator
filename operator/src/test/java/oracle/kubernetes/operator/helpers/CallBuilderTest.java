// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.GsonBuilder;
import com.meterware.pseudoserver.PseudoServer;
import com.meterware.pseudoserver.PseudoServlet;
import com.meterware.pseudoserver.WebResource;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.PROGRESSING;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("SameParameterValue")
class CallBuilderTest {
  private static final String NAMESPACE = "testspace";
  private static final String UID = "uid";
  private static final String DOMAIN_RESOURCE = String.format(
          "/apis/weblogic.oracle/" + KubernetesConstants.DOMAIN_VERSION + "/namespaces/%s/domains",
          NAMESPACE);
  private static final String SERVICE_RESOURCE = String.format("/api/v1/namespaces/%s/services", NAMESPACE);
  private static final String CM_RESOURCE = String.format("/api/v1/namespaces/%s/configmaps", NAMESPACE);
  private static final String SAR_RESOURCE = "/apis/authorization.k8s.io/v1/subjectaccessreviews";
  private static final String SSAR_RESOURCE = "/apis/authorization.k8s.io/v1/selfsubjectaccessreviews";
  private static final String SSRR_RESOURCE = "/apis/authorization.k8s.io/v1/selfsubjectrulesreviews";
  private static final String TR_RESOURCE = "/apis/authentication.k8s.io/v1/tokenreviews";
  private static final String CRD_RESOURCE = "/apis/apiextensions.k8s.io/v1/customresourcedefinitions";

  private static final ApiClient apiClient = new ApiClient();
  private final List<Memento> mementos = new ArrayList<>();
  private final CallBuilder callBuilder = new CallBuilder();
  private Object requestBody;
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
  void getVersionCode_returnsAVersionInfo() throws ApiException {
    VersionInfo versionInfo = new VersionInfo().major("1").minor("2");
    defineHttpGetResponse("/version/", versionInfo);

    assertThat(callBuilder.readVersionCode(), equalTo(versionInfo));
  }

  @Test
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
  void listDomains_returnsListAsJson() throws ApiException {
    DomainList list = new DomainList().withItems(Arrays.asList(new Domain(), new Domain()));
    defineHttpGetResponse(DOMAIN_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    assertThat(callBuilder.withFieldSelector("xxx").listDomain(NAMESPACE), equalTo(list));
  }

  @Test
  void replaceDomain_sendsNewDomain() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID, domain, (json) -> requestBody = fromJson(json, Domain.class));

    callBuilder.replaceDomain(UID, NAMESPACE, domain);

    assertThat(requestBody, equalTo(domain));
  }

  @Test
  void replaceDomain_errorResponseCode_throws() {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_BAD_REQUEST));

    assertThrows(ApiException.class, () -> callBuilder.replaceDomain(UID, NAMESPACE, domain));
  }

  @Test
  void replaceDomain_conflictResponseCode_throws() {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_CONFLICT));

    assertThrows(ApiException.class, () -> callBuilder.replaceDomain(UID, NAMESPACE, domain));
  }

  @Disabled("Disabled until synchronization issue is resolved - RJE")
  @Test
  void listDomainsAsync_returnsUpgrade() throws InterruptedException {
    Domain domain1 = new Domain();
    DomainStatus domainStatus1 = new DomainStatus().withStartTime(null);
    domain1.setStatus(domainStatus1);

    domainStatus1.getConditions().add(new DomainCondition(PROGRESSING).withLastTransitionTime(null));
    domainStatus1.getConditions().add(new DomainCondition(AVAILABLE).withLastTransitionTime(null));

    Domain domain2 = new Domain();
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
    assertThat(received.getItems().get(0), not(hasCondition(PROGRESSING)));
    assertThat(received.getItems().get(1), not(hasCondition(PROGRESSING)));
  }

  @Test
  void listServices_returnsListAsJson() throws InterruptedException {
    V1ServiceList list = new V1ServiceList().items(Arrays.asList(new V1Service(), new V1Service()));
    defineHttpGetResponse(SERVICE_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    KubernetesTestSupportTest.TestResponseStep<V1ServiceList> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().withFieldSelector("xxx").listServiceAsync(NAMESPACE, responseStep));

    V1ServiceList received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(list));
  }

  @Test
  void createService_returnsNewService() throws InterruptedException {
    V1Service service = new V1Service().metadata(createMetadata());
    defineHttpPostResponse(
        SERVICE_RESOURCE, service, (json) -> requestBody = fromJson(json, V1Service.class));

    KubernetesTestSupportTest.TestResponseStep<V1Service> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createServiceAsync(NAMESPACE, service, responseStep));

    V1Service received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(service));
  }

  @Test
  void deleteService_returnsDeletedService() throws InterruptedException {
    V1Service service = new V1Service().metadata(createMetadata());
    defineHttpDeleteResponse(
        SERVICE_RESOURCE, UID, service, (json) -> requestBody = fromJson(json, V1Service.class));

    KubernetesTestSupportTest.TestResponseStep<V1Service> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .deleteServiceAsync(service.getMetadata().getName(), NAMESPACE, UID, new DeleteOptions(), responseStep));

    V1Service received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(service));
  }

  @Test
  void createSubjectAccessReview_returnsNewResource() throws ApiException {
    V1SubjectAccessReview resource = new V1SubjectAccessReview().metadata(createMetadata());
    defineHttpPostResponse(
        SAR_RESOURCE, resource, (json) -> requestBody = fromJson(json, V1Service.class));

    V1SubjectAccessReview received = callBuilder.createSubjectAccessReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  void createSelfSubjectAccessReview_returnsNewResource() throws ApiException {
    V1SelfSubjectAccessReview resource = new V1SelfSubjectAccessReview().metadata(createMetadata());
    defineHttpPostResponse(
        SSAR_RESOURCE, resource, (json) -> requestBody = fromJson(json, V1Service.class));

    V1SelfSubjectAccessReview received = callBuilder.createSelfSubjectAccessReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  void createSelfSubjectRulesReview_returnsNewResource() throws ApiException {
    V1SelfSubjectRulesReview resource = new V1SelfSubjectRulesReview().metadata(createMetadata());
    defineHttpPostResponse(
        SSRR_RESOURCE, resource, (json) -> requestBody = fromJson(json, V1Service.class));

    V1SelfSubjectRulesReview received = callBuilder.createSelfSubjectRulesReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  void createTokensReview_returnsNewResource() throws ApiException {
    V1TokenReview resource = new V1TokenReview().metadata(createMetadata());
    defineHttpPostResponse(
        TR_RESOURCE, resource, (json) -> requestBody = fromJson(json, V1Service.class));

    V1TokenReview received = callBuilder.createTokenReview(resource);

    assertThat(received, equalTo(resource));
  }

  @Test
  void createCRD_returnsNewResource() throws InterruptedException {
    V1CustomResourceDefinition resource = new V1CustomResourceDefinition().metadata(createMetadata());
    defineHttpPostResponse(
        CRD_RESOURCE, resource, (json) -> requestBody = fromJson(json, V1CustomResourceDefinition.class));

    KubernetesTestSupportTest.TestResponseStep<V1CustomResourceDefinition> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createCustomResourceDefinitionAsync(resource, responseStep));

    V1CustomResourceDefinition received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  void replaceCRD_returnsUpdatedResource() throws InterruptedException {
    V1CustomResourceDefinition resource = new V1CustomResourceDefinition().metadata(createMetadata());
    defineHttpPutResponse(
        CRD_RESOURCE, UID, resource, (json) -> requestBody = fromJson(json, V1CustomResourceDefinition.class));

    KubernetesTestSupportTest.TestResponseStep<V1CustomResourceDefinition> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .replaceCustomResourceDefinitionAsync(resource.getMetadata().getName(), resource, responseStep));

    V1CustomResourceDefinition received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  void createConfigMap_returnsNewResource() throws InterruptedException {
    V1ConfigMap resource = new V1ConfigMap().metadata(createMetadata());
    defineHttpPostResponse(
        CM_RESOURCE, resource, (json) -> requestBody = fromJson(json, V1ConfigMap.class));

    KubernetesTestSupportTest.TestResponseStep<V1ConfigMap> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder().createConfigMapAsync(NAMESPACE, resource, responseStep));

    V1ConfigMap received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  @Test
  void replaceConfigMap_returnsUpdatedResource() throws InterruptedException {
    V1ConfigMap resource = new V1ConfigMap().metadata(createMetadata());
    defineHttpPutResponse(
        CM_RESOURCE, UID, resource, (json) -> requestBody = fromJson(json, V1ConfigMap.class));

    KubernetesTestSupportTest.TestResponseStep<V1ConfigMap> responseStep
        = new KubernetesTestSupportTest.TestResponseStep<>();
    testSupport.runSteps(new CallBuilder()
        .replaceConfigMapAsync(resource.getMetadata().getName(), NAMESPACE, resource, responseStep));

    V1ConfigMap received = responseStep.waitForAndGetCallResponse().getResult();

    assertThat(received, equalTo(resource));
  }

  private Object fromJson(String json, Class<?> aaClass) {
    return new GsonBuilder().create().fromJson(json, aaClass);
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta().namespace(NAMESPACE).name(UID);
  }

  /** defines a get request for an list of items. */
  private JsonServlet defineHttpGetResponse(String resourceName, Object response) {
    JsonGetServlet servlet = new JsonGetServlet(response);
    defineResource(resourceName, servlet);
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

  private void defineHttpPostResponse(
      String resourceName, String name, Object response, Consumer<String> bodyValidation) {
    defineResource(resourceName + "/" + name, new JsonPostServlet(response, bodyValidation));
  }

  @SuppressWarnings("unused")
  private void defineHttpPostResponse(
      String resourceName, String name, Object response, PseudoServlet pseudoServlet) {
    defineResource(resourceName + "/" + name, pseudoServlet);
  }

  private void defineHttpDeleteResponse(
      String resourceName, String name, Object response, Consumer<String> bodyValidation) {
    defineResource(resourceName + "/" + name, new JsonDeleteServlet(response, bodyValidation));
  }

  @SuppressWarnings("unused")
  private void defineHttpDeleteResponse(
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

    WebResource getResponse() throws IOException {
      validateParameters();
      return response;
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
}
