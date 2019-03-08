// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.builders.EventMatcher.addEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.deleteEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.errorEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.modifyEvent;
import static oracle.kubernetes.operator.builders.WatchBuilderTest.JsonServletAction.withResponses;
import static oracle.kubernetes.operator.builders.WatchBuilderTest.ParameterValidation.parameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import com.meterware.pseudoserver.HttpUserAgentTest;
import com.meterware.pseudoserver.PseudoServlet;
import com.meterware.pseudoserver.WebResource;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests watches created by the WatchBuilder, verifying that they are created with the correct query
 * URLs and handle http response correctly. Uses PseudoServer to provide an in-memory test web
 * server.
 */
public class WatchBuilderTest extends HttpUserAgentTest {

  private static final String API_VERSION = "weblogic.oracle/" + KubernetesConstants.DOMAIN_VERSION;
  private static final String NAMESPACE = "testspace";
  private static final String DOMAIN_RESOURCE =
      "/apis/weblogic.oracle/"
          + KubernetesConstants.DOMAIN_VERSION
          + "/namespaces/"
          + NAMESPACE
          + "/domains";
  private static final String SERVICE_RESOURCE = "/api/v1/namespaces/" + NAMESPACE + "/services";
  private static final String POD_RESOURCE = "/api/v1/namespaces/" + NAMESPACE + "/pods";
  private static final String EOL = "\n";
  private static final int INITIAL_RESOURCE_VERSION = 123;

  private static List<AssertionError> validationErrors;

  private int resourceVersion = INITIAL_RESOURCE_VERSION;
  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(ClientPoolStub.install(getHostPath()));
    validationErrors = new ArrayList<>();
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
    if (!validationErrors.isEmpty()) throw validationErrors.get(0);
  }

  @Test
  public void whenDomainWatchReceivesAddResponse_returnItFromIterator() throws Exception {
    Domain domain =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    defineHttpResponse(DOMAIN_RESOURCE, withResponses(createAddedResponse(domain)));

    WatchI<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE);

    assertThat(domainWatch, contains(addEvent(domain)));
  }

  @Test
  public void afterWatchClosed_returnClientToPool() throws Exception {
    Domain domain =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    defineHttpResponse(DOMAIN_RESOURCE, withResponses(createAddedResponse(domain)));

    try (WatchI<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE)) {
      domainWatch.next();
    }

    assertThat(ClientPoolStub.getPooledClients(), not(empty()));
  }

  @Test
  public void afterWatchError_closeDoesNotReturnClientToPool() throws Exception {
    defineHttpResponse(DOMAIN_RESOURCE, withResponses());

    try (WatchI<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE)) {
      domainWatch.next();
      fail("Should have thrown an exception");
    } catch (Throwable ignore) {
    }

    assertThat(ClientPoolStub.getPooledClients(), is(empty()));
  }

  @Test
  public void whenDomainWatchReceivesModifyAndDeleteResponses_returnBothFromIterator()
      throws Exception {
    Domain domain1 =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    Domain domain2 =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain2", NAMESPACE));
    defineHttpResponse(
        DOMAIN_RESOURCE,
        withResponses(createModifiedResponse(domain1), createDeletedResponse(domain2)));

    WatchI<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE);

    assertThat(domainWatch, contains(modifyEvent(domain1), deleteEvent(domain2)));
  }

  @Test
  public void whenDomainWatchReceivesErrorResponse_returnItFromIterator() throws Exception {
    defineHttpResponse(DOMAIN_RESOURCE, withResponses(createErrorResponse(HTTP_ENTITY_TOO_LARGE)));

    WatchI<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE);

    assertThat(domainWatch, contains(errorEvent(HTTP_ENTITY_TOO_LARGE)));
  }

  @Test
  public void whenServiceWatchSpecifiesParameters_verifyAndReturnResponse() throws Exception {
    String startResourceVersion = getNextResourceVersion();
    V1Service service =
        new V1Service()
            .apiVersion(API_VERSION)
            .kind("Service")
            .metadata(createMetaData("service3", NAMESPACE));
    defineHttpResponse(
        SERVICE_RESOURCE,
        withResponses(createModifiedResponse(service))
            .andValidations(
                parameter("resourceVersion").withValue(startResourceVersion),
                parameter("labelSelector")
                    .withValue(DOMAINUID_LABEL + "," + CREATEDBYOPERATOR_LABEL),
                parameter("watch").withValue("true")));

    WatchI<V1Service> serviceWatch =
        new WatchBuilder()
            .withResourceVersion(startResourceVersion)
            .withLabelSelector(DOMAINUID_LABEL + "," + CREATEDBYOPERATOR_LABEL)
            .createServiceWatch(NAMESPACE);

    assertThat(serviceWatch, contains(modifyEvent(service)));
  }

  @Test
  public void whenPodWatchSpecifiesParameters_verifyAndReturnResponse() throws Exception {
    V1Pod pod =
        new V1Pod().apiVersion(API_VERSION).kind("Pod").metadata(createMetaData("pod4", NAMESPACE));
    defineHttpResponse(
        POD_RESOURCE,
        withResponses(createAddedResponse(pod))
            .andValidations(
                parameter("fieldSelector").withValue("thisValue"),
                parameter("includeUninitialized").withValue("false"),
                parameter("limit").withValue("25")));

    WatchI<V1Pod> podWatch =
        new WatchBuilder()
            .withFieldSelector("thisValue")
            .withIncludeUninitialized(false)
            .withLimit(25)
            .createPodWatch(NAMESPACE);

    assertThat(podWatch, contains(addEvent(pod)));
  }

  @Test
  public void whenPodWatchFindsNoData_hasNextReturnsFalse() throws Exception {
    defineHttpResponse(POD_RESOURCE, NO_RESPONSES);

    WatchI<V1Pod> podWatch = new WatchBuilder().createPodWatch(NAMESPACE);

    assertThat(podWatch.hasNext(), is(false));
  }

  private void defineHttpResponse(String resourceName, JsonServletAction... responses) {
    defineResource(resourceName, new JsonServlet(responses));
  }

  static class ParameterValidation {
    private String parameterName;
    private String expectedValue;

    private ParameterValidation(String parameterName) {
      this.parameterName = parameterName;
    }

    static ParameterValidation parameter(String parameterName) {
      return new ParameterValidation(parameterName);
    }

    ParameterValidation withValue(String expectedValue) {
      this.expectedValue = expectedValue;
      return this;
    }

    void verify(String[] parameterValues) {
      try {
        assertThat(
            "parameter " + parameterName, getSingleValue(parameterValues), equalTo(expectedValue));
      } catch (AssertionError e) {
        validationErrors.add(e);
      }
    }
  }

  private static String getSingleValue(String[] values) {
    if (values == null || values.length == 0) return null;
    else return values[0];
  }

  static class JsonServletAction {
    private ParameterValidation[] validations = new ParameterValidation[0];
    private WebResource webResource;

    private JsonServletAction(String... responses) {
      webResource = new WebResource(String.join(EOL, responses), "application/json");
    }

    static JsonServletAction withResponses(String... responses) {
      return new JsonServletAction(responses);
    }

    JsonServletAction andValidations(ParameterValidation... validations) {
      this.validations = validations;
      return this;
    }
  }

  private static final JsonServletAction NO_RESPONSES = new JsonServletAction();

  static class JsonServlet extends PseudoServlet {
    private List<JsonServletAction> actions;
    int requestNum = 0;

    private JsonServlet(JsonServletAction... actions) {
      this.actions = new ArrayList<>(Arrays.asList(actions));
    }

    @Override
    public WebResource getGetResponse() {
      if (requestNum >= actions.size())
        return new WebResource("Unexpected Request #" + requestNum, HTTP_UNAVAILABLE);

      JsonServletAction action = actions.get(requestNum++);
      for (ParameterValidation validation : action.validations)
        validation.verify(getParameter(validation.parameterName));

      return action.webResource;
    }
  }

  @SuppressWarnings("SameParameterValue")
  private V1ObjectMeta createMetaData(String name, String namespace) {
    return new V1ObjectMeta()
        .name(name)
        .namespace(namespace)
        .resourceVersion(getNextResourceVersion());
  }

  private String getNextResourceVersion() {
    return Integer.toString(resourceVersion++);
  }

  private <T> String createAddedResponse(T object) {
    return WatchEvent.createAddedEvent(object).toJson();
  }

  private <T> String createModifiedResponse(T object) {
    return WatchEvent.createModifiedEvent(object).toJson();
  }

  private <T> String createDeletedResponse(T object) {
    return WatchEvent.createDeleteEvent(object).toJson();
  }

  @SuppressWarnings("SameParameterValue")
  private String createErrorResponse(int statusCode) {
    return WatchEvent.createErrorEvent(statusCode).toJson();
  }

  static class ClientPoolStub extends ClientPool {
    private String basePath;
    private static Queue<ApiClient> queue;

    static Memento install(String basePath) throws NoSuchFieldException {
      queue = new ArrayDeque<>();
      return StaticStubSupport.install(ClientPool.class, "SINGLETON", new ClientPoolStub(basePath));
    }

    static Collection<ApiClient> getPooledClients() {
      return Collections.unmodifiableCollection(queue);
    }

    ClientPoolStub(String basePath) {
      this.basePath = basePath;
    }

    @Override
    protected ApiClient create() {
      ApiClient apiClient = super.create();
      apiClient.setBasePath(basePath);
      return apiClient;
    }

    @Override
    protected Queue<ApiClient> getQueue() {
      return queue;
    }
  }
}
