// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

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
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import oracle.kubernetes.common.utils.BaseTestUtils;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.utils.TestUtils;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.rest.AuthenticationFilter.ACCESS_TOKEN_PREFIX;

@SuppressWarnings("SameParameterValue")
class RestTestBase extends JerseyTest {
  private static final String ACCESS_TOKEN = "dummy token";

  private final List<Memento> mementos = new ArrayList<>();
  final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  final RestBackendStub restBackend = createStrictStub(RestBackendStub.class);
  boolean includeRequestedByHeader = true;
  String authorizationHeader = ACCESS_TOKEN_PREFIX + " " + ACCESS_TOKEN;

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

  Map getJsonResponse(String href) {
    return new Gson().fromJson(createRequest(href).get(String.class), Map.class);
  }

  Invocation.Builder createRequest(String href) {
    Invocation.Builder request = target(href).request();
    if (authorizationHeader != null) {
      request.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
    if (includeRequestedByHeader) {
      request.header("X-Requested-By", "TestClient");
    }
    return request;
  }

  @Override
  protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
    return new InMemoryTestContainerFactory();
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
    private final Map<String, List<RestTest.ClusterState>> domainClusters = new HashMap<>();

    void addDomain(String domain, String... clusterNames) {
      domainClusters.put(
          domain, Arrays.stream(clusterNames).map(RestTest.ClusterState::new).collect(Collectors.toList()));
    }

    Integer getNumManagedServers(String domain, String clusterName) {
      return getClusterStateStream(domain, clusterName)
          .findFirst()
          .map(RestTest.ClusterState::getScale)
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
          .map(RestTest.ClusterState::getClusterName)
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

    Stream<RestTest.ClusterState> getClusterStateStream(String domainUid, String cluster) {
      return domainClusters.get(domainUid).stream().filter(cs -> cs.hasClusterName(cluster));
    }
  }
}
