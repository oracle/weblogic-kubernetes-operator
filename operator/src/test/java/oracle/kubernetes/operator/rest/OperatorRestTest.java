// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Map;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import oracle.kubernetes.operator.rest.model.ScaleClusterParamsModel;
import org.junit.jupiter.api.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.rest.AuthenticationFilter.ACCESS_TOKEN_PREFIX;
import static oracle.kubernetes.operator.rest.RestTestBase.JsonArrayMatcher.withValues;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
class RestTest extends RestTestBase {
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
