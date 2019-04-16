// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SUBJECT_ACCESS_REVIEW;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.TOKEN_REVIEW;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1SubjectAccessReview;
import io.kubernetes.client.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.models.V1TokenReview;
import io.kubernetes.client.models.V1TokenReviewStatus;
import io.kubernetes.client.models.V1UserInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.rest.RestBackendImpl.TopologyRetriever;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class RestBackendImplTest {

  private static final int REPLICA_LIMIT = 4;
  private static final String NS = "namespace1";
  private static final String NAME1 = "domain";
  private static final String NAME2 = "domain2";
  private WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(NAME1);

  private List<Memento> mementos = new ArrayList<>();
  private RestBackend restBackend;
  private Domain domain = createDomain(NS, NAME1);
  private Domain domain2 = createDomain(NS, NAME2);
  private Domain updatedDomain;
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();

  private static Domain createDomain(String namespace, String name) {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(name))
        .withSpec(new DomainSpec().withDomainUID(name));
  }

  private WlsDomainConfig config;

  private class TopologyRetrieverStub implements TopologyRetriever {
    @Override
    public WlsDomainConfig getWlsDomainConfig(String ns, String domainUID) {
      return config;
    }
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(
        StaticStubSupport.install(RestBackendImpl.class, "INSTANCE", new TopologyRetrieverStub()));

    testSupport.defineResources(domain, domain2);
    testSupport.doOnCreate(TOKEN_REVIEW, r -> authenticate((V1TokenReview) r));
    testSupport.doOnCreate(SUBJECT_ACCESS_REVIEW, s -> allow((V1SubjectAccessReview) s));
    testSupport.doOnUpdate(DOMAIN, d -> updatedDomain = (Domain) d);
    configSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5", "ms6");
    restBackend = new RestBackendImpl("", "", Collections.singletonList(NS));

    setupScanCache();
  }

  private void authenticate(V1TokenReview tokenReview) {
    tokenReview.setStatus(new V1TokenReviewStatus().authenticated(true).user(new V1UserInfo()));
  }

  private void allow(V1SubjectAccessReview subjectAccessReview) {
    subjectAccessReview.setStatus(new V1SubjectAccessReviewStatus().allowed(true));
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test(expected = WebApplicationException.class)
  public void whenNegativeScaleSpecified_throwException() {
    restBackend.scaleCluster(NAME1, "cluster1", -1);
  }

  @Test
  public void whenPerClusterReplicaSettingMatchesScaleRequest_doNothing() {
    configureCluster("cluster1").withReplicas(5);

    restBackend.scaleCluster(NAME1, "cluster1", 5);

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

    restBackend.scaleCluster(NAME1, "cluster1", 5);

    assertThat(getUpdatedDomain().getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  @Ignore
  public void whenNoPerClusterReplicaSetting_scaleClusterCreatesOne() {
    restBackend.scaleCluster(NAME1, "cluster1", 5);

    assertThat(getUpdatedDomain().getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void whenNoPerClusterReplicaSettingAndDefaultMatchesRequest_doNothing() {
    configureDomain().withDefaultReplicaCount(REPLICA_LIMIT);

    restBackend.scaleCluster(NAME1, "cluster1", REPLICA_LIMIT);

    assertThat(getUpdatedDomain(), nullValue());
  }

  @Test(expected = WebApplicationException.class)
  public void whenReplaceDomainReturnsError_scaleClusterThrowsException() {
    testSupport.failOnResource(DOMAIN, NAME2, NS, HTTP_CONFLICT);

    DomainConfiguratorFactory.forDomain(domain2).configureCluster("cluster1").withReplicas(2);

    restBackend.scaleCluster(NAME2, "cluster1", 3);
  }

  @Test
  public void verify_getWlsDomainConfig_returnsWlsDomainConfig() {
    WlsDomainConfig wlsDomainConfig = ((RestBackendImpl) restBackend).getWlsDomainConfig(NAME1);

    assertThat(wlsDomainConfig.getName(), equalTo(NAME1));
  }

  @Test
  public void verify_getWlsDomainConfig_doesNotReturnNull_whenNoSuchDomainUID() {
    WlsDomainConfig wlsDomainConfig =
        ((RestBackendImpl) restBackend).getWlsDomainConfig("NoSuchDomainUID");

    assertThat(wlsDomainConfig, notNullValue());
  }

  @Test
  public void verify_getWlsDomainConfig_doesNotReturnNull_whenScanIsNull() {
    config = null;

    WlsDomainConfig wlsDomainConfig = ((RestBackendImpl) restBackend).getWlsDomainConfig(NAME1);

    assertThat(wlsDomainConfig, notNullValue());
  }

  private DomainConfigurator configureDomain() {
    return configurator;
  }

  private void setupScanCache() {
    config = configSupport.createDomainConfig();
  }
}
