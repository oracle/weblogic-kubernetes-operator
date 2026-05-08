// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import jakarta.ws.rs.WebApplicationException;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestBackendImplTest {
  private static final String MANAGED_NS = "managed-ns";
  private static final String UNMANAGED_NS = "unmanaged-ns";
  private static final String DOMAIN_NAME = "sample-domain";
  private static final String DOMAIN_UID = "domain-uid";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
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
        new V1ObjectMeta().name(DOMAIN_NAME).namespace(MANAGED_NS).uid("other-domain-uid")));

    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> listClusters(MANAGED_NS));

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
            () -> createBackend().createOrReplaceCluster(createClusterMap(UNMANAGED_NS)));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenCreatingClusterInManagedNamespace_createCluster() {
    createBackend().createOrReplaceCluster(createClusterMap(MANAGED_NS));

    ClusterResource clusterResource =
        testSupport.getResourceWithName(KubernetesTestSupport.CLUSTER, "sample-domain-cluster-1");
    assertThat(clusterResource, notNullValue());
  }

  @Test
  void whenReplacingClusterNotCreatedByOperator_returnForbidden() {
    testSupport.defineResources(createExistingCluster());

    WebApplicationException exception =
        assertThrows(WebApplicationException.class,
            () -> createBackend().createOrReplaceCluster(createClusterMap(MANAGED_NS)));

    assertThat(exception.getResponse().getStatus(), equalTo(HTTP_FORBIDDEN));
  }

  @Test
  void whenReplacingClusterCreatedByOperator_replaceCluster() {
    ClusterResource existingCluster = createExistingCluster();
    existingCluster.getMetadata().putLabelsItem(CREATEDBYOPERATOR_LABEL, "true");
    testSupport.defineResources(existingCluster);

    createBackend().createOrReplaceCluster(createClusterMap(MANAGED_NS));

    ClusterResource clusterResource =
        testSupport.getResourceWithName(KubernetesTestSupport.CLUSTER, "sample-domain-cluster-1");
    assertThat(clusterResource, notNullValue());
  }

  private RestBackendImpl createBackend() {
    Set<String> managedNamespaces = Set.of(MANAGED_NS);
    return new RestBackendImpl(() -> managedNamespaces, managedNamespaces::contains);
  }

  private List<Map<String, Object>> listClusters(String namespace) {
    return createBackend().listClusters(namespace, DOMAIN_NAME, DOMAIN_UID);
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
    return new DomainResource().withMetadata(
        new V1ObjectMeta().name(DOMAIN_NAME).namespace(MANAGED_NS).uid(DOMAIN_UID))
        .withSpec(new DomainSpec().withCluster(new V1LocalObjectReference().name("sample-domain-cluster-1")));
  }
}
