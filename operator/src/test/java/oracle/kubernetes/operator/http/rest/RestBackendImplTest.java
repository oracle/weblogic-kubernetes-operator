// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.auth.ApiKeyAuth;
import io.kubernetes.client.openapi.auth.Authentication;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.V1UserInfo;
import jakarta.ws.rs.WebApplicationException;
import oracle.kubernetes.operator.helpers.AuthorizationProxy;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.http.rest.RestBackendImpl.TopologyRetriever;
import oracle.kubernetes.operator.http.rest.backend.RestBackend;
import oracle.kubernetes.operator.http.rest.model.DomainAction;
import oracle.kubernetes.operator.http.rest.model.DomainActionType;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CLUSTER;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SUBJECT_ACCESS_REVIEW;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.TOKEN_REVIEW;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("SameParameterValue")
class RestBackendImplTest {

  public static final String CLUSTER_1 = "cluster1";
  private static final int REPLICA_LIMIT = 4;
  private static final String NS = "namespace1";
  private static final String NS2 = "namespace2";
  private static final String NS3 = "namespace3";
  private static final String DOMAIN1 = "domain";
  private static final String DOMAIN2 = "domain2";
  private static final String DOMAIN3 = "domain3";
  private static final String DOMAIN4 = "domain4";
  public static final String INITIAL_VERSION = "1";
  private final WlsDomainConfigSupport domain1ConfigSupport = new WlsDomainConfigSupport(DOMAIN1);

  private final List<Memento> mementos = new ArrayList<>();
  private RestBackend restBackend;
  private final V1Namespace namespace = createNamespace(NS);
  private final DomainResource domain1 = createDomain(NS, DOMAIN1);
  private final DomainResource domain2 = createDomain(NS, DOMAIN2);
  private final Collection<String> namespaces = new ArrayList<>(Collections.singletonList(NS));
  private DomainResource updatedDomain;
  private ClusterResource updatedClusterResource;
  private ClusterResource createdClusterResource;
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain1);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private WlsDomainConfig config;

  private static V1Namespace createNamespace(String name) {
    return new V1Namespace().metadata(new V1ObjectMeta().name(name));
  }

  private static DomainResource createDomain(String namespace, String name) {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(name))
        .withSpec(new DomainSpec().withDomainUid(name));
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(
        StaticStubSupport.install(RestBackendImpl.class, "instance", new TopologyRetrieverStub()));

    testSupport.defineResources(namespace, domain1, domain2);
    testSupport.doOnCreate(TOKEN_REVIEW, r -> authenticate((V1TokenReview) r));
    testSupport.doOnCreate(SUBJECT_ACCESS_REVIEW, s -> allow((V1SubjectAccessReview) s));
    testSupport.doOnUpdate(DOMAIN, d -> updatedDomain = (DomainResource) d);
    testSupport.doOnUpdate(CLUSTER, c -> updatedClusterResource = (ClusterResource) c);
    testSupport.doOnCreate(CLUSTER, c -> createdClusterResource = (ClusterResource) c);
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5", "ms6");
    domain1ConfigSupport.addWlsCluster("cluster2", "ms1", "ms2", "ms3", "ms4", "ms5", "ms6");
    restBackend = new RestBackendImpl("", "", this::getDomainNamespaces);

    setupScanCache();
  }

  Collection<String> getDomainNamespaces() {
    return namespaces;
  }

  private void authenticate(V1TokenReview tokenReview) {
    tokenReview.setStatus(new V1TokenReviewStatus().authenticated(true).user(new V1UserInfo()));
  }

  private void allow(V1SubjectAccessReview subjectAccessReview) {
    subjectAccessReview.setStatus(new V1SubjectAccessReviewStatus().allowed(true));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  // functionality needed for Domains resource

  @Test
  void retrieveRegisteredDomainIds() {
    createNamespaceWithDomains("ns2", DOMAIN3, DOMAIN4);
    namespaces.add("ns2");
    
    assertThat(restBackend.getDomainUids(), containsInAnyOrder(DOMAIN1, DOMAIN2, DOMAIN3, DOMAIN4));
  }

  private void createNamespaceWithDomains(String ns, String... domainNames) {
    testSupport.defineResources(createNamespace(ns));
    for (String domainName : domainNames) {
      testSupport.defineResources(createDomain(ns, domainName));
    }
  }

  // functionality needed for Domain resource

  @Test
  void validateKnownUid() {
    assertThat(restBackend.isDomainUid(DOMAIN2), is(true));
  }

  @Test
  void rejectUnknownUid() {
    assertThat(restBackend.isDomainUid("no_such_uid"), is(false));
  }

  @Test
  void whenUnknownDomain_throwException() {
    assertThrows(WebApplicationException.class,
          () -> restBackend.performDomainAction("no_such_uid", new DomainAction(DomainActionType.INTROSPECT)));
  }

  @Test
  void whenUnknownDomainUpdateCommand_throwException() {
    assertThrows(WebApplicationException.class,
              () -> restBackend.performDomainAction(DOMAIN1, new DomainAction(null)));
  }

  @Test
  void whenIntrospectionRequestedWhileNoIntrospectVersionDefined_setIntrospectVersion() {
    restBackend.performDomainAction(DOMAIN1, createIntrospectRequest());

    assertThat(getUpdatedIntrospectVersion(), equalTo(INITIAL_VERSION));
  }

  private DomainAction createIntrospectRequest() {
    return new DomainAction(DomainActionType.INTROSPECT);
  }

  private String getUpdatedIntrospectVersion() {
    return getOptionalDomain1().map(DomainResource::getIntrospectVersion).orElse(null);
  }

  @Nonnull
  private Optional<DomainResource> getOptionalDomain1() {
    return testSupport.<DomainResource>getResources(DOMAIN).stream().filter(this::isDomain1).findFirst();
  }

  private boolean isDomain1(DomainResource domain) {
    return Optional.ofNullable(domain).map(DomainResource::getMetadata).filter(this::isDomain1Meta).isPresent();
  }

  private boolean isDomain1Meta(V1ObjectMeta meta) {
    return meta != null && NS.equals(meta.getNamespace()) && DOMAIN1.equals(meta.getName());
  }

  @Test
  void whenIntrospectionRequestedWhileIntrospectVersionNonNumeric_setNumericVersion() {
    configurator.withIntrospectVersion("zork");

    restBackend.performDomainAction(DOMAIN1, createIntrospectRequest());

    assertThat(getUpdatedIntrospectVersion(), equalTo(INITIAL_VERSION));
  }

  @Test
  void whenIntrospectionRequestedWhileIntrospectVersionDefined_incrementIntrospectVersion() {
    configurator.withIntrospectVersion("17");

    restBackend.performDomainAction(DOMAIN1, createIntrospectRequest());

    assertThat(getUpdatedIntrospectVersion(), equalTo("18"));
  }

  @Test
  void whenClusterRestartRequestedWhileNoRestartVersionDefined_setRestartVersion() {
    restBackend.performDomainAction(DOMAIN1, createDomainRestartRequest());

    assertThat(getUpdatedRestartVersion(), equalTo(INITIAL_VERSION));
  }

  private DomainAction createDomainRestartRequest() {
    return new DomainAction(DomainActionType.RESTART);
  }

  private String getUpdatedRestartVersion() {
    return getOptionalDomain1().map(DomainResource::getRestartVersion).orElse(null);
  }

  @Test
  void whenRestartRequestedWhileRestartVersionDefined_incrementIntrospectVersion() {
    configurator.withRestartVersion("23");

    restBackend.performDomainAction(DOMAIN1, createDomainRestartRequest());

    assertThat(getUpdatedRestartVersion(), equalTo("24"));
  }

  // functionality needed for clusters resource

  @Test
  void retrieveDefinedClusters() {
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    domain1ConfigSupport.addWlsCluster("cluster2", "ms4", "ms5", "ms6");
    setupScanCache();

    assertThat(restBackend.getClusters(DOMAIN1), containsInAnyOrder("cluster1", "cluster2"));
  }

  // functionality needed for cluster resource

  @Test
  void acceptDefinedClusterName() {
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    domain1ConfigSupport.addWlsCluster("cluster2", "ms4", "ms5", "ms6");
    setupScanCache();

    assertThat(restBackend.isCluster(DOMAIN1, "cluster1"), is(true));
  }

  @Test
  void rejectUndefinedClusterName() {
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    domain1ConfigSupport.addWlsCluster("cluster2", "ms4", "ms5", "ms6");
    setupScanCache();

    assertThat(restBackend.isCluster(DOMAIN1, "cluster3"), is(false));
  }

  @Test
  void whenDomainRestartRequestedWhileNoRestartVersionDefined_setRestartVersion() {
    restBackend.performDomainAction(DOMAIN1, createDomainRestartRequest());

    assertThat(getUpdatedRestartVersion(), equalTo(INITIAL_VERSION));
  }

  // functionality used for scale resource

  @Test
  void whenNegativeScaleSpecified_throwException() {
    assertThrows(WebApplicationException.class,
              () -> restBackend.scaleCluster(DOMAIN1, "cluster1", -1));
  }

  @Test
  void whenPerClusterReplicaSettingMatchesScaleRequest_doNothing() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain1);
    configureCluster(info,"cluster1").withReplicas(5);
    info.getReferencedClusters().forEach(testSupport::defineResources);

    restBackend.scaleCluster(DOMAIN1, "cluster1", 5);

    assertThat(getUpdatedDomain(), nullValue());
  }

  @Test
  void whenPerClusterReplicaSettingGreaterThanMax_throwsException() {
    final String cluster1 = "cluster1";
    DomainPresenceInfo info = new DomainPresenceInfo(domain1);
    configureCluster(info, cluster1).withReplicas(5);
    domain1ConfigSupport.addWlsCluster(new WlsDomainConfigSupport.DynamicClusterConfigBuilder(cluster1)
            .withClusterLimits(0, 5));
    info.getReferencedClusters().forEach(testSupport::defineResources);

    assertThrows(WebApplicationException.class,
            () -> restBackend.scaleCluster(DOMAIN1, cluster1, 10));
  }

  private DomainResource getUpdatedDomain() {
    return updatedDomain;
  }

  private ClusterConfigurator configureCluster(DomainPresenceInfo info, String clusterName) {
    return configureDomain().configureCluster(info, clusterName);
  }

  @Test
  void whenPerClusterResourceReplicaSetting_scaleClusterUpdatesClusterResource() {
    final ClusterResource clusterResource = createClusterResource(DOMAIN1, NS, CLUSTER_1)
            .withReplicas(1);
    testSupport.defineResources(clusterResource);

    configureDomain().withClusterReference(clusterResource.getClusterResourceName());
    restBackend.scaleCluster(DOMAIN1, CLUSTER_1, 5);

    assertThat(getUpdatedClusterResource().getSpec().getReplicas(), equalTo(5));
  }

  @Test
  void whenPerClusterResourceReplicaSetting_NullReplica_scaleClusterUpdatesClusterResource() {
    final ClusterResource clusterResource = createClusterResource(DOMAIN1, NS, CLUSTER_1);
    clusterResource.getSpec().setReplicas(null);
    testSupport.defineResources(clusterResource);

    configureDomain().withClusterReference(clusterResource.getClusterResourceName());
    restBackend.scaleCluster(DOMAIN1, CLUSTER_1, 5);

    assertThat(getUpdatedClusterResource().getSpec().getReplicas(), equalTo(5));
  }

  @Test
  void whenMultipleClusterResourceWithSameClusterName_scaleClusterUpdatesClusterInCorrectDomain() {
    defineClusterResources(new String[] {DOMAIN2, DOMAIN1, DOMAIN3}, new String[] {NS, NS, NS});
    restBackend.scaleCluster(DOMAIN1, CLUSTER_1, 5);

    assertThat(getUpdatedClusterResource().getClusterResourceName(), equalTo(DOMAIN1 + '-' + CLUSTER_1));
    assertThat(getUpdatedClusterResource().getSpec().getReplicas(), equalTo(5));
  }

  @Test
  void whenMultipleClusterResourceWithSameResourceName_scaleClusterUpdatesClusterInCorrectNamespace() {
    defineClusterResources(new String[] {DOMAIN1, DOMAIN1, DOMAIN1}, new String[] {NS2, NS, NS3});
    restBackend.scaleCluster(DOMAIN1, CLUSTER_1, 5);

    assertThat(getUpdatedClusterResource().getMetadata().getNamespace(), equalTo(NS));
    assertThat(getUpdatedClusterResource().getSpec().getReplicas(), equalTo(5));
  }

  private void defineClusterResources(String[] domainNames, String[] namespaces) {

    ClusterResource[] clusterResources  = new ClusterResource[domainNames.length];
    for (int i = 0; i < clusterResources.length; i++) {
      clusterResources[i] =
          createClusterResource(domainNames[i], namespaces[i], CLUSTER_1).withReplicas(1);
    }
    testSupport.defineResources(clusterResources);

    ClusterResource clusterToReturn = clusterResources[1]; // return the middle one
    configureDomain().withClusterReference(DOMAIN1 + '-' + CLUSTER_1);
  }

  @Test
  void whenPerClusterResourceReplicaSettingMatchesScaleRequest_doNothing() {
    final ClusterResource clusterResource = createClusterResource(DOMAIN1, NS, CLUSTER_1)
            .withReplicas(1);
    testSupport.defineResources(clusterResource);

    restBackend.scaleCluster(DOMAIN1, CLUSTER_1, 1);

    assertThat(getUpdatedClusterResource(), nullValue());
  }

  @Test
  void whenPerClusterResourceReplicaSettingGreaterThanMax_throwsException() {
    final ClusterResource clusterResource = createClusterResource(DOMAIN1, NS, CLUSTER_1)
            .withReplicas(1);
    testSupport.defineResources(clusterResource);
    domain1ConfigSupport.addWlsCluster(new WlsDomainConfigSupport.DynamicClusterConfigBuilder(CLUSTER_1)
            .withClusterLimits(0, 5));

    assertThrows(WebApplicationException.class,
            () -> restBackend.scaleCluster(DOMAIN1, CLUSTER_1, 10));
  }

  private ClusterResource createClusterResource(String uid, String namespace, String clusterName) {
    return new ClusterResource()
            .withMetadata(new V1ObjectMeta().namespace(namespace).name(uid + '-' + clusterName))
            .spec(new ClusterSpec().withClusterName(clusterName));
  }

  private ClusterResource getUpdatedClusterResource() {
    return updatedClusterResource;
  }

  private ClusterResource getCreatedClusterResource() {
    return createdClusterResource;
  }

  @Test
  void whenNoPerClusterReplicaSettingAndDefaultMatchesRequest_doNothing() {
    configureDomain().withDefaultReplicaCount(REPLICA_LIMIT);

    restBackend.scaleCluster(DOMAIN1, "cluster1", REPLICA_LIMIT);

    assertThat(getUpdatedDomain(), nullValue());
  }

  @Test
  void whenNoPerClusterReplicaSettingAndNonDefaultMatchesRequest_createCluster() {
    configureDomain().withDefaultReplicaCount(REPLICA_LIMIT);

    restBackend.scaleCluster(DOMAIN1, "cluster1", 3);

    assertThat(getCreatedClusterResource().getSpec().getReplicas(), equalTo(3));
  }

  @Test
  void verify_getWlsDomainConfig_returnsWlsDomainConfig() {
    WlsDomainConfig wlsDomainConfig = ((RestBackendImpl) restBackend).getWlsDomainConfig(DOMAIN1);

    assertThat(wlsDomainConfig.getName(), equalTo(DOMAIN1));
  }

  @Test
  void verify_getWlsDomainConfig_doesNotReturnNull_whenNoSuchDomainUid() {
    WlsDomainConfig wlsDomainConfig =
        ((RestBackendImpl) restBackend).getWlsDomainConfig("NoSuchDomainUID");

    assertThat(wlsDomainConfig, notNullValue());
  }

  @Test
  void verify_getWlsDomainConfig_doesNotReturnNull_whenScanIsNull() {
    config = null;

    WlsDomainConfig wlsDomainConfig = ((RestBackendImpl) restBackend).getWlsDomainConfig(DOMAIN1);

    assertThat(wlsDomainConfig, notNullValue());
  }

  @Test
  void whenUsingAccessToken_userInfoIsNull() {
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces);
    assertThat(restBackend.getUserInfo(), nullValue());
  }

  @Test
  void whenUsingTokenReview_userInfoNotNull() {
    TuningParametersStub.setParameter("tokenReviewAuthentication", "true");
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces);
    assertThat(restBackend.getUserInfo(), notNullValue());
  }

  @Test
  void whenUsingAccessToken_authorizationCheckNotCalled() {
    AuthorizationProxyStub authorizationProxyStub = new AuthorizationProxyStub();
    RestBackendImpl restBackendImpl = new RestBackendImpl("", "", this::getDomainNamespaces)
        .withAuthorizationProxy(authorizationProxyStub);
    restBackendImpl.getClusters(DOMAIN1);
    assertThat(authorizationProxyStub.atzCheck, is(false));
  }

  @Test
  void whenUsingTokenReview_authorizationCheckCalled() {
    TuningParametersStub.setParameter("tokenReviewAuthentication", "true");
    AuthorizationProxyStub authorizationProxyStub = new AuthorizationProxyStub();
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces)
        .withAuthorizationProxy(authorizationProxyStub);
    restBackend.getClusters(DOMAIN1);
    assertThat(authorizationProxyStub.atzCheck, is(true));
  }

  @Test
  void whenUsingAccessToken_configureApiClient() {
    RestBackendImpl restBackend = new RestBackendImpl("", "1234", this::getDomainNamespaces);
    ApiClient apiClient = restBackend.getCallBuilder().getClientPool().take();
    Authentication authentication = apiClient.getAuthentication("BearerToken");
    assertThat(authentication instanceof ApiKeyAuth, is(true));
    String apiKey = ((ApiKeyAuth) authentication).getApiKey();
    assertThat(apiKey, is("1234"));
  }

  @Test
  void whenUsingTokenReview_configureApiClient() {
    TuningParametersStub.setParameter("tokenReviewAuthentication", "true");
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces);
    ApiClient apiClient = restBackend.getCallBuilder().getClientPool().take();
    Authentication authentication = apiClient.getAuthentication("BearerToken");
    assertThat(authentication instanceof ApiKeyAuth, is(true));
    String apiKey = ((ApiKeyAuth) authentication).getApiKey();
    assertThat(apiKey, nullValue());
  }


  private DomainConfigurator configureDomain() {
    return configurator;
  }

  private void setupScanCache() {
    config = domain1ConfigSupport.createDomainConfig();
  }

  private class TopologyRetrieverStub implements TopologyRetriever {
    @Override
    public WlsDomainConfig getWlsDomainConfig(String ns, String domainUid) {
      return config;
    }
  }

  private static class AuthorizationProxyStub extends AuthorizationProxy {
    boolean atzCheck = false;

    /**
     * Check if the specified principal is allowed to perform the specified operation on the specified
     * resource in the specified scope.
     *
     * @param principal The user, group or service account.
     * @param groups The groups that principal is a member of.
     * @param operation The operation to be authorized.
     * @param resource The kind of resource on which the operation is to be authorized.
     * @param resourceName The name of the resource instance on which the operation is to be
     *     authorized.
     * @param scope The scope of the operation (cluster or namespace).
     * @param namespaceName name of the namespace if scope is namespace else null.
     * @return true if the operation is allowed, or false if not.
     */
    public boolean check(
        String principal,
        final List<String> groups,
        Operation operation,
        Resource resource,
        String resourceName,
        Scope scope,
        String namespaceName) {
      atzCheck = true;
      return atzCheck;
    }
  }
}
