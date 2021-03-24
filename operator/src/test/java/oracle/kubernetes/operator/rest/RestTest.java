// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.rest.model.ScaleClusterParamsModel;
import oracle.kubernetes.utils.TestUtils;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.rest.AuthenticationFilter.ACCESS_TOKEN_PREFIX;
import static oracle.kubernetes.operator.rest.RestTest.JsonArrayMatcher.withValues;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
public class RestTest extends JerseyTest {
  private static final String V1 = "v1";
  private static final String OPERATOR_HREF = "/operator";
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

  private final List<Memento> mementos = new ArrayList<>();
  private final RestBackendStub restBackend = createStrictStub(RestBackendStub.class);
  private boolean includeRequestedByHeader = true;
  private String authorizationHeader = ACCESS_TOKEN_PREFIX + " " + ACCESS_TOKEN;

  @BeforeEach
  public void setupRestTest() throws Exception {
    setUp();
    mementos.add(TestUtils.silenceJsonPathLogger());
  }

  @AfterEach
  public void restore() throws Exception {
    tearDown();
    mementos.forEach(Memento::revert);
  }

  @Override
  protected Application configure() {
    return RestServer.createResourceConfig(RestConfigStub.create(this::getRestBackend));
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
  public void whenNoAuthenticationHeader_rejectRequest() {
    excludeAuthorizationHeader();

    assertThat(createRequest(OPERATOR_HREF).get().getStatus(), equalTo(HTTP_UNAUTHORIZED));
  }

  @Test
  public void whenAuthenticationHeaderLacksBearerPrefix_rejectRequest() {
    removeBearerPrefix();

    assertThat(createRequest(OPERATOR_HREF).get().getStatus(), equalTo(HTTP_UNAUTHORIZED));
  }

  @Test
  public void whenAuthenticationHeaderLacksAccessToken_rejectRequest() {
    removeAccessToken();

    assertThat(createRequest(OPERATOR_HREF).get().getStatus(), equalTo(HTTP_UNAUTHORIZED));
  }

  @Test
  public void operatorEndPoint_returnsVersion() {
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
  public void v1EndPoint_returnsVersionAndLinks() {
    Map result = getJsonResponse(V1_HREF);

    assertThat(result, hasJsonPath("$.version", equalTo("v1")));
    assertThat(result, hasJsonPath("$.latest", equalTo(true)));
    assertThat(result, hasJsonPath("$.lifecycle", equalTo("active")));
    assertThat(
        result, hasJsonPath("$.links[*].href", withValues(V1_DOMAINS_HREF, V1_SWAGGER_HREF)));
  }

  @Test
  public void latestVersionEndPoint_returnsVersionAndLinks() {
    Map result = getJsonResponse(LATEST_HREF);

    assertThat(result, hasJsonPath("$.version", equalTo("v1")));
    assertThat(result, hasJsonPath("$.latest", equalTo(true)));
    assertThat(result, hasJsonPath("$.lifecycle", equalTo("active")));
    assertThat(result, hasJsonPath("$.links[*].href", withValues(DOMAINS_HREF, SWAGGER_HREF)));
  }

  @Test
  public void nonexistingVersionEndPoint_fails() {
    assertThat(getResponseStatus(OPERATOR_HREF + "/v99"), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  public void swaggerEndPoint_returnsSwaggerFile() {
    Map result = getJsonResponse(SWAGGER_HREF);

    assertThat(result, hasJsonPath("$.swagger", equalTo("2.0")));
    assertThat(result, hasJsonPath("$.info.version", equalTo("v1")));
    assertThat(result, hasJsonPath("$.paths./operator.get.tags", withValues("Version")));
  }

  @Test
  public void domainsEndPoint_returnsListOfDomainsAndLinks() {
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
  public void existingDomainEndPoint_returnsDomainsUidAndClusterLink() {
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
  public void nonexistingDomainEndPoint_fails() {
    defineDomains("uid1", "uid2");

    assertThat(getResponseStatus(DOMAINS_HREF + "/uid3"), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  public void clustersEndPoint_returnsListOfClustersAndLinks() {
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
  public void existingClusterEndPoint_returnsClusterNameAndScalingLink() {
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
  public void nonexistingClusterEndPoint_fails() {
    defineClusters("uid1", "cluster1", "cluster2");

    assertThat(getResponseStatus(DOMAIN1_CLUSTERS_HREF + "/cluster3"), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  public void scaleExistingCluster() {
    defineClusters("uid1", "cluster1", "cluster2");

    sendScaleRequest("cluster1", 3);

    assertThat(restBackend.getNumManagedServers("uid1", "cluster1"), equalTo(3));
  }

  private Response sendScaleRequest(String cluster, int numManagedServers) {
    return createRequest(DOMAIN1_CLUSTERS_HREF + String.format("/%s/scale", cluster))
        .post(createScaleRequest(numManagedServers));
  }

  @Test
  public void whenClusterUndefined_scalingIsRejected() {
    assertThat(sendScaleRequest("cluster1", 3).getStatus(), equalTo(HTTP_NOT_FOUND));
  }

  @Test
  public void whenRequestedByHeaderMissing_scalingIsRejected() {
    excludeRequestedByHeader();
    defineClusters("uid1", "cluster1", "cluster2");

    assertThat(
        sendScaleRequest("cluster1", 3).getStatus(), equalTo(HttpURLConnection.HTTP_BAD_REQUEST));
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
