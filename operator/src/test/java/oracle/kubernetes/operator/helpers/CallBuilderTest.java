// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.google.gson.GsonBuilder;
import com.meterware.pseudoserver.HttpUserAgentTest;
import com.meterware.pseudoserver.PseudoServlet;
import com.meterware.pseudoserver.WebResource;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.models.V1PersistentVolumeSpec;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.VersionInfo;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class CallBuilderTest extends HttpUserAgentTest {
  private static final String NAMESPACE = "testspace";
  private static final String NAME = "name";
  private static final String UID = "uid";
  private static final String DOMAIN_RESOURCE =
      String.format(
          "/apis/weblogic.oracle/" + KubernetesConstants.DOMAIN_VERSION + "/namespaces/%s/domains",
          NAMESPACE);
  private static final String PV_RESOURCE = "/api/v1/persistentvolumes";
  private static final String PVC_RESOURCE =
      String.format("/api/v1/namespaces/%s/persistentvolumeclaims", NAMESPACE);

  private static ApiClient apiClient = new ApiClient();
  private List<Memento> mementos = new ArrayList<>();
  private CallBuilder callBuilder = new CallBuilder();
  private Object requestBody;

  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(PseudoServletCallDispatcher.install(getHostPath()));
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void getVersionCode_returnsAVersionInfo() throws ApiException {
    VersionInfo versionInfo = new VersionInfo().major("1").minor("2");
    defineHttpGetResponse("/version/", versionInfo);

    assertThat(callBuilder.readVersionCode(), equalTo(versionInfo));
  }

  @Test
  public void listDomains_returnsListasJson() throws ApiException {
    DomainList list = new DomainList().withItems(Arrays.asList(new Domain(), new Domain()));
    defineHttpGetResponse(DOMAIN_RESOURCE, list).expectingParameter("fieldSelector", "xxx");

    assertThat(callBuilder.withFieldSelector("xxx").listDomain(NAMESPACE), equalTo(list));
  }

  @Test
  public void replaceDomain_sendsNewDomain() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID, domain, (json) -> requestBody = fromJson(json, Domain.class));

    callBuilder.replaceDomain(UID, NAMESPACE, domain);

    assertThat(requestBody, equalTo(domain));
  }

  @Test(expected = ApiException.class)
  public void replaceDomain_errorResonseCode_throws() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_BAD_REQUEST));

    callBuilder.replaceDomain(UID, NAMESPACE, domain);
  }

  @Test(expected = ApiException.class)
  public void replaceDomain_conflictResponseCode_throws() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_CONFLICT));

    callBuilder.replaceDomain(UID, NAMESPACE, domain);
  }

  @Test
  public void replaceDomainWithRetry_sendsNewDomain() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID, domain, (json) -> requestBody = fromJson(json, Domain.class));

    callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);

    assertThat(requestBody, equalTo(domain));
  }

  @Test
  public void replaceDomainWithRetry_withMaxRetryCountOfZero_sendsNewDomain()
      throws ApiException, NoSuchFieldException, IllegalAccessException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(
        DOMAIN_RESOURCE, UID, domain, (json) -> requestBody = fromJson(json, Domain.class));

    setMaxRetryCount(callBuilder, 0);
    callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);

    assertThat(requestBody, equalTo(domain));
  }

  @Test
  public void replaceDomainWithRetry_sendsNewDomain_afterRetry() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    ConflictOncePutServlet conflictOncePutServlet =
        new ConflictOncePutServlet(domain, (json) -> requestBody = fromJson(json, Domain.class));
    defineResource(DOMAIN_RESOURCE + "/" + UID, conflictOncePutServlet);

    callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);

    assertThat(requestBody, equalTo(domain));
    assertThat(conflictOncePutServlet.conflictReturned, equalTo(true));
  }

  @Test(expected = ApiException.class)
  public void replaceDomainWithConflictRetry_conflictResponseCode_throws() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_CONFLICT));

    callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);
  }

  @Test(expected = ApiException.class)
  public void replaceDomainWithConflictRetry_errorResponseCode_throws() throws ApiException {
    Domain domain = new Domain().withMetadata(createMetadata());
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, new ErrorCodePutServlet(HTTP_BAD_REQUEST));

    callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);
  }

  @Test
  public void replaceDomainWithConflictRetry_conflictResponseCode_retriedMaxTimes()
      throws ApiException, NoSuchFieldException, IllegalAccessException {
    final int MAX_RETRY_COUNT = 5;
    setMaxRetryCount(callBuilder, MAX_RETRY_COUNT);
    Domain domain = new Domain().withMetadata(createMetadata());
    ErrorCodePutServlet conflictPutServlet = new ErrorCodePutServlet(HTTP_CONFLICT);
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, conflictPutServlet);
    try {
      callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);
      fail("Expected ApiException not thrown");
    } catch (ApiException apiException) {

    }
    assertThat(conflictPutServlet.numGetPutResponseCalled, equalTo(MAX_RETRY_COUNT));
  }

  @Test
  public void replaceDomainWithConflictRetry_otherResponseCode_noRetries()
      throws ApiException, NoSuchFieldException, IllegalAccessException {
    Domain domain = new Domain().withMetadata(createMetadata());
    ErrorCodePutServlet conflictPutServlet = new ErrorCodePutServlet(HTTP_INTERNAL_ERROR);
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, conflictPutServlet);
    try {
      callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);
      fail("Expected ApiException not thrown");
    } catch (ApiException apiException) {

    }
    assertThat(conflictPutServlet.numGetPutResponseCalled, equalTo(1));
  }

  @Test
  public void replaceDomainWithConflictRetry_withMaxRetryCountOfZero_noRetries()
      throws ApiException, NoSuchFieldException, IllegalAccessException {
    Domain domain = new Domain().withMetadata(createMetadata());
    ErrorCodePutServlet conflictPutServlet = new ErrorCodePutServlet(HTTP_CONFLICT);
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, conflictPutServlet);
    setMaxRetryCount(callBuilder, 0);
    try {
      callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> domain);
      fail("Expected ApiException not thrown");
    } catch (ApiException apiException) {
      assertThat(apiException.getCode(), equalTo(HTTP_CONFLICT));
    }
    assertThat(conflictPutServlet.numGetPutResponseCalled, equalTo(1));
  }

  @Test
  public void replaceDomainWithConflictRetry_nullUpdatedObject_noRetries()
      throws ApiException, NoSuchFieldException, IllegalAccessException {
    Domain domain = new Domain().withMetadata(createMetadata());
    ErrorCodePutServlet conflictPutServlet = new ErrorCodePutServlet(HTTP_CONFLICT);
    defineHttpPutResponse(DOMAIN_RESOURCE, UID, domain, conflictPutServlet);
    try {
      callBuilder.replaceDomainWithConflictRetry(UID, NAMESPACE, domain, () -> null);
      fail("Expected ApiException not thrown");
    } catch (ApiException apiException) {
      assertThat(apiException.getCode(), equalTo(HTTP_CONFLICT));
    }
    assertThat(conflictPutServlet.numGetPutResponseCalled, equalTo(1));
  }

  Field callBuilderMaxRetryCount;

  private void setMaxRetryCount(CallBuilder callBuilder, int maxRetryCount)
      throws IllegalAccessException, NoSuchFieldException {
    if (callBuilderMaxRetryCount == null) {
      callBuilderMaxRetryCount = CallBuilder.class.getDeclaredField("maxRetryCount");
      callBuilderMaxRetryCount.setAccessible(true);
    }
    callBuilderMaxRetryCount.set(callBuilder, maxRetryCount);
  }

  @Test
  public void createPV_returnsVolumeAsJson() throws ApiException {
    V1PersistentVolume volume = createPersistentVolume();
    defineHttpPostResponse(PV_RESOURCE, volume);

    assertThat(callBuilder.createPersistentVolume(volume), equalTo(volume));
  }

  private V1PersistentVolume createPersistentVolume() {
    return new V1PersistentVolume().metadata(createMetadata()).spec(new V1PersistentVolumeSpec());
  }

  @Test
  public void createPV_sendsClaimAsJson() throws ApiException {
    V1PersistentVolume volume = createPersistentVolume();
    defineHttpPostResponse(
        PV_RESOURCE, volume, (json) -> requestBody = fromJson(json, V1PersistentVolume.class));

    callBuilder.createPersistentVolume(volume);

    assertThat(requestBody, equalTo(volume));
  }

  @Test
  public void deletePV_returnsStatus() throws ApiException {
    defineHttpDeleteResponse(PV_RESOURCE, NAME, new V1Status());

    assertThat(
        callBuilder.deletePersistentVolume(NAME, new V1DeleteOptions()),
        instanceOf(V1Status.class));
  }

  @Test
  public void createPVC_returnsClaimAsJson() throws ApiException {
    V1PersistentVolumeClaim claim = createPersistentVolumeClaim();
    defineHttpPostResponse(PVC_RESOURCE, claim);

    assertThat(callBuilder.createPersistentVolumeClaim(claim), equalTo(claim));
  }

  private V1PersistentVolumeClaim createPersistentVolumeClaim() {
    return new V1PersistentVolumeClaim().metadata(createMetadata()).spec(createSpec());
  }

  @Test
  public void createPVC_sendsClaimAsJson() throws ApiException {
    V1PersistentVolumeClaim claim = createPersistentVolumeClaim();
    defineHttpPostResponse(
        PVC_RESOURCE, claim, (json) -> requestBody = fromJson(json, V1PersistentVolumeClaim.class));

    callBuilder.createPersistentVolumeClaim(claim);

    assertThat(requestBody, equalTo(claim));
  }

  @Test
  public void deletePVC_returnsStatus() throws ApiException {
    defineHttpDeleteResponse(PVC_RESOURCE, NAME, new V1Status());

    assertThat(
        callBuilder.deletePersistentVolumeClaim(NAME, NAMESPACE, new V1DeleteOptions()),
        instanceOf(V1Status.class));
  }

  private V1PersistentVolumeClaimSpec createSpec() {
    return new V1PersistentVolumeClaimSpec().volumeName("TEST_VOL");
  }

  private Object fromJson(String json, Class<?> aClass) {
    return new GsonBuilder().create().fromJson(json, aClass);
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta().namespace(NAMESPACE);
  }

  /** defines a get request for an list of items. */
  private JsonServlet defineHttpGetResponse(String resourceName, Object response) {
    JsonGetServlet servlet = new JsonGetServlet(response);
    defineResource(resourceName, servlet);
    return servlet;
  }

  private void defineHttpPostResponse(String resourceName, Object response) {
    defineResource(resourceName, new JsonPostServlet(response));
  }

  private void defineHttpPostResponse(
      String resourceName, Object response, Consumer<String> bodyValidation) {
    defineResource(resourceName, new JsonPostServlet(response, bodyValidation));
  }

  private void defineHttpPutResponse(
      String resourceName, String name, Object response, Consumer<String> bodyValidation) {
    defineResource(resourceName + "/" + name, new JsonPutServlet(response, bodyValidation));
  }

  private void defineHttpPutResponse(
      String resourceName, String name, Object response, PseudoServlet pseudoServlet) {
    defineResource(resourceName + "/" + name, pseudoServlet);
  }

  private void defineHttpDeleteResponse(String resourceName, String name, Object response) {
    defineResource(resourceName + "/" + name, new JsonDeleteServlet(response));
  }

  private static String toJson(Object object) {
    return new GsonBuilder().create().toJson(object);
  }

  static class PseudoServletCallDispatcher implements SynchronousCallDispatcher {
    private static String basePath;

    static Memento install(String basePath) throws NoSuchFieldException {
      PseudoServletCallDispatcher.basePath = basePath;
      PseudoServletCallDispatcher dispatcher = new PseudoServletCallDispatcher();
      Memento memento = StaticStubSupport.install(CallBuilder.class, "DISPATCHER", dispatcher);
      dispatcher.setUnderlyingDispatcher(memento.getOriginalValue());
      return memento;
    }

    private SynchronousCallDispatcher underlyingDispatcher;

    void setUnderlyingDispatcher(SynchronousCallDispatcher underlyingDispatcher) {
      this.underlyingDispatcher = underlyingDispatcher;
    }

    @Override
    public <T> T execute(
        SynchronousCallFactory<T> factory, RequestParams requestParams, Pool<ApiClient> pool)
        throws ApiException {
      return underlyingDispatcher.execute(factory, requestParams, createSingleUsePool());
    }

    private Pool<ApiClient> createSingleUsePool() {
      return new Pool<ApiClient>() {
        @Override
        protected ApiClient create() {
          ApiClient client = apiClient;
          client.setBasePath(basePath);
          return client;
        }
      };
    }
  }

  static class ErrorCodePutServlet extends PseudoServlet {

    int numGetPutResponseCalled = 0;
    final int errorCode;

    public ErrorCodePutServlet(int errorCode) {
      this.errorCode = errorCode;
    }

    @Override
    public WebResource getPutResponse() {
      numGetPutResponseCalled++;
      return new WebResource("", errorCode);
    }
  }

  static class ConflictOncePutServlet extends JsonBodyServlet {

    boolean conflictReturned;

    private ConflictOncePutServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue, bodyValidation);
    }

    @Override
    public WebResource getPutResponse() throws IOException {
      if (!conflictReturned) {
        conflictReturned = true;
        return new WebResource("", HTTP_CONFLICT);
      }
      return getResponse();
    }
  }

  abstract static class JsonServlet extends PseudoServlet {

    private WebResource response;
    private List<ParameterExpectation> parameterExpectations = new ArrayList<>();

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
        if (error != null) validationErrors.add(error);
      }

      if (!validationErrors.isEmpty()) throw new IOException(String.join("\n", validationErrors));
    }

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
        if (expectedValue.equals(value)) return null;

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
    private Consumer<String> bodyValidation;

    private JsonBodyServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue);
      this.bodyValidation = bodyValidation;
    }

    @Override
    WebResource getResponse() throws IOException {
      if (bodyValidation != null) bodyValidation.accept(new String(getBody()));
      return super.getResponse();
    }
  }

  static class JsonPostServlet extends JsonBodyServlet {

    private JsonPostServlet(Object returnValue) {
      this(returnValue, null);
    }

    private JsonPostServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue, bodyValidation);
    }

    @Override
    public WebResource getPostResponse() throws IOException {
      return getResponse();
    }
  }

  static class JsonPutServlet extends JsonBodyServlet {

    private JsonPutServlet(Object returnValue) {
      this(returnValue, null);
    }

    private JsonPutServlet(Object returnValue, Consumer<String> bodyValidation) {
      super(returnValue, bodyValidation);
    }

    @Override
    public WebResource getPutResponse() throws IOException {
      return getResponse();
    }
  }

  static class JsonDeleteServlet extends JsonServlet {

    private JsonDeleteServlet(Object returnValue) {
      super(returnValue);
    }

    @Override
    public WebResource getDeleteResponse() throws IOException {
      return getResponse();
    }
  }

  static class Event {
    static long lastTime;
    long time;
    long interval;
    String description;

    public Event(long time, String description) {
      this.time = time;
      this.description = description;
      interval = lastTime == 0 ? 0 : time - lastTime;
      lastTime = time;
    }

    @Override
    public String toString() {
      return "Event{"
          + "time="
          + time
          + ", interval="
          + interval
          + ", description='"
          + description
          + '\''
          + '}';
    }
  }
}
