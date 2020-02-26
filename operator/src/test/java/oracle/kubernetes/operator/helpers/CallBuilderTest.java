// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.GsonBuilder;
import com.meterware.pseudoserver.HttpUserAgentTest;
import com.meterware.pseudoserver.PseudoServlet;
import com.meterware.pseudoserver.WebResource;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
public class CallBuilderTest extends HttpUserAgentTest {
  private static final String NAMESPACE = "testspace";
  private static final String UID = "uid";
  private static final String DOMAIN_RESOURCE =
      String.format(
          "/apis/weblogic.oracle/" + KubernetesConstants.DOMAIN_VERSION + "/namespaces/%s/domains",
          NAMESPACE);

  private static ApiClient apiClient = new ApiClient();
  private List<Memento> mementos = new ArrayList<>();
  private CallBuilder callBuilder = new CallBuilder();
  private Object requestBody;

  private static String toJson(Object object) {
    return new GsonBuilder().create().toJson(object);
  }

  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(PseudoServletCallDispatcher.install(getHostPath()));
  }

  /**
   * Tear down test.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
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

  private V1PersistentVolumeClaimSpec createSpec() {
    return new V1PersistentVolumeClaimSpec().volumeName("TEST_VOL");
  }

  private Object fromJson(String json, Class<?> aaClass) {
    return new GsonBuilder().create().fromJson(json, aaClass);
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

  @SuppressWarnings("unused")
  private void defineHttpPutResponse(
      String resourceName, String name, Object response, PseudoServlet pseudoServlet) {
    defineResource(resourceName + "/" + name, pseudoServlet);
  }

  private void defineHttpDeleteResponse(String resourceName, String name, Object response) {
    defineResource(resourceName + "/" + name, new JsonDeleteServlet(response));
  }

  static class PseudoServletCallDispatcher implements SynchronousCallDispatcher {
    private static String basePath;
    private SynchronousCallDispatcher underlyingDispatcher;

    static Memento install(String basePath) throws NoSuchFieldException {
      PseudoServletCallDispatcher.basePath = basePath;
      PseudoServletCallDispatcher dispatcher = new PseudoServletCallDispatcher();
      Memento memento = StaticStubSupport.install(CallBuilder.class, "DISPATCHER", dispatcher);
      dispatcher.setUnderlyingDispatcher(memento.getOriginalValue());
      return memento;
    }

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
    private Consumer<String> bodyValidation;

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
