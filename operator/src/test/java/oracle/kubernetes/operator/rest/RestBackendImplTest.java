// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.ws.rs.WebApplicationException;

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
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.AuthorizationProxy;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.rest.RestBackendImpl.TopologyRetriever;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.rest.model.DomainAction;
import oracle.kubernetes.operator.rest.model.DomainActionType;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SUBJECT_ACCESS_REVIEW;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.TOKEN_REVIEW;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("SameParameterValue")
public class RestBackendImplTest {

  private static final int REPLICA_LIMIT = 4;
  private static final String NS = "namespace1";
  private static final String DOMAIN1 = "domain";
  private static final String DOMAIN2 = "domain2";
  private static final String DOMAIN3 = "domain3";
  private static final String DOMAIN4 = "domain4";
  public static final String INITIAL_VERSION = "1";
  private final WlsDomainConfigSupport domain1ConfigSupport = new WlsDomainConfigSupport(DOMAIN1);

  private final List<Memento> mementos = new ArrayList<>();
  private RestBackend restBackend;
  private final V1Namespace namespace = createNamespace(NS);
  private final Domain domain1 = createDomain(NS, DOMAIN1);
  private final Domain domain2 = createDomain(NS, DOMAIN2);
  private final Collection<String> namespaces = new ArrayList<>(Collections.singletonList(NS));
  private Domain updatedDomain;
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain1);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private WlsDomainConfig config;

  private static V1Namespace createNamespace(String name) {
    return new V1Namespace().metadata(new V1ObjectMeta().name(name));
  }

  private static Domain createDomain(String namespace, String name) {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(name))
        .withSpec(new DomainSpec().withDomainUid(name));
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(
        StaticStubSupport.install(RestBackendImpl.class, "INSTANCE", new TopologyRetrieverStub()));

    testSupport.defineResources(namespace, domain1, domain2);
    testSupport.doOnCreate(TOKEN_REVIEW, r -> authenticate((V1TokenReview) r));
    testSupport.doOnCreate(SUBJECT_ACCESS_REVIEW, s -> allow((V1SubjectAccessReview) s));
    testSupport.doOnUpdate(DOMAIN, d -> updatedDomain = (Domain) d);
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5", "ms6");
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
  public void retrieveRegisteredDomainIds() {
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
  public void validateKnownUid() {
    assertThat(restBackend.isDomainUid(DOMAIN2), is(true));
  }

  @Test
  public void rejectUnknownUid() {
    assertThat(restBackend.isDomainUid("no_such_uid"), is(false));
  }

  @Test
  public void whenUnknownDomain_throwException() {
    assertThrows(WebApplicationException.class,
          () -> restBackend.performDomainAction("no_such_uid", new DomainAction(DomainActionType.INTROSPECT)));
  }

  @Test
  public void whenUnknownDomainUpdateCommand_throwException() {
    assertThrows(WebApplicationException.class,
              () -> restBackend.performDomainAction(DOMAIN1, new DomainAction(null)));
  }

  @Test
  public void whenIntrospectionRequestedWhileNoIntrospectVersionDefined_setIntrospectVersion() {
    restBackend.performDomainAction(DOMAIN1, createIntrospectRequest());

    assertThat(getUpdatedIntrospectVersion(), equalTo(INITIAL_VERSION));
  }

  private DomainAction createIntrospectRequest() {
    return new DomainAction(DomainActionType.INTROSPECT);
  }

  private String getUpdatedIntrospectVersion() {
    return getOptionalDomain1().map(Domain::getIntrospectVersion).orElse(null);
  }

  @Nonnull
  private Optional<Domain> getOptionalDomain1() {
    return testSupport.<Domain>getResources(DOMAIN).stream().filter(this::isDomain1).findFirst();
  }

  private boolean isDomain1(Domain domain) {
    return Optional.ofNullable(domain).map(Domain::getMetadata).filter(this::isDomain1Meta).isPresent();
  }

  private boolean isDomain1Meta(V1ObjectMeta meta) {
    return meta != null && NS.equals(meta.getNamespace()) && DOMAIN1.equals(meta.getName());
  }

  @Test
  public void whenIntrospectionRequestedWhileIntrospectVersionNonNumeric_setNumericVersion() {
    configurator.withIntrospectVersion("zork");

    restBackend.performDomainAction(DOMAIN1, createIntrospectRequest());

    assertThat(getUpdatedIntrospectVersion(), equalTo(INITIAL_VERSION));
  }

  @Test
  public void whenIntrospectionRequestedWhileIntrospectVersionDefined_incrementIntrospectVersion() {
    configurator.withIntrospectVersion("17");

    restBackend.performDomainAction(DOMAIN1, createIntrospectRequest());

    assertThat(getUpdatedIntrospectVersion(), equalTo("18"));
  }

  @Test
  public void whenClusterRestartRequestedWhileNoRestartVersionDefined_setRestartVersion() {
    restBackend.performDomainAction(DOMAIN1, createDomainRestartRequest());

    assertThat(getUpdatedRestartVersion(), equalTo(INITIAL_VERSION));
  }

  private DomainAction createDomainRestartRequest() {
    return new DomainAction(DomainActionType.RESTART);
  }

  private String getUpdatedRestartVersion() {
    return getOptionalDomain1().map(Domain::getRestartVersion).orElse(null);
  }

  @Test
  public void whenRestartRequestedWhileRestartVersionDefined_incrementIntrospectVersion() {
    configurator.withRestartVersion("23");

    restBackend.performDomainAction(DOMAIN1, createDomainRestartRequest());

    assertThat(getUpdatedRestartVersion(), equalTo("24"));
  }

  // functionality needed for clusters resource

  @Test
  public void retrieveDefinedClusters() {
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    domain1ConfigSupport.addWlsCluster("cluster2", "ms4", "ms5", "ms6");
    setupScanCache();

    assertThat(restBackend.getClusters(DOMAIN1), containsInAnyOrder("cluster1", "cluster2"));
  }

  // functionality needed for cluster resource

  @Test
  public void acceptDefinedClusterName() {
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    domain1ConfigSupport.addWlsCluster("cluster2", "ms4", "ms5", "ms6");
    setupScanCache();

    assertThat(restBackend.isCluster(DOMAIN1, "cluster1"), is(true));
  }

  @Test
  public void rejectUndefinedClusterName() {
    domain1ConfigSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    domain1ConfigSupport.addWlsCluster("cluster2", "ms4", "ms5", "ms6");
    setupScanCache();

    assertThat(restBackend.isCluster(DOMAIN1, "cluster3"), is(false));
  }

  @Test
  public void whenDomainRestartRequestedWhileNoRestartVersionDefined_setRestartVersion() {
    restBackend.performDomainAction(DOMAIN1, createDomainRestartRequest());

    assertThat(getUpdatedRestartVersion(), equalTo(INITIAL_VERSION));
  }

  // functionality used for scale resource

  @Test
  public void whenNegativeScaleSpecified_throwException() {
    assertThrows(WebApplicationException.class,
              () -> restBackend.scaleCluster(DOMAIN1, "cluster1", -1));
  }

  @Test
  public void whenPerClusterReplicaSettingMatchesScaleRequest_doNothing() {
    configureCluster("cluster1").withReplicas(5);

    restBackend.scaleCluster(DOMAIN1, "cluster1", 5);

    assertThat(getUpdatedDomain(), nullValue());
  }

  private Domain getUpdatedDomain() {
    return updatedDomain;
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configureDomain().configureCluster(clusterName);
  }

  @Test
  public void whenPerClusterReplicaSetting_scaleClusterUpdatesSetting() {
    configureCluster("cluster1").withReplicas(1);

    restBackend.scaleCluster(DOMAIN1, "cluster1", 5);

    assertThat(getUpdatedDomain().getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void whenNoPerClusterReplicaSettingAndDefaultMatchesRequest_doNothing() {
    configureDomain().withDefaultReplicaCount(REPLICA_LIMIT);

    restBackend.scaleCluster(DOMAIN1, "cluster1", REPLICA_LIMIT);

    assertThat(getUpdatedDomain(), nullValue());
  }

  @Test
  public void whenReplaceDomainReturnsError_scaleClusterThrowsException() {
    testSupport.failOnResource(DOMAIN, DOMAIN2, NS, HTTP_CONFLICT);

    DomainConfiguratorFactory.forDomain(domain2).configureCluster("cluster1").withReplicas(2);

    assertThrows(WebApplicationException.class,
              () -> restBackend.scaleCluster(DOMAIN2, "cluster1", 3));
  }

  @Test
  public void verify_getWlsDomainConfig_returnsWlsDomainConfig() {
    WlsDomainConfig wlsDomainConfig = ((RestBackendImpl) restBackend).getWlsDomainConfig(DOMAIN1);

    assertThat(wlsDomainConfig.getName(), equalTo(DOMAIN1));
  }

  @Test
  public void verify_getWlsDomainConfig_doesNotReturnNull_whenNoSuchDomainUid() {
    WlsDomainConfig wlsDomainConfig =
        ((RestBackendImpl) restBackend).getWlsDomainConfig("NoSuchDomainUID");

    assertThat(wlsDomainConfig, notNullValue());
  }

  @Test
  public void verify_getWlsDomainConfig_doesNotReturnNull_whenScanIsNull() {
    config = null;

    WlsDomainConfig wlsDomainConfig = ((RestBackendImpl) restBackend).getWlsDomainConfig(DOMAIN1);

    assertThat(wlsDomainConfig, notNullValue());
  }

  @Test
  public void whenUsingAccessToken_userInfoIsNull() {
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces);
    assertThat(restBackend.getUserInfo(), nullValue());
  }

  @Test
  public void whenUsingTokenReview_userInfoNotNull() {
    TuningParameters.getInstance().put("tokenReviewAuthentication", "true");
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces);
    assertThat(restBackend.getUserInfo(), notNullValue());
  }

  @Test
  public void whenUsingAccessToken_authorizationCheckNotCalled() {
    AuthorizationProxyStub authorizationProxyStub = new AuthorizationProxyStub();
    RestBackendImpl restBackendImpl = new RestBackendImpl("", "", this::getDomainNamespaces)
        .withAuthorizationProxy(authorizationProxyStub);
    restBackendImpl.getClusters(DOMAIN1);
    assertThat(authorizationProxyStub.atzCheck, is(false));
  }

  @Test
  public void whenUsingTokenReview_authorizationCheckCalled() {
    TuningParameters.getInstance().put("tokenReviewAuthentication", "true");
    AuthorizationProxyStub authorizationProxyStub = new AuthorizationProxyStub();
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces)
        .withAuthorizationProxy(authorizationProxyStub);
    restBackend.getClusters(DOMAIN1);
    assertThat(authorizationProxyStub.atzCheck, is(true));
  }

  @Test
  public void whenUsingAccessToken_configureApiClient() {
    RestBackendImpl restBackend = new RestBackendImpl("", "1234", this::getDomainNamespaces);
    ApiClient apiClient = restBackend.getCallBuilder().getClientPool().take();
    Authentication authentication = apiClient.getAuthentication("BearerToken");
    assertThat(authentication instanceof ApiKeyAuth, is(true));
    String apiKey = ((ApiKeyAuth) authentication).getApiKey();
    assertThat(apiKey, is("1234"));
  }

  @Test
  public void whenUsingTokenReview_configureApiClient() {
    TuningParameters.getInstance().put("tokenReviewAuthentication", "true");
    RestBackendImpl restBackend = new RestBackendImpl("", "", this::getDomainNamespaces);
    ApiClient apiClient = restBackend.getCallBuilder().getClientPool().take();
    Authentication authentication = apiClient.getAuthentication("BearerToken");
    assertThat(authentication instanceof ApiKeyAuth, is(true));
    String apiKey = ((ApiKeyAuth) authentication).getApiKey();
    assertNull(apiKey);
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
