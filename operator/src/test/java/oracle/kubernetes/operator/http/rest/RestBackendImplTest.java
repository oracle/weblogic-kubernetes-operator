// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CLUSTER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestBackendImplTest {
  private static final String MANAGED_NS = "managed-ns";
  private static final String UNMANAGED_NS = "unmanaged-ns";
  private static final String CONVERSION_DOMAIN_NAME = "sample-domain";
  private static final String CONVERSION_DOMAIN_UID = "domain-uid";
  private static final String SCALE_DOMAIN_UID = "fchost-domain";
  private static final String SCALE_CLUSTER_NAME = "HostCluster";
  private static final String SCALE_CLUSTER_RESOURCE_NAME = "fchost-domain-hostcluster";
  private static final String MOCK_NS = "mock-ns";
  private static final String PROD_NS = "prod-ns";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

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
  void whenListingClustersInUnmanagedNamespace_returnForbidden() {
    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> listClusters(UNMANAGED_NS));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenListingClustersWithoutMatchingDomain_returnForbidden() {
    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> listClusters(MANAGED_NS));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenListingClustersForDifferentDomainUid_returnForbidden() {
    testSupport.defineResources(createExistingDomain().withMetadata(
        new V1ObjectMeta().name(CONVERSION_DOMAIN_NAME).namespace(MANAGED_NS).uid("other-domain-uid")));

    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> listClusters(MANAGED_NS));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenListingClustersWithoutDomainUid_returnForbidden() {
    testSupport.defineResources(createExistingDomain());

    WebApplicationException exception = assertThrows(WebApplicationException.class,
        () -> createConversionBackend().listClusters(MANAGED_NS, CONVERSION_DOMAIN_NAME, null));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenListingClustersForMatchingDomain_listClusters() {
    testSupport.defineResources(createExistingDomain(), createExistingCluster());

    assertThat(listClusters(MANAGED_NS).size(), equalTo(1));
  }

  @Test
  void whenCreatingClusterInUnmanagedNamespace_returnForbidden() {
    WebApplicationException exception =
        assertThrows(WebApplicationException.class,
            () -> createConversionBackend().createOrReplaceCluster(
                createClusterMap(UNMANAGED_NS), CONVERSION_DOMAIN_NAME, CONVERSION_DOMAIN_UID));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenCreatingClusterInManagedNamespace_createCluster() {
    createConversionBackend().createOrReplaceCluster(
        createClusterMap(MANAGED_NS), CONVERSION_DOMAIN_NAME, CONVERSION_DOMAIN_UID);

    ClusterResource clusterResource =
        testSupport.getResourceWithName(KubernetesTestSupport.CLUSTER, "sample-domain-cluster-1");
    assertThat(clusterResource, notNullValue());
  }

  @Test
  void whenReplacingClusterNotCreatedByOperator_returnForbidden() {
    testSupport.defineResources(createExistingCluster());

    WebApplicationException exception =
        assertThrows(WebApplicationException.class,
            () -> createConversionBackend().createOrReplaceCluster(
                createClusterMap(MANAGED_NS), CONVERSION_DOMAIN_NAME, CONVERSION_DOMAIN_UID));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenReplacingClusterCreatedByOperatorWithoutMatchingDomain_returnForbidden() {
    ClusterResource existingCluster = createExistingCluster();
    existingCluster.getMetadata().putLabelsItem(CREATEDBYOPERATOR_LABEL, "true");
    testSupport.defineResources(existingCluster);

    WebApplicationException exception =
        assertThrows(WebApplicationException.class,
            () -> createConversionBackend().createOrReplaceCluster(
                createClusterMap(MANAGED_NS), CONVERSION_DOMAIN_NAME, CONVERSION_DOMAIN_UID));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenReplacingClusterCreatedByOperatorNotReferencedByDomain_returnForbidden() {
    ClusterResource existingCluster = createExistingCluster();
    existingCluster.getMetadata().putLabelsItem(CREATEDBYOPERATOR_LABEL, "true");
    testSupport.defineResources(existingCluster, createExistingDomain("other-cluster"));

    WebApplicationException exception =
        assertThrows(WebApplicationException.class,
            () -> createConversionBackend().createOrReplaceCluster(
                createClusterMap(MANAGED_NS), CONVERSION_DOMAIN_NAME, CONVERSION_DOMAIN_UID));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenReplacingClusterCreatedByOperatorForMatchingDomain_replaceCluster() {
    ClusterResource existingCluster = createExistingCluster();
    existingCluster.getMetadata().putLabelsItem(CREATEDBYOPERATOR_LABEL, "true");
    testSupport.defineResources(existingCluster, createExistingDomain());

    createConversionBackend().createOrReplaceCluster(
        createClusterMap(MANAGED_NS), CONVERSION_DOMAIN_NAME, CONVERSION_DOMAIN_UID);

    ClusterResource clusterResource =
        testSupport.getResourceWithName(KubernetesTestSupport.CLUSTER, "sample-domain-cluster-1");
    assertThat(clusterResource, notNullValue());
  }

  @Test
  void whenDomainUidExistsInMultipleManagedNamespaces_scaleClusterFailsWithoutPatchingWrongNamespace() {
    defineDomainAndCluster(MOCK_NS, 1);
    defineDomainAndCluster(PROD_NS, 3);
    defineTopology(MOCK_NS);
    defineTopology(PROD_NS);

    WebApplicationException thrown =
        assertThrows(WebApplicationException.class,
            () -> createScaleBackend(MOCK_NS, PROD_NS).scaleCluster(SCALE_DOMAIN_UID, SCALE_CLUSTER_NAME, 4));

    assertThat(thrown.getResponse().getStatus(), equalTo(CONFLICT.getStatusCode()));
    assertThat(thrown.getResponse().getStatusInfo().getReasonPhrase(), containsString(SCALE_DOMAIN_UID));
    assertThat(thrown.getResponse().getStatusInfo().getReasonPhrase(), containsString(MOCK_NS));
    assertThat(thrown.getResponse().getStatusInfo().getReasonPhrase(), containsString(PROD_NS));
    assertThat(getClusterReplicas(MOCK_NS), equalTo(1));
    assertThat(getClusterReplicas(PROD_NS), equalTo(3));
  }

  @Test
  void whenDomainUidIsUnique_scaleClusterPatchesMatchingNamespace() {
    defineDomainAndCluster(PROD_NS, 3);
    defineTopology(PROD_NS);

    createScaleBackend(MOCK_NS, PROD_NS).scaleCluster(SCALE_DOMAIN_UID, SCALE_CLUSTER_NAME, 4);

    assertThat(getClusterReplicas(PROD_NS), equalTo(4));
  }

  private RestBackendImpl createConversionBackend() {
    Set<String> managedNamespaces = Set.of(MANAGED_NS);
    return new RestBackendImpl(() -> managedNamespaces, managedNamespaces::contains);
  }

  private RestBackendImpl createScaleBackend(String... namespaces) {
    return new RestBackendImpl("principal", "accessToken", () -> List.of(namespaces), new Gson(), c -> c);
  }

  private List<Map<String, Object>> listClusters(String namespace) {
    return createConversionBackend().listClusters(namespace, CONVERSION_DOMAIN_NAME, CONVERSION_DOMAIN_UID);
  }

  private Map<String, Object> createClusterMap(String namespace) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("name", "sample-domain-cluster-1");
    metadata.put("namespace", namespace);

    Map<String, Object> spec = new HashMap<>();
    spec.put("clusterName", "cluster-1");

    Map<String, Object> cluster = new HashMap<>();
    cluster.put("apiVersion", "weblogic.oracle/v1");
    cluster.put("kind", "Cluster");
    cluster.put("metadata", metadata);
    cluster.put("spec", spec);
    return cluster;
  }

  private ClusterResource createExistingCluster() {
    return new ClusterResource().withMetadata(
        new V1ObjectMeta().name("sample-domain-cluster-1").namespace(MANAGED_NS));
  }

  private DomainResource createExistingDomain() {
    return createExistingDomain("sample-domain-cluster-1");
  }

  private DomainResource createExistingDomain(String clusterResourceName) {
    return new DomainResource().withMetadata(
        new V1ObjectMeta().name(CONVERSION_DOMAIN_NAME).namespace(MANAGED_NS).uid(CONVERSION_DOMAIN_UID))
        .withSpec(new DomainSpec().withCluster(new V1LocalObjectReference().name(clusterResourceName)));
  }

  private void defineDomainAndCluster(String namespace, int replicas) {
    testSupport.defineResources(createScaleDomain(namespace), createScaleCluster(namespace, replicas));
  }

  private DomainResource createScaleDomain(String namespace) {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(SCALE_DOMAIN_UID))
        .withSpec(new DomainSpec()
            .withDomainUid(SCALE_DOMAIN_UID)
            .withCluster(new V1LocalObjectReference().name(SCALE_CLUSTER_RESOURCE_NAME)));
  }

  private ClusterResource createScaleCluster(String namespace, int replicas) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(SCALE_CLUSTER_RESOURCE_NAME))
        .spec(new ClusterSpec().withClusterName(SCALE_CLUSTER_NAME).withReplicas(replicas));
  }

  private void defineTopology(String namespace) {
    WlsDomainConfig domainConfig = new WlsDomainConfig("domain");
    WlsClusterConfig clusterConfig = new WlsClusterConfig(SCALE_CLUSTER_NAME)
        .addServerConfig(new WlsServerConfig("ms1", "localhost", 8001))
        .addServerConfig(new WlsServerConfig("ms2", "localhost", 8002))
        .addServerConfig(new WlsServerConfig("ms3", "localhost", 8003))
        .addServerConfig(new WlsServerConfig("ms4", "localhost", 8004));
    domainConfig.withCluster(clusterConfig);
    ScanCache.INSTANCE.registerScan(namespace, SCALE_DOMAIN_UID, new Scan(domainConfig, SystemClock.now()));
  }

  private Integer getClusterReplicas(String namespace) {
    return testSupport.<ClusterResource>getResources(CLUSTER).stream()
        .filter(c -> SCALE_CLUSTER_RESOURCE_NAME.equals(c.getClusterResourceName()))
        .filter(c -> namespace.equals(c.getNamespace()))
        .findFirst()
        .map(ClusterResource::getSpec)
        .map(ClusterSpec::getReplicas)
        .orElse(null);
  }
}
