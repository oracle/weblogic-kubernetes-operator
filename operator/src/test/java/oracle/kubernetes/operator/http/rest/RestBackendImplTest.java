// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.util.List;

import com.google.gson.Gson;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import jakarta.ws.rs.WebApplicationException;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CLUSTER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestBackendImplTest {
  private static final String DOMAIN_UID = "fchost-domain";
  private static final String CLUSTER_NAME = "HostCluster";
  private static final String CLUSTER_RESOURCE_NAME = "fchost-domain-hostcluster";
  private static final String MOCK_NS = "mock-ns";
  private static final String PROD_NS = "prod-ns";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new java.util.ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(ScanCacheStub.install());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenDomainUidExistsInMultipleManagedNamespaces_scaleClusterFailsWithoutPatchingWrongNamespace() {
    defineDomainAndCluster(MOCK_NS, 1);
    defineDomainAndCluster(PROD_NS, 3);
    defineTopology(MOCK_NS);
    defineTopology(PROD_NS);

    WebApplicationException thrown =
        assertThrows(WebApplicationException.class,
            () -> createBackend(MOCK_NS, PROD_NS).scaleCluster(DOMAIN_UID, CLUSTER_NAME, 4));

    assertThat(thrown.getResponse().getStatus(), equalTo(CONFLICT.getStatusCode()));
    assertThat(thrown.getResponse().getStatusInfo().getReasonPhrase(), containsString(DOMAIN_UID));
    assertThat(thrown.getResponse().getStatusInfo().getReasonPhrase(), containsString(MOCK_NS));
    assertThat(thrown.getResponse().getStatusInfo().getReasonPhrase(), containsString(PROD_NS));
    assertThat(getClusterReplicas(MOCK_NS), equalTo(1));
    assertThat(getClusterReplicas(PROD_NS), equalTo(3));
  }

  @Test
  void whenDomainUidIsUnique_scaleClusterPatchesMatchingNamespace() {
    defineDomainAndCluster(PROD_NS, 3);
    defineTopology(PROD_NS);

    createBackend(MOCK_NS, PROD_NS).scaleCluster(DOMAIN_UID, CLUSTER_NAME, 4);

    assertThat(getClusterReplicas(PROD_NS), equalTo(4));
  }

  private RestBackendImpl createBackend(String... namespaces) {
    return new RestBackendImpl("principal", "accessToken", () -> List.of(namespaces), new Gson(), c -> c);
  }

  private void defineDomainAndCluster(String namespace, int replicas) {
    testSupport.defineResources(createDomain(namespace), createCluster(namespace, replicas));
  }

  private DomainResource createDomain(String namespace) {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(DOMAIN_UID))
        .withSpec(new DomainSpec()
            .withDomainUid(DOMAIN_UID)
            .withCluster(new V1LocalObjectReference().name(CLUSTER_RESOURCE_NAME)));
  }

  private ClusterResource createCluster(String namespace, int replicas) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(CLUSTER_RESOURCE_NAME))
        .spec(new ClusterSpec().withClusterName(CLUSTER_NAME).withReplicas(replicas));
  }

  private void defineTopology(String namespace) {
    WlsDomainConfig domainConfig = new WlsDomainConfig("domain");
    WlsClusterConfig clusterConfig = new WlsClusterConfig(CLUSTER_NAME)
        .addServerConfig(new WlsServerConfig("ms1", "localhost", 8001))
        .addServerConfig(new WlsServerConfig("ms2", "localhost", 8002))
        .addServerConfig(new WlsServerConfig("ms3", "localhost", 8003))
        .addServerConfig(new WlsServerConfig("ms4", "localhost", 8004));
    domainConfig.withCluster(clusterConfig);
    ScanCache.INSTANCE.registerScan(namespace, DOMAIN_UID, new Scan(domainConfig, SystemClock.now()));
  }

  private Integer getClusterReplicas(String namespace) {
    return testSupport.<ClusterResource>getResources(CLUSTER).stream()
        .filter(c -> CLUSTER_RESOURCE_NAME.equals(c.getClusterResourceName()))
        .filter(c -> namespace.equals(c.getNamespace()))
        .findFirst()
        .map(ClusterResource::getSpec)
        .map(ClusterSpec::getReplicas)
        .orElse(null);
  }
}
